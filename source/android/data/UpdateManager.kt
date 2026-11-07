package com.blocker.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.blocker.BlockerStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseInfo(
    val version: String,
    val tagName: String,
    val name: String,
    val body: String,
    val publishedAt: String,
    val downloadUrl: String,
    val apkName: String,
    val sizeBytes: Long,
    val htmlUrl: String
)

data class UpdateState(
    val checking: Boolean = false,
    val hasUpdate: Boolean = false,
    val release: ReleaseInfo? = null,
    val downloading: Boolean = false,
    val downloadProgress: Float = 0f, // 0.0 to 1.0
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val isDownloaded: Boolean = false,
    val error: String? = null,
    val lastCheckedAt: Long = 0L
)

class UpdateManager(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _updateState = MutableStateFlow(UpdateState())
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    companion object {
        const val GITHUB_API_URL = "https://api.github.com/repos/shell-ninja/Blocker-App/releases/latest"

        fun compareSemVer(v1: String, v2: String): Int {
            val parse: (String) -> List<Int> = { v ->
                v.replace(Regex("^[vV]"), "")
                    .split(Regex("[-+]"))[0]
                    .split(".")
                    .map { it.toIntOrNull() ?: 0 }
            }
            val p1 = parse(v1)
            val p2 = parse(v2)
            val maxLen = maxOf(p1.size, p2.size)
            for (i in 0 until maxLen) {
                val num1 = p1.getOrElse(i) { 0 }
                val num2 = p2.getOrElse(i) { 0 }
                if (num1 > num2) return 1
                if (num1 < num2) return -1
            }
            return 0
        }
    }

    private fun getUpdatesDir(): File {
        val dir = File(context.cacheDir, "updates")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getCacheStats(): Pair<Int, Long> {
        val dir = getUpdatesDir()
        val files = dir.listFiles { f -> f.extension.equals("apk", ignoreCase = true) } ?: return Pair(0, 0L)
        val totalBytes = files.sumOf { it.length() }
        return Pair(files.size, totalBytes)
    }

    fun clearUpdateCache(): Long {
        val dir = getUpdatesDir()
        var freed = 0L
        dir.listFiles()?.forEach { file ->
            freed += file.length()
            file.delete()
        }
        _updateState.value = _updateState.value.copy(isDownloaded = false)
        return freed
    }

    fun canRequestPackageInstalls(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun openInstallSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    fun checkForUpdate(manual: Boolean = false) {
        if (_updateState.value.checking) return

        _updateState.value = _updateState.value.copy(checking = true, error = null)

        scope.launch {
            try {
                val url = URL(GITHUB_API_URL)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 12000
                    readTimeout = 12000
                    setRequestProperty("Accept", "application/vnd.github.v3+json")
                    setRequestProperty("User-Agent", "Blocker-App")
                }

                val responseCode = conn.responseCode
                if (responseCode !in 200..299) {
                    throw IllegalStateException("GitHub API returned HTTP $responseCode")
                }

                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()

                val json = JSONObject(responseText)
                val tagName = json.optString("tag_name", "")
                val version = tagName.replace(Regex("^[vV]"), "")
                val name = json.optString("name", tagName)
                val body = json.optString("body", "No release notes provided.")
                val publishedAt = json.optString("published_at", "")
                val htmlUrl = json.optString("html_url", "https://github.com/shell-ninja/Blocker-App/releases")

                val isCurrentDebug = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

                val assets = json.optJSONArray("assets") ?: JSONArray()
                var chosenAsset: JSONObject? = null

                for (i in 0 until assets.length()) {
                    val a = assets.optJSONObject(i) ?: continue
                    val aName = a.optString("name", "")
                    if (aName.endsWith(".apk", ignoreCase = true)) {
                        if (!isCurrentDebug) {
                            if (!aName.contains("debug", ignoreCase = true)) {
                                chosenAsset = a
                                break
                            }
                        } else {
                            if (chosenAsset == null || !aName.contains("debug", ignoreCase = true)) {
                                chosenAsset = a
                            }
                        }
                    }
                }

                if (chosenAsset == null) {
                    throw IllegalStateException(
                        if (!isCurrentDebug) "No official release APK attached to this GitHub release."
                        else "No APK asset attached to the latest release."
                    )
                }

                val downloadUrl = chosenAsset.optString("browser_download_url", "")
                val apkName = chosenAsset.optString("name", "Blocker-$version.apk")
                val sizeBytes = chosenAsset.optLong("size", 0L)

                val release = ReleaseInfo(
                    version = version,
                    tagName = tagName,
                    name = name,
                    body = body,
                    publishedAt = publishedAt,
                    downloadUrl = downloadUrl,
                    apkName = apkName,
                    sizeBytes = sizeBytes,
                    htmlUrl = htmlUrl
                )

                val hasUpdate = compareSemVer(version, BlockerStore.APP_VERSION) > 0

                // Check if already downloaded
                val targetFile = File(getUpdatesDir(), "Blocker-$version.apk")
                val isDownloaded = targetFile.exists() && (sizeBytes <= 0 || targetFile.length() == sizeBytes)

                _updateState.value = _updateState.value.copy(
                    checking = false,
                    hasUpdate = hasUpdate,
                    release = release,
                    isDownloaded = isDownloaded,
                    error = null,
                    lastCheckedAt = System.currentTimeMillis()
                )
            } catch (e: Exception) {
                _updateState.value = _updateState.value.copy(
                    checking = false,
                    error = e.localizedMessage ?: "Failed to check for updates"
                )
            }
        }
    }

    fun downloadAndInstall() {
        val rel = _updateState.value.release ?: return
        if (_updateState.value.downloading) return

        val targetFile = File(getUpdatesDir(), "Blocker-${rel.version}.apk")

        if (targetFile.exists() && (rel.sizeBytes <= 0L || targetFile.length() == rel.sizeBytes)) {
            _updateState.value = _updateState.value.copy(isDownloaded = true)
            launchInstallIntent(targetFile)
            return
        }

        _updateState.value = _updateState.value.copy(
            downloading = true,
            downloadProgress = 0.01f,
            downloadedBytes = 0L,
            totalBytes = rel.sizeBytes,
            isDownloaded = false,
            error = null
        )

        scope.launch {
            try {
                if (targetFile.exists()) targetFile.delete()

                var conn = (URL(rel.downloadUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 30000
                    readTimeout = 30000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "Blocker-App-Android")
                }
                conn.connect()

                var responseCode = conn.responseCode
                var redirects = 0
                while ((responseCode in listOf(
                        HttpURLConnection.HTTP_MOVED_PERM,
                        HttpURLConnection.HTTP_MOVED_TEMP,
                        HttpURLConnection.HTTP_SEE_OTHER,
                        307, 308
                    )) && redirects < 5
                ) {
                    val newUrl = conn.getHeaderField("Location") ?: break
                    conn.disconnect()
                    conn = (URL(newUrl).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 30000
                        readTimeout = 30000
                        setRequestProperty("User-Agent", "Blocker-App-Android")
                    }
                    conn.connect()
                    responseCode = conn.responseCode
                    redirects++
                }

                if (responseCode !in 200..299) {
                    throw IllegalStateException("Server returned HTTP $responseCode")
                }

                val totalBytes = conn.contentLength.toLong().let { if (it > 0) it else rel.sizeBytes }
                var downloaded = 0L
                val input = BufferedInputStream(conn.inputStream)
                val output = FileOutputStream(targetFile)
                val buffer = ByteArray(16384)
                var bytesRead: Int
                var lastProgressUpdate = 0L

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    downloaded += bytesRead

                    val now = System.currentTimeMillis()
                    if (now - lastProgressUpdate > 100 || (totalBytes > 0 && downloaded == totalBytes)) {
                        lastProgressUpdate = now
                        val progress = if (totalBytes > 0) (downloaded.toFloat() / totalBytes) else 0.5f
                        _updateState.value = _updateState.value.copy(
                            downloadProgress = progress.coerceIn(0f, 1f),
                            downloadedBytes = downloaded,
                            totalBytes = totalBytes
                        )
                    }
                }

                output.flush()
                output.close()
                input.close()
                conn.disconnect()

                _updateState.value = _updateState.value.copy(
                    downloading = false,
                    isDownloaded = true,
                    downloadProgress = 1f,
                    downloadedBytes = downloaded,
                    totalBytes = downloaded
                )

                launchInstallIntent(targetFile)
            } catch (e: Exception) {
                if (targetFile.exists()) targetFile.delete()
                _updateState.value = _updateState.value.copy(
                    downloading = false,
                    isDownloaded = false,
                    error = e.localizedMessage ?: "Download failed"
                )
            }
        }
    }

    fun launchInstallIntent(apkFile: File) {
        try {
            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            _updateState.value = _updateState.value.copy(
                error = "Failed to launch installer: ${e.localizedMessage}"
            )
        }
    }
}
