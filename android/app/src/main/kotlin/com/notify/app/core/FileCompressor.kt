package com.notify.app.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlin.math.max

data class CompressedFileResult(
    val bytes: ByteArray,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val formattedSize: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as CompressedFileResult
        return bytes.contentEquals(other.bytes) && fileName == other.fileName
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + fileName.hashCode()
        return result
    }
}

object FileCompressor {

    suspend fun compressAndPrepareFile(context: Context, uri: Uri): Result<CompressedFileResult> = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            val detectedMime = contentResolver.getType(uri) ?: "application/octet-stream"
            val originalName = queryFileName(context, uri) ?: "material_upload_${System.currentTimeMillis()}"

            if (detectedMime.startsWith("image/")) {
                // Image compression pipeline
                val inputStream: InputStream? = contentResolver.openInputStream(uri)
                val originalBytes = inputStream?.use { it.readBytes() }
                    ?: return@withContext Result.failure(Exception("Cannot read image bytes from device"))

                // Decode bounds first to prevent OutOfMemory
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(originalBytes, 0, originalBytes.size, options)

                val maxDimension = 1600
                var sampleSize = 1
                val largest = max(options.outWidth, options.outHeight)
                while (largest / sampleSize > maxDimension) {
                    sampleSize *= 2
                }

                // Decode actual scaled bitmap
                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.RGB_565 // Half memory footprint
                }
                val bitmap = BitmapFactory.decodeByteArray(originalBytes, 0, originalBytes.size, decodeOptions)
                    ?: return@withContext Result.failure(Exception("Failed to decode image"))

                val outputStream = ByteArrayOutputStream()
                // Compress to JPEG with 80% quality (ideal balance between clarity and tiny file size)
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                bitmap.recycle()

                val compressedBytes = outputStream.toByteArray()
                val safeFileName = if (originalName.contains(".")) {
                    "${originalName.substringBeforeLast(".")}.jpg"
                } else {
                    "$originalName.jpg"
                }

                Result.success(
                    CompressedFileResult(
                        bytes = compressedBytes,
                        fileName = safeFileName,
                        mimeType = "image/jpeg",
                        sizeBytes = compressedBytes.size.toLong(),
                        formattedSize = formatBytes(compressedBytes.size.toLong())
                    )
                )
            } else {
                // PDF or Document
                val inputStream = contentResolver.openInputStream(uri)
                val bytes = inputStream?.use { it.readBytes() }
                    ?: return@withContext Result.failure(Exception("Cannot read document bytes"))

                val maxAllowedBytes = 15 * 1024 * 1024 // 15MB hard cap
                if (bytes.size > maxAllowedBytes) {
                    return@withContext Result.failure(Exception("File is too large (${formatBytes(bytes.size.toLong())}). Please select a file under 15MB."))
                }

                val safeMime = if (originalName.endsWith(".pdf", ignoreCase = true)) "application/pdf" else detectedMime
                Result.success(
                    CompressedFileResult(
                        bytes = bytes,
                        fileName = originalName,
                        mimeType = safeMime,
                        sizeBytes = bytes.size.toLong(),
                        formattedSize = formatBytes(bytes.size.toLong())
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun queryFileName(context: Context, uri: Uri): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        name = cursor.getString(index)
                    }
                }
            }
        }
        if (name == null) {
            name = uri.path
            val cut = name?.lastIndexOf('/')
            if (cut != null && cut != -1) {
                name = name?.substring(cut + 1)
            }
        }
        return name
    }

    fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> String.format(java.util.Locale.US, "%.1f MB", bytes.toDouble() / (1024 * 1024))
        }
    }
}
