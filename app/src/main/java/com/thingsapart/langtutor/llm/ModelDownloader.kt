package com.thingsapart.langtutor.llm

import android.content.Context
import com.thingsapart.langtutor.llm.AsrModelConfig // Added import
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.use
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class ModelDownloader {
    private val client = OkHttpClient()

    suspend fun downloadModel(
        context: Context,
        modelConfig: LlmModelConfig,
        progressCallback: (Float) -> Unit
    ): Result<File> {
        return withContext(Dispatchers.IO) {
            val outputFile = File(ModelManager.getLocalModelPath(context, modelConfig))

            try {
                val requestBuilder = Request.Builder().url(modelConfig.url)
                val response = client.newCall(requestBuilder.build()).execute()

                /**
                if (!response.isSuccessful) {
                if (response.code == UNAUTHORIZED_CODE) {
                val accessToken = SecureStorage.getToken(context)
                if (!accessToken.isNullOrEmpty()) {
                // Remove invalid or expired token
                SecureStorage.removeToken(context)
                }
                throw UnauthorizedAccessException()
                }
                throw Exception("Download failed: ${response.code}")
                }
                 */

                response.body?.byteStream()?.use { inputStream ->
                    FileOutputStream(outputFile).use { outputStream ->
                        val buffer = ByteArray(4096)
                        var bytesRead: Int
                        var totalBytesRead = 0L
                        val contentLength = response.body?.contentLength() ?: -1
                        var lastProgress = -1

                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                            totalBytesRead += bytesRead
                            val progress = if (contentLength > 0) {
                                (totalBytesRead * 100 / contentLength).toInt()
                            } else {
                                50
                            }
                            if (lastProgress != progress) {
                                progressCallback(progress.toFloat())
                                lastProgress = progress
                            }
                        }
                        outputStream.flush()
                    }
                }

                Result.success(outputFile)
            } catch (e: Exception) {
                // Clean up partially downloaded file on error
                if (outputFile.exists()) {
                    outputFile.delete()
                }
                Result.failure(e)
            }
        }
    }

    suspend fun downloadAsrVocab(
        context: Context,
        modelConfig: AsrModelConfig,
        progressCallback: (Float) -> Unit
    ): Result<File> {
        return withContext(Dispatchers.IO) {
            // Check if vocabUrl or vocabFileName is null. If so, cannot download.
            val vocabUrlString = modelConfig.vocabUrl
            if (vocabUrlString == null) {
                return@withContext Result.failure(IllegalArgumentException("vocabUrl is null in AsrModelConfig"))
            }
            val vocabFile = ModelManager.getLocalAsrVocabFile(context, modelConfig)
            if (vocabFile == null) {
                return@withContext Result.failure(IllegalArgumentException("vocabFileName is null, cannot determine output file for vocab."))
            }

            // Ensure parent directory exists
            vocabFile.parentFile?.mkdirs()

            try {
                val request = Request.Builder().url(vocabUrlString).build()
                val response = client.newCall(request).execute() // client is the OkHttpClient instance

                if (!response.isSuccessful) {
                    throw IOException("Download failed: ${response.code} ${response.message}")
                }

                response.body?.byteStream()?.use { inputStream ->
                    FileOutputStream(vocabFile).use { outputStream ->
                        val buffer = ByteArray(4096)
                        var bytesRead: Int
                        var totalBytesRead = 0L
                        val contentLength = response.body?.contentLength() ?: -1L
                        var lastProgress = -1

                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                            totalBytesRead += bytesRead
                            if (contentLength > 0) {
                                val progress = (totalBytesRead * 100 / contentLength).toInt()
                                if (lastProgress != progress) {
                                    progressCallback(progress.toFloat())
                                    lastProgress = progress
                                }
                            } else {
                                // Indeterminate progress, report 50% or update frequently
                                progressCallback(50f)
                            }
                        }
                        outputStream.flush()
                        // Ensure 100% is reported at the end if determinate
                        if (contentLength > 0 && lastProgress != 100) progressCallback(100f)
                        else if (contentLength <=0 && lastProgress == 50) progressCallback(100f) // Ensure 100% for indeterminate too
                    }
                }
                Result.success(vocabFile)
            } catch (e: Exception) {
                // Clean up partially downloaded file on error
                if (vocabFile.exists()) {
                    vocabFile.delete()
                }
                Result.failure(e)
            }
        }
    }

    suspend fun downloadAsrModel(
        context: Context,
        modelConfig: AsrModelConfig,
        progressCallback: (Float) -> Unit
    ): Result<File> {
        return withContext(Dispatchers.IO) {
            val outputFile = ModelManager.getLocalAsrModelFile(context, modelConfig)

            try {
                val requestBuilder = Request.Builder().url(modelConfig.url)
                val response = client.newCall(requestBuilder.build()).execute()

                if (!response.isSuccessful) {
                    throw IOException("Download failed: ${response.code} ${response.message}")
                }

                response.body?.byteStream()?.use { inputStream ->
                    FileOutputStream(outputFile).use { outputStream ->
                        val buffer = ByteArray(4096)
                        var bytesRead: Int
                        var totalBytesRead = 0L
                        val contentLength = response.body?.contentLength() ?: -1
                        var lastProgress = -1

                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                            totalBytesRead += bytesRead
                            val progress = if (contentLength > 0) {
                                (totalBytesRead * 100 / contentLength).toInt()
                            } else {
                                // Fallback for unknown content length, report 50% then 100%
                                50 // Or handle differently, e.g., based on bytes downloaded
                            }
                            if (lastProgress != progress) {
                                progressCallback(progress.toFloat())
                                lastProgress = progress
                            }
                        }
                        outputStream.flush()
                        // Ensure 100% is reported at the end if not already
                        if (lastProgress != 100 && contentLength > 0) {
                             progressCallback(100f)
                        } else if (contentLength <= 0 && lastProgress == 50) { // If content length was unknown
                            progressCallback(100f)
                        }
                    }
                }
                Result.success(outputFile)
            } catch (e: Exception) {
                // Clean up partially downloaded file on error
                if (outputFile.exists()) {
                    outputFile.delete()
                }
                Result.failure(e)
            }
        }
    }

    suspend fun downloadModelOld(
        context: Context,
        modelConfig: LlmModelConfig,
        progressCallback: (Float) -> Unit
    ): Result<File> {
        return withContext(Dispatchers.IO) {
            val targetFile = ModelManager.getLocalModelFile(context, modelConfig)
            var connection: HttpURLConnection? = null
            var outputStream: FileOutputStream? = null

            try {
                // Ensure parent directory exists
                targetFile.parentFile?.mkdirs()

                val url = URL(modelConfig.url)
                connection = url.openConnection() as HttpURLConnection
                connection.connect()

                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    return@withContext Result.failure(
                        IOException("Server returned HTTP ${connection.responseCode} ${connection.responseMessage}")
                    )
                }

                val fileSize = connection.contentLength
                outputStream = FileOutputStream(targetFile)
                val buffer = ByteArray(4096)
                var bytesRead: Int
                var totalBytesRead: Long = 0

                // Initialize progress to 0%
                progressCallback(0f)

                while (connection.inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead
                    if (fileSize > 0) {
                        val progress = (totalBytesRead * 100f / fileSize)
                        progressCallback(progress)
                    } else {
                        // If fileSize is unknown, report indeterminate progress (-1 or just update frequently)
                        // For simplicity, let's just report 50% and then 100% at the end in this case
                        progressCallback(50f) // Placeholder for indeterminate
                    }
                }

                progressCallback(100f) // Ensure 100% is reported at the end
                Result.success(targetFile)

            } catch (e: Exception) {
                // Clean up partially downloaded file on error
                if (targetFile.exists()) {
                    targetFile.delete()
                }
                Result.failure(e)
            } finally {
                outputStream?.close()
                connection?.disconnect()
            }
        }
    }
}

