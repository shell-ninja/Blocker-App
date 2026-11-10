package com.blocker.ui.screens

import android.content.Intent
import android.net.Uri
import com.blocker.MainActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.blocker.BlockerStore
import com.blocker.R
import com.blocker.data.BlockerRepository
import com.blocker.ui.components.*
import com.blocker.ui.theme.*

private fun formatCountdown(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return String.format("%02d:%02d:%02d", h, m, s)
}

@Composable
fun SettingsScreen(
    repo: BlockerRepository,
    modifier: Modifier = Modifier
) {
    val protection by repo.protectionState.collectAsState()
    val securityLock by repo.securityLockState.collectAsState()
    val permissions by repo.permissionsState.collectAsState()
    val updateState by repo.updateState.collectAsState()
    val nowMillis by repo.currentTimeMillisFlow.collectAsState()
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var showSetPasswordDialog by remember { mutableStateOf(false) }
    var showDisablePasswordDialog by remember { mutableStateOf(false) }
    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var showUpdateModal by remember { mutableStateOf(false) }
    var cacheStats by remember { mutableStateOf(repo.getCacheStats()) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        SectionHeader(
            title = "Settings & Security",
            icon = CyberIcons.Settings,
            subtitle = "Parental safety, 24-hour covenant lock, and hardware protection"
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 1. Strict 24-Hour Delay Period Card
        NordicMicroGridCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f).padding(end = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(NordicColors.CardSurfaceHero)
                            .border(1.dp, NordicColors.HairlineBorder, RoundedCornerShape(11.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = CyberIcons.Clock,
                            contentDescription = null,
                            tint = NordicColors.AccentPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Protection Delay Period",
                            color = NordicColors.TextPrimary,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "24-Hour Fixed Covenant Timer",
                            color = NordicColors.TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                StatusPillBadge(label = "24h Locked", tone = BadgeTone.ACCENT, small = true)
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Disarming protection or removing restrictions enforces a strict 24-hour waiting countdown. Instant bypass or shorter timers are permanently removed.",
                color = NordicColors.TextSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 2. Parental Security & App Lock
        SectionHeader(
            title = "Parental & Child Safety Lock",
            icon = CyberIcons.Lock,
            subtitle = "Restricts children from opening the app, modifying filters, or changing settings"
        )

        Spacer(modifier = Modifier.height(10.dp))

        NordicMicroGridCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f).padding(end = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(NordicColors.CardSurfaceHero)
                            .border(1.dp, NordicColors.HairlineBorder, RoundedCornerShape(11.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = CyberIcons.Lock,
                            contentDescription = null,
                            tint = NordicColors.AccentPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Master Password Lock",
                            color = NordicColors.TextPrimary,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (securityLock.isParentLockEnabled) "App locked with strong password" else "Requires parent password to open app",
                            color = if (securityLock.isParentLockEnabled) NordicColors.AccentPrimary else NordicColors.TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
                NordicSwitch(
                    checked = securityLock.isParentLockEnabled,
                    onCheckedChange = { enable ->
                        if (enable) {
                            showSetPasswordDialog = true
                        } else {
                            showDisablePasswordDialog = true
                        }
                    }
                )
            }

            if (securityLock.isParentLockEnabled) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Btn(
                        label = "Change Password",
                        icon = CyberIcons.Lock,
                        onClick = { showChangePasswordDialog = true },
                        kind = BtnKind.SECONDARY,
                        modifier = Modifier.weight(1f)
                    )
                    Btn(
                        label = "Lock App Now",
                        icon = CyberIcons.Lock,
                        onClick = { repo.lockApp() },
                        kind = BtnKind.GHOST,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Hardware & System Defense
        SectionHeader(
            title = "Hardware & Admin Protection",
            icon = CyberIcons.ShieldCheck,
            subtitle = "Low-level system enforcement preventing circumvention and background termination"
        )

        Spacer(modifier = Modifier.height(10.dp))

        // One-tap Unrestricted Battery Button
        NordicMicroGridCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(NordicColors.CardSurfaceHero)
                            .border(1.dp, NordicColors.HairlineBorder, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = CyberIcons.Power,
                            contentDescription = null,
                            tint = NordicColors.AccentPrimary,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Unrestricted Battery",
                            color = NordicColors.TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (permissions.batteryOptimizationsIgnored) "Exemption active — system cannot kill service" else "OEM battery optimization may stop protection",
                            color = NordicColors.TextSecondary,
                            fontSize = 11.5.sp
                        )
                    }
                }
                StatusPillBadge(
                    label = if (permissions.batteryOptimizationsIgnored) "Exempted" else "Optimized",
                    tone = if (permissions.batteryOptimizationsIgnored) BadgeTone.OK else BadgeTone.WARN,
                    small = true
                )
            }

            if (!permissions.batteryOptimizationsIgnored) {
                Spacer(modifier = Modifier.height(12.dp))
                Btn(
                    label = "Grant Unrestricted Battery",
                    icon = CyberIcons.Check,
                    onClick = { repo.openPermissionSettings(context, "battery") },
                    kind = BtnKind.PRIMARY,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Device Administrator Card
        NordicMicroGridCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(NordicColors.CardSurfaceHero)
                            .border(1.dp, NordicColors.HairlineBorder, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = CyberIcons.ShieldCheck,
                            contentDescription = null,
                            tint = NordicColors.AccentPrimary,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Device Administrator",
                            color = NordicColors.TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (permissions.deviceAdmin) "Admin active — uninstall attempts are guarded" else "Activate to prevent tampering and instant uninstall",
                            color = NordicColors.TextSecondary,
                            fontSize = 11.5.sp
                        )
                    }
                }
                StatusPillBadge(
                    label = if (permissions.deviceAdmin) "Active" else "Inactive",
                    tone = if (permissions.deviceAdmin) BadgeTone.OK else BadgeTone.WARN,
                    small = true
                )
            }

            if (!permissions.deviceAdmin) {
                Spacer(modifier = Modifier.height(12.dp))
                Btn(
                    label = "Set Device Admin",
                    icon = CyberIcons.ShieldCheck,
                    onClick = { repo.openPermissionSettings(context, "deviceAdmin") },
                    kind = BtnKind.PRIMARY,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Prevent Power Off
        val hasPowerCooldown = protection.powerShieldCooldownUntil > 0L
        val powerCooldownRem = (protection.powerShieldCooldownUntil - nowMillis).coerceAtLeast(0L)
        val isCooldownFinished = hasPowerCooldown && powerCooldownRem <= 0L

        NordicMicroGridCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(NordicColors.CardSurfaceHero)
                                .border(1.dp, NordicColors.HairlineBorder, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = CyberIcons.Power,
                                contentDescription = null,
                                tint = if (hasPowerCooldown) NordicColors.AlertMuted else NordicColors.AccentPrimary,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Prevent Power Off",
                                color = NordicColors.TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (hasPowerCooldown && !isCooldownFinished) {
                                    "Cooldown active: ${formatCountdown(powerCooldownRem)} remaining before Power Shield can be disabled"
                                } else if (isCooldownFinished) {
                                    "Cooldown complete — Tap toggle to turn off"
                                } else {
                                    "Blocks device power down attempts while protected"
                                },
                                color = if (hasPowerCooldown) NordicColors.AlertMuted else NordicColors.TextSecondary,
                                fontSize = 11.5.sp
                            )
                        }
                    }
                    NordicSwitch(
                        checked = protection.preventPowerOff,
                        onCheckedChange = { repo.setPreventPowerOff(it) }
                    )
                }

                if (hasPowerCooldown && !isCooldownFinished) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Btn(
                            label = "Cancel Cooldown",
                            icon = CyberIcons.Close,
                            onClick = { repo.cancelPowerOffCooldown() },
                            kind = BtnKind.GHOST
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 4. Popup Language
        SectionHeader(
            title = "Warning Language",
            icon = CyberIcons.Web,
            subtitle = "Language used in religious reminders and covenant alerts"
        )

        Spacer(modifier = Modifier.height(10.dp))

        NordicMicroGridCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val isBn = protection.popupLanguage == "bn"
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (!isBn) NordicColors.AccentPrimary else NordicColors.CardSurfaceHero)
                        .border(1.dp, if (!isBn) NordicColors.AccentPrimary else NordicColors.HairlineBorder, RoundedCornerShape(12.dp))
                        .clickable { repo.setPopupLanguage("en") }
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "English",
                        color = if (!isBn) NordicColors.OnAccentPrimary else NordicColors.TextSecondary,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isBn) NordicColors.AccentPrimary else NordicColors.CardSurfaceHero)
                        .border(1.dp, if (isBn) NordicColors.AccentPrimary else NordicColors.HairlineBorder, RoundedCornerShape(12.dp))
                        .clickable { repo.setPopupLanguage("bn") }
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "বাংলা (Bengali)",
                        color = if (isBn) NordicColors.OnAccentPrimary else NordicColors.TextSecondary,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 5. App Updates Card
        NordicMicroGridCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(NordicColors.CardSurfaceHero)
                            .border(1.dp, NordicColors.HairlineBorder, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = CyberIcons.Download,
                            contentDescription = null,
                            tint = NordicColors.AccentPrimary,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "App Updates",
                            color = NordicColors.TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Direct GitHub release delivery & in-app updater",
                            color = NordicColors.TextSecondary,
                            fontSize = 11.5.sp
                        )
                    }
                }

                if (updateState.hasUpdate) {
                    StatusPillBadge(
                        label = "New update",
                        tone = BadgeTone.WARN,
                        breathing = true
                    )
                } else if (updateState.release != null) {
                    StatusPillBadge(
                        label = "Up to date",
                        tone = BadgeTone.OK,
                        icon = CyberIcons.Check
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Installed Version
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Installed Version", color = NordicColors.TextSecondary, fontSize = 12.5.sp)
                Text(
                    text = "v${BlockerStore.DISPLAY_VERSION} (build ${BlockerStore.BUILD_NUMBER})",
                    color = NordicColors.TextPrimary,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Latest GitHub Release
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Latest GitHub Release", color = NordicColors.TextSecondary, fontSize = 12.5.sp)
                val relText = when {
                    updateState.checking -> "Checking..."
                    updateState.release != null -> updateState.release!!.tagName
                    else -> "Not checked yet"
                }
                Text(
                    text = relText,
                    color = if (updateState.hasUpdate) NordicColors.AlertMuted else NordicColors.AccentPrimary,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Download Cache
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Download Cache", color = NordicColors.TextSecondary, fontSize = 12.5.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val cacheMb = if (cacheStats.second > 0L) {
                        String.format("%.1f MB (%d file%s)", cacheStats.second / (1024f * 1024f), cacheStats.first, if (cacheStats.first > 1) "s" else "")
                    } else {
                        "Clean (0 MB)"
                    }
                    Text(text = cacheMb, color = NordicColors.TextSecondary, fontSize = 12.sp)
                    if (cacheStats.first > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(NordicColors.CardSurfaceHero)
                                .border(1.dp, NordicColors.AlertMuted.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                .clickable {
                                    repo.clearUpdateCache()
                                    cacheStats = repo.getCacheStats()
                                }
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(text = "Clear", color = NordicColors.AlertMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (updateState.error != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = updateState.error!!,
                    color = NordicColors.AlertMuted,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (updateState.hasUpdate) {
                    Btn(
                        label = "View & Install",
                        icon = CyberIcons.Download,
                        onClick = { showUpdateModal = true },
                        kind = BtnKind.PRIMARY,
                        modifier = Modifier.weight(1.3f)
                    )
                    Btn(
                        label = if (updateState.checking) "Checking..." else "Check Again",
                        icon = CyberIcons.Refresh,
                        enabled = !updateState.checking,
                        onClick = { repo.checkForUpdates(true) },
                        kind = BtnKind.GHOST,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Btn(
                        label = if (updateState.checking) "Checking..." else "Check Updates",
                        icon = if (updateState.checking) CyberIcons.Refresh else CyberIcons.Clock,
                        enabled = !updateState.checking,
                        onClick = { repo.checkForUpdates(true) },
                        kind = BtnKind.PRIMARY,
                        modifier = Modifier.weight(1f)
                    )
                    if (updateState.release != null) {
                        Btn(
                            label = "Release Notes",
                            icon = CyberIcons.Book,
                            onClick = { showUpdateModal = true },
                            kind = BtnKind.GHOST,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Source Code Button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(NordicColors.CardSurface)
                .border(1.dp, NordicColors.HairlineBorder, RoundedCornerShape(16.dp))
                .clickable {
                    runCatching {
                        MainActivity.isLaunchingExternal = true
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/shell-ninja/Blocker-App"))
                        context.startActivity(intent)
                    }
                }
                .padding(vertical = 12.dp, horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(imageVector = CyberIcons.Code, contentDescription = null, tint = NordicColors.AccentPrimary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Source Code", color = NordicColors.TextPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(6.dp))
                Icon(imageVector = CyberIcons.OpenInNew, contentDescription = null, tint = NordicColors.TextSecondary, modifier = Modifier.size(14.dp))
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Shell Ninja Author Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable {
                    runCatching {
                        MainActivity.isLaunchingExternal = true
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/shell-ninja"))
                        context.startActivity(intent)
                    }
                }
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = R.drawable.shell_ninja_logo),
                contentDescription = "Shell Ninja Logo",
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .border(1.dp, NordicColors.HairlineBorder, CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Developed by Shell Ninja",
                color = NordicColors.AccentPrimary,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = CyberIcons.OpenInNew,
                contentDescription = null,
                tint = NordicColors.TextSecondary,
                modifier = Modifier.size(12.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Version Footer
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = R.mipmap.ic_launcher),
                contentDescription = "Blocker Icon",
                modifier = Modifier
                    .size(18.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
            Spacer(modifier = Modifier.width(7.dp))
            Text(
                text = "Blocker v${BlockerStore.DISPLAY_VERSION} (build ${BlockerStore.BUILD_NUMBER})",
                color = NordicColors.TextSecondary,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(28.dp))
    }

    // --- Dialog for Update Release Notes & Download ---
    if (showUpdateModal && updateState.release != null) {
        UpdateModal(
            updateState = updateState,
            onDismiss = { showUpdateModal = false },
            onDownloadAndInstall = {
                repo.downloadAndInstallUpdate()
                cacheStats = repo.getCacheStats()
            },
            onOpenSettings = {
                repo.openInstallSettings()
            },
            canInstall = repo.canRequestPackageInstalls()
        )
    }

    // --- Dialogs for Password Management ---

    if (showSetPasswordDialog) {
        SetPasswordDialog(
            title = "Set Master Password",
            subtitle = "Create a strong master password to lock Blocker controls and protect against bypass",
            onDismiss = { showSetPasswordDialog = false },
            onConfirm = { pass ->
                repo.setParentPassword(pass)
                showSetPasswordDialog = false
            }
        )
    }

    if (showDisablePasswordDialog) {
        ConfirmPasswordDialog(
            title = "Disable Parental Lock",
            subtitle = "Enter your current master password to remove password protection",
            confirmLabel = "Disable Lock",
            onDismiss = { showDisablePasswordDialog = false },
            onConfirm = { currentPass ->
                val ok = repo.setParentLockEnabled(false, currentPass)
                if (ok) showDisablePasswordDialog = false
                ok
            }
        )
    }

    if (showChangePasswordDialog) {
        ChangePasswordDialog(
            onDismiss = { showChangePasswordDialog = false },
            onConfirm = { currentPass, newPass ->
                val ok = repo.changeParentPassword(currentPass, newPass)
                if (ok) showChangePasswordDialog = false
                ok
            }
        )
    }
}

@Composable
private fun SetPasswordDialog(
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isPassVisible by remember { mutableStateOf(false) }

    val strength = remember(password) { BlockerStore.validatePasswordStrength(password) }
    val passwordsMatch = password.isNotEmpty() && password == confirmPassword

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(NordicColors.CardSurface)
                .border(1.dp, NordicColors.HairlineBorder, RoundedCornerShape(22.dp))
                .padding(20.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(NordicColors.CardSurfaceHero)
                            .border(1.dp, NordicColors.HairlineBorder, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = CyberIcons.Lock,
                            contentDescription = null,
                            tint = NordicColors.AccentPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = title,
                        color = NordicColors.TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    color = NordicColors.TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Password Input
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    placeholder = { Text("Enter strong password", color = NordicColors.TextSecondary, fontSize = 13.sp) },
                    visualTransformation = if (isPassVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isPassVisible = !isPassVisible }) {
                            Icon(
                                imageVector = if (isPassVisible) CyberIcons.EyeOff else CyberIcons.Eye,
                                contentDescription = null,
                                tint = NordicColors.TextSecondary
                            )
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = NordicColors.CardSurfaceHero,
                        unfocusedContainerColor = NordicColors.CardSurfaceHero,
                        focusedBorderColor = NordicColors.AccentPrimary,
                        unfocusedBorderColor = NordicColors.HairlineBorder,
                        focusedTextColor = NordicColors.TextPrimary,
                        unfocusedTextColor = NordicColors.TextPrimary
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Confirm Password Input
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    placeholder = { Text("Confirm password", color = NordicColors.TextSecondary, fontSize = 13.sp) },
                    visualTransformation = if (isPassVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = NordicColors.CardSurfaceHero,
                        unfocusedContainerColor = NordicColors.CardSurfaceHero,
                        focusedBorderColor = if (confirmPassword.isNotEmpty() && !passwordsMatch) NordicColors.AlertMuted else NordicColors.AccentPrimary,
                        unfocusedBorderColor = NordicColors.HairlineBorder,
                        focusedTextColor = NordicColors.TextPrimary,
                        unfocusedTextColor = NordicColors.TextPrimary
                    )
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Strong Password Requirements
                Text(
                    text = "STRONG PASSWORD REQUIREMENTS:",
                    color = NordicColors.TextSecondary,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.4.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                PasswordCheckItem("Minimum 8 characters", strength.minLength)
                PasswordCheckItem("Uppercase letter (A-Z)", strength.hasUpper)
                PasswordCheckItem("Lowercase letter (a-z)", strength.hasLower)
                PasswordCheckItem("Number (0-9)", strength.hasDigit)
                PasswordCheckItem("Special character (!@#$%...)", strength.hasSpecial)
                if (confirmPassword.isNotEmpty()) {
                    PasswordCheckItem("Passwords match", passwordsMatch)
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Btn(
                        label = "Cancel",
                        onClick = onDismiss,
                        kind = BtnKind.GHOST,
                        modifier = Modifier.weight(1f)
                    )
                    Btn(
                        label = "Save Password",
                        icon = CyberIcons.Check,
                        enabled = strength.isValid && passwordsMatch,
                        onClick = { onConfirm(password) },
                        kind = BtnKind.PRIMARY,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun PasswordCheckItem(label: String, passed: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(if (passed) NordicColors.AccentPrimary else NordicColors.CardSurfaceHero)
                .border(1.dp, if (passed) NordicColors.AccentPrimary else NordicColors.HairlineBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (passed) {
                Icon(
                    imageVector = CyberIcons.Check,
                    contentDescription = null,
                    tint = NordicColors.OnAccentPrimary,
                    modifier = Modifier.size(11.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            color = if (passed) NordicColors.TextPrimary else NordicColors.TextSecondary,
            fontSize = 12.sp,
            fontWeight = if (passed) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun ConfirmPasswordDialog(
    title: String,
    subtitle: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Boolean
) {
    var password by remember { mutableStateOf("") }
    var isPassVisible by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(NordicColors.CardSurface)
                .border(1.dp, NordicColors.HairlineBorder, RoundedCornerShape(22.dp))
                .padding(20.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = title,
                    color = NordicColors.TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    color = NordicColors.TextSecondary,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        if (error) error = false
                    },
                    placeholder = { Text("Enter current password", color = NordicColors.TextSecondary, fontSize = 13.sp) },
                    visualTransformation = if (isPassVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isPassVisible = !isPassVisible }) {
                            Icon(
                                imageVector = if (isPassVisible) CyberIcons.EyeOff else CyberIcons.Eye,
                                contentDescription = null,
                                tint = NordicColors.TextSecondary
                            )
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = NordicColors.CardSurfaceHero,
                        unfocusedContainerColor = NordicColors.CardSurfaceHero,
                        focusedBorderColor = NordicColors.AccentPrimary,
                        unfocusedBorderColor = NordicColors.HairlineBorder,
                        focusedTextColor = NordicColors.TextPrimary,
                        unfocusedTextColor = NordicColors.TextPrimary
                    )
                )

                if (error) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Incorrect master password. Please try again.",
                        color = NordicColors.AlertMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Btn(
                        label = "Cancel",
                        onClick = onDismiss,
                        kind = BtnKind.GHOST,
                        modifier = Modifier.weight(1f)
                    )
                    Btn(
                        label = confirmLabel,
                        icon = CyberIcons.Check,
                        enabled = password.isNotEmpty(),
                        onClick = {
                            val ok = onConfirm(password)
                            if (!ok) error = true
                        },
                        kind = BtnKind.DANGER,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ChangePasswordDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Boolean
) {
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmNewPassword by remember { mutableStateOf("") }
    var isPassVisible by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val strength = remember(newPassword) { BlockerStore.validatePasswordStrength(newPassword) }
    val passwordsMatch = newPassword.isNotEmpty() && newPassword == confirmNewPassword

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(NordicColors.CardSurface)
                .border(1.dp, NordicColors.HairlineBorder, RoundedCornerShape(22.dp))
                .padding(20.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Change Master Password",
                    color = NordicColors.TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Enter current password and choose a new strong master password",
                    color = NordicColors.TextSecondary,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Current Password
                OutlinedTextField(
                    value = currentPassword,
                    onValueChange = {
                        currentPassword = it
                        errorMsg = null
                    },
                    placeholder = { Text("Current master password", color = NordicColors.TextSecondary, fontSize = 13.sp) },
                    visualTransformation = if (isPassVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = NordicColors.CardSurfaceHero,
                        unfocusedContainerColor = NordicColors.CardSurfaceHero,
                        focusedBorderColor = NordicColors.AccentPrimary,
                        unfocusedBorderColor = NordicColors.HairlineBorder,
                        focusedTextColor = NordicColors.TextPrimary,
                        unfocusedTextColor = NordicColors.TextPrimary
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // New Password
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = {
                        newPassword = it
                        errorMsg = null
                    },
                    placeholder = { Text("New strong password", color = NordicColors.TextSecondary, fontSize = 13.sp) },
                    visualTransformation = if (isPassVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isPassVisible = !isPassVisible }) {
                            Icon(
                                imageVector = if (isPassVisible) CyberIcons.EyeOff else CyberIcons.Eye,
                                contentDescription = null,
                                tint = NordicColors.TextSecondary
                            )
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = NordicColors.CardSurfaceHero,
                        unfocusedContainerColor = NordicColors.CardSurfaceHero,
                        focusedBorderColor = NordicColors.AccentPrimary,
                        unfocusedBorderColor = NordicColors.HairlineBorder,
                        focusedTextColor = NordicColors.TextPrimary,
                        unfocusedTextColor = NordicColors.TextPrimary
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Confirm New Password
                OutlinedTextField(
                    value = confirmNewPassword,
                    onValueChange = {
                        confirmNewPassword = it
                        errorMsg = null
                    },
                    placeholder = { Text("Confirm new password", color = NordicColors.TextSecondary, fontSize = 13.sp) },
                    visualTransformation = if (isPassVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = NordicColors.CardSurfaceHero,
                        unfocusedContainerColor = NordicColors.CardSurfaceHero,
                        focusedBorderColor = if (confirmNewPassword.isNotEmpty() && !passwordsMatch) NordicColors.AlertMuted else NordicColors.AccentPrimary,
                        unfocusedBorderColor = NordicColors.HairlineBorder,
                        focusedTextColor = NordicColors.TextPrimary,
                        unfocusedTextColor = NordicColors.TextPrimary
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                PasswordCheckItem("Minimum 8 characters", strength.minLength)
                PasswordCheckItem("Uppercase letter (A-Z)", strength.hasUpper)
                PasswordCheckItem("Lowercase letter (a-z)", strength.hasLower)
                PasswordCheckItem("Number (0-9)", strength.hasDigit)
                PasswordCheckItem("Special character (!@#$%...)", strength.hasSpecial)

                if (errorMsg != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMsg!!,
                        color = NordicColors.AlertMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Btn(
                        label = "Cancel",
                        onClick = onDismiss,
                        kind = BtnKind.GHOST,
                        modifier = Modifier.weight(1f)
                    )
                    Btn(
                        label = "Update Password",
                        icon = CyberIcons.Check,
                        enabled = currentPassword.isNotEmpty() && strength.isValid && passwordsMatch,
                        onClick = {
                            val ok = onConfirm(currentPassword, newPassword)
                            if (!ok) {
                                errorMsg = "Incorrect current password"
                            }
                        },
                        kind = BtnKind.PRIMARY,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
