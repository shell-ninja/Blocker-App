package com.blocker

import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.blocker.data.CompiledRuleSnapshot
import com.blocker.data.RuleSnapshotHolder
import java.util.Locale

/**
 * High-performance, Litho-aware detector for Facebook Reels AND Facebook Videos.
 *
 * Guarantees:
 * 1. Facebook Stories (tray click & full-screen viewer) are 100% ALLOWED.
 * 2. Dedicated Facebook Reels (Reels tab + Reels overlay opened from Newsfeed) and
 *    Facebook Videos (Watch tab + dedicated players) are 100% BLOCKED.
 * 3. Newsfeed scroll/thumbnail events NEVER trigger GLOBAL_ACTION_BACK or page reloads.
 * 4. Inline videos on the Home Newsfeed are paused in-place (no BACK action) to avoid refresh loops.
 */
object FacebookReelsDetector {

    @Volatile
    var pendingFacebookPlayerCheckUntilMs: Long = 0L

    // Backward-compatibility aliases
    @Volatile
    var pendingFacebookReelCheckUntilMs: Long = 0L

    @Volatile
    var pendingFacebookVideoCheckUntilMs: Long = 0L

    val DURATION_TIMESTAMP_REGEX = Regex("""^\d{1,2}:\d{2}(:\d{2})?$""")

    enum class FacebookBlockAction {
        NONE,
        EXIT_BACK,      // Dedicated Reels, Watch tab, or full-screen player
        PAUSE_INLINE    // Active playing video right inside Home Newsfeed
    }

    data class FacebookVerdict(
        val action: FacebookBlockAction,
        val reason: String,
        val pauseNode: AccessibilityNodeInfo? = null
    )

    private fun AccessibilityNodeInfo?.safeRecycle() {
        if (this == null) return
        runCatching {
            if (Build.VERSION.SDK_INT < 33) {
                recycle()
            }
        }
    }

    // -------------------------------------------------------------------------
    // 1. Stories Whitelist (Checked First)
    // -------------------------------------------------------------------------
    fun isFacebookStoryViewerOrStoryClick(
        root: AccessibilityNodeInfo?,
        ev: AccessibilityEvent?,
        screenWidth: Int = 1080,
        screenHeight: Int = 2400
    ): Boolean {
        if (ev != null && ev.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            if (isFacebookStoryClick(ev)) {
                pendingFacebookPlayerCheckUntilMs = 0L
                return true
            }
        }

        if (root != null) {
            val cls = ev?.className?.toString().orEmpty()
            if (isFacebookStoryViewer(root, cls, screenWidth, screenHeight)) {
                pendingFacebookPlayerCheckUntilMs = 0L
                return true
            }
        }

        return false
    }

    fun isFacebookStoryClick(ev: AccessibilityEvent): Boolean {
        if (ev.eventType != AccessibilityEvent.TYPE_VIEW_CLICKED) return false

        fun isStoryMarker(s: String): Boolean {
            if (s.isEmpty()) return false
            val l = s.lowercase(Locale.ROOT)
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

    fun isFacebookStoryViewer(
        root: AccessibilityNodeInfo,
        className: String,
        screenWidth: Int,
        screenHeight: Int
    ): Boolean {
        val lowerCls = className.lowercase(Locale.ROOT)
        val rootCls = root.className?.toString().orEmpty().lowercase(Locale.ROOT)
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
            val lowerText = text.lowercase(Locale.ROOT)
            val lowerCd = cd.lowercase(Locale.ROOT)
            val lowerId = id.lowercase(Locale.ROOT)

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

            val count = node.childCount
            for (i in count - 1 downTo 0) {
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

    // -------------------------------------------------------------------------
    // 2. Smart Prefix/Token Action Helpers (Handles "Like, 15K", "Comment, 420")
    // -------------------------------------------------------------------------
    fun isLikeAction(lower: String): Boolean {
        if (lower.isEmpty()) return false
        return lower == "like" || lower.startsWith("like,") || lower.startsWith("like ") ||
               lower.startsWith("like.") || lower.contains("like button") ||
               lower.startsWith("react") || lower.contains("পছন্দ") || lower.contains("লাইক")
    }

    fun isCommentAction(lower: String): Boolean {
        if (lower.isEmpty()) return false
        return lower == "comment" || lower.startsWith("comment,") || lower.startsWith("comment ") ||
               lower.startsWith("comment.") || lower.contains("comment button") ||
               lower.contains("মন্তব্য") || lower.contains("কমেন্ট")
    }

    fun isShareAction(lower: String): Boolean {
        if (lower.isEmpty()) return false
        return lower == "share" || lower.startsWith("share,") || lower.startsWith("share ") ||
               lower.startsWith("share.") || lower == "send" || lower.startsWith("send,") ||
               lower.startsWith("send ") || lower.contains("শেয়ার") || lower.contains("শেয়ার")
    }

    // -------------------------------------------------------------------------
    // 3. Litho-Aware Visible Node Model
    // -------------------------------------------------------------------------
    data class VisibleNodeData(
        val rect: Rect,
        val label: String,
        val lower: String,
        val id: String,
        val cls: String,
        val isSelected: Boolean,
        val isClickable: Boolean,
        val liveNode: AccessibilityNodeInfo? = null
    )

    // -------------------------------------------------------------------------
    // 4. Main Litho-Aware Evaluation
    // -------------------------------------------------------------------------
    fun evaluateFacebook(
        root: AccessibilityNodeInfo?,
        ev: AccessibilityEvent?,
        screenWidth: Int = 1080,
        screenHeight: Int = 2400,
        snapshot: CompiledRuleSnapshot = RuleSnapshotHolder.snapshot
    ): FacebookVerdict {
        if (!snapshot.blockFacebookReels) {
            pendingFacebookPlayerCheckUntilMs = 0L
            return FacebookVerdict(FacebookBlockAction.NONE, "Disabled")
        }

        // Line-1 Whitelist: Stories
        if (isFacebookStoryViewerOrStoryClick(root, ev, screenWidth, screenHeight)) {
            pendingFacebookPlayerCheckUntilMs = 0L
            return FacebookVerdict(FacebookBlockAction.NONE, "Story Whitelist")
        }

        // TRIGGER D: Explicit User Click Interception (TYPE_VIEW_CLICKED)
        if (ev != null && ev.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            val clickVerdict = checkExplicitUserClick(ev)
            if (clickVerdict.action != FacebookBlockAction.NONE) {
                return clickVerdict
            }
        }

        if (root == null) return FacebookVerdict(FacebookBlockAction.NONE, "Null root")

        val density = (screenWidth / 360f).coerceAtLeast(1.0f)
        val dp52 = (52 * density).toInt()
        val dp36 = (36 * density).toInt()
        val dp24 = (24 * density).toInt()
        val dp30 = (30 * density).toInt()

        // Reverse-order traversal collecting up to 350 visible nodes
        val collected = ArrayList<VisibleNodeData>(350)
        val tempRect = Rect()
        var pauseNodeFound: AccessibilityNodeInfo? = null

        fun collectReverse(node: AccessibilityNodeInfo?, depth: Int) {
            if (node == null || depth > 25 || collected.size >= 350) return
            if (!node.isVisibleToUser) return

            node.getBoundsInScreen(tempRect)
            if (tempRect.right > 0 && tempRect.bottom > 0 &&
                tempRect.left < screenWidth && tempRect.top < screenHeight) {

                val text = node.text?.toString().orEmpty()
                val cd = node.contentDescription?.toString().orEmpty()
                val rawLabel = if (cd.isNotBlank()) cd else text
                val label = rawLabel.trim()
                val lower = label.lowercase(Locale.ROOT)
                val id = node.viewIdResourceName?.toString().orEmpty().lowercase(Locale.ROOT)
                val cls = node.className?.toString().orEmpty().lowercase(Locale.ROOT)

                val stateDesc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    node.stateDescription?.toString()?.lowercase(Locale.ROOT).orEmpty()
                } else ""

                val isSelected = node.isSelected ||
                    lower.contains("selected") ||
                    lower.contains("active") ||
                    stateDesc.contains("selected") ||
                    stateDesc.contains("active")

                val isPause = lower.contains("pause video") || lower == "pause" ||
                              lower == "pause, button" || lower.startsWith("pause,") ||
                              lower.contains("ভিডিও থামান")

                var retainedLiveNode: AccessibilityNodeInfo? = null
                if (isPause && pauseNodeFound == null && node.isClickable) {
                    pauseNodeFound = AccessibilityNodeInfo.obtain(node)
                    retainedLiveNode = pauseNodeFound
                }

                collected.add(
                    VisibleNodeData(
                        rect = Rect(tempRect),
                        label = label,
                        lower = lower,
                        id = id,
                        cls = cls,
                        isSelected = isSelected,
                        isClickable = node.isClickable,
                        liveNode = retainedLiveNode
                    )
                )
            }

            // CRITICAL: Iterate children in REVERSE order (childCount - 1 downTo 0)
            val childCount = node.childCount
            for (i in childCount - 1 downTo 0) {
                if (collected.size >= 350) break
                val child = node.getChild(i) ?: continue
                collectReverse(child, depth + 1)
                child.safeRecycle()
            }
        }

        collectReverse(root, 0)

        // ---------------------------------------------------------------------
        // Check Feed Post & Home Tab Signals
        // ---------------------------------------------------------------------
        var isTopTabHomeSelected = false
        var likeFeedBounds: Rect? = null
        var commentFeedBounds: Rect? = null
        var shareFeedBounds: Rect? = null
        var hasHorizontalFeedPostFooter = false

        for (item in collected) {
            // Home tab in top 24%
            if (item.rect.top <= screenHeight * 0.24f) {
                val isHomeTab = item.lower.contains("home") || item.lower.contains("হোম")
                if (isHomeTab && item.isSelected) {
                    isTopTabHomeSelected = true
                }
            }

            // Collect Like/Comment/Share for horizontal footer
            if (isLikeAction(item.lower) && likeFeedBounds == null) {
                likeFeedBounds = item.rect
            } else if (isCommentAction(item.lower) && commentFeedBounds == null) {
                commentFeedBounds = item.rect
            } else if (isShareAction(item.lower) && shareFeedBounds == null) {
                shareFeedBounds = item.rect
            }

            if (likeFeedBounds != null) {
                val lb = likeFeedBounds
                val cb = commentFeedBounds
                if (cb != null) {
                    val dy = Math.abs(lb.centerY() - cb.centerY())
                    val dx = cb.centerX() - lb.centerX()
                    if (dy <= dp24 && dx >= dp30) {
                        hasHorizontalFeedPostFooter = true
                    }
                }
                val sb = shareFeedBounds
                if (sb != null) {
                    val dy = Math.abs(lb.centerY() - sb.centerY())
                    val dx = sb.centerX() - lb.centerX()
                    if (dy <= dp24 && dx >= dp30) {
                        hasHorizontalFeedPostFooter = true
                    }
                }
            }
        }

        val isOnHomeTabFeed = isTopTabHomeSelected || hasHorizontalFeedPostFooter

        // ---------------------------------------------------------------------
        // TRIGGER A — Full-Screen Reels Player (Checked FIRST!)
        // ---------------------------------------------------------------------
        // A1. Vertical Right-Edge Action Stack (bounds.left >= screenWidth * 0.65f)
        val rightActions = ArrayList<Pair<String, Rect>>()
        for (item in collected) {
            if (item.rect.left >= screenWidth * 0.65f) {
                if (isLikeAction(item.lower)) rightActions.add("like" to item.rect)
                else if (isCommentAction(item.lower)) rightActions.add("comment" to item.rect)
                else if (isShareAction(item.lower)) rightActions.add("share" to item.rect)
            }
        }

        if (rightActions.map { it.first }.distinct().size >= 2) {
            for (i in 0 until rightActions.size) {
                for (j in i + 1 until rightActions.size) {
                    val a = rightActions[i]
                    val b = rightActions[j]
                    if (a.first != b.first) {
                        val dx = Math.abs(a.second.centerX() - b.second.centerX())
                        val dy = Math.abs(a.second.centerY() - b.second.centerY())
                        if (dx <= dp52 && dy >= dp36) {
                            pauseNodeFound?.safeRecycle()
                            return FacebookVerdict(FacebookBlockAction.EXIT_BACK, "Facebook Reels (Vertical Action Stack)")
                        }
                    }
                }
            }
        }

        // A2. Unique Reels Player UI Elements
        for (item in collected) {
            // Bottom Reels bar
            if (item.rect.top >= screenHeight * 0.62f) {
                if (item.lower.contains("add a comment") || item.lower.contains("original audio") ||
                    item.lower.contains("use audio") || item.lower.contains("remix this reel") ||
                    item.lower.contains("audio track") || item.lower.contains("see more reels") ||
                    item.lower.contains("sponsored • reels")) {
                    pauseNodeFound?.safeRecycle()
                    return FacebookVerdict(FacebookBlockAction.EXIT_BACK, "Facebook Reels (Bottom Bar)")
                }
            }

            // Top full-screen Reels overlay
            if (item.rect.top <= screenHeight * 0.16f) {
                val isReelsExact = item.lower == "reels" || item.lower == "রিলস"
                if (isReelsExact) {
                    val hasReelsPill = collected.any {
                        it.rect.top <= screenHeight * 0.16f &&
                        (it.lower.contains("camera") || it.lower.contains("create") || it.lower.contains("search reels"))
                    }
                    if (hasReelsPill && !item.lower.contains("reels and short videos")) {
                        pauseNodeFound?.safeRecycle()
                        return FacebookVerdict(FacebookBlockAction.EXIT_BACK, "Facebook Reels (Top Overlay)")
                    }
                }
            }
        }

        // ---------------------------------------------------------------------
        // TRIGGER B — Facebook Video / Watch Tab or Reels Dedicated Tab
        // ---------------------------------------------------------------------
        for (item in collected) {
            if (item.rect.top <= screenHeight * 0.24f) {
                val isVideoOrReelsTab = item.lower.contains("video") || item.lower.contains("watch") ||
                                        item.lower.contains("reels") || item.lower.contains("রিলস") ||
                                        item.lower.contains("ভিডিও") || item.lower.contains("ওয়াচ")
                val isNotHome = !item.lower.contains("home") && !item.lower.contains("হোম")

                if (isVideoOrReelsTab && isNotHome && item.isSelected) {
                    pauseNodeFound?.safeRecycle()
                    return FacebookVerdict(FacebookBlockAction.EXIT_BACK, "Facebook Video & Reels Tab Selected")
                }

                val isHeaderLabel = item.lower == "video" || item.lower == "watch" || item.lower == "reels" ||
                                    item.lower == "ভিডিও" || item.lower == "ওয়াচ" || item.lower == "রিলস"
                if (isHeaderLabel) {
                    val hasVideoTabPills = collected.any {
                        it.rect.top <= screenHeight * 0.24f &&
                        (it.lower.contains("search videos") || it.lower.contains("for you") ||
                         it.lower.contains("saved videos") || it.lower.contains("following"))
                    }
                    if (hasVideoTabPills) {
                        pauseNodeFound?.safeRecycle()
                        return FacebookVerdict(FacebookBlockAction.EXIT_BACK, "Facebook Watch/Video Tab Header")
                    }
                }
            }
        }

        // ---------------------------------------------------------------------
        // TRIGGER C — Dedicated / Full-Screen Video Player or Active Playing Video
        // ---------------------------------------------------------------------
        val evCls = ev?.className?.toString().orEmpty().lowercase(Locale.ROOT)
        val rootCls = root.className?.toString().orEmpty().lowercase(Locale.ROOT)
        val isPlayerActivity =
            evCls.contains("fbreelsviewer") || rootCls.contains("fbreelsviewer") ||
            evCls.contains("inspirationreelviewer") || rootCls.contains("inspirationreelviewer") ||
            evCls.contains("fullscreenvideo") || rootCls.contains("fullscreenvideo") ||
            evCls.contains("videoplayer") || rootCls.contains("videoplayer") ||
            evCls.contains("tahoe") || rootCls.contains("tahoe") ||
            evCls.contains("fbshortsviewer") || rootCls.contains("fbshortsviewer") ||
            evCls.contains("clipsviewer") || rootCls.contains("clipsviewer")

        val isMainTab = evCls.contains("fbmaintabactivity") || rootCls.contains("fbmaintabactivity") ||
                        evCls.contains("mainactivity") || rootCls.contains("mainactivity")

        if (isPlayerActivity && !isMainTab) {
            pauseNodeFound?.safeRecycle()
            return FacebookVerdict(FacebookBlockAction.EXIT_BACK, "Facebook Dedicated Video Player Activity")
        }

        var hasActivePlaybackControls = false
        var isPauseControl = false

        for (item in collected) {
            if (item.lower.contains("pause video") || item.lower == "pause" ||
                item.lower == "pause, button" || item.lower.startsWith("pause,") ||
                item.lower.contains("ভিডিও থামান")) {
                hasActivePlaybackControls = true
                isPauseControl = true
                break
            }
            if (item.lower.contains("rewind 10") || item.lower.contains("forward 10") ||
                item.lower.contains("exit fullscreen") || item.lower.contains("exit full screen") ||
                item.lower.contains("playback speed") || item.lower.contains("video settings") ||
                item.lower.contains("watch more videos") || item.lower.contains("suggested videos") ||
                item.lower.contains("up next")) {
                hasActivePlaybackControls = true
                break
            }
            if (item.id.contains("reels_viewer") || item.id.contains("fbreelsviewer") ||
                item.id.contains("fullscreen_video") || item.id.contains("video_fullscreen_player") ||
                item.id.contains("unified_video_viewer")) {
                hasActivePlaybackControls = true
                break
            }
        }

        if (hasActivePlaybackControls) {
            if (isOnHomeTabFeed && isPauseControl) {
                return FacebookVerdict(FacebookBlockAction.PAUSE_INLINE, "Facebook Inline Video (Home Feed)", pauseNodeFound)
            } else {
                pauseNodeFound?.safeRecycle()
                return FacebookVerdict(FacebookBlockAction.EXIT_BACK, "Facebook Video Player Active")
            }
        }

        // Post-click 2.5s window check
        val now = SystemClock.elapsedRealtime()
        if (now < pendingFacebookPlayerCheckUntilMs) {
            if (!isOnHomeTabFeed) {
                pauseNodeFound?.safeRecycle()
                return FacebookVerdict(FacebookBlockAction.EXIT_BACK, "Facebook Pending Player Open")
            }
        }

        pauseNodeFound?.safeRecycle()
        return FacebookVerdict(FacebookBlockAction.NONE, "Allowed Newsfeed")
    }

    // -------------------------------------------------------------------------
    // 5. Explicit User Click Inspection
    // -------------------------------------------------------------------------
    private fun checkExplicitUserClick(ev: AccessibilityEvent): FacebookVerdict {
        if (ev.eventType != AccessibilityEvent.TYPE_VIEW_CLICKED) {
            return FacebookVerdict(FacebookBlockAction.NONE, "Not a click")
        }

        if (isFacebookStoryClick(ev)) {
            pendingFacebookPlayerCheckUntilMs = 0L
            return FacebookVerdict(FacebookBlockAction.NONE, "Story click")
        }

        fun isExplicitReelOrVideoTarget(s: String): Boolean {
            if (s.isEmpty()) return false
            val l = s.lowercase(Locale.ROOT).trim()
            if (l.contains("story") || l.contains("stories")) return false
            return l == "reels" || l == "reel" || l == "রিলস" ||
                   l.contains("play reel") || l.contains("original audio") ||
                   l.contains("reels, tab") || l.contains("reels tab") ||
                   l.contains("tab reels") || l.contains("watch, tab") ||
                   l.contains("video, tab") || l.contains("tab video") ||
                   l.contains("tab watch") || l.contains("play video") ||
                   l.contains("watch video")
        }

        val evCd = ev.contentDescription?.toString().orEmpty()
        val evText = ev.text.joinToString(" ")

        var matched = isExplicitReelOrVideoTarget(evCd) || isExplicitReelOrVideoTarget(evText)
        var isDirectTabOrCard = matched

        if (!matched) {
            val src = ev.source
            if (src != null) {
                val srcCd = src.contentDescription?.toString().orEmpty()
                val srcText = src.text?.toString().orEmpty()
                val srcId = src.viewIdResourceName?.toString().orEmpty().lowercase(Locale.ROOT)
                if (isExplicitReelOrVideoTarget(srcCd) || isExplicitReelOrVideoTarget(srcText) ||
                    srcId.contains("reels") || srcId.contains("video_tab") || srcId.contains("watch_tab")) {
                    matched = true
                    isDirectTabOrCard = true
                }

                if (!matched) {
                    var p = src.parent
                    var depth = 0
                    while (p != null && depth < 3) {
                        val pCd = p.contentDescription?.toString().orEmpty()
                        val pText = p.text?.toString().orEmpty()
                        val pId = p.viewIdResourceName?.toString().orEmpty().lowercase(Locale.ROOT)
                        if (isExplicitReelOrVideoTarget(pCd) || isExplicitReelOrVideoTarget(pText) ||
                            pId.contains("reels") || pId.contains("video_tab") || pId.contains("watch_tab")) {
                            matched = true
                            isDirectTabOrCard = true
                            p.safeRecycle()
                            break
                        }
                        val nextP = p.parent
                        p.safeRecycle()
                        p = nextP
                        depth++
                    }
                    p?.safeRecycle()
                }
                src.safeRecycle()
            }
        }

        if (matched) {
            pendingFacebookPlayerCheckUntilMs = SystemClock.elapsedRealtime() + 2500L
            if (isDirectTabOrCard) {
                return FacebookVerdict(FacebookBlockAction.EXIT_BACK, "Facebook Explicit Reels/Video Tap")
            }
        }

        return FacebookVerdict(FacebookBlockAction.NONE, "Allowed click")
    }

    // -------------------------------------------------------------------------
    // Backward Compatibility API
    // -------------------------------------------------------------------------
    fun evaluate(
        root: AccessibilityNodeInfo?,
        ev: AccessibilityEvent?,
        screenWidth: Int = 1080,
        screenHeight: Int = 2400,
        snapshot: CompiledRuleSnapshot = RuleSnapshotHolder.snapshot
    ): Boolean {
        val verdict = evaluateFacebook(root, ev, screenWidth, screenHeight, snapshot)
        verdict.pauseNode?.safeRecycle()
        return verdict.action != FacebookBlockAction.NONE
    }

    fun isDedicatedFullScreenReelsOrVideoViewer(
        root: AccessibilityNodeInfo,
        ev: AccessibilityEvent?,
        screenWidth: Int = 1080,
        screenHeight: Int = 2400,
        snapshot: CompiledRuleSnapshot = RuleSnapshotHolder.snapshot
    ): Boolean = evaluate(root, ev, screenWidth, screenHeight, snapshot)

    fun isFacebookVideoOrReels(
        root: AccessibilityNodeInfo?,
        ev: AccessibilityEvent?,
        screenWidth: Int = 1080,
        screenHeight: Int = 2400,
        snapshot: CompiledRuleSnapshot = RuleSnapshotHolder.snapshot
    ): Boolean = evaluate(root, ev, screenWidth, screenHeight, snapshot)

    fun isFacebookVideoOrReelClick(
        ev: AccessibilityEvent,
        screenWidth: Int = 1080,
        screenHeight: Int = 2400,
        snapshot: CompiledRuleSnapshot = RuleSnapshotHolder.snapshot
    ): Boolean {
        val verdict = checkExplicitUserClick(ev)
        return verdict.action != FacebookBlockAction.NONE
    }

    fun isFacebookReelClick(
        ev: AccessibilityEvent,
        screenWidth: Int = 1080,
        screenHeight: Int = 2400,
        snapshot: CompiledRuleSnapshot = RuleSnapshotHolder.snapshot
    ): Boolean = isFacebookVideoOrReelClick(ev, screenWidth, screenHeight, snapshot)

    fun isVideoOrReelIndicator(s: String): Boolean {
        val l = s.lowercase(Locale.ROOT).trim()
        return l == "reels" || l == "reel" || l.contains("play reel") ||
               l.contains("play video") || l.contains("original audio")
    }

    fun isVideoNode(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        val cd = node.contentDescription?.toString().orEmpty()
        val text = node.text?.toString().orEmpty()
        val id = node.viewIdResourceName?.toString().orEmpty().lowercase(Locale.ROOT)
        return id.contains("reels") || id.contains("video_player") ||
               cd.contains("Reel", ignoreCase = true) || text.contains("Reel", ignoreCase = true)
    }
}
