package com.blocker.ui.components

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.blocker.BlockerStore
import com.blocker.data.UpdateManager
import com.blocker.data.UpdateState
import com.blocker.ui.theme.*

@Composable
fun UpdateModal(
    updateState: UpdateState,
    onDismiss: () -> Unit,
    onDownloadAndInstall: () -> Unit,
    onOpenSettings: () -> Unit,
    canInstall: Boolean
) {
    val rel = updateState.release ?: return
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val isNewer = updateState.hasUpdate
    val isAhead = UpdateManager.compareSemVer(BlockerStore.APP_VERSION, rel.version) > 0
    val sizeMb = if (rel.sizeBytes > 0) String.format("%.1f MB", rel.sizeBytes / (1024f * 1024f)) else ""
    val downloadedMb = String.format("%.1f", updateState.downloadedBytes / (1024f * 1024f))
    val totalMb = if (updateState.totalBytes > 0) String.format("%.1f MB", updateState.totalBytes / (1024f * 1024f)) else sizeMb
    val progressPercent = (updateState.downloadProgress * 100).toInt()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xCC050814))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.88f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xFF0E1322))
                    .border(1.dp, CyberAccent.copy(alpha = 0.35f), RoundedCornerShape(22.dp))
                    .padding(20.dp)
            ) {
                // Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(CyberAccentSoft)
                                .border(1.dp, CyberAccent.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = CyberIcons.Download,
                                contentDescription = null,
                                tint = CyberAccentGlow,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (isNewer) "New Update Available" else if (isAhead) "Public Release Notes" else "Release Notes",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${rel.tagName} • Installed: v${BlockerStore.APP_VERSION}",
                                color = CyberTextSub,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(CyberCardSurface)
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = CyberIcons.Close,
                            contentDescription = "Close",
                            tint = CyberTextSub,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Metadata Pill Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (sizeMb.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(CyberCardSurface)
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(text = "Size: $sizeMb", color = CyberTextSub, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(CyberCardSurface)
                            .clickable {
                                runCatching {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(rel.htmlUrl))
                                    context.startActivity(intent)
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "View on GitHub", color = CyberAccentGlow, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(imageVector = CyberIcons.OpenInNew, contentDescription = null, tint = CyberAccentGlow, modifier = Modifier.size(13.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Scrollable Changelog
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF090D18))
                        .border(1.dp, CyberCardBorder, RoundedCornerShape(14.dp))
                        .padding(14.dp)
                ) {
                    Column(modifier = Modifier.verticalScroll(scrollState)) {
                        Text(
                            text = "Changelog",
                            color = Color.White,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        val cleanBody = rel.body.ifBlank { "No release notes provided." }
                        cleanBody.lines().forEach { line ->
                            val trimmed = line.trim()
                            if (trimmed.startsWith("#")) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = trimmed.replace(Regex("^#+\\s*"), ""),
                                    color = CyberAccentGlow,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                            } else if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
                                Row(modifier = Modifier.padding(vertical = 2.dp)) {
                                    Text(text = "• ", color = CyberAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = trimmed.substring(2).replace("**", ""),
                                        color = CyberText,
                                        fontSize = 12.5.sp,
                                        lineHeight = 17.sp
                                    )
                                }
                            } else if (trimmed.isNotEmpty()) {
                                Text(
                                    text = trimmed.replace("**", ""),
                                    color = CyberTextSub,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Download Progress (if active)
                if (updateState.downloading) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Downloading update...", color = CyberAccentGlow, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text(text = "$progressPercent% ($downloadedMb / $totalMb)", color = CyberTextSub, fontSize = 11.5.sp)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = updateState.downloadProgress,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = CyberAccentGlow,
                            trackColor = Color(0xFF1E293B)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }

                if (updateState.error != null) {
                    Text(
                        text = updateState.error,
                        color = CyberDanger,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Btn(
                        label = "Close",
                        onClick = onDismiss,
                        kind = BtnKind.GHOST,
                        modifier = Modifier.weight(1f)
                    )

                    if (!canInstall) {
                        Btn(
                            label = "Allow Install",
                            icon = CyberIcons.OpenInNew,
                            onClick = onOpenSettings,
                            kind = BtnKind.PRIMARY,
                            modifier = Modifier.weight(1.4f)
                        )
                    } else if (updateState.isDownloaded) {
                        Btn(
                            label = "Install Now",
                            icon = CyberIcons.Check,
                            onClick = onDownloadAndInstall,
                            kind = BtnKind.PRIMARY,
                            modifier = Modifier.weight(1.4f)
                        )
                    } else {
                        Btn(
                            label = if (updateState.downloading) "Downloading ($progressPercent%)" else "Download & Install",
                            icon = CyberIcons.Download,
                            enabled = !updateState.downloading,
                            onClick = onDownloadAndInstall,
                            kind = BtnKind.PRIMARY,
                            modifier = Modifier.weight(1.4f)
                        )
                    }
                }
            }
        }
    }
}
