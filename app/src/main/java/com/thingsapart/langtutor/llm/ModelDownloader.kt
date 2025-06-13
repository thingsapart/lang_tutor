package com.thingsapart.langtutor.llm

import android.content.Context
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

