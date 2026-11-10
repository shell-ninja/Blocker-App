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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
    val isLocked = protection.isActive || focus.active
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    val domainBringIntoView = remember { BringIntoViewRequester() }
    val keywordBringIntoView = remember { BringIntoViewRequester() }
    val testBringIntoView = remember { BringIntoViewRequester() }

    var domainInput by remember { mutableStateOf("") }
    var keywordInput by remember { mutableStateOf("") }
    var testInput by remember { mutableStateOf("") }
    var testResult by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NordicColors.CanvasBg)
            .imePadding()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        SectionHeader(
            title = "Web & keywords",
            icon = CyberIcons.Web,
            subtitle = "Custom websites, domain patterns and restricted keyword phrases"
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Categories: Default block list
        NordicMicroGridCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(NordicColors.CardSurfaceHero)
                            .border(1.dp, NordicColors.HairlineBorder, RoundedCornerShape(11.dp)),
                        contentAlignment = Alignment.Center
                    ) {
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
                            text = "Default block list",
                            color = NordicColors.TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "9,357 sites, 62 keywords and 4 web endings active",
                            color = NordicColors.TextSecondary,
                            fontSize = 11.5.sp
                        )
                    }
                }

                StatusPillBadge(label = "Active", tone = BadgeTone.OK, small = true)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Custom Domain Adder
        NordicMicroGridCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Add Custom Blocked Website",
                color = NordicColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = "Enter domain name (e.g. example.com or badsite.org)",
                color = NordicColors.TextSecondary,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = domainInput,
                    onValueChange = { domainInput = it },
                    placeholder = { Text("domain.com", color = NordicColors.TextSecondary, fontSize = 13.sp) },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .bringIntoViewRequester(domainBringIntoView)
                        .onFocusEvent { focusState ->
                            if (focusState.isFocused) {
                                coroutineScope.launch {
                                    delay(200L)
                                    domainBringIntoView.bringIntoView()
                                }
                            }
                        },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = NordicColors.CardSurfaceHero,
                        unfocusedContainerColor = NordicColors.CardSurfaceHero,
                        focusedTextColor = NordicColors.TextPrimary,
                        unfocusedTextColor = NordicColors.TextPrimary,
                        focusedBorderColor = NordicColors.AccentPrimary,
                        unfocusedBorderColor = NordicColors.HairlineBorder
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Btn(
                    label = "Add",
                    icon = CyberIcons.Add,
                    onClick = {
                        if (domainInput.isNotBlank()) {
                            repo.addDomain(domainInput.trim())
                            domainInput = ""
                        }
                    }
                )
            }

            if (domains.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Blocked Domains (${domains.size})",
                    color = NordicColors.TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    domains.forEach { dom ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(NordicColors.CardSurfaceHero)
                                .border(1.dp, NordicColors.HairlineBorder, RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = dom, color = NordicColors.TextPrimary, fontSize = 13.sp)
                            Icon(
                                imageVector = CyberIcons.Delete,
                                contentDescription = null,
                                tint = if (isLocked) NordicColors.TextSecondary.copy(alpha = 0.35f) else NordicColors.AlertMuted,
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable(enabled = !isLocked) { repo.removeDomain(dom) }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Custom Keyword Adder
        NordicMicroGridCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Add Custom Restricted Keyword",
                color = NordicColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = "Restricts matching web pages, search terms, and URL paths",
                color = NordicColors.TextSecondary,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = keywordInput,
                    onValueChange = { keywordInput = it },
                    placeholder = { Text("keyword or phrase", color = NordicColors.TextSecondary, fontSize = 13.sp) },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .bringIntoViewRequester(keywordBringIntoView)
                        .onFocusEvent { focusState ->
                            if (focusState.isFocused) {
                                coroutineScope.launch {
                                    delay(200L)
                                    keywordBringIntoView.bringIntoView()
                                }
                            }
                        },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = NordicColors.CardSurfaceHero,
                        unfocusedContainerColor = NordicColors.CardSurfaceHero,
                        focusedTextColor = NordicColors.TextPrimary,
                        unfocusedTextColor = NordicColors.TextPrimary,
                        focusedBorderColor = NordicColors.AccentPrimary,
                        unfocusedBorderColor = NordicColors.HairlineBorder
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Btn(
                    label = "Add",
                    icon = CyberIcons.Add,
                    onClick = {
                        if (keywordInput.isNotBlank()) {
                            repo.addKeyword(keywordInput.trim())
                            keywordInput = ""
                        }
                    }
                )
            }

            if (keywords.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Restricted Keywords (${keywords.size})",
                    color = NordicColors.TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    keywords.forEach { kw ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(NordicColors.CardSurfaceHero)
                                .border(1.dp, NordicColors.HairlineBorder, RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = kw, color = NordicColors.TextPrimary, fontSize = 13.sp)
                            Icon(
                                imageVector = CyberIcons.Delete,
                                contentDescription = null,
                                tint = if (isLocked) NordicColors.TextSecondary.copy(alpha = 0.35f) else NordicColors.AlertMuted,
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable(enabled = !isLocked) { repo.removeKeyword(kw) }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // URL Verification Sandbox
        NordicMicroGridCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Block Engine Simulator",
                color = NordicColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = "Test a URL against the binary C++ engine and custom rules",
                color = NordicColors.TextSecondary,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

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
                    placeholder = { Text("https://example.com/page", color = NordicColors.TextSecondary, fontSize = 13.sp) },
                    singleLine = true,
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
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = NordicColors.CardSurfaceHero,
                        unfocusedContainerColor = NordicColors.CardSurfaceHero,
                        focusedTextColor = NordicColors.TextPrimary,
                        unfocusedTextColor = NordicColors.TextPrimary,
                        focusedBorderColor = NordicColors.AccentPrimary,
                        unfocusedBorderColor = NordicColors.HairlineBorder
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Btn(
                    label = "Test",
                    icon = CyberIcons.Search,
                    onClick = {
                        if (testInput.isNotBlank()) {
                            val isBlocked = NativeBlockerBridge.isUrlBlocked(testInput.trim())
                            testResult = if (isBlocked) "BLOCKED" else "ALLOWED"
                        }
                    }
                )
            }

            if (testResult != null) {
                Spacer(modifier = Modifier.height(12.dp))
                val isBlk = testResult == "BLOCKED"
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(NordicColors.CardSurfaceHero)
                        .border(
                            1.dp,
                            if (isBlk) NordicColors.AlertMuted else NordicColors.AccentPrimary,
                            RoundedCornerShape(10.dp)
                        )
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isBlk) CyberIcons.Ban else CyberIcons.Check,
                            contentDescription = null,
                            tint = if (isBlk) NordicColors.AlertMuted else NordicColors.AccentPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isBlk) "$testInput is BLOCKED" else "$testInput is ALLOWED",
                            color = if (isBlk) NordicColors.AlertMuted else NordicColors.AccentPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(140.dp))
    }
}
