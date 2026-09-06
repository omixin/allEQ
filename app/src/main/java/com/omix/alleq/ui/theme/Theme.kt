package com.omix.alleq.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

@Composable
fun AllEqTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accentTheme: AccentTheme = AccentTheme.GREEN,
    customAccentColor: Color = Color(0xFFE91E63),
    uiScale: UiScale = UiScale.STANDARD,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val hasDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val dynamicColor = (accentTheme == AccentTheme.MATERIAL_YOU && hasDynamic)

    val colorScheme = when {
        dynamicColor -> if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val baseAccent = when (accentTheme) {
        AccentTheme.GREEN -> if (darkTheme) AccentTheme.GREEN.darkColor else AccentTheme.GREEN.lightColor
        AccentTheme.CYAN -> if (darkTheme) AccentTheme.CYAN.darkColor else AccentTheme.CYAN.lightColor
        AccentTheme.ORANGE -> if (darkTheme) AccentTheme.ORANGE.darkColor else AccentTheme.ORANGE.lightColor
        AccentTheme.PURPLE -> if (darkTheme) AccentTheme.PURPLE.darkColor else AccentTheme.PURPLE.lightColor
        AccentTheme.MATERIAL_YOU -> {
            if (hasDynamic) {
                val dyn = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                dyn.primary
            } else {
                if (darkTheme) AccentTheme.GREEN.darkColor else AccentTheme.GREEN.lightColor
            }
        }
        AccentTheme.CUSTOM -> customAccentColor
    }

    val baseEqColors = if (darkTheme) DarkEqColors else LightEqColors
    val eqColors = baseEqColors.copy(accent = baseAccent, faderLevelLine = baseAccent)

    val currentDensity = LocalDensity.current
    val scaledDensity = Density(
        density = currentDensity.density * uiScale.scale,
        fontScale = currentDensity.fontScale * uiScale.scale
    )

    CompositionLocalProvider(
        LocalDensity provides scaledDensity,
        LocalEqColors provides eqColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}