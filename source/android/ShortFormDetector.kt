package com.blocker

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.blocker.data.CompiledRuleSnapshot
import com.blocker.data.RuleSnapshotHolder

object ShortFormDetector {

    private fun AccessibilityNodeInfo?.safeRecycle() {
        if (this == null) return
        runCatching {
            if (Build.VERSION.SDK_INT < 33) {
                recycle()
            }
        }
    }

    /**
     * Checks if current screen in Instagram represents Reels or Explore feed.
     * If blocked, performs GLOBAL_ACTION_BACK, shows HUD overlay, and returns true.
     */
    fun checkInstagram(
        service: AccessibilityService,
        root: AccessibilityNodeInfo,
        ev: AccessibilityEvent?,
        pkg: String,
        snapshot: CompiledRuleSnapshot = RuleSnapshotHolder.snapshot
    ): Boolean {
        if (pkg != "com.instagram.android" && pkg != "com.instagram.lite") return false

        if (!snapshot.blockInstagramReels && !snapshot.blockInstagramExplore) return false

        // Fast story whitelist: Instagram Stories must NEVER be blocked
        if (isInstagramStory(root)) return false

        val evCls = ev?.className?.toString().orEmpty().lowercase()
        val rootCls = root.className?.toString().orEmpty().lowercase()

        // 1. Check Instagram Reels
        if (snapshot.blockInstagramReels) {
            if (isInstagramReelsViewer(service, root, evCls, rootCls)) {
                exitSubFeature(service, pkg, "Blocked Feed: Instagram Reels", BlockCategory.SHORT_FORM_VIDEO)
                return true
            }
        }

        // 2. Check Instagram Explore
        if (snapshot.blockInstagramExplore) {
            if (isInstagramExplorePage(service, root)) {
                exitSubFeature(service, pkg, "Blocked Feed: Instagram Explore", BlockCategory.SHORT_FORM_VIDEO)
                return true
            }
        }

        return false
    }

    private fun isInstagramStory(root: AccessibilityNodeInfo): Boolean {
        val storyIds = listOf(
            "com.instagram.android:id/reel_viewer_progress_bar",
            "com.instagram.android:id/story_item_container",
            "com.instagram.android:id/toolbar_container",
            "com.instagram.android:id/reel_viewer_tall_story_container",
            "com.instagram.android:id/reel_viewer_root",
            "com.instagram.lite:id/story_item_container"
        )
        for (id in storyIds) {
            val list = root.findAccessibilityNodeInfosByViewId(id)
            if (!list.isNullOrEmpty()) {
                for (n in list) n.safeRecycle()
                return true
            }
        }
        var hasStoryText = false
        fun scanStory(node: AccessibilityNodeInfo?, depth: Int, budget: IntArray) {
            if (node == null || depth > 12 || budget[0]-- <= 0 || hasStoryText) return
            val cd = node.contentDescription?.toString()?.lowercase().orEmpty()
            val text = node.text?.toString()?.lowercase().orEmpty()
            if (cd.contains("pause story") || text.contains("pause story") ||
                cd.contains("next story") || text.contains("next story") ||
                cd.contains("send message") || text.contains("send message") ||
                cd.contains("reply to ") || text.contains("reply to ")) {
                hasStoryText = true
                return
            }
            for (i in 0 until node.childCount) {
                if (hasStoryText) break
                val child = node.getChild(i) ?: continue
                scanStory(child, depth + 1, budget)
                child.safeRecycle()
            }
        }
        scanStory(root, 0, intArrayOf(60))
        return hasStoryText
    }

    private fun isInstagramReelsViewer(
        service: AccessibilityService,
        root: AccessibilityNodeInfo,
        evCls: String,
        rootCls: String
    ): Boolean {
        // Bottom "Reels" tab clips_tab is selected
        if (isTabSelectedById(root, "com.instagram.android:id/clips_tab") ||
            isTabSelectedById(root, "com.instagram.lite:id/clips_tab") ||
            scanSelectedTab(root, listOf("reels, tab", "reels tab", "reels"))) {
            return true
        }

        if (evCls.contains("clipsviewer") || rootCls.contains("clipsviewer")) {
            return true
        }

        val reelIds = listOf(
            "com.instagram.android:id/clips_viewer_view_pager",
            "com.instagram.android:id/clips_video_container",
            "com.instagram.android:id/clips_viewer_container",
            "com.instagram.android:id/reel_viewer_root",
            "com.instagram.lite:id/clips_viewer_view_pager",
            "com.instagram.lite:id/clips_video_container"
        )
        val dm = service.resources.displayMetrics
        val screenH = dm.heightPixels.coerceAtLeast(1)
        val rect = Rect()

        for (id in reelIds) {
            val list = root.findAccessibilityNodeInfosByViewId(id)
            if (!list.isNullOrEmpty()) {
                var fillsScreen = false
                for (n in list) {
                    n.getBoundsInScreen(rect)
                    if (rect.height() >= screenH * 0.50f) {
                        fillsScreen = true
                    }
                    n.safeRecycle()
                }
                if (fillsScreen) return true
            }
        }
        return false
    }

    private fun isInstagramExplorePage(
        service: AccessibilityService,
        root: AccessibilityNodeInfo
    ): Boolean {
        // Confirmed when bottom "Search and explore" / "Explore" tab (search_tab) is selected (isSelected == true)
        val isSearchTabSelected = isTabSelectedById(root, "com.instagram.android:id/search_tab") ||
            isTabSelectedById(root, "com.instagram.lite:id/search_tab") ||
            scanSelectedTab(root, listOf("search and explore", "search & explore", "explore", "explore, tab", "explore tab"))

        val dm = service.resources.displayMetrics
        val screenH = dm.heightPixels.coerceAtLeast(1)
        val rect = Rect()

        // AND Explore media grid (explore_grid, recycler_view with multi-column image/video tiles, or "Explore" feed header) is visible on screen,
        // OR when viewing a post/reel chain opened from Explore section ("Explore" title at top of screen).
        var hasExploreHeader = false
        var hasExploreGridId = false
        var hasExploreTitleAtTop = false

        val exploreIds = listOf(
            "com.instagram.android:id/explore_grid",
            "com.instagram.android:id/explore_grid_recycler_view",
            "com.instagram.lite:id/explore_grid"
        )
        for (id in exploreIds) {
            val list = root.findAccessibilityNodeInfosByViewId(id)
            if (!list.isNullOrEmpty()) {
                for (n in list) n.safeRecycle()
                hasExploreGridId = true
                break
            }
        }

        fun scanExplore(node: AccessibilityNodeInfo?, depth: Int, budget: IntArray) {
            if (node == null || depth > 15 || budget[0]-- <= 0) return
            val cd = node.contentDescription?.toString()?.lowercase().orEmpty()
            val text = node.text?.toString()?.lowercase().orEmpty()
            val id = node.viewIdResourceName?.toString()?.lowercase().orEmpty()

            node.getBoundsInScreen(rect)
            if (rect.top < screenH * 0.18f) {
                if (text == "explore" || cd == "explore" || cd == "search and explore") {
                    hasExploreTitleAtTop = true
                }
            }
            if (id.contains("explore_grid") || id.contains("media_grid") ||
                (node.className?.toString()?.contains("RecyclerView") == true && id.contains("grid"))) {
                hasExploreGridId = true
            }
            if (text.contains("explore") || cd.contains("explore")) {
                hasExploreHeader = true
            }

            for (i in 0 until node.childCount) {
                if (hasExploreTitleAtTop || (hasExploreGridId && isSearchTabSelected)) break
                val child = node.getChild(i) ?: continue
                scanExplore(child, depth + 1, budget)
                child.safeRecycle()
            }
        }

        scanExplore(root, 0, intArrayOf(100))

        if (hasExploreTitleAtTop) return true
        if (isSearchTabSelected && (hasExploreGridId || hasExploreHeader)) return true

        return false
    }

    private fun isTabSelectedById(root: AccessibilityNodeInfo, tabId: String): Boolean {
        val list = root.findAccessibilityNodeInfosByViewId(tabId)
        if (list.isNullOrEmpty()) return false
        var selected = false
        for (node in list) {
            val cd = node.contentDescription?.toString()?.lowercase()
            val stateDesc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) node.stateDescription?.toString()?.lowercase() else null
            if (node.isSelected ||
                cd?.contains("selected") == true ||
                cd?.contains("active") == true ||
                stateDesc?.contains("selected") == true ||
                stateDesc?.contains("active") == true) {
                selected = true
            }
            node.safeRecycle()
        }
        return selected
    }

    private fun scanSelectedTab(root: AccessibilityNodeInfo, targets: List<String>): Boolean {
        var found = false
        fun scan(node: AccessibilityNodeInfo?, depth: Int, budget: IntArray) {
            if (node == null || depth > 10 || budget[0]-- <= 0 || found) return
            val cd = node.contentDescription?.toString()?.lowercase().orEmpty()
            val text = node.text?.toString()?.lowercase().orEmpty()
            val stateDesc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) node.stateDescription?.toString()?.lowercase().orEmpty() else ""
            val isSel = node.isSelected || cd.contains("selected") || stateDesc.contains("selected") || cd.contains("active")
            if (isSel) {
                for (t in targets) {
                    if (cd.contains(t) || text.contains(t)) {
                        found = true
                        return
                    }
                }
            }
            for (i in 0 until node.childCount) {
                if (found) break
                val child = node.getChild(i) ?: continue
                scan(child, depth + 1, budget)
                child.safeRecycle()
            }
        }
        scan(root, 0, intArrayOf(60))
        return found
    }

    private fun exitSubFeature(
        service: AccessibilityService,
        pkg: String,
        reason: String,
        category: BlockCategory
    ) {
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        OverlayHudManager.show(
            service,
            if (BlockerStore.popupLanguage(service) == "bn") {
                if (reason.contains("Reels")) "ইনস্টাগ্রাম রিলস ব্লক করা হয়েছে" else "ইনস্টাগ্রাম এক্সপ্লোর ব্লক করা হয়েছে"
            } else {
                reason
            },
            4,
            BlockReason(category, reason)
        )
        BlockerStore.incr(service, "blocked_apps")
        BlockerStore.incr(service, "reels_block")
    }
}
