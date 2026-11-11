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
import com.blocker.MainActivity
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
        const val GITHUB_WEB_LATEST_URL = "https://github.com/shell-ninja/Blocker-App/releases/latest"
        const val GITHUB_WEB_ATOM_URL = "https://github.com/shell-ninja/Blocker-App/releases.atom"
        const val GITHUB_RELEASES_BASE = "https://github.com/shell-ninja/Blocker-App/releases"
        private const val AUTO_CHECK_COOLDOWN_MS = 10 * 60_000L

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

        /**
         * Resolves the latest release tag via GitHub's non-API web redirect
         * (`https://github.com/.../releases/latest` -> `302 Location: .../releases/tag/<tag>`),
         * falling back to the releases Atom feed if no redirect Location is returned.
         */
        fun resolveLatestTagViaWeb(): String? {
            runCatching {
                val conn = (URL(GITHUB_WEB_LATEST_URL).openConnection() as HttpURLConnection).apply {
                    requestMethod = "HEAD"
                    instanceFollowRedirects = false
                    connectTimeout = 12000
                    readTimeout = 12000
                    setRequestProperty("User-Agent", "Blocker-App")
                }
                val code = conn.responseCode
                val loc = conn.getHeaderField("Location").orEmpty()
                val finalUrl = if (loc.isNotBlank()) loc else conn.url?.toString().orEmpty()
                conn.disconnect()
                if (code in 300..399 || finalUrl.contains("/releases/tag/")) {
                    val tag = finalUrl.substringAfter("/releases/tag/", "").substringBefore('?').substringBefore('#').trim()
                    if (tag.isNotEmpty()) return tag
                }
            }
            return runCatching {
                val xml = httpGetText(GITHUB_WEB_ATOM_URL)
                val hrefMatch = Regex("""href="https?://github\.com/[^"]+/releases/tag/([^"]+)"""").find(xml)
                hrefMatch?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
            }.getOrNull()
        }

        private fun httpGetText(urlStr: String, headers: Map<String, String> = emptyMap()): String {
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 12000
                readTimeout = 12000
                setRequestProperty("User-Agent", "Blocker-App")
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
            }
            try {
                val code = conn.responseCode
                if (code !in 200..299) {
                    throw IllegalStateException("GitHub returned HTTP $code")
                }
                return conn.inputStream.bufferedReader().use { it.readText() }
            } finally {
                conn.disconnect()
            }
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
            MainActivity.isLaunchingExternal = true
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    fun checkForUpdate(manual: Boolean = false) {
        if (_updateState.value.checking) return

        val prefs = BlockerStore.prefs(context)
        val isCurrentDebug = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val cachedReleaseJson = prefs.getString("ui_update_cached_release", null)
        val cachedForDebug = prefs.getBoolean("ui_update_cached_is_debug", isCurrentDebug)
        val lastCheckedAt = prefs.getLong("ui_update_last_check_ts", 0L)

        if (!manual &&
            cachedReleaseJson != null &&
            cachedForDebug == isCurrentDebug &&
            (System.currentTimeMillis() - lastCheckedAt) < AUTO_CHECK_COOLDOWN_MS
        ) {
            runCatching {
                val rel = parseCachedRelease(JSONObject(cachedReleaseJson))
                publishRelease(rel, saveToCache = false, isCurrentDebug = isCurrentDebug)
                return
            }
        }

        _updateState.value = _updateState.value.copy(checking = true, error = null)

        scope.launch {
            try {
                val cachedApiJson = prefs.getString("ui_update_raw_api_json", null)
                val cachedEtag = if (cachedApiJson != null) prefs.getString("ui_update_etag", null) else null
                val release = fetchReleaseWithFallback(isCurrentDebug, cachedEtag, cachedApiJson)
                publishRelease(release, saveToCache = true, isCurrentDebug = isCurrentDebug)
            } catch (e: Exception) {
                _updateState.value = _updateState.value.copy(
                    checking = false,
                    error = e.localizedMessage ?: "Failed to check for updates"
                )
            }
        }
    }

    private fun publishRelease(
        release: ReleaseInfo,
        saveToCache: Boolean = true,
        isCurrentDebug: Boolean = false
    ) {
        if (saveToCache) {
            val serialized = JSONObject()
                .put("version", release.version)
                .put("tagName", release.tagName)
                .put("name", release.name)
                .put("body", release.body)
                .put("publishedAt", release.publishedAt)
                .put("downloadUrl", release.downloadUrl)
                .put("apkName", release.apkName)
                .put("sizeBytes", release.sizeBytes)
                .put("htmlUrl", release.htmlUrl)
                .toString()
            BlockerStore.prefs(context).edit()
                .putString("ui_update_cached_release", serialized)
                .putBoolean("ui_update_cached_is_debug", isCurrentDebug)
                .putLong("ui_update_last_check_ts", System.currentTimeMillis())
                .apply()
        }

        val hasUpdate = compareSemVer(release.version, BlockerStore.APP_VERSION) > 0
        val targetFile = File(getUpdatesDir(), "Blocker-${release.version}.apk")
        val isDownloaded = targetFile.exists() && (release.sizeBytes <= 0 || targetFile.length() == release.sizeBytes)

        _updateState.value = _updateState.value.copy(
            checking = false,
            hasUpdate = hasUpdate,
            release = release,
            isDownloaded = isDownloaded,
            error = null,
            lastCheckedAt = System.currentTimeMillis()
        )
    }

    private fun parseCachedRelease(obj: JSONObject): ReleaseInfo = ReleaseInfo(
        version = obj.getString("version"),
        tagName = obj.getString("tagName"),
        name = obj.optString("name", obj.getString("tagName")),
        body = obj.optString("body", "No release notes provided."),
        publishedAt = obj.optString("publishedAt", ""),
        downloadUrl = obj.getString("downloadUrl"),
        apkName = obj.optString("apkName", ""),
        sizeBytes = obj.optLong("sizeBytes", 0L),
        htmlUrl = obj.optString("htmlUrl", GITHUB_RELEASES_BASE)
    )

    private fun fetchReleaseWithFallback(
        isCurrentDebug: Boolean,
        cachedEtag: String?,
        cachedApiJson: String?
    ): ReleaseInfo {
        val prefs = BlockerStore.prefs(context)
        val conn = (URL(GITHUB_API_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12000
            readTimeout = 12000
            setRequestProperty("Accept", "application/vnd.github.v3+json")
            setRequestProperty("User-Agent", "Blocker-App")
            if (!cachedEtag.isNullOrBlank()) {
                setRequestProperty("If-None-Match", cachedEtag)
            }
        }

        val responseCode = try {
            conn.responseCode
        } catch (e: Exception) {
            conn.disconnect()
            return fetchReleaseViaWeb(isCurrentDebug) ?: throw e
        }

        if (responseCode == HttpURLConnection.HTTP_NOT_MODIFIED && cachedApiJson != null) {
            conn.disconnect()
            prefs.edit().putLong("ui_update_last_check_ts", System.currentTimeMillis()).apply()
            return parseReleaseFromApiJson(JSONObject(cachedApiJson), isCurrentDebug)
        }

        if (responseCode == 403 || responseCode == 429) {
            conn.disconnect()
            return fetchReleaseViaWeb(isCurrentDebug)
                ?: throw IllegalStateException("GitHub API rate limit exceeded (HTTP $responseCode) and web fallback failed.")
        }

        if (responseCode !in 200..299) {
            conn.disconnect()
            throw IllegalStateException("GitHub API returned HTTP $responseCode")
        }

        val responseText = conn.inputStream.bufferedReader().use { it.readText() }
        val etag = conn.getHeaderField("ETag")
        conn.disconnect()

        val editor = prefs.edit()
            .putString("ui_update_raw_api_json", responseText)
        if (!etag.isNullOrBlank()) {
            editor.putString("ui_update_etag", etag)
        }
        editor.apply()

        return parseReleaseFromApiJson(JSONObject(responseText), isCurrentDebug)
    }

    private fun parseReleaseFromApiJson(json: JSONObject, isCurrentDebug: Boolean): ReleaseInfo {
        val tagName = json.optString("tag_name", "")
        val version = tagName.replace(Regex("^[vV]"), "")
        val name = json.optString("name", tagName)
        val body = json.optString("body", "No release notes provided.")
        val publishedAt = json.optString("published_at", "")
        val htmlUrl = json.optString("html_url", "$GITHUB_RELEASES_BASE/tag/$tagName")

        val assets = json.optJSONArray("assets") ?: JSONArray()
        val candidates = mutableListOf<Triple<String, String, Long>>()
        for (i in 0 until assets.length()) {
            val a = assets.optJSONObject(i) ?: continue
            val aName = a.optString("name", "")
            val dlUrl = a.optString("browser_download_url", "")
            val sz = a.optLong("size", 0L)
            if (aName.endsWith(".apk", ignoreCase = true) && dlUrl.isNotBlank()) {
                candidates.add(Triple(aName, dlUrl, sz))
            }
        }

        val chosen = pickBestApkAsset(candidates, isCurrentDebug)
            ?: throw IllegalStateException(
                if (!isCurrentDebug) "No official release APK attached to this GitHub release."
                else "No APK asset attached to the latest release."
            )

        return ReleaseInfo(
            version = version,
            tagName = tagName,
            name = name,
            body = body,
            publishedAt = publishedAt,
            downloadUrl = chosen.second,
            apkName = chosen.first,
            sizeBytes = chosen.third,
            htmlUrl = htmlUrl
        )
    }

    private fun fetchReleaseViaWeb(isCurrentDebug: Boolean): ReleaseInfo? {
        val tagName = resolveLatestTagViaWeb() ?: return null
        val version = tagName.replace(Regex("^[vV]"), "")
        val htmlUrl = "$GITHUB_RELEASES_BASE/tag/$tagName"

        val assetsHtml = runCatching {
            httpGetText("$GITHUB_RELEASES_BASE/expanded_assets/$tagName")
        }.getOrNull().orEmpty()

        val candidates = mutableListOf<Triple<String, String, Long>>()
        val liRegex = Regex("<li[^>]*>(.*?)</li>", RegexOption.DOT_MATCHES_ALL)
        val hrefRegex = Regex("""href="(/shell-ninja/Blocker-App/releases/download/[^"]+\.apk)"""")
        for (liMatch in liRegex.findAll(assetsHtml)) {
            val liBlock = liMatch.groupValues[1]
            val hrefMatch = hrefRegex.find(liBlock) ?: continue
            val relPath = hrefMatch.groupValues[1]
            val apkName = relPath.substringAfterLast('/').substringBefore('?')
            val dlUrl = "https://github.com$relPath"
            candidates.add(Triple(apkName, dlUrl, 0L))
        }

        val chosen = pickBestApkAsset(candidates, isCurrentDebug)
            ?: return null

        val exactSize = resolveRemoteContentLength(chosen.second)
        val (atomTitle, atomPublished, atomBody) = fetchAtomMetadataForTag(tagName)

        return ReleaseInfo(
            version = version,
            tagName = tagName,
            name = atomTitle.ifBlank { tagName },
            body = atomBody.ifBlank { "No release notes provided." },
            publishedAt = atomPublished,
            downloadUrl = chosen.second,
            apkName = chosen.first,
            sizeBytes = exactSize,
            htmlUrl = htmlUrl
        )
    }

    private fun pickBestApkAsset(
        candidates: List<Triple<String, String, Long>>,
        isCurrentDebug: Boolean
    ): Triple<String, String, Long>? {
        var chosen: Triple<String, String, Long>? = null
        for (item in candidates) {
            val aName = item.first
            if (!isCurrentDebug) {
                if (!aName.contains("debug", ignoreCase = true)) {
                    chosen = item
                    break
                }
            } else {
                if (chosen == null || !aName.contains("debug", ignoreCase = true)) {
                    chosen = item
                }
            }
        }
        return chosen
    }

    private fun resolveRemoteContentLength(urlStr: String): Long = runCatching {
        var currentUrl = urlStr
        repeat(5) {
            val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "HEAD"
                instanceFollowRedirects = false
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("User-Agent", "Blocker-App-Android")
            }
            val code = conn.responseCode
            val loc = conn.getHeaderField("Location")
            val len = conn.contentLengthLong
            conn.disconnect()
            if (code in listOf(301, 302, 303, 307, 308) && !loc.isNullOrBlank()) {
                currentUrl = loc
            } else {
                return@runCatching if (len > 0L) len else 0L
            }
        }
        0L
    }.getOrDefault(0L)

    private fun fetchAtomMetadataForTag(tagName: String): Triple<String, String, String> = runCatching {
        val xml = httpGetText(GITHUB_WEB_ATOM_URL)
        val entries = Regex("<entry>(.*?)</entry>", RegexOption.DOT_MATCHES_ALL)
            .findAll(xml)
            .map { it.groupValues[1] }
            .toList()
        val matched = entries.firstOrNull {
            it.contains("/tag/$tagName\"") || it.contains("<title>$tagName</title>")
        } ?: entries.firstOrNull() ?: return@runCatching Triple("", "", "")

        val title = Regex("<title[^>]*>(.*?)</title>", RegexOption.DOT_MATCHES_ALL)
            .find(matched)?.groupValues?.getOrNull(1)?.let { unescapeXml(it) }?.trim().orEmpty()
        val updated = Regex("<updated[^>]*>(.*?)</updated>", RegexOption.DOT_MATCHES_ALL)
            .find(matched)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        val rawContent = Regex("<content[^>]*>(.*?)</content>", RegexOption.DOT_MATCHES_ALL)
            .find(matched)?.groupValues?.getOrNull(1).orEmpty()

        Triple(title, updated, htmlNotesToReadableMarkdown(rawContent))
    }.getOrDefault(Triple("", "", ""))

    private fun unescapeXml(s: String): String = s
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&amp;", "&")

    private fun htmlNotesToReadableMarkdown(raw: String): String {
        val unescaped = unescapeXml(raw)
        val withStructure = unescaped
            .replace(Regex("<h[1-6][^>]*>", RegexOption.IGNORE_CASE), "\n## ")
            .replace(Regex("</h[1-6]>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<li[^>]*>", RegexOption.IGNORE_CASE), "\n- ")
            .replace(Regex("</p>|<br\\s*/?>|</div>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<[^>]+>"), "")
        return unescapeXml(withStructure)
            .lines()
            .joinToString("\n") { it.trimEnd() }
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
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
            MainActivity.isLaunchingExternal = true
            context.startActivity(installIntent)
        } catch (e: Exception) {
            _updateState.value = _updateState.value.copy(
                error = "Failed to launch installer: ${e.localizedMessage}"
            )
        }
    }
}
