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
import java.security.MessageDigest
import java.util.UUID
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/** Keywords this app used to ship. They are dropped from saved lists without the delay timer. */
private val RETIRED_KEYWORDS = setOf("usb debugging", "oem unlocking", "oem unlock", "bare")

data class PasswordRuleCheck(
    val minLength: Boolean,
    val hasUpper: Boolean,
    val hasLower: Boolean,
    val hasDigit: Boolean,
    val hasSpecial: Boolean
) {
    val isValid: Boolean get() = minLength && hasUpper && hasLower && hasDigit && hasSpecial
    val score: Int get() = listOf(minLength, hasUpper, hasLower, hasDigit, hasSpecial).count { it }
}

/** One recurring daily focus window, e.g. "Bedtime" 22:00 to 06:00, with optional per-schedule unblocked apps. */
data class FocusSchedule(
    val id: String,
    val label: String,
    val startMin: Int,
    val endMin: Int,
    val enabled: Boolean,
    val allowedApps: Set<String> = emptySet(),
    val allowedPackages: Set<String> = allowedApps,
    val daysOfWeek: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7) // 1=Mon .. 7=Sun (DayOfWeek.value)
) {
    fun isRunningAt(dateTime: java.time.LocalDateTime = java.time.LocalDateTime.now()): Boolean {
        if (!enabled) return false
        val nowMinutes = dateTime.hour * 60 + dateTime.minute
        val todayDow = dateTime.dayOfWeek.value // 1 (Mon) .. 7 (Sun)
        val yesterdayDow = if (todayDow == 1) 7 else todayDow - 1

        val start = startMin.coerceIn(0, 1439)
        val end = endMin.coerceIn(0, 1439)

        return if (start == end) {
            false
        } else if (start < end) {
            // Case 1: Same-Day Schedule (e.g. 12:00 AM [0] to 06:00 AM [360], or 09:00 AM [540] to 05:00 PM [1020])
            (todayDow in daysOfWeek) && (nowMinutes in start until end)
        } else {
            // Case 2: Overnight Schedule crossing midnight (e.g. 11:00 PM [1380] to 06:00 AM [360])
            if (nowMinutes >= start) {
                todayDow in daysOfWeek
            } else if (nowMinutes < end) {
                yesterdayDow in daysOfWeek
            } else {
                false
            }
        }
    }

    fun getRemainingMillisNow(dateTime: java.time.LocalDateTime = java.time.LocalDateTime.now()): Long {
        if (!isRunningAt(dateTime)) return 0L
        val nowMinutes = dateTime.hour * 60 + dateTime.minute
        val start = startMin.coerceIn(0, 1439)
        val end = endMin.coerceIn(0, 1439)

        val targetDateTime = if (start < end) {
            dateTime.toLocalDate().atTime(end / 60, end % 60, 0)
        } else {
            if (nowMinutes >= start) {
                dateTime.toLocalDate().plusDays(1).atTime(end / 60, end % 60, 0)
            } else {
                dateTime.toLocalDate().atTime(end / 60, end % 60, 0)
            }
        }
        val zone = java.time.ZoneId.systemDefault()
        val targetMs = targetDateTime.atZone(zone).toInstant().toEpochMilli()
        val nowMs = dateTime.atZone(zone).toInstant().toEpochMilli()
        return (targetMs - nowMs).coerceAtLeast(0L)
    }

    fun getNextStartMillis(dateTime: java.time.LocalDateTime = java.time.LocalDateTime.now()): Long? {
        if (!enabled) return null
        val nowMinutes = dateTime.hour * 60 + dateTime.minute
        val start = startMin.coerceIn(0, 1439)

        for (dayOffset in 0..7) {
            val checkDate = dateTime.toLocalDate().plusDays(dayOffset.toLong())
            val checkDow = checkDate.dayOfWeek.value
            if (checkDow in daysOfWeek) {
                if (dayOffset == 0 && nowMinutes >= start) {
                    continue
                }
                val startDateTime = checkDate.atTime(start / 60, start % 60, 0)
                val zone = java.time.ZoneId.systemDefault()
                val targetMs = startDateTime.atZone(zone).toInstant().toEpochMilli()
                val nowMs = dateTime.atZone(zone).toInstant().toEpochMilli()
                return (targetMs - nowMs).coerceAtLeast(0L)
            }
        }
        return null
    }

    fun toJson(): JSONObject {
        val appArr = JSONArray()
        allowedPackages.forEach { appArr.put(it) }
        val daysArr = JSONArray()
        daysOfWeek.forEach { daysArr.put(it) }
        return JSONObject()
            .put("id", id).put("label", label).put("startMin", startMin).put("endMin", endMin).put("enabled", enabled)
            .put("allowedApps", appArr)
            .put("allowedPackages", appArr)
            .put("daysOfWeek", daysArr)
    }

    companion object {
        fun fromJson(o: JSONObject): FocusSchedule {
            val appSet = mutableSetOf<String>()
            val arr = o.optJSONArray("allowedPackages") ?: o.optJSONArray("allowedApps")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val a = arr.optString(i, "")
                    if (a.isNotEmpty()) appSet.add(a)
                }
            }
            val daysSet = mutableSetOf<Int>()
            val dArr = o.optJSONArray("daysOfWeek")
            if (dArr != null && dArr.length() > 0) {
                for (i in 0 until dArr.length()) {
                    daysSet.add(dArr.getInt(i))
                }
            } else {
                daysSet.addAll(listOf(1, 2, 3, 4, 5, 6, 7))
            }
            var sMin = o.getInt("startMin")
            val eMin = o.getInt("endMin")
            val lbl = o.optString("label", "Schedule")
            if (sMin == 720 && (lbl.contains("12", ignoreCase = true) || lbl.contains("midnight", ignoreCase = true) || lbl.contains("night", ignoreCase = true))) {
                sMin = 0
            }
            return FocusSchedule(
                id = o.getString("id"),
                label = lbl,
                startMin = sMin,
                endMin = eMin,
                enabled = o.optBoolean("enabled", true),
                allowedApps = appSet,
                allowedPackages = appSet,
                daysOfWeek = daysSet
            )
        }
    }
}

/** One manually adjustable daily prayer / Jumu'ah focus window for seasonal adjustments. */
data class PrayerSlot(
    val id: String,              // "FAJR", "DHUHR", "JUMUAH", "ASR", "MAGHRIB", "ISHA"
    val nameEn: String,
    val nameBn: String,
    val startHour24: Int,        // 0..23
    val startMinute: Int,        // 0..59
    val durationMinutes: Int,    // 10..90
    val isEnabled: Boolean = false,
    val autoDnd: Boolean = true
) {
    fun startTotalMinutes(): Int = startHour24.coerceIn(0, 23) * 60 + startMinute.coerceIn(0, 59)

    fun endTotalMinutes(): Int = (startTotalMinutes() + durationMinutes.coerceIn(10, 90)) % 1440

    fun isApplicableOnDate(date: java.time.LocalDate, hasEnabledJumuah: Boolean): Boolean {
        val isFri = date.dayOfWeek == java.time.DayOfWeek.FRIDAY
        return when (id) {
            "JUMUAH" -> isFri
            "DHUHR" -> !(isFri && hasEnabledJumuah)
            else -> true
        }
    }

    fun getActiveEndEpochMs(
        dateTime: java.time.LocalDateTime = java.time.LocalDateTime.now(),
        hasEnabledJumuah: Boolean = false
    ): Long? {
        if (!isEnabled) return null
        val zone = java.time.ZoneId.systemDefault()
        val nowMs = dateTime.atZone(zone).toInstant().toEpochMilli()
        val durMs = durationMinutes.coerceIn(10, 90) * 60_000L
        val h = startHour24.coerceIn(0, 23)
        val m = startMinute.coerceIn(0, 59)
        for (offset in -1L..0L) {
            val checkDate = dateTime.toLocalDate().plusDays(offset)
            if (!isApplicableOnDate(checkDate, hasEnabledJumuah)) continue
            val startEpoch = checkDate.atTime(h, m, 0).atZone(zone).toInstant().toEpochMilli()
            val endEpoch = startEpoch + durMs
            if (nowMs in startEpoch until endEpoch) {
                return endEpoch
            }
        }
        return null
    }

    fun isRunningAt(
        dateTime: java.time.LocalDateTime = java.time.LocalDateTime.now(),
        hasEnabledJumuah: Boolean = false
    ): Boolean = getActiveEndEpochMs(dateTime, hasEnabledJumuah) != null

    fun getRemainingMillisNow(
        dateTime: java.time.LocalDateTime = java.time.LocalDateTime.now(),
        hasEnabledJumuah: Boolean = false
    ): Long {
        val endEpoch = getActiveEndEpochMs(dateTime, hasEnabledJumuah) ?: return 0L
        val nowMs = dateTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        return (endEpoch - nowMs).coerceAtLeast(0L)
    }

    fun getNextStartMillis(
        dateTime: java.time.LocalDateTime = java.time.LocalDateTime.now(),
        hasEnabledJumuah: Boolean = false
    ): Long? {
        if (!isEnabled) return null
        val zone = java.time.ZoneId.systemDefault()
        val nowMs = dateTime.atZone(zone).toInstant().toEpochMilli()
        val h = startHour24.coerceIn(0, 23)
        val m = startMinute.coerceIn(0, 59)
        for (offset in 0L..7L) {
            val checkDate = dateTime.toLocalDate().plusDays(offset)
            if (!isApplicableOnDate(checkDate, hasEnabledJumuah)) continue
            val startEpoch = checkDate.atTime(h, m, 0).atZone(zone).toInstant().toEpochMilli()
            if (startEpoch > nowMs) {
                return startEpoch - nowMs
            }
        }
        return null
    }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("nameEn", nameEn)
        .put("nameBn", nameBn)
        .put("startHour24", startHour24.coerceIn(0, 23))
        .put("startMinute", startMinute.coerceIn(0, 59))
        .put("durationMinutes", durationMinutes.coerceIn(10, 90))
        .put("isEnabled", isEnabled)
        .put("autoDnd", autoDnd)

    companion object {
        fun defaultSlots(): List<PrayerSlot> = listOf(
            PrayerSlot("FAJR", "Fajr", "ফজর", 5, 0, 25, isEnabled = false, autoDnd = true),
            PrayerSlot("DHUHR", "Dhuhr", "যোহর", 12, 15, 25, isEnabled = false, autoDnd = true),
            PrayerSlot("JUMUAH", "Jumu'ah (Friday)", "জুমু'আহ (শুক্রবার)", 12, 45, 45, isEnabled = false, autoDnd = true),
            PrayerSlot("ASR", "Asr", "আসর", 15, 45, 20, isEnabled = false, autoDnd = true),
            PrayerSlot("MAGHRIB", "Maghrib", "মাগরিব", 17, 30, 20, isEnabled = false, autoDnd = true),
            PrayerSlot("ISHA", "Isha", "এশা", 19, 15, 25, isEnabled = false, autoDnd = true)
        )

        fun fromJson(o: JSONObject): PrayerSlot {
            val id = o.optString("id", "FAJR")
            val def = defaultSlots().find { it.id == id } ?: defaultSlots().first()
            return PrayerSlot(
                id = id,
                nameEn = o.optString("nameEn", def.nameEn),
                nameBn = o.optString("nameBn", def.nameBn),
                startHour24 = o.optInt("startHour24", def.startHour24).coerceIn(0, 23),
                startMinute = o.optInt("startMinute", def.startMinute).coerceIn(0, 59),
                durationMinutes = o.optInt("durationMinutes", def.durationMinutes).coerceIn(10, 90),
                isEnabled = o.optBoolean("isEnabled", false),
                autoDnd = o.optBoolean("autoDnd", true)
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
    const val APP_VERSION = "1.0.6.8"
    const val DISPLAY_VERSION = "1.0.6.8"
    const val BUILD_NUMBER = 100
    const val NOTIFICATION_ID_BANKING_MODE = 1005
    const val DAY_MS = 86_400_000L
    const val DEBUG_UNLOCK_DURATION_MS = 3000L
    const val GUARD_WINDOW_MS = 10 * 60_000L

    fun isTimerDebug(): Boolean = try {
        BuildConfig.DEBUG || BuildConfig.IS_DEBUG_TIMER
    } catch (_: Throwable) {
        false
    }

    fun resolveUnlockDelayDurationMs(c: Context? = null): Long = try {
        if (isTimerDebug()) BuildConfig.DEBUG_UNLOCK_DURATION_MS else DAY_MS
    } catch (_: Throwable) {
        if (isTimerDebug()) DEBUG_UNLOCK_DURATION_MS else DAY_MS
    }

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

    fun autoClampStuckTimers(c: Context) {
        if (!isTimerDebug()) return
        val p = prefs(c)
        val now = System.currentTimeMillis()
        var changed = false
        val editor = p.edit()
        val all = p.all
        val maxAllowed = resolveUnlockDelayDurationMs(c)
        for ((key, value) in all) {
            if (key.startsWith("pending_") || key.contains("unlock") || key.contains("cooldown")) {
                val target = (value as? Number)?.toLong() ?: continue
                if (target <= 0L) continue
                val remaining = target - now
                if (remaining > maxAllowed) {
                    editor.putLong(key, now + maxAllowed)
                    changed = true
                }
            }
        }
        if (changed) {
            editor.apply()
            updateIntegrityHmac(c)
        }
    }

    fun checkAndApplyExpiredDelayTimers(c: Context) {
        val now = System.currentTimeMillis()
        val p = prefs(c)
        var changed = false
        val editor = p.edit()

        val all = p.all
        for ((k, v) in all) {
            if (k == "pending_protection" || k == "pending_setting_focus_apps" || k.startsWith("pending_setting_sched_apps_")) {
                val target = (v as? Number)?.toLong() ?: continue
                if (target != 0L && now >= target) {
                    val baseKey = k.removePrefix("pending_")
                    editor.remove(k)
                    editor.putLong("open_$baseKey", now + GUARD_WINDOW_MS)
                    changed = true
                }
            } else if (k.startsWith("open_")) {
                val openUntil = (v as? Number)?.toLong() ?: continue
                if (openUntil != 0L && now >= openUntil) {
                    editor.remove(k)
                    changed = true
                }
            }
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

    fun powerOffCooldownUntil(c: Context): Long = prefs(c).getLong("pending_power_off_cooldown", 0L)
    fun setPowerOffCooldown(c: Context, target: Long) {
        val current = prefs(c).getLong("pending_power_off_cooldown", 0L)
        if (current > 0L && current > System.currentTimeMillis()) return
        prefs(c).edit().putLong("pending_power_off_cooldown", target).apply()
        updateIntegrityHmac(c)
    }
    fun cancelPowerOffCooldown(c: Context) {
        prefs(c).edit().remove("pending_power_off_cooldown").apply()
        updateIntegrityHmac(c)
    }

    fun pendingFocusUnlockUntil(c: Context): Long = prefs(c).getLong("pending_focus_unlock", 0L)
    fun setPendingFocusUnlock(c: Context, target: Long) {
        val current = prefs(c).getLong("pending_focus_unlock", 0L)
        if (current > 0L && current > System.currentTimeMillis()) return
        prefs(c).edit().putLong("pending_focus_unlock", target).apply()
        updateIntegrityHmac(c)
    }
    fun cancelPendingFocusUnlock(c: Context) {
        prefs(c).edit().remove("pending_focus_unlock").apply()
        updateIntegrityHmac(c)
    }

    fun scheduleUnlockTarget(c: Context, id: String): Long = prefs(c).getLong("pending_sched_unlock_$id", 0L)
    fun setScheduleUnlockTarget(c: Context, id: String, target: Long, action: String = "delete") {
        val current = prefs(c).getLong("pending_sched_unlock_$id", 0L)
        if (current > 0L && current > System.currentTimeMillis()) return
        prefs(c).edit()
            .putLong("pending_sched_unlock_$id", target)
            .putString("pending_sched_action_$id", action)
            .apply()
        updateIntegrityHmac(c)
    }
    fun cancelScheduleUnlock(c: Context, id: String) {
        prefs(c).edit()
            .remove("pending_sched_unlock_$id")
            .remove("pending_sched_action_$id")
            .apply()
        updateIntegrityHmac(c)
    }

    fun settingUnlockTarget(c: Context, key: String): Long =
        prefs(c).getLong("pending_setting_$key", 0L)

    fun setSettingUnlockTarget(c: Context, key: String, target: Long) {
        val current = prefs(c).getLong("pending_setting_$key", 0L)
        if (current > 0L && current > System.currentTimeMillis()) return
        prefs(c).edit().putLong("pending_setting_$key", target).apply()
        updateIntegrityHmac(c)
    }

    fun cancelSettingUnlockTarget(c: Context, key: String) {
        prefs(c).edit().remove("pending_setting_$key").apply()
        updateIntegrityHmac(c)
    }

    fun isSettingGuardOpen(c: Context, key: String): Boolean {
        val now = System.currentTimeMillis()
        val openUntil = prefs(c).getLong("open_setting_$key", 0L)
        return openUntil > now
    }

    fun openSettingGuardWindow(c: Context, key: String) {
        val now = System.currentTimeMillis()
        prefs(c).edit()
            .remove("pending_setting_$key")
            .putLong("open_setting_$key", now + GUARD_WINDOW_MS)
            .apply()
        updateIntegrityHmac(c)
    }

    fun popupLanguage(c: Context): String = prefs(c).getString("popup_language", "en") ?: "en"

    fun setPopupLanguage(c: Context, lang: String) {
        prefs(c).edit().putString("popup_language", if (lang == "bn") "bn" else "en").apply()
    }

    fun delayDays(c: Context): Int = 1
    // Live 3-second in debug builds, strict 24-hour waiting period in production
    fun delayMs(c: Context): Long = resolveUnlockDelayDurationMs(c)

    fun isOnboardingCompleted(c: Context): Boolean =
        prefs(c).getBoolean("onboarding_completed", false)

    fun setOnboardingCompleted(c: Context, completed: Boolean) {
        prefs(c).edit().putBoolean("onboarding_completed", completed).apply()
    }

    // --- Parental Security & App Lock ---
    fun validatePasswordStrength(pass: String): PasswordRuleCheck = PasswordRuleCheck(
        minLength = pass.length >= 8,
        hasUpper = pass.any { it.isUpperCase() },
        hasLower = pass.any { it.isLowerCase() },
        hasDigit = pass.any { it.isDigit() },
        hasSpecial = pass.any { !it.isLetterOrDigit() }
    )

    fun isParentLockEnabled(c: Context): Boolean = prefs(c).getBoolean("parent_lock_enabled", false)

    fun setParentLockEnabled(c: Context, enabled: Boolean) {
        prefs(c).edit().putBoolean("parent_lock_enabled", enabled).apply()
        updateIntegrityHmac(c)
    }

    fun hasParentPassword(c: Context): Boolean = !prefs(c).getString("parent_lock_hash", "").isNullOrEmpty()

    fun setParentPassword(c: Context, rawPass: String) {
        val salt = UUID.randomUUID().toString()
        val hash = sha256("$salt:$rawPass")
        prefs(c).edit()
            .putString("parent_lock_salt", salt)
            .putString("parent_lock_hash", hash)
            .putBoolean("parent_lock_enabled", true)
            .apply()
        updateIntegrityHmac(c)
    }

    fun verifyParentPassword(c: Context, rawPass: String): Boolean {
        val salt = prefs(c).getString("parent_lock_salt", "") ?: ""
        val storedHash = prefs(c).getString("parent_lock_hash", "") ?: ""
        if (salt.isEmpty() || storedHash.isEmpty()) return false
        val hash = sha256("$salt:$rawPass")
        return hash == storedHash
    }

    private fun sha256(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
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

    fun scheduleIsActiveNow(s: FocusSchedule, c: Context? = null): Boolean = s.isRunningAt()
    fun anyScheduleActiveNow(c: Context) = schedules(c).any { it.isRunningAt() }

    // ---- Prayer Time (Salah) Focus Mode: 5 daily prayers + Friday Jumu'ah override ----
    fun prayerSlots(c: Context): List<PrayerSlot> = runCatching {
        val raw = prefs(c).getString("prayer_slots_json", null)
        val defaults = PrayerSlot.defaultSlots()
        if (raw.isNullOrBlank()) return defaults
        val arr = JSONArray(raw)
        val parsed = (0 until arr.length()).map { PrayerSlot.fromJson(arr.getJSONObject(it)) }
            .associateBy { it.id }
        defaults.map { def -> parsed[def.id] ?: def }
    }.getOrDefault(PrayerSlot.defaultSlots())

    fun putPrayerSlots(c: Context, list: List<PrayerSlot>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs(c).edit().putString("prayer_slots_json", arr.toString()).apply()
    }

    fun hasEnabledJumuah(slots: List<PrayerSlot>): Boolean =
        slots.any { it.id == "JUMUAH" && it.isEnabled }

    fun activePrayerSlot(c: Context): PrayerSlot? {
        val slots = prayerSlots(c)
        val jumuahEnabled = hasEnabledJumuah(slots)
        val nowDt = java.time.LocalDateTime.now()
        return slots.firstOrNull { it.isRunningAt(nowDt, jumuahEnabled) }
    }

    fun anyPrayerActiveNow(c: Context): Boolean = activePrayerSlot(c) != null

    fun prayerRemainingMs(c: Context): Long = runCatching {
        val slots = prayerSlots(c)
        val jumuahEnabled = hasEnabledJumuah(slots)
        val nowDt = java.time.LocalDateTime.now()
        slots.filter { it.isRunningAt(nowDt, jumuahEnabled) }
            .maxOfOrNull { it.getRemainingMillisNow(nowDt, jumuahEnabled) } ?: 0L
    }.getOrDefault(0L)

    fun pendingPrayerUnlockUntil(c: Context, slotId: String): Long =
        prefs(c).getLong("pending_prayer_unlock_$slotId", 0L)

    fun setPendingPrayerUnlock(c: Context, slotId: String, target: Long) {
        val current = prefs(c).getLong("pending_prayer_unlock_$slotId", 0L)
        if (current > 0L && current > System.currentTimeMillis()) return
        prefs(c).edit().putLong("pending_prayer_unlock_$slotId", target).apply()
        updateIntegrityHmac(c)
    }

    fun cancelPendingPrayerUnlock(c: Context, slotId: String) {
        prefs(c).edit().remove("pending_prayer_unlock_$slotId").apply()
        updateIntegrityHmac(c)
    }

    private val prayerDndLock = Any()

    fun syncPrayerAutoDnd(c: Context) = synchronized(prayerDndLock) {
        val p = prefs(c)
        val studyOn = p.getBoolean("study_active", false)
        if (studyOn) {
            if (p.getBoolean("prayer_dnd_applied", false)) {
                p.edit()
                    .remove("prayer_dnd_applied")
                    .remove("prayer_prev_interruption_filter")
                    .apply()
            }
            return
        }
        val activeSlot = activePrayerSlot(c)
        val shouldApplyDnd = activeSlot != null && activeSlot.autoDnd
        val alreadyApplied = p.getBoolean("prayer_dnd_applied", false)
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        if (nm == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return

        if (shouldApplyDnd && !alreadyApplied) {
            if (nm.isNotificationPolicyAccessGranted) {
                p.edit()
                    .putInt("prayer_prev_interruption_filter", nm.currentInterruptionFilter)
                    .putBoolean("prayer_dnd_applied", true)
                    .apply()
                runCatching {
                    nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
                }
            }
        } else if (!shouldApplyDnd && alreadyApplied) {
            if (nm.isNotificationPolicyAccessGranted) {
                val prev = p.getInt("prayer_prev_interruption_filter", NotificationManager.INTERRUPTION_FILTER_ALL)
                runCatching { nm.setInterruptionFilter(prev) }
            }
            p.edit()
                .remove("prayer_dnd_applied")
                .remove("prayer_prev_interruption_filter")
                .apply()
        }
    }

    fun focusActive(c: Context): Boolean {
        val manualActive = focusUntil(c) > SecureTime.now(c)
        val schedActive = anyScheduleActiveNow(c)
        val prayerActive = anyPrayerActiveNow(c)
        return manualActive || schedActive || prayerActive
    }

    fun focusRemainingMs(c: Context): Long = runCatching {
        val now = SecureTime.now(c)
        val untilMs = (focusUntil(c) - now).coerceAtLeast(0L)
        val schedMs = schedules(c)
            .filter { it.isRunningAt() }
            .map { it.getRemainingMillisNow() }
            .maxOrNull() ?: 0L
        val prayerMs = prayerRemainingMs(c)
        maxOf(untilMs, schedMs, prayerMs)
    }.getOrDefault(0L)

    /** Returns the union of allowed apps configured for all schedules currently active. */
    fun scheduleAllowedApps(c: Context): Set<String> {
        val active = schedules(c).filter { it.isRunningAt() }
        val set = mutableSetOf<String>()
        for (s in active) {
            set.addAll(s.allowedPackages)
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

    fun isWifiEnabled(c: Context): Boolean = runCatching {
        val wm = c.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        @Suppress("DEPRECATION")
        wm?.isWifiEnabled ?: (Settings.Global.getInt(c.contentResolver, "wifi_on", 0) != 0)
    }.getOrDefault(false)

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
    private fun dayIso() = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    fun incr(c: Context, kind: String) {
        val d = day()
        val dIso = dayIso()
        val k = "stat_${kind}_$d"
        val kTotal = "stat_daily_total_$dIso"
        val p = prefs(c)
        val editor = p.edit()
        editor.putInt(k, p.getInt(k, 0) + 1)
        if (kind != "tamper") {
            editor.putInt(kTotal, p.getInt(kTotal, 0) + 1)
        }
        editor.apply()
    }

    data class DailyBlockPoint(
        val dateIso: String,
        val dayLabel: String,
        val count: Int,
        val isToday: Boolean
    )

    fun get7DayBlockStats(c: Context): List<DailyBlockPoint> {
        val p = prefs(c)
        val result = mutableListOf<DailyBlockPoint>()
        val isoFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val dayNameFmt = SimpleDateFormat("EEE", Locale.US)
        val dayKeyFmt = SimpleDateFormat("yyyyMMdd", Locale.US)

        val dates = mutableListOf<Calendar>()
        for (i in 6 downTo 0) {
            val d = Calendar.getInstance().apply {
                timeInMillis = SecureTime.now(c)
                add(Calendar.DAY_OF_YEAR, -i)
            }
            dates.add(d)
        }

        for (i in dates.indices) {
            val d = dates[i]
            val iso = isoFmt.format(d.time)
            val dKey = dayKeyFmt.format(d.time)
            val isToday = (i == dates.size - 1)
            val label = if (isToday) "Today" else dayNameFmt.format(d.time)

            var total = p.getInt("stat_daily_total_$iso", -1)
            if (total < 0) {
                val nsfw = p.getInt("stat_nsfw_$dKey", 0)
                val apps = p.getInt("stat_app_block_$dKey", 0)
                val reels = p.getInt("stat_reels_$dKey", 0)
                total = nsfw + apps + reels
            }
            result.add(DailyBlockPoint(dateIso = iso, dayLabel = label, count = total.coerceAtLeast(0), isToday = isToday))
        }
        return result
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

    fun blockIncognitoMode(c: Context): Boolean = prefs(c).getBoolean("block_incognito_mode", true)
    fun setBlockIncognitoMode(c: Context, enabled: Boolean) =
        prefs(c).edit().putBoolean("block_incognito_mode", enabled).apply()

    fun enforceSafeSearchGuard(c: Context): Boolean = prefs(c).getBoolean("enforce_safesearch_guard", true)
    fun setEnforceSafeSearchGuard(c: Context, enabled: Boolean) =
        prefs(c).edit().putBoolean("enforce_safesearch_guard", enabled).apply()

    fun suppressNotificationsInFocus(c: Context): Boolean =
        prefs(c).getBoolean("suppress_notifications_in_focus", true)
    fun setSuppressNotificationsInFocus(c: Context, enabled: Boolean) =
        prefs(c).edit().putBoolean("suppress_notifications_in_focus", enabled).apply()

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
