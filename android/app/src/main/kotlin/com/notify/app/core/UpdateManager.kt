package com.notify.app.core

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

data class AppUpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val title: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val isUpdateAvailable: Boolean,
    val currentVersionName: String,
    val currentVersionCode: Int
)

object UpdateManager {
    private const val TAG = "UpdateManager"
    private const val REMOTE_VERSION_URL = "https://raw.githubusercontent.com/Nick-0-7/notify-app/main/version.json"
    private const val GITHUB_LATEST_RELEASE_API = "https://api.github.com/repos/Nick-0-7/notify-app/releases/latest"

    fun getCurrentVersion(context: Context): Pair<String, Int> {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode
            }
            val name = packageInfo.versionName ?: "1.0"
            Pair(name, code)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get current app version", e)
            Pair("1.0", 1)
        }
    }

    suspend fun checkForUpdates(context: Context): AppUpdateInfo? = withContext(Dispatchers.IO) {
        val (currentName, currentCode) = getCurrentVersion(context)
        Log.d(TAG, "Current app version: $currentName ($currentCode)")

        // 1. Try raw repository version.json first
        try {
            val cacheBusterUrl = "$REMOTE_VERSION_URL?nocache=${System.currentTimeMillis()}"
            val connection = URL(cacheBusterUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "Notify-Android-App")
            connection.setRequestProperty("Cache-Control", "no-cache")

            if (connection.responseCode == 200) {
                val jsonString = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(jsonString)

                val remoteCode = json.optInt("versionCode", currentCode)
                val remoteName = json.optString("versionName", currentName)
                val title = json.optString("title", "New Update Available")
                val notes = json.optString("releaseNotes", "Performance improvements and bug fixes.")
                val downloadUrl = json.optString("downloadUrl", "")

                val isNewer = remoteCode > currentCode || isVersionGreater(remoteName, currentName)
                if (isNewer) {
                    return@withContext AppUpdateInfo(
                        versionCode = remoteCode,
                        versionName = remoteName,
                        title = title,
                        releaseNotes = notes,
                        downloadUrl = downloadUrl,
                        isUpdateAvailable = true,
                        currentVersionName = currentName,
                        currentVersionCode = currentCode
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed fetching version.json: ${e.message}")
        }

        // 2. Fallback: Check GitHub Releases API
        try {
            val ghConn = URL(GITHUB_LATEST_RELEASE_API).openConnection() as HttpURLConnection
            ghConn.connectTimeout = 8000
            ghConn.readTimeout = 8000
            ghConn.requestMethod = "GET"
            ghConn.setRequestProperty("User-Agent", "Notify-Android-App")

            if (ghConn.responseCode == 200) {
                val ghJsonStr = ghConn.inputStream.bufferedReader().use { it.readText() }
                val ghJson = JSONObject(ghJsonStr)
                val tagName = ghJson.optString("tag_name", "").removePrefix("v")
                val releaseName = ghJson.optString("name", "Notify Update")
                val body = ghJson.optString("body", "What's new in this release.")
                
                var apkUrl = ""
                val assets = ghJson.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.optString("name", "")
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            apkUrl = asset.optString("browser_download_url", "")
                            break
                        }
                    }
                }

                val isNewer = isVersionGreater(tagName, currentName)
                return@withContext AppUpdateInfo(
                    versionCode = currentCode + (if (isNewer) 1 else 0),
                    versionName = tagName.ifBlank { currentName },
                    title = releaseName,
                    releaseNotes = body,
                    downloadUrl = apkUrl,
                    isUpdateAvailable = isNewer && apkUrl.isNotBlank(),
                    currentVersionName = currentName,
                    currentVersionCode = currentCode
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed fetching GitHub releases: ${e.message}")
        }

        return@withContext AppUpdateInfo(
            versionCode = currentCode,
            versionName = currentName,
            title = "Notify App",
            releaseNotes = "You have the latest version.",
            downloadUrl = "",
            isUpdateAvailable = false,
            currentVersionName = currentName,
            currentVersionCode = currentCode
        )
    }

    private fun isVersionGreater(remote: String, local: String): Boolean {
        if (remote.isBlank() || local.isBlank()) return false
        val rParts = remote.split(".").mapNotNull { it.toIntOrNull() }
        val lParts = local.split(".").mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(rParts.size, lParts.size)
        for (i in 0 until maxLen) {
            val r = rParts.getOrElse(i) { 0 }
            val l = lParts.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }
        return false
    }

    suspend fun downloadApk(
        context: Context,
        downloadUrl: String,
        onProgress: (Float) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        var currentConnection: HttpURLConnection? = null
        try {
            val updatesDir = File(context.cacheDir, "updates")
            if (!updatesDir.exists()) updatesDir.mkdirs()

            val outputFile = File(updatesDir, "Notify-update.apk")
            if (outputFile.exists()) outputFile.delete()

            var currentUrl = downloadUrl
            var redirectCount = 0
            val maxRedirects = 8

            while (true) {
                val url = URL(currentUrl)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 20000
                    readTimeout = 35000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                    setRequestProperty("Accept", "*/*")
                }

                val code = conn.responseCode
                Log.d(TAG, "HTTP $code from $currentUrl")

                if (code in 300..308 && code != 304) {
                    val location = conn.getHeaderField("Location")
                    conn.disconnect()
                    if (!location.isNullOrBlank() && redirectCount < maxRedirects) {
                        currentUrl = if (location.startsWith("http://") || location.startsWith("https://")) {
                            location
                        } else {
                            URL(url, location).toString()
                        }
                        redirectCount++
                        continue
                    } else {
                        return@withContext Result.failure(java.io.IOException("Too many redirects or missing Location header (HTTP $code)"))
                    }
                }

                if (code != HttpURLConnection.HTTP_OK && code != HttpURLConnection.HTTP_PARTIAL) {
                    conn.disconnect()
                    return@withContext Result.failure(java.io.IOException("Server returned HTTP $code: ${conn.responseMessage}"))
                }

                currentConnection = conn
                break
            }

            val conn = currentConnection ?: throw java.io.IOException("Unable to establish connection")
            val totalBytes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                conn.contentLengthLong.takeIf { it > 0 } ?: conn.contentLength.toLong()
            } else {
                conn.contentLength.toLong()
            }
            var downloadedBytes = 0L

            conn.inputStream.use { input: InputStream ->
                FileOutputStream(outputFile).use { output: FileOutputStream ->
                    val buffer = ByteArray(32768)
                    var bytesRead: Int
                    var lastProgressUpdate = 0L
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        val now = System.currentTimeMillis()
                        if (totalBytes > 0 && (now - lastProgressUpdate > 100 || downloadedBytes == totalBytes)) {
                            lastProgressUpdate = now
                            val progress = (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                            withContext(Dispatchers.Main) {
                                onProgress(progress)
                            }
                        }
                    }
                    output.flush()
                }
            }

            withContext(Dispatchers.Main) {
                onProgress(1f)
            }
            Result.success(outputFile)
        } catch (e: Exception) {
            Log.e(TAG, "Failed downloading update APK from $downloadUrl", e)
            Result.failure(e)
        } finally {
            currentConnection?.disconnect()
        }
    }

    fun promptInstall(context: Context, apkFile: File): Boolean {
        return try {
            // Check unknown sources installation permission on Android 8+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(settingsIntent)
                    return false
                }
            }

            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch package installer", e)
            false
        }
    }
}
