package com.blocker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blocker.data.BlockerRepository
import com.blocker.ui.components.*
import com.blocker.ui.theme.*

@Composable
fun OnboardingScreen(
    repo: BlockerRepository,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val perms by repo.permissionsState.collectAsState()
    val protection by repo.protectionState.collectAsState()
    val scrollState = rememberScrollState()

    var currentStep by remember { mutableStateOf(0) } // 0 = Language, 1 = Permissions, 2 = Confirmation
    var selectedLang by remember { mutableStateOf(protection.popupLanguage) }
    var enableProtection by remember { mutableStateOf(true) }
    var enableShield by remember { mutableStateOf(true) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CyberBg)
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App Icon & Welcome
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(CyberAccentWell)
                .border(1.5.dp, CyberAccent, RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = CyberIcons.Shield,
                contentDescription = null,
                tint = Color(0xFFA78BFA),
                modifier = Modifier.size(28.dp)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "Welcome to Blocker",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.4).sp
        )

        Spacer(modifier = Modifier.height(18.dp))

        when (currentStep) {
            0 -> {
                // Step 0: Language Selection
                SectionHeader(
                    title = "Popup Message Language",
                    icon = CyberIcons.ShieldCheck,
                    subtitle = "Choose your preferred language for blocker warning popups, Quranic reminders, and Hadiths.\n(Settings and navigation remain in English.)"
                )

                Spacer(modifier = Modifier.height(16.dp))

                // English Option
                val isEn = selectedLang == "en"
                GlowCard(
                    active = isEn,
                    tone = if (isEn) CardTone.ACCENT else CardTone.DEFAULT,
                    onClick = {
                        selectedLang = "en"
                        repo.setPopupLanguage("en")
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .border(2.dp, if (isEn) CyberAccent else CyberTextSub, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isEn) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(CyberAccent)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "English",
                                color = Color.White,
                                fontSize = 16.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (isEn) {
                            StatusPillBadge(label = "Selected", tone = BadgeTone.OK, small = true)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Displays verses & reminders in English:\n“Tell the believing men to lower their gaze…” (24:30)",
                        color = CyberTextSub,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bengali Option
                val isBn = selectedLang == "bn"
                GlowCard(
                    active = isBn,
                    tone = if (isBn) CardTone.ACCENT else CardTone.DEFAULT,
                    onClick = {
                        selectedLang = "bn"
                        repo.setPopupLanguage("bn")
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .border(2.dp, if (isBn) CyberAccent else CyberTextSub, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isBn) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(CyberAccent)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "বাংলা (Bengali)",
                                color = Color.White,
                                fontSize = 16.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (isBn) {
                            StatusPillBadge(label = "Selected", tone = BadgeTone.OK, small = true)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "কোরআনের আয়াত ও সতর্কবার্তা বাংলায় দেখানো হবে:\n“মুমিন পুরুষদের বলুন, তারা যেন তাদের দৃষ্টি অবনত রাখে…” (২৪:৩০)",
                        color = CyberTextSub,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Btn(
                    label = "Continue to Permissions",
                    icon = CyberIcons.ChevronRight,
                    onClick = { currentStep = 1 },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            1 -> {
                // Step 1: Permissions Wizard
                SectionHeader(
                    title = "System Permissions",
                    icon = CyberIcons.ShieldCheck,
                    subtitle = "Grant the following permissions to enable airtight protection and zero-latency content filtering."
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 1. Accessibility Service
                PermissionItemCard(
                    title = "Accessibility service",
                    icon = CyberIcons.Eye,
                    why = "Reads the browser address bar and detects blocked apps in 0ms.",
                    how = "Installed apps → Blocker → turn on.",
                    isGranted = perms.accessibility,
                    onGrant = { repo.openPermissionSettings(context, "accessibility") }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 2. Device Administrator
                PermissionItemCard(
                    title = "Device admin",
                    icon = CyberIcons.ShieldCheck,
                    why = "Prevents Blocker from being uninstalled while protection is active.",
                    how = "Tap Activate this device admin app.",
                    isGranted = perms.deviceAdmin,
                    onGrant = { repo.openPermissionSettings(context, "deviceAdmin") }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 3. Usage Access
                PermissionItemCard(
                    title = "Usage access",
                    icon = CyberIcons.Apps,
                    why = "Lets Blocker monitor foreground app usage and enforce limits.",
                    how = "Select Blocker and allow usage access.",
                    isGranted = perms.usageAccess,
                    onGrant = { repo.openPermissionSettings(context, "usageAccess") }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 4. Display Over Other Apps
                PermissionItemCard(
                    title = "Display over other apps",
                    icon = CyberIcons.Layers,
                    why = "Shows blocking HUD warnings immediately over restricted content.",
                    how = "Allow display over other apps for Blocker.",
                    isGranted = perms.overlay,
                    onGrant = { repo.openPermissionSettings(context, "overlay") }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Optional Background Battery Optimization
                PermissionItemCard(
                    title = "Background execution",
                    icon = CyberIcons.Battery,
                    why = "Ensures the Android system does not kill the watchdog in background.",
                    how = "Allow unrestricted battery usage.",
                    isGranted = perms.batteryOptimizationsIgnored,
                    onGrant = { repo.openPermissionSettings(context, "battery") }
                )

                Spacer(modifier = Modifier.height(24.dp))

                Btn(
                    label = if (perms.allGranted) "Continue to Protection" else "Proceed Anyway",
                    icon = CyberIcons.ChevronRight,
                    onClick = { currentStep = 2 },
                    kind = if (perms.allGranted) BtnKind.PRIMARY else BtnKind.SECONDARY,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            2 -> {
                // Step 2: Protection Confirmation & Defaults
                SectionHeader(
                    title = "Confirm Initial Defenses",
                    icon = CyberIcons.Lock,
                    subtitle = "Verify starting security policies before entering the app."
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Master Protection Toggle
                GlowCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                            Text(
                                text = "Master Protection",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Block 9,357+ adult/gambling websites and restricted apps.",
                                color = CyberTextSub,
                                fontSize = 12.sp
                            )
                        }
                        CyberSwitch(
                            checked = enableProtection,
                            onCheckedChange = { enableProtection = it }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Anti-Tamper Shield Toggle
                GlowCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                            Text(
                                text = "Anti-Tamper & Uninstall Guard",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Locks Settings, launcher shortcuts, and device admin deactivation.",
                                color = CyberTextSub,
                                fontSize = 12.sp
                            )
                        }
                        CyberSwitch(
                            checked = enableShield,
                            onCheckedChange = { enableShield = it }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Btn(
                    label = "Launch Blocker Fortress",
                    icon = CyberIcons.ShieldCheck,
                    onClick = {
                        if (enableProtection) repo.toggleProtection(true)
                        onComplete()
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun PermissionItemCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    why: String,
    how: String,
    isGranted: Boolean,
    onGrant: () -> Unit
) {
    GlowCard(
        modifier = Modifier.fillMaxWidth(),
        active = isGranted,
        tone = if (isGranted) CardTone.OK else CardTone.DEFAULT
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isGranted) CyberOkWell else CyberAccentWell)
                    .border(
                        1.dp,
                        if (isGranted) CyberOk.copy(alpha = 0.5f) else CyberAccent.copy(alpha = 0.4f),
                        RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isGranted) CyberOkGlow else Color(0xFFC084FC),
                    modifier = Modifier.size(19.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = why,
                    color = CyberTextSub,
                    fontSize = 11.5.sp,
                    lineHeight = 15.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (!isGranted) {
                Btn(
                    label = "Grant",
                    onClick = onGrant,
                    kind = BtnKind.PRIMARY
                )
            } else {
                StatusPillBadge(
                    label = "Granted",
                    tone = BadgeTone.OK,
                    icon = CyberIcons.Check,
                    small = true
                )
            }
        }
    }
}
