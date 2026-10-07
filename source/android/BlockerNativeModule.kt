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
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.util.Base64
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

/** One recurring daily focus window, e.g. "Bedtime" 22:00 to 06:00. */
data class FocusSchedule(val id: String, val label: String, val startMin: Int, val endMin: Int, val enabled: Boolean) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("label", label).put("startMin", startMin).put("endMin", endMin).put("enabled", enabled)

    companion object {
        fun fromJson(o: JSONObject) = FocusSchedule(
            o.getString("id"), o.optString("label", "Schedule"), o.getInt("startMin"), o.getInt("endMin"),
            o.optBoolean("enabled", true)
        )
    }
}

/** Shared state between the RN module, accessibility service and device admin receiver. */
object BlockerStore {
    const val DAY_MS = 86_400_000L
    const val GUARD_WINDOW_MS = 10 * 60_000L

    fun prefs(c: Context): SharedPreferences =
        c.applicationContext.getSharedPreferences("blocker_store", Context.MODE_PRIVATE)

    fun active(c: Context) = prefs(c).getBoolean("active", false)
    fun shield(c: Context) = prefs(c).getBoolean("shield", true)
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

    private fun minuteOfDay(): Int {
        val cal = Calendar.getInstance()
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    }

    private fun inWindow(start: Int, end: Int, now: Int): Boolean =
        if (start == end) false else if (start < end) now in start until end else now >= start || now < end

    fun scheduleIsActiveNow(s: FocusSchedule): Boolean = s.enabled && inWindow(s.startMin, s.endMin, minuteOfDay())
    fun anyScheduleActiveNow(c: Context) = schedules(c).any { scheduleIsActiveNow(it) }
    fun focusActive(c: Context) = focusUntil(c) > System.currentTimeMillis() || anyScheduleActiveNow(c)

    /** Minutes remaining until a window closes, from a given minute-of-day. */
    private fun minutesUntil(target: Int, from: Int): Int = if (target > from) target - from else 1440 - from + target

    fun focusRemainingMs(c: Context): Long {
        val untilMs = (focusUntil(c) - System.currentTimeMillis()).coerceAtLeast(0L)
        val activeEnds = schedules(c).filter { scheduleIsActiveNow(it) }
            .map { minutesUntil(it.endMin, minuteOfDay()) * 60_000L }
        val schedMs = activeEnds.maxOrNull() ?: 0L
        return maxOf(untilMs, schedMs)
    }

    fun pendingUntil(c: Context, key: String) = prefs(c).getLong("pending_$key", 0L)
    fun guardOpen(c: Context, key: String) = prefs(c).getLong("open_$key", 0L) > System.currentTimeMillis()
    fun consumeGuard(c: Context, key: String) = prefs(c).edit().remove("open_$key").apply()

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
        var usmTotal = 0L
        runCatching {
            val usm = c.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            if (usm != null) {
                val cal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val startOfDay = cal.timeInMillis
                val now = System.currentTimeMillis()
                val list = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startOfDay, now)
                if (!list.isNullOrEmpty()) {
                    usmTotal = list.filter { it.packageName == pkg }.sumOf { it.totalTimeInForeground }
                }
            }
        }
        val stored = getStoredTodayUsageMillis(c, pkg)
        return maxOf(usmTotal, stored)
    }

    fun getAllTodayUsageMillis(c: Context): Map<String, Long> {
        val res = getAllStoredTodayUsageMillis(c).toMutableMap()
        runCatching {
            val usm = c.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            if (usm != null) {
                val cal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val startOfDay = cal.timeInMillis
                val now = System.currentTimeMillis()
                val list = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startOfDay, now)
                if (!list.isNullOrEmpty()) {
                    for (s in list) {
                        if (s.totalTimeInForeground > 0) {
                            val prev = res[s.packageName] ?: 0L
                            res[s.packageName] = maxOf(prev, s.totalTimeInForeground)
                        }
                    }
                }
            }
        }
        return res
    }
}

class BlockerNativeModule(private val rc: ReactApplicationContext) : ReactContextBaseJavaModule(rc) {

    private val io = Executors.newSingleThreadExecutor()
    private val ctx: Context get() = rc.applicationContext
    private val admin get() = ComponentName(ctx, BlockerDeviceAdminReceiver::class.java)

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
                    .sortedBy { it.loadLabel(pm).toString().lowercase() }
                    .forEach { ri ->
                        val ai = ri.activityInfo.applicationInfo
                        val pkg = ai.packageName
                        if (pkg == ctx.packageName || !seen.add(pkg)) return@forEach
                        val system = (ai.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
                        if (system && !includeSystem) return@forEach
                        out.pushMap(Arguments.createMap().apply {
                            putString("packageName", pkg)
                            putString("label", ri.loadLabel(pm).toString())
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
        promise.resolve(true)
    }

    @ReactMethod
    fun setShieldEnabled(enabled: Boolean, promise: Promise) {
        if (!enabled && BlockerStore.shield(ctx) && BlockerStore.active(ctx) && !BlockerStore.guardOpen(ctx, "shield")) {
            promise.reject("LOCKED", "Turning off the shield requires the delay timer.")
            return
        }
        BlockerStore.prefs(ctx).edit().putBoolean("shield", enabled).apply()
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
        promise.resolve(d)
    }

    @ReactMethod
    fun getDelayDays(promise: Promise) = promise.resolve(BlockerStore.delayDays(ctx))

    /** Starts the countdown for `key`; idempotent while a countdown is running. Resolves unlock epoch ms. */
    @ReactMethod
    fun requestSettingChange(key: String, promise: Promise) {
        val existing = BlockerStore.pendingUntil(ctx, key)
        val until = if (existing != 0L) existing else System.currentTimeMillis() + BlockerStore.delayMs(ctx)
        BlockerStore.prefs(ctx).edit().putLong("pending_$key", until).apply()
        promise.resolve(until.toDouble())
    }

    @ReactMethod
    fun getSettingLocks(keys: ReadableArray, promise: Promise) {
        val now = System.currentTimeMillis()
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
        val now = System.currentTimeMillis()
        when {
            until == 0L -> promise.reject("NO_PENDING", "No pending change for $key")
            now < until -> promise.reject("NOT_READY", "Timer still running")
            else -> {
                val open = now + BlockerStore.GUARD_WINDOW_MS
                BlockerStore.prefs(ctx).edit().remove("pending_$key").putLong("open_$key", open).apply()
                promise.resolve(open.toDouble())
            }
        }
    }

    @ReactMethod
    fun cancelSettingChange(key: String, promise: Promise) {
        BlockerStore.prefs(ctx).edit().remove("pending_$key").remove("open_$key").apply()
        promise.resolve(true)
    }

    // ---------- Screen monitor ----------
    // Always on \u2014 there is no user-facing toggle for this; it's core to how Blocker works.

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
        BlockerStore.prefs(ctx).edit().putLong("focus_until", System.currentTimeMillis() + m * 60_000L).apply()
        promise.resolve(true)
    }

    @ReactMethod
    fun extendFocus(minutes: Int, promise: Promise) {
        val base = maxOf(BlockerStore.focusUntil(ctx), System.currentTimeMillis())
        val m = minutes.coerceIn(1, 10_080)
        BlockerStore.prefs(ctx).edit().putLong("focus_until", base + m * 60_000L).apply()
        promise.resolve(true)
    }

    @ReactMethod
    fun shortenFocus(minutes: Int, promise: Promise) {
        if (BlockerStore.focusActive(ctx) && !BlockerStore.guardOpen(ctx, "focus")) {
            promise.reject("LOCKED", "Shortening focus mode requires the delay timer.")
            return
        }
        val target = System.currentTimeMillis() + minutes.coerceAtLeast(1) * 60_000L
        if (target < BlockerStore.focusUntil(ctx)) {
            BlockerStore.prefs(ctx).edit().putLong("focus_until", target).apply()
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
        promise.resolve(true)
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
    fun addSchedule(label: String, startMin: Int, endMin: Int, enabled: Boolean, promise: Promise) {
        val s = FocusSchedule(
            java.util.UUID.randomUUID().toString(), label.trim().ifEmpty { "Schedule" },
            startMin.coerceIn(0, 1439), endMin.coerceIn(0, 1439), enabled
        )
        BlockerStore.putSchedules(ctx, BlockerStore.schedules(ctx) + s)
        promise.resolve(s.id)
    }

    @ReactMethod
    fun updateSchedule(id: String, label: String, startMin: Int, endMin: Int, enabled: Boolean, promise: Promise) {
        val list = BlockerStore.schedules(ctx)
        val old = list.find { it.id == id } ?: run {
            promise.reject("NOT_FOUND", "No such schedule.")
            return
        }
        val new = FocusSchedule(id, label.trim().ifEmpty { "Schedule" }, startMin.coerceIn(0, 1439), endMin.coerceIn(0, 1439), enabled)
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
                val updatesDir = File(rc.cacheDir, "updates")
                if (!updatesDir.exists()) updatesDir.mkdirs()
                val apkFile = File(updatesDir, "Blocker-$version.apk")
                if (apkFile.exists()) apkFile.delete()

                val url = URL(downloadUrl)
                var conn = url.openConnection() as HttpURLConnection
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
                    val nextUrl = URL(newUrl)
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

                val totalBytes = conn.contentLength.toLong()
                var downloadedBytes = 0L

                val input = BufferedInputStream(conn.inputStream)
                val output = FileOutputStream(apkFile)
                val buffer = ByteArray(16384)
                var bytesRead: Int
                var lastProgressEmit = 0L

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead
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
            val apkFile = File(File(rc.cacheDir, "updates"), "Blocker-$version.apk")
            if (!apkFile.exists()) {
                promise.reject("ERR_NOT_FOUND", "Downloaded APK file not found")
                return
            }
            launchInstallIntent(apkFile)
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("ERR_INSTALL", e.message)
        }
    }

    private fun launchInstallIntent(apkFile: File) {
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
}

class BlockerPackage : ReactPackage {
    override fun createNativeModules(rc: ReactApplicationContext): List<NativeModule> =
        listOf(BlockerNativeModule(rc), ProtectionModule(rc))
    override fun createViewManagers(rc: ReactApplicationContext): List<ViewManager<*, *>> = emptyList()
}