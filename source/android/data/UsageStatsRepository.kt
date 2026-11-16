package com.blocker.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import android.os.Process
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

data class DailyAppUsageItem(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    val isSystem: Boolean,
    val usageMs: Long
)

data class DailyUsageSummary(
    val totalScreenTimeMs: Long = 0L,
    val top5Apps: List<DailyAppUsageItem> = emptyList(),
    val allApps: List<DailyAppUsageItem> = emptyList(),
    val hasUsageAccess: Boolean = true
)

class UsageStatsRepository(private val context: Context) {

    companion object {
        @Volatile private var cachedSummary: DailyUsageSummary? = null
        @Volatile private var lastFetchTimeMs: Long = 0L
    }

    suspend fun getDailyUsage(force: Boolean = false): DailyUsageSummary = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!force && cachedSummary != null && (now - lastFetchTimeMs) < 60_000L) {
            return@withContext cachedSummary!!
        }

        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
        val mode = appOps?.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        val hasAccess = (mode == AppOpsManager.MODE_ALLOWED)
        if (!hasAccess) {
            return@withContext DailyUsageSummary(hasUsageAccess = false)
        }

        val startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endOfDay = System.currentTimeMillis()
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return@withContext DailyUsageSummary(hasUsageAccess = true)

        val totalTimeMap = mutableMapOf<String, Long>()
        val resumeTimeMap = mutableMapOf<String, Long>()

        runCatching {
            val events = usm.queryEvents(startOfDay, endOfDay)
            if (events != null) {
                val event = UsageEvents.Event()
                while (events.hasNextEvent()) {
                    events.getNextEvent(event)
                    val pkg = event.packageName ?: continue
                    val ts = event.timeStamp
                    when (event.eventType) {
                        UsageEvents.Event.ACTIVITY_RESUMED -> {
                            resumeTimeMap[pkg] = ts
                        }
                        UsageEvents.Event.ACTIVITY_PAUSED -> {
                            val resumedAt = resumeTimeMap.remove(pkg)
                            if (resumedAt != null && ts > resumedAt) {
                                val delta = ts - resumedAt
                                totalTimeMap[pkg] = (totalTimeMap[pkg] ?: 0L) + delta
                            }
                        }
                    }
                }
                for ((pkg, resumedAt) in resumeTimeMap) {
                    if (endOfDay > resumedAt) {
                        val delta = endOfDay - resumedAt
                        totalTimeMap[pkg] = (totalTimeMap[pkg] ?: 0L) + delta
                    }
                }
            }
        }

        if (totalTimeMap.isEmpty()) {
            runCatching {
                val stats = usm.queryAndAggregateUsageStats(startOfDay, endOfDay)
                for ((pkg, stat) in stats) {
                    if (stat.totalTimeInForeground > 0L) {
                        totalTimeMap[pkg] = stat.totalTimeInForeground
                    }
                }
            }
        }

        val pm = context.packageManager
        val resolvedList = mutableListOf<DailyAppUsageItem>()
        for ((pkg, usageMs) in totalTimeMap) {
            if (usageMs <= 0L) continue
            runCatching {
                val appInfo = pm.getApplicationInfo(pkg, 0)
                val label = pm.getApplicationLabel(appInfo).toString()
                val icon = pm.getApplicationIcon(appInfo)
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                resolvedList.add(
                    DailyAppUsageItem(
                        packageName = pkg,
                        label = label.ifBlank { pkg },
                        icon = icon,
                        isSystem = isSystem,
                        usageMs = usageMs
                    )
                )
            }
        }

        val sorted = resolvedList.sortedByDescending { it.usageMs }
        val filteredForList = if (sorted.any { it.usageMs >= 60_000L }) {
            sorted.filter { it.usageMs >= 60_000L }
        } else {
            sorted
        }
        val totalScreenTimeMs = sorted.sumOf { it.usageMs }
        val top5 = filteredForList.take(5)

        val result = DailyUsageSummary(
            totalScreenTimeMs = totalScreenTimeMs,
            top5Apps = top5,
            allApps = filteredForList,
            hasUsageAccess = true
        )
        cachedSummary = result
        lastFetchTimeMs = System.currentTimeMillis()
        result
    }
}
