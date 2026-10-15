package com.omix.alleq.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.sp
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import com.omix.alleq.R
import com.omix.alleq.model.EqualizerDefaults
import com.omix.alleq.ui.haptics.AppHaptics
import com.omix.alleq.ui.theme.LocalEqColors

@Composable
fun WaveformEqualizerCard(
    presetName: String,
    bandGains: List<Float>,
    onBandGainChanged: (Int, Float) -> Unit,
    modifier: Modifier = Modifier,
    isModified: Boolean = false,
    onSavePreset: () -> Unit = {}
) {
    val colors = LocalEqColors.current
    val context = LocalContext.current
    val view = LocalView.current
    val haptic = LocalHapticFeedback.current

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = colors.cardBg),
        border = BorderStroke(1.dp, colors.cardBorder),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 10.dp)
        ) {
            Box(
                modifier = Modifier
                    .background(colors.accent.copy(alpha = 0.12f), RoundedCornerShape(50))
                    .border(1.dp, colors.accent.copy(alpha = 0.3f), RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = presetName.uppercase(),
                        fontFamily = com.omix.alleq.ui.theme.BenzinFontFamily,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        color = colors.accent
                    )
                    AnimatedVisibility(
                        visible = isModified,
                        enter = fadeIn() + expandHorizontally(),
                        exit = fadeOut() + shrinkHorizontally()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(10.dp)
                                    .background(colors.accent.copy(alpha = 0.35f))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(Color(0xFFFF9800).copy(alpha = 0.16f))
                                    .border(0.75.dp, Color(0xFFFF9800).copy(alpha = 0.45f), RoundedCornerShape(50))
                                    .clickable {
                                        AppHaptics.click(view, context)
                                        onSavePreset()
                                    }
                                    .padding(horizontal = 7.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .background(Color(0xFFFF9800), CircleShape)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.btn_save_inline).uppercase(),
                                    fontFamily = com.omix.alleq.ui.theme.BenzinFontFamily,
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp,
                                    color = Color(0xFFFF9800)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            var canvasWidth by remember { mutableFloatStateOf(0f) }
            var canvasHeight by remember { mutableFloatStateOf(0f) }
            var activeDraggingBand by remember { mutableIntStateOf(-1) }
            var localBandGains by remember { mutableStateOf(bandGains) }

            val currentOnBandGainChanged by rememberUpdatedState(onBandGainChanged)

            LaunchedEffect(bandGains) {
                if (activeDraggingBand == -1) {
                    localBandGains = bandGains
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                EqualizerDefaults.BANDS.forEach { band ->
                    val gain = localBandGains.getOrElse(band.index) { 0f }
                    Text(
                        text = "${if (gain > 0) "+" else ""}${gain.toInt()}",
                        fontFamily = com.omix.alleq.ui.theme.WixFontFamily,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (gain != 0f) colors.accent else colors.textMuted,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .onSizeChanged { size ->
                        canvasWidth = size.width.toFloat()
                        canvasHeight = size.height.toFloat()
                    }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            if (canvasWidth <= 0 || canvasHeight <= 0) return@awaitEachGesture

                            val bandCount = EqualizerDefaults.BANDS.size
                            val colWidth = canvasWidth / bandCount
                            val padding = 20f
                            val usableHeight = canvasHeight - padding * 2

                            val hitRadiusPx = 44.dp.toPx()
                            var targetBand = -1
                            var minDistanceSq = Float.MAX_VALUE

                            val snapshotGains = localBandGains

                            for (i in 0 until bandCount) {
                                val gain = snapshotGains.getOrElse(i) { 0f }.coerceIn(-15f, 15f)
                                val normalized = (gain + 15f) / 30f
                                val ptX = i * colWidth + colWidth / 2
                                val ptY = padding + (1f - normalized) * usableHeight

                                val dx = down.position.x - ptX
                                val dy = down.position.y - ptY
                                val distSq = dx * dx + dy * dy
                                if (distSq <= hitRadiusPx * hitRadiusPx && distSq < minDistanceSq) {
                                    minDistanceSq = distSq
                                    targetBand = i
                                }
                            }

                            // ignore touch outside handles to allow vertical scroll
                            if (targetBand == -1) {
                                return@awaitEachGesture
                            }

                            val activeBand = targetBand
                            activeDraggingBand = activeBand
                            down.consume()

                            fun calculateGain(y: Float): Float {
                                val relativeY = (y - padding).coerceIn(0f, usableHeight)
                                val normalized = 1f - (relativeY / usableHeight)
                                return (normalized * 30f - 15f).coerceIn(-15f, 15f)
                            }

                            var lastIntGain = snapshotGains.getOrElse(activeBand) { 0f }.toInt()
                            fun applyGain(y: Float) {
                                val newGain = calculateGain(y)
                                val currentInt = kotlin.math.round(newGain).toInt()
                                if (currentInt != lastIntGain) {
                                    AppHaptics.tick(view, context)
                                    lastIntGain = currentInt
                                }
                                localBandGains = localBandGains.toMutableList().also {
                                    if (activeBand in it.indices) it[activeBand] = newGain
                                }
                                currentOnBandGainChanged(activeBand, newGain)
                            }

                            applyGain(down.position.y)

                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!pointer.pressed) break

                                    pointer.consume()
                                    applyGain(pointer.position.y)
                                }
                            } finally {
                                activeDraggingBand = -1
                            }
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    canvasWidth = size.width
                    canvasHeight = size.height
                    val bandCount = EqualizerDefaults.BANDS.size
                    val colWidth = size.width / bandCount
                    val padding = 20f
                    val usableHeight = size.height - padding * 2

                    val points = mutableListOf<Offset>()

                    val curveLineColor = colors.accent
                    val guideColor = if (colors.isDark) Color(0xFF222222) else Color(0xFFE5E7EB)

                    for (i in 0 until bandCount) {
                        val gain = localBandGains.getOrElse(i) { 0f }.coerceIn(-15f, 15f)
                        val normalized = (gain + 15f) / 30f // 0 .. 1
                        val x = i * colWidth + colWidth / 2
                        val y = padding + (1f - normalized) * usableHeight
                        points.add(Offset(x, y))

                        drawLine(
                            color = guideColor,
                            start = Offset(x, padding),
                            end = Offset(x, size.height - padding),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    if (points.isNotEmpty()) {
                        val curvePath = Path().apply {
                            moveTo(points[0].x, points[0].y)
                            for (i in 0 until points.size - 1) {
                                val p0 = points[i]
                                val p1 = points[i + 1]
                                val controlX = (p0.x + p1.x) / 2
                                cubicTo(controlX, p0.y, controlX, p1.y, p1.x, p1.y)
                            }
                        }

                        val fillPath = Path().apply {
                            addPath(curvePath)
                            lineTo(points.last().x, size.height)
                            lineTo(points.first().x, size.height)
                            close()
                        }
                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(colors.accent.copy(alpha = 0.25f), Color.Transparent)
                            )
                        )

                        drawPath(
                            path = curvePath,
                            color = curveLineColor,
                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )

                        points.forEachIndexed { index, pt ->
                            val isDraggingThis = (index == activeDraggingBand)
                            if (isDraggingThis) {
                                drawCircle(
                                    color = curveLineColor.copy(alpha = 0.35f),
                                    radius = 16.dp.toPx(),
                                    center = pt
                                )
                            }
                            drawCircle(
                                color = colors.background,
                                radius = if (isDraggingThis) 9.dp.toPx() else 7.dp.toPx(),
                                center = pt
                            )
                            drawCircle(
                                color = curveLineColor,
                                radius = if (isDraggingThis) 7.dp.toPx() else 5.dp.toPx(),
                                center = pt
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val shortLabels = listOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")
                shortLabels.forEach { label ->
                    Text(
                        text = label,
                        fontFamily = com.omix.alleq.ui.theme.WixFontFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.textSecondary,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
