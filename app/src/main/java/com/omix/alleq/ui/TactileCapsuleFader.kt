package com.omix.alleq.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import com.omix.alleq.ui.haptics.AppHaptics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.omix.alleq.ui.theme.BenzinFontFamily
import com.omix.alleq.ui.theme.LocalEqColors
import com.omix.alleq.ui.theme.WixFontFamily
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

private val CollapsedHeight = 56.dp
private val ExpandedHeight = 150.dp
// below this width title and power button do not fit side by side in the pill
private val NarrowPillWidth = 150.dp
private val MorphEasing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)

private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t

/**
 * Collapsed: low pill, level fills left to right, horizontal swipe adjusts in place.
 * Tap expands into the tall fader (fill pours from horizontal to vertical), vertical swipe adjusts,
 * collapses back after 3.5s idle.
 */
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
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
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

    // 0 = collapsed pill, 1 = expanded fader
    val expansion by animateFloatAsState(
        targetValue = if (isEditing) 1f else 0f,
        animationSpec = tween(420, easing = MorphEasing),
        label = "expansion",
    )
    val fill by animateFloatAsState(
        targetValue = if (switchEnabled) localProgress.coerceIn(0f, 1f) else 0f,
        animationSpec = if (isDragging) snap() else tween(260, easing = MorphEasing),
        label = "fill",
    )

    val isNarrow = containerWidthPx > 0f && containerWidthPx < with(density) { NarrowPillWidth.toPx() }
    val powerAlpha = if (isNarrow) expansion else 1f

    // gesture loop is keyed on Unit, read fresh values through updated state
    val editing by rememberUpdatedState(isEditing)
    val enabled by rememberUpdatedState(switchEnabled)
    val powerVisible by rememberUpdatedState(!isNarrow || isEditing)
    val currentOnProgressChanged by rememberUpdatedState(onProgressChanged)
    val currentOnSwitchToggle by rememberUpdatedState(onSwitchToggle)
    val currentOnActivate by rememberUpdatedState(onActivate)

    fun applyPosition(fraction: Float) {
        val newProg = fraction.coerceIn(0f, 1f)
        localProgress = newProg
        val step = (newProg * hapticSteps).roundToInt()
        if (step != lastEmittedStep) {
            AppHaptics.tick(view, context)
            lastEmittedStep = step
            currentOnProgressChanged(step.toFloat() / hapticSteps.toFloat())
        }
    }

    val shape = RoundedCornerShape(mix(18f, 22f, expansion).dp)

    Box(
        modifier = modifier
            .height(lerp(CollapsedHeight, ExpandedHeight, expansion))
            .onSizeChanged { containerWidthPx = it.width.toFloat() }
            .clip(shape)
            .background(if (isEditing) colors.surfaceElevated else colors.cardBg)
            .drawBehind {
                if (fill <= 0f) return@drawBehind
                val t = expansion
                val fillW = size.width * mix(fill, 1f, t)
                val fillH = size.height * mix(1f, fill, t)
                val top = size.height - fillH
                val strong = colors.accent.copy(alpha = if (colors.isDark) 0.32f else 0.38f)
                val weak = colors.accent.copy(alpha = if (colors.isDark) 0.08f else 0.12f)
                // gradient runs from the level edge into the fill: right to left as a pill, top down as a fader
                val start = Offset(mix(fillW, 0f, t), mix(0f, top, t))
                val end = Offset(0f, mix(0f, size.height, t))
                drawRect(
                    brush = Brush.linearGradient(listOf(strong, weak), start, end),
                    topLeft = Offset(0f, top),
                    size = Size(fillW, fillH),
                )
                val edge = 2.5.dp.toPx()
                if (t < 1f) {
                    drawRect(colors.accent.copy(alpha = 1f - t), Offset(fillW - edge, top), Size(edge, fillH))
                }
                if (t > 0f) {
                    drawRect(colors.accent.copy(alpha = t), Offset(0f, top), Size(fillW, edge))
                }
            }
            .border(
                width = if (isEditing) 1.5.dp else 1.dp,
                color = if (isEditing) colors.accent
                else if (switchEnabled) colors.cardBorder else colors.borderSubtle,
                shape = shape
            )
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val isPowerButtonArea = powerVisible &&
                            down.position.x >= size.width - 48.dp.toPx() &&
                            down.position.y <= 52.dp.toPx()
                    if (isPowerButtonArea) return@awaitEachGesture

                    if (editing) {
                        // expanded: vertical position sets the value
                        down.consume()
                        isDragging = true
                        try {
                            applyPosition(1f - down.position.y / size.height)
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                change.consume()
                                applyPosition(1f - change.position.y / size.height)
                            }
                        } finally {
                            isDragging = false
                        }
                        currentOnProgressChanged(localProgress)
                        return@awaitEachGesture
                    }

                    // collapsed: tap expands, horizontal swipe adjusts, vertical swipe is left to the page scroll
                    val slop = viewConfiguration.touchSlop
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                        if (change.isConsumed) return@awaitEachGesture
                        if (!change.pressed) {
                            AppHaptics.heavyClick(view, context)
                            if (!enabled) currentOnSwitchToggle(true)
                            currentOnActivate()
                            return@awaitEachGesture
                        }
                        val dx = change.position.x - down.position.x
                        val dy = change.position.y - down.position.y
                        if (abs(dy) > slop && abs(dy) > abs(dx)) return@awaitEachGesture
                        if (abs(dx) > slop) {
                            change.consume()
                            break
                        }
                    }

                    if (!enabled) currentOnSwitchToggle(true)
                    isDragging = true
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            change.consume()
                            applyPosition(change.position.x / size.width)
                        }
                    } finally {
                        isDragging = false
                    }
                    currentOnProgressChanged(localProgress)
                }
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = 14.dp,
                    end = 14.dp,
                    top = lerp(10.dp, 22.dp, expansion),
                    bottom = lerp(7.dp, 14.dp, expansion),
                )
        ) {
            Text(
                text = title.uppercase(),
                fontFamily = BenzinFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp,
                letterSpacing = 0.2.sp,
                lineHeight = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 38.dp * powerAlpha),
                color = if (switchEnabled) colors.textPrimary else colors.textSecondary
            )

            Spacer(modifier = Modifier.weight(1f))

            Box {
                val valueSize = lerp(15.sp, 18.sp, expansion)
                if (switchEnabled) {
                    Text(
                        text = valueText,
                        fontFamily = BenzinFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = valueSize,
                        lineHeight = valueSize * 1.15f,
                        maxLines = 1,
                        style = androidx.compose.ui.text.TextStyle(
                            color = if (colors.isDark) Color(0xFF0C0C0C) else Color(0xFFFFFFFF),
                            drawStyle = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = with(density) { 1.2.dp.toPx() },
                                join = androidx.compose.ui.graphics.StrokeJoin.Round
                            )
                        )
                    )
                }
                Text(
                    text = if (switchEnabled) valueText else androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.fader_off),
                    fontFamily = if (switchEnabled) BenzinFontFamily else WixFontFamily,
                    // off is a quiet status, the title stays the main thing
                    fontWeight = if (switchEnabled) FontWeight.Bold else FontWeight.Medium,
                    fontSize = if (switchEnabled) valueSize else 12.sp,
                    lineHeight = valueSize * 1.15f,
                    maxLines = 1,
                    color = if (switchEnabled) (if (isEditing) colors.accent else colors.textPrimary) else colors.textMuted
                )
            }

            // swipe hint only exists in the expanded fader
            Box(
                modifier = Modifier
                    .height(16.dp * expansion)
                    .clipToBounds()
                    .graphicsLayer { alpha = expansion }
            ) {
                Text(
                    text = androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.fader_swipe_hint),
                    fontFamily = WixFontFamily,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.accent,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        val onAccentTint = if (colors.accent.luminance() < 0.45f) Color.White else Color.Black
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 6.dp, end = 4.dp)
                .size(44.dp)
                .graphicsLayer { alpha = powerAlpha }
                .clip(CircleShape)
                .clickable(enabled = powerAlpha > 0.5f) {
                    AppHaptics.click(view, context)
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
}
