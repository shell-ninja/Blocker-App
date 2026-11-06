package com.blocker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blocker.NativeBlockerBridge
import com.blocker.data.BlockerRepository
import com.blocker.ui.components.*
import com.blocker.ui.theme.*

@Composable
fun BlocklistScreen(
    repo: BlockerRepository,
    modifier: Modifier = Modifier
) {
    val domains by repo.blockedDomains.collectAsState()
    val keywords by repo.blockedKeywords.collectAsState()
    val scrollState = rememberScrollState()

    var domainInput by remember { mutableStateOf("") }
    var keywordInput by remember { mutableStateOf("") }
    var testInput by remember { mutableStateOf("") }
    var testResult by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CyberBg)
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
        GlowCard(modifier = Modifier.fillMaxWidth()) {
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
                            .background(CyberAccentWell)
                            .border(1.dp, CyberAccent.copy(alpha = 0.4f), RoundedCornerShape(11.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = CyberIcons.Shield,
                            contentDescription = null,
                            tint = Color(0xFFC084FC),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "Default block list",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "9,357 sites, 62 keywords and 4 web endings active",
                            color = CyberTextSub,
                            fontSize = 11.5.sp
                        )
                    }
                }

                StatusPillBadge(label = "Active", tone = BadgeTone.OK, small = true)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Custom Domain Adder
        GlowCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Add Custom Blocked Website",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = "Enter domain name (e.g. example.com or badsite.org)",
                color = CyberTextSub,
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
                    placeholder = { Text("domain.com", color = CyberTextSub, fontSize = 13.sp) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CyberCardSurface,
                        unfocusedContainerColor = CyberCardSurface,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = CyberAccent,
                        unfocusedBorderColor = CyberCardBorder
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
                    color = CyberTextSub,
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
                                .background(CyberCardSurface)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = dom, color = Color.White, fontSize = 13.sp)
                            Icon(
                                imageVector = CyberIcons.Delete,
                                contentDescription = null,
                                tint = CyberDanger,
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable { repo.removeDomain(dom) }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Custom Keyword Adder
        GlowCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Add Custom Restricted Keyword",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = "Restricts matching web pages, search terms, and URL paths",
                color = CyberTextSub,
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
                    placeholder = { Text("keyword or phrase", color = CyberTextSub, fontSize = 13.sp) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CyberCardSurface,
                        unfocusedContainerColor = CyberCardSurface,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = CyberAccent,
                        unfocusedBorderColor = CyberCardBorder
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
                    color = CyberTextSub,
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
                                .background(CyberCardSurface)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = kw, color = Color.White, fontSize = 13.sp)
                            Icon(
                                imageVector = CyberIcons.Delete,
                                contentDescription = null,
                                tint = CyberDanger,
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable { repo.removeKeyword(kw) }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // URL Verification Sandbox
        GlowCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Block Engine Simulator",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = "Test a URL against the binary C++ engine and custom rules",
                color = CyberTextSub,
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
                    placeholder = { Text("https://example.com/page", color = CyberTextSub, fontSize = 13.sp) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CyberCardSurface,
                        unfocusedContainerColor = CyberCardSurface,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = CyberAccent,
                        unfocusedBorderColor = CyberCardBorder
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
                        .background(if (isBlk) CyberDangerSoft else CyberOkSoft)
                        .border(1.dp, if (isBlk) CyberDanger else CyberOk, RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isBlk) CyberIcons.Ban else CyberIcons.Check,
                            contentDescription = null,
                            tint = if (isBlk) CyberDanger else CyberOkGlow,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isBlk) "$testInput is BLOCKED" else "$testInput is ALLOWED",
                            color = if (isBlk) CyberDanger else CyberOkGlow,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
