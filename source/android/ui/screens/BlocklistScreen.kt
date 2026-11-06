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
import com.blocker.ui.components.CyberCard
import com.blocker.ui.components.GlowButton
import com.blocker.ui.components.SectionHeader
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
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        SectionHeader(
            title = "Web & Domain Filters",
            subtitle = "Custom websites, domain patterns and restricted keyword phrases",
            icon = "🌐"
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Custom Domain Adder
        CyberCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Add Custom Blocked Website",
                color = CyberText,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
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
                        focusedTextColor = CyberText,
                        unfocusedTextColor = CyberText,
                        focusedBorderColor = CyberAccent,
                        unfocusedBorderColor = CyberCardBorder
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                GlowButton(
                    text = "Add",
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
                            Text(text = dom, color = CyberText, fontSize = 13.sp)
                            Text(
                                text = "✕",
                                color = CyberDanger,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable { repo.removeDomain(dom) }
                                    .padding(4.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Custom Keyword Adder
        CyberCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Add Custom Restricted Keyword",
                color = CyberText,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
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
                        focusedTextColor = CyberText,
                        unfocusedTextColor = CyberText,
                        focusedBorderColor = CyberAccent,
                        unfocusedBorderColor = CyberCardBorder
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                GlowButton(
                    text = "Add",
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
                            Text(text = kw, color = CyberText, fontSize = 13.sp)
                            Text(
                                text = "✕",
                                color = CyberDanger,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable { repo.removeKeyword(kw) }
                                    .padding(4.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // URL Verification Sandbox
        CyberCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Block Engine Simulator",
                color = CyberText,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
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
                        focusedTextColor = CyberText,
                        unfocusedTextColor = CyberText,
                        focusedBorderColor = CyberAccent,
                        unfocusedBorderColor = CyberCardBorder
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                GlowButton(
                    text = "Test",
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
                    Text(
                        text = if (isBlk) "⛔ $testInput is BLOCKED" else "✅ $testInput is ALLOWED",
                        color = if (isBlk) CyberDanger else CyberOkGlow,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
