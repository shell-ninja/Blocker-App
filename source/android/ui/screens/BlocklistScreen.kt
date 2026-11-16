package com.blocker.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blocker.NativeBlockerBridge
import com.blocker.data.BlockerRepository
import com.blocker.ui.components.*
import com.blocker.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BlocklistScreen(
    repo: BlockerRepository,
    modifier: Modifier = Modifier
) {
    val domains by repo.blockedDomains.collectAsState()
    val keywords by repo.blockedKeywords.collectAsState()
    val protection by repo.protectionState.collectAsState()
    val focus by repo.focusState.collectAsState()
    val distraction by repo.distractionShield.collectAsState()
    val isLocked = protection.isActive || focus.active
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    val rulesBringIntoView = remember { BringIntoViewRequester() }
    val testBringIntoView = remember { BringIntoViewRequester() }

    var rulesTab by remember { mutableStateOf(0) } // 0 = Domains, 1 = Keywords
    var ruleInput by remember { mutableStateOf("") }
    var testInput by remember { mutableStateOf("") }
    var testResult by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .imePadding()
            .verticalScroll(scrollState)
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp)
    ) {
        SectionHeader(
            title = "Web Security Studio",
            icon = CyberIcons.Web,
            subtitle = "Browser privacy defense, custom filtering rules, and inspection simulation"
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Default Block List Indicator
        Elevated3DCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                    RaisedSquirclePedestal(size = 36.dp) {
                        Icon(
                            imageVector = CyberIcons.Shield,
                            contentDescription = null,
                            tint = NordicColors.AccentPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "Native Engine Core",
                            color = NordicColors.TextPrimary,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                        Text(
                            text = "9,357 adult sites & 62 keyword patterns loaded in memory",
                            color = NordicColors.TextSecondary,
                            fontSize = 11.5.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                StatusPillBadge(label = "Active", tone = BadgeTone.OK, small = true)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // SECTION 1: 3D Browser & Search Shields Card (Incognito + SafeSearch together)
        Elevated3DCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RaisedSquirclePedestal(size = 36.dp) {
                        Icon(
                            imageVector = CyberIcons.ShieldCheck,
                            contentDescription = null,
                            tint = NordicColors.AccentPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Browser & Search Shields",
                            color = NordicColors.TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Zero-VPN privacy tab and search thumbnail protection",
                            color = NordicColors.TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Row A: Incognito & Private Tab Blocker
            WebShieldRow(
                title = "Incognito & Private Tab Blocker",
                subtitle = "Automatically closes Incognito, Private, and Secret tabs across all browsers",
                icon = CyberIcons.EyeOff,
                checked = distraction.blockIncognitoMode,
                pendingUnlockUntil = distraction.pendingUnlocks["block_incognito_mode"] ?: 0L,
                onCancelUnlock = { repo.cancelDistractionUnlock("block_incognito_mode") },
                onCheckedChange = { repo.toggleDistraction("block_incognito_mode", it) }
            )

            Divider(color = NordicColors.HairlineBorder.copy(alpha = 0.5f), thickness = 0.8.dp, modifier = Modifier.padding(vertical = 10.dp))

            // Row B: SafeSearch & Query Guard
            WebShieldRow(
                title = "SafeSearch & Query Guard",
                subtitle = "Enforces strict SafeSearch and blocks unfiltered adult search queries",
                icon = CyberIcons.Search,
                checked = distraction.enforceSafeSearchGuard,
                pendingUnlockUntil = distraction.pendingUnlocks["enforce_safesearch_guard"] ?: 0L,
                onCancelUnlock = { repo.cancelDistractionUnlock("enforce_safesearch_guard") },
                onCheckedChange = { repo.toggleDistraction("enforce_safesearch_guard", it) }
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // SECTION 2: 3D Custom Rules Manager Card (Domains + Keywords combined)
        Elevated3DCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RaisedSquirclePedestal(size = 36.dp) {
                    Icon(
                        imageVector = CyberIcons.Edit,
                        contentDescription = null,
                        tint = NordicColors.AccentPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Custom Rules Manager",
                        color = NordicColors.TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "User-defined blocked websites and restricted keywords",
                        color = NordicColors.TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 2-Tab Pill Switcher in Recessed Well
            val activeTabBrush = remember {
                Brush.verticalGradient(
                    listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9))
                )
            }
            val activeTabBorder = remember {
                Brush.verticalGradient(
                    listOf(Color(0xFFA78BFA).copy(alpha = 0.85f), Color(0xFF4C1D95).copy(alpha = 0.35f))
                )
            }

            Recessed3DWell(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(3.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(9.dp))
                            .then(
                                if (rulesTab == 0) {
                                    Modifier
                                        .background(activeTabBrush)
                                        .border(1.dp, activeTabBorder, RoundedCornerShape(9.dp))
                                } else {
                                    Modifier.background(Color.Transparent)
                                }
                            )
                            .clickable { rulesTab = 0 }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Domains (${domains.size})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (rulesTab == 0) Color.White else NordicColors.TextSecondary,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(9.dp))
                            .then(
                                if (rulesTab == 1) {
                                    Modifier
                                        .background(activeTabBrush)
                                        .border(1.dp, activeTabBorder, RoundedCornerShape(9.dp))
                                } else {
                                    Modifier.background(Color.Transparent)
                                }
                            )
                            .clickable { rulesTab = 1 }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Keywords (${keywords.size})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (rulesTab == 1) Color.White else NordicColors.TextSecondary,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Recessed Input Well
            Recessed3DWell(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = ruleInput,
                        onValueChange = { ruleInput = it },
                        placeholder = {
                            Text(
                                if (rulesTab == 0) "e.g. reddit.com or badsite.org" else "e.g. adult term or phrase",
                                color = NordicColors.TextSecondary,
                                fontSize = 12.5.sp,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        singleLine = true,
                        maxLines = 1,
                        modifier = Modifier
                            .weight(1f)
                            .bringIntoViewRequester(rulesBringIntoView)
                            .onFocusEvent { focusState ->
                                if (focusState.isFocused) {
                                    coroutineScope.launch {
                                        delay(200L)
                                        rulesBringIntoView.bringIntoView()
                                    }
                                }
                            },
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF140E29),
                            unfocusedContainerColor = Color(0xFF140E29),
                            focusedTextColor = NordicColors.TextPrimary,
                            unfocusedTextColor = NordicColors.TextPrimary,
                            focusedBorderColor = NordicColors.AccentPrimary,
                            unfocusedBorderColor = NordicColors.HairlineBorder
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Tactile3DButton(
                        onClick = {
                            if (ruleInput.isNotBlank()) {
                                if (rulesTab == 0) {
                                    repo.addDomain(ruleInput.trim())
                                } else {
                                    repo.addKeyword(ruleInput.trim())
                                }
                                ruleInput = ""
                            }
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = CyberIcons.Add,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Add",
                            color = Color.White,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Active rules list
            val activeList = if (rulesTab == 0) domains else keywords
            if (activeList.isEmpty()) {
                Text(
                    text = if (rulesTab == 0) "No custom domains added yet" else "No custom keywords added yet",
                    color = NordicColors.TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    activeList.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF160F30))
                                .border(1.dp, NordicColors.HairlineBorder, RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = item,
                                color = NordicColors.TextPrimary,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f).padding(end = 8.dp)
                            )
                            Icon(
                                imageVector = CyberIcons.Delete,
                                contentDescription = "Delete",
                                tint = if (isLocked) NordicColors.TextSecondary.copy(alpha = 0.35f) else NordicColors.AlertMuted,
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable(enabled = !isLocked) {
                                        if (rulesTab == 0) repo.removeDomain(item)
                                        else repo.removeKeyword(item)
                                    }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // SECTION 3: 3D Block Engine Simulator Card
        Elevated3DCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RaisedSquirclePedestal(size = 36.dp) {
                    Icon(
                        imageVector = CyberIcons.Search,
                        contentDescription = null,
                        tint = NordicColors.AccentPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Block Engine Simulator",
                        color = NordicColors.TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Simulate URL evaluation against native C++ engine & rules",
                        color = NordicColors.TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Recessed3DWell(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = testInput,
                        onValueChange = {
                            testInput = it
                            testResult = null
                        },
                        placeholder = { Text("https://example.com/page", color = NordicColors.TextSecondary, fontSize = 12.5.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                        singleLine = true,
                        maxLines = 1,
                        modifier = Modifier
                            .weight(1f)
                            .bringIntoViewRequester(testBringIntoView)
                            .onFocusEvent { focusState ->
                                if (focusState.isFocused) {
                                    coroutineScope.launch {
                                        delay(200L)
                                        testBringIntoView.bringIntoView()
                                    }
                                }
                            },
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF140E29),
                            unfocusedContainerColor = Color(0xFF140E29),
                            focusedTextColor = NordicColors.TextPrimary,
                            unfocusedTextColor = NordicColors.TextPrimary,
                            focusedBorderColor = NordicColors.AccentPrimary,
                            unfocusedBorderColor = NordicColors.HairlineBorder
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Tactile3DButton(
                        onClick = {
                            if (testInput.isNotBlank()) {
                                val isBlocked = NativeBlockerBridge.isUrlBlocked(testInput.trim())
                                testResult = if (isBlocked) "BLOCKED" else "ALLOWED"
                            }
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "Test",
                            color = Color.White,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            if (testResult != null) {
                Spacer(modifier = Modifier.height(12.dp))
                val isBlk = testResult == "BLOCKED"
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isBlk) Color(0xFF2A0D18) else Color(0xFF0F261C))
                        .border(
                            1.dp,
                            if (isBlk) NordicColors.AlertMuted else NordicColors.SuccessMuted,
                            RoundedCornerShape(10.dp)
                        )
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isBlk) CyberIcons.Ban else CyberIcons.Check,
                            contentDescription = null,
                            tint = if (isBlk) NordicColors.AlertMuted else NordicColors.SuccessMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isBlk) "$testInput is BLOCKED" else "$testInput is ALLOWED",
                            color = if (isBlk) NordicColors.AlertMuted else NordicColors.SuccessMuted,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
private fun WebShieldRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    pendingUnlockUntil: Long,
    onCancelUnlock: () -> Unit,
    onCheckedChange: (Boolean) -> Unit
) {
    val isPending = pendingUnlockUntil > 0L
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f).padding(end = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RaisedSquirclePedestal(size = 36.dp) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = NordicColors.AccentPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = title,
                        color = NordicColors.TextPrimary,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        color = NordicColors.TextSecondary,
                        fontSize = 11.5.sp,
                        lineHeight = 15.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
            NordicSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
        if (isPending) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                LiveCountdownBadge(
                    targetEpochMs = pendingUnlockUntil,
                    prefix = "Unlocking in ",
                    tone = BadgeTone.WARN,
                    small = true
                )
                Spacer(modifier = Modifier.width(6.dp))
                IconButton(
                    onClick = onCancelUnlock,
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        imageVector = CyberIcons.Close,
                        contentDescription = "Cancel",
                        tint = NordicColors.AlertMuted,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}
