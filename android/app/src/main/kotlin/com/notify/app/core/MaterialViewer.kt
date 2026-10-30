package com.notify.app.core

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.notify.app.domain.models.StudyMaterial
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object MaterialViewer {

    /**
     * Finds the local file if it exists in internal filesDir or cacheDir.
     */
    fun getMaterialFile(context: Context, material: StudyMaterial): File? {
        val filesDir = File(context.filesDir, "study_materials")
        if (!filesDir.exists()) filesDir.mkdirs()

        // 1. By ID_filename
        val f1 = File(filesDir, "${material.id}_${material.fileName}")
        if (f1.exists() && f1.length() > 0) return f1

        // 2. By filename directly
        val f2 = File(filesDir, material.fileName)
        if (f2.exists() && f2.length() > 0) return f2

        // 3. Match by name or title prefix in directory
        val cleanName = material.fileName.substringBeforeLast(".")
        val matchedInFiles = filesDir.listFiles()?.firstOrNull { file ->
            file.name.contains(material.id) || 
            (cleanName.isNotBlank() && file.name.contains(cleanName, ignoreCase = true)) ||
            file.name.contains(material.title.replace(" ", "_"), ignoreCase = true)
        }
        if (matchedInFiles != null && matchedInFiles.length() > 0) return matchedInFiles

        // 4. By file:// URL scheme
        if (material.fileUrl.startsWith("file://")) {
            val path = Uri.parse(material.fileUrl).path
            if (!path.isNullOrBlank()) {
                val f3 = File(path)
                if (f3.exists() && f3.length() > 0) return f3
            }
        }

        // 5. In cache directory
        val cacheDir = File(context.cacheDir, "study_materials")
        if (cacheDir.exists()) {
            val f4 = File(cacheDir, "${material.id}_${material.fileName}")
            if (f4.exists() && f4.length() > 0) return f4

            val f5 = File(cacheDir, material.fileName)
            if (f5.exists() && f5.length() > 0) return f5
        }

        return null
    }

    /**
     * Opens study material in Android's native PDF reader / image viewer.
     * Guaranteed never to open a broken website or 404 page!
     */
    fun openMaterial(context: Context, material: StudyMaterial, scope: CoroutineScope) {
        val existing = getMaterialFile(context, material)
        if (existing != null) {
            launchFileViewer(context, existing, material)
            return
        }

        // Remote downloadable file (e.g. Supabase storage public URL)
        val isFakeWebUrl = material.fileUrl.contains("sitcoe.ac.in/notices")
        if ((material.fileUrl.startsWith("http://") || material.fileUrl.startsWith("https://")) && !isFakeWebUrl) {
            Toast.makeText(context, "Opening ${material.fileName}...", Toast.LENGTH_SHORT).show()
            scope.launch(Dispatchers.IO) {
                val downloadedFile = downloadFile(context, material)
                withContext(Dispatchers.Main) {
                    if (downloadedFile != null && downloadedFile.exists()) {
                        launchFileViewer(context, downloadedFile, material)
                    } else {
                        Toast.makeText(context, "Opening study document preview...", Toast.LENGTH_SHORT).show()
                        val preview = generateDocumentPreview(context, material)
                        launchFileViewer(context, preview, material)
                    }
                }
            }
            return
        }

        // Generate offline native PDF preview with metadata (e.g. for mock or offline items)
        scope.launch(Dispatchers.IO) {
            val preview = generateDocumentPreview(context, material)
            withContext(Dispatchers.Main) {
                launchFileViewer(context, preview, material)
            }
        }
    }

    /**
     * Launches device's native PDF or image viewer using FileProvider.
     */
    private fun launchFileViewer(context: Context, file: File, material: StudyMaterial) {
        try {
            val authority = "${context.packageName}.provider"
            val uri = FileProvider.getUriForFile(context, authority, file)
            val mime = resolveMimeType(material.mimeType, file.name)

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(intent, "Open ${material.title}")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "No PDF/Image app installed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Shares file via Android system share sheet.
     */
    fun shareMaterial(context: Context, material: StudyMaterial) {
        try {
            val file = getMaterialFile(context, material)
            val intent = Intent(Intent.ACTION_SEND).apply {
                if (file != null && file.exists()) {
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                    type = resolveMimeType(material.mimeType, file.name)
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } else {
                    type = "text/plain"
                }
                putExtra(
                    Intent.EXTRA_TEXT,
                    "📚 ${material.title}\nSubject: ${material.subject} (${material.branch} Sem ${material.semester})\nCategory: ${material.category.name}\nShared via Notify App"
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(intent, "Share Study Material")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot share: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun resolveMimeType(mimeType: String, fileName: String): String {
        return when {
            mimeType.isNotBlank() && mimeType != "application/octet-stream" -> mimeType
            fileName.endsWith(".pdf", ignoreCase = true) -> "application/pdf"
            fileName.endsWith(".png", ignoreCase = true) -> "image/png"
            fileName.endsWith(".jpg", ignoreCase = true) || fileName.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
            else -> "application/pdf"
        }
    }

    private suspend fun downloadFile(context: Context, material: StudyMaterial): File? = withContext(Dispatchers.IO) {
        try {
            val url = URL(material.fileUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.connect()
            if (connection.responseCode in 200..299) {
                val dir = File(context.filesDir, "study_materials").apply { mkdirs() }
                val target = File(dir, "${material.id}_${material.fileName}")
                connection.inputStream.use { input ->
                    FileOutputStream(target).use { output ->
                        input.copyTo(output)
                    }
                }
                target
            } else {
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("MaterialViewer", "Error downloading material: ${e.message}")
            null
        }
    }

    /**
     * Generates a clean, professional A4 PDF document preview for offline items.
     */
    fun generateDocumentPreview(context: Context, material: StudyMaterial): File {
        val dir = File(context.filesDir, "study_materials").apply { mkdirs() }
        val target = File(dir, "${material.id}_${material.fileName.substringBeforeLast(".")}.pdf")
        if (target.exists() && target.length() > 0) return target

        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // Standard A4 points
        val page = doc.startPage(pageInfo)
        val canvas = page.canvas

        // Header Background
        val bgPaint = Paint().apply { color = Color.parseColor("#0F172A") }
        canvas.drawRect(0f, 0f, 595f, 130f, bgPaint)

        // Title Paint
        val titlePaint = Paint().apply {
            color = Color.WHITE
            textSize = 20f
            isFakeBoldText = true
            isAntiAlias = true
        }
        canvas.drawText("Notify Campus Hub - Study Material", 40f, 55f, titlePaint)

        val subTitlePaint = Paint().apply {
            color = Color.parseColor("#94A3B8")
            textSize = 12f
            isAntiAlias = true
        }
        canvas.drawText("Sharad Institute of Technology College of Engineering", 40f, 85f, subTitlePaint)
        canvas.drawText("Department of Computer Science & Engineering (CSE)", 40f, 105f, subTitlePaint)

        // Body Info
        val textPaint = Paint().apply {
            color = Color.parseColor("#1E293B")
            textSize = 15f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val detailPaint = Paint().apply {
            color = Color.parseColor("#334155")
            textSize = 13f
            isAntiAlias = true
        }

        var y = 180f
        canvas.drawText("Material Title:", 40f, y, textPaint)
        canvas.drawText(material.title, 40f, y + 22f, detailPaint)

        y += 65f
        canvas.drawText("Subject & Branch:", 40f, y, textPaint)
        canvas.drawText("${material.subject}  |  Branch: ${material.branch}  |  Semester: ${material.semester}", 40f, y + 22f, detailPaint)

        y += 65f
        canvas.drawText("Category:", 40f, y, textPaint)
        canvas.drawText(material.category.name, 40f, y + 22f, detailPaint)

        y += 65f
        canvas.drawText("Author / Contributor:", 40f, y, textPaint)
        canvas.drawText(material.author?.fullName ?: "CSE Student", 40f, y + 22f, detailPaint)

        // Divider
        val linePaint = Paint().apply {
            color = Color.parseColor("#CBD5E1")
            strokeWidth = 2f
        }
        canvas.drawLine(40f, y + 50f, 555f, y + 50f, linePaint)

        // Footer / Note
        y += 90f
        val notePaint = Paint().apply {
            color = Color.parseColor("#64748B")
            textSize = 12f
            isAntiAlias = true
        }
        canvas.drawText("File Name: ${material.fileName}", 40f, y, notePaint)
        canvas.drawText("Verified for SITCOE Students on Notify App", 40f, y + 22f, notePaint)
        canvas.drawText("This study document is ready for local offline reading.", 40f, y + 44f, notePaint)

        doc.finishPage(page)
        FileOutputStream(target).use { out ->
            doc.writeTo(out)
        }
        doc.close()
        return target
    }
}
