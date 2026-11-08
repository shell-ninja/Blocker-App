package com.blocker

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout

object TamperShieldManager {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var shieldView: FrameLayout? = null
    private var shieldParams: WindowManager.LayoutParams? = null
    private var isAttached = false
    @Volatile var isShieldActive = false
        private set

    @Volatile private var lastShieldShowTime = 0L

    // Unconditional safety watchdog: automatically hides shield within 250ms
    private val safetyTimeoutRunnable = Runnable {
        hideShield()
    }

    fun init(service: AccessibilityService) {
        if (shieldView != null) return
        mainHandler.post {
            setupViews(service)
        }
    }

    private fun setupViews(context: Context) {
        if (shieldView != null) return

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        windowManager = wm

        val container = FrameLayout(context).apply {
            // Transparent shield consumes 100% of touches without blinding the user with a black screen!
            setBackgroundColor(Color.TRANSPARENT)
            isClickable = true
            isFocusable = true
            visibility = View.GONE
        }

        val wmParams = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )

        shieldView = container
        shieldParams = wmParams

        // Pre-attach the shield view in advance with View.GONE!
        try {
            if (container.parent == null && !container.isAttachedToWindow) {
                wm?.addView(container, wmParams)
                isAttached = true
            }
        } catch (_: Throwable) {}
    }

    /**
     * Instantly shows the touch-absorbing shield in the exact same UI frame.
     * Consumes all user touch input immediately to prevent spam taps during ejection.
     * Transparent so the user never sees a dead/black screen.
     * Guaranteed to auto-dismiss within 250ms.
     */
    fun showInstantShield(service: AccessibilityService) {
        val now = SystemClock.uptimeMillis()
        if (isShieldActive && (now - lastShieldShowTime < 250L)) {
            return
        }
        lastShieldShowTime = now
        isShieldActive = true

        val action = Runnable {
            if (shieldView == null) {
                setupViews(service)
            }

            val view = shieldView ?: return@Runnable
            val params = shieldParams ?: return@Runnable
            val wm = windowManager ?: (service.getSystemService(Context.WINDOW_SERVICE) as? WindowManager) ?: return@Runnable

            if (!isAttached || view.parent == null || !view.isAttachedToWindow) {
                runCatching {
                    if (view.parent == null && !view.isAttachedToWindow) {
                        wm.addView(view, params)
                        isAttached = true
                    } else {
                        wm.updateViewLayout(view, params)
                        isAttached = true
                    }
                }
            }

            view.isClickable = true
            view.isFocusable = true
            view.visibility = View.VISIBLE

            // Unconditional safety watchdog: guaranteed auto-hide after 250ms
            mainHandler.removeCallbacks(safetyTimeoutRunnable)
            mainHandler.postDelayed(safetyTimeoutRunnable, 250L)
        }

        if (Looper.myLooper() == Looper.getMainLooper()) {
            action.run()
        } else {
            mainHandler.post(action)
        }
    }

    /**
     * Cleanly hides the shield once a launcher event or timeout confirms the transition.
     */
    fun hideShield() {
        if (!isShieldActive && shieldView?.visibility != View.VISIBLE) return
        isShieldActive = false
        mainHandler.removeCallbacks(safetyTimeoutRunnable)
        val action = Runnable {
            shieldView?.apply {
                isClickable = false
                isFocusable = false
                visibility = View.GONE
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action.run()
        } else {
            mainHandler.post(action)
        }
    }

    fun cleanup() {
        isShieldActive = false
        mainHandler.removeCallbacks(safetyTimeoutRunnable)
        mainHandler.post {
            val view = shieldView
            val wm = windowManager
            if (view != null && isAttached && wm != null) {
                try {
                    wm.removeViewImmediate(view)
                } catch (_: Throwable) {
                    try { wm.removeView(view) } catch (_: Throwable) {}
                }
            }
            isAttached = false
            shieldView = null
            shieldParams = null
            windowManager = null
        }
    }
}
