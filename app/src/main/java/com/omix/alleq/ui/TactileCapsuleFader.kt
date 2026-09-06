package com.omix.alleq.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omix.alleq.ui.theme.BenzinFontFamily
import com.omix.alleq.ui.theme.LocalEqColors
import com.omix.alleq.ui.theme.WixFontFamily
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun TactileCapsuleFader(
    title: String,
    valueText: String,
    progress: Float, // 0.0f .. 1.0f
    onProgressChanged: (Float) -> Unit,
    switchEnabled: Boolean,
    onSwitchToggle: (Boolean) -> Unit,
    isEditing: Boolean = false,
    onActivate: () -> Unit = {},
    onDeactivate: () -> Unit = {},
    modifier: Modifier = Modifier,
    hapticSteps: Int = 40
) {
    val colors = LocalEqColors.current
    val haptic = LocalHapticFeedback.current
    var containerHeightPx by remember { mutableFloatStateOf(0f) }
    var containerWidthPx by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var localProgress by remember { mutableFloatStateOf(progress) }
    var lastEmittedStep by remember { mutableIntStateOf((progress * hapticSteps).roundToInt()) }

    LaunchedEffect(progress) {
        if (!isDragging) {
            localProgress = progress
            lastEmittedStep = (progress * hapticSteps).roundToInt()
        }
    }

    // auto lock after 3.5s idle
    LaunchedEffect(isEditing, isDragging) {
        if (isEditing && !isDragging) {
            delay(3500)
            onDeactivate()
        }
    }

    Box(
        modifier = modifier
            .height(150.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(if (isEditing) colors.surfaceElevated else colors.cardBg)
            .border(
                width = if (isEditing) 1.5.dp else 1.dp,
                color = if (isEditing) colors.accent
                else if (switchEnabled) colors.cardBorder else colors.borderSubtle,
                shape = RoundedCornerShape(22.dp)
            )
            .onGloballyPositioned { coordinates ->
                containerHeightPx = coordinates.size.height.toFloat()
                containerWidthPx = coordinates.size.width.toFloat()
            }
            .pointerInput(isEditing) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val isPowerButtonArea = (down.position.y <= 46.dp.toPx()) &&
                            (containerWidthPx > 0f && down.position.x >= (containerWidthPx - 46.dp.toPx()))

                    if (isPowerButtonArea) {
                        return@awaitEachGesture
                    }

                    if (!isEditing) {
                        val up = waitForUpOrCancellation()
                        if (up != null) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (!switchEnabled) {
                                onSwitchToggle(true)
                            }
                            onActivate()
                        }
                    } else {
                        down.consume()
                        isDragging = true
                        try {
                            if (containerHeightPx > 0f) {
                                val newProg = (1f - (down.position.y / containerHeightPx)).coerceIn(0f, 1f)
                                localProgress = newProg
                                val step = (newProg * hapticSteps).roundToInt()
                                if (step != lastEmittedStep) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    lastEmittedStep = step
                                    onProgressChanged(step.toFloat() / hapticSteps.toFloat())
                                }
                            }

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                change.consume()
                                if (containerHeightPx > 0f) {
                                    val newProg = (1f - (change.position.y / containerHeightPx)).coerceIn(0f, 1f)
                                    localProgress = newProg
                                    val step = (newProg * hapticSteps).roundToInt()
                                    if (step != lastEmittedStep) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        lastEmittedStep = step
                                        onProgressChanged(step.toFloat() / hapticSteps.toFloat())
                                    }
                                }
                            }
                        } finally {
                            isDragging = false
                        }
                        onProgressChanged(localProgress)
                    }
                }
            }
    ) {
        val fillHeightFraction = if (switchEnabled) localProgress.coerceIn(0f, 1f) else 0f
        if (fillHeightFraction > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(fillHeightFraction)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = if (colors.isDark) listOf(
                                colors.accent.copy(alpha = 0.32f),
                                colors.accent.copy(alpha = 0.08f)
                            ) else listOf(
                                colors.accent.copy(alpha = 0.38f),
                                colors.accent.copy(alpha = 0.12f)
                            )
                        )
                    )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.5.dp)
                        .align(Alignment.TopCenter)
                        .background(colors.accent)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title.uppercase(),
                    fontFamily = BenzinFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                    letterSpacing = 0.2.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                    color = if (switchEnabled) colors.textPrimary else colors.textMuted
                )

                val onAccentTint = if (colors.accent.luminance() < 0.45f) Color.White else Color.Black
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSwitchToggle(!switchEnabled)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(
                                if (switchEnabled) colors.accent
                                else (if (colors.isDark) Color(0xFF222222) else Color(0xFFE0E3E8))
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = "Toggle",
                            tint = if (switchEnabled) onAccentTint else colors.textMuted,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }

            Column {
                Box {
                    if (switchEnabled) {
                        Text(
                            text = valueText,
                            fontFamily = BenzinFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            style = androidx.compose.ui.text.TextStyle(
                                color = if (colors.isDark) Color(0xFF0C0C0C) else Color(0xFFFFFFFF),
                                drawStyle = androidx.compose.ui.graphics.drawscope.Stroke(
                                    width = 6f,
                                    join = androidx.compose.ui.graphics.StrokeJoin.Round
                                )
                            )
                        )
                    }
                    Text(
                        text = if (switchEnabled) valueText else androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.fader_off),
                        fontFamily = if (switchEnabled) BenzinFontFamily else WixFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = if (switchEnabled) 18.sp else 16.sp,
                        color = if (switchEnabled) (if (isEditing) colors.accent else colors.textPrimary) else colors.textMuted
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = when {
                        !switchEnabled -> androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.fader_disabled_hint)
                        isEditing -> androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.fader_swipe_hint)
                        else -> androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.fader_tap_to_adjust)
                    },
                    fontFamily = WixFontFamily,
                    fontSize = 10.sp,
                    fontWeight = if (isEditing) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isEditing) colors.accent else colors.textMuted
                )
            }
        }
    }
}
