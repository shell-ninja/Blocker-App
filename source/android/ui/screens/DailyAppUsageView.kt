package com.blocker.ui.screens

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blocker.PermissionFlowTracker
import com.blocker.data.BlockerRepository
import com.blocker.data.DailyAppUsageItem
import com.blocker.data.DailyUsageSummary
import com.blocker.data.UsageStatsRepository
import com.blocker.ui.components.CyberIcons
import kotlinx.coroutines.launch

@Composable
fun DailyAppUsageView(
    repo: BlockerRepository,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val usageRepo = remember { UsageStatsRepository(context) }

    var usageSummary by remember { mutableStateOf<DailyUsageSummary?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    var filterTab by remember { mutableStateOf(0) } // 0 = User Apps, 1 = System Apps, 2 = All Apps

    fun refresh(force: Boolean = false) {
        coroutineScope.launch {
            isRefreshing = true
            usageSummary = usageRepo.getDailyUsage(force = force)
            isRefreshing = false
        }
    }

    LaunchedEffect(Unit) {
        refresh(force = false)
    }

    val summary = usageSummary

    if (summary != null && !summary.hasUsageAccess) {
        // Usage Access permission required card
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF110D22),
                border = BorderStroke(1.dp, Color(0xFF2B224D))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF1B1536)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = CyberIcons.Apps,
                            contentDescription = null,
                            tint = Color(0xFFA78BFA),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Text(
                        text = "Usage Access Required",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF5F3FF),
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = "To show accurate daily screen time and per-app usage graphs, please grant Usage Access in Android Settings.",
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = Color(0xFF9E94C4),
                        textAlign = TextAlign.Center
                    )

                    Button(
                        onClick = {
                            PermissionFlowTracker.isLaunchingPermissionIntent = true
                            com.blocker.MainActivity.isLaunchingExternal = true
                            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            runCatching { context.startActivity(intent) }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                        modifier = Modifier.fillMaxWidth().height(44.dp)
                    ) {
                        Text(
                            text = "Grant Usage Access",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
        return
    }

    val displayedApps = remember(summary, filterTab) {
        if (summary == null) return@remember emptyList<DailyAppUsageItem>()
        when (filterTab) {
            0 -> summary.allApps.filter { !it.isSystem }
            1 -> summary.allApps.filter { it.isSystem }
            else -> summary.allApps
        }
    }

    val maxAppUsage = remember(summary) {
        summary?.allApps?.firstOrNull()?.usageMs ?: 1L
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Card 1: Today's Total Screen Time & Top 5 Apps Bar Chart Card
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF110D22),
                border = BorderStroke(1.dp, Color(0xFF2B224D))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "TODAY'S SCREEN TIME",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF9E94C4),
                        letterSpacing = 1.sp
                    )

                    val totalMs = summary?.totalScreenTimeMs ?: 0L
                    val totalHours = totalMs / (3600 * 1000L)
                    val totalMins = (totalMs % (3600 * 1000L)) / (60 * 1000L)
                    val formattedTotal = if (totalHours > 0) "${totalHours}h ${totalMins}m" else "${totalMins}m"

                    Text(
                        text = formattedTotal,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFF5F3FF),
                        fontFamily = FontFamily.Default
                    )

                    // Vertical Bar Chart for Top 5 Most Used Apps Today
                    val top5 = summary?.top5Apps ?: emptyList()
                    if (top5.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        val top1Usage = top5.first().usageMs.coerceAtLeast(1L)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            top5.forEachIndexed { index, app ->
                                val fraction = (app.usageMs.toFloat() / top1Usage.toFloat()).coerceIn(0.08f, 1f)
                                val animatedFraction by animateFloatAsState(
                                    targetValue = fraction,
                                    animationSpec = tween(durationMillis = 650),
                                    label = "bar_height_$index"
                                )

                                val barColor = when (index) {
                                    0 -> Color(0xFF8B5CF6)
                                    1 -> Color(0xFFA78BFA)
                                    2 -> Color(0xFF9333EA)
                                    3 -> Color(0xFF7C3AED)
                                    else -> Color(0xFF6D28D9)
                                }

                                val appMins = app.usageMs / 60_000L
                                val appHours = appMins / 60
                                val appRemMins = appMins % 60
                                val durStr = if (appHours > 0) "${appHours}h ${appRemMins}m" else "${appRemMins}m"

                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Bottom
                                ) {
                                    Text(
                                        text = durStr,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFFC4B5FD),
                                        maxLines = 1,
                                        softWrap = false
                                    )

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Box(
                                        modifier = Modifier
                                            .width(26.dp)
                                            .height((100 * animatedFraction).dp)
                                            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                            .background(barColor)
                                    )

                                    Spacer(modifier = Modifier.height(6.dp))

                                    AppIconView(
                                        drawable = app.icon,
                                        packageName = app.packageName,
                                        modifier = Modifier.size(24.dp)
                                    )

                                    Spacer(modifier = Modifier.height(2.dp))

                                    Text(
                                        text = app.label,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFFF5F3FF),
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Filter Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val filters = listOf("User Apps", "System Apps", "All Apps")
                    filters.forEachIndexed { index, title ->
                        val isSelected = filterTab == index
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) Color(0xFF8B5CF6) else Color(0xFF1B1536))
                                .border(
                                    1.dp,
                                    if (isSelected) Color(0xFF8B5CF6) else Color(0xFF2B224D),
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { filterTab = index }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = title,
                                color = if (isSelected) Color.White else Color(0xFF9E94C4),
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                IconButton(
                    onClick = { refresh(force = true) },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = CyberIcons.Refresh,
                        contentDescription = "Refresh",
                        tint = if (isRefreshing) Color(0xFF8B5CF6) else Color(0xFF9E94C4),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Card List: Per-App Daily Usage Rows (Horizontal Bar View)
        if (displayedApps.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isRefreshing) "Loading usage stats..." else "No usage recorded today for this category",
                        fontSize = 13.sp,
                        color = Color(0xFF9E94C4),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            items(displayedApps, key = { it.packageName }, contentType = { "app_row" }) { app ->
                val fraction = (app.usageMs.toFloat() / maxAppUsage.toFloat()).coerceIn(0.02f, 1f)
                val animatedBar by animateFloatAsState(
                    targetValue = fraction,
                    animationSpec = tween(durationMillis = 500),
                    label = "horizontal_bar_${app.packageName}"
                )

                val appMins = app.usageMs / 60_000L
                val appHours = appMins / 60
                val remMins = appMins % 60
                val durationStr = if (appHours > 0) "${appHours}h ${remMins}m" else "${remMins}m"

                val totalMs = summary?.totalScreenTimeMs?.coerceAtLeast(1L) ?: 1L
                val pct = ((app.usageMs.toFloat() / totalMs.toFloat()) * 100f).toInt()
                val readoutStr = "$durationStr ($pct%)"

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF110D22),
                    border = BorderStroke(1.dp, Color(0xFF2B224D))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AppIconView(
                            drawable = app.icon,
                            packageName = app.packageName,
                            modifier = Modifier.size(40.dp)
                        )

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = app.label,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFF5F3FF),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                                )

                                Text(
                                    text = readoutStr,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFFA78BFA),
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }

                            // Animated horizontal progress bar
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF2E2552))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(animatedBar)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF8B5CF6))
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
