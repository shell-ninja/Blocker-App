package com.blocker

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

object OverlayHudManager {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var hudContainer: FrameLayout? = null
    private var hudPill: LinearLayout? = null
    private var hudText: TextView? = null
    private var hudParams: WindowManager.LayoutParams? = null
    private var isAttached = false

    private val hideRunnable = Runnable {
        val pill = hudPill
        if (pill != null) {
            pill.animate()
                .alpha(0f)
                .translationY(-25f)
                .setDuration(130L)
                .withEndAction {
                    hudContainer?.visibility = View.GONE
                }
                .start()
        } else {
            hudContainer?.visibility = View.GONE
        }
    }

    fun init(service: AccessibilityService) {
        if (hudContainer != null) return
        mainHandler.post {
            setupViews(service)
        }
    }

    private fun setupViews(context: Context) {
        if (hudContainer != null) return

        val density = context.resources.displayMetrics.density
        fun dp(v: Int): Int = (v * density).toInt()

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        windowManager = wm

        val container = FrameLayout(context).apply {
            visibility = View.GONE
        }

        val pillLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(10), dp(18), dp(10))

            background = GradientDrawable().apply {
                setColor(0xEE0A0618.toInt()) // Deep obsidian with slight translucency
                cornerRadius = dp(24).toFloat()
                setStroke(dp(1), 0x998B5CF6.toInt()) // Accent violet outline
            }
            elevation = dp(8).toFloat()
        }

        val iconTv = TextView(context).apply {
            text = "🛡️"
            textSize = 16f
            setPadding(0, 0, dp(8), 0)
        }

        val messageTv = TextView(context).apply {
            text = ""
            setTextColor(0xFFEDE9FE.toInt()) // Lavender white
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_VERTICAL
            maxLines = 2
        }

        pillLayout.addView(iconTv)
        pillLayout.addView(messageTv)

        val pillParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        )
        container.addView(pillLayout, pillParams)

        val wmParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(54) // Safe status-bar / cutout clearance
        }

        hudContainer = container
        hudPill = pillLayout
        hudText = messageTv
        hudParams = wmParams

        // Pre-attach the HUD view to WindowManager in advance with View.GONE!
        try {
            wm?.addView(container, wmParams)
            isAttached = true
        } catch (_: Throwable) {}
    }

    fun show(service: AccessibilityService, message: CharSequence, reasonId: Int = 0) {
        if (reasonId > 0 && !NativeBlockerBridge.checkHudRateLimit(reasonId)) {
            return
        }

        val action = Runnable {
            if (hudContainer == null) {
                setupViews(service)
            }

            val container = hudContainer ?: return@Runnable
            val pill = hudPill ?: return@Runnable
            val tv = hudText ?: return@Runnable
            val params = hudParams ?: return@Runnable
            val wm = windowManager ?: (service.getSystemService(Context.WINDOW_SERVICE) as? WindowManager) ?: return@Runnable

            tv.text = message

            if (!isAttached) {
                try {
                    wm.addView(container, params)
                    isAttached = true
                } catch (t: Throwable) {
                    try {
                        wm.updateViewLayout(container, params)
                        isAttached = true
                    } catch (_: Throwable) {}
                }
            }

            mainHandler.removeCallbacks(hideRunnable)
            container.visibility = View.VISIBLE

            pill.animate().cancel()
            pill.alpha = 0f
            pill.translationY = -35f
            pill.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(160L)
                .setInterpolator(DecelerateInterpolator(1.8f))
                .start()

            mainHandler.postDelayed(hideRunnable, 2000L)
        }

        if (Looper.myLooper() == Looper.getMainLooper()) {
            action.run()
        } else {
            mainHandler.post(action)
        }
    }

    fun hide() {
        mainHandler.removeCallbacks(hideRunnable)
        if (Looper.myLooper() == Looper.getMainLooper()) {
            hideRunnable.run()
        } else {
            mainHandler.post(hideRunnable)
        }
    }

    fun cleanup() {
        mainHandler.removeCallbacks(hideRunnable)
        mainHandler.post {
            val container = hudContainer
            val wm = windowManager
            if (container != null && isAttached && wm != null) {
                try {
                    wm.removeViewImmediate(container)
                } catch (_: Throwable) {
                    try { wm.removeView(container) } catch (_: Throwable) {}
                }
            }
            isAttached = false
            hudContainer = null
            hudPill = null
            hudText = null
            hudParams = null
            windowManager = null
        }
    }
}
