package com.blocker.ui.screens

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.blocker.MyDeviceAdminReceiver
import com.blocker.data.BlockerRepository
import com.blocker.ui.components.CyberCard
import com.blocker.ui.components.GlowButton
import com.blocker.ui.components.SectionHeader
import com.blocker.ui.theme.*

@Composable
fun OnboardingScreen(
    repo: BlockerRepository,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val perms by repo.permissionsState.collectAsState()
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CyberBg)
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App Icon & Welcome
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(CyberAccentWell)
                .border(1.5.dp, CyberAccent, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "🛡️", fontSize = 36.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Welcome to Blocker",
            color = CyberText,
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Grant permissions below to enable unbypassable self-protection and content filtering.",
            color = CyberTextSub,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 1. Accessibility Service
        PermissionStepCard(
            title = "Accessibility Service",
            desc = "Required to inspect URLs, enforce app blocks, and protect system settings in 0ms.",
            isGranted = perms.accessibility,
            onGrant = {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 2. Device Administrator
        PermissionStepCard(
            title = "Device Administrator",
            desc = "Prevents unauthorized uninstallations and tampering with app settings.",
            isGranted = perms.deviceAdmin,
            onGrant = {
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(
                        DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                        ComponentName(context, MyDeviceAdminReceiver::class.java)
                    )
                    putExtra(
                        DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        "Blocker requires Device Admin to prevent uninstall attempts while protection is active."
                    )
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 3. Usage Access
        PermissionStepCard(
            title = "Usage Access",
            desc = "Monitors app screen time and enforces daily limits accurately.",
            isGranted = perms.usageAccess,
            onGrant = {
                val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 4. Draw Over Other Apps (Overlay)
        PermissionStepCard(
            title = "Display Over Other Apps",
            desc = "Shows zero-latency warning dialogs and Covenant alert banners.",
            isGranted = perms.overlay,
            onGrant = {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 5. Battery Optimization
        PermissionStepCard(
            title = "Ignore Battery Optimizations",
            desc = "Ensures background protection is never killed by the OS.",
            isGranted = perms.batteryOptimizationsIgnored,
            onGrant = {
                val intent = Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Continue Button
        GlowButton(
            text = if (perms.allGranted) "Continue to App" else "Proceed Anyway",
            onClick = onComplete,
            isEmerald = perms.allGranted,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun PermissionStepCard(
    title: String,
    desc: String,
    isGranted: Boolean,
    onGrant: () -> Unit
) {
    CyberCard(
        modifier = Modifier.fillMaxWidth(),
        isGlowing = isGranted,
        glowColor = CyberOkSoft
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (isGranted) CyberOkSoft else CyberCardSurface)
                    .border(1.dp, if (isGranted) CyberOk else CyberCardBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isGranted) "✓" else "!",
                    color = if (isGranted) CyberOkGlow else CyberWarn,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = CyberText,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = desc,
                    color = CyberTextSub,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            if (!isGranted) {
                GlowButton(
                    text = "Grant",
                    onClick = onGrant
                )
            } else {
                Text(
                    text = "Granted",
                    color = CyberOkGlow,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
