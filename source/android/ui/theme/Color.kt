package com.blocker.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

val CyberBg = Color(0xFF070913)
val CyberBgAlt = Color(0xFF0E1322)
val CyberCard = Color(0xFF0E1322)
val CyberCardSurface = Color(0xFF121727)
val CyberCardBorder = Color(0xFF1E2640)
val CyberCardBorderGlow = Color(0x597C3AED)
val CyberCardBorderSpecular = Color(0x33A78BFA)

val CyberAmbientTop = Color(0xFF130E2A)
val CyberAmbientMid = Color(0xFF080B16)
val CyberAmbientBottom = Color(0xFF04060C)

val CyberCardTop = Color(0xFF13192B)
val CyberCardBottom = Color(0xFF0A0D1A)

val CyberMeshBackground = Brush.verticalGradient(
    listOf(CyberAmbientTop, CyberAmbientMid, CyberAmbientBottom)
)

val CyberCardGradient = Brush.verticalGradient(
    listOf(CyberCardTop, CyberCardBottom)
)

val CyberAccent = Color(0xFF7C3AED)
val CyberAccentGlow = Color(0xFF9333EA)
val CyberAccentSoft = Color(0x297C3AED)
val CyberAccentWell = Color(0xFF18122B)

val CyberOk = Color(0xFF10B981)
val CyberOkGlow = Color(0xFF00E676)
val CyberOkSoft = Color(0x2410B981)
val CyberOkWell = Color(0xFF07241A)

val CyberWarn = Color(0xFFF59E0B)
val CyberWarnSoft = Color(0x24F59E0B)

val CyberDanger = Color(0xFFEF4444)
val CyberDangerSoft = Color(0x24EF4444)

val CyberText = Color(0xFFFFFFFF)
val CyberTextSub = Color(0xFF8B97B0)
val CyberTextMuted = Color(0xFF4B5568)
