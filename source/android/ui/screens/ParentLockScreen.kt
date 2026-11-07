package com.blocker.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blocker.data.BlockerRepository
import com.blocker.ui.components.*
import com.blocker.ui.theme.*

@Composable
fun ParentLockScreen(
    repo: BlockerRepository,
    modifier: Modifier = Modifier
) {
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }

    // Pulsing aura animation for the lock icon
    val infiniteTransition = rememberInfiniteTransition(label = "lock_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    fun attemptUnlock() {
        if (password.isEmpty()) return
        val ok = repo.unlockAppWithPassword(password)
        if (!ok) {
            showError = true
            password = ""
        } else {
            showError = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CyberMeshBackground)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Glowing Lock Icon
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(CyberAccentSoft)
                    .border(1.5.dp, CyberAccent.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(CyberAccentWell)
                        .border(1.dp, CyberAccentGlow, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = CyberIcons.Lock,
                        contentDescription = "Parental Lock",
                        tint = Color(0xFFC084FC),
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Parental Security Lock",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.4).sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Enter master password to access Blocker controls",
                color = CyberTextSub,
                fontSize = 13.5.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Password Input Box
            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    if (showError) showError = false
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { attemptUnlock() }
                ),
                placeholder = {
                    Text(text = "Enter master password", color = CyberTextSub, fontSize = 13.5.sp)
                },
                trailingIcon = {
                    IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                        Icon(
                            imageVector = if (isPasswordVisible) CyberIcons.EyeOff else CyberIcons.Eye,
                            contentDescription = if (isPasswordVisible) "Hide password" else "Show password",
                            tint = CyberTextSub
                        )
                    }
                },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = CyberCardSurface,
                    unfocusedContainerColor = CyberCardSurface,
                    focusedBorderColor = CyberAccent,
                    unfocusedBorderColor = CyberCardBorder,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = CyberAccentGlow
                )
            )

            AnimatedVisibility(visible = showError) {
                Column {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Incorrect password. Please verify and try again.",
                        color = CyberDanger,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Btn(
                label = "Unlock Blocker",
                icon = CyberIcons.LockOpen,
                onClick = { attemptUnlock() },
                modifier = Modifier.fillMaxWidth(),
                kind = BtnKind.PRIMARY
            )
        }
    }
}
