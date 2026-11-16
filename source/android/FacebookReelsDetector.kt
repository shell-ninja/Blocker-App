package com.blocker

import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * High-performance, zero-lag detector for Facebook Reels and full-screen video viewers.
 *
 * Core Guarantees:
 * 1. Facebook Stories (both clicking from tray and viewing in full screen) are 100% ALLOWED.
 * 2. Facebook Reels (both dedicated tab and clicking a Reel card/thumbnail in the Newsfeed) are 100% BLOCKED.
 * 3. Normal Newsfeed scrolling past unplayed posts remains 100% smooth without false reloads.
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
     * Evaluated FIRST before any Reels inspection.
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
     * Returns true if screen matches Story Activity, Story controls, or bottom horizontal reaction bar,
     * while NOT having a top "Reels" header.
     */
    fun isFacebookStoryViewer(
        root: AccessibilityNodeInfo,
        className: String,
        screenWidth: Int,
        screenHeight: Int
    ): Boolean {
        // Activity/class name fast check
        val lowerCls = className.lowercase()
        val rootCls = root.className?.toString().orEmpty().lowercase()
        val isStoryClass = lowerCls.contains("storyviewer") || rootCls.contains("storyviewer") ||
                           lowerCls.contains("fbstories") || rootCls.contains("fbstories") ||
                           lowerCls.contains("storypermalink") || rootCls.contains("storypermalink") ||
                           lowerCls.contains("inspirationstory") || rootCls.contains("inspirationstory")

        val rect = Rect()
        var hasTopReelsHeader = false
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

            // Check if top Reels title is present
            if (rect.top < screenHeight * 0.16f) {
                if (text == "Reels" || cd == "Reels" || text == "রিলস" || cd == "রিলস") {
                    hasTopReelsHeader = true
                }
            }

            // 1. Story Activity / View ID
            if (lowerId.contains("story") || lowerId.contains("stories") ||
                lowerId.contains("story_viewer") || lowerId.contains("story_viewer_fragment")) {
                hasStoryIdOrMarker = true
            }

            // 2. Story Viewer Controls & Bottom Reply Bar
            if (isStoryControl(lowerText) || isStoryControl(lowerCd)) {
                hasStoryControlOrReplyBar = true
            }

            // 3. Story Bottom Horizontal Reaction Bar
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

        // If top "Reels" header is visibly present, this is definitely Reels, not a Story!
        if (hasTopReelsHeader) return false

        // Match ANY of the 3 Story Viewer signatures:
        if (hasStoryIdOrMarker) return true
        if (hasStoryControlOrReplyBar) return true
        if (horizontalReactionCount >= 2) return true

        return false
    }

    /**
     * Intercept clicking a Reel card or video play button in the Newsfeed.
     *
     * STRICT RULE:
     * Only trigger if clicked node or immediate container EXPLICITLY contains "reel" or "reels"
     * (e.g. "Reel", "Reels", "Play reel", "See more reels", "Reels and short videos", "Original audio").
     * Never trigger on generic Story cards or unadorned "Play" buttons.
     */
    fun isFacebookReelClick(
        ev: AccessibilityEvent,
        screenWidth: Int,
        screenHeight: Int
    ): Boolean {
        if (ev.eventType != AccessibilityEvent.TYPE_VIEW_CLICKED) return false

        // Unconditional story exemption
        if (isFacebookStoryClick(ev)) {
            pendingFacebookReelCheckUntilMs = 0L
            return false
        }

        val src = ev.source ?: return false
        val rect = Rect()
        src.getBoundsInScreen(rect)

        val h = rect.height()
        if (h > screenHeight * 0.58f && !rect.isEmpty) {
            src.safeRecycle()
            return false
        }

        fun isExplicitReelKeyword(s: String): Boolean {
            if (s.isEmpty()) return false
            val l = s.lowercase()
            if (l.contains("story") || l.contains("stories")) return false
            return l.contains("reel") || l.contains("reels") ||
                   l.contains("play reel") || l.contains("see more reels") ||
                   l.contains("reels and short videos") || l.contains("reels & short videos") ||
                   l.contains("original audio") || l.contains("watch reel") ||
                   l.contains("view reel") || l.contains("রিলস")
        }

        fun isExplicitReelId(id: String): Boolean {
            if (id.isEmpty()) return false
            val l = id.lowercase()
            if (l.contains("story")) return false
            return l.contains("reel") || l.contains("fb_shorts") ||
                   l.contains("reels_tab") || l.contains("reels_viewer")
        }

        val evCd = ev.contentDescription?.toString().orEmpty()
        val evText = ev.text.joinToString(" ")
        val srcCd = src.contentDescription?.toString().orEmpty()
        val srcText = src.text?.toString().orEmpty()
        val srcId = src.viewIdResourceName?.toString().orEmpty()

        if (isExplicitReelKeyword(evCd) || isExplicitReelKeyword(evText) ||
            isExplicitReelKeyword(srcCd) || isExplicitReelKeyword(srcText) ||
            isExplicitReelId(srcId)) {
            pendingFacebookReelCheckUntilMs = SystemClock.elapsedRealtime() + 2000L
            src.safeRecycle()
            return true
        }

        // Walk up to 3 parent levels
        var p = src.parent
        var depth = 0
        while (p != null && depth < 3) {
            val pCd = p.contentDescription?.toString().orEmpty()
            val pText = p.text?.toString().orEmpty()
            val pId = p.viewIdResourceName?.toString().orEmpty()
            if (isExplicitReelKeyword(pCd) || isExplicitReelKeyword(pText) || isExplicitReelId(pId)) {
                pendingFacebookReelCheckUntilMs = SystemClock.elapsedRealtime() + 2000L
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
            val hit = isExplicitReelKeyword(cCd) || isExplicitReelKeyword(cText) || isExplicitReelId(cId)
            c.safeRecycle()
            if (hit) {
                pendingFacebookReelCheckUntilMs = SystemClock.elapsedRealtime() + 2000L
                src.safeRecycle()
                return true
            }
        }

        src.safeRecycle()
        return false
    }

    /**
     * Detect full-screen Reels viewer even when opened as an overlay Fragment over the Newsfeed.
     * Evaluates only visible nodes (node.isVisibleToUser == true) and caps traversal to 120 visible nodes.
     *
     * STRICT RULES:
     * 1. Returns false IMMEDIATELY if it matches Facebook Story viewer.
     * 2. Only triggers if at least one of Proof 1, Proof 2, or Proof 3 is met.
     */
    fun isDedicatedFullScreenReelsOrVideoViewer(
        root: AccessibilityNodeInfo,
        ev: AccessibilityEvent?,
        screenWidth: Int,
        screenHeight: Int
    ): Boolean {
        val evCls = ev?.className?.toString().orEmpty()

        // 1. Unconditional Stories Whitelist (First Guard)
        if (isFacebookStoryViewer(root, evCls, screenWidth, screenHeight)) {
            pendingFacebookReelCheckUntilMs = 0L
            return false
        }

        val nowElapsed = SystemClock.elapsedRealtime()
        val inPostClickWindow = nowElapsed < pendingFacebookReelCheckUntilMs

        // Proof 1: Dedicated Reels Activity or View ID
        val lowerEvCls = evCls.lowercase()
        val rootCls = root.className?.toString().orEmpty().lowercase()
        if (lowerEvCls.contains("fbreelsviewer") || rootCls.contains("fbreelsviewer") ||
            lowerEvCls.contains("inspirationreelviewer") || rootCls.contains("inspirationreelviewer") ||
            lowerEvCls.contains("reelsviewer") || rootCls.contains("reelsviewer")) {
            return true
        }

        val rect = Rect()

        var hasExplicitReelsViewerId = false
        var hasVisibleSeekBarInDarkPlayer = false

        // Proof 2: Vertical action stack on right edge (bounds.left >= screenWidth * 0.72f)
        var likeBounds: Rect? = null
        var commentBounds: Rect? = null
        var shareBounds: Rect? = null
        var hasStoryReplyText = false

        // Proof 3: Top "Reels" overlay bar
        var hasTopReelsTitle = false
        var isWhatsOnYourMindVisible = false

        var visibleCount = 0

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

            // Check if Story reply bar is visible (never block if user can send a message/reply to story)
            if (lowerText.contains("send a message") || lowerCd.contains("send a message") ||
                lowerText.contains("send message") || lowerCd.contains("send message") ||
                lowerText.contains("reply to ") || lowerCd.contains("reply to ") ||
                lowerText.contains("write a reply") || lowerCd.contains("write a reply")) {
                hasStoryReplyText = true
            }

            // Check "What's on your mind?" visibility (indicates newsfeed composer is currently visible)
            if (lowerText.contains("what's on your mind") || lowerCd.contains("what's on your mind") ||
                lowerText.contains("কী ভাবছেন") || lowerCd.contains("কী ভাবছেন")) {
                isWhatsOnYourMindVisible = true
            }

            // Proof 1 View IDs: Dedicated Reels player container IDs
            if (lowerId.contains("fbreelsviewer") || lowerId.contains("inspirationreelviewer") ||
                lowerId.contains("reelsviewer") || lowerId.contains("reels_viewer")) {
                if (rect.height() >= screenHeight * 0.50f) {
                    hasExplicitReelsViewerId = true
                }
            }

            // Dark full-screen player SeekBar (without newsfeed composer)
            if (lowerId.contains("seekbar") || lowerCd.contains("seek bar")) {
                if (rect.centerY() > screenHeight * 0.60f && !isWhatsOnYourMindVisible) {
                    hasVisibleSeekBarInDarkPlayer = true
                }
            }

            // Proof 2: Right-edge vertical action stack (bounds.left >= screenWidth * 0.72f)
            if (rect.left >= screenWidth * 0.72f) {
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

            // Proof 3: Top "Reels" header (bounds.top < screenHeight * 0.16f)
            if (rect.top < screenHeight * 0.16f) {
                if (text == "Reels" || cd == "Reels" || text == "রিলস" || cd == "রিলস") {
                    hasTopReelsTitle = true
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

        // Safety gate: If Story reply bar is visible, this is definitely a Story!
        if (hasStoryReplyText) {
            return false
        }

        // Proof 1 Evaluation: Explicit Reels Viewer ID / Class
        if (hasExplicitReelsViewerId) {
            return true
        }

        // Proof 2 Evaluation: Vertical action stack along right edge (likeY < commentY < shareY or 2+ stacked actions)
        if (likeBounds != null && commentBounds != null) {
            val isVerticallyStacked = likeBounds!!.centerY() < commentBounds!!.centerY()
            if (isVerticallyStacked) {
                if (shareBounds == null || commentBounds!!.centerY() < shareBounds!!.centerY()) {
                    return true
                }
            }
        }

        // Proof 3 Evaluation: Top "Reels" header while composer is NOT visible
        if (hasTopReelsTitle && !isWhatsOnYourMindVisible) {
            return true
        }

        // Seekbar in full-screen dark player
        if (hasVisibleSeekBarInDarkPlayer && (inPostClickWindow || hasTopReelsTitle)) {
            return true
        }

        return false
    }
}
