package com.blocker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.view.inputmethod.InputMethodManager
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Single source of truth for all system exemptions across Focus Mode, Study Mode,
 * and Watchdog enforcers.
 */
object SystemExemptions {

    @Volatile
    var lastFocusHomeKickElapsedMs: Long = 0L

    @Volatile
    private var cachedHomePackages: Set<String> = emptySet()

    @Volatile
    private var lastHomeQueryMs: Long = 0L

    @Volatile
    private var cachedImePackages: Set<String> = emptySet()

    @Volatile
    private var lastImeQueryMs: Long = 0L

    @Volatile
    private var receiverRegistered = false

    private val perPackageProbeCache = ConcurrentHashMap<String, Boolean>()

    private val DIALER_PACKAGES = setOf(
        "com.android.incallui",
        "com.samsung.android.incallui",
        "com.android.server.telecom",
        "com.android.dialer",
        "com.google.android.dialer",
        "com.samsung.android.dialer",
        "com.android.phone",
        "com.android.emergency"
    )

    // All Major OEM & Custom Launchers (lowercase)
    private val KNOWN_LAUNCHERS = setOf(
        "com.miui.home",
        "com.mi.android.globallauncher",
        "com.sec.android.app.launcher",
        "com.sec.android.app.easylauncher",
        "com.oppo.launcher",
        "com.coloros.launcher",
        "com.oplus.launcher",
        "com.android.launcher",
        "com.android.launcher3",
        "com.oneplus.launcher",
        "net.oneplus.launcher",
        "com.bbk.launcher2",
        "com.bbk.launcher",
        "com.vivo.launcher",
        "com.vivo.upslide",
        "com.huawei.android.launcher",
        "com.hihonor.android.launcher",
        "com.motorola.launcher3",
        "com.sonymobile.home",
        "com.asus.launcher",
        "com.google.android.apps.nexuslauncher",
        "com.teslacoilsw.launcher",
        "app.lawnchair",
        "ch.deletescape.lawnchair.plah",
        "ch.deletescape.lawnchair",
        "com.microsoft.launcher",
        "bitpit.launcher",
        "org.fossify.launcher",
        "com.nothing.launcher"
    )

    fun init(context: Context) {
        refreshHomePackages(context)
        refreshImePackages(context)
        if (!receiverRegistered) {
            receiverRegistered = true
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addDataScheme("package")
            }
            runCatching {
                context.applicationContext.registerReceiver(object : BroadcastReceiver() {
                    override fun onReceive(ctx: Context?, intent: Intent?) {
                        if (ctx != null) {
                            perPackageProbeCache.clear()
                            refreshHomePackages(ctx)
                            refreshImePackages(ctx)
                        }
                    }
                }, filter)
            }
        }
    }

    private fun queryHomePackages(pm: PackageManager): Set<String> {
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val list = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(
                    homeIntent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(homeIntent, PackageManager.MATCH_ALL)
            }
        }.getOrDefault(emptyList())

        val defaultResolved = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.resolveActivity(
                    homeIntent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                pm.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
            }
        }.getOrNull()

        return buildSet {
            list.mapNotNull { it.activityInfo?.packageName }.forEach { add(it.trim().lowercase(Locale.ROOT)) }
            defaultResolved?.activityInfo?.packageName?.let { add(it.trim().lowercase(Locale.ROOT)) }
        }
    }

    fun refreshHomePackages(context: Context): Set<String> {
        val pkgs = queryHomePackages(context.packageManager)
        if (pkgs.isNotEmpty()) {
            cachedHomePackages = pkgs
            lastHomeQueryMs = SystemClock.elapsedRealtime()
        }
        return cachedHomePackages
    }

    fun getHomePackages(context: Context): Set<String> {
        val now = SystemClock.elapsedRealtime()
        val cached = cachedHomePackages
        if (cached.isNotEmpty() && now - lastHomeQueryMs < 15_000L) {
            return cached
        }
        val refreshed = refreshHomePackages(context)
        return if (refreshed.isNotEmpty()) refreshed else cached
    }

    private fun probePackageIsCategoryHome(context: Context, rawPkg: String): Boolean {
        val cached = perPackageProbeCache[rawPkg]
        if (cached != null) return cached

        val isHome = runCatching {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(rawPkg)
            val list = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(
                    intent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            }
            !list.isNullOrEmpty()
        }.getOrDefault(false)

        if (isHome) {
            perPackageProbeCache[rawPkg] = true
        }
        return isHome
    }

    fun refreshImePackages(context: Context): Set<String> {
        val imes = runCatching {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.enabledInputMethodList?.mapNotNull { it.packageName?.trim()?.lowercase(Locale.ROOT) }?.toSet()
        }.getOrNull() ?: emptySet()
        if (imes.isNotEmpty()) {
            cachedImePackages = imes
            lastImeQueryMs = SystemClock.elapsedRealtime()
        }
        return cachedImePackages
    }

    fun getImePackages(context: Context): Set<String> {
        val now = SystemClock.elapsedRealtime()
        val cached = cachedImePackages
        if (cached.isNotEmpty() && now - lastImeQueryMs < 15_000L) {
            return cached
        }
        val refreshed = refreshImePackages(context)
        return if (refreshed.isNotEmpty()) refreshed else cached
    }

    fun isLauncherPackage(context: Context, rawPkg: String?): Boolean {
        if (rawPkg.isNullOrBlank()) return true
        val pkg = rawPkg.trim().lowercase(Locale.ROOT)

        // 1. Dynamic CATEGORY_HOME query (lowercase)
        if (pkg in getHomePackages(context)) return true

        // 2. Per-package CATEGORY_HOME runtime probe
        if (probePackageIsCategoryHome(context, rawPkg)) return true

        // 3. Transsion / Infinix / Tecno / Itel (XOS & HiOS) Launcher & Home Companions (all lowercase):
        if (pkg.startsWith("com.transsion.xoslauncher") ||
            pkg.startsWith("com.transsion.hilauncher") ||
            pkg.startsWith("com.transsion.itel.launcher") ||
            pkg.startsWith("com.transsion.launcher") ||
            pkg.startsWith("com.transsion.magiclauncher") ||
            pkg.startsWith("com.transsion.smartpanel") ||
            pkg.startsWith("com.transsion.os.smartpanel") ||
            pkg.startsWith("com.transsion.desktop") ||
            pkg.startsWith("com.transsion.zerohome") ||
            pkg.startsWith("com.transsion.minusone") ||
            pkg.startsWith("com.transsion.hiassistant") ||
            pkg.startsWith("com.transsion.folax") ||
            pkg.startsWith("com.transsion.aivoiceassistant") ||
            pkg.startsWith("com.transsion.systemui")) {
            return true
        }

        // 4. All Major OEM & Custom Launchers (lowercase):
        if (pkg in KNOWN_LAUNCHERS) return true

        // 5. Safe Pattern Fallback:
        if (pkg.contains("xoslauncher") ||
            pkg.contains("hilauncher") ||
            pkg.contains("trebuchet") ||
            pkg.contains("nexuslauncher") ||
            pkg.contains("quickstep") ||
            pkg.contains("lawnchair") ||
            pkg.endsWith(".launcher") ||
            pkg.endsWith(".launcher2") ||
            pkg.endsWith(".launcher3") ||
            pkg.endsWith(".home")) {
            return true
        }

        return false
    }

    /**
     * Primary single-source-of-truth helper:
     * Returns true if a package must NEVER be blocked by Focus Mode, Study Mode, Prayer Mode,
     * or any app-block enforcer.
     */
    fun isNeverBlockPackage(context: Context, rawPkg: String?): Boolean {
        // 1. rawPkg.isNullOrBlank()
        if (rawPkg.isNullOrBlank()) return true

        val pkg = rawPkg.trim().lowercase(Locale.ROOT)

        // 2. pkg == context.packageName.lowercase(Locale.ROOT) (Blocker itself)
        if (pkg == context.packageName.lowercase(Locale.ROOT)) return true

        // 3. pkg == "android" || pkg == "com.android.systemui"
        if (pkg == "android" || pkg == "com.android.systemui") return true

        // 4. Launcher checks (CATEGORY_HOME probe, OEM lists, safe patterns)
        if (isLauncherPackage(context, rawPkg)) return true

        // 5. Enabled Keyboards / IMEs
        if (pkg in getImePackages(context)) return true

        // 6. Active Phone / Dialer UI
        if (pkg in DIALER_PACKAGES) return true

        // 7. System Permission Controller ONLY when PermissionFlowTracker is launching permission intent
        if (PermissionFlowTracker.isLaunchingPermissionIntent) {
            if (pkg == "com.android.permissioncontroller" ||
                pkg == "com.google.android.permissioncontroller" ||
                pkg == "com.android.settings") {
                return true
            }
        }

        return false
    }

    fun isExemptFromFocusBlocking(context: Context, rawPkg: String?): Boolean {
        return isNeverBlockPackage(context, rawPkg)
    }

    fun getLauncherPackages(context: Context): Set<String> {
        return getHomePackages(context) + KNOWN_LAUNCHERS
    }
}
