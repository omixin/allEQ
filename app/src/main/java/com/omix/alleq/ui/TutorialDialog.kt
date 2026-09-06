package com.omix.alleq.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.omix.alleq.ui.theme.BenzinFontFamily
import com.omix.alleq.ui.theme.LocalEqColors
import com.omix.alleq.ui.theme.WixFontFamily

@Composable
fun TutorialDialog(
    shizukuRunning: Boolean,
    shizukuHasPermission: Boolean,
    whitelistApplied: Boolean,
    onRequestPermission: () -> Unit,
    onApplyWhitelist: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalEqColors.current

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = colors.sheetBg),
            border = BorderStroke(1.dp, colors.border),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(if (colors.isDark) Color(0xFF242424) else Color(0xFFE8ECEF), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        tint = colors.textPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "allEQ SETUP",
                    fontFamily = BenzinFontFamily,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    color = colors.textPrimary
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.tutorial_subtitle),
                    fontFamily = WixFontFamily,
                    fontSize = 12.sp,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                val onAccentColor = if (colors.accent.luminance() < 0.45f) Color.White else Color.Black

                TutorialStepItem(
                    stepNumber = "1",
                    title = "SHIZUKU SERVICE",
                    description = if (shizukuRunning) androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.tutorial_step1_running) else androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.tutorial_step1_not_running),
                    icon = Icons.Default.Security,
                    isDone = shizukuRunning,
                    actionButton = null
                )

                Spacer(modifier = Modifier.height(12.dp))

                TutorialStepItem(
                    stepNumber = "2",
                    title = "ACCESS PERMISSIONS",
                    description = if (shizukuHasPermission) androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.tutorial_step2_granted) else androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.tutorial_step2_not_granted),
                    icon = Icons.Default.CheckCircle,
                    isDone = shizukuHasPermission,
                    actionButton = {
                        if (!shizukuHasPermission) {
                            Button(
                                onClick = onRequestPermission,
                                enabled = shizukuRunning,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.accent,
                                    contentColor = onAccentColor
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                            ) {
                                Text(androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.tutorial_btn_grant), fontFamily = WixFontFamily, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                TutorialStepItem(
                    stepNumber = "3",
                    title = "BACKGROUND PROTECTION",
                    description = if (whitelistApplied) androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.tutorial_step3_applied) else androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.tutorial_step3_not_applied),
                    icon = Icons.Default.BatteryAlert,
                    isDone = whitelistApplied,
                    actionButton = {
                        if (shizukuHasPermission && !whitelistApplied) {
                            Button(
                                onClick = onApplyWhitelist,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.accent,
                                    contentColor = onAccentColor
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                            ) {
                                Text(androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.tutorial_btn_apply), fontFamily = WixFontFamily, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (colors.isDark) Color.White else Color(0xFF121212),
                        contentColor = if (colors.isDark) Color.Black else Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.tutorial_btn_close), fontFamily = WixFontFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun TutorialStepItem(
    stepNumber: String,
    title: String,
    description: String,
    icon: ImageVector,
    isDone: Boolean,
    actionButton: (@Composable () -> Unit)?
) {
    val colors = LocalEqColors.current

    val onAccent = if (colors.accent.luminance() < 0.45f) Color.White else Color.Black

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDone) colors.accent.copy(alpha = 0.12f)
            else colors.sheetCardBg
        ),
        border = BorderStroke(
            1.dp,
            if (isDone) colors.accent.copy(alpha = 0.4f)
            else colors.borderSubtle
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .background(
                            if (isDone) colors.accent
                            else (if (colors.isDark) Color(0xFF333333) else Color(0xFFD4D8DE)),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stepNumber,
                        color = if (isDone) onAccent else (if (colors.isDark) Color.White else Color(0xFF1E1E1E)),
                        fontWeight = FontWeight.Bold,
                        fontFamily = BenzinFontFamily,
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = title.uppercase(),
                    fontFamily = BenzinFontFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.4.sp,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f)
                )

                if (isDone) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = description.lowercase(),
                fontFamily = WixFontFamily,
                fontSize = 12.sp,
                color = colors.textSecondary,
                lineHeight = 16.sp
            )

            actionButton?.invoke()
        }
    }
}
