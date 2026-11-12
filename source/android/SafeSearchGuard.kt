package com.blocker

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.net.URI
import java.net.URLDecoder
import java.util.ArrayDeque

data class SafeSearchViolation(
    val englishReason: String,
    val banglaReason: String,
    val triggerBadge: String
)

/**
 * Local Stage-0 SafeSearch & search query guard for major search engines.
 * Inspects SafeSearch-off URL parameters, search settings pages, and decoded search queries.
 */
object SafeSearchGuard {

    private val GOOGLE_HOST_REGEX = Regex("^(?:[a-z0-9-]+\\.)*google\\.[a-z.]{2,}$")
    private val YANDEX_HOST_REGEX = Regex("^(?:[a-z0-9-]+\\.)*yandex\\.[a-z.]{2,}$")

    /**
     * Parses a raw address bar string into normalized (host, pathLower, queryPairs).
     * Returns null when the text does not look like a URL or belongs to a non-search host.
     */
    fun parseSearchEngineUrl(rawUrl: String): ParsedSearchUrl? {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty() || trimmed.contains(' ')) return null
        if (!trimmed.contains('.') && !trimmed.contains("://")) return null

        val withScheme = if (trimmed.contains("://")) trimmed else "https://$trimmed"
        val uri = runCatching { URI(withScheme) }.getOrNull() ?: return null
        val host = (uri.host ?: return null).lowercase().removePrefix("www.")
        val engine = identifyEngine(host) ?: return null

        val pathLower = (uri.rawPath ?: "").lowercase()
        val rawQuery = uri.rawQuery ?: ""
        val params = ArrayList<Pair<String, String>>()
        if (rawQuery.isNotEmpty()) {
            for (part in rawQuery.split('&')) {
                if (part.isEmpty()) continue
                val eq = part.indexOf('=')
                val rawKey = if (eq >= 0) part.substring(0, eq) else part
                val rawVal = if (eq >= 0) part.substring(eq + 1) else ""
                val key = decodeComponent(rawKey).trim().lowercase()
                val value = decodeComponent(rawVal).trim()
                if (key.isNotEmpty()) {
                    params.add(key to value)
                }
            }
        }
        return ParsedSearchUrl(engine, host, pathLower, params)
    }

    enum class SearchEngine {
        GOOGLE, BING, DUCKDUCKGO, YAHOO, BRAVE, YANDEX, ECOSIA
    }

    data class ParsedSearchUrl(
        val engine: SearchEngine,
        val host: String,
        val pathLower: String,
        val params: List<Pair<String, String>>
    ) {
        fun isSettingsPage(): Boolean = when (engine) {
            SearchEngine.GOOGLE -> pathLower.contains("/safesearch") || pathLower.contains("/preferences")
            SearchEngine.BING -> pathLower.contains("/account/general")
            SearchEngine.DUCKDUCKGO -> pathLower.contains("/settings")
            SearchEngine.BRAVE -> pathLower.contains("/settings")
            SearchEngine.YAHOO -> pathLower.contains("/preferences")
            SearchEngine.YANDEX -> pathLower.contains("/tune") || pathLower.contains("/settings")
            SearchEngine.ECOSIA -> pathLower.contains("/settings")
        }
    }

    private fun identifyEngine(host: String): SearchEngine? = when {
        host == "bing.com" || host.endsWith(".bing.com") -> SearchEngine.BING
        host == "duckduckgo.com" || host.endsWith(".duckduckgo.com") -> SearchEngine.DUCKDUCKGO
        host == "search.yahoo.com" || host.endsWith(".search.yahoo.com") -> SearchEngine.YAHOO
        host == "search.brave.com" -> SearchEngine.BRAVE
        host == "ecosia.org" || host.endsWith(".ecosia.org") -> SearchEngine.ECOSIA
        GOOGLE_HOST_REGEX.matches(host) -> SearchEngine.GOOGLE
        YANDEX_HOST_REGEX.matches(host) -> SearchEngine.YANDEX
        else -> null
    }

    /**
     * Checks Stage-0 search engine URL rules:
     * a) Explicit SafeSearch-Off parameters (safe=off, adlt=off, kp=-2, etc.)
     * c) Decoded search query (`q=`, `p=`, `text=`) against native binary engine & custom keywords.
     */
    fun inspectSearchUrl(
        rawUrl: String,
        nativeMatcher: (String) -> String?,
        customKeywordHit: (String) -> String?,
        whitelistShieldedCheck: (String, String) -> Boolean
    ): SafeSearchViolation? {
        val parsed = parseSearchEngineUrl(rawUrl) ?: return null

        // 3(a) Explicit SafeSearch-Off URL Parameters
        for ((k, rawVal) in parsed.params) {
            val v = rawVal.lowercase()
            val unsafeParam = when {
                parsed.engine == SearchEngine.GOOGLE && k == "safe" && (v == "off" || v == "0" || v == "images") -> "safe=$v"
                parsed.engine == SearchEngine.BING && k == "adlt" && (v == "off" || v == "demote") -> "adlt=$v"
                parsed.engine == SearchEngine.DUCKDUCKGO && k == "kp" && (v == "-2" || v == "-1") -> "kp=$v"
                parsed.engine == SearchEngine.YAHOO && k == "vm" && v == "p" -> "vm=p"
                parsed.engine == SearchEngine.BRAVE && k == "safesearch" && v == "off" -> "safesearch=off"
                parsed.engine == SearchEngine.YANDEX && k == "family" && (v == "0" || v == "no") -> "family=$v"
                parsed.engine == SearchEngine.YANDEX && k == "fyandex" && v == "0" -> "fyandex=0"
                else -> null
            }
            if (unsafeParam != null) {
                return SafeSearchViolation(
                    englishReason = "Unsafe search filter ($unsafeParam)",
                    banglaReason = "আনসেফ সার্চ ফিল্টার ($unsafeParam)",
                    triggerBadge = unsafeParam
                )
            }
        }

        // 3(c) Search Query Parameter (q=, p=, text=) Deep Inspection
        for ((k, rawVal) in parsed.params) {
            if (k != "q" && k != "p" && k != "text") continue
            val queryNormalized = rawVal.replace('+', ' ').trim().lowercase()
            if (queryNormalized.isEmpty()) continue

            val nativeRule = nativeMatcher(queryNormalized)
            if (nativeRule != null && !whitelistShieldedCheck(queryNormalized, nativeRule)) {
                return SafeSearchViolation(
                    englishReason = "Blocked search keyword \"$nativeRule\"",
                    banglaReason = "নিষিদ্ধ সার্চ কীওয়ার্ড: \"$nativeRule\"",
                    triggerBadge = nativeRule
                )
            }

            val customRule = customKeywordHit(queryNormalized)
            if (customRule != null) {
                return SafeSearchViolation(
                    englishReason = "Blocked search keyword \"$customRule\"",
                    banglaReason = "নিষিদ্ধ সার্চ কীওয়ার্ড: \"$customRule\"",
                    triggerBadge = customRule
                )
            }
        }

        return null
    }

    /**
     * 3(b) Detects attempts to disable SafeSearch (selecting Off or Blur) on SafeSearch settings pages.
     */
    fun isSafeSearchDisableAttempt(
        ev: AccessibilityEvent?,
        root: AccessibilityNodeInfo?,
        currentUrl: String
    ): Boolean {
        val parsed = parseSearchEngineUrl(currentUrl) ?: return false
        if (!parsed.isSettingsPage()) return false

        // 1. User directly tapped "Off" or "Blur" (or radio item containing them) on the settings page
        if (ev?.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            val evCombined = ((ev.text.joinToString(" ")) + " " + (ev.contentDescription ?: "")).trim().lowercase()
            if (isUnsafeSafeSearchOptionLabel(evCombined)) {
                return true
            }
            val src = ev.source
            if (src != null) {
                try {
                    val srcTxt = ((src.safeText()?.toString().orEmpty()) + " " + (src.safeDesc()?.toString().orEmpty())).trim().lowercase()
                    if (isUnsafeSafeSearchOptionLabel(srcTxt)) return true
                    val childCount = src.safeChildCount()
                    for (i in 0 until minOf(childCount, 8)) {
                        val c = src.safeGetChild(i) ?: continue
                        try {
                            val ct = ((c.safeText()?.toString().orEmpty()) + " " + (c.safeDesc()?.toString().orEmpty())).trim().lowercase()
                            if (isUnsafeSafeSearchOptionLabel(ct)) return true
                        } finally {
                            c.safeRecycle()
                        }
                    }
                } finally {
                    src.safeRecycle()
                }
            }
        }

        // 2. Check if an "Off" SafeSearch control is actively checked/selected on the settings page
        if (root != null) {
            val queue = ArrayDeque<AccessibilityNodeInfo>(80)
            val visited = HashSet<Int>(80)
            queue.add(root)
            visited.add(System.identityHashCode(root))
            var visitedCount = 0
            while (queue.isNotEmpty() && visitedCount < 80) {
                val n = queue.removeFirst()
                visitedCount++
                val isSelectedOrChecked = n.safeIsChecked() || n.safeIsSelected()
                if (isSelectedOrChecked) {
                    val txt = ((n.safeText()?.toString().orEmpty()) + " " + (n.safeDesc()?.toString().orEmpty())).trim().lowercase()
                    if (txt == "off" || txt == "turn off" || txt == "safesearch off") {
                        while (queue.isNotEmpty()) {
                            val rem = queue.removeFirst()
                            if (rem !== root) rem.safeRecycle()
                        }
                        if (n !== root) n.safeRecycle()
                        return true
                    }
                }
                val childCount = n.safeChildCount()
                for (i in 0 until childCount) {
                    if (visitedCount + queue.size >= 80) break
                    val c = n.safeGetChild(i) ?: continue
                    if (visited.add(System.identityHashCode(c))) {
                        queue.add(c)
                    } else {
                        c.safeRecycle()
                    }
                }
                if (n !== root) n.safeRecycle()
            }
        }

        return false
    }

    private fun isUnsafeSafeSearchOptionLabel(t: String): Boolean {
        if (t.isEmpty() || t.length > 80) return false
        return t == "off" ||
            t == "turn off" ||
            t == "blur" ||
            t.startsWith("off ") ||
            t.startsWith("blur explicit") ||
            t.contains("safesearch off") ||
            (t.contains("blur") && (t.contains("explicit") || t.contains("images") || t.contains("videos")))
    }

    private fun decodeComponent(raw: String): String {
        return runCatching {
            URLDecoder.decode(raw, "UTF-8")
        }.getOrElse {
            raw.replace("+", " ").replace("%20", " ")
        }
    }
}
