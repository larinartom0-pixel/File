package com.example

import android.app.Application
import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.util.UUID

enum class ConversionStatus {
    PENDING,
    CONVERTING,
    COMPLETED,
    FAILED
}

data class ConversionTask(
    val id: String = UUID.randomUUID().toString(),
    val originalName: String,
    val sourceUri: Uri? = null,
    val localSourceFile: File? = null,
    val targetFormat: String,
    val status: ConversionStatus = ConversionStatus.PENDING,
    val progress: Float = 0f,
    val statusText: String = "In Queue",
    val outputFilePath: String? = null,
    val parentZipId: String? = null,
    val parentZipName: String? = null,
    val errorMessage: String? = null
)

data class ZipTracker(
    val id: String,
    val originalName: String,
    val childTaskIds: List<String>,
    val targetFormat: String,
    val isCompletedAndRezipped: Boolean = false,
    val outputZipPath: String? = null
)

class ConversionViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "ConversionViewModel"
    private val context: Context get() = getApplication()

    private val prefs = context.getSharedPreferences("retro_transcoder_prefs", Context.MODE_PRIVATE)

    private val _tasks = MutableStateFlow<List<ConversionTask>>(emptyList())
    val tasks = _tasks.asStateFlow()

    private val _zipTrackers = MutableStateFlow<Map<String, ZipTracker>>(emptyMap())
    val zipTrackers = _zipTrackers.asStateFlow()

    // Persistent Concurrency Limit config from 1 to 20
    private val _concurrencyLimit = MutableStateFlow(2)
    val concurrencyLimit = _concurrencyLimit.asStateFlow()

    // Persistent Working Directory custom SAF URI string
    private val _workingDirectoryUri = MutableStateFlow<String?>(prefs.getString("working_directory_uri", null))
    val workingDirectoryUri = _workingDirectoryUri.asStateFlow()

    // Navigation/screen transition state flag
    private val _isSettingsOpen = MutableStateFlow(false)
    val isSettingsOpen = _isSettingsOpen.asStateFlow()

    // Auto compute host cores as helper capability reference
    val availableCores = Runtime.getRuntime().availableProcessors()

    private var activeSemaphore = Semaphore(2)

    private val _playingTaskId = MutableStateFlow<String?>(null)
    val playingTaskId = _playingTaskId.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null

    init {
        // Set dynamic limit based on cores
        val defaultLimit = when {
            availableCores <= 2 -> 1
            availableCores <= 4 -> 2
            else -> 3
        }
        val savedLimit = prefs.getInt("concurrency_limit", defaultLimit)
        setConcurrencyLimit(savedLimit)
    }

    fun setConcurrencyLimit(limit: Int) {
        val safeLimit = limit.coerceIn(1, 20)
        _concurrencyLimit.value = safeLimit
        activeSemaphore = Semaphore(safeLimit)
        prefs.edit().putInt("concurrency_limit", safeLimit).apply()
        Log.d(TAG, "Concurrency limit set to $safeLimit (Core count: $availableCores)")
    }

    fun setWorkingDirectoryUri(uri: Uri) {
        _workingDirectoryUri.value = uri.toString()
        prefs.edit().putString("working_directory_uri", uri.toString()).apply()
    }

    fun clearWorkingDirectoryUri() {
        _workingDirectoryUri.value = null
        prefs.edit().remove("working_directory_uri").apply()
    }

    fun setSettingsOpen(isOpen: Boolean) {
        _isSettingsOpen.value = isOpen
    }

    private fun getMimeTypeForExt(ext: String): String {
        return when (ext.lowercase()) {
            "wav" -> "audio/wav"
            "m4a" -> "audio/mp4"
            "mp3" -> "audio/mpeg"
            "ogg" -> "audio/ogg"
            "flac" -> "audio/x-flac"
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "mov" -> "video/quicktime"
            "webm" -> "video/webm"
            "zip" -> "application/zip"
            else -> "application/octet-stream"
        }
    }

    fun copyToCustomDirectory(file: File, mimeType: String): String? {
        val folderUriStr = _workingDirectoryUri.value ?: return null
        val treeUri = Uri.parse(folderUriStr)
        try {
            val resolver = context.contentResolver
            val treeId = DocumentsContract.getTreeDocumentId(treeUri)
            val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeId)
            val newDocUri = DocumentsContract.createDocument(
                resolver,
                parentDocUri,
                mimeType,
                file.name
            ) ?: return null

            resolver.openOutputStream(newDocUri)?.use { out ->
                file.inputStream().use { input ->
                    input.copyTo(out)
                }
            }
            Log.d(TAG, "Copied ${file.name} to custom directory: $newDocUri")
            return newDocUri.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy file to custom directory", e)
        }
        return null
    }

    /**
     * Pick a normal file to convert
     */
    fun addFileForConversion(uri: Uri, targetFormat: String) {
        val originalName = getFileName(context, uri) ?: "file_${System.currentTimeMillis()}"
        val task = ConversionTask(
            originalName = originalName,
            sourceUri = uri,
            targetFormat = targetFormat.lowercase()
        )
        _tasks.update { it + task }
        processQueue()
    }

    /**
     * Pick multiple files
     */
    fun addMultipleFilesForConversion(uris: List<Uri>, targetFormat: String) {
        val newTasks = uris.map { uri ->
            val originalName = getFileName(context, uri) ?: "file_${System.currentTimeMillis()}"
            ConversionTask(
                originalName = originalName,
                sourceUri = uri,
                targetFormat = targetFormat.lowercase()
            )
        }
        _tasks.update { it + newTasks }
        processQueue()
    }

    /**
     * Pick a ZIP archive containing multiple files.
     * Extracts files locally and queues them.
     */
    fun addZipForConversion(zipUri: Uri, targetFormat: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val zipName = getFileName(context, zipUri) ?: "archive_${System.currentTimeMillis()}.zip"
            val zipNameNoExt = zipName.substringBeforeLast(".")

            // Temporary location for unzipped files
            val tempDir = File(context.cacheDir, "unzipped_${UUID.randomUUID()}")
            tempDir.mkdirs()

            // Unzip content
            val extractedFiles = ZipManager.unzipFile(context, zipUri, tempDir)
            if (extractedFiles.isEmpty()) {
                Log.e(TAG, "No files extracted from ZIP or invalid ZIP format")
                return@launch
            }

            // Create individual conversion tasks
            val zipId = UUID.randomUUID().toString()
            val childTasks = extractedFiles.map { file ->
                ConversionTask(
                    originalName = file.name,
                    localSourceFile = file,
                    targetFormat = targetFormat.lowercase(),
                    parentZipId = zipId,
                    parentZipName = zipNameNoExt
                )
            }

            // Record Zip Tracker
            val tracker = ZipTracker(
                id = zipId,
                originalName = zipNameNoExt,
                childTaskIds = childTasks.map { it.id },
                targetFormat = targetFormat.lowercase()
            )

            _zipTrackers.update { it + (zipId to tracker) }
            _tasks.update { it + childTasks }

            Log.d(TAG, "Processed zip. Extracted ${extractedFiles.size} task(s). Zip ID: $zipId")
            processQueue()
        }
    }

    /**
     * Triggers queue execution loop
     */
    private fun processQueue() {
        val pendingTasks = _tasks.value.filter { it.status == ConversionStatus.PENDING }
        if (pendingTasks.isEmpty()) return

        for (task in pendingTasks) {
            // Update status to wait/scheduled so we don't pick it again instantly
            updateTaskStatus(task.id, ConversionStatus.PENDING, 0f, "Waiting in queue...")
            
            viewModelScope.launch(Dispatchers.Default) {
                activeSemaphore.withPermit {
                    executeConversion(task)
                }
            }
        }
    }

    private fun executeConversion(task: ConversionTask) {
        updateTaskStatus(task.id, ConversionStatus.CONVERTING, 0f, "Starting...")

        val outputDir = File(context.filesDir, "converted_outputs")
        if (!outputDir.exists()) outputDir.mkdirs()

        try {
            // Prepare source
            val localFileSource = task.localSourceFile
            val inputUriUri = task.sourceUri

            val finalFile = if (localFileSource != null && localFileSource.exists()) {
                // If it's a file unzipped from a zipper
                val tempUri = Uri.fromFile(localFileSource)
                LocalMediaProcessor.transcode(
                    context = context,
                    inputUri = tempUri,
                    outputDirectory = outputDir,
                    targetFormat = task.targetFormat,
                    originalFileName = task.originalName,
                    listener = object : LocalMediaProcessor.ProgressListener {
                        override fun onProgress(percentage: Float) {
                            updateTaskProgress(task.id, percentage)
                        }
                        override fun onStatusUpdate(status: String) {
                            updateTaskStatusText(task.id, status)
                        }
                    }
                )
            } else if (inputUriUri != null) {
                // Single file picked from SAF URI
                LocalMediaProcessor.transcode(
                    context = context,
                    inputUri = inputUriUri,
                    outputDirectory = outputDir,
                    targetFormat = task.targetFormat,
                    originalFileName = task.originalName,
                    listener = object : LocalMediaProcessor.ProgressListener {
                        override fun onProgress(percentage: Float) {
                            updateTaskProgress(task.id, percentage)
                        }
                        override fun onStatusUpdate(status: String) {
                            updateTaskStatusText(task.id, status)
                        }
                    }
                )
            } else {
                throw Exception("Reference file not found")
            }

            // Success
            val hasCustomDir = _workingDirectoryUri.value != null
            val savedToMsg = if (hasCustomDir) {
                val mimeType = getMimeTypeForExt(task.targetFormat)
                val retUri = copyToCustomDirectory(finalFile, mimeType)
                if (retUri != null) "Saved to Folder" else "Completed!"
            } else {
                "Completed!"
            }

            _tasks.update { current ->
                current.map {
                    if (it.id == task.id) {
                        it.copy(
                            status = ConversionStatus.COMPLETED,
                            progress = 100f,
                            statusText = savedToMsg,
                            outputFilePath = finalFile.absolutePath
                        )
                    } else it
                }
            }

            // Trigger ZIP check if applicable
            if (task.parentZipId != null) {
                checkAndAssembleZip(task.parentZipId)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error converting task: ${task.originalName}", e)
            _tasks.update { current ->
                current.map {
                    if (it.id == task.id) {
                        it.copy(
                            status = ConversionStatus.FAILED,
                            progress = 0f,
                            statusText = "Failed",
                            errorMessage = e.message ?: "Unknown error"
                        )
                    } else it
                }
            }
            
            // Trigger ZIP check even if failed so ZIP packaging is evaluated
            if (task.parentZipId != null) {
                checkAndAssembleZip(task.parentZipId)
            }
        }
    }

    /**
     * Checks if all files belonging to a ZIP are finished converting.
     * Repacks completed files back into a brand-new timestamped ZIP!
     */
    private fun checkAndAssembleZip(zipId: String) {
        val tracker = _zipTrackers.value[zipId] ?: return
        if (tracker.isCompletedAndRezipped) return

        // Fetch children
        val currentTasks = _tasks.value
        val zipChildren = currentTasks.filter { it.parentZipId == zipId }
        val allFinished = zipChildren.all { it.status == ConversionStatus.COMPLETED || it.status == ConversionStatus.FAILED }

        if (allFinished) {
            viewModelScope.launch(Dispatchers.IO) {
                val successfulOutputs = zipChildren
                    .filter { it.status == ConversionStatus.COMPLETED && it.outputFilePath != null }
                    .map { File(it.outputFilePath!!) }

                if (successfulOutputs.isNotEmpty()) {
                    val zipOutputDir = File(context.filesDir, "rezipped_outputs")
                    val resultZip = ZipManager.zipFiles(
                        filesToZip = successfulOutputs,
                        originalZipNameWithoutExt = tracker.originalName,
                        outputDirectory = zipOutputDir
                    )

                    val finalZipPath = if (_workingDirectoryUri.value != null) {
                        copyToCustomDirectory(resultZip, "application/zip")
                        resultZip.absolutePath
                    } else {
                        resultZip.absolutePath
                    }

                    _zipTrackers.update { trackers ->
                        val updatedMap = trackers.toMutableMap()
                        updatedMap[zipId] = tracker.copy(
                            isCompletedAndRezipped = true,
                            outputZipPath = finalZipPath
                        )
                        updatedMap
                    }

                    Log.d(TAG, "Re-zipped successfully! Compressed output file: ${resultZip.name} in standard path.")

                    // Cleanup unzipped sources
                    try {
                        zipChildren.firstOrNull()?.localSourceFile?.parentFile?.deleteRecursively()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error cleaning cache folder: ${e.message}")
                    }
                } else {
                    Log.w(TAG, "No successful outputs to pack inside zip $zipId")
                }
            }
        }
    }

    fun playAudio(task: ConversionTask) {
        val path = task.outputFilePath ?: return
        try {
            stopAudio()
            _playingTaskId.value = task.id
            mediaPlayer = MediaPlayer().apply {
                setDataSource(path)
                prepare()
                start()
                setOnCompletionListener {
                    _playingTaskId.value = null
                    stopAudio()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "playAudio error: ${e.message}", e)
            _playingTaskId.value = null
        }
    }

    fun stopAudio() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {}
        mediaPlayer = null
        _playingTaskId.value = null
    }

    fun clearHistory() {
        _tasks.value = emptyList()
        _zipTrackers.value = emptyMap()
    }

    // Helper state updaters
    private fun updateTaskStatus(id: String, status: ConversionStatus, progress: Float, text: String) {
        _tasks.update { list ->
            list.map { if (it.id == id) it.copy(status = status, progress = progress, statusText = text) else it }
        }
    }

    private fun updateTaskProgress(id: String, progress: Float) {
        _tasks.update { list ->
            list.map { if (it.id == id) it.copy(progress = progress) else it }
        }
    }

    private fun updateTaskStatusText(id: String, text: String) {
        _tasks.update { list ->
            list.map { if (it.id == id) it.copy(statusText = text) else it }
        }
    }

    // Get true name of safe user source document Uri using column query
    private fun getFileName(context: Context, uri: Uri): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        name = it.getString(index)
                    }
                }
            }
        }
        if (name == null) {
            name = uri.path
            val cut = name?.lastIndexOf('/') ?: -1
            if (cut != -1) {
                name = name?.substring(cut + 1)
            }
        }
        return name
    }

    override fun onCleared() {
        super.onCleared()
        stopAudio()
    }
}
