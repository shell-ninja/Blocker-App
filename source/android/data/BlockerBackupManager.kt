package com.blocker.data

import android.content.Context
import com.blocker.BlockerStore
import com.blocker.FocusSchedule
import com.blocker.PrayerSlot
import org.json.JSONArray
import org.json.JSONObject

object BlockerBackupManager {

    const val BACKUP_VERSION = 1

    fun exportBackupJson(context: Context): String {
        val root = JSONObject()
        root.put("version", BACKUP_VERSION)
        root.put("timestamp", System.currentTimeMillis())

        // 1. Blocked apps & safe-scan whitelisted apps
        root.put("blockedApps", JSONArray(BlockerStore.set(context, "apps")))
        root.put("whitelistedApps", JSONArray(BlockerStore.set(context, "whitelist")))

        // 2. App daily usage limits
        val limitsObj = JSONObject()
        BlockerStore.appLimits(context).forEach { (pkg, mins) ->
            if (mins > 0) limitsObj.put(pkg, mins)
        }
        root.put("appDailyLimits", limitsObj)

        // 3. Manual focus allowed apps
        root.put("manualFocusAllowedApps", JSONArray(BlockerStore.focusApps(context)))

        // 4. Schedules
        val schedArr = JSONArray()
        BlockerStore.schedules(context).forEach { schedArr.put(it.toJson()) }
        root.put("schedules", schedArr)

        // 5. Prayer slots
        val prayerArr = JSONArray()
        BlockerStore.prayerSlots(context).forEach { prayerArr.put(it.toJson()) }
        root.put("prayerSlots", prayerArr)

        // 6. Blocked domains, keywords, and whitelisted keywords
        root.put("blockedDomains", JSONArray(BlockerStore.set(context, "domains")))
        root.put("blockedKeywords", JSONArray(BlockerStore.set(context, "keywords")))
        root.put("whitelistedKeywords", JSONArray(BlockerStore.set(context, "whitelist_keywords")))

        // 7. Distraction shield toggles
        val shieldObj = JSONObject().apply {
            put("blockYtShorts", BlockerStore.granularToggle(context, "block_yt_shorts"))
            put("blockInstaReels", BlockerStore.granularToggle(context, "block_insta_reels"))
            put("blockInstaSearch", BlockerStore.granularToggle(context, "block_insta_search"))
            put("blockFbReels", BlockerStore.granularToggle(context, "block_fb_reels"))
            put("blockIncognitoMode", BlockerStore.blockIncognitoMode(context))
            put("enforceSafeSearchGuard", BlockerStore.enforceSafeSearchGuard(context))
            put("suppressNotificationsInFocus", BlockerStore.suppressNotificationsInFocus(context))
            put("preventPowerOff", BlockerStore.preventPowerOff(context))
            put("popupLanguage", BlockerStore.popupLanguage(context))
        }
        root.put("distractionShield", shieldObj)

        return root.toString(2)
    }

    fun restoreBackupJson(context: Context, jsonString: String): Result<Unit> = runCatching {
        val root = JSONObject(jsonString)
        if (!root.has("version")) {
            throw IllegalArgumentException("Invalid backup format: missing version header")
        }

        // 1. Blocked apps
        if (root.has("blockedApps")) {
            val arr = root.getJSONArray("blockedApps")
            val set = (0 until arr.length()).map { arr.getString(it) }.toSet()
            BlockerStore.putSet(context, "apps", set)
        }

        // 2. Whitelisted apps
        if (root.has("whitelistedApps")) {
            val arr = root.getJSONArray("whitelistedApps")
            val set = (0 until arr.length()).map { arr.getString(it) }.toSet()
            BlockerStore.putSet(context, "whitelist", set)
        }

        // 3. App daily limits
        if (root.has("appDailyLimits")) {
            val limitsObj = root.getJSONObject("appDailyLimits")
            val map = mutableMapOf<String, Int>()
            val keys = limitsObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val v = limitsObj.optInt(k, 0)
                if (v > 0) map[k] = v
            }
            BlockerStore.putAppLimits(context, map)
        }

        // 4. Manual focus allowed apps
        if (root.has("manualFocusAllowedApps")) {
            val arr = root.getJSONArray("manualFocusAllowedApps")
            val set = (0 until arr.length()).map { arr.getString(it) }.toSet()
            BlockerStore.prefs(context).edit().putStringSet("focus_apps", set).apply()
        }

        // 5. Schedules
        if (root.has("schedules")) {
            val arr = root.getJSONArray("schedules")
            val list = (0 until arr.length()).map { FocusSchedule.fromJson(arr.getJSONObject(it)) }
            BlockerStore.putSchedules(context, list)
        }

        // 6. Prayer slots
        if (root.has("prayerSlots")) {
            val arr = root.getJSONArray("prayerSlots")
            val list = (0 until arr.length()).map { PrayerSlot.fromJson(arr.getJSONObject(it)) }
            BlockerStore.putPrayerSlots(context, list)
        }

        // 7. Domains & Keywords
        if (root.has("blockedDomains")) {
            val arr = root.getJSONArray("blockedDomains")
            val set = (0 until arr.length()).map { arr.getString(it) }.toSet()
            BlockerStore.putSet(context, "domains", set)
        }
        if (root.has("blockedKeywords")) {
            val arr = root.getJSONArray("blockedKeywords")
            val set = (0 until arr.length()).map { arr.getString(it) }.toSet()
            BlockerStore.putSet(context, "keywords", set)
        }
        if (root.has("whitelistedKeywords")) {
            val arr = root.getJSONArray("whitelistedKeywords")
            val set = (0 until arr.length()).map { arr.getString(it) }.toSet()
            BlockerStore.putSet(context, "whitelist_keywords", set)
        }

        // 8. Distraction shield
        if (root.has("distractionShield")) {
            val shield = root.getJSONObject("distractionShield")
            if (shield.has("blockYtShorts")) BlockerStore.setGranularToggle(context, "block_yt_shorts", shield.getBoolean("blockYtShorts"))
            if (shield.has("blockInstaReels")) BlockerStore.setGranularToggle(context, "block_insta_reels", shield.getBoolean("blockInstaReels"))
            if (shield.has("blockInstaSearch")) BlockerStore.setGranularToggle(context, "block_insta_search", shield.getBoolean("blockInstaSearch"))
            if (shield.has("blockFbReels")) BlockerStore.setGranularToggle(context, "block_fb_reels", shield.getBoolean("blockFbReels"))
            if (shield.has("blockIncognitoMode")) BlockerStore.setBlockIncognitoMode(context, shield.getBoolean("blockIncognitoMode"))
            if (shield.has("enforceSafeSearchGuard")) BlockerStore.setEnforceSafeSearchGuard(context, shield.getBoolean("enforceSafeSearchGuard"))
            if (shield.has("suppressNotificationsInFocus")) BlockerStore.setSuppressNotificationsInFocus(context, shield.getBoolean("suppressNotificationsInFocus"))
            if (shield.has("preventPowerOff")) BlockerStore.setPreventPowerOff(context, shield.getBoolean("preventPowerOff"))
            if (shield.has("popupLanguage")) BlockerStore.setPopupLanguage(context, shield.getString("popupLanguage"))
        }

        RuleSnapshotHolder.syncFromStore(context)
    }
}
