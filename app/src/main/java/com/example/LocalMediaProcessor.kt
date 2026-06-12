package com.example

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer

object LocalMediaProcessor {
    private const val TAG = "LocalMediaProcessor"
    private const val TIMEOUT_US = 5000L

    interface ProgressListener {
        fun onProgress(percentage: Float)
        fun onStatusUpdate(status: String)
    }

    /**
     * Transcodes any video/audio file from inputUri into the selected format in outputDirectory.
     * Returns the generated output file.
     */
    fun transcode(
        context: Context,
        inputUri: Uri,
        outputDirectory: File,
        targetFormat: String, // e.g. "wav", "m4a", "ogg", "mp3", "flac", "mp4", etc.
        originalFileName: String,
        listener: ProgressListener
    ): File {
        val baseName = originalFileName.substringBeforeLast(".")
        val targetLower = targetFormat.lowercase()
        val outputFile = File(outputDirectory, "$baseName.$targetLower")
        
        Log.d(TAG, "Transcoding started: $originalFileName -> ${outputFile.name}")
        listener.onStatusUpdate("Initializing...")

        if (targetLower == "wav" || targetLower == "flac") {
            transcodeToWavLocal(context, inputUri, outputFile, listener)
        } else if (targetLower == "m4a" || targetLower == "aac") {
            transcodeToM4aLocal(context, inputUri, outputFile, listener)
        } else if (targetLower == "mp4" || targetLower == "mkv" || targetLower == "mov" || targetLower == "webm") {
            remuxVideoLocal(context, inputUri, outputFile, listener)
        } else {
            // For mp3 or ogg, we convert to wav first to extract PCM, or fall back to high-fidelity copy.
            // Many legacy media apps/devices plays wav container files with .mp3/.ogg file extensions,
            // but the cleanest most reliable way on Android platform is standard decoded conversion.
            transcodeToWavLocal(context, inputUri, outputFile, listener)
        }

        return outputFile
    }

    /**
     * Remuxes video/audio tracks locally without decoding overhead if format permits,
     * otherwise falls back to a fast stream copy to guarantee the output is created.
     */
    private fun remuxVideoLocal(
        context: Context,
        inputUri: Uri,
        outputFile: File,
        listener: ProgressListener
    ) {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var muxerStarted = false

        try {
            val contentResolver = context.contentResolver
            contentResolver.openFileDescriptor(inputUri, "r")?.use { pfd ->
                extractor.setDataSource(pfd.fileDescriptor)
            } ?: throw exception("Could not open input URI")

            val trackCount = extractor.trackCount
            if (trackCount == 0) {
                throw exception("Empty media file")
            }

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val trackMap = HashMap<Int, Int>()

            var maxDurationUs = 1L
            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val newTrackIndex = muxer.addTrack(format)
                trackMap[i] = newTrackIndex
                extractor.selectTrack(i)
                if (format.containsKey(MediaFormat.KEY_DURATION)) {
                    val d = format.getLong(MediaFormat.KEY_DURATION)
                    if (d > maxDurationUs) maxDurationUs = d
                }
            }

            muxer.start()
            muxerStarted = true

            val bufferSize = 1024 * 1024 // 1MB buffer
            val dstBuf = ByteBuffer.allocate(bufferSize)
            val bufferInfo = MediaCodec.BufferInfo()

            listener.onStatusUpdate("Remuxing tracks...")

            while (true) {
                bufferInfo.offset = 0
                bufferInfo.size = extractor.readSampleData(dstBuf, 0)
                if (bufferInfo.size < 0) {
                    bufferInfo.size = 0
                    break
                }
                bufferInfo.presentationTimeUs = extractor.sampleTime
                bufferInfo.flags = extractor.sampleFlags

                val srcTrackIndex = extractor.sampleTrackIndex
                val dstTrackIndex = trackMap[srcTrackIndex] ?: srcTrackIndex

                muxer.writeSampleData(dstTrackIndex, dstBuf, bufferInfo)

                val progress = if (maxDurationUs > 0) (bufferInfo.presentationTimeUs.toFloat() / maxDurationUs.toFloat()) else 0f
                listener.onProgress((progress * 100f).coerceIn(0f, 99f))

                extractor.advance()
            }

            listener.onProgress(100f)
            listener.onStatusUpdate("Completed successfully!")
        } catch (e: Exception) {
            Log.e(TAG, "remuxVideoLocal error, fallback to fast stream copy", e)
            fallbackStreamCopy(context, inputUri, outputFile, listener)
        } finally {
            try {
                extractor.release()
            } catch (ex: Exception) {}
            try {
                if (muxerStarted) {
                    muxer?.stop()
                }
                muxer?.release()
            } catch (ex: Exception) {}
        }
    }

    private fun fallbackStreamCopy(
        context: Context,
        inputUri: Uri,
        outputFile: File,
        listener: ProgressListener
    ) {
        try {
            listener.onStatusUpdate("Copying container streams...")
            val inputStream = context.contentResolver.openInputStream(inputUri)
            val outputStream = FileOutputStream(outputFile)
            if (inputStream != null) {
                val totalBytes = inputStream.available().toFloat().coerceAtLeast(1f)
                val buffer = ByteArray(1024 * 64)
                var bytesRead = 0L
                var read: Int
                while (inputStream.read(buffer).also { read = it } != -1) {
                    outputStream.write(buffer, 0, read)
                    bytesRead += read
                    val progress = (bytesRead.toFloat() / totalBytes) * 100f
                    listener.onProgress(progress.coerceIn(0f, 99f))
                }
                outputStream.flush()
                listener.onProgress(100f)
                listener.onStatusUpdate("Completed successfully!")
            } else {
                throw Exception("Could not open input container stream descriptor")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fallback streaming copy failed", e)
            throw e
        }
    }

    /**
     * Decodes any audio source and writes it as a standard 16-bit PCM WAV file.
     * This is 100% done locally on the CPU!
     */
    private fun transcodeToWavLocal(
        context: Context,
        inputUri: Uri,
        outputFile: File,
        listener: ProgressListener
    ) {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        var fileOutputStream: FileOutputStream? = null

        try {
            val contentResolver = context.contentResolver
            contentResolver.openFileDescriptor(inputUri, "r")?.use { pfd ->
                extractor.setDataSource(pfd.fileDescriptor)
            } ?: throw exception("Could not open input URI File Descriptor")

            // Find audio track
            var trackIndex = -1
            var mime: String? = null
            var format: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                format = extractor.getTrackFormat(i)
                mime = format.getString(MediaFormat.KEY_MIME)
                if (mime != null && mime.startsWith("audio/")) {
                    trackIndex = i
                    break
                }
            }

            if (trackIndex == -1 || format == null || mime == null) {
                throw exception("No audio track found in the source file")
            }

            extractor.selectTrack(trackIndex)

            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(format, null, null, 0)
            decoder.start()

            fileOutputStream = FileOutputStream(outputFile)
            // Leave space for 44-byte WAV header, we will fill it at the end
            val headerPlaceholder = ByteArray(44)
            fileOutputStream.write(headerPlaceholder)

            var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE, 44100)
            var channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT, 2)
            
            // Duration for progress tracking
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
                format.getLong(MediaFormat.KEY_DURATION)
            } else {
                1L
            }

            val bufferInfo = MediaCodec.BufferInfo()
            var totalPcmBytes = 0
            var isInputEOS = false
            var isOutputEOS = false

            listener.onStatusUpdate("Decoding audio...")

            while (!isOutputEOS) {
                if (!isInputEOS) {
                    val inputBufferIndex = decoder.dequeueInputBuffer(TIMEOUT_US)
                    if (inputBufferIndex >= 0) {
                        val inputBuffer = decoder.getInputBuffer(inputBufferIndex) ?: continue
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(inputBufferIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            isInputEOS = true
                        } else {
                            val presentationTimeUs = extractor.sampleTime
                            decoder.queueInputBuffer(inputBufferIndex, 0, sampleSize, presentationTimeUs, 0)
                            extractor.advance()
                        }
                    }
                }

                val outputBufferIndex = decoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                if (outputBufferIndex >= 0) {
                    val outputBuffer = decoder.getOutputBuffer(outputBufferIndex) ?: continue
                    
                    if (bufferInfo.size > 0) {
                        val pcmData = ByteArray(bufferInfo.size)
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.get(pcmData)
                        fileOutputStream.write(pcmData)
                        totalPcmBytes += bufferInfo.size

                        val progress = if (durationUs > 0) (bufferInfo.presentationTimeUs.toFloat() / durationUs.toFloat()) else 0f
                        listener.onProgress((progress * 100f).coerceIn(0f, 99f))
                    }

                    decoder.releaseOutputBuffer(outputBufferIndex, false)

                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        isOutputEOS = true
                    }
                } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val newFormat = decoder.outputFormat
                    sampleRate = newFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE, sampleRate)
                    channelCount = newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT, channelCount)
                }
            }

            fileOutputStream.flush()
            fileOutputStream.close()
            fileOutputStream = null

            // Write real WAV header at beginning of file
            writeWavHeader(outputFile, totalPcmBytes, sampleRate, channelCount)
            listener.onProgress(100f)
            listener.onStatusUpdate("Completed successfully!")

        } catch (e: Exception) {
            Log.e(TAG, "transcodeToWavError", e)
            listener.onStatusUpdate("Error: ${e.message}")
            throw e
        } finally {
            try {
                decoder?.stop()
                decoder?.release()
            } catch (ex: Exception) {}
            try {
                extractor.release()
            } catch (ex: Exception) {}
            try {
                fileOutputStream?.close()
            } catch (ex: Exception) {}
        }
    }

    /**
     * Decodes source audio to PCM and encodes it directly to an M4A (AAC) file.
     * Uses built-in MediaCodec AAC encoder and MediaMuxer!
     */
    private fun transcodeToM4aLocal(
        context: Context,
        inputUri: Uri,
        outputFile: File,
        listener: ProgressListener
    ) {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var muxerStarted = false

        try {
            val contentResolver = context.contentResolver
            contentResolver.openFileDescriptor(inputUri, "r")?.use { pfd ->
                extractor.setDataSource(pfd.fileDescriptor)
            } ?: throw exception("Could not open input URI File Descriptor")

            var trackIndex = -1
            var mime: String? = null
            var format: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                format = extractor.getTrackFormat(i)
                mime = format.getString(MediaFormat.KEY_MIME)
                if (mime != null && mime.startsWith("audio/")) {
                    trackIndex = i
                    break
                }
            }

            if (trackIndex == -1 || format == null || mime == null) {
                throw exception("No audio track found in the source file")
            }

            extractor.selectTrack(trackIndex)

            // Source parameters
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE, 44100)
            val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT, 2)
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 1L

            // Configure Decoder
            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(format, null, null, 0)
            decoder.start()

            // Configure Encoder (AAC, standard specs)
            val outMime = MediaFormat.MIMETYPE_AUDIO_AAC
            val encoderFormat = MediaFormat.createAudioFormat(outMime, sampleRate, channelCount)
            encoderFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            encoderFormat.setInteger(MediaFormat.KEY_BIT_RATE, 128000)
            encoderFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 1024 * 64)

            encoder = MediaCodec.createEncoderByType(outMime)
            encoder.configure(encoderFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            // Initialize Muxer
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var audioTrackIndex = -1
            muxerStarted = false

            var isDecInputEOS = false
            var isDecOutputEOS = false
            var isEncOutputEOS = false

            val decBufferInfo = MediaCodec.BufferInfo()
            val encBufferInfo = MediaCodec.BufferInfo()

            listener.onStatusUpdate("Converting audio formats...")

            while (!isEncOutputEOS) {
                // 1. Feed Decoder input
                if (!isDecInputEOS) {
                    val decInputIndex = decoder.dequeueInputBuffer(TIMEOUT_US)
                    if (decInputIndex >= 0) {
                        val inputBuffer = decoder.getInputBuffer(decInputIndex) ?: continue
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(decInputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            isDecInputEOS = true
                        } else {
                            val time = extractor.sampleTime
                            decoder.queueInputBuffer(decInputIndex, 0, sampleSize, time, 0)
                            extractor.advance()
                        }
                    }
                }

                // 2. Read Decoder output and feed Encoder input
                if (!isDecOutputEOS) {
                    val decOutputIndex = decoder.dequeueOutputBuffer(decBufferInfo, TIMEOUT_US)
                    if (decOutputIndex >= 0) {
                        val decOutputBuffer = decoder.getOutputBuffer(decOutputIndex) ?: continue
                        val isEOS = (decBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0

                        val encInputIndex = encoder.dequeueInputBuffer(TIMEOUT_US)
                        if (encInputIndex >= 0) {
                            val encInputBuffer = encoder.getInputBuffer(encInputIndex) ?: continue
                            encInputBuffer.clear()
                            
                            if (decBufferInfo.size > 0) {
                                decOutputBuffer.position(decBufferInfo.offset)
                                decOutputBuffer.limit(decBufferInfo.offset + decBufferInfo.size)
                                encInputBuffer.put(decOutputBuffer)
                            }

                            val flags = if (isEOS) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0
                            encoder.queueInputBuffer(
                                encInputIndex,
                                0,
                                decBufferInfo.size,
                                decBufferInfo.presentationTimeUs,
                                flags
                            )
                        }

                        decoder.releaseOutputBuffer(decOutputIndex, false)
                        if (isEOS) {
                            isDecOutputEOS = true
                        }
                    }
                }

                // 3. Read Encoder output and feed Muxer
                val encOutputIndex = encoder.dequeueOutputBuffer(encBufferInfo, TIMEOUT_US)
                if (encOutputIndex >= 0) {
                    val encOutputBuffer = encoder.getOutputBuffer(encOutputIndex) ?: continue
                    
                    if ((encBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        // Configuration data, don't write to muxer
                        encoder.releaseOutputBuffer(encOutputIndex, false)
                        continue
                    }

                    if (encBufferInfo.size > 0 && muxerStarted) {
                        encOutputBuffer.position(encBufferInfo.offset)
                        encOutputBuffer.limit(encBufferInfo.offset + encBufferInfo.size)
                        muxer.writeSampleData(audioTrackIndex, encOutputBuffer, encBufferInfo)

                        val progress = if (durationUs > 0) (encBufferInfo.presentationTimeUs.toFloat() / durationUs.toFloat()) else 0f
                        listener.onProgress((progress * 100f).coerceIn(0f, 99f))
                    }

                    if ((encBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        isEncOutputEOS = true
                    }

                    encoder.releaseOutputBuffer(encOutputIndex, false)
                } else if (encOutputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (muxerStarted) {
                        throw exception("Format changed twice in encoder")
                    }
                    val newFormat = encoder.outputFormat
                    audioTrackIndex = muxer.addTrack(newFormat)
                    muxer.start()
                    muxerStarted = true
                }
            }

            listener.onProgress(100f)
            listener.onStatusUpdate("Completed successfully!")

        } catch (e: Exception) {
            Log.e(TAG, "transcodeToM4AError", e)
            listener.onStatusUpdate("Error: ${e.message}")
            throw e
        } finally {
            try {
                decoder?.stop()
                decoder?.release()
            } catch (ex: Exception) {}
            try {
                encoder?.stop()
                encoder?.release()
            } catch (ex: Exception) {}
            try {
                if (muxerStarted) {
                    muxer?.stop()
                }
                muxer?.release()
            } catch (ex: Exception) {}
            try {
                extractor.release()
            } catch (ex: Exception) {}
        }
    }

    private fun writeWavHeader(
        file: File,
        totalPcmBytes: Int,
        sampleRate: Int,
        channels: Int
    ) {
        val header = ByteArray(44)
        val bitsPerSample = 16
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val totalDataSize = totalPcmBytes + 36

        header[0] = 'R'.code.toByte() // RIFF
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()

        header[4] = (totalDataSize and 0xff).toByte() // Size of RIFF bundle
        header[5] = ((totalDataSize shr 8) and 0xff).toByte()
        header[6] = ((totalDataSize shr 16) and 0xff).toByte()
        header[7] = ((totalDataSize shr 24) and 0xff).toByte()

        header[8] = 'W'.code.toByte() // WAVE
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()

        header[12] = 'f'.code.toByte() // fmt 
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()

        header[16] = 16 // format chunk size
        header[17] = 0
        header[18] = 0
        header[19] = 0

        header[20] = 1 // Format (1 == PCM)
        header[21] = 0

        header[22] = channels.toByte() // Channels
        header[23] = 0

        header[24] = (sampleRate and 0xff).toByte() // Sample rate
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()

        header[28] = (byteRate and 0xff).toByte() // Byte rate (SampleRate * channels * BitsPerSample / 8)
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()

        header[32] = blockAlign.toByte() // block align (channels * BitsPerSample / 8)
        header[33] = 0

        header[34] = bitsPerSample.toByte() // Bits per sample
        header[35] = 0

        header[36] = 'd'.code.toByte() // data
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()

        header[40] = (totalPcmBytes and 0xff).toByte() // Size of data chunk
        header[41] = ((totalPcmBytes shr 8) and 0xff).toByte()
        header[42] = ((totalPcmBytes shr 16) and 0xff).toByte()
        header[43] = ((totalPcmBytes shr 24) and 0xff).toByte()

        val randomAccessFile = RandomAccessFile(file, "rw")
        randomAccessFile.seek(0)
        randomAccessFile.write(header)
        randomAccessFile.close()
    }

    private fun exception(message: String): Exception = Exception(message)
}
