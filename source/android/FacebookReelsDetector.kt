package com.blocker

import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.blocker.data.CompiledRuleSnapshot
import com.blocker.data.RuleSnapshotHolder

/**
 * High-performance, zero-lag detector for Facebook Reels AND Facebook Videos.
 *
 * Core Guarantees:
 * 1. Facebook Stories (both clicking from tray and viewing in full screen) are 100% ALLOWED.
 * 2. Facebook Reels AND Facebook Videos (Watch tab, playing in-feed video, full-screen/theatre player,
 *    and clicking to play) are 100% BLOCKED.
 * 3. Normal Newsfeed scrolling past unplayed video posts remains 100% allowed without false blocks.
 */
object FacebookReelsDetector {

    @Volatile
    var pendingFacebookReelCheckUntilMs: Long = 0L

    private fun AccessibilityNodeInfo?.safeRecycle() {
        if (this == null) return
        runCatching {
            if (Build.VERSION.SDK_INT < 33) {
                recycle()
            }
        }
    }

    /**
     * Unconditional Facebook Stories Whitelist Check:
     * Evaluated FIRST before any Reels or Video inspection.
     * Returns true if user is clicking a Story card or currently viewing a full-screen Story.
     */
    fun isFacebookStoryViewerOrStoryClick(
        root: AccessibilityNodeInfo?,
        ev: AccessibilityEvent?,
        screenWidth: Int = 1080,
        screenHeight: Int = 2400
    ): Boolean {
        // A. Story Tray Click Exemption (TYPE_VIEW_CLICKED)
        if (ev != null && ev.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            if (isFacebookStoryClick(ev)) {
                pendingFacebookReelCheckUntilMs = 0L
                return true
            }
        }

        // B. Full-Screen Story Viewer Exemption
        if (root != null) {
            val cls = ev?.className?.toString().orEmpty()
            if (isFacebookStoryViewer(root, cls, screenWidth, screenHeight)) {
                pendingFacebookReelCheckUntilMs = 0L
                return true
            }
        }

        return false
    }

    /**
     * Inspects a click event and its parent hierarchy for explicit Facebook Story markers.
     */
    fun isFacebookStoryClick(ev: AccessibilityEvent): Boolean {
        if (ev.eventType != AccessibilityEvent.TYPE_VIEW_CLICKED) return false

        fun isStoryMarker(s: String): Boolean {
            if (s.isEmpty()) return false
            val l = s.lowercase()
            val hasStory = l.contains("story") || l.contains("stories") ||
                           l.contains("'s story") || l.contains("add to story") ||
                           l.contains("create story") || l.contains("your story") ||
                           l.contains("unread story") || l.contains("স্টোরি")
            val hasReel = l.contains("reel")
            return hasStory && !hasReel
        }

        val evCd = ev.contentDescription?.toString().orEmpty()
        val evText = ev.text.joinToString(" ")
        if (isStoryMarker(evCd) || isStoryMarker(evText)) return true

        val src = ev.source ?: return false
        val srcCd = src.contentDescription?.toString().orEmpty()
        val srcText = src.text?.toString().orEmpty()
        val srcId = src.viewIdResourceName?.toString().orEmpty()

        if (isStoryMarker(srcCd) || isStoryMarker(srcText) || isStoryMarker(srcId)) {
            src.safeRecycle()
            return true
        }

        // Walk up 3 parent levels
        var p = src.parent
        var depth = 0
        while (p != null && depth < 3) {
            val pCd = p.contentDescription?.toString().orEmpty()
            val pText = p.text?.toString().orEmpty()
            val pId = p.viewIdResourceName?.toString().orEmpty()
            if (isStoryMarker(pCd) || isStoryMarker(pText) || isStoryMarker(pId)) {
                p.safeRecycle()
                src.safeRecycle()
                return true
            }
            val nextP = p.parent
            p.safeRecycle()
            p = nextP
            depth++
        }
        p?.safeRecycle()

        // Inspect direct children
        for (i in 0 until src.childCount) {
            val c = src.getChild(i) ?: continue
            val cCd = c.contentDescription?.toString().orEmpty()
            val cText = c.text?.toString().orEmpty()
            val cId = c.viewIdResourceName?.toString().orEmpty()
            val isStory = isStoryMarker(cCd) || isStoryMarker(cText) || isStoryMarker(cId)
            c.safeRecycle()
            if (isStory) {
                src.safeRecycle()
                return true
            }
        }

        src.safeRecycle()
        return false
    }

    /**
     * Detects whether the current active window is the Facebook Story Viewer.
     */
    fun isFacebookStoryViewer(
        root: AccessibilityNodeInfo,
        className: String,
        screenWidth: Int,
        screenHeight: Int
    ): Boolean {
        val lowerCls = className.lowercase()
        val rootCls = root.className?.toString().orEmpty().lowercase()
        val isStoryClass = lowerCls.contains("storyviewer") || rootCls.contains("storyviewer") ||
                           lowerCls.contains("fbstories") || rootCls.contains("fbstories") ||
                           lowerCls.contains("storypermalink") || rootCls.contains("storypermalink") ||
                           lowerCls.contains("inspirationstory") || rootCls.contains("inspirationstory")

        val rect = Rect()
        var hasTopReelsOrWatchHeader = false
        var hasStoryIdOrMarker = isStoryClass
        var hasStoryControlOrReplyBar = false
        var horizontalReactionCount = 0
        var visibleCount = 0

        fun isStoryControl(lower: String): Boolean {
            if (lower.isEmpty()) return false
            return lower.contains("pause story") || lower.contains("play story") ||
                   lower.contains("next story") || lower.contains("previous story") ||
                   lower.contains("close story") || lower.contains("story privacy") ||
                   lower.contains("'s story") || lower.contains("add to story") ||
                   lower.contains("see who viewed") ||
                   lower.contains("send a message") || lower.contains("send message") ||
                   lower.contains("reply to ") || lower.contains("write a reply") ||
                   lower.contains("send a reply") || lower.contains("স্টোরি")
        }

        fun isHorizontalReaction(lower: String): Boolean {
            return lower == "like" || lower == "love" || lower == "care" ||
                   lower == "haha" || lower == "wow" || lower == "sad" || lower == "angry" ||
                   lower.startsWith("react")
        }

        fun scan(node: AccessibilityNodeInfo?, depth: Int) {
            if (node == null || depth > 20 || visibleCount >= 120) return
            if (!node.isVisibleToUser) return

            visibleCount++
            node.getBoundsInScreen(rect)

            val text = node.text?.toString().orEmpty()
            val cd = node.contentDescription?.toString().orEmpty()
            val id = node.viewIdResourceName?.toString().orEmpty()
            val lowerText = text.lowercase()
            val lowerCd = cd.lowercase()
            val lowerId = id.lowercase()

            if (rect.top < screenHeight * 0.16f) {
                if (text == "Reels" || cd == "Reels" || text == "রিলস" || cd == "রিলস" ||
                    text == "Watch" || cd == "Watch" || text == "Video" || cd == "Video") {
                    hasTopReelsOrWatchHeader = true
                }
            }

            if (lowerId.contains("story") || lowerId.contains("stories") ||
                lowerId.contains("story_viewer") || lowerId.contains("story_viewer_fragment")) {
                hasStoryIdOrMarker = true
            }

            if (isStoryControl(lowerText) || isStoryControl(lowerCd)) {
                hasStoryControlOrReplyBar = true
            }

            if (rect.top > screenHeight * 0.78f) {
                if (isHorizontalReaction(lowerText) || isHorizontalReaction(lowerCd)) {
                    horizontalReactionCount++
                }
            }

            for (i in 0 until node.childCount) {
                if (visibleCount >= 120) break
                val child = node.getChild(i) ?: continue
                scan(child, depth + 1)
                child.safeRecycle()
            }
        }

        scan(root, 0)

        if (hasTopReelsOrWatchHeader) return false
        if (hasStoryIdOrMarker) return true
        if (hasStoryControlOrReplyBar) return true
        if (horizontalReactionCount >= 2) return true

        return false
    }

    /**
     * Block Clicking Any Video or Reel to Play It (TYPE_VIEW_CLICKED):
     * Walks up 3 parent levels to check if the user clicked:
     * - Any "Play", "Play video", "Watch", "Watch more", "Unmute", or "Fullscreen" button on a video post,
     * - The "Video" or "Watch" tab icon in the Facebook navigation bar, OR
     * - Any "Reel" / "Reels" card or button.
     */
    fun isFacebookVideoOrReelClick(
        ev: AccessibilityEvent,
        screenWidth: Int = 1080,
        screenHeight: Int = 2400,
        snapshot: CompiledRuleSnapshot = RuleSnapshotHolder.snapshot
    ): Boolean {
        if (!snapshot.blockFacebookReels) {
            pendingFacebookReelCheckUntilMs = 0L
            return false
        }
        if (ev.eventType != AccessibilityEvent.TYPE_VIEW_CLICKED) return false

        // Story click is 100% exempt
        if (isFacebookStoryClick(ev)) {
            pendingFacebookReelCheckUntilMs = 0L
            return false
        }

        fun isVideoOrReelAction(s: String): Boolean {
            if (s.isEmpty()) return false
            val l = s.lowercase().trim()
            if (l.contains("story") || l.contains("stories")) return false

            // 1. Play / Watch / Fullscreen video buttons
            if (l == "play" || l == "play video" || l == "watch" || l == "watch more" ||
                l == "unmute" || l == "mute" || l == "fullscreen" || l == "enter fullscreen" ||
                l.startsWith("play video") || l.startsWith("watch video") ||
                l == "ভিডিও চালান" || l == "চালান") {
                return true
            }

            // 2. Video / Watch tab in navigation bar
            if (l == "video" || l == "watch" || l == "facebook watch" || l == "live videos" ||
                l == "video, tab" || l == "watch, tab" || l == "video tab" || l == "watch tab" ||
                l == "ভিডিও" || l == "ওয়াচ") {
                return true
            }

            // 3. Any Reel / Reels card or button
            if (l.contains("reel") || l.contains("reels") || l.contains("play reel") ||
                l.contains("see more reels") || l.contains("reels and short videos") ||
                l.contains("reels & short videos") || l.contains("original audio") ||
                l.contains("watch reel") || l.contains("view reel") || l.contains("রিলস")) {
                return true
            }

            return false
        }

        fun isVideoOrReelId(id: String): Boolean {
            if (id.isEmpty()) return false
            val l = id.lowercase()
            if (l.contains("story")) return false
            return l.contains("reel") || l.contains("reels") || l.contains("video") ||
                   l.contains("watch") || l.contains("fb_shorts") || l.contains("videoplayer") ||
                   l.contains("tahoe")
        }

        val evCd = ev.contentDescription?.toString().orEmpty()
        val evText = ev.text.joinToString(" ")
        if (isVideoOrReelAction(evCd) || isVideoOrReelAction(evText)) {
            pendingFacebookReelCheckUntilMs = SystemClock.elapsedRealtime() + 2500L
            return true
        }

        val src = ev.source ?: return false
        val srcCd = src.contentDescription?.toString().orEmpty()
        val srcText = src.text?.toString().orEmpty()
        val srcId = src.viewIdResourceName?.toString().orEmpty()

        if (isVideoOrReelAction(srcCd) || isVideoOrReelAction(srcText) || isVideoOrReelId(srcId)) {
            pendingFacebookReelCheckUntilMs = SystemClock.elapsedRealtime() + 2500L
            src.safeRecycle()
            return true
        }

        // Walk up 3 parent levels
        var p = src.parent
        var depth = 0
        while (p != null && depth < 3) {
            val pCd = p.contentDescription?.toString().orEmpty()
            val pText = p.text?.toString().orEmpty()
            val pId = p.viewIdResourceName?.toString().orEmpty()
            if (isVideoOrReelAction(pCd) || isVideoOrReelAction(pText) || isVideoOrReelId(pId)) {
                pendingFacebookReelCheckUntilMs = SystemClock.elapsedRealtime() + 2500L
                p.safeRecycle()
                src.safeRecycle()
                return true
            }
            val nextP = p.parent
            p.safeRecycle()
            p = nextP
            depth++
        }
        p?.safeRecycle()

        // Inspect direct children
        for (i in 0 until src.childCount) {
            val c = src.getChild(i) ?: continue
            val cCd = c.contentDescription?.toString().orEmpty()
            val cText = c.text?.toString().orEmpty()
            val cId = c.viewIdResourceName?.toString().orEmpty()
            val hit = isVideoOrReelAction(cCd) || isVideoOrReelAction(cText) || isVideoOrReelId(cId)
            c.safeRecycle()
            if (hit) {
                pendingFacebookReelCheckUntilMs = SystemClock.elapsedRealtime() + 2500L
                src.safeRecycle()
                return true
            }
        }

        src.safeRecycle()
        return false
    }

    /**
     * Backward-compatible alias for click detection.
     */
    fun isFacebookReelClick(
        ev: AccessibilityEvent,
        screenWidth: Int = 1080,
        screenHeight: Int = 2400,
        snapshot: CompiledRuleSnapshot = RuleSnapshotHolder.snapshot
    ): Boolean = isFacebookVideoOrReelClick(ev, screenWidth, screenHeight, snapshot)

    /**
     * Line-1 gated evaluate method for Facebook Reels & Videos.
     */
    fun evaluate(
        root: AccessibilityNodeInfo?,
        ev: AccessibilityEvent?,
        screenWidth: Int = 1080,
        screenHeight: Int = 2400,
        snapshot: CompiledRuleSnapshot = RuleSnapshotHolder.snapshot
    ): Boolean {
        if (!snapshot.blockFacebookReels) {
            pendingFacebookReelCheckUntilMs = 0L
            return false
        }
        return isFacebookVideoOrReels(root, ev, screenWidth, screenHeight, snapshot)
    }

    /**
     * Comprehensive Facebook Video & Reels Checker:
     * Returns true if screen or click event represents ANY Facebook video or Reel.
     * Always allows Stories and unplayed Newsfeed scrolling.
     */
    fun isFacebookVideoOrReels(
        root: AccessibilityNodeInfo?,
        ev: AccessibilityEvent?,
        screenWidth: Int = 1080,
        screenHeight: Int = 2400,
        snapshot: CompiledRuleSnapshot = RuleSnapshotHolder.snapshot
    ): Boolean {
        if (!snapshot.blockFacebookReels) {
            pendingFacebookReelCheckUntilMs = 0L
            return false
        }

        // 1. Stories Whitelist Check FIRST
        if (isFacebookStoryViewerOrStoryClick(root, ev, screenWidth, screenHeight)) {
            pendingFacebookReelCheckUntilMs = 0L
            return false
        }

        // 2. Click Intercept (TYPE_VIEW_CLICKED)
        if (ev != null && ev.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            if (isFacebookVideoOrReelClick(ev, screenWidth, screenHeight, snapshot)) {
                return true
            }
        }

        if (root == null) return false

        val evCls = ev?.className?.toString().orEmpty()
        val rootCls = root.className?.toString().orEmpty()
        val lowerEvCls = evCls.lowercase()
        val lowerRootCls = rootCls.lowercase()

        // 3. Dedicated Video/Reels Player Activity/Class check
        if (lowerEvCls.contains("videoplayer") || lowerRootCls.contains("videoplayer") ||
            lowerEvCls.contains("fullscreenvideo") || lowerRootCls.contains("fullscreenvideo") ||
            lowerEvCls.contains("watchfeed") || lowerRootCls.contains("watchfeed") ||
            lowerEvCls.contains("videohome") || lowerRootCls.contains("videohome") ||
            lowerEvCls.contains("tahoe") || lowerRootCls.contains("tahoe") ||
            lowerEvCls.contains("fbreelsviewer") || lowerRootCls.contains("fbreelsviewer") ||
            lowerEvCls.contains("inspirationreelviewer") || lowerRootCls.contains("inspirationreelviewer") ||
            lowerEvCls.contains("reelviewer") || lowerRootCls.contains("reelviewer")) {
            return true
        }

        val rect = Rect()
        var hasWatchTabSelected = false
        var hasWatchTopTitle = false
        var hasSuggestedVideosHeader = false

        var hasPlayerClassOrId = false
        var hasActivePlaybackControls = false
        var hasVisibleSeekBar = false

        var likeBounds: Rect? = null
        var commentBounds: Rect? = null
        var shareBounds: Rect? = null

        var isWhatsOnYourMindVisible = false
        var visibleCount = 0

        fun scan(node: AccessibilityNodeInfo?, depth: Int) {
            if (node == null || depth > 22 || visibleCount >= 140) return
            if (!node.isVisibleToUser) return

            visibleCount++
            node.getBoundsInScreen(rect)

            val text = node.text?.toString().orEmpty()
            val cd = node.contentDescription?.toString().orEmpty()
            val id = node.viewIdResourceName?.toString().orEmpty()
            val cls = node.className?.toString().orEmpty()
            val lowerText = text.lowercase().trim()
            val lowerCd = cd.lowercase().trim()
            val lowerId = id.lowercase()
            val lowerNodeCls = cls.lowercase()

            // Check composer visibility (Newsfeed indicator)
            if (lowerText.contains("what's on your mind") || lowerCd.contains("what's on your mind") ||
                lowerText.contains("কী ভাবছেন") || lowerCd.contains("কী ভাবছেন")) {
                isWhatsOnYourMindVisible = true
            }

            // Check A: Watch / Video tab is open
            val isWatchOrVideoLabel = lowerText == "video" || lowerCd == "video" ||
                                      lowerText == "watch" || lowerCd == "watch" ||
                                      lowerText == "facebook watch" || lowerCd == "facebook watch" ||
                                      lowerText == "live videos" || lowerCd == "live videos" ||
                                      lowerText.contains("watch, tab") || lowerCd.contains("watch, tab") ||
                                      lowerText.contains("video, tab") || lowerCd.contains("video, tab") ||
                                      lowerText == "ভিডিও" || lowerCd == "ভিডিও" ||
                                      lowerText == "ওয়াচ" || lowerCd == "ওয়াচ"

            val stateDesc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) node.stateDescription?.toString()?.lowercase().orEmpty() else ""
            val isNodeSelected = node.isSelected || lowerCd.contains("selected") || stateDesc.contains("selected") || lowerCd.contains("active")

            if (isWatchOrVideoLabel && isNodeSelected) {
                hasWatchTabSelected = true
            }

            if (rect.top < screenHeight * 0.18f) {
                if (lowerText == "video" || lowerCd == "video" || lowerText == "watch" || lowerCd == "watch" ||
                    lowerText == "reels" || lowerCd == "reels" || lowerText == "ভিডিও" || lowerText == "রিলস") {
                    hasWatchTopTitle = true
                }
            }

            if (lowerText.contains("suggested videos") || lowerCd.contains("suggested videos") ||
                lowerText.contains("up next") || lowerCd.contains("up next") ||
                lowerText.contains("more videos") || lowerCd.contains("more videos") ||
                lowerText.contains("reels and short videos") || lowerCd.contains("reels and short videos")) {
                hasSuggestedVideosHeader = true
            }

            // Check B: Full-screen or Theatre video player IDs/classes
            if (lowerNodeCls.contains("videoplayer") || lowerNodeCls.contains("fullscreenvideo") ||
                lowerId.contains("videoplayer") || lowerId.contains("fullscreenvideo") ||
                lowerId.contains("watchfeed") || lowerId.contains("videohome") ||
                lowerId.contains("tahoe") || lowerId.contains("fbreelsviewer") ||
                lowerId.contains("inspirationreelviewer") || lowerId.contains("reels_viewer")) {
                if (rect.height() >= screenHeight * 0.45f) {
                    hasPlayerClassOrId = true
                }
            }

            // Check C: Active Video Playback Controls
            val isPause = lowerText == "pause" || lowerCd == "pause" ||
                          lowerText == "pause video" || lowerCd == "pause video" ||
                          lowerText == "ভিডিও থামান" || lowerCd == "ভিডিও থামান"

            val isFullscreenToggle = lowerText.contains("enter fullscreen") || lowerCd.contains("enter fullscreen") ||
                                     lowerText.contains("exit fullscreen") || lowerCd.contains("exit fullscreen")

            val isPlaybackScrub = lowerText.contains("rewind 10") || lowerCd.contains("rewind 10") ||
                                  lowerText.contains("forward 10") || lowerCd.contains("forward 10") ||
                                  lowerText == "video player" || lowerCd == "video player"

            if (isPause || isFullscreenToggle || isPlaybackScrub) {
                hasActivePlaybackControls = true
            }

            // Visible SeekBar scrubber bar
            if (lowerNodeCls.contains("seekbar") || lowerId.contains("seekbar") || lowerCd.contains("seek bar")) {
                if (!lowerId.contains("story") && rect.centerY() > screenHeight * 0.20f) {
                    hasVisibleSeekBar = true
                }
            }

            // Vertical action stack on right edge
            if (rect.left >= screenWidth * 0.70f) {
                val isLike = lowerCd.startsWith("like") || lowerCd.startsWith("react") || lowerText == "like"
                val isComment = lowerCd.startsWith("comment") || lowerText == "comment"
                val isShare = lowerCd.startsWith("share") || lowerCd.startsWith("send") || lowerText == "share"

                if (isLike && likeBounds == null) {
                    likeBounds = Rect(rect)
                } else if (isComment && commentBounds == null) {
                    commentBounds = Rect(rect)
                } else if (isShare && shareBounds == null) {
                    shareBounds = Rect(rect)
                }
            }

            for (i in 0 until node.childCount) {
                if (visibleCount >= 140) break
                val child = node.getChild(i) ?: continue
                scan(child, depth + 1)
                child.safeRecycle()
            }
        }

        scan(root, 0)

        // Evaluate conditions:
        // A. Watch / Video tab is open
        if (hasWatchTabSelected || hasWatchTopTitle || (hasSuggestedVideosHeader && !isWhatsOnYourMindVisible)) {
            return true
        }

        // B. Full-Screen / Theatre Video Player open
        if (hasPlayerClassOrId) {
            return true
        }

        // C. Active video playback controls visible (Playing In-Feed or in player)
        if (hasActivePlaybackControls) {
            return true
        }

        if (hasVisibleSeekBar) {
            return true
        }

        // Vertical Reels action stack
        if (likeBounds != null && commentBounds != null) {
            if (likeBounds!!.centerY() < commentBounds!!.centerY()) {
                if (shareBounds == null || commentBounds!!.centerY() < shareBounds!!.centerY()) {
                    return true
                }
            }
        }

        // Check if within post-click window
        if (SystemClock.elapsedRealtime() < pendingFacebookReelCheckUntilMs) {
            return true
        }

        return false
    }

    /**
     * Backward-compatible alias for full-screen viewer detection.
     */
    fun isDedicatedFullScreenReelsOrVideoViewer(
        root: AccessibilityNodeInfo,
        ev: AccessibilityEvent?,
        screenWidth: Int = 1080,
        screenHeight: Int = 2400,
        snapshot: CompiledRuleSnapshot = RuleSnapshotHolder.snapshot
    ): Boolean = isFacebookVideoOrReels(root, ev, screenWidth, screenHeight, snapshot)
}
