package com.blocker

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Full-screen, touch-blocking hardware overlay displayed over Settings, PackageInstaller,
 * and App Info while Accessibility is temporarily disabled for Banking apps.
 *
 * Strictly without FLAG_NOT_TOUCHABLE or FLAG_NOT_FOCUSABLE so that zero taps can reach
 * any uninstall, deactivation, or clear storage button underneath.
 */
object UninstallShieldOverlay {
    private const val TAG = "UninstallShieldOverlay"
    private val mainHandler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null

    @Volatile var isShowing = false
        private set

    @Volatile var hasRequestedHomeReturn = false

    fun show(context: Context) {
        val appCtx = context.applicationContext
        mainHandler.post {
            try {
                if (!Settings.canDrawOverlays(appCtx)) {
                    sendHome(appCtx)
                    return@post
                }

                if (isShowing && overlayView != null) {
                    sendHome(appCtx)
                    return@post
                }

                val wm = (appCtx.getSystemService(Context.WINDOW_SERVICE) as? WindowManager) ?: return@post
                windowManager = wm

                hasRequestedHomeReturn = false
                val view = createOverlayView(appCtx)
                overlayView = view

                val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
                }

                // Explicitly WITHOUT FLAG_NOT_TOUCHABLE and WITHOUT FLAG_NOT_FOCUSABLE so zero touches pass through!
                val params = WindowManager.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    type,
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.CENTER
                }

                wm.addView(view, params)
                isShowing = true
                sendHome(appCtx)
            } catch (t: Throwable) {
                Log.e(TAG, "Failed showing uninstall shield overlay", t)
                sendHome(appCtx)
            }
        }
    }

    fun hide() {
        mainHandler.post {
            try {
                val wm = windowManager
                val view = overlayView
                if (isShowing && view != null && wm != null) {
                    try {
                        wm.removeViewImmediate(view)
                    } catch (_: Throwable) {
                        try { wm.removeView(view) } catch (_: Throwable) {}
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed hiding uninstall shield overlay", t)
            } finally {
                isShowing = false
                overlayView = null
                windowManager = null
                hasRequestedHomeReturn = false
            }
        }
    }

    private fun sendHome(context: Context) {
        runCatching {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            context.startActivity(homeIntent)
        }
    }

    private fun dp(context: Context, value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }

    private fun createOverlayView(context: Context): View {
        val root = FrameLayout(context).apply {
            setBackgroundColor(Color.parseColor("#070512"))
            isClickable = true
            isFocusable = true
        }

        val cardLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val pad = dp(context, 22)
            setPadding(pad, pad, pad, pad)

            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#15102A"))
                cornerRadius = dp(context, 20).toFloat()
                setStroke(dp(context, 2), Color.parseColor("#7C3AED"))
            }
            background = bg
        }

        val cardParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER
            val margin = dp(context, 24)
            setMargins(margin, 0, margin, 0)
        }

        // Title
        val titleView = TextView(context).apply {
            text = "Uninstall & Settings Shield Active"
            textSize = 17f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            paint.isFakeBoldText = true
            val marginB = dp(context, 10)
            setPadding(0, 0, 0, marginB)
        }
        cardLayout.addView(titleView)

        // Message
        val msgView = TextView(context).apply {
            text = "Android Settings and Package Installer are locked while Accessibility is disabled for Banking apps. Re-enable Accessibility first to unlock Settings."
            textSize = 13f
            setTextColor(Color.parseColor("#C4B5FD"))
            gravity = Gravity.CENTER
            val padB = dp(context, 20)
            setPadding(0, 0, 0, padB)
        }
        cardLayout.addView(msgView)

        // Button 1: Re-enable Accessibility (Single line 3D button)
        val btnReEnable = Button(context).apply {
            text = "Re-enable Accessibility"
            isSingleLine = true
            maxLines = 1
            textSize = 12.5f
            setTextColor(Color.WHITE)
            paint.isFakeBoldText = true
            val bgBtn = GradientDrawable().apply {
                setColor(Color.parseColor("#7C3AED"))
                cornerRadius = dp(context, 10).toFloat()
            }
            background = bgBtn
            val pV = dp(context, 10)
            setPadding(dp(context, 14), pV, dp(context, 14), pV)
            setOnClickListener {
                hide()
                AccessibilityPauseManager.reEnableAccessibility(context)
            }
        }
        val btnParams1 = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(context, 44)
        ).apply {
            bottomMargin = dp(context, 10)
        }
        cardLayout.addView(btnReEnable, btnParams1)

        // Button 2: Return to Home (Single line 3D button)
        val btnHome = Button(context).apply {
            text = "Return to Home"
            isSingleLine = true
            maxLines = 1
            textSize = 12.5f
            setTextColor(Color.parseColor("#E9D5FF"))
            paint.isFakeBoldText = true
            val bgBtn2 = GradientDrawable().apply {
                setColor(Color.parseColor("#1F1638"))
                cornerRadius = dp(context, 10).toFloat()
                setStroke(dp(context, 1), Color.parseColor("#4C3B87"))
            }
            background = bgBtn2
            val pV = dp(context, 10)
            setPadding(dp(context, 14), pV, dp(context, 14), pV)
            setOnClickListener {
                hasRequestedHomeReturn = true
                sendHome(context)
                mainHandler.postDelayed({
                    hide()
                }, 350L)
            }
        }
        val btnParams2 = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(context, 42)
        )
        cardLayout.addView(btnHome, btnParams2)

        root.addView(cardLayout, cardParams)
        return root
    }
}
