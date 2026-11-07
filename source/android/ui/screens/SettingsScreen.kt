package com.blocker.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.blocker.BlockerStore
import com.blocker.R
import com.blocker.data.BlockerRepository
import com.blocker.ui.components.*
import com.blocker.ui.theme.*

@Composable
fun SettingsScreen(
    repo: BlockerRepository,
    modifier: Modifier = Modifier
) {
    val protection by repo.protectionState.collectAsState()
    val securityLock by repo.securityLockState.collectAsState()
    val scrollState = rememberScrollState()

    var showSetPasswordDialog by remember { mutableStateOf(false) }
    var showDisablePasswordDialog by remember { mutableStateOf(false) }
    var showChangePasswordDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CyberMeshBackground)
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        SectionHeader(
            title = "Settings & Security",
            icon = CyberIcons.Settings,
            subtitle = "Parental safety, 24-hour covenant lock, and hardware protection"
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 1. Strict 24-Hour Delay Period Card (Others removed per user request)
        GlowCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f).padding(end = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(CyberAccentWell)
                            .border(1.dp, CyberAccent.copy(alpha = 0.45f), RoundedCornerShape(11.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = CyberIcons.Clock,
                            contentDescription = null,
                            tint = Color(0xFFC084FC),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Protection Delay Period",
                            color = Color.White,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "24-Hour Fixed Covenant Timer",
                            color = Color(0xFFA78BFA),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                StatusPillBadge(label = "24h Locked", tone = BadgeTone.ACCENT, small = true)
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Disarming protection or removing restrictions enforces a strict 24-hour waiting countdown. Instant bypass or shorter timers are permanently removed.",
                color = CyberTextSub,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 2. Parental Security & App Lock (User requested feature)
        SectionHeader(
            title = "Parental & Child Safety Lock",
            icon = CyberIcons.Lock,
            subtitle = "Restricts children from opening the app, modifying filters, or changing settings"
        )

        Spacer(modifier = Modifier.height(10.dp))

        GlowCard(
            modifier = Modifier.fillMaxWidth(),
            active = securityLock.isParentLockEnabled,
            tone = if (securityLock.isParentLockEnabled) CardTone.ACCENT else CardTone.DEFAULT
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f).padding(end = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(CyberAccentWell)
                            .border(1.dp, CyberAccent.copy(alpha = 0.45f), RoundedCornerShape(11.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = CyberIcons.Lock,
                            contentDescription = null,
                            tint = Color(0xFFC084FC),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Master Password Lock",
                            color = Color.White,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (securityLock.isParentLockEnabled) "App locked with strong password" else "Requires parent password to open app",
                            color = if (securityLock.isParentLockEnabled) CyberOk else CyberTextSub,
                            fontSize = 12.sp
                        )
                    }
                }
                CyberSwitch(
                    checked = securityLock.isParentLockEnabled,
                    onCheckedChange = { enable ->
                        if (enable) {
                            showSetPasswordDialog = true
                        } else {
                            showDisablePasswordDialog = true
                        }
                    }
                )
            }

            if (securityLock.isParentLockEnabled) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Btn(
                        label = "Change Password",
                        icon = CyberIcons.Lock,
                        onClick = { showChangePasswordDialog = true },
                        kind = BtnKind.SECONDARY,
                        modifier = Modifier.weight(1f)
                    )
                    Btn(
                        label = "Lock App Now",
                        icon = CyberIcons.Lock,
                        onClick = { repo.lockApp() },
                        kind = BtnKind.GHOST,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Hardware & System Defense
        SectionHeader(
            title = "Hardware & Admin Protection",
            icon = CyberIcons.ShieldCheck,
            subtitle = "Low-level system enforcement preventing circumvention"
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Prevent Power Off
        GlowCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(CyberAccentWell),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = CyberIcons.Power,
                            contentDescription = null,
                            tint = Color(0xFFA78BFA),
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Prevent Power Off",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Blocks device power down attempts while protected",
                            color = CyberTextSub,
                            fontSize = 11.5.sp
                        )
                    }
                }
                CyberSwitch(
                    checked = protection.preventPowerOff,
                    onCheckedChange = { repo.setPreventPowerOff(it) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Banking Mode
        GlowCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(CyberAccentWell),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = CyberIcons.Bank,
                            contentDescription = null,
                            tint = Color(0xFFA78BFA),
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Auto-Pause for Banking Apps",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Auto-exempts financial apps (bKash, Nagad) to pass security checks",
                            color = CyberTextSub,
                            fontSize = 11.5.sp
                        )
                    }
                }
                CyberSwitch(
                    checked = protection.isAutoPauseBanking,
                    onCheckedChange = { repo.setAutoPauseBanking(it) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 4. Popup Language
        SectionHeader(
            title = "Warning Language",
            icon = CyberIcons.Web,
            subtitle = "Language used in religious reminders and covenant alerts"
        )

        Spacer(modifier = Modifier.height(10.dp))

        GlowCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val isBn = protection.popupLanguage == "bn"
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (!isBn) CyberAccentSoft else CyberCardSurface)
                        .border(1.dp, if (!isBn) CyberAccent else CyberCardBorder, RoundedCornerShape(12.dp))
                        .clickable { repo.setPopupLanguage("en") }
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "English",
                        color = if (!isBn) Color.White else CyberTextSub,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isBn) CyberAccentSoft else CyberCardSurface)
                        .border(1.dp, if (isBn) CyberAccent else CyberCardBorder, RoundedCornerShape(12.dp))
                        .clickable { repo.setPopupLanguage("bn") }
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "বাংলা (Bengali)",
                        color = if (isBn) Color.White else CyberTextSub,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 5. Engine Architecture & Footer
        GlowCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Engine Architecture",
                color = Color.White,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "UI Framework", color = CyberTextSub, fontSize = 12.sp)
                Text(text = "100% Pure Jetpack Compose", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "Core Engine", color = CyberTextSub, fontSize = 12.sp)
                Text(text = "C++20 NDK (Zero-GC)", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "Version", color = CyberTextSub, fontSize = 12.sp)
                Text(text = "v1.0.6.7 (Pure Native)", color = CyberAccentGlow, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = R.mipmap.ic_launcher),
                contentDescription = "Blocker Icon",
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(5.dp))
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Blocker v1.0.6.7 (build 88)",
                color = CyberTextSub,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    // --- Dialogs for Password Management ---

    if (showSetPasswordDialog) {
        SetPasswordDialog(
            title = "Set Master Password",
            subtitle = "Create a strong master password to lock Blocker controls and protect against bypass",
            onDismiss = { showSetPasswordDialog = false },
            onConfirm = { pass ->
                repo.setParentPassword(pass)
                showSetPasswordDialog = false
            }
        )
    }

    if (showDisablePasswordDialog) {
        ConfirmPasswordDialog(
            title = "Disable Parental Lock",
            subtitle = "Enter your current master password to remove password protection",
            confirmLabel = "Disable Lock",
            onDismiss = { showDisablePasswordDialog = false },
            onConfirm = { currentPass ->
                val ok = repo.setParentLockEnabled(false, currentPass)
                if (ok) showDisablePasswordDialog = false
                ok
            }
        )
    }

    if (showChangePasswordDialog) {
        ChangePasswordDialog(
            onDismiss = { showChangePasswordDialog = false },
            onConfirm = { currentPass, newPass ->
                val ok = repo.changeParentPassword(currentPass, newPass)
                if (ok) showChangePasswordDialog = false
                ok
            }
        )
    }
}

@Composable
private fun SetPasswordDialog(
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isPassVisible by remember { mutableStateOf(false) }

    val strength = remember(password) { BlockerStore.validatePasswordStrength(password) }
    val passwordsMatch = password.isNotEmpty() && password == confirmPassword

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF13192C), Color(0xFF090D1A))))
                .border(1.dp, CyberCardBorderGlow, RoundedCornerShape(22.dp))
                .padding(20.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(CyberAccentSoft),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = CyberIcons.Lock,
                            contentDescription = null,
                            tint = Color(0xFFC084FC),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    color = CyberTextSub,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Password Input
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    placeholder = { Text("Enter strong password", color = CyberTextSub, fontSize = 13.sp) },
                    visualTransformation = if (isPassVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isPassVisible = !isPassVisible }) {
                            Icon(
                                imageVector = if (isPassVisible) CyberIcons.EyeOff else CyberIcons.Eye,
                                contentDescription = null,
                                tint = CyberTextSub
                            )
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CyberCardSurface,
                        unfocusedContainerColor = CyberCardSurface,
                        focusedBorderColor = CyberAccent,
                        unfocusedBorderColor = CyberCardBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Confirm Password Input
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    placeholder = { Text("Confirm password", color = CyberTextSub, fontSize = 13.sp) },
                    visualTransformation = if (isPassVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CyberCardSurface,
                        unfocusedContainerColor = CyberCardSurface,
                        focusedBorderColor = if (confirmPassword.isNotEmpty() && !passwordsMatch) CyberDanger else CyberAccent,
                        unfocusedBorderColor = CyberCardBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Strong Password Requirements (User specified)
                Text(
                    text = "STRONG PASSWORD REQUIREMENTS:",
                    color = CyberTextSub,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.4.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                PasswordCheckItem("Minimum 8 characters", strength.minLength)
                PasswordCheckItem("Uppercase letter (A-Z)", strength.hasUpper)
                PasswordCheckItem("Lowercase letter (a-z)", strength.hasLower)
                PasswordCheckItem("Number (0-9)", strength.hasDigit)
                PasswordCheckItem("Special character (!@#$%...)", strength.hasSpecial)
                if (confirmPassword.isNotEmpty()) {
                    PasswordCheckItem("Passwords match", passwordsMatch)
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Btn(
                        label = "Cancel",
                        onClick = onDismiss,
                        kind = BtnKind.GHOST,
                        modifier = Modifier.weight(1f)
                    )
                    Btn(
                        label = "Save Password",
                        icon = CyberIcons.Check,
                        enabled = strength.isValid && passwordsMatch,
                        onClick = { onConfirm(password) },
                        kind = BtnKind.PRIMARY,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun PasswordCheckItem(label: String, passed: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(if (passed) CyberOkSoft else CyberCardSurface)
                .border(1.dp, if (passed) CyberOk else CyberCardBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (passed) {
                Icon(
                    imageVector = CyberIcons.Check,
                    contentDescription = null,
                    tint = CyberOk,
                    modifier = Modifier.size(11.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            color = if (passed) Color.White else CyberTextSub,
            fontSize = 12.sp,
            fontWeight = if (passed) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun ConfirmPasswordDialog(
    title: String,
    subtitle: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Boolean
) {
    var password by remember { mutableStateOf("") }
    var isPassVisible by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF13192C), Color(0xFF090D1A))))
                .border(1.dp, CyberCardBorderGlow, RoundedCornerShape(22.dp))
                .padding(20.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    color = CyberTextSub,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        if (error) error = false
                    },
                    placeholder = { Text("Enter current password", color = CyberTextSub, fontSize = 13.sp) },
                    visualTransformation = if (isPassVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isPassVisible = !isPassVisible }) {
                            Icon(
                                imageVector = if (isPassVisible) CyberIcons.EyeOff else CyberIcons.Eye,
                                contentDescription = null,
                                tint = CyberTextSub
                            )
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CyberCardSurface,
                        unfocusedContainerColor = CyberCardSurface,
                        focusedBorderColor = CyberAccent,
                        unfocusedBorderColor = CyberCardBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                if (error) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Incorrect master password. Please try again.",
                        color = CyberDanger,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Btn(
                        label = "Cancel",
                        onClick = onDismiss,
                        kind = BtnKind.GHOST,
                        modifier = Modifier.weight(1f)
                    )
                    Btn(
                        label = confirmLabel,
                        icon = CyberIcons.Check,
                        enabled = password.isNotEmpty(),
                        onClick = {
                            val ok = onConfirm(password)
                            if (!ok) error = true
                        },
                        kind = BtnKind.DANGER,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ChangePasswordDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Boolean
) {
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmNewPassword by remember { mutableStateOf("") }
    var isPassVisible by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val strength = remember(newPassword) { BlockerStore.validatePasswordStrength(newPassword) }
    val passwordsMatch = newPassword.isNotEmpty() && newPassword == confirmNewPassword

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF13192C), Color(0xFF090D1A))))
                .border(1.dp, CyberCardBorderGlow, RoundedCornerShape(22.dp))
                .padding(20.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Change Master Password",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Enter current password and choose a new strong master password",
                    color = CyberTextSub,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Current Password
                OutlinedTextField(
                    value = currentPassword,
                    onValueChange = {
                        currentPassword = it
                        errorMsg = null
                    },
                    placeholder = { Text("Current master password", color = CyberTextSub, fontSize = 13.sp) },
                    visualTransformation = if (isPassVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CyberCardSurface,
                        unfocusedContainerColor = CyberCardSurface,
                        focusedBorderColor = CyberAccent,
                        unfocusedBorderColor = CyberCardBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // New Password
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = {
                        newPassword = it
                        errorMsg = null
                    },
                    placeholder = { Text("New strong password", color = CyberTextSub, fontSize = 13.sp) },
                    visualTransformation = if (isPassVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isPassVisible = !isPassVisible }) {
                            Icon(
                                imageVector = if (isPassVisible) CyberIcons.EyeOff else CyberIcons.Eye,
                                contentDescription = null,
                                tint = CyberTextSub
                            )
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CyberCardSurface,
                        unfocusedContainerColor = CyberCardSurface,
                        focusedBorderColor = CyberAccent,
                        unfocusedBorderColor = CyberCardBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Confirm New Password
                OutlinedTextField(
                    value = confirmNewPassword,
                    onValueChange = {
                        confirmNewPassword = it
                        errorMsg = null
                    },
                    placeholder = { Text("Confirm new password", color = CyberTextSub, fontSize = 13.sp) },
                    visualTransformation = if (isPassVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CyberCardSurface,
                        unfocusedContainerColor = CyberCardSurface,
                        focusedBorderColor = if (confirmNewPassword.isNotEmpty() && !passwordsMatch) CyberDanger else CyberAccent,
                        unfocusedBorderColor = CyberCardBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                PasswordCheckItem("Minimum 8 characters", strength.minLength)
                PasswordCheckItem("Uppercase letter (A-Z)", strength.hasUpper)
                PasswordCheckItem("Lowercase letter (a-z)", strength.hasLower)
                PasswordCheckItem("Number (0-9)", strength.hasDigit)
                PasswordCheckItem("Special character (!@#$%...)", strength.hasSpecial)

                if (errorMsg != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMsg!!,
                        color = CyberDanger,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Btn(
                        label = "Cancel",
                        onClick = onDismiss,
                        kind = BtnKind.GHOST,
                        modifier = Modifier.weight(1f)
                    )
                    Btn(
                        label = "Update Password",
                        icon = CyberIcons.Check,
                        enabled = currentPassword.isNotEmpty() && strength.isValid && passwordsMatch,
                        onClick = {
                            val ok = onConfirm(currentPassword, newPassword)
                            if (!ok) {
                                errorMsg = "Incorrect current password"
                            }
                        },
                        kind = BtnKind.PRIMARY,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
