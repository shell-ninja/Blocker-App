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
    private var hudReasonText: TextView? = null
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
                setColor(0xEE110D22.toInt()) // Elevated Dark Violet-Obsidian
                cornerRadius = dp(24).toFloat()
                setStroke(dp(1), 0xFF4C3B87.toInt()) // TopEdgeHighlight violet rim
            }
            elevation = dp(8).toFloat()
        }

        val iconIv = android.widget.ImageView(context).apply {
            setImageResource(R.drawable.ic_shield_cyber)
            setColorFilter(0xFF8B5CF6.toInt()) // Vibrant Royal Purple
            layoutParams = LinearLayout.LayoutParams(dp(18), dp(18)).apply {
                setMargins(0, 0, dp(8), 0)
            }
        }

        val textCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        val messageTv = TextView(context).apply {
            text = ""
            setTextColor(0xFFF5F3FF.toInt()) // Crisp Pale Lavender Off-White
            textSize = 13.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_VERTICAL
            maxLines = 2
        }

        val reasonTv = TextView(context).apply {
            text = ""
            setTextColor(0xFFA78BFA.toInt()) // Soft Lavender-Purple
            textSize = 11.5f
            typeface = Typeface.DEFAULT
            visibility = View.GONE
            setPadding(0, dp(2), 0, 0)
        }

        textCol.addView(messageTv)
        textCol.addView(reasonTv)

        pillLayout.addView(iconIv)
        pillLayout.addView(textCol)

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
        hudReasonText = reasonTv
        hudParams = wmParams

        // Pre-attach the HUD view to WindowManager in advance with View.GONE!
        try {
            if (container.parent == null && !container.isAttachedToWindow) {
                wm?.addView(container, wmParams)
                isAttached = true
            }
        } catch (_: Throwable) {}
    }

    fun show(
        service: AccessibilityService,
        message: CharSequence,
        reasonId: Int = 0,
        blockReason: BlockReason? = null
    ) {
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
            val reasonTv = hudReasonText
            val params = hudParams ?: return@Runnable
            val wm = windowManager ?: (service.getSystemService(Context.WINDOW_SERVICE) as? WindowManager) ?: return@Runnable

            tv.text = message
            if (blockReason != null) {
                val isBn = BlockerStore.popupLanguage(service) == "bn"
                reasonTv?.apply {
                    text = blockReason.toHudText(isBn)
                    visibility = View.VISIBLE
                }
            } else {
                reasonTv?.visibility = View.GONE
            }

            if (!isAttached || container.parent == null || !container.isAttachedToWindow) {
                runCatching {
                    if (container.parent == null && !container.isAttachedToWindow) {
                        wm.addView(container, params)
                        isAttached = true
                    } else {
                        wm.updateViewLayout(container, params)
                        isAttached = true
                    }
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
            hudReasonText = null
            hudParams = null
            windowManager = null
        }
    }
}
