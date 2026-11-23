package com.blocker.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.blocker.MainActivity
import com.blocker.PermissionFlowTracker
import com.blocker.R
import com.blocker.data.BlockerRepository
import com.blocker.engine.tamper.AccessibilityPauseState
import com.blocker.ui.components.CyberIcons
import com.blocker.ui.components.Elevated3DCard
import com.blocker.ui.components.NordicSwitch

@Composable
fun OnboardingScreen(
    repo: BlockerRepository,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val perms by repo.permissionsState.collectAsState()
    val scrollState = rememberScrollState()
    val lifecycleOwner = LocalLifecycleOwner.current

    var isBatteryGranted by remember {
        mutableStateOf(
            (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isIgnoringBatteryOptimizations(context.packageName) == true
        )
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isBatteryGranted = (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isIgnoringBatteryOptimizations(context.packageName) == true
                repo.refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val totalCount = 7
    val effectiveBatteryGranted = isBatteryGranted || perms.batteryOptimizationsIgnored
    val grantedCount = listOf(
        perms.accessibility,
        perms.deviceAdmin,
        perms.overlay,
        perms.dndAccess,
        perms.notificationAccess,
        perms.usageAccess,
        effectiveBatteryGranted
    ).count { it }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Header Section
        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1B1536)),
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

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = "System Permissions",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFF5F3FF),
            textAlign = TextAlign.Center
        )

        Text(
            text = "Grant the permissions below so Blocker can enforce focus, silence distractions, and prevent tampering.",
            fontSize = 13.sp,
            color = Color(0xFF9E94C4),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Surface(
            color = Color(0xFF1B1536),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color(0xFF4C3B87))
        ) {
            Text(
                text = "$grantedCount of $totalCount Granted",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (grantedCount == totalCount) Color(0xFF34D399) else Color(0xFFA78BFA),
                maxLines = 1,
                softWrap = false
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 1. Accessibility Service
        PermissionSetupCard(
            title = "Accessibility Service",
            description = "Reads browser address bar URLs and detects blocked apps in 0ms.",
            iconVector = CyberIcons.Eye,
            isGranted = perms.accessibility,
            onGrantClick = { repo.openPermissionSettings(context, "accessibility") }
        )

        // 2. Device Administrator
        PermissionSetupCard(
            title = "Device Administrator",
            description = "Prevents Blocker from being uninstalled or bypassed while active.",
            iconVector = CyberIcons.ShieldCheck,
            isGranted = perms.deviceAdmin,
            onGrantClick = { repo.openPermissionSettings(context, "deviceAdmin") }
        )

        // 3. Display Over Other Apps
        PermissionSetupCard(
            title = "Display Over Other Apps",
            description = "Shows blocking warning HUD and reminder screens over restricted content.",
            iconVector = CyberIcons.Layers,
            isGranted = perms.overlay,
            onGrantClick = { repo.openPermissionSettings(context, "overlay") }
        )

        // 4. Do Not Disturb Access
        PermissionSetupCard(
            title = "Do Not Disturb Access",
            description = "Auto-silences incoming sounds and alerts during Study & Prayer focus sessions.",
            iconVector = CyberIcons.Dnd,
            isGranted = perms.dndAccess,
            onGrantClick = { repo.openPermissionSettings(context, "dnd") }
        )

        // 5. Notification Access
        PermissionSetupCard(
            title = "Notification Access",
            description = "Suppresses distracting notifications from blocked apps during focus.",
            iconVector = CyberIcons.NotificationsOff,
            isGranted = perms.notificationAccess,
            onGrantClick = { repo.openPermissionSettings(context, "notification") }
        )

        // 6. Usage Access
        PermissionSetupCard(
            title = "Usage Access",
            description = "Enables Daily Usage tracking, screen time graphs, and daily app limits.",
            iconVector = CyberIcons.Apps,
            isGranted = perms.usageAccess,
            onGrantClick = { repo.openPermissionSettings(context, "usageAccess") }
        )

        // 7. Unrestricted Battery Usage
        PermissionSetupCard(
            title = "Unrestricted Battery Usage",
            description = "Prevents Android from pausing Blocker in the background",
            iconVector = CyberIcons.Battery,
            isGranted = effectiveBatteryGranted,
            onGrantClick = {
                PermissionFlowTracker.isLaunchingPermissionIntent = true
                PermissionFlowTracker.lastBatteryPermissionRequestMs = SystemClock.elapsedRealtime()
                MainActivity.isLaunchingExternal = true
                val pkgUri = Uri.parse("package:${context.packageName}")
                runCatching {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkgUri).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }.onFailure {
                    runCatching {
                        val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(fallback)
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(10.dp))

        val pauseState by AccessibilityPauseState.stateFlow.collectAsState()
        LaunchedEffect(Unit) { AccessibilityPauseState.load(context) }

        Elevated3DCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = "Temporary Accessibility Pause (Optional)",
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFF5F3FF),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Allow pausing Accessibility for 10 minutes later (for Banking apps). Leave OFF to keep this option completely hidden.",
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = Color(0xFF9E94C4)
                    )
                }
                NordicSwitch(
                    checked = pauseState.allowTempPauseOptIn,
                    onCheckedChange = { AccessibilityPauseState.setOptIn(context, it) }
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        val coreGranted = perms.accessibility && perms.deviceAdmin && perms.overlay && perms.dndAccess && effectiveBatteryGranted

        if (coreGranted) {
            Button(
                onClick = {
                    AccessibilityPauseState.finalizeOptInDecision(
                        context,
                        AccessibilityPauseState.load(context).allowTempPauseOptIn
                    )
                    repo.toggleProtection(true)
                    repo.setInitialSetupActivated(true)
                    com.blocker.PermissionFlowTracker.isOnPermissionSetupScreen = false
                    onComplete()
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF8B5CF6),
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(
                    text = "Start Protection & Enter Blocker",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    softWrap = false
                )
            }
        } else {
            Button(
                onClick = { },
                enabled = false,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    disabledContainerColor = Color(0xFF2B224D).copy(alpha = 0.5f),
                    disabledContentColor = Color(0xFF9E94C4).copy(alpha = 0.6f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(
                    text = "Enable Required Permissions Above ($grantedCount/$totalCount)",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
fun PermissionSetupCard(
    title: String,
    description: String,
    iconVector: ImageVector,
    isGranted: Boolean,
    onGrantClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF110D22),
        border = BorderStroke(1.dp, if (isGranted) Color(0xFF10B981) else Color(0xFF2B224D))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Left Icon Box (Fixed 42.dp)
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1B1536)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    iconVector,
                    contentDescription = null,
                    tint = Color(0xFFA78BFA),
                    modifier = Modifier.size(20.dp)
                )
            }

            // 2. Center Title & Description Column (Takes remaining width cleanly)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFF5F3FF),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = description,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = Color(0xFF9E94C4),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 3. Right Action Button or Granted Badge (Fixed single-line pill, never wraps!)
            if (isGranted) {
                Surface(
                    color = Color(0xFF064E3B).copy(alpha = 0.55f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.6f))
                ) {
                    Text(
                        text = "Granted",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF34D399),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            } else {
                Button(
                    onClick = onGrantClick,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text(
                        text = "Enable",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}
