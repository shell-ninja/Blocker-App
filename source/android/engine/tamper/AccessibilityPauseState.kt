package com.blocker.engine.tamper

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AccessibilityPauseSnapshot(
    val allowTempPauseOptIn: Boolean = false,
    val hasDecidedOptIn: Boolean = false,
    val pauseUntilEpochMs: Long = 0L,
    val isAwaitingReEnable: Boolean = false
) {
    fun isPausedNow(nowMs: Long = System.currentTimeMillis()): Boolean =
        pauseUntilEpochMs > nowMs

    fun isGuardRequired(nowMs: Long = System.currentTimeMillis()): Boolean =
        isPausedNow(nowMs) || isAwaitingReEnable
}

object AccessibilityPauseState {
    private const val PREF_NAME = "blocker_accessibility_pause_state"
    private const val KEY_OPT_IN = "allow_temp_pause_opt_in"
    private const val KEY_HAS_DECIDED = "has_decided_temp_pause_opt_in"
    private const val KEY_PAUSE_UNTIL = "pause_until_epoch_ms"
    private const val KEY_AWAITING_REENABLE = "is_awaiting_reenable"

    const val PAUSE_DURATION_MS = 10L * 60L * 1000L // Exactly 10 minutes
    private const val REENABLE_SETTINGS_WINDOW_MS = 30_000L // 30s grace to reach Accessibility toggle

    @Volatile
    private var reEnableFlowGraceUntilElapsedMs: Long = 0L

    private val _stateFlow = MutableStateFlow(AccessibilityPauseSnapshot())
    val stateFlow: StateFlow<AccessibilityPauseSnapshot> = _stateFlow.asStateFlow()

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun load(context: Context): AccessibilityPauseSnapshot {
        val p = prefs(context)
        val snap = AccessibilityPauseSnapshot(
            allowTempPauseOptIn = p.getBoolean(KEY_OPT_IN, false),
            hasDecidedOptIn = p.getBoolean(KEY_HAS_DECIDED, false),
            pauseUntilEpochMs = p.getLong(KEY_PAUSE_UNTIL, 0L),
            isAwaitingReEnable = p.getBoolean(KEY_AWAITING_REENABLE, false)
        )
        _stateFlow.value = snap
        return snap
    }

    fun setOptIn(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_OPT_IN, enabled).commit()
        load(context)
    }

    fun finalizeOptInDecision(context: Context, enabled: Boolean) {
        prefs(context).edit()
            .putBoolean(KEY_OPT_IN, enabled)
            .putBoolean(KEY_HAS_DECIDED, true)
            .commit()
        load(context)
    }

    fun startTenMinutePause(context: Context): Long {
        val endEpochMs = System.currentTimeMillis() + PAUSE_DURATION_MS
        reEnableFlowGraceUntilElapsedMs = 0L
        prefs(context).edit()
            .putLong(KEY_PAUSE_UNTIL, endEpochMs)
            .putBoolean(KEY_AWAITING_REENABLE, false)
            .commit()
        load(context)
        return endEpochMs
    }

    fun markPauseExpiredAwaitingReEnable(context: Context) {
        prefs(context).edit()
            .putLong(KEY_PAUSE_UNTIL, 0L)
            .putBoolean(KEY_AWAITING_REENABLE, true)
            .commit()
        load(context)
    }

    fun clearAllOnAccessibilityReconnected(context: Context) {
        reEnableFlowGraceUntilElapsedMs = 0L
        prefs(context).edit()
            .putLong(KEY_PAUSE_UNTIL, 0L)
            .putBoolean(KEY_AWAITING_REENABLE, false)
            .commit()
        load(context)
    }

    fun beginReEnableSettingsWindow() {
        reEnableFlowGraceUntilElapsedMs =
            SystemClock.elapsedRealtime() + REENABLE_SETTINGS_WINDOW_MS
    }

    fun isInsideReEnableSettingsWindow(): Boolean =
        SystemClock.elapsedRealtime() < reEnableFlowGraceUntilElapsedMs
}
