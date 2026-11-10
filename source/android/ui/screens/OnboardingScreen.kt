package com.blocker.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blocker.R
import com.blocker.data.BlockerRepository
import com.blocker.ui.components.*
import com.blocker.ui.theme.NordicColors

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
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Flagship Botanical Hero Welcome Card
        NordicFoliageHeroCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(NordicColors.CardSurfaceHero)
                        .border(1.dp, NordicColors.HairlineBorder, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.mipmap.ic_launcher),
                        contentDescription = "Blocker Icon",
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = "Welcome to Blocker",
                        color = NordicColors.TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.3).sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Airtight focus, Islamic reminders, and distraction defense",
                        color = NordicColors.TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        when (currentStep) {
            0 -> {
                // Step 0: Language Selection
                SectionHeader(
                    title = "Popup Message Language",
                    icon = CyberIcons.ShieldCheck,
                    subtitle = "Choose your preferred language for warning popups, Quranic reminders, and Hadiths.\n(Settings and navigation remain in English.)"
                )

                Spacer(modifier = Modifier.height(16.dp))

                // English Option
                val isEn = selectedLang == "en"
                NordicMicroGridCard(
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
                                    .border(2.dp, if (isEn) NordicColors.AccentPrimary else NordicColors.TextSecondary, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isEn) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(NordicColors.AccentPrimary)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "English",
                                color = NordicColors.TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        if (isEn) {
                            StatusPillBadge(label = "Selected", tone = BadgeTone.OK, small = true)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Reminders and popup warnings will be shown in English:\n\"Tell the believing men to reduce [some] of their vision...\" (24:30)",
                        color = NordicColors.TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bengali Option
                val isBn = selectedLang == "bn"
                NordicMicroGridCard(
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
                                    .border(2.dp, if (isBn) NordicColors.AccentPrimary else NordicColors.TextSecondary, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isBn) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(NordicColors.AccentPrimary)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "বাংলা (Bengali)",
                                color = NordicColors.TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        if (isBn) {
                            StatusPillBadge(label = "Selected", tone = BadgeTone.OK, small = true)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "কোরআনের আয়াত ও সতর্কবার্তা বাংলায় দেখানো হবে:\n“মুমিন পুরুষদের বলুন, তারা যেন তাদের দৃষ্টি অবনত রাখে…” (২৪:৩০)",
                        color = NordicColors.TextSecondary,
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
                    subtitle = "Grant permissions to activate zero-latency content filtering and airtight anti-uninstall protection."
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 1. Device Administrator (Direct system redirect to BlockerDeviceAdminReceiver)
                PermissionItemCard(
                    title = "Device admin",
                    icon = CyberIcons.ShieldCheck,
                    why = "Prevents Blocker from being uninstalled while protection is active.",
                    buttonLabel = "Set Device Admin",
                    isGranted = perms.deviceAdmin,
                    onGrant = { repo.openPermissionSettings(context, "deviceAdmin") }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 2. Usage Access
                PermissionItemCard(
                    title = "Usage access",
                    icon = CyberIcons.Apps,
                    why = "Lets Blocker monitor foreground app usage and enforce daily limits.",
                    buttonLabel = "Grant",
                    isGranted = perms.usageAccess,
                    onGrant = { repo.openPermissionSettings(context, "usageAccess") }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 3. Display Over Other Apps
                PermissionItemCard(
                    title = "Display over other apps",
                    icon = CyberIcons.Layers,
                    why = "Shows blocking HUD warnings and reminder overlays over restricted content.",
                    buttonLabel = "Grant",
                    isGranted = perms.overlay,
                    onGrant = { repo.openPermissionSettings(context, "overlay") }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 4. Accessibility Service
                PermissionItemCard(
                    title = "Accessibility service",
                    icon = CyberIcons.Eye,
                    why = "Reads browser address bar URLs and detects blocked apps in 0ms.",
                    buttonLabel = "Enable",
                    isGranted = perms.accessibility,
                    onGrant = { repo.openPermissionSettings(context, "accessibility") }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 5. Unrestricted Battery Execution
                PermissionItemCard(
                    title = "Unrestricted battery",
                    icon = CyberIcons.Battery,
                    why = "Exempts Blocker from OEM battery killers so protection never stops.",
                    buttonLabel = "Unrestricted Battery",
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
                    subtitle = "Verify starting security policies before entering Blocker."
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Master Protection Toggle Card
                NordicMicroGridCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                            Text(
                                text = "Master Protection",
                                color = NordicColors.TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Block 9,350+ adult websites, porn domains, and restricted apps.",
                                color = NordicColors.TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                        NordicSwitch(
                            checked = enableProtection,
                            onCheckedChange = { enableProtection = it }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Anti-Tamper Shield Toggle Card
                NordicMicroGridCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                            Text(
                                text = "Anti-Tamper & Uninstall Guard",
                                color = NordicColors.TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Locks Settings, launcher shortcuts, and device admin deactivation.",
                                color = NordicColors.TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                        NordicSwitch(
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
    buttonLabel: String = "Grant",
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
                    .clip(RoundedCornerShape(11.dp))
                    .background(NordicColors.CardSurfaceHero)
                    .border(
                        1.dp,
                        if (isGranted) NordicColors.AccentPrimary else NordicColors.HairlineBorder,
                        RoundedCornerShape(11.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isGranted) NordicColors.AccentPrimary else NordicColors.TextSecondary,
                    modifier = Modifier.size(19.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = NordicColors.TextPrimary,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = why,
                    color = NordicColors.TextSecondary,
                    fontSize = 11.5.sp,
                    lineHeight = 15.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (!isGranted) {
                Btn(
                    label = buttonLabel,
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
