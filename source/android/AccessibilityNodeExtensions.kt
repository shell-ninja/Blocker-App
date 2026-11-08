package com.blocker

import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import java.util.ArrayDeque

/**
 * Null-safe & exception-safe extension functions for [AccessibilityNodeInfo].
 * Remote binder nodes can become stale mid-traversal or throw DeadObjectException / IllegalStateException.
 * These helpers wrap all access in try-catch and strictly avoid deprecated [recycle] on API 33+.
 */
fun AccessibilityNodeInfo?.safeText(): CharSequence? = runCatching { this?.text }.getOrNull()

fun AccessibilityNodeInfo?.safeDesc(): CharSequence? = runCatching { this?.contentDescription }.getOrNull()

fun AccessibilityNodeInfo?.safeViewId(): String? = runCatching { this?.viewIdResourceName }.getOrNull()

fun AccessibilityNodeInfo?.safeClassName(): CharSequence? = runCatching { this?.className }.getOrNull()

fun AccessibilityNodeInfo?.safeChildCount(): Int = runCatching { this?.childCount ?: 0 }.getOrDefault(0)

fun AccessibilityNodeInfo?.safeGetChild(i: Int): AccessibilityNodeInfo? = runCatching { this?.getChild(i) }.getOrNull()

fun AccessibilityNodeInfo?.safeBounds(rect: Rect) {
    runCatching { this?.getBoundsInScreen(rect) }
}

fun AccessibilityNodeInfo?.safeIsSelected(): Boolean = runCatching { this?.isSelected == true }.getOrDefault(false)

fun AccessibilityNodeInfo?.safeIsChecked(): Boolean = runCatching { this?.isChecked == true }.getOrDefault(false)

fun AccessibilityNodeInfo?.safeIsClickable(): Boolean = runCatching { this?.isClickable == true }.getOrDefault(false)

fun AccessibilityNodeInfo?.safePerformClick(): Boolean = runCatching {
    this?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
}.getOrDefault(false)

fun AccessibilityNodeInfo?.safePerformAction(action: Int): Boolean = runCatching {
    this?.performAction(action) == true
}.getOrDefault(false)

@Suppress("DEPRECATION")
fun AccessibilityNodeInfo?.safeRecycle() {
    if (this == null) return
    if (Build.VERSION.SDK_INT < 33) {
        runCatching { this.recycle() }
    }
}

/**
 * Thread-safe, bounded tree traversal scanner.
 * Uses a local ArrayDeque<AccessibilityNodeInfo>(200) per scan pass so that
 * onAccessibilityEvent and the ForegroundWatchdog never mutate the same queue concurrently.
 * Enforces a hard cap of 180 visited nodes per pass and tracks visited node identity hashes
 * to guarantee that cyclic trees never cause ANRs, StackOverflowError, or OutOfMemoryError.
 */
object ZeroAllocWindowScanner {
    const val MAX_VISITED_NODES = 180

    fun scanText(root: AccessibilityNodeInfo?, maxBudget: Int = 180, maxChars: Int = 2000): String {
        if (root == null) return ""
        val sb = StringBuilder()
        val queue = ArrayDeque<AccessibilityNodeInfo>(200)
        val visitedHashes = HashSet<Int>(200)
        var visitedCount = 0

        queue.add(root)
        visitedHashes.add(System.identityHashCode(root))

        val limit = minOf(maxBudget, MAX_VISITED_NODES)

        while (queue.isNotEmpty() && visitedCount < limit && sb.length < maxChars) {
            val node = queue.removeFirst()
            visitedCount++

            val isPassword = runCatching { node.isPassword }.getOrDefault(false)
            if (!isPassword) {
                node.safeText()?.let { if (it.isNotBlank()) sb.append(it).append(' ') }
                node.safeDesc()?.let { if (it.isNotBlank()) sb.append(it).append(' ') }
            }

            val childCount = node.safeChildCount()
            for (i in 0 until childCount) {
                if (visitedCount + queue.size >= limit || sb.length >= maxChars) break
                val child = node.safeGetChild(i) ?: continue
                val hash = System.identityHashCode(child)
                if (visitedHashes.add(hash)) {
                    queue.add(child)
                } else {
                    child.safeRecycle()
                }
            }

            if (node !== root) {
                node.safeRecycle()
            }
        }

        // Clean up remaining unvisited queue items safely
        while (queue.isNotEmpty()) {
            val rem = queue.removeFirst()
            if (rem !== root) {
                rem.safeRecycle()
            }
        }

        return sb.toString()
    }
}
