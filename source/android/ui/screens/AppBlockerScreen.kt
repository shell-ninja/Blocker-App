package com.blocker.ui.screens

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blocker.data.AppItem
import com.blocker.data.BlockerRepository
import com.blocker.ui.components.*
import com.blocker.ui.theme.*

@Composable
fun AppBlockerScreen(
    repo: BlockerRepository,
    modifier: Modifier = Modifier
) {
    val apps by repo.installedApps.collectAsState()
    val isRefreshing by repo.isRefreshingApps.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var filterTab by remember { mutableStateOf(0) } // 0 = All, 1 = Blocked, 2 = User, 3 = System
    var selectedAppForLimit by remember { mutableStateOf<AppItem?>(null) }

    val filteredApps = remember(apps, searchQuery, filterTab) {
        apps.filter { app ->
            val matchesQuery = searchQuery.isBlank() ||
                app.label.contains(searchQuery, ignoreCase = true) ||
                app.packageName.contains(searchQuery, ignoreCase = true)

            val matchesTab = when (filterTab) {
                1 -> app.isBlocked
                2 -> !app.isSystem
                3 -> app.isSystem
                else -> true
            }

            matchesQuery && matchesTab
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        SectionHeader(
            title = "Apps & packages",
            icon = CyberIcons.Apps,
            subtitle = "Prevent launches of distracting or addictive applications"
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Search Input
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search installed apps...", color = NordicColors.TextSecondary, fontSize = 13.5.sp) },
            leadingIcon = {
                Icon(
                    imageVector = CyberIcons.Search,
                    contentDescription = null,
                    tint = NordicColors.TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = NordicColors.CardSurfaceHero,
                unfocusedContainerColor = NordicColors.CardSurfaceHero,
                focusedTextColor = NordicColors.TextPrimary,
                unfocusedTextColor = NordicColors.TextPrimary,
                focusedBorderColor = NordicColors.AccentPrimary,
                unfocusedBorderColor = NordicColors.HairlineBorder
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Filter Tabs
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val tabs = listOf("All", "Blocked (${apps.count { it.isBlocked }})", "User", "System")
            tabs.forEachIndexed { index, label ->
                val isSelected = filterTab == index
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) NordicColors.AccentPrimary else NordicColors.CardSurfaceHero)
                        .border(1.dp, if (isSelected) NordicColors.AccentPrimary else NordicColors.HairlineBorder, RoundedCornerShape(10.dp))
                        .clickable { filterTab = index }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) NordicColors.OnAccentPrimary else NordicColors.TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (isRefreshing && apps.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NordicColors.AccentPrimary)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredApps, key = { it.packageName }) { app ->
                    AppRow(
                        app = app,
                        onToggle = { repo.toggleAppBlock(app.packageName) },
                        onLimitClick = { selectedAppForLimit = app }
                    )
                }
            }
        }
    }

    // App Limit Modal
    if (selectedAppForLimit != null) {
        val app = selectedAppForLimit!!
        AlertDialog(
            onDismissRequest = { selectedAppForLimit = null },
            containerColor = NordicColors.CardSurface,
            title = {
                Text(
                    text = "Daily Limit: ${app.label}",
                    color = NordicColors.TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Block app once daily usage exceeds limit:",
                        color = NordicColors.TextSecondary,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    val limits = listOf(0 to "No Limit", 15 to "15 minutes", 30 to "30 minutes", 45 to "45 minutes", 60 to "1 hour", 120 to "2 hours")
                    limits.forEach { (mins, text) ->
                        val isCurrent = app.limitMinutes == mins
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isCurrent) NordicColors.CardSurfaceHero else Color.Transparent)
                                .border(
                                    1.dp,
                                    if (isCurrent) NordicColors.AccentPrimary.copy(alpha = 0.4f) else Color.Transparent,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    repo.setAppLimit(app.packageName, mins)
                                    selectedAppForLimit = null
                                }
                                .padding(vertical = 10.dp, horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = text,
                                color = if (isCurrent) NordicColors.TextPrimary else NordicColors.TextSecondary,
                                fontSize = 14.sp,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                            )
                            if (isCurrent) {
                                Icon(
                                    imageVector = CyberIcons.Check,
                                    contentDescription = null,
                                    tint = NordicColors.AccentPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedAppForLimit = null }) {
                    Text("Close", color = NordicColors.AccentPrimary, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
private fun AppRow(
    app: AppItem,
    onToggle: () -> Unit,
    onLimitClick: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(NordicColors.CardSurface)
            .border(1.dp, if (app.isBlocked) NordicColors.AccentPrimary.copy(alpha = 0.35f) else NordicColors.HairlineBorder, shape)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // App Icon
        AppIconView(
            drawable = app.icon,
            modifier = Modifier.size(42.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Label and Usage
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.label,
                color = NordicColors.TextPrimary,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                val usageMins = app.todayUsageMs / 60_000L
                Text(
                    text = "${usageMins}m today",
                    color = NordicColors.TextSecondary,
                    fontSize = 11.5.sp
                )

                if (app.limitMinutes > 0) {
                    Text(text = " • ", color = NordicColors.TrackMuted, fontSize = 11.sp)
                    Text(
                        text = "Limit: ${app.limitMinutes}m",
                        color = NordicColors.AlertMuted,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Limit Picker Button
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(NordicColors.CardSurfaceHero)
                .clickable(onClick = onLimitClick)
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = CyberIcons.Clock,
                contentDescription = null,
                tint = NordicColors.TextSecondary,
                modifier = Modifier.size(15.dp)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Block Toggle Switch
        NordicSwitch(
            checked = app.isBlocked,
            onCheckedChange = { onToggle() }
        )
    }
}

@Composable
fun AppIconView(
    drawable: Drawable?,
    modifier: Modifier = Modifier
) {
    if (drawable == null) {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(12.dp))
                .background(CyberCardSurface),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = CyberIcons.Apps,
                contentDescription = null,
                tint = CyberTextSub,
                modifier = Modifier.size(20.dp)
            )
        }
        return
    }

    val bitmap = remember(drawable) {
        val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 96
        val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 96
        val bm = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bm)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        bm.asImageBitmap()
    }

    Image(
        bitmap = bitmap,
        contentDescription = null,
        modifier = modifier.clip(RoundedCornerShape(12.dp))
    )
}
