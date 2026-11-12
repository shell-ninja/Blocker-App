package com.blocker.data

import android.content.Context
import com.blocker.BlockerStore
import com.blocker.FocusSchedule
import com.blocker.NativeBlockerBridge
import com.blocker.PrayerSlot
import com.blocker.SecureTime
import java.io.File
import java.time.LocalDateTime

data class CompiledRuleSnapshot(
    val blockedApps: Set<String> = emptySet(),
    val blockedDomains: Set<String> = emptySet(),
    val blockedKeywords: Set<String> = emptySet(),
    val whitelist: Set<String> = emptySet(),
    val isProtectionActive: Boolean = false,
    val isShieldActive: Boolean = true,
    val blockIncognitoMode: Boolean = true,
    val enforceSafeSearchGuard: Boolean = true,
    val suppressNotificationsInFocus: Boolean = true,
    val focusUntilMs: Long = 0L,
    val isStudyRunning: Boolean = false,
    val manualFocusAllowedPackages: Set<String> = emptySet(),
    val schedules: List<FocusSchedule> = emptyList(),
    val prayerSlots: List<PrayerSlot> = emptyList(),
    val prayerAllowedPackages: Set<String> = emptySet(),
    val syncedAtMs: Long = 0L
) {
    fun isManualFocusRunningNow(nowMs: Long = System.currentTimeMillis()): Boolean =
        focusUntilMs > nowMs

    fun isStudyModeRunningNow(context: Context): Boolean =
        BlockerStore.studyActive(context)

    fun getActiveScheduleNow(): FocusSchedule? =
        schedules.firstOrNull { it.enabled && it.isRunningAt() }

    fun getActivePrayerSlotNow(): PrayerSlot? {
        val nowDt = LocalDateTime.now()
        val hasJumuah = prayerSlots.any { it.id == "JUMUAH" && it.isEnabled }
        return prayerSlots.firstOrNull { it.isRunningAt(nowDt, hasJumuah) }
    }
}

object RuleSnapshotHolder {
    @Volatile
    var snapshot: CompiledRuleSnapshot = CompiledRuleSnapshot()
        private set

    fun update(newSnapshot: CompiledRuleSnapshot) {
        snapshot = newSnapshot
    }

    fun syncFromStore(context: Context): CompiledRuleSnapshot {
        val allowed = BlockerStore.focusApps(context)
        val updated = CompiledRuleSnapshot(
            blockedApps = BlockerStore.set(context, "apps"),
            blockedDomains = BlockerStore.set(context, "domains"),
            blockedKeywords = BlockerStore.set(context, "keywords"),
            whitelist = BlockerStore.set(context, "whitelist"),
            isProtectionActive = BlockerStore.active(context),
            isShieldActive = BlockerStore.shield(context),
            blockIncognitoMode = BlockerStore.blockIncognitoMode(context),
            enforceSafeSearchGuard = BlockerStore.enforceSafeSearchGuard(context),
            suppressNotificationsInFocus = BlockerStore.suppressNotificationsInFocus(context),
            focusUntilMs = BlockerStore.focusUntil(context),
            isStudyRunning = BlockerStore.prefs(context).getBoolean("study_active", false),
            manualFocusAllowedPackages = allowed,
            schedules = BlockerStore.schedules(context),
            prayerSlots = BlockerStore.prayerSlots(context),
            prayerAllowedPackages = allowed,
            syncedAtMs = SecureTime.now(context)
        )
        snapshot = updated
        return updated
    }

    fun getFreshSnapshot(context: Context): CompiledRuleSnapshot {
        val current = snapshot
        val now = System.currentTimeMillis()
        if (current.syncedAtMs == 0L || (now - current.syncedAtMs) > 2000L) {
            return syncFromStore(context)
        }
        return current
    }
}

object MappedBlocklistEngine {
    fun load(context: Context): Boolean {
        return runCatching {
            val binFile = File(context.filesDir, "blocklist.bin")
            if (!binFile.exists() || binFile.length() == 0L) {
                runCatching {
                    context.assets.open("blocklist.bin").use { input ->
                        binFile.outputStream().use { output -> input.copyTo(output) }
                    }
                }
            }
            if (binFile.exists() && binFile.length() > 0L) {
                NativeBlockerBridge.load(binFile)
                true
            } else {
                false
            }
        }.getOrDefault(false)
    }
}
