package com.blocker.data

import android.app.Activity
import android.app.AppOpsManager
import android.app.NotificationManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import com.blocker.BlockerDeviceAdminReceiver
import com.blocker.BlockerStore
import com.blocker.DeviceOwnerHelper
import com.blocker.FocusSchedule
import com.blocker.MainActivity
import com.blocker.SecureTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ProtectionState(
    val isActive: Boolean = false,
    val isShieldActive: Boolean = true,
    val preventPowerOff: Boolean = false,
    val powerShieldCooldownUntil: Long = 0L,
    val powerShieldCooldownRemainingMs: Long = 0L,
    val delayDays: Int = 1,
    val pendingUntil: Long = 0L,
    val guardOpenUntil: Long = 0L,
    val popupLanguage: String = "en",
    val isBankingMode: Boolean = false,
    val isAutoPauseBanking: Boolean = false,
    val blockFacebookReels: Boolean = true
)

data class PermissionsState(
    val accessibility: Boolean = false,
    val deviceAdmin: Boolean = false,
    val usageAccess: Boolean = false,
    val overlay: Boolean = false,
    val batteryOptimizationsIgnored: Boolean = false
) {
    val allGranted: Boolean
        get() = accessibility && deviceAdmin && usageAccess && overlay
}

data class FocusState(
    val active: Boolean = false,
    val focusUntil: Long = 0L,
    val remainingMs: Long = 0L,
    val schedules: List<FocusSchedule> = emptyList(),
    val allowedApps: Set<String> = emptySet(),
    val pendingUnlockUntil: Long = 0L,
    val pendingUnlockRemainingMs: Long = 0L,
    val pendingScheduleUnlocks: Map<String, Long> = emptyMap(),
    val pendingScheduleActions: Map<String, String> = emptyMap(),
    val pendingAppsUnlockUntil: Long = 0L
)

data class StudyState(
    val active: Boolean = false,
    val until: Long = 0L,
    val remainingMs: Long = 0L,
    val totalRounds: Int = 4,
    val currentRound: Int = 1,
    val isBreak: Boolean = false,
    val focusMinutes: Int = 25,
    val breakMinutes: Int = 5,
    val autoDnd: Boolean = true,
    val autoWifi: Boolean = true,
    val hasDndAccess: Boolean = false
)

data class AppItem(
    val packageName: String,
    val label: String,
    val isBlocked: Boolean,
    val isSystem: Boolean,
    val todayUsageMs: Long,
    val limitMinutes: Int,
    val icon: Drawable? = null
)

data class DistractionShieldState(
    val blockYtShorts: Boolean = false,
    val blockInstaReels: Boolean = false,
    val blockInstaSearch: Boolean = false,
    val blockFbReels: Boolean = true,
    val pendingUnlocks: Map<String, Long> = emptyMap()
)

data class SecurityLockState(
    val isParentLockEnabled: Boolean = false,
    val hasPasswordSet: Boolean = false,
    val isAppUnlocked: Boolean = false
)

data class StatsState(
    val tamperCount: Int = 0,
    val nsfwCount: Int = 0,
    val reelsCount: Int = 0,
    val appBlockCount: Int = 0,
    val dailyHistory: List<BlockerStore.DailyBlockPoint> = emptyList()
)

class BlockerRepository(private val context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _protectionState = MutableStateFlow(ProtectionState())
    val protectionState: StateFlow<ProtectionState> = _protectionState.asStateFlow()

    private val _permissionsState = MutableStateFlow(PermissionsState())
    val permissionsState: StateFlow<PermissionsState> = _permissionsState.asStateFlow()

    private val _focusState = MutableStateFlow(FocusState())
    val focusState: StateFlow<FocusState> = _focusState.asStateFlow()

    private val _studyState = MutableStateFlow(StudyState())
    val studyState: StateFlow<StudyState> = _studyState.asStateFlow()

    private val _distractionShield = MutableStateFlow(DistractionShieldState())
    val distractionShield: StateFlow<DistractionShieldState> = _distractionShield.asStateFlow()

    private val _securityLockState = MutableStateFlow(SecurityLockState())
    val securityLockState: StateFlow<SecurityLockState> = _securityLockState.asStateFlow()

    private val _installedApps = MutableStateFlow<List<AppItem>>(emptyList())
    val installedApps: StateFlow<List<AppItem>> = _installedApps.asStateFlow()

    private val _blockedApps = MutableStateFlow<Set<String>>(emptySet())
    val blockedApps: StateFlow<Set<String>> = _blockedApps.asStateFlow()

    private val _blockedDomains = MutableStateFlow<Set<String>>(emptySet())
    val blockedDomains: StateFlow<Set<String>> = _blockedDomains.asStateFlow()

    private val _blockedKeywords = MutableStateFlow<Set<String>>(emptySet())
    val blockedKeywords: StateFlow<Set<String>> = _blockedKeywords.asStateFlow()

    private val _statsState = MutableStateFlow(StatsState())
    val statsState: StateFlow<StatsState> = _statsState.asStateFlow()

    private val _isRefreshingApps = MutableStateFlow(false)
    val isRefreshingApps: StateFlow<Boolean> = _isRefreshingApps.asStateFlow()

    val updateManager = UpdateManager(appContext)
    val updateState: StateFlow<UpdateState> = updateManager.updateState

    val currentTimeMillisFlow: StateFlow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            kotlinx.coroutines.delay(1000L)
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), System.currentTimeMillis())

    init {
        BlockerStore.autoClampStuckTimers(appContext)
        BlockerStore.seedDefaultApps(appContext)
        refreshAll()
        checkForUpdates(false)

        // Periodic ticker for active countdowns
        scope.launch {
            while (true) {
                delay(1000L)
                tickCountdowns()
            }
        }
    }

    fun checkForUpdates(manual: Boolean = false) {
        updateManager.checkForUpdate(manual)
    }

    fun downloadAndInstallUpdate() {
        updateManager.downloadAndInstall()
    }

    fun clearUpdateCache(): Long {
        return updateManager.clearUpdateCache()
    }

    fun getCacheStats(): Pair<Int, Long> {
        return updateManager.getCacheStats()
    }

    fun canRequestPackageInstalls(): Boolean {
        return updateManager.canRequestPackageInstalls()
    }

    fun openInstallSettings() {
        updateManager.openInstallSettings()
    }

    fun refreshAll() {
        scope.launch {
            refreshProtection()
            refreshPermissions()
            refreshFocusAndStudy()
            refreshDistractionShield()
            refreshSecurityLock()
            refreshBlocklists()
            refreshStats()
            refreshInstalledApps()
        }
    }

    fun refreshProtection() {
        BlockerStore.autoClampStuckTimers(appContext)
        BlockerStore.checkAndApplyExpiredDelayTimers(appContext)
        val p = BlockerStore.prefs(appContext)
        val active = BlockerStore.active(appContext)
        val shield = BlockerStore.shield(appContext)
        val power = BlockerStore.preventPowerOff(appContext)
        val delayDays = BlockerStore.delayDays(appContext)
        val pending = p.getLong("pending_protection", 0L)
        val open = p.getLong("open_protection", 0L)
        val lang = BlockerStore.popupLanguage(appContext)
        val banking = BlockerStore.isBankingMode(appContext)
        val autoPause = BlockerStore.isAutoPauseBanking(appContext)
        val reels = BlockerStore.granularToggle(appContext, "block_facebook_reels")

        val now = SecureTime.now(appContext)
        val pCooldownUntil = BlockerStore.powerOffCooldownUntil(appContext)
        val pCooldownRem = (pCooldownUntil - now).coerceAtLeast(0L)
        if (pCooldownUntil > 0L && now >= pCooldownUntil) {
            BlockerStore.prefs(appContext).edit()
                .putBoolean("prevent_power_off", false)
                .remove("pending_power_off_cooldown")
                .apply()
        }

        _protectionState.value = ProtectionState(
            isActive = active,
            isShieldActive = shield,
            preventPowerOff = power,
            powerShieldCooldownUntil = pCooldownUntil,
            powerShieldCooldownRemainingMs = pCooldownRem,
            delayDays = delayDays,
            pendingUntil = pending,
            guardOpenUntil = open,
            popupLanguage = lang,
            isBankingMode = banking,
            isAutoPauseBanking = autoPause,
            blockFacebookReels = reels
        )
        refreshDistractionShield()
    }

    fun refreshPermissions() {
        val cr = appContext.contentResolver
        val pkg = appContext.packageName

        // Accessibility
        val enabledServices = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        val acc = enabledServices.contains(pkg)

        // Device Admin
        val dpm = appContext.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        val blockerAdmin = ComponentName(appContext, BlockerDeviceAdminReceiver::class.java)
        val adm = dpm?.isAdminActive(blockerAdmin) == true

        // Usage Access
        val appOps = appContext.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
        val mode = appOps?.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), pkg)
        val usage = (mode == AppOpsManager.MODE_ALLOWED)

        // Overlay
        val overlay = Settings.canDrawOverlays(appContext)

        // Battery optimizations ignored
        val pm = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val battery = pm?.isIgnoringBatteryOptimizations(pkg) == true

        _permissionsState.value = PermissionsState(
            accessibility = acc,
            deviceAdmin = adm,
            usageAccess = usage,
            overlay = overlay,
            batteryOptimizationsIgnored = battery
        )
    }

    fun refreshFocusAndStudy() {
        BlockerStore.autoClampStuckTimers(appContext)
        val now = SecureTime.now(appContext)
        val fActive = BlockerStore.focusActive(appContext)
        val fUntil = BlockerStore.focusUntil(appContext)
        val fRem = BlockerStore.focusRemainingMs(appContext)
        val scheds = BlockerStore.schedules(appContext)
        val allowed = BlockerStore.focusApps(appContext)

        val fUnlockUntil = BlockerStore.pendingFocusUnlockUntil(appContext)
        val fUnlockRem = (fUnlockUntil - now).coerceAtLeast(0L)
        if (fUnlockUntil > 0L && now >= fUnlockUntil) {
            BlockerStore.prefs(appContext).edit()
                .putLong("focus_until", 0L)
                .remove("pending_focus_unlock")
                .apply()
        }

        val allPrefs = BlockerStore.prefs(appContext).all
        val schedUnlocks = mutableMapOf<String, Long>()
        val schedActions = mutableMapOf<String, String>()
        for ((k, v) in allPrefs) {
            if (k.startsWith("pending_sched_unlock_")) {
                val schedId = k.removePrefix("pending_sched_unlock_")
                val target = (v as? Number)?.toLong() ?: continue
                if (target > 0L && now >= target) {
                    val action = allPrefs["pending_sched_action_$schedId"] as? String ?: "delete"
                    val currentScheds = BlockerStore.schedules(appContext).toMutableList()
                    if (action == "delete") {
                        currentScheds.removeAll { it.id == schedId }
                    } else if (action == "disable") {
                        val idx = currentScheds.indexOfFirst { it.id == schedId }
                        if (idx >= 0) {
                            currentScheds[idx] = currentScheds[idx].copy(enabled = false)
                        }
                    }
                    BlockerStore.putSchedules(appContext, currentScheds)
                    BlockerStore.prefs(appContext).edit()
                        .remove(k)
                        .remove("pending_sched_action_$schedId")
                        .apply()
                } else if (target > 0L) {
                    schedUnlocks[schedId] = target
                    val action = allPrefs["pending_sched_action_$schedId"] as? String ?: "delete"
                    schedActions[schedId] = action
                }
            }
        }

        val fAppsUnlockUntil = BlockerStore.settingUnlockTarget(appContext, "focus_apps")

        _focusState.value = FocusState(
            active = fActive,
            focusUntil = fUntil,
            remainingMs = fRem,
            schedules = scheds,
            allowedApps = allowed,
            pendingUnlockUntil = fUnlockUntil,
            pendingUnlockRemainingMs = fUnlockRem,
            pendingScheduleUnlocks = schedUnlocks,
            pendingScheduleActions = schedActions,
            pendingAppsUnlockUntil = fAppsUnlockUntil
        )

        val sActive = BlockerStore.studyActive(appContext)
        val sUntil = BlockerStore.studyUntil(appContext)
        val sRem = BlockerStore.studyRemainingMs(appContext)
        val sRounds = BlockerStore.studyTotalRounds(appContext)
        val sCur = BlockerStore.studyCurrentRound(appContext)
        val sBreak = BlockerStore.studyIsBreak(appContext)
        val sFm = BlockerStore.studyFocusMinutes(appContext)
        val sBm = BlockerStore.studyBreakMinutes(appContext)
        val sAutoDnd = BlockerStore.prefs(appContext).getBoolean("study_auto_dnd", true)
        val sAutoWifi = BlockerStore.prefs(appContext).getBoolean("study_auto_wifi", true)
        val dndAccess = hasDndAccess()

        _studyState.value = StudyState(
            active = sActive,
            until = sUntil,
            remainingMs = sRem,
            totalRounds = sRounds,
            currentRound = sCur,
            isBreak = sBreak,
            focusMinutes = sFm,
            breakMinutes = sBm,
            autoDnd = sAutoDnd,
            autoWifi = sAutoWifi,
            hasDndAccess = dndAccess
        )
    }

    fun refreshBlocklists() {
        _blockedApps.value = BlockerStore.set(appContext, "apps")
        _blockedDomains.value = BlockerStore.set(appContext, "domains")
        _blockedKeywords.value = BlockerStore.set(appContext, "keywords")
    }

    fun refreshStats() {
        _statsState.value = StatsState(
            tamperCount = BlockerStore.stat(appContext, "tamper"),
            nsfwCount = BlockerStore.stat(appContext, "nsfw"),
            reelsCount = BlockerStore.stat(appContext, "reels"),
            appBlockCount = BlockerStore.stat(appContext, "app_block"),
            dailyHistory = BlockerStore.get7DayBlockStats(appContext)
        )
    }

    fun isOnboardingCompleted(): Boolean = BlockerStore.isOnboardingCompleted(appContext)

    fun setOnboardingCompleted(completed: Boolean) {
        BlockerStore.setOnboardingCompleted(appContext, completed)
    }

    fun refreshInstalledApps(force: Boolean = false) {
        if (!force && _installedApps.value.isNotEmpty()) return
        scope.launch {
            _isRefreshingApps.value = true
            val items = withContext(Dispatchers.IO) {
                val pm = appContext.packageManager
                val blocked = BlockerStore.set(appContext, "apps")
                val limits = BlockerStore.appLimits(appContext)
                val usageMap = BlockerStore.getAllTodayUsageMillis(appContext)

                val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                val result = mutableListOf<AppItem>()

                for (ai in installed) {
                    val pName = ai.packageName
                    if (pName == appContext.packageName) continue

                    val isSys = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    // Filter system apps that have no launch intent unless already blocked
                    val launchIntent = pm.getLaunchIntentForPackage(pName)
                    if (isSys && launchIntent == null && !blocked.contains(pName)) {
                        continue
                    }

                    val label = runCatching { pm.getApplicationLabel(ai).toString() }.getOrDefault(pName)
                    val isBlk = blocked.contains(pName)
                    val icon = runCatching { pm.getApplicationIcon(ai) }.getOrNull()
                    val usage = usageMap[pName] ?: 0L
                    val limit = limits[pName] ?: 0

                    result.add(
                        AppItem(
                            packageName = pName,
                            label = label,
                            isBlocked = isBlk,
                            isSystem = isSys,
                            todayUsageMs = usage,
                            limitMinutes = limit,
                            icon = icon
                        )
                    )
                }

                result.sortedWith(
                    compareByDescending<AppItem> { it.isBlocked }
                        .thenByDescending { it.todayUsageMs }
                        .thenBy { it.label.lowercase() }
                )
            }

            _installedApps.value = items
            _isRefreshingApps.value = false
        }
    }

    private fun tickCountdowns() {
        val now = System.currentTimeMillis()

        // 1. Study Mode session
        if (_studyState.value.active) {
            val rem = BlockerStore.studyRemainingMs(appContext)
            if (rem <= 0L) {
                BlockerStore.advanceStudyState(appContext)
                refreshFocusAndStudy()
            } else {
                _studyState.value = _studyState.value.copy(remainingMs = rem)
            }
        }

        // 2. Focus Mode session & Pending Focus Unlock
        val fUnlockUntil = BlockerStore.pendingFocusUnlockUntil(appContext)
        if (fUnlockUntil > 0L && now >= fUnlockUntil) {
            endQuickFocus()
        }

        // 3. Schedules Pending Unlocks
        val schedUnlocks = _focusState.value.pendingScheduleUnlocks
        for ((id, target) in schedUnlocks) {
            if (target > 0L && now >= target) {
                val action = _focusState.value.pendingScheduleActions[id] ?: "delete"
                if (action == "delete") {
                    deleteSchedule(id)
                } else {
                    toggleScheduleEnabled(id, false)
                    BlockerStore.cancelScheduleUnlock(appContext, id)
                    refreshFocusAndStudy()
                }
            }
        }

        if (_focusState.value.active || _focusState.value.pendingUnlockUntil > 0L || _focusState.value.pendingScheduleUnlocks.isNotEmpty()) {
            val rem = BlockerStore.focusRemainingMs(appContext)
            _focusState.value = _focusState.value.copy(remainingMs = rem)
            refreshFocusAndStudy()
        }

        // 4. Master Protection Pending Unlock
        val p = BlockerStore.prefs(appContext)
        val pendingProt = p.getLong("pending_protection", 0L)
        if (pendingProt > 0L && now >= pendingProt) {
            confirmProtectionOff()
        }

        // 5. Power Shield Cooldown
        val pCooldownUntil = BlockerStore.powerOffCooldownUntil(appContext)
        if (pCooldownUntil > 0L && now >= pCooldownUntil) {
            BlockerStore.setPreventPowerOff(appContext, false)
            BlockerStore.cancelPowerOffCooldown(appContext)
            refreshProtection()
        }

        // 6. Distraction Shield Pending Unlocks
        for (key in listOf("block_yt_shorts", "block_insta_reels", "block_insta_search", "block_fb_reels")) {
            val target = BlockerStore.settingUnlockTarget(appContext, key)
            if (target > 0L && now >= target) {
                BlockerStore.cancelSettingUnlockTarget(appContext, key)
                BlockerStore.prefs(appContext).edit().putBoolean(key, false).apply()
                refreshDistractionShield()
            }
        }

        // 7. Focus Mode Apps Unlock & Schedule Apps Unlocks
        var appsChanged = false
        val fAppsTarget = BlockerStore.settingUnlockTarget(appContext, "focus_apps")
        if (fAppsTarget > 0L && now >= fAppsTarget) {
            BlockerStore.openSettingGuardWindow(appContext, "focus_apps")
            appsChanged = true
        }
        for (sched in _focusState.value.schedules) {
            val sAppsKey = "sched_apps_${sched.id}"
            val sAppsTarget = BlockerStore.settingUnlockTarget(appContext, sAppsKey)
            if (sAppsTarget > 0L && now >= sAppsTarget) {
                BlockerStore.openSettingGuardWindow(appContext, sAppsKey)
                appsChanged = true
            }
        }
        if (appsChanged) {
            refreshFocusAndStudy()
        }

        val prot = _protectionState.value
        if (prot.pendingUntil > 0L || prot.guardOpenUntil > 0L || prot.powerShieldCooldownUntil > 0L) {
            refreshProtection()
        }
    }

    // --- Action Methods ---

    fun toggleProtection(enable: Boolean) {
        val p = BlockerStore.prefs(appContext)
        val now = System.currentTimeMillis()
        if (enable) {
            DeviceOwnerHelper.updateUninstallBlocked(appContext, true)
            p.edit()
                .putBoolean("active", true)
                .remove("pending_protection")
                .remove("open_protection")
                .apply()
            BlockerStore.updateIntegrityHmac(appContext)
            refreshProtection()
        } else {
            // Check if open window is active
            val openUntil = p.getLong("open_protection", 0L)
            if (openUntil > 0L && now < openUntil) {
                confirmProtectionOff()
            } else {
                val currentPending = p.getLong("pending_protection", 0L)
                if (currentPending == 0L || now >= currentPending) {
                    val delayMs = BlockerStore.delayMs(appContext)
                    p.edit()
                        .putLong("pending_protection", now + delayMs)
                        .remove("open_protection")
                        .apply()
                    BlockerStore.updateIntegrityHmac(appContext)
                    refreshProtection()
                }
            }
        }
    }

    fun cancelProtectionPending() {
        val p = BlockerStore.prefs(appContext)
        p.edit()
            .remove("pending_protection")
            .remove("open_protection")
            .apply()
        BlockerStore.updateIntegrityHmac(appContext)
        refreshProtection()
    }

    fun confirmProtectionOff() {
        val p = BlockerStore.prefs(appContext)
        val now = SecureTime.now(appContext)
        val openUntil = p.getLong("open_protection", 0L)
        val pendingUntil = p.getLong("pending_protection", 0L)
        if ((openUntil > 0L && now < openUntil) || (pendingUntil > 0L && now >= pendingUntil)) {
            DeviceOwnerHelper.updateUninstallBlocked(appContext, false)
            DeviceOwnerHelper.clearDisallowSafeBoot(appContext)
            // Programmatically deactivate Device Admin so user can uninstall freely
            val dpm = appContext.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            if (dpm != null) {
                val admin = ComponentName(appContext, BlockerDeviceAdminReceiver::class.java)
                runCatching { dpm.removeActiveAdmin(admin) }
            }
            p.edit()
                .putBoolean("active", false)
                .remove("pending_protection")
                .remove("open_protection")
                .apply()
            BlockerStore.updateIntegrityHmac(appContext)
            refreshProtection()
            refreshPermissions()
        }
    }

    fun cancelProtectionRequest() {
        val p = BlockerStore.prefs(appContext)
        p.edit()
            .remove("pending_protection")
            .remove("open_protection")
            .apply()
        BlockerStore.updateIntegrityHmac(appContext)
        refreshProtection()
    }

    fun setDelayDays(days: Int = 1) {
        // Enforce strict 24-hour waiting timer
        BlockerStore.prefs(appContext).edit().putInt("delay_days", 1).apply()
        BlockerStore.updateIntegrityHmac(appContext)
        refreshProtection()
    }

    // --- Parental Security & App Lock Actions ---

    fun refreshSecurityLock() {
        val enabled = BlockerStore.isParentLockEnabled(appContext)
        val hasPass = BlockerStore.hasParentPassword(appContext)
        _securityLockState.value = _securityLockState.value.copy(
            isParentLockEnabled = enabled && hasPass,
            hasPasswordSet = hasPass
        )
    }

    fun unlockAppWithPassword(password: String): Boolean {
        if (!_securityLockState.value.isParentLockEnabled) {
            _securityLockState.value = _securityLockState.value.copy(isAppUnlocked = true)
            return true
        }
        val ok = BlockerStore.verifyParentPassword(appContext, password)
        if (ok) {
            _securityLockState.value = _securityLockState.value.copy(isAppUnlocked = true)
        }
        return ok
    }

    fun lockApp() {
        _securityLockState.value = _securityLockState.value.copy(isAppUnlocked = false)
    }

    fun setParentPassword(password: String): Boolean {
        val check = BlockerStore.validatePasswordStrength(password)
        if (!check.isValid) return false
        BlockerStore.setParentPassword(appContext, password)
        refreshSecurityLock()
        _securityLockState.value = _securityLockState.value.copy(isAppUnlocked = true)
        return true
    }

    fun setParentLockEnabled(enabled: Boolean, currentPassword: String): Boolean {
        if (!enabled) {
            if (BlockerStore.hasParentPassword(appContext)) {
                if (!BlockerStore.verifyParentPassword(appContext, currentPassword)) {
                    return false
                }
            }
        }
        BlockerStore.setParentLockEnabled(appContext, enabled)
        refreshSecurityLock()
        return true
    }

    fun changeParentPassword(currentPassword: String, newPassword: String): Boolean {
        if (BlockerStore.hasParentPassword(appContext)) {
            if (!BlockerStore.verifyParentPassword(appContext, currentPassword)) {
                return false
            }
        }
        val check = BlockerStore.validatePasswordStrength(newPassword)
        if (!check.isValid) return false
        BlockerStore.setParentPassword(appContext, newPassword)
        refreshSecurityLock()
        return true
    }

    fun setPreventPowerOff(enable: Boolean) {
        val now = SecureTime.now(appContext)
        if (enable) {
            BlockerStore.setPreventPowerOff(appContext, true)
            BlockerStore.cancelPowerOffCooldown(appContext)
            refreshProtection()
        } else {
            val cooldownUntil = BlockerStore.powerOffCooldownUntil(appContext)
            if (cooldownUntil > 0L && now >= cooldownUntil) {
                BlockerStore.setPreventPowerOff(appContext, false)
                BlockerStore.cancelPowerOffCooldown(appContext)
                refreshProtection()
            } else {
                val cooldownDuration = if (BlockerStore.isTimerDebug()) BlockerStore.resolveUnlockDelayDurationMs(appContext) else 2L * 60L * 60L * 1000L
                BlockerStore.setPowerOffCooldown(appContext, now + cooldownDuration)
                refreshProtection()
            }
        }
    }

    fun cancelPowerOffCooldown() {
        BlockerStore.cancelPowerOffCooldown(appContext)
        refreshProtection()
    }

    fun setPopupLanguage(lang: String) {
        BlockerStore.setPopupLanguage(appContext, lang)
        refreshProtection()
    }

    fun setBankingMode(active: Boolean) {
        BlockerStore.setBankingMode(appContext, active)
        refreshProtection()
    }

    fun setAutoPauseBanking(active: Boolean) {
        BlockerStore.setAutoPauseBanking(appContext, active)
        refreshProtection()
    }

    fun setFacebookReelsBlocked(blocked: Boolean) {
        BlockerStore.setGranularToggle(appContext, "block_facebook_reels", blocked)
        refreshProtection()
    }

    fun toggleAppBlock(pkg: String) {
        val current = _blockedApps.value.toMutableSet()
        if (current.contains(pkg)) {
            if (BlockerStore.active(appContext) || BlockerStore.focusActive(appContext)) {
                return
            }
            current.remove(pkg)
        } else {
            current.add(pkg)
        }
        BlockerStore.putSet(appContext, "apps", current)
        _blockedApps.value = current

        // Update list
        _installedApps.value = _installedApps.value.map {
            if (it.packageName == pkg) it.copy(isBlocked = current.contains(pkg)) else it
        }
    }

    fun setAppLimit(pkg: String, limitMinutes: Int) {
        BlockerStore.setAppLimit(appContext, pkg, limitMinutes)
        _installedApps.value = _installedApps.value.map {
            if (it.packageName == pkg) it.copy(limitMinutes = limitMinutes) else it
        }
    }

    fun addDomain(domain: String) {
        val clean = domain.trim().lowercase().removePrefix("https://").removePrefix("http://").trimEnd('/')
        if (clean.isEmpty()) return
        val current = _blockedDomains.value.toMutableSet()
        current.add(clean)
        BlockerStore.putSet(appContext, "domains", current)
        _blockedDomains.value = current
    }

    fun removeDomain(domain: String) {
        if (BlockerStore.active(appContext) || BlockerStore.focusActive(appContext)) {
            return
        }
        val current = _blockedDomains.value.toMutableSet()
        current.remove(domain)
        BlockerStore.putSet(appContext, "domains", current)
        _blockedDomains.value = current
    }

    fun addKeyword(keyword: String) {
        val clean = keyword.trim().lowercase()
        if (clean.isEmpty()) return
        val current = _blockedKeywords.value.toMutableSet()
        current.add(clean)
        BlockerStore.putSet(appContext, "keywords", current)
        _blockedKeywords.value = current
    }

    fun removeKeyword(keyword: String) {
        if (BlockerStore.active(appContext) || BlockerStore.focusActive(appContext)) {
            return
        }
        val current = _blockedKeywords.value.toMutableSet()
        current.remove(keyword)
        BlockerStore.putSet(appContext, "keywords", current)
        _blockedKeywords.value = current
    }

    fun hasDndAccess(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            return nm?.isNotificationPolicyAccessGranted == true
        }
        return true
    }

    fun requestDndAccess(context: Context) {
        com.blocker.PermissionFlowTracker.isLaunchingPermissionIntent = true
        com.blocker.PermissionFlowTracker.lastDndPermissionRequestMs = SystemClock.elapsedRealtime()
        MainActivity.isLaunchingExternal = true
        val act = context.findActivity() ?: (context as? Activity)
        val launchedDirect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                val detailIntent = Intent("android.settings.NOTIFICATION_POLICY_ACCESS_DETAIL_SETTINGS").apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (detailIntent.resolveActivity(context.packageManager) != null) {
                    if (act != null) act.startActivity(detailIntent) else context.startActivity(detailIntent)
                    true
                } else {
                    false
                }
            }.getOrDefault(false)
        } else {
            false
        }
        if (!launchedDirect) {
            runCatching {
                val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (act != null) act.startActivity(intent) else context.startActivity(intent)
            }
        }
    }

    fun setStudyAutoDnd(enabled: Boolean) {
        BlockerStore.prefs(appContext).edit().putBoolean("study_auto_dnd", enabled).apply()
        refreshFocusAndStudy()
    }

    fun isWifiEnabled(): Boolean = BlockerStore.isWifiEnabled(appContext)

    fun toggleWifi(context: Context) {
        if (isWifiEnabled()) {
            BlockerStore.disableWifi(appContext)
        } else {
            BlockerStore.restoreWifi(appContext)
            runCatching {
                val wm = appContext.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                @Suppress("DEPRECATION")
                wm?.isWifiEnabled = true
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            openWifiSettings(context)
        }
    }

    fun setStudyAutoWifi(enabled: Boolean) {
        BlockerStore.prefs(appContext).edit().putBoolean("study_auto_wifi", enabled).apply()
        refreshFocusAndStudy()
    }

    fun openWifiSettings(context: Context) {
        com.blocker.PermissionFlowTracker.isLaunchingPermissionIntent = true
        MainActivity.isLaunchingExternal = true
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Intent(Settings.Panel.ACTION_WIFI)
        } else {
            Intent(Settings.ACTION_WIFI_SETTINGS)
        }.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        val act = context.findActivity() ?: (context as? Activity)
        if (act != null) act.startActivity(intent) else context.startActivity(intent)
    }

    fun openDataSettings(context: Context) {
        com.blocker.PermissionFlowTracker.isLaunchingPermissionIntent = true
        MainActivity.isLaunchingExternal = true
        val intent = Intent(Settings.ACTION_DATA_ROAMING_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val act = context.findActivity() ?: (context as? Activity)
        try {
            if (act != null) act.startActivity(intent) else context.startActivity(intent)
        } catch (_: Throwable) {
            try {
                val fallback = Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (act != null) act.startActivity(fallback) else context.startActivity(fallback)
            } catch (_: Throwable) {
                openPermissionSettings(context, "settings")
            }
        }
    }

    fun startStudySession(focusMinutes: Int, breakMinutes: Int, rounds: Int) {
        BlockerStore.startStudy(appContext, focusMinutes, breakMinutes, rounds)
        if (BlockerStore.prefs(appContext).getBoolean("study_auto_dnd", true) && hasDndAccess()) {
            BlockerStore.muteNotifications(appContext)
        }
        if (BlockerStore.prefs(appContext).getBoolean("study_auto_wifi", true)) {
            BlockerStore.disableWifi(appContext)
        }
        refreshFocusAndStudy()
    }

    fun endStudySession() {
        BlockerStore.endStudySession(appContext)
        BlockerStore.restoreNotifications(appContext)
        BlockerStore.restoreWifi(appContext)
        refreshFocusAndStudy()
    }

    fun skipStudyBreak() {
        BlockerStore.skipStudyBreak(appContext)
        refreshFocusAndStudy()
    }

    fun startQuickFocus(minutes: Int) {
        val now = SecureTime.now(appContext)
        val until = now + minutes * 60_000L
        BlockerStore.prefs(appContext).edit().putLong("focus_until", until).apply()
        BlockerStore.cancelPendingFocusUnlock(appContext)
        refreshFocusAndStudy()
    }

    fun endQuickFocus() {
        BlockerStore.prefs(appContext).edit().putLong("focus_until", 0L).apply()
        BlockerStore.cancelPendingFocusUnlock(appContext)
        refreshFocusAndStudy()
    }

    fun requestEndQuickFocus() {
        val now = SecureTime.now(appContext)
        val fActive = BlockerStore.focusActive(appContext)
        val protActive = BlockerStore.active(appContext)
        if (fActive || protActive) {
            val pending = BlockerStore.pendingFocusUnlockUntil(appContext)
            if (pending > 0L && now >= pending) {
                endQuickFocus()
            } else if (pending == 0L) {
                val delayMs = BlockerStore.delayMs(appContext)
                BlockerStore.setPendingFocusUnlock(appContext, now + delayMs)
                refreshFocusAndStudy()
            }
        } else {
            endQuickFocus()
        }
    }

    fun cancelFocusUnlock() {
        BlockerStore.cancelPendingFocusUnlock(appContext)
        refreshFocusAndStudy()
    }

    fun refreshDistractionShield() {
        val p = BlockerStore.prefs(appContext)
        val pendingMap = mutableMapOf<String, Long>()
        for (k in listOf("block_yt_shorts", "block_insta_reels", "block_insta_search", "block_fb_reels")) {
            val t = p.getLong("pending_setting_$k", 0L)
            if (t > 0L) pendingMap[k] = t
        }
        _distractionShield.value = DistractionShieldState(
            blockYtShorts = BlockerStore.granularToggle(appContext, "block_yt_shorts"),
            blockInstaReels = BlockerStore.granularToggle(appContext, "block_insta_reels"),
            blockInstaSearch = BlockerStore.granularToggle(appContext, "block_insta_search"),
            blockFbReels = BlockerStore.granularToggle(appContext, "block_fb_reels"),
            pendingUnlocks = pendingMap
        )
    }

    fun toggleDistraction(key: String, enabled: Boolean) {
        val now = System.currentTimeMillis()
        if (enabled) {
            BlockerStore.cancelSettingUnlockTarget(appContext, key)
            BlockerStore.prefs(appContext).edit().putBoolean(key, true).apply()
            refreshDistractionShield()
        } else {
            val protActive = BlockerStore.active(appContext)
            val fActive = BlockerStore.focusActive(appContext)
            if (protActive || fActive) {
                val target = BlockerStore.settingUnlockTarget(appContext, key)
                if (target > 0L && now >= target) {
                    BlockerStore.cancelSettingUnlockTarget(appContext, key)
                    BlockerStore.prefs(appContext).edit().putBoolean(key, false).apply()
                    refreshDistractionShield()
                } else if (target == 0L) {
                    val delayMs = BlockerStore.delayMs(appContext)
                    BlockerStore.setSettingUnlockTarget(appContext, key, now + delayMs)
                    refreshDistractionShield()
                }
            } else {
                BlockerStore.prefs(appContext).edit().putBoolean(key, false).apply()
                refreshDistractionShield()
            }
        }
    }

    fun cancelDistractionUnlock(key: String) {
        BlockerStore.cancelSettingUnlockTarget(appContext, key)
        refreshDistractionShield()
    }

    fun addSchedule(label: String, startMin: Int, endMin: Int, allowedApps: Set<String> = emptySet(), daysOfWeek: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7)) {
        val current = _focusState.value.schedules.toMutableList()
        val id = "sched_" + System.currentTimeMillis()
        current.add(FocusSchedule(id = id, label = label.ifBlank { "Focus Window" }, startMin = startMin.coerceIn(0, 1439), endMin = endMin.coerceIn(0, 1439), enabled = true, allowedApps = allowedApps, allowedPackages = allowedApps, daysOfWeek = daysOfWeek))
        saveSchedules(current)
    }

    fun updateSchedule(id: String, label: String, startMin: Int, endMin: Int, enabled: Boolean, allowedApps: Set<String>, daysOfWeek: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7)) {
        val current = _focusState.value.schedules.map {
            if (it.id == id) it.copy(label = label.ifBlank { it.label }, startMin = startMin.coerceIn(0, 1439), endMin = endMin.coerceIn(0, 1439), enabled = enabled, allowedApps = allowedApps, allowedPackages = allowedApps, daysOfWeek = daysOfWeek) else it
        }
        saveSchedules(current)
    }

    fun toggleScheduleEnabled(id: String, enabled: Boolean) {
        val current = _focusState.value.schedules.map {
            if (it.id == id) it.copy(enabled = enabled) else it
        }
        saveSchedules(current)
    }

    fun requestToggleScheduleEnabled(id: String, enabled: Boolean) {
        if (enabled) {
            toggleScheduleEnabled(id, true)
            BlockerStore.cancelScheduleUnlock(appContext, id)
            refreshFocusAndStudy()
        } else {
            val now = SecureTime.now(appContext)
            val protActive = BlockerStore.active(appContext)
            val fActive = BlockerStore.focusActive(appContext)
            if (protActive || fActive) {
                val pending = BlockerStore.scheduleUnlockTarget(appContext, id)
                if (pending > 0L && now >= pending) {
                    toggleScheduleEnabled(id, false)
                    BlockerStore.cancelScheduleUnlock(appContext, id)
                    refreshFocusAndStudy()
                } else if (pending == 0L) {
                    val delayMs = BlockerStore.delayMs(appContext)
                    BlockerStore.setScheduleUnlockTarget(appContext, id, now + delayMs, "disable")
                    refreshFocusAndStudy()
                }
            } else {
                toggleScheduleEnabled(id, false)
            }
        }
    }

    fun deleteSchedule(id: String) {
        val current = _focusState.value.schedules.filter { it.id != id }
        BlockerStore.cancelScheduleUnlock(appContext, id)
        saveSchedules(current)
    }

    fun requestDeleteSchedule(id: String) {
        val now = SecureTime.now(appContext)
        val protActive = BlockerStore.active(appContext)
        val fActive = BlockerStore.focusActive(appContext)
        if (protActive || fActive) {
            val pending = BlockerStore.scheduleUnlockTarget(appContext, id)
            if (pending > 0L && now >= pending) {
                deleteSchedule(id)
            } else if (pending == 0L) {
                val delayMs = BlockerStore.delayMs(appContext)
                BlockerStore.setScheduleUnlockTarget(appContext, id, now + delayMs, "delete")
                refreshFocusAndStudy()
            }
        } else {
            deleteSchedule(id)
        }
    }

    fun cancelScheduleUnlock(id: String) {
        BlockerStore.cancelScheduleUnlock(appContext, id)
        refreshFocusAndStudy()
    }

    fun isScheduleActiveNow(s: FocusSchedule): Boolean = s.isRunningAt()

    fun isFocusCurrentlyLocked(): Boolean {
        val now = System.currentTimeMillis()
        val isManualFocusRunningNow = _focusState.value.focusUntil > now
        val isStudyLockdownRunningNow = _studyState.value.active
        val isScheduleRunningNow = _focusState.value.schedules.any { it.enabled && it.isRunningAt() }
        return isManualFocusRunningNow || isStudyLockdownRunningNow || isScheduleRunningNow
    }

    fun saveSchedules(list: List<FocusSchedule>) {
        BlockerStore.putSchedules(appContext, list)
        refreshFocusAndStudy()
    }

    fun toggleAllowedFocusApp(pkg: String) {
        val current = BlockerStore.focusApps(appContext).toMutableSet()
        val isCurrentlyAllowed = current.contains(pkg)
        val locked = isFocusCurrentlyLocked()
        val protActive = _protectionState.value.isActive
        val now = System.currentTimeMillis()

        if (!isCurrentlyAllowed) {
            // Attempting to ADD a new app
            if (locked) {
                // Strictly block adding any new apps while Focus Mode is active
                return
            }
            if (protActive && !BlockerStore.isSettingGuardOpen(appContext, "focus_apps")) {
                val target = BlockerStore.settingUnlockTarget(appContext, "focus_apps")
                if (target > 0L && now >= target) {
                    // Timer expired: open guard window and allow adding
                    BlockerStore.openSettingGuardWindow(appContext, "focus_apps")
                } else if (target == 0L) {
                    val delayMs = BlockerStore.resolveUnlockDelayDurationMs(appContext)
                    BlockerStore.setSettingUnlockTarget(appContext, "focus_apps", now + delayMs)
                    refreshFocusAndStudy()
                    return
                } else {
                    return
                }
            }
            current.add(pkg)
        } else {
            // Removing an app
            current.remove(pkg)
        }
        BlockerStore.prefs(appContext).edit().putStringSet("focus_apps", current).apply()
        refreshFocusAndStudy()
    }

    fun cancelFocusAppsUnlock() {
        BlockerStore.cancelSettingUnlockTarget(appContext, "focus_apps")
        refreshFocusAndStudy()
    }

    fun toggleScheduleAllowedApp(scheduleId: String, pkg: String) {
        val sched = _focusState.value.schedules.find { it.id == scheduleId } ?: return
        val currentApps = sched.allowedApps.toMutableSet()
        val isCurrentlyAllowed = currentApps.contains(pkg)
        val isActive = isScheduleActiveNow(sched)
        val protActive = _protectionState.value.isActive
        val now = System.currentTimeMillis()

        if (!isCurrentlyAllowed) {
            // Adding a new allowed app to this schedule
            val lockKey = "sched_apps_$scheduleId"
            if ((isActive || protActive) && !BlockerStore.isSettingGuardOpen(appContext, lockKey)) {
                val target = BlockerStore.settingUnlockTarget(appContext, lockKey)
                if (target > 0L && now >= target) {
                    BlockerStore.openSettingGuardWindow(appContext, lockKey)
                } else if (target == 0L) {
                    val delayMs = BlockerStore.resolveUnlockDelayDurationMs(appContext)
                    BlockerStore.setSettingUnlockTarget(appContext, lockKey, now + delayMs)
                    refreshFocusAndStudy()
                    return
                } else {
                    return
                }
            }
            currentApps.add(pkg)
        } else {
            currentApps.remove(pkg)
        }

        val updated = _focusState.value.schedules.map {
            if (it.id == scheduleId) it.copy(allowedApps = currentApps) else it
        }
        saveSchedules(updated)
    }

    fun cancelScheduleAppsUnlock(scheduleId: String) {
        BlockerStore.cancelSettingUnlockTarget(appContext, "sched_apps_$scheduleId")
        refreshFocusAndStudy()
    }

    fun openPermissionSettings(context: Context, kind: String) {
        val pkg = appContext.packageName
        com.blocker.PermissionFlowTracker.isLaunchingPermissionIntent = true
        MainActivity.isLaunchingExternal = true

        if (kind == "deviceAdmin") {
            val adminComponent = ComponentName(context, BlockerDeviceAdminReceiver::class.java)
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Blocker requires Device Administrator permission to prevent unauthorized uninstallation while protection is active."
                )
            }
            val act = context.findActivity() ?: (context as? Activity)
            runCatching {
                if (act != null) {
                    act.startActivity(intent)
                } else {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
            }.onFailure {
                runCatching {
                    val fallback = Intent(Settings.ACTION_SECURITY_SETTINGS)
                    if (act != null) {
                        act.startActivity(fallback)
                    } else {
                        fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(fallback)
                    }
                }
            }
            return
        }

        val tries: List<Intent> = when (kind) {
            "accessibility" -> listOf(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            "overlay" -> listOf(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$pkg")),
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            )
            "usageAccess" -> listOf(
                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).putExtra("package", pkg),
                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            )
            "battery" -> listOf(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$pkg")),
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
            )
            "dnd" -> listOf(
                Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS),
                Intent(Settings.ACTION_SOUND_SETTINGS),
                Intent(Settings.ACTION_SETTINGS)
            )
            "settings" -> listOf(Intent(Settings.ACTION_SETTINGS))
            else -> emptyList()
        }

        val act = context.findActivity() ?: (context as? Activity)
        for (intent in tries) {
            try {
                if (act != null) {
                    act.startActivity(intent)
                } else {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
                break
            } catch (_: Throwable) {
                try {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    appContext.startActivity(intent)
                    break
                } catch (_: Throwable) {}
            }
        }
    }

    private fun Context.findActivity(): Activity? {
        var current = this
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return null
    }
}
