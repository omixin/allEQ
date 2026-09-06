package com.omix.alleq.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

enum class UiScale(val scale: Float, val title: String) {
    COMPACT(0.85f, "Compact"),
    STANDARD(1.00f, "Standard"),
    LARGE(1.15f, "Large")
}

enum class AccentTheme(val id: String, val title: String, val darkColor: Color, val lightColor: Color) {
    GREEN("green", "Green", Color(0xFF10B981), Color(0xFF059669)),
    CYAN("cyan", "Cyan", Color(0xFF06B6D4), Color(0xFF0891B2)),
    ORANGE("orange", "Orange", Color(0xFFF97316), Color(0xFFEA580C)),
    PURPLE("purple", "Purple", Color(0xFF8B5CF6), Color(0xFF7C3AED)),
    MATERIAL_YOU("material_you", "Material You", Color.Unspecified, Color.Unspecified),
    CUSTOM("custom", "Custom", Color(0xFFE91E63), Color(0xFFD81B60))
}

data class EqColors(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val border: Color,
    val borderSubtle: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accent: Color,
    val cardBg: Color,
    val cardBorder: Color,
    val sheetBg: Color,
    val sheetCardBg: Color,
    val faderTrack: Color,
    val faderLevelLine: Color,
    val badgeBg: Color
)

val DarkEqColors = EqColors(
    isDark = true,
    background = Color(0xFF0A0A0A),
    surface = Color(0xFF141414),
    surfaceElevated = Color(0xFF1E1E1E),
    border = Color(0xFF282828),
    borderSubtle = Color(0xFF1E1E1E),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFFAAAAAA),
    textMuted = Color(0xFF666666),
    accent = Color(0xFF4CAF50),
    cardBg = Color(0xFF141414),
    cardBorder = Color(0xFF1E1E1E),
    sheetBg = Color(0xFF121212),
    sheetCardBg = Color(0xFF181818),
    faderTrack = Color(0xFF161616),
    faderLevelLine = Color.White,
    badgeBg = Color(0xFF222222)
)

val LightEqColors = EqColors(
    isDark = false,
    background = Color(0xFFF4F5F7),
    surface = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFEBEFF4),
    border = Color(0xFFDDE1E6),
    borderSubtle = Color(0xFFE5E9EE),
    textPrimary = Color(0xFF121212),
    textSecondary = Color(0xFF555555),
    textMuted = Color(0xFF888888),
    accent = Color(0xFF2E7D32),
    cardBg = Color(0xFFFFFFFF),
    cardBorder = Color(0xFFE2E5E9),
    sheetBg = Color(0xFFFFFFFF),
    sheetCardBg = Color(0xFFF6F7F9),
    faderTrack = Color(0xFFE8ECEF),
    faderLevelLine = Color(0xFF111111),
    badgeBg = Color(0xFFE8ECEF)
)

val LocalEqColors = staticCompositionLocalOf { DarkEqColors }