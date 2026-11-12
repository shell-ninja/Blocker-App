package com.blocker

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import java.util.ArrayDeque

/**
 * High-precision local detector for active Incognito / Private / Secret browser tabs.
 * Designed to eliminate false positives on normal browser tabs and overflow menus.
 */
object IncognitoDetector {

    val RECOGNIZED_BROWSER_PACKAGES: Set<String> = setOf(
        // Chrome
        "com.android.chrome",
        "com.chrome.beta",
        "com.chrome.dev",
        "com.chrome.canary",
        "org.chromium.chrome",
        // Brave
        "com.brave.browser",
        "com.brave.browser_beta",
        "com.brave.browser_nightly",
        // Microsoft Edge
        "com.microsoft.emmx",
        "com.microsoft.emmx.beta",
        "com.microsoft.emmx.canary",
        // Firefox / Fenix / Focus
        "org.mozilla.firefox",
        "org.mozilla.firefox_beta",
        "org.mozilla.fenix",
        "org.mozilla.focus",
        // Samsung Internet
        "com.sec.android.app.sbrowser",
        "com.sec.android.app.sbrowser.beta",
        // Opera
        "com.opera.browser",
        "com.opera.browser.beta",
        "com.opera.mini.native",
        "com.opera.gx",
        // Vivaldi / Kiwi / DuckDuckGo / Mi Browser
        "com.vivaldi.browser",
        "com.kiwibrowser.browser",
        "com.duckduckgo.mobile.android",
        "com.mi.globalbrowser",
        "com.android.browser"
    )

    private val SIGNAL_A_UNCONDITIONAL_VIEW_IDS = setOf(
        // Chrome / Brave / Edge / Chromium
        "incognito_badge",
        "incognito_ntp_logo",
        "new_tab_incognito_container",
        "incognito_description_layout",
        "incognito_reauth_view",
        "incognito_lock_icon",
        // Firefox / Fenix
        "private_browsing_description",
        "mozac_browser_toolbar_private_mode_indicator",
        "private_session_common",
        // Samsung Internet
        "secret_mode_badge",
        "secret_mode_indicator",
        "secret_mode_title",
        // Opera / Vivaldi
        "private_mode_badge",
        "incognito_icon"
    )

    private val SIGNAL_B_EXACT_CHROME_BADGES = setOf(
        "incognito",
        "incognito mode",
        "incognito tab",
        "inprivate",
        "inprivate tab",
        "private tab",
        "private browsing",
        "secret mode"
    )

    private val MENU_EXCLUSION_PHRASES = listOf(
        "new incognito tab",
        "new private tab",
        "new inprivate tab",
        "turn on secret mode",
        "open in incognito",
        "open in private",
        "open in secret"
    )

    private val SIGNAL_C_LANDING_PHRASES = listOf(
        "you’ve gone incognito",
        "you've gone incognito",
        "you are in incognito mode",
        "close all incognito tabs",
        "close all private tabs",
        "you have entered secret mode",
        "browse in private"
    )

    private const val MAX_VISITED_NODES = 160

    /**
     * Inspects browser windows for an active Incognito/Private session.
     * Never matches page body words ("incognito") or overflow menu launch items.
     */
    fun isActiveIncognitoSession(
        root: AccessibilityNodeInfo?,
        windows: List<AccessibilityWindowInfo>?,
        pkg: String,
        screenHeightPx: Int
    ): Boolean {
        if (pkg !in RECOGNIZED_BROWSER_PACKAGES) return false

        val topChromeLimitPx = if (screenHeightPx > 0) (screenHeightPx * 0.22f).toInt() else 520

        // Prefer inspecting active browser windows if available, otherwise use active root.
        val inspectedAnyWindow = if (!windows.isNullOrEmpty()) {
            var checked = false
            for (win in windows) {
                if (win.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) continue
                val wr = runCatching { win.root }.getOrNull() ?: continue
                val wPkg = runCatching { wr.packageName?.toString() }.getOrNull()
                if (wPkg == pkg) {
                    checked = true
                    if (scanTreeForIncognito(wr, topChromeLimitPx)) {
                        return true
                    }
                }
            }
            checked
        } else {
            false
        }

        if (!inspectedAnyWindow && root != null) {
            val rPkg = runCatching { root.packageName?.toString() }.getOrNull()
            if (rPkg == pkg || rPkg.isNullOrEmpty()) {
                return scanTreeForIncognito(root, topChromeLimitPx)
            }
        }
        return false
    }

    private fun scanTreeForIncognito(startRoot: AccessibilityNodeInfo, topChromeLimitPx: Int): Boolean {
        val queue = ArrayDeque<AccessibilityNodeInfo>(MAX_VISITED_NODES)
        val visited = HashSet<Int>(MAX_VISITED_NODES)
        val bounds = Rect()
        var visitedCount = 0

        queue.add(startRoot)
        visited.add(System.identityHashCode(startRoot))

        var detected = false

        while (queue.isNotEmpty() && visitedCount < MAX_VISITED_NODES) {
            val node = queue.removeFirst()
            visitedCount++

            val isVisible = runCatching { node.isVisibleToUser }.getOrDefault(true)
            if (isVisible && evaluateNode(node, topChromeLimitPx, bounds)) {
                detected = true
                if (node !== startRoot) node.safeRecycle()
                break
            }

            val childCount = node.safeChildCount()
            for (i in 0 until childCount) {
                if (visitedCount + queue.size >= MAX_VISITED_NODES) break
                val child = node.safeGetChild(i) ?: continue
                if (visited.add(System.identityHashCode(child))) {
                    queue.add(child)
                } else {
                    child.safeRecycle()
                }
            }

            if (node !== startRoot) {
                node.safeRecycle()
            }
        }

        while (queue.isNotEmpty()) {
            val rem = queue.removeFirst()
            if (rem !== startRoot) {
                rem.safeRecycle()
            }
        }

        return detected
    }

    private fun evaluateNode(node: AccessibilityNodeInfo, topChromeLimitPx: Int, bounds: Rect): Boolean {
        val rawText = node.safeText()?.toString()?.trim().orEmpty()
        val rawDesc = node.safeDesc()?.toString()?.trim().orEmpty()
        val textLower = rawText.lowercase()
        val descLower = rawDesc.lowercase()

        // Exclude browser overflow/context menu rows offering to open incognito/private tabs
        if (isMenuLaunchItem(textLower) || isMenuLaunchItem(descLower)) {
            return false
        }

        // Signal A: Browser-specific active incognito View IDs
        val fullId = node.safeViewId()?.lowercase().orEmpty()
        val shortId = fullId.substringAfter(":id/", fullId)
        if (shortId.isNotEmpty()) {
            if (shortId in SIGNAL_A_UNCONDITIONAL_VIEW_IDS) {
                return true
            }
            if (shortId == "tab_switcher_mode_menu" && isSelectedIncognitoSwitcher(node, textLower, descLower)) {
                return true
            }
        }

        // Signal B: Top Browser Chrome / Address Bar Badge (top 22% of screen)
        node.safeBounds(bounds)
        val inTopChrome = bounds.top in 0 until topChromeLimitPx && bounds.bottom > 0
        if (inTopChrome) {
            if (textLower in SIGNAL_B_EXACT_CHROME_BADGES || descLower in SIGNAL_B_EXACT_CHROME_BADGES) {
                return true
            }
        }

        // Signal C: Active Incognito empty-tab / landing screen header phrases
        // Guard against arbitrary web page articles by checking concise UI header/button nodes
        if (textLower.isNotEmpty() && textLower.length <= 160 && matchesLandingPhrase(textLower)) {
            return true
        }
        if (descLower.isNotEmpty() && descLower.length <= 160 && matchesLandingPhrase(descLower)) {
            return true
        }

        return false
    }

    private fun isMenuLaunchItem(s: String): Boolean {
        if (s.isEmpty()) return false
        return MENU_EXCLUSION_PHRASES.any { s.contains(it) }
    }

    private fun matchesLandingPhrase(s: String): Boolean {
        return SIGNAL_C_LANDING_PHRASES.any { phrase ->
            s == phrase || s.startsWith(phrase)
        }
    }

    private fun isSelectedIncognitoSwitcher(
        menuNode: AccessibilityNodeInfo,
        textLower: String,
        descLower: String
    ): Boolean {
        if (menuNode.safeIsSelected() &&
            (textLower.contains("incognito") || textLower.contains("private") ||
                descLower.contains("incognito") || descLower.contains("private"))
        ) {
            return true
        }
        val childCount = menuNode.safeChildCount()
        for (i in 0 until childCount) {
            val child = menuNode.safeGetChild(i) ?: continue
            try {
                val cSel = child.safeIsSelected()
                if (cSel) {
                    val cTxt = child.safeText()?.toString()?.lowercase().orEmpty()
                    val cDesc = child.safeDesc()?.toString()?.lowercase().orEmpty()
                    if (cTxt.contains("incognito") || cTxt.contains("private") ||
                        cDesc.contains("incognito") || cDesc.contains("private")
                    ) {
                        return true
                    }
                }
            } finally {
                child.safeRecycle()
            }
        }
        return false
    }
}
