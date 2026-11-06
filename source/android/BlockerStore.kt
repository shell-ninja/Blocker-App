package com.blocker

import android.app.AppOpsManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.app.admin.DevicePolicyManager
import android.app.usage.UsageStatsManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.AudioManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import android.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/** Keywords this app used to ship. They are dropped from saved lists without the delay timer. */
private val RETIRED_KEYWORDS = setOf("usb debugging", "oem unlocking", "oem unlock", "bare")

/** One recurring daily focus window, e.g. "Bedtime" 22:00 to 06:00, with optional per-schedule unblocked apps. */
data class FocusSchedule(
    val id: String,
    val label: String,
    val startMin: Int,
    val endMin: Int,
    val enabled: Boolean,
    val allowedApps: Set<String> = emptySet()
) {
    fun toJson(): JSONObject {
        val arr = JSONArray()
        allowedApps.forEach { arr.put(it) }
        return JSONObject()
            .put("id", id).put("label", label).put("startMin", startMin).put("endMin", endMin).put("enabled", enabled)
            .put("allowedApps", arr)
    }

    companion object {
        fun fromJson(o: JSONObject): FocusSchedule {
            val appSet = mutableSetOf<String>()
            val arr = o.optJSONArray("allowedApps")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val a = arr.optString(i, "")
                    if (a.isNotEmpty()) appSet.add(a)
                }
            }
            return FocusSchedule(
                o.getString("id"), o.optString("label", "Schedule"), o.getInt("startMin"), o.getInt("endMin"),
                o.optBoolean("enabled", true), appSet
            )
        }
    }
}

/**
 * Tamper-resistant monotonic time provider.
 * Protects all delay timers, Pomodoro sessions, and daily schedules against
 * wall-clock manipulation (ADB date -s, SIM removal, rogue NTP, and manual clock changes).
 */
object SecureTime {
    private val lock = Any()
    @Volatile private var memAnchorElapsed = 0L
    @Volatile private var memAnchorWall = 0L
    @Volatile private var lastTamperIncrementTime = 0L

    fun now(c: Context): Long = synchronized(lock) {
        val currentElapsed = SystemClock.elapsedRealtime()
        val currentWall = System.currentTimeMillis()

        if (memAnchorElapsed == 0L || memAnchorWall == 0L) {
            val p = BlockerStore.prefs(c)
            memAnchorElapsed = p.getLong("sec_anchor_elapsed", 0L)
            memAnchorWall = p.getLong("sec_anchor_wall", 0L)

            if (memAnchorElapsed == 0L || memAnchorWall == 0L || currentElapsed < memAnchorElapsed) {
                memAnchorElapsed = currentElapsed
                memAnchorWall = currentWall
                p.edit()
                    .putLong("sec_anchor_elapsed", currentElapsed)
                    .putLong("sec_anchor_wall", currentWall)
                    .apply()
                return currentWall
            }
        }

        // Reboot detection: elapsedRealtime resets on device boot
        if (currentElapsed < memAnchorElapsed) {
            val wallDelta = currentWall - memAnchorWall
            val isAutoTime = runCatching {
                Settings.Global.getInt(c.contentResolver, Settings.Global.AUTO_TIME, 0) == 1
            }.getOrDefault(false)

            val maxAllowedOfflineJump = if (isAutoTime) 86_400_000L * 30 else 300_000L
            val creditedJump = if (wallDelta > 0) minOf(wallDelta, maxAllowedOfflineJump) else 0L

            val newAnchorWall = memAnchorWall + creditedJump
            memAnchorElapsed = currentElapsed
            memAnchorWall = newAnchorWall
            BlockerStore.prefs(c).edit()
                .putLong("sec_anchor_elapsed", currentElapsed)
                .putLong("sec_anchor_wall", newAnchorWall)
                .apply()
            return newAnchorWall
        }

        // Running device: elapsedRealtime is strictly monotonic hardware time
        val elapsedDelta = currentElapsed - memAnchorElapsed
        val expectedWall = memAnchorWall + elapsedDelta
        val wallDiscrepancy = currentWall - expectedWall

        // Normal clock synchronization / minor drift tolerance (up to 5 minutes)
        if (Math.abs(wallDiscrepancy) <= 300_000L) {
            // Keep anchor aligned periodically every 5 minutes
            if (elapsedDelta > 300_000L) {
                memAnchorElapsed = currentElapsed
                memAnchorWall = currentWall
                BlockerStore.prefs(c).edit()
                    .putLong("sec_anchor_elapsed", currentElapsed)
                    .putLong("sec_anchor_wall", currentWall)
                    .apply()
            }
            return currentWall
        }

        // Clock Tampering detected: jump > 5 minutes forward or rewind > 5 minutes backward
        val nowElapsed = SystemClock.elapsedRealtime()
        if (nowElapsed - lastTamperIncrementTime > 60_000L) {
            lastTamperIncrementTime = nowElapsed
            if (BlockerStore.active(c)) {
                BlockerStore.incr(c, "tamper")
            }
        }

        // Continue monotonically from expected hardware time so timers are immune to clock jumps
        return expectedWall
    }
}

/** Shared state between the RN module, accessibility service and device admin receiver. */
object BlockerStore {
    const val NOTIFICATION_ID_BANKING_MODE = 1005
    const val DAY_MS = 86_400_000L
    const val GUARD_WINDOW_MS = 10 * 60_000L

    fun prefs(c: Context): SharedPreferences =
        c.applicationContext.getSharedPreferences("blocker_store", Context.MODE_PRIVATE)

    private fun getHmacSecret(c: Context): ByteArray {
        val salt = "blocker_anti_tamper_${c.packageName}_salt_v2"
        return salt.toByteArray(Charsets.UTF_8)
    }

    fun computeIntegrityHmac(c: Context): String {
        val p = prefs(c)
        val sb = java.lang.StringBuilder()
        sb.append("active=").append(p.getBoolean("active", false)).append(";")
        sb.append("shield=").append(p.getBoolean("shield", true)).append(";")
        sb.append("prevent_power_off=").append(p.getBoolean("prevent_power_off", false)).append(";")
        sb.append("delay_days=").append(p.getInt("delay_days", 1)).append(";")
        sb.append("study_active=").append(p.getBoolean("study_active", false)).append(";")
        sb.append("focus_until=").append(p.getLong("focus_until", 0L)).append(";")
        sb.append("study_until=").append(p.getLong("study_until", 0L)).append(";")
        val all = p.all
        all.keys.filter { it.startsWith("open_") || it.startsWith("pending_") }
            .sorted()
            .forEach { k -> sb.append(k).append("=").append(all[k]).append(";") }

        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(getHmacSecret(c), "HmacSHA256"))
        val sig = mac.doFinal(sb.toString().toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(sig, Base64.NO_WRAP)
    }

    fun updateIntegrityHmac(c: Context) {
        prefs(c).edit().putString("state_integrity_hmac", computeIntegrityHmac(c)).apply()
    }

    @Volatile private var lastIntegrityCheck = 0L
    @Volatile private var cachedIntegrityValid = true

    fun verifyIntegrity(c: Context): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now - lastIntegrityCheck < 30_000L) {
            return cachedIntegrityValid
        }
        lastIntegrityCheck = now

        val p = prefs(c)
        val savedHmac = p.getString("state_integrity_hmac", null) ?: run {
            cachedIntegrityValid = true
            return true
        }
        val computed = computeIntegrityHmac(c)
        if (savedHmac != computed) {
            incr(c, "tamper")
            val editor = p.edit()
            editor.putBoolean("active", true)
            editor.putBoolean("shield", true)
            p.all.keys.filter { it.startsWith("open_") }.forEach { editor.remove(it) }
            editor.apply()
            updateIntegrityHmac(c)
            cachedIntegrityValid = false
            return false
        }
        cachedIntegrityValid = true
        return true
    }

    fun checkAndApplyExpiredDelayTimers(c: Context) {
        val now = SecureTime.now(c)
        val p = prefs(c)
        var changed = false
        val editor = p.edit()

        val pendingProtection = p.getLong("pending_protection", 0L)
        if (pendingProtection != 0L && now >= pendingProtection) {
            editor.putBoolean("active", false)
            editor.remove("pending_protection")
            editor.remove("open_protection")
            changed = true
        }

        val pendingShield = p.getLong("pending_shield", 0L)
        if (pendingShield != 0L && now >= pendingShield) {
            editor.putBoolean("shield", false)
            editor.remove("pending_shield")
            editor.remove("open_shield")
            changed = true
        }

        val pendingPower = p.getLong("pending_power_menu", 0L)
        if (pendingPower != 0L && now >= pendingPower) {
            editor.putBoolean("prevent_power_off", false)
            editor.remove("pending_power_menu")
            editor.remove("open_power_menu")
            changed = true
        }

        if (changed) {
            editor.apply()
            updateIntegrityHmac(c)
        }
    }

    fun active(c: Context): Boolean {
        verifyIntegrity(c)
        checkAndApplyExpiredDelayTimers(c)
        return prefs(c).getBoolean("active", false)
    }

    fun shield(c: Context): Boolean {
        verifyIntegrity(c)
        checkAndApplyExpiredDelayTimers(c)
        return prefs(c).getBoolean("shield", true)
    }

    fun isBankingMode(c: Context): Boolean = prefs(c).getBoolean("banking_mode_active", false)
    fun setBankingMode(c: Context, active: Boolean) =
        prefs(c).edit().putBoolean("banking_mode_active", active).apply()

    fun isAutoPauseBanking(c: Context): Boolean = prefs(c).getBoolean("auto_pause_banking", false)
    fun setAutoPauseBanking(c: Context, active: Boolean) =
        prefs(c).edit().putBoolean("auto_pause_banking", active).apply()

    fun preventPowerOff(c: Context): Boolean {
        verifyIntegrity(c)
        checkAndApplyExpiredDelayTimers(c)
        return prefs(c).getBoolean("prevent_power_off", false)
    }

    fun setPreventPowerOff(c: Context, enable: Boolean) {
        prefs(c).edit().putBoolean("prevent_power_off", enable).apply()
        updateIntegrityHmac(c)
    }

    fun popupLanguage(c: Context): String = prefs(c).getString("popup_language", "en") ?: "en"

    fun setPopupLanguage(c: Context, lang: String) {
        prefs(c).edit().putString("popup_language", if (lang == "bn") "bn" else "en").apply()
    }

    fun delayDays(c: Context) = prefs(c).getInt("delay_days", 1)
    // Debug builds shorten every "day" to one minute so the timers can be tested quickly.
    fun delayMs(c: Context): Long {
        val debug = (c.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        return delayDays(c) * (if (debug) 60_000L else DAY_MS)
    }
    fun set(c: Context, key: String): Set<String> = prefs(c).getStringSet(key, emptySet())?.toSet() ?: emptySet()
    fun putSet(c: Context, key: String, v: Set<String>) {
        prefs(c).edit().putStringSet(key, v).apply()
        if (key == "domains" || key == "keywords") {
            syncBinaryBlocklist(c)
        }
    }

    fun syncBinaryBlocklist(c: Context) {
        Executors.newSingleThreadExecutor().execute {
            try {
                val binFile = File(c.filesDir, "blocklist.bin")
                if (!binFile.exists()) {
                    try {
                        c.assets.open("blocklist.bin").use { input ->
                            binFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                    } catch (_: Throwable) {}
                }
                val d = set(c, "domains").toTypedArray()
                val k = (set(c, "keywords") - RETIRED_KEYWORDS).toTypedArray()
                if (d.isNotEmpty() || k.isNotEmpty()) {
                    if (NativeBlockerBridge.compileBinary(d, k, binFile.absolutePath)) {
                        NativeBlockerBridge.load(binFile)
                    }
                } else if (binFile.exists() && !NativeBlockerBridge.isEngineReady()) {
                    NativeBlockerBridge.load(binFile)
                }
            } catch (_: Throwable) {}
        }
    }

    /** Apps blocked out of the box. Mirrored by DEFAULT_BLOCKED_APPS in ProtectionManager.ts. */
    val DEFAULT_BLOCKED_APPS = setOf("com.streamdev.aiostreamer", "org.xbmc.kodi")

    /** Adds the default blocks exactly once, so a later (delayed) unblock is not undone. Safe to call repeatedly. */
    fun seedDefaultApps(c: Context) {
        val p = prefs(c)
        if (p.getBoolean("default_apps_seeded_v1", false)) return
        p.edit().putStringSet("apps", set(c, "apps") + DEFAULT_BLOCKED_APPS).putBoolean("default_apps_seeded_v1", true).apply()
    }

    val DEFAULT_FOCUS_APPS = setOf("com.whatsapp", "com.facebook.orca", "com.google.android.apps.translate")
    fun focusApps(c: Context): Set<String> =
        prefs(c).getStringSet("focus_apps", DEFAULT_FOCUS_APPS)?.toSet() ?: DEFAULT_FOCUS_APPS

    // ---- Focus mode: a one-off countdown (focus_until) plus any number of named daily schedules ----
    fun focusUntil(c: Context) = prefs(c).getLong("focus_until", 0L)

    fun schedules(c: Context): List<FocusSchedule> = runCatching {
        val arr = JSONArray(prefs(c).getString("schedules_json", "[]"))
        (0 until arr.length()).map { FocusSchedule.fromJson(arr.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    fun putSchedules(c: Context, list: List<FocusSchedule>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs(c).edit().putString("schedules_json", arr.toString()).apply()
    }

    fun minuteOfDay(c: Context? = null): Int {
        val cal = Calendar.getInstance()
        if (c != null) {
            cal.timeInMillis = SecureTime.now(c)
        }
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    }

    private fun inWindow(start: Int, end: Int, now: Int): Boolean =
        if (start == end) false else if (start < end) now in start until end else now >= start || now < end

    fun scheduleIsActiveNow(s: FocusSchedule, c: Context? = null): Boolean = s.enabled && inWindow(s.startMin, s.endMin, minuteOfDay(c))
    fun anyScheduleActiveNow(c: Context) = schedules(c).any { scheduleIsActiveNow(it, c) }
    fun focusActive(c: Context) = focusUntil(c) > SecureTime.now(c) || anyScheduleActiveNow(c)

    /** Minutes remaining until a window closes, from a given minute-of-day. */
    private fun minutesUntil(target: Int, from: Int): Int = if (target > from) target - from else 1440 - from + target

    fun focusRemainingMs(c: Context): Long = runCatching {
        val now = SecureTime.now(c)
        val untilMs = (focusUntil(c) - now).coerceAtLeast(0L)
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val secOfDay = cal.get(Calendar.HOUR_OF_DAY) * 3600 + cal.get(Calendar.MINUTE) * 60 + cal.get(Calendar.SECOND)
        val activeEnds = schedules(c).filter { scheduleIsActiveNow(it, c) }
            .map {
                val endSec = it.endMin * 60
                val secUntil = if (endSec > secOfDay) endSec - secOfDay else (86400 - secOfDay + endSec)
                secUntil * 1000L
            }
        val schedMs = activeEnds.maxOrNull() ?: 0L
        maxOf(untilMs, schedMs)
    }.getOrDefault(0L)

    /** Returns the union of allowed apps configured for all schedules currently active. */
    fun scheduleAllowedApps(c: Context): Set<String> {
        val active = schedules(c).filter { scheduleIsActiveNow(it, c) }
        val set = mutableSetOf<String>()
        for (s in active) {
            set.addAll(s.allowedApps)
        }
        return set
    }

    fun pendingUntil(c: Context, key: String) = prefs(c).getLong("pending_$key", 0L)
    fun guardOpen(c: Context, key: String) = prefs(c).getLong("open_$key", 0L) > SecureTime.now(c)
    fun consumeGuard(c: Context, key: String) {
        prefs(c).edit().remove("open_$key").apply()
        updateIntegrityHmac(c)
    }

    // ---- Study Mode (Aggressive Pomodoro Phone Lockout) ----
    fun studyActive(c: Context): Boolean {
        advanceStudyState(c)
        return prefs(c).getBoolean("study_active", false)
    }

    fun studyUntil(c: Context): Long {
        advanceStudyState(c)
        return prefs(c).getLong("study_until", 0L)
    }

    fun studyRemainingMs(c: Context): Long {
        advanceStudyState(c)
        val until = prefs(c).getLong("study_until", 0L)
        return (until - SecureTime.now(c)).coerceAtLeast(0L)
    }

    fun studyTotalRounds(c: Context): Int = prefs(c).getInt("study_total_rounds", 4)

    fun studyCurrentRound(c: Context): Int {
        advanceStudyState(c)
        return prefs(c).getInt("study_current_round", 1)
    }

    fun studyIsBreak(c: Context): Boolean {
        advanceStudyState(c)
        return prefs(c).getBoolean("study_is_break", false)
    }

    fun studyFocusMinutes(c: Context): Int = prefs(c).getInt("study_focus_minutes", 25)
    fun studyBreakMinutes(c: Context): Int = prefs(c).getInt("study_break_minutes", 5)

    fun startStudy(c: Context, focusMinutes: Int, breakMinutes: Int, totalRounds: Int) {
        val fm = focusMinutes.coerceIn(1, 180)
        val bm = breakMinutes.coerceIn(0, 60)
        val rounds = totalRounds.coerceIn(1, 12)
        val now = SecureTime.now(c)
        prefs(c).edit()
            .putBoolean("study_active", true)
            .putInt("study_focus_minutes", fm)
            .putInt("study_break_minutes", bm)
            .putInt("study_total_rounds", rounds)
            .putInt("study_current_round", 1)
            .putBoolean("study_is_break", false)
            .putLong("study_started_at", now)
            .putLong("study_until", now + fm * 60_000L)
            .apply()
        updateIntegrityHmac(c)
        muteNotifications(c)
        disableWifi(c)
    }

    fun endStudySession(c: Context) {
        prefs(c).edit()
            .putBoolean("study_active", false)
            .putLong("study_until", 0L)
            .putBoolean("study_is_break", false)
            .remove("study_started_at")
            .apply()
        updateIntegrityHmac(c)
        restoreNotifications(c)
        restoreWifi(c)
    }

    fun skipStudyBreak(c: Context) {
        val p = prefs(c)
        if (!p.getBoolean("study_active", false) || !p.getBoolean("study_is_break", false)) return
        val totalRounds = p.getInt("study_total_rounds", 4)
        val currentRound = p.getInt("study_current_round", 1)
        val focusMin = p.getInt("study_focus_minutes", 25)
        val nextRound = currentRound + 1
        val now = SecureTime.now(c)
        if (nextRound <= totalRounds) {
            p.edit()
                .putBoolean("study_is_break", false)
                .putInt("study_current_round", nextRound)
                .putLong("study_until", now + focusMin * 60_000L)
                .apply()
            updateIntegrityHmac(c)
        } else {
            endStudySession(c)
        }
    }

    @Synchronized
    fun advanceStudyState(c: Context) {
        val p = prefs(c)
        if (!p.getBoolean("study_active", false)) return
        val until = p.getLong("study_until", 0L)
        val now = SecureTime.now(c)
        if (now < until) return

        val totalRounds = p.getInt("study_total_rounds", 4)
        val currentRound = p.getInt("study_current_round", 1)
        val isBreak = p.getBoolean("study_is_break", false)
        val focusMin = p.getInt("study_focus_minutes", 25)
        val breakMin = p.getInt("study_break_minutes", 5)

        if (!isBreak) {
            if (currentRound < totalRounds && breakMin > 0) {
                p.edit()
                    .putBoolean("study_is_break", true)
                    .putLong("study_until", now + breakMin * 60_000L)
                    .apply()
                updateIntegrityHmac(c)
            } else {
                endStudySession(c)
            }
        } else {
            val nextRound = currentRound + 1
            if (nextRound <= totalRounds) {
                p.edit()
                    .putBoolean("study_is_break", false)
                    .putInt("study_current_round", nextRound)
                    .putLong("study_until", now + focusMin * 60_000L)
                    .apply()
                updateIntegrityHmac(c)
            } else {
                endStudySession(c)
            }
        }
    }

    fun muteNotifications(c: Context) {
        val p = prefs(c)
        val am = c.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (am != null) {
            if (!p.contains("study_prev_vol_ring")) {
                p.edit()
                    .putInt("study_prev_vol_ring", am.getStreamVolume(AudioManager.STREAM_RING))
                    .putInt("study_prev_vol_notif", am.getStreamVolume(AudioManager.STREAM_NOTIFICATION))
                    .putInt("study_prev_vol_music", am.getStreamVolume(AudioManager.STREAM_MUSIC))
                    .putInt("study_prev_vol_sys", am.getStreamVolume(AudioManager.STREAM_SYSTEM))
                    .apply()
            }
            runCatching { am.setStreamVolume(AudioManager.STREAM_NOTIFICATION, 0, 0) }
            runCatching { am.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0) }
            runCatching { am.setStreamVolume(AudioManager.STREAM_SYSTEM, 0, 0) }
            runCatching { am.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_MUTE, 0) }
            runCatching { am.adjustStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_MUTE, 0) }
            runCatching { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0) }
            runCatching { am.adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_MUTE, 0) }
        }
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        if (nm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (nm.isNotificationPolicyAccessGranted) {
                if (!p.contains("study_prev_interruption_filter")) {
                    p.edit().putInt("study_prev_interruption_filter", nm.currentInterruptionFilter).apply()
                }
                runCatching {
                    nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
                }
            }
        }
        if (am != null) {
            runCatching {
                if (!p.contains("study_prev_ringer_mode")) {
                    p.edit().putInt("study_prev_ringer_mode", am.ringerMode).apply()
                }
                am.ringerMode = AudioManager.RINGER_MODE_SILENT
            }
        }
    }

    fun restoreNotifications(c: Context) {
        val p = prefs(c)
        val am = c.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (am != null) {
            if (p.contains("study_prev_vol_notif")) {
                val vRing = p.getInt("study_prev_vol_ring", 5)
                val vNotif = p.getInt("study_prev_vol_notif", 5)
                val vMusic = p.getInt("study_prev_vol_music", 5)
                val vSys = p.getInt("study_prev_vol_sys", 5)
                runCatching { am.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_UNMUTE, 0) }
                runCatching { am.adjustStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_UNMUTE, 0) }
                runCatching { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0) }
                runCatching { am.adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_UNMUTE, 0) }
                runCatching { am.setStreamVolume(AudioManager.STREAM_NOTIFICATION, vNotif, 0) }
                runCatching { am.setStreamVolume(AudioManager.STREAM_RING, vRing, 0) }
                runCatching { am.setStreamVolume(AudioManager.STREAM_MUSIC, vMusic, 0) }
                runCatching { am.setStreamVolume(AudioManager.STREAM_SYSTEM, vSys, 0) }
                p.edit()
                    .remove("study_prev_vol_ring")
                    .remove("study_prev_vol_notif")
                    .remove("study_prev_vol_music")
                    .remove("study_prev_vol_sys")
                    .apply()
            }
            if (p.contains("study_prev_ringer_mode")) {
                val prev = p.getInt("study_prev_ringer_mode", AudioManager.RINGER_MODE_NORMAL)
                runCatching { am.ringerMode = prev }
                p.edit().remove("study_prev_ringer_mode").apply()
            }
        }
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        if (nm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && p.contains("study_prev_interruption_filter")) {
            if (nm.isNotificationPolicyAccessGranted) {
                val prev = p.getInt("study_prev_interruption_filter", NotificationManager.INTERRUPTION_FILTER_ALL)
                runCatching { nm.setInterruptionFilter(prev) }
            }
            p.edit().remove("study_prev_interruption_filter").apply()
        }
    }

    fun disableWifi(c: Context) {
        val p = prefs(c)
        runCatching {
            val wm = c.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (wm != null) {
                @Suppress("DEPRECATION")
                if (wm.isWifiEnabled) {
                    p.edit().putBoolean("study_prev_wifi_enabled", true).apply()
                    @Suppress("DEPRECATION")
                    wm.isWifiEnabled = false
                }
            }
        }
        runCatching {
            val cur = Settings.Global.getInt(c.contentResolver, "wifi_on", 1)
            if (cur != 0) {
                p.edit().putBoolean("study_prev_wifi_enabled", true).apply()
                Settings.Global.putInt(c.contentResolver, "wifi_on", 0)
            }
        }
    }

    fun restoreWifi(c: Context) {
        val p = prefs(c)
        val wasEnabled = p.getBoolean("study_prev_wifi_enabled", false)
        if (wasEnabled) {
            p.edit().remove("study_prev_wifi_enabled").apply()
            runCatching {
                val wm = c.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                @Suppress("DEPRECATION")
                wm?.isWifiEnabled = true
            }
            runCatching {
                Settings.Global.putInt(c.contentResolver, "wifi_on", 1)
            }
        }
    }

    private fun day() = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
    fun incr(c: Context, kind: String) {
        val k = "stat_${kind}_${day()}"
        val p = prefs(c)
        p.edit().putInt(k, p.getInt(k, 0) + 1).apply()
    }
    fun stat(c: Context, kind: String): Int {
        val k = "stat_${kind}_${day()}"
        val p = prefs(c)
        val v = p.getInt(k, 0)
        if (kind == "tamper" && v > 500) {
            p.edit().putInt(k, 0).apply()
            return 0
        }
        return v
    }

    fun granularToggle(c: Context, key: String): Boolean = prefs(c).getBoolean(key, false)
    fun setGranularToggle(c: Context, key: String, enabled: Boolean) =
        prefs(c).edit().putBoolean(key, enabled).apply()

    fun appLimits(c: Context): Map<String, Int> = runCatching {
        val json = prefs(c).getString("app_limits", "{}") ?: "{}"
        val obj = JSONObject(json)
        val map = mutableMapOf<String, Int>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val v = obj.optInt(key, 0)
            if (v > 0) map[key] = v
        }
        map
    }.getOrDefault(emptyMap())

    fun putAppLimits(c: Context, limits: Map<String, Int>) {
        val obj = JSONObject()
        limits.forEach { (k, v) ->
            if (v > 0) obj.put(k, v)
        }
        prefs(c).edit().putString("app_limits", obj.toString()).apply()
    }

    fun setAppLimit(c: Context, pkg: String, limitMinutes: Int) {
        val m = appLimits(c).toMutableMap()
        if (limitMinutes > 0) {
            m[pkg] = limitMinutes
        } else {
            m.remove(pkg)
        }
        putAppLimits(c, m)
    }

    fun addAppUsageMillis(c: Context, pkg: String, deltaMs: Long) {
        if (deltaMs <= 0L) return
        val k = "usage_${day()}_$pkg"
        val p = prefs(c)
        val cur = p.getLong(k, 0L)
        p.edit().putLong(k, cur + deltaMs).apply()
    }

    fun getStoredTodayUsageMillis(c: Context, pkg: String): Long {
        val k = "usage_${day()}_$pkg"
        return prefs(c).getLong(k, 0L)
    }

    fun getAllStoredTodayUsageMillis(c: Context): Map<String, Long> {
        val prefix = "usage_${day()}_"
        val all = prefs(c).all
        val res = mutableMapOf<String, Long>()
        all.forEach { (k, v) ->
            if (k.startsWith(prefix)) {
                val value = (v as? Number)?.toLong() ?: 0L
                if (value > 0L) {
                    val pkg = k.removePrefix(prefix)
                    res[pkg] = value
                }
            }
        }
        return res
    }

    fun getTodayUsageMillis(c: Context, pkg: String): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = cal.timeInMillis
        val now = System.currentTimeMillis()
        val elapsedToday = (now - startOfDay).coerceAtLeast(0L)

        var usmTotal = 0L
        runCatching {
            val usm = c.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            if (usm != null) {
                val list = usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, startOfDay, now)
                if (!list.isNullOrEmpty()) {
                    val pkgStats = list.filter { it.packageName == pkg && it.lastTimeStamp >= startOfDay }
                    val maxFg = pkgStats.maxOfOrNull { it.totalTimeInForeground } ?: 0L
                    usmTotal = minOf(maxFg, elapsedToday)
                }
            }
        }
        val stored = getStoredTodayUsageMillis(c, pkg)
        return minOf(maxOf(usmTotal, stored), elapsedToday)
    }

    fun getAllTodayUsageMillis(c: Context): Map<String, Long> {
        val res = getAllStoredTodayUsageMillis(c).toMutableMap()
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = cal.timeInMillis
        val now = System.currentTimeMillis()
        val elapsedToday = (now - startOfDay).coerceAtLeast(0L)

        runCatching {
            val usm = c.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            if (usm != null) {
                val list = usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, startOfDay, now)
                if (!list.isNullOrEmpty()) {
                    for (s in list) {
                        if (s.lastTimeStamp >= startOfDay && s.totalTimeInForeground > 0) {
                            val prev = res[s.packageName] ?: 0L
                            val bounded = minOf(s.totalTimeInForeground, elapsedToday)
                            res[s.packageName] = maxOf(prev, bounded)
                        }
                    }
                }
            }
        }
        res.keys.forEach { k ->
            res[k] = minOf(res[k] ?: 0L, elapsedToday)
        }
        return res
    }
}
