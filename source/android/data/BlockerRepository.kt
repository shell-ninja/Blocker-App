package com.blocker.data

import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import com.blocker.BlockerDeviceAdminReceiver
import com.blocker.BlockerStore
import com.blocker.FocusSchedule
import com.blocker.MyDeviceAdminReceiver
import com.blocker.SecureTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ProtectionState(
    val isActive: Boolean = false,
    val isShieldActive: Boolean = true,
    val preventPowerOff: Boolean = false,
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
    val allowedApps: Set<String> = emptySet()
)

data class StudyState(
    val active: Boolean = false,
    val until: Long = 0L,
    val remainingMs: Long = 0L,
    val totalRounds: Int = 4,
    val currentRound: Int = 1,
    val isBreak: Boolean = false,
    val focusMinutes: Int = 25,
    val breakMinutes: Int = 5
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

data class StatsState(
    val tamperCount: Int = 0,
    val nsfwCount: Int = 0,
    val reelsCount: Int = 0,
    val appBlockCount: Int = 0
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

    init {
        BlockerStore.seedDefaultApps(appContext)
        refreshAll()

        // Periodic ticker for active countdowns
        scope.launch {
            while (true) {
                delay(1000L)
                tickCountdowns()
            }
        }
    }

    fun refreshAll() {
        scope.launch {
            refreshProtection()
            refreshPermissions()
            refreshFocusAndStudy()
            refreshBlocklists()
            refreshStats()
            refreshInstalledApps()
        }
    }

    fun refreshProtection() {
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

        _protectionState.value = ProtectionState(
            isActive = active,
            isShieldActive = shield,
            preventPowerOff = power,
            delayDays = delayDays,
            pendingUntil = pending,
            guardOpenUntil = open,
            popupLanguage = lang,
            isBankingMode = banking,
            isAutoPauseBanking = autoPause,
            blockFacebookReels = reels
        )
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
        val myAdmin = ComponentName(appContext, MyDeviceAdminReceiver::class.java)
        val adm = dpm?.isAdminActive(blockerAdmin) == true || dpm?.isAdminActive(myAdmin) == true

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
        val fActive = BlockerStore.focusActive(appContext)
        val fUntil = BlockerStore.focusUntil(appContext)
        val fRem = BlockerStore.focusRemainingMs(appContext)
        val scheds = BlockerStore.schedules(appContext)
        val allowed = BlockerStore.focusApps(appContext)

        _focusState.value = FocusState(
            active = fActive,
            focusUntil = fUntil,
            remainingMs = fRem,
            schedules = scheds,
            allowedApps = allowed
        )

        val sActive = BlockerStore.studyActive(appContext)
        val sUntil = BlockerStore.studyUntil(appContext)
        val sRem = BlockerStore.studyRemainingMs(appContext)
        val sRounds = BlockerStore.studyTotalRounds(appContext)
        val sCur = BlockerStore.studyCurrentRound(appContext)
        val sBreak = BlockerStore.studyIsBreak(appContext)
        val sFm = BlockerStore.studyFocusMinutes(appContext)
        val sBm = BlockerStore.studyBreakMinutes(appContext)

        _studyState.value = StudyState(
            active = sActive,
            until = sUntil,
            remainingMs = sRem,
            totalRounds = sRounds,
            currentRound = sCur,
            isBreak = sBreak,
            focusMinutes = sFm,
            breakMinutes = sBm
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
            appBlockCount = BlockerStore.stat(appContext, "app_block")
        )
    }

    fun refreshInstalledApps() {
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
        if (_studyState.value.active) {
            val rem = BlockerStore.studyRemainingMs(appContext)
            if (rem <= 0L) {
                BlockerStore.advanceStudyState(appContext)
                refreshFocusAndStudy()
            } else {
                _studyState.value = _studyState.value.copy(remainingMs = rem)
            }
        }
        if (_focusState.value.active) {
            val rem = BlockerStore.focusRemainingMs(appContext)
            _focusState.value = _focusState.value.copy(remainingMs = rem)
        }
    }

    // --- Action Methods ---

    fun toggleProtection(enable: Boolean) {
        val p = BlockerStore.prefs(appContext)
        val now = SecureTime.now(appContext)
        if (enable) {
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
            if (now < openUntil) {
                p.edit()
                    .putBoolean("active", false)
                    .remove("pending_protection")
                    .remove("open_protection")
                    .apply()
                BlockerStore.updateIntegrityHmac(appContext)
                refreshProtection()
            } else {
                // Request countdown timer
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

    fun cancelProtectionRequest() {
        val p = BlockerStore.prefs(appContext)
        p.edit()
            .remove("pending_protection")
            .remove("open_protection")
            .apply()
        BlockerStore.updateIntegrityHmac(appContext)
        refreshProtection()
    }

    fun setDelayDays(days: Int) {
        val d = days.coerceIn(1, 30)
        BlockerStore.prefs(appContext).edit().putInt("delay_days", d).apply()
        BlockerStore.updateIntegrityHmac(appContext)
        refreshProtection()
    }

    fun setPreventPowerOff(enable: Boolean) {
        BlockerStore.setPreventPowerOff(appContext, enable)
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
        val current = _blockedKeywords.value.toMutableSet()
        current.remove(keyword)
        BlockerStore.putSet(appContext, "keywords", current)
        _blockedKeywords.value = current
    }

    fun startStudySession(focusMinutes: Int, breakMinutes: Int, rounds: Int) {
        BlockerStore.startStudy(appContext, focusMinutes, breakMinutes, rounds)
        refreshFocusAndStudy()
    }

    fun endStudySession() {
        BlockerStore.endStudySession(appContext)
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
        refreshFocusAndStudy()
    }

    fun endQuickFocus() {
        BlockerStore.prefs(appContext).edit().putLong("focus_until", 0L).apply()
        refreshFocusAndStudy()
    }

    fun saveSchedules(list: List<FocusSchedule>) {
        BlockerStore.putSchedules(appContext, list)
        refreshFocusAndStudy()
    }

    fun toggleAllowedFocusApp(pkg: String) {
        val current = BlockerStore.focusApps(appContext).toMutableSet()
        if (current.contains(pkg)) {
            current.remove(pkg)
        } else {
            current.add(pkg)
        }
        BlockerStore.prefs(appContext).edit().putStringSet("focus_apps", current).apply()
        refreshFocusAndStudy()
    }

    fun openPermissionSettings(context: Context, kind: String) {
        val pkg = appContext.packageName
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
            "deviceAdmin" -> {
                val adminComponent = ComponentName(appContext, com.blocker.BlockerDeviceAdminReceiver::class.java)
                listOf(
                    Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                        .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                        .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Prevents Blocker from being uninstalled while protection is active."),
                    Intent(Settings.ACTION_SECURITY_SETTINGS),
                    Intent(Settings.ACTION_SETTINGS)
                )
            }
            else -> emptyList()
        }

        for (intent in tries) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                break
            } catch (_: Throwable) {}
        }
    }
}
