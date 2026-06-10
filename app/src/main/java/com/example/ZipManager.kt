package com.example

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ZipManager {
    private const val TAG = "ZipManager"

    /**
     * Unzips a file from uri into a destDirectory.
     * Returns a list of extracted Files.
     */
    fun unzipFile(context: Context, zipUri: Uri, destDirectory: File): List<File> {
        val extractedFiles = mutableListOf<File>()
        if (!destDirectory.exists()) {
            destDirectory.mkdirs()
        }

        try {
            val contentResolver = context.contentResolver
            contentResolver.openInputStream(zipUri)?.use { inputStream ->
                ZipInputStream(BufferedInputStream(inputStream)).use { zipInputStream ->
                    var entry: ZipEntry? = zipInputStream.nextEntry
                    while (entry != null) {
                        val name = entry.name
                        // Avoid Directory Traversal vulnerability
                        val file = File(destDirectory, name).canonicalFile
                        if (!file.path.startsWith(destDirectory.canonicalPath)) {
                            throw SecurityException("Zip entry is outside of destination directory")
                        }

                        if (entry.isDirectory) {
                            file.mkdirs()
                        } else {
                            file.parentFile?.mkdirs()
                            FileOutputStream(file).use { fileOutputStream ->
                                val buffer = ByteArray(4096)
                                var count: Int
                                while (zipInputStream.read(buffer).also { count = it } != -1) {
                                        fileOutputStream.write(buffer, 0, count)
                                }
                            }
                            extractedFiles.add(file)
                        }
                        zipInputStream.closeEntry()
                        entry = zipInputStream.nextEntry
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "unzipFile error: ${e.message}", e)
        }

        return extractedFiles
    }

    /**
     * Zips a list of files into a new ZIP file named as requested:
     * <original_name>_converted_<date>.zip
     */
    fun zipFiles(filesToZip: List<File>, originalZipNameWithoutExt: String, outputDirectory: File): File {
        if (!outputDirectory.exists()) {
            outputDirectory.mkdirs()
        }

        val dateString = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
        val outputZipFile = File(outputDirectory, "${originalZipNameWithoutExt}_converted_${dateString}.zip")

        try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(outputZipFile))).use { zipOutputStream ->
                for (file in filesToZip) {
                    if (!file.exists() || file.isDirectory) continue

                    val entry = ZipEntry(file.name)
                    zipOutputStream.putNextEntry(entry)

                    FileInputStream(file).use { fileInputStream ->
                        val buffer = ByteArray(4096)
                        var count: Int
                        while (fileInputStream.read(buffer).also { count = it } != -1) {
                            zipOutputStream.write(buffer, 0, count)
                        }
                    }
                    zipOutputStream.closeEntry()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "zipFiles error: ${e.message}", e)
        }

        return outputZipFile
    }
}
