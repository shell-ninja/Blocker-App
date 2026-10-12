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
import com.facebook.react.ReactPackage
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.facebook.react.uimanager.ViewManager
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

    fun now(c: Context): Long = synchronized(lock) {
        val currentElapsed = SystemClock.elapsedRealtime()
        val currentWall = System.currentTimeMillis()

        if (memAnchorElapsed == 0L || memAnchorWall == 0L) {
            val p = BlockerStore.prefs(c)
            memAnchorElapsed = p.getLong("sec_anchor_elapsed", 0L)
            memAnchorWall = p.getLong("sec_anchor_wall", 0L)

            if (memAnchorElapsed == 0L || memAnchorWall == 0L) {
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

        // Detect tampering: clock rewind > 30s or clock forward jump > 60s
        if (wallDiscrepancy < -30_000L || wallDiscrepancy > 60_000L) {
            if (BlockerStore.active(c)) {
                BlockerStore.incr(c, "tamper")
            }
            // Block wall-clock cheat by continuing monotonically from anchor
            return expectedWall
        }

        // Periodically refresh persisted anchor every 15 minutes to stay current across reboots
        if (elapsedDelta > 900_000L) {
            memAnchorElapsed = currentElapsed
            memAnchorWall = currentWall
            BlockerStore.prefs(c).edit()
                .putLong("sec_anchor_elapsed", currentElapsed)
                .putLong("sec_anchor_wall", currentWall)
                .apply()
        }

        return expectedWall
    }
}

/** Shared state between the RN module, accessibility service and device admin receiver. */
object BlockerStore {
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

    fun active(c: Context): Boolean {
        verifyIntegrity(c)
        return prefs(c).getBoolean("active", false)
    }

    fun shield(c: Context): Boolean {
        verifyIntegrity(c)
        return prefs(c).getBoolean("shield", true)
    }

    fun delayDays(c: Context) = prefs(c).getInt("delay_days", 1)
    // Debug builds shorten every "day" to one minute so the timers can be tested quickly.
    fun delayMs(c: Context): Long {
        val debug = (c.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        return delayDays(c) * (if (debug) 60_000L else DAY_MS)
    }
    fun set(c: Context, key: String): Set<String> = prefs(c).getStringSet(key, emptySet())?.toSet() ?: emptySet()
    fun putSet(c: Context, key: String, v: Set<String>) = prefs(c).edit().putStringSet(key, v).apply()

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

    fun focusRemainingMs(c: Context): Long {
        val now = SecureTime.now(c)
        val untilMs = (focusUntil(c) - now).coerceAtLeast(0L)
        val mOfDay = minuteOfDay(c)
        val activeEnds = schedules(c).filter { scheduleIsActiveNow(it, c) }
            .map { minutesUntil(it.endMin, mOfDay) * 60_000L }
        val schedMs = activeEnds.maxOrNull() ?: 0L
        return maxOf(untilMs, schedMs)
    }

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
    fun stat(c: Context, kind: String) = prefs(c).getInt("stat_${kind}_${day()}", 0)

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

class BlockerNativeModule(private val rc: ReactApplicationContext) : ReactContextBaseJavaModule(rc) {

    private val io = Executors.newSingleThreadExecutor()
    private val ctx: Context get() = rc.applicationContext
    private val admin get() = ComponentName(ctx, BlockerDeviceAdminReceiver::class.java)

    init {
        BlockerForegroundService.start(ctx)
        io.execute {
            try {
                val pInfo = rc.packageManager.getPackageInfo(rc.packageName, 0)
                val currentVersion = pInfo.versionName ?: ""
                deleteObsoleteApks(currentVersion)
            } catch (_: Exception) {}
        }
    }

    override fun getName() = "BlockerNative"

    // ---------- Permissions ----------

    @ReactMethod
    fun getPermissionStatus(promise: Promise) {
        val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        promise.resolve(Arguments.createMap().apply {
            putBoolean("accessibility", isAccessibilityOn())
            putBoolean("overlay", Settings.canDrawOverlays(ctx))
            putBoolean("usageAccess", hasUsageAccess())
            putBoolean("deviceAdmin", dpm.isAdminActive(admin))
            putBoolean("adminLost", BlockerStore.prefs(ctx).getBoolean("admin_lost", false))
            putBoolean("battery", isIgnoringBatteryOptimizations())
            putBoolean("secureSettings", ctx.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED)
        })
    }

    private fun isIgnoringBatteryOptimizations(): Boolean = runCatching {
        (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(ctx.packageName)
    }.getOrDefault(false)

    private fun isAccessibilityOn(): Boolean {
        val me = ComponentName(ctx, BlockerAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    @Suppress("DEPRECATION")
    private fun hasUsageAccess(): Boolean {
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29)
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        else ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Opens the settings page for one permission. Some phones ignore ACTION_ADD_DEVICE_ADMIN (and
     * similar system screens) when started from an application Context, even with NEW_TASK set, so
     * this prefers the foreground Activity when one is available and only falls back to the
     * application Context (with NEW_TASK) if there truly isn't one. It reports back what actually
     * happened instead of failing silently.
     */
    @ReactMethod
    fun openPermissionSettings(kind: String, promise: Promise) {
        val tries: List<Intent> = when (kind) {
            "accessibility" -> listOf(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            "overlay" -> listOf(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")),
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            )
            "usageAccess" -> listOf(
                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).putExtra("package", ctx.packageName),
                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            )
            // direct system dialog first, then the full battery-optimization list, then this app's own info page
            "battery" -> listOf(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")),
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
            )
            "deviceAdmin" -> listOf(
                Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                    .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
                    .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Prevents Blocker from being uninstalled."),
                Intent(Settings.ACTION_SECURITY_SETTINGS),
                Intent(Settings.ACTION_SETTINGS)
            )
            "dnd", "notificationPolicy" -> listOf(
                Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            )
            else -> {
                promise.reject("BAD_KIND", "Unknown permission: $kind")
                return
            }
        }
        val activity = rc.currentActivity
        for ((i, intent) in tries.withIndex()) {
            try {
                if (activity != null) {
                    activity.startActivity(intent)
                } else {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    ctx.startActivity(intent)
                }
                promise.resolve(i == 0) // true only if the direct/preferred screen opened
                return
            } catch (_: ActivityNotFoundException) {
                // try the next fallback
            } catch (t: Throwable) {
                promise.reject("OPEN_FAILED", t)
                return
            }
        }
        promise.reject("NO_SCREEN", "Could not open a settings screen for $kind on this device.")
    }

    // ---------- Installed apps ----------

    @ReactMethod
    fun getInstalledApps(includeSystem: Boolean, promise: Promise) {
        io.execute {
            try {
                val pm = ctx.packageManager
                val launch = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                val blocked = BlockerStore.set(ctx, "apps")
                val exempt = BlockerStore.set(ctx, "scan_exempt")
                val seen = HashSet<String>()
                val out = Arguments.createArray()
                pm.queryIntentActivities(launch, 0)
                    .map { ri -> Pair(ri, ri.loadLabel(pm).toString()) }
                    .sortedBy { it.second.lowercase() }
                    .forEach { (ri, label) ->
                        val ai = ri.activityInfo.applicationInfo
                        val pkg = ai.packageName
                        if (pkg == ctx.packageName || !seen.add(pkg)) return@forEach
                        val system = (ai.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
                        if (system && !includeSystem) return@forEach
                        out.pushMap(Arguments.createMap().apply {
                            putString("packageName", pkg)
                            putString("label", label)
                            putBoolean("isSystem", system)
                            putString("category", categoryOf(ai))
                            putBoolean("blocked", pkg in blocked)
                            putBoolean("scanExempt", pkg in exempt)
                        })
                    }
                promise.resolve(out)
            } catch (t: Throwable) {
                promise.reject("APPS_ERROR", t)
            }
        }
    }

    private fun categoryOf(ai: ApplicationInfo): String {
        if (ai.packageName in BlockerAccessibilityService.BROWSER_URL_IDS) return "Browsers"
        if (Build.VERSION.SDK_INT < 26) return "Other"
        return when (ai.category) {
            ApplicationInfo.CATEGORY_GAME -> "Games"
            ApplicationInfo.CATEGORY_SOCIAL -> "Social"
            ApplicationInfo.CATEGORY_VIDEO -> "Video"
            ApplicationInfo.CATEGORY_AUDIO -> "Audio"
            ApplicationInfo.CATEGORY_IMAGE -> "Photos"
            ApplicationInfo.CATEGORY_NEWS -> "News"
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Productivity"
            else -> "Other"
        }
    }

    @ReactMethod
    fun getAppIcon(pkg: String, size: Int, promise: Promise) {
        io.execute {
            try {
                val s = size.coerceIn(24, 192)
                val d = ctx.packageManager.getApplicationIcon(pkg)
                val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
                d.setBounds(0, 0, s, s)
                d.draw(Canvas(bmp))
                val bos = ByteArrayOutputStream()
                bmp.compress(Bitmap.CompressFormat.PNG, 100, bos)
                promise.resolve("data:image/png;base64," + Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP))
            } catch (t: Throwable) {
                promise.reject("ICON_ERROR", t)
            }
        }
    }

    // ---------- Protection state (removals / disabling are gated by unlocked guards) ----------

    @ReactMethod
    fun setProtectionActive(active: Boolean, promise: Promise) {
        if (!active && BlockerStore.active(ctx)) {
            if (!BlockerStore.guardOpen(ctx, "protection")) {
                promise.reject("LOCKED", "Turning off protection requires the delay timer.")
                return
            }
            BlockerStore.consumeGuard(ctx, "protection")
        }
        BlockerStore.prefs(ctx).edit().putBoolean("active", active).apply()
        BlockerStore.updateIntegrityHmac(ctx)
        promise.resolve(true)
    }

    @ReactMethod
    fun setShieldEnabled(enabled: Boolean, promise: Promise) {
        if (!enabled && BlockerStore.shield(ctx) && BlockerStore.active(ctx) && !BlockerStore.guardOpen(ctx, "shield")) {
            promise.reject("LOCKED", "Turning off the shield requires the delay timer.")
            return
        }
        BlockerStore.prefs(ctx).edit().putBoolean("shield", enabled).apply()
        BlockerStore.updateIntegrityHmac(ctx)
        promise.resolve(true)
    }

    /** Adding a domain/keyword/TLD strengthens protection (immediate); removing any of them, or adding
     *  a whitelist exception, weakens it and needs its own delay guard. */
    @ReactMethod
    fun setBlocklist(
        domains: ReadableArray, keywords: ReadableArray, tlds: ReadableArray, whitelist: ReadableArray, promise: Promise
    ) {
        val d = normalize(domains)
        val k = normalize(keywords)
        val tl = normalize(tlds)
        val wl = normalize(whitelist)
        // Dropping a retired keyword (one this app itself stopped shipping) is an app update, not a user bypass,
        // so it never needs the delay timer. Without this, an update over an older install kept the old phrase forever.
        val removing = (BlockerStore.set(ctx, "domains") - d).isNotEmpty() ||
            (BlockerStore.set(ctx, "keywords") - k - RETIRED_KEYWORDS).isNotEmpty() ||
            (BlockerStore.set(ctx, "tlds") - tl).isNotEmpty()
        val addingWhitelist = (wl - BlockerStore.set(ctx, "whitelist")).isNotEmpty()
        if (BlockerStore.active(ctx)) {
            if (removing && !BlockerStore.guardOpen(ctx, "remove_blocklist")) {
                promise.reject("LOCKED", "Removing blocklist entries requires the delay timer.")
                return
            }
            if (addingWhitelist && !BlockerStore.guardOpen(ctx, "whitelist")) {
                promise.reject("LOCKED", "Adding a whitelist exception requires the delay timer.")
                return
            }
        }
        BlockerStore.putSet(ctx, "domains", d)
        BlockerStore.putSet(ctx, "keywords", k)
        BlockerStore.putSet(ctx, "tlds", tl)
        BlockerStore.putSet(ctx, "whitelist", wl)
        if (removing) BlockerStore.consumeGuard(ctx, "remove_blocklist")
        if (addingWhitelist) BlockerStore.consumeGuard(ctx, "whitelist")
        promise.resolve(true)
    }

    @ReactMethod
    fun setBlockedApps(packages: ReadableArray, promise: Promise) {
        val n = normalize(packages)
        val removing = (BlockerStore.set(ctx, "apps") - n).isNotEmpty()
        if (BlockerStore.active(ctx) && removing && !BlockerStore.guardOpen(ctx, "remove_apps")) {
            promise.reject("LOCKED", "Unblocking apps requires the delay timer.")
            return
        }
        BlockerStore.putSet(ctx, "apps", n)
        if (removing) BlockerStore.consumeGuard(ctx, "remove_apps")
        promise.resolve(true)
    }

    /** Exempting an app from screen scanning weakens protection (gated); un-exempting is immediate. */
    @ReactMethod
    fun setExemptApps(packages: ReadableArray, promise: Promise) {
        val n = normalize(packages)
        val adding = (n - BlockerStore.set(ctx, "scan_exempt")).isNotEmpty()
        if (BlockerStore.active(ctx) && adding && !BlockerStore.guardOpen(ctx, "exempt_apps")) {
            promise.reject("LOCKED", "Exempting an app from screen monitoring requires the delay timer.")
            return
        }
        BlockerStore.putSet(ctx, "scan_exempt", n)
        if (adding) BlockerStore.consumeGuard(ctx, "exempt_apps")
        promise.resolve(true)
    }

    private fun normalize(a: ReadableArray): Set<String> =
        (0 until a.size()).mapNotNull { a.getString(it)?.trim()?.lowercase()?.takeIf { s -> s.isNotEmpty() } }.toSet()

    // ---------- Granular focus toggles ----------

    @ReactMethod
    fun setGranularFocusToggle(key: String, enabled: Boolean, promise: Promise) {
        if (!enabled && BlockerStore.active(ctx) && BlockerStore.granularToggle(ctx, key)) {
            if (!BlockerStore.guardOpen(ctx, "granular_focus")) {
                promise.reject("LOCKED", "Disabling distraction shields requires the delay timer.")
                return
            }
            BlockerStore.consumeGuard(ctx, "granular_focus")
        }
        BlockerStore.setGranularToggle(ctx, key, enabled)
        promise.resolve(true)
    }

    @ReactMethod
    fun getGranularFocusToggles(promise: Promise) {
        promise.resolve(Arguments.createMap().apply {
            putBoolean("block_fb_reels", BlockerStore.granularToggle(ctx, "block_fb_reels"))
            putBoolean("block_insta_reels", BlockerStore.granularToggle(ctx, "block_insta_reels"))
            putBoolean("block_insta_search", BlockerStore.granularToggle(ctx, "block_insta_search"))
            putBoolean("block_yt_shorts", BlockerStore.granularToggle(ctx, "block_yt_shorts"))
        })
    }

    // ---------- Individual App Usage Limits ----------

    @ReactMethod
    fun getAppUsageLimits(promise: Promise) {
        val limits = BlockerStore.appLimits(ctx)
        val map = Arguments.createMap()
        limits.forEach { (pkg, limit) ->
            map.putInt(pkg, limit)
        }
        promise.resolve(map)
    }

    @ReactMethod
    fun setAppUsageLimit(pkg: String, limitMinutes: Int, promise: Promise) {
        val curLimits = BlockerStore.appLimits(ctx)
        val cur = curLimits[pkg] ?: 0
        val weakening = (limitMinutes == 0 && cur > 0) || (limitMinutes > cur && cur > 0)
        if (BlockerStore.active(ctx) && weakening && !BlockerStore.guardOpen(ctx, "app_limits")) {
            promise.reject("LOCKED", "Increasing or removing an app usage limit requires the delay timer.")
            return
        }
        BlockerStore.setAppLimit(ctx, pkg, limitMinutes)
        if (weakening) BlockerStore.consumeGuard(ctx, "app_limits")
        promise.resolve(true)
    }

    @ReactMethod
    fun getAppUsageToday(promise: Promise) {
        val map = Arguments.createMap()
        val stats = BlockerStore.getAllTodayUsageMillis(ctx)
        stats.forEach { (pkg, millis) ->
            val minutes = (millis / 60_000L).toInt()
            map.putInt(pkg, minutes)
        }
        promise.resolve(map)
    }

    // ---------- Per-setting delay timers ----------

    @ReactMethod
    fun setDelayDays(days: Int, promise: Promise) {
        val d = days.coerceIn(1, 30)
        if (d < BlockerStore.delayDays(ctx) && BlockerStore.active(ctx)) {
            if (!BlockerStore.guardOpen(ctx, "delay_duration")) {
                promise.reject("LOCKED", "Shortening the delay requires the delay timer.")
                return
            }
            BlockerStore.consumeGuard(ctx, "delay_duration")
        }
        BlockerStore.prefs(ctx).edit().putInt("delay_days", d).apply()
        BlockerStore.updateIntegrityHmac(ctx)
        promise.resolve(d)
    }

    @ReactMethod
    fun getDelayDays(promise: Promise) = promise.resolve(BlockerStore.delayDays(ctx))

    /** Starts the countdown for `key`; idempotent while a countdown is running. Resolves unlock epoch ms. */
    @ReactMethod
    fun requestSettingChange(key: String, promise: Promise) {
        val existing = BlockerStore.pendingUntil(ctx, key)
        val now = SecureTime.now(ctx)
        val until = if (existing != 0L) existing else now + BlockerStore.delayMs(ctx)
        BlockerStore.prefs(ctx).edit().putLong("pending_$key", until).apply()
        BlockerStore.updateIntegrityHmac(ctx)
        promise.resolve(until.toDouble())
    }

    @ReactMethod
    fun getSettingLocks(keys: ReadableArray, promise: Promise) {
        val now = SecureTime.now(ctx)
        val out = Arguments.createMap()
        for (i in 0 until keys.size()) {
            val k = keys.getString(i) ?: continue
            val until = BlockerStore.pendingUntil(ctx, k)
            out.putMap(k, Arguments.createMap().apply {
                putBoolean("pending", until != 0L)
                putDouble("unlockAt", until.toDouble())
                putDouble("remainingMs", (until - now).coerceAtLeast(0L).toDouble())
                putBoolean("ready", until != 0L && now >= until)
                putBoolean("open", BlockerStore.guardOpen(ctx, k))
            })
        }
        promise.resolve(out)
    }

    /** Manual confirmation after countdown reaches zero; opens a 10-minute window to apply the change. */
    @ReactMethod
    fun confirmSettingChange(key: String, promise: Promise) {
        val until = BlockerStore.pendingUntil(ctx, key)
        val now = SecureTime.now(ctx)
        when {
            until == 0L -> promise.reject("NO_PENDING", "No pending change for $key")
            now < until -> promise.reject("NOT_READY", "Timer still running")
            else -> {
                val open = now + BlockerStore.GUARD_WINDOW_MS
                BlockerStore.prefs(ctx).edit().remove("pending_$key").putLong("open_$key", open).apply()
                BlockerStore.updateIntegrityHmac(ctx)
                promise.resolve(open.toDouble())
            }
        }
    }

    @ReactMethod
    fun cancelSettingChange(key: String, promise: Promise) {
        BlockerStore.prefs(ctx).edit().remove("pending_$key").remove("open_$key").apply()
        BlockerStore.updateIntegrityHmac(ctx)
        promise.resolve(true)
    }

    // ---------- Screen monitor ----------
    // Always on — there is no user-facing toggle for this; it's core to how Blocker works.

    // ---------- Focus mode ----------
    // Lengthening / enabling is immediate. Ending early, shortening, disabling the schedule, or
    // allowing more apps while focus runs needs the "focus" guard (JS clears it after confirming).

    @ReactMethod
    fun startFocus(minutes: Int, promise: Promise) {
        if (BlockerStore.focusActive(ctx)) {
            promise.reject("ACTIVE", "Focus mode is already running.")
            return
        }
        val m = minutes.coerceIn(1, 10_080)
        BlockerStore.prefs(ctx).edit().putLong("focus_until", SecureTime.now(ctx) + m * 60_000L).apply()
        BlockerStore.updateIntegrityHmac(ctx)
        promise.resolve(true)
    }

    @ReactMethod
    fun extendFocus(minutes: Int, promise: Promise) {
        val now = SecureTime.now(ctx)
        val base = maxOf(BlockerStore.focusUntil(ctx), now)
        val m = minutes.coerceIn(1, 10_080)
        BlockerStore.prefs(ctx).edit().putLong("focus_until", base + m * 60_000L).apply()
        BlockerStore.updateIntegrityHmac(ctx)
        promise.resolve(true)
    }

    @ReactMethod
    fun shortenFocus(minutes: Int, promise: Promise) {
        if (BlockerStore.focusActive(ctx) && !BlockerStore.guardOpen(ctx, "focus")) {
            promise.reject("LOCKED", "Shortening focus mode requires the delay timer.")
            return
        }
        val target = SecureTime.now(ctx) + minutes.coerceAtLeast(1) * 60_000L
        if (target < BlockerStore.focusUntil(ctx)) {
            BlockerStore.prefs(ctx).edit().putLong("focus_until", target).apply()
            BlockerStore.updateIntegrityHmac(ctx)
        }
        promise.resolve(true)
    }

    @ReactMethod
    fun endFocus(promise: Promise) {
        if (BlockerStore.focusActive(ctx) && !BlockerStore.guardOpen(ctx, "focus")) {
            promise.reject("LOCKED", "Ending focus mode early requires the delay timer.")
            return
        }
        BlockerStore.prefs(ctx).edit().remove("focus_until").apply()
        BlockerStore.updateIntegrityHmac(ctx)
        promise.resolve(true)
    }

    // ---------- Study mode (Aggressive Pomodoro Phone Lockout) ----------

    @ReactMethod
    fun startStudy(focusMinutes: Int, breakMinutes: Int, totalRounds: Int, promise: Promise) {
        BlockerStore.startStudy(ctx, focusMinutes, breakMinutes, totalRounds)
        promise.resolve(true)
    }

    @ReactMethod
    fun endStudy(promise: Promise) {
        BlockerStore.endStudySession(ctx)
        promise.resolve(true)
    }

    @ReactMethod
    fun skipStudyBreak(promise: Promise) {
        BlockerStore.skipStudyBreak(ctx)
        promise.resolve(true)
    }

    @ReactMethod
    fun isWifiOn(promise: Promise) {
        try {
            val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            @Suppress("DEPRECATION")
            promise.resolve(wm?.isWifiEnabled ?: false)
        } catch (_: Exception) {
            promise.resolve(false)
        }
    }

    @ReactMethod
    fun openWifiSettings(promise: Promise) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    val intent = Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    ctx.startActivity(intent)
                    promise.resolve(true)
                    return
                } catch (_: Exception) {}
                val intent = Intent(Settings.Panel.ACTION_WIFI).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                ctx.startActivity(intent)
            } else {
                val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                ctx.startActivity(intent)
            }
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("FAIL", e.message)
        }
    }

    @ReactMethod
    fun setWifiEnabled(enabled: Boolean, promise: Promise) {
        try {
            if (enabled) BlockerStore.restoreWifi(ctx) else BlockerStore.disableWifi(ctx)
            val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            @Suppress("DEPRECATION")
            promise.resolve(wm?.isWifiEnabled ?: false)
        } catch (e: Exception) {
            promise.resolve(false)
        }
    }

    @ReactMethod
    fun hasDndAccess(promise: Promise) {
        try {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && nm != null) {
                promise.resolve(nm.isNotificationPolicyAccessGranted)
            } else {
                promise.resolve(true)
            }
        } catch (_: Exception) {
            promise.resolve(false)
        }
    }

    @ReactMethod
    fun requestDndAccess(promise: Promise) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                ctx.startActivity(intent)
            }
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("FAIL", e.message)
        }
    }

    @ReactMethod
    fun setFocusApps(packages: ReadableArray, promise: Promise) {
        val n = normalize(packages)
        if (n.size > 5) {
            promise.reject("TOO_MANY", "Pick at most 5 essential apps.")
            return
        }
        val adding = (n - BlockerStore.focusApps(ctx)).isNotEmpty()
        if (BlockerStore.focusActive(ctx) && adding && !BlockerStore.guardOpen(ctx, "focus")) {
            promise.reject("LOCKED", "Allowing more apps during focus mode requires the delay timer.")
            return
        }
        BlockerStore.putSet(ctx, "focus_apps", n)
        promise.resolve(true)
    }

    /** A recurring daily focus window, e.g. "Bedtime" 22:00 to 06:00. Creating one, enabling one, or
     *  widening one is immediate. Shrinking, disabling, or deleting one that is currently in effect
     *  needs the "focus" guard; doing any of those while it's not currently active is immediate. */
    @ReactMethod
    fun addSchedule(label: String, startMin: Int, endMin: Int, enabled: Boolean, allowedApps: ReadableArray?, promise: Promise) {
        val appSet = mutableSetOf<String>()
        if (allowedApps != null) {
            for (i in 0 until allowedApps.size()) {
                allowedApps.getString(i)?.let { if (it.isNotEmpty()) appSet.add(it) }
            }
        }
        val s = FocusSchedule(
            java.util.UUID.randomUUID().toString(), label.trim().ifEmpty { "Schedule" },
            startMin.coerceIn(0, 1439), endMin.coerceIn(0, 1439), enabled, appSet
        )
        BlockerStore.putSchedules(ctx, BlockerStore.schedules(ctx) + s)
        promise.resolve(s.id)
    }

    @ReactMethod
    fun updateSchedule(id: String, label: String, startMin: Int, endMin: Int, enabled: Boolean, allowedApps: ReadableArray?, promise: Promise) {
        val list = BlockerStore.schedules(ctx)
        val old = list.find { it.id == id } ?: run {
            promise.reject("NOT_FOUND", "No such schedule.")
            return
        }
        val appSet = if (allowedApps != null) {
            val set = mutableSetOf<String>()
            for (i in 0 until allowedApps.size()) {
                allowedApps.getString(i)?.let { if (it.isNotEmpty()) set.add(it) }
            }
            set
        } else {
            old.allowedApps
        }
        val new = FocusSchedule(id, label.trim().ifEmpty { "Schedule" }, startMin.coerceIn(0, 1439), endMin.coerceIn(0, 1439), enabled, appSet)
        val wasActive = BlockerStore.scheduleIsActiveNow(old)
        val shrinking = wasActive && (!new.enabled || windowLen(new.startMin, new.endMin) < windowLen(old.startMin, old.endMin))
        if (shrinking && !BlockerStore.guardOpen(ctx, "schedule") && !BlockerStore.guardOpen(ctx, "focus")) {
            promise.reject("LOCKED", "Shortening or disabling an active schedule requires the delay timer.")
            return
        }
        BlockerStore.putSchedules(ctx, list.map { if (it.id == id) new else it })
        promise.resolve(true)
    }

    @ReactMethod
    fun deleteSchedule(id: String, promise: Promise) {
        val list = BlockerStore.schedules(ctx)
        val old = list.find { it.id == id } ?: run {
            promise.resolve(true) // already gone
            return
        }
        if (BlockerStore.scheduleIsActiveNow(old) && !BlockerStore.guardOpen(ctx, "schedule") && !BlockerStore.guardOpen(ctx, "focus")) {
            promise.reject("LOCKED", "Deleting an active schedule requires the delay timer.")
            return
        }
        BlockerStore.putSchedules(ctx, list.filterNot { it.id == id })
        promise.resolve(true)
    }

    @ReactMethod
    fun getSchedules(promise: Promise) {
        val out = Arguments.createArray()
        BlockerStore.schedules(ctx).forEach { s ->
            out.pushMap(Arguments.createMap().apply {
                putString("id", s.id)
                putString("label", s.label)
                putInt("startMin", s.startMin)
                putInt("endMin", s.endMin)
                putBoolean("enabled", s.enabled)
                putBoolean("activeNow", BlockerStore.scheduleIsActiveNow(s))
                val appsArr = Arguments.createArray()
                s.allowedApps.forEach { appsArr.pushString(it) }
                putArray("allowedApps", appsArr)
            })
        }
        promise.resolve(out)
    }

    private fun windowLen(start: Int, end: Int) = if (start <= end) end - start else 1440 - start + end

    /** Shows Android's own clock-style time picker (AM/PM) and resolves the chosen time as minutes since midnight. */
    @ReactMethod
    fun pickTime(initialMinute: Int, promise: Promise) {
        val activity = rc.currentActivity
        if (activity == null) {
            promise.reject("NO_ACTIVITY", "App is not in the foreground.")
            return
        }
        val m = initialMinute.coerceIn(0, 1439)
        activity.runOnUiThread {
            var resolved = false
            val dialog = TimePickerDialog(
                activity,
                android.R.style.Theme_DeviceDefault_Dialog_Alert,
                { _, hour, minute ->
                    resolved = true
                    promise.resolve(hour * 60 + minute)
                },
                m / 60, m % 60, false
            )
            dialog.setOnCancelListener { if (!resolved) promise.reject("CANCELLED", "Time picker was cancelled.") }
            dialog.show()
        }
    }

    @ReactMethod
    fun launchApp(pkg: String, promise: Promise) {
        if (BlockerStore.focusActive(ctx) && pkg !in BlockerStore.focusApps(ctx)) {
            promise.reject("LOCKED", "Not an essential app.")
            return
        }
        val i = ctx.packageManager.getLaunchIntentForPackage(pkg)
        if (i == null) {
            promise.reject("NOT_INSTALLED", "That app is not installed.")
            return
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
        promise.resolve(true)
    }

    // ---------- JS state persistence ----------

    @ReactMethod
    fun loadState(promise: Promise) = promise.resolve(BlockerStore.prefs(ctx).getString("js_state", null))

    @ReactMethod
    fun saveState(json: String, promise: Promise) {
        if (json.length > 2 * 1024 * 1024) {
            promise.reject("PAYLOAD_TOO_LARGE", "State JSON exceeds maximum allowable size (2MB)")
            return
        }
        try {
            org.json.JSONTokener(json).nextValue()
        } catch (e: Exception) {
            promise.reject("INVALID_JSON", "Payload is not valid JSON")
            return
        }
        BlockerStore.prefs(ctx).edit().putString("js_state", json).apply()
        promise.resolve(true)
    }

    // ---------- Stats ----------

    @ReactMethod
    fun getStats(promise: Promise) {
        promise.resolve(Arguments.createMap().apply {
            putInt("sitesBlockedToday", BlockerStore.stat(ctx, "sites"))
            putInt("appsBlockedToday", BlockerStore.stat(ctx, "apps"))
            putInt("tamperAttemptsToday", BlockerStore.stat(ctx, "tamper"))
            putInt("screenBlocksToday", BlockerStore.stat(ctx, "screen"))
            putBoolean("focusActive", BlockerStore.focusActive(ctx))
            putDouble("focusUntil", BlockerStore.focusUntil(ctx).toDouble())
            putDouble("focusRemainingMs", BlockerStore.focusRemainingMs(ctx).toDouble())
            putArray("focusApps", Arguments.fromList(BlockerStore.focusApps(ctx).toList()))
            putBoolean("active", BlockerStore.active(ctx))
            putBoolean("shield", BlockerStore.shield(ctx))
            putBoolean("studyActive", BlockerStore.studyActive(ctx))
            putDouble("studyUntil", BlockerStore.studyUntil(ctx).toDouble())
            putDouble("studyRemainingMs", BlockerStore.studyRemainingMs(ctx).toDouble())
            putInt("studyTotalRounds", BlockerStore.studyTotalRounds(ctx))
            putInt("studyCurrentRound", BlockerStore.studyCurrentRound(ctx))
            putBoolean("studyIsBreak", BlockerStore.studyIsBreak(ctx))
            putInt("studyFocusMinutes", BlockerStore.studyFocusMinutes(ctx))
            putInt("studyBreakMinutes", BlockerStore.studyBreakMinutes(ctx))
        })
    }

    // ---------- Event Emitter support ----------

    private fun sendEvent(eventName: String, params: WritableMap?) {
        try {
            if (rc.hasActiveReactInstance()) {
                rc.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                    .emit(eventName, params)
            }
        } catch (_: Exception) {}
    }

    @ReactMethod
    fun addListener(eventName: String) {
        // Required for React Native EventEmitter
    }

    @ReactMethod
    fun removeListeners(count: Int) {
        // Required for React Native EventEmitter
    }

    // ---------- In-App Updates ----------

    @ReactMethod
    fun checkCanInstallPackages(promise: Promise) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                promise.resolve(rc.packageManager.canRequestPackageInstalls())
            } else {
                promise.resolve(true)
            }
        } catch (e: Exception) {
            promise.resolve(true)
        }
    }

    @ReactMethod
    fun openInstallPermissionSettings(promise: Promise) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${rc.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                rc.startActivity(intent)
                promise.resolve(true)
            } else {
                promise.resolve(true)
            }
        } catch (e: Exception) {
            promise.reject("ERR_SETTINGS", e.message)
        }
    }

    @ReactMethod
    fun getUpdateCheckTimestamp(promise: Promise) {
        promise.resolve(BlockerStore.prefs(ctx).getLong("last_update_check_ts", 0L).toDouble())
    }

    @ReactMethod
    fun setUpdateCheckTimestamp(timestamp: Double, promise: Promise) {
        BlockerStore.prefs(ctx).edit().putLong("last_update_check_ts", timestamp.toLong()).apply()
        promise.resolve(true)
    }

    @ReactMethod
    fun getLastNotifiedVersion(promise: Promise) {
        promise.resolve(BlockerStore.prefs(ctx).getString("last_notified_version", null))
    }

    @ReactMethod
    fun setLastNotifiedVersion(version: String, promise: Promise) {
        BlockerStore.prefs(ctx).edit().putString("last_notified_version", version).apply()
        promise.resolve(true)
    }

    @ReactMethod
    fun getHasPendingUpdate(promise: Promise) {
        promise.resolve(BlockerStore.prefs(ctx).getBoolean("has_pending_update", false))
    }

    @ReactMethod
    fun setHasPendingUpdate(pending: Boolean, promise: Promise) {
        BlockerStore.prefs(ctx).edit().putBoolean("has_pending_update", pending).apply()
        promise.resolve(true)
    }

    @ReactMethod
    fun showUpdateNotification(title: String, message: String, version: String, promise: Promise) {
        try {
            val channelId = "blocker_updates"
            val notificationManager = rc.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "Blocker Updates",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifications for new releases and updates"
                }
                notificationManager.createNotificationChannel(channel)
            }
            val launchIntent = rc.packageManager.getLaunchIntentForPackage(rc.packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("open_update", true)
                putExtra("update_version", version)
            }
            val pendingIntent = PendingIntent.getActivity(
                rc,
                1002,
                launchIntent,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE else PendingIntent.FLAG_UPDATE_CURRENT
            )
            val builder = NotificationCompat.Builder(rc, channelId)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
            notificationManager.notify(1002, builder.build())
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("ERR_NOTIFICATION", e.message)
        }
    }

    @ReactMethod
    fun downloadAndInstallApk(downloadUrl: String, version: String, promise: Promise) {
        Executors.newSingleThreadExecutor().execute {
            try {
                // SECURITY: Sanitize version to prevent path traversal
                val safeVersion = version.trim()
                if (!safeVersion.matches(Regex("^[a-zA-Z0-9._-]+$"))) {
                    promise.reject("INVALID_VERSION", "Invalid version string format")
                    return@execute
                }

                // SECURITY: If running Release build, strictly forbid downloading/installing debug APKs
                val isCurrentDebug = (rc.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
                if (!isCurrentDebug && (downloadUrl.contains("debug", ignoreCase = true) || safeVersion.contains("debug", ignoreCase = true))) {
                    promise.reject("DEBUG_UPDATE_FORBIDDEN", "Updating to debug version is not permitted from the release version")
                    return@execute
                }

                // SECURITY: Enforce HTTPS scheme and trusted GitHub hosts from official repository
                val initialUrl = URL(downloadUrl)
                if (!initialUrl.protocol.equals("https", ignoreCase = true)) {
                    promise.reject("INSECURE_URL", "Only HTTPS downloads are permitted")
                    return@execute
                }
                val host = initialUrl.host.lowercase()
                val isTrustedHost = host == "github.com" ||
                                    host.endsWith(".github.com") ||
                                    host.endsWith(".githubusercontent.com")
                if (!isTrustedHost) {
                    promise.reject("UNTRUSTED_HOST", "Updates can only be downloaded from official GitHub domains")
                    return@execute
                }
                val path = initialUrl.path.lowercase()
                if (!path.contains("shell-ninja/blocker-app")) {
                    promise.reject("UNTRUSTED_REPOSITORY", "Updates can only originate from shell-ninja/Blocker-App repository")
                    return@execute
                }

                val updatesDir = File(rc.cacheDir, "updates")
                if (!updatesDir.exists()) updatesDir.mkdirs()
                val apkFile = File(updatesDir, "Blocker-$safeVersion.apk")

                // SECURITY: Verify canonical path remains within the updates cache directory
                if (!apkFile.canonicalFile.startsWith(updatesDir.canonicalFile)) {
                    promise.reject("PATH_TRAVERSAL", "Invalid destination file path")
                    return@execute
                }
                if (apkFile.exists()) apkFile.delete()

                var conn = initialUrl.openConnection() as HttpURLConnection
                conn.connectTimeout = 30000
                conn.readTimeout = 30000
                conn.instanceFollowRedirects = true
                conn.setRequestProperty("User-Agent", "Blocker-App-Android")
                conn.connect()

                var responseCode = conn.responseCode
                var redirects = 0
                while ((responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                        responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                        responseCode == HttpURLConnection.HTTP_SEE_OTHER ||
                        responseCode == 307 || responseCode == 308) && redirects < 5) {
                    val newUrl = conn.getHeaderField("Location")
                    conn.disconnect()
                    if (newUrl.isNullOrEmpty()) break

                    val nextUrl = URL(newUrl)
                    // SECURITY: Ensure redirects remain strictly on HTTPS and trusted GitHub hosts
                    if (!nextUrl.protocol.equals("https", ignoreCase = true)) {
                        promise.reject("INSECURE_REDIRECT", "Redirected to non-HTTPS URL")
                        return@execute
                    }
                    val redirectHost = nextUrl.host.lowercase()
                    val isRedirectTrusted = redirectHost == "github.com" ||
                                            redirectHost.endsWith(".github.com") ||
                                            redirectHost.endsWith(".githubusercontent.com")
                    if (!isRedirectTrusted) {
                        promise.reject("UNTRUSTED_REDIRECT", "Redirected to untrusted host: $redirectHost")
                        return@execute
                    }

                    conn = nextUrl.openConnection() as HttpURLConnection
                    conn.connectTimeout = 30000
                    conn.readTimeout = 30000
                    conn.setRequestProperty("User-Agent", "Blocker-App-Android")
                    conn.connect()
                    responseCode = conn.responseCode
                    redirects++
                }

                if (responseCode !in 200..299) {
                    promise.reject("DOWNLOAD_FAILED", "Server returned HTTP $responseCode")
                    return@execute
                }

                // SECURITY: Cap maximum allowed download size (200MB max) to prevent DOS/disk exhaustion
                val maxAllowedBytes = 200L * 1024L * 1024L
                val totalBytes = conn.contentLength.toLong()
                if (totalBytes > maxAllowedBytes) {
                    conn.disconnect()
                    promise.reject("FILE_TOO_LARGE", "Update file exceeds size limit")
                    return@execute
                }

                var downloadedBytes = 0L
                val input = BufferedInputStream(conn.inputStream)
                val output = FileOutputStream(apkFile)
                val buffer = ByteArray(16384)
                var bytesRead: Int
                var lastProgressEmit = 0L

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    downloadedBytes += bytesRead
                    if (downloadedBytes > maxAllowedBytes) {
                        output.close()
                        input.close()
                        conn.disconnect()
                        if (apkFile.exists()) apkFile.delete()
                        promise.reject("FILE_TOO_LARGE", "Downloaded APK exceeded size limit")
                        return@execute
                    }
                    output.write(buffer, 0, bytesRead)
                    val now = System.currentTimeMillis()
                    if (now - lastProgressEmit > 120 || downloadedBytes == totalBytes) {
                        lastProgressEmit = now
                        val progress = if (totalBytes > 0) (downloadedBytes.toDouble() / totalBytes) else 0.0
                        val map = Arguments.createMap().apply {
                            putDouble("progress", progress)
                            putDouble("downloadedBytes", downloadedBytes.toDouble())
                            putDouble("totalBytes", totalBytes.toDouble())
                        }
                        sendEvent("apkDownloadProgress", map)
                    }
                }
                output.flush()
                output.close()
                input.close()
                conn.disconnect()

                // SECURITY: Verify download wasn't truncated or empty
                if (totalBytes > 0 && downloadedBytes < totalBytes) {
                    if (apkFile.exists()) apkFile.delete()
                    promise.reject("INCOMPLETE_DOWNLOAD", "Download incomplete: received $downloadedBytes of $totalBytes bytes")
                    return@execute
                }
                if (downloadedBytes <= 1024) {
                    if (apkFile.exists()) apkFile.delete()
                    promise.reject("INVALID_APK", "Downloaded file is too small to be a valid APK")
                    return@execute
                }

                launchInstallIntent(apkFile)
                promise.resolve(true)
            } catch (e: Exception) {
                promise.reject("DOWNLOAD_ERROR", e.message ?: "Failed downloading APK")
            }
        }
    }

    @ReactMethod
    fun installDownloadedApk(version: String, promise: Promise) {
        try {
            val safeVersion = version.trim()
            if (!safeVersion.matches(Regex("^[a-zA-Z0-9._-]+$"))) {
                promise.reject("INVALID_VERSION", "Invalid version string format")
                return
            }
            val isCurrentDebug = (rc.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
            if (!isCurrentDebug && safeVersion.contains("debug", ignoreCase = true)) {
                promise.reject("DEBUG_UPDATE_FORBIDDEN", "Updating to debug version is not permitted from the release version")
                return
            }
            val updatesDir = File(rc.cacheDir, "updates")
            val apkFile = File(updatesDir, "Blocker-$safeVersion.apk")
            if (!apkFile.canonicalFile.startsWith(updatesDir.canonicalFile)) {
                promise.reject("PATH_TRAVERSAL", "Invalid file path")
                return
            }
            if (!apkFile.exists() || apkFile.length() <= 1024) {
                promise.reject("ERR_NOT_FOUND", "Downloaded APK file not found or invalid")
                return
            }
            launchInstallIntent(apkFile)
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("ERR_INSTALL", e.message)
        }
    }

    private fun launchInstallIntent(apkFile: File) {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        val pi = rc.packageManager.getPackageArchiveInfo(apkFile.absolutePath, flags)
        if (pi == null) {
            if (apkFile.exists()) apkFile.delete()
            throw IllegalArgumentException("Downloaded file is not a valid Android package archive")
        }
        if (pi.packageName != rc.packageName) {
            if (apkFile.exists()) apkFile.delete()
            throw SecurityException("Downloaded package (${pi.packageName}) does not match application (${rc.packageName})")
        }

        // Verify code signing certificate matches the currently running app
        pi.applicationInfo?.sourceDir = apkFile.absolutePath
        pi.applicationInfo?.publicSourceDir = apkFile.absolutePath
        val currentPkgInfo = try {
            rc.packageManager.getPackageInfo(rc.packageName, flags)
        } catch (e: Exception) {
            null
        }
        if (currentPkgInfo != null) {
            val isSignatureMatch = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val currentSigs = currentPkgInfo.signingInfo?.apkContentsSigners?.map { it.toByteArray().toList() }?.toSet().orEmpty()
                val apkSigs = pi.signingInfo?.apkContentsSigners?.map { it.toByteArray().toList() }?.toSet().orEmpty()
                currentSigs.isNotEmpty() && apkSigs.isNotEmpty() && currentSigs == apkSigs
            } else {
                @Suppress("DEPRECATION")
                val currentSigs = currentPkgInfo.signatures?.map { it.toByteArray().toList() }?.toSet().orEmpty()
                @Suppress("DEPRECATION")
                val apkSigs = pi.signatures?.map { it.toByteArray().toList() }?.toSet().orEmpty()
                currentSigs.isNotEmpty() && apkSigs.isNotEmpty() && currentSigs == apkSigs
            }
            if (!isSignatureMatch) {
                if (apkFile.exists()) apkFile.delete()
                throw SecurityException("Downloaded update signing certificate does not match authentic Blocker app certificate!")
            }
        }

        val isCurrentDebug = (rc.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (!isCurrentDebug) {
            val isApkDebug = pi.applicationInfo?.flags?.let { (it and ApplicationInfo.FLAG_DEBUGGABLE) != 0 } ?: false
            if (isApkDebug || apkFile.name.contains("debug", ignoreCase = true)) {
                if (apkFile.exists()) apkFile.delete()
                throw SecurityException("Updating to debug version is not permitted from the release version")
            }
        }

        val newVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pi.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            pi.versionCode.toLong()
        }
        if (currentPkgInfo != null) {
            val currentVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                currentPkgInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                currentPkgInfo.versionCode.toLong()
            }
            if (newVersionCode < currentVersionCode) {
                if (apkFile.exists()) apkFile.delete()
                throw IllegalStateException("Downloaded update (code $newVersionCode) is older than currently installed version (code $currentVersionCode). Downgrades cannot be installed.")
            }
        }
        val apkUri = FileProvider.getUriForFile(
            rc,
            "${rc.packageName}.fileprovider",
            apkFile
        )
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        rc.startActivity(installIntent)
    }

    @ReactMethod
    fun isDebugBuild(promise: Promise) {
        val debug = (rc.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        promise.resolve(debug)
    }

    @ReactMethod
    fun cleanupOldUpdateApks(currentVersion: String, promise: Promise) {
        io.execute {
            try {
                val count = deleteObsoleteApks(currentVersion)
                promise.resolve(count)
            } catch (e: Exception) {
                promise.resolve(0)
            }
        }
    }

    @ReactMethod
    fun clearAllUpdateApks(promise: Promise) {
        io.execute {
            try {
                val updatesDir = File(rc.cacheDir, "updates")
                var deletedCount = 0
                var freedBytes = 0L
                if (updatesDir.exists() && updatesDir.isDirectory) {
                    val files = updatesDir.listFiles() ?: emptyArray()
                    for (file in files) {
                        if (file.isFile) {
                            val len = file.length()
                            if (file.delete()) {
                                deletedCount++
                                freedBytes += len
                            }
                        }
                    }
                }
                val map = Arguments.createMap().apply {
                    putInt("deletedCount", deletedCount)
                    putDouble("freedBytes", freedBytes.toDouble())
                }
                promise.resolve(map)
            } catch (e: Exception) {
                promise.reject("ERR_CLEAR_CACHE", e.message ?: "Failed to clear update cache")
            }
        }
    }

    @ReactMethod
    fun getUpdateCacheStats(promise: Promise) {
        io.execute {
            try {
                val updatesDir = File(rc.cacheDir, "updates")
                var fileCount = 0
                var totalBytes = 0L
                if (updatesDir.exists() && updatesDir.isDirectory) {
                    val files = updatesDir.listFiles() ?: emptyArray()
                    for (file in files) {
                        if (file.isFile) {
                            fileCount++
                            totalBytes += file.length()
                        }
                    }
                }
                val map = Arguments.createMap().apply {
                    putInt("fileCount", fileCount)
                    putDouble("totalBytes", totalBytes.toDouble())
                }
                promise.resolve(map)
            } catch (e: Exception) {
                val map = Arguments.createMap().apply {
                    putInt("fileCount", 0)
                    putDouble("totalBytes", 0.0)
                }
                promise.resolve(map)
            }
        }
    }

    private fun compareSemVer(v1: String, v2: String): Int {
        val parse = { v: String ->
            v.trim().removePrefix("v").removePrefix("V")
                .split("-")[0].split("+")[0]
                .split(".")
                .map { it.toIntOrNull() ?: 0 }
        }
        val p1 = parse(v1)
        val p2 = parse(v2)
        val maxLen = maxOf(p1.size, p2.size)
        for (i in 0 until maxLen) {
            val num1 = p1.getOrElse(i) { 0 }
            val num2 = p2.getOrElse(i) { 0 }
            if (num1 > num2) return 1
            if (num1 < num2) return -1
        }
        return 0
    }

    private fun deleteObsoleteApks(currentVersion: String): Int {
        var deletedCount = 0
        try {
            val updatesDir = File(rc.cacheDir, "updates")
            if (!updatesDir.exists() || !updatesDir.isDirectory) return 0
            val files = updatesDir.listFiles() ?: return 0
            for (file in files) {
                if (file.isFile && file.name.startsWith("Blocker-") && file.name.endsWith(".apk")) {
                    val apkVer = file.name.removePrefix("Blocker-").removeSuffix(".apk")
                    if (currentVersion.isNotEmpty() && compareSemVer(currentVersion, apkVer) >= 0) {
                        if (file.delete()) deletedCount++
                    } else if (file.length() <= 1024L) {
                        if (file.delete()) deletedCount++
                    }
                }
            }
        } catch (_: Exception) {}
        return deletedCount
    }
}

class BlockerPackage : ReactPackage {
    override fun createNativeModules(rc: ReactApplicationContext): List<NativeModule> =
        listOf(BlockerNativeModule(rc), ProtectionModule(rc))
    override fun createViewManagers(rc: ReactApplicationContext): List<ViewManager<*, *>> = emptyList()
}