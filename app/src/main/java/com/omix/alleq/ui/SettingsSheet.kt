package com.omix.alleq.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omix.alleq.ui.theme.AccentTheme
import com.omix.alleq.ui.theme.BenzinFontFamily
import com.omix.alleq.ui.theme.LocalEqColors
import com.omix.alleq.ui.theme.UiScale
import com.omix.alleq.ui.theme.WixFontFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    onDismiss: () -> Unit,
    shizukuRunning: Boolean,
    shizukuHasPermission: Boolean,
    whitelistApplied: Boolean,
    allPresetsCount: Int,
    currentUiScale: UiScale,
    onUiScaleChanged: (UiScale) -> Unit,
    currentAccentTheme: AccentTheme,
    onAccentThemeChanged: (AccentTheme) -> Unit,
    customAccentColor: Color,
    onCustomColorChanged: (Color) -> Unit,
    onRequestShizuku: () -> Unit,
    onApplyWhitelist: () -> Unit,
    onOpenPresetManager: () -> Unit,
    onCopyDiagnostics: () -> Unit,
    onOpenTutorial: () -> Unit,
    onOpenGitHubIssues: () -> Unit = {}
) {
    val colors = LocalEqColors.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.sheetBg,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(if (colors.isDark) Color(0xFF333333) else Color(0xFFD4D8DE))
            )
        },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = "SETTINGS",
                    fontFamily = BenzinFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    letterSpacing = 0.5.sp,
                    color = colors.textPrimary
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            SettingsActionCard(
                title = "PRESET QUEUE & MANAGER",
                subtitle = androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.settings_preset_manager_desc),
                statusBadgeText = androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.preset_in_queue, allPresetsCount),
                statusBadgeColor = colors.accent,
                icon = Icons.AutoMirrored.Filled.QueueMusic,
                buttonText = androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.settings_preset_manager_btn),
                onButtonClick = onOpenPresetManager
            )

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = colors.cardBg),
                border = BorderStroke(1.dp, colors.cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.badgeBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "APPEARANCE & UI SCALE",
                                fontFamily = BenzinFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                letterSpacing = 0.5.sp,
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.settings_appearance_desc),
                                fontFamily = WixFontFamily,
                                fontSize = 11.sp,
                                color = colors.textSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "INTERFACE SCALE (DPI)",
                        fontFamily = BenzinFontFamily,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textSecondary,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        UiScale.values().forEach { scale ->
                            val isSelected = (currentUiScale == scale)
                            val scaleTitle = when (scale) {
                                UiScale.COMPACT -> androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.scale_compact)
                                UiScale.STANDARD -> androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.scale_standard)
                                UiScale.LARGE -> androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.scale_large)
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) colors.accent else colors.badgeBg,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onUiScaleChanged(scale) }
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val onAccentColor = if (colors.accent.luminance() < 0.45f) Color.White else Color.Black
                                    Text(
                                        text = scaleTitle,
                                        fontFamily = WixFontFamily,
                                        fontSize = 11.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) onAccentColor else colors.textPrimary
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "STUDIO ACCENT",
                        fontFamily = BenzinFontFamily,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textSecondary,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val rowThemes = listOf(
                            AccentTheme.GREEN to androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.accent_green),
                            AccentTheme.CYAN to androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.accent_cyan),
                            AccentTheme.ORANGE to androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.accent_orange)
                        )
                        rowThemes.forEach { (theme, label) ->
                            val isSelected = (currentAccentTheme == theme)
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) theme.darkColor.copy(alpha = 0.25f) else colors.badgeBg,
                                border = if (isSelected) BorderStroke(1.5.dp, theme.darkColor) else null,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onAccentThemeChanged(theme) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(9.dp)
                                            .clip(CircleShape)
                                            .background(theme.darkColor)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = label,
                                        fontFamily = WixFontFamily,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = colors.textPrimary,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val isPurpleSelected = (currentAccentTheme == AccentTheme.PURPLE)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isPurpleSelected) AccentTheme.PURPLE.darkColor.copy(alpha = 0.25f) else colors.badgeBg,
                            border = if (isPurpleSelected) BorderStroke(1.5.dp, AccentTheme.PURPLE.darkColor) else null,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onAccentThemeChanged(AccentTheme.PURPLE) }
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(9.dp)
                                        .clip(CircleShape)
                                        .background(AccentTheme.PURPLE.darkColor)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.accent_purple),
                                    fontFamily = WixFontFamily,
                                    fontSize = 10.5.sp,
                                    fontWeight = if (isPurpleSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = colors.textPrimary,
                                    maxLines = 1
                                )
                            }
                        }

                        val isMySelected = (currentAccentTheme == AccentTheme.MATERIAL_YOU)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isMySelected) colors.accent.copy(alpha = 0.25f) else colors.badgeBg,
                            border = if (isMySelected) BorderStroke(1.5.dp, colors.accent) else null,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onAccentThemeChanged(AccentTheme.MATERIAL_YOU) }
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = if (isMySelected) colors.accent else colors.textSecondary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.accent_system),
                                    fontFamily = WixFontFamily,
                                    fontSize = 10.5.sp,
                                    fontWeight = if (isMySelected) FontWeight.Bold else FontWeight.Normal,
                                    color = colors.textPrimary,
                                    maxLines = 1
                                )
                            }
                        }

                        val isCustomSelected = (currentAccentTheme == AccentTheme.CUSTOM)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isCustomSelected) customAccentColor.copy(alpha = 0.25f) else colors.badgeBg,
                            border = if (isCustomSelected) BorderStroke(1.5.dp, customAccentColor) else null,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onAccentThemeChanged(AccentTheme.CUSTOM) }
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(9.dp)
                                        .clip(CircleShape)
                                        .background(customAccentColor)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.accent_custom),
                                    fontFamily = WixFontFamily,
                                    fontSize = 11.sp,
                                    fontWeight = if (isCustomSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = colors.textPrimary,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    if (currentAccentTheme == AccentTheme.CUSTOM) {
                        Spacer(modifier = Modifier.height(12.dp))
                        val customPalette = listOf(
                            Color(0xFFE91E63), // Rose Pink
                            Color(0xFFFF5722), // Deep Orange
                            Color(0xFFFFEB3B), // Amber Yellow
                            Color(0xFF00E5FF), // Electric Cyan
                            Color(0xFF3F51B5), // Indigo
                            Color(0xFF9C27B0), // Purple
                            Color(0xFF009688)  // Teal
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            customPalette.forEach { palColor ->
                                val isChosen = (customAccentColor == palColor)
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .clickable { onCustomColorChanged(palColor) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .clip(CircleShape)
                                            .background(palColor)
                                            .border(
                                                width = if (isChosen) 2.5.dp else 1.dp,
                                                color = if (isChosen) colors.textPrimary else colors.border,
                                                shape = CircleShape
                                            )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            SettingsActionCard(
                title = "BACKGROUND PROTECTION",
                subtitle = androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.settings_bg_protection_desc),
                statusBadgeText = if (whitelistApplied) androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.badge_applied) else androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.badge_recommended),
                statusBadgeColor = if (whitelistApplied) Color(0xFF4CAF50) else Color(0xFF2196F3),
                icon = Icons.Default.Shield,
                buttonText = androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.btn_apply_bg_protection),
                onButtonClick = onApplyWhitelist
            )

            Spacer(modifier = Modifier.height(12.dp))

            SettingsActionCard(
                title = "SHIZUKU PRIVILEGES",
                subtitle = if (shizukuHasPermission) {
                    androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.shizuku_granted_desc)
                } else if (shizukuRunning) {
                    androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.shizuku_running_desc)
                } else {
                    androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.shizuku_not_running_desc)
                },
                statusBadgeText = if (shizukuHasPermission) androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.badge_active) else if (shizukuRunning) androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.badge_permission_needed) else androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.badge_offline),
                statusBadgeColor = if (shizukuHasPermission) Color(0xFF4CAF50) else Color(0xFFFF9800),
                icon = Icons.Default.Terminal,
                buttonText = if (!shizukuHasPermission) androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.btn_grant_shizuku) else null,
                onButtonClick = onRequestShizuku
            )

            Spacer(modifier = Modifier.height(12.dp))

            SettingsActionCard(
                title = "QUICK GUIDE & HELP",
                subtitle = androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.settings_tutorial_desc),
                statusBadgeText = androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.badge_guide),
                statusBadgeColor = Color(0xFF00BCD4),
                icon = Icons.AutoMirrored.Filled.HelpOutline,
                buttonText = androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.btn_open_tutorial),
                onButtonClick = onOpenTutorial
            )

            Spacer(modifier = Modifier.height(12.dp))

            SettingsActionCard(
                title = "OPEN SOURCE & FEEDBACK",
                subtitle = androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.settings_github_desc),
                statusBadgeText = androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.badge_github),
                statusBadgeColor = if (colors.isDark) Color(0xFFC9D1D9) else Color(0xFF24292E),
                icon = Icons.Default.Code,
                buttonText = androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.btn_report_issue),
                onButtonClick = onOpenGitHubIssues
            )

            Spacer(modifier = Modifier.height(12.dp))

            SettingsActionCard(
                title = "SYSTEM DIAGNOSTICS & LOGS",
                subtitle = androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.settings_diag_desc),
                statusBadgeText = androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.badge_logs),
                statusBadgeColor = Color(0xFF9C27B0),
                icon = Icons.Default.BugReport,
                buttonText = androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.btn_copy_diag),
                onButtonClick = onCopyDiagnostics
            )

            Spacer(modifier = Modifier.height(20.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "allEQ • DSP ENGINE",
                        fontFamily = BenzinFontFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        color = colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.footer_subtitle),
                        fontFamily = WixFontFamily,
                        fontSize = 11.sp,
                        color = colors.textMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SettingsActionCard(
    title: String,
    subtitle: String,
    statusBadgeText: String,
    statusBadgeColor: Color,
    icon: ImageVector,
    buttonText: String? = null,
    onButtonClick: (() -> Unit)? = null
) {
    val colors = LocalEqColors.current

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = colors.sheetCardBg),
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(if (colors.isDark) Color(0xFF222222) else Color(0xFFE8ECEF)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = colors.textPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = title,
                        fontFamily = BenzinFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        lineHeight = 13.sp,
                        letterSpacing = 0.3.sp,
                        color = colors.textPrimary
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(statusBadgeColor.copy(alpha = if (colors.isDark) 0.16f else 0.12f))
                        .border(1.dp, statusBadgeColor.copy(alpha = if (colors.isDark) 0.35f else 0.25f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 7.dp, vertical = 2.5.dp)
                ) {
                    Text(
                        text = statusBadgeText,
                        fontFamily = WixFontFamily,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusBadgeColor,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = subtitle,
                fontFamily = WixFontFamily,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = colors.textSecondary
            )

            if (buttonText != null && onButtonClick != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onButtonClick,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (colors.isDark) Color(0xFF282828) else Color(0xFFE2E6EA),
                        contentColor = colors.textPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = buttonText,
                        fontFamily = WixFontFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
