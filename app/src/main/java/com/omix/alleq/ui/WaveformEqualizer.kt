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
import androidx.compose.ui.unit.sp
import com.omix.alleq.model.EqualizerDefaults
import com.omix.alleq.ui.theme.LocalEqColors

@Composable
fun WaveformEqualizerCard(
    presetName: String,
    bandGains: List<Float>,
    onBandGainChanged: (Int, Float) -> Unit,
    modifier: Modifier = Modifier,
    isModified: Boolean = false
) {
    val colors = LocalEqColors.current

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
                    .padding(horizontal = 14.dp, vertical = 6.dp)
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
                    if (isModified) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(Color(0xFFFF9800), CircleShape)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.preset_unsaved),
                            fontFamily = com.omix.alleq.ui.theme.WixFontFamily,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF9800)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                EqualizerDefaults.BANDS.forEach { band ->
                    val gain = bandGains.getOrElse(band.index) { 0f }
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

            var canvasWidth by remember { mutableFloatStateOf(0f) }
            var canvasHeight by remember { mutableFloatStateOf(0f) }
            var activeDraggingBand by remember { mutableIntStateOf(-1) }

            val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .pointerInput(bandGains) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            if (canvasWidth <= 0 || canvasHeight <= 0) return@awaitEachGesture

                            val bandCount = EqualizerDefaults.BANDS.size
                            val colWidth = canvasWidth / bandCount
                            val padding = 20f
                            val usableHeight = canvasHeight - padding * 2

                            val hitRadiusPx = 28.dp.toPx()
                            var targetBand = -1
                            var minDistanceSq = Float.MAX_VALUE

                            for (i in 0 until bandCount) {
                                val gain = bandGains.getOrElse(i) { 0f }.coerceIn(-15f, 15f)
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

                            var lastIntGain = bandGains.getOrElse(activeBand) { 0f }.toInt()
                            fun applyGain(y: Float) {
                                val newGain = calculateGain(y)
                                val currentInt = kotlin.math.round(newGain).toInt()
                                if (currentInt != lastIntGain) {
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                    lastIntGain = currentInt
                                }
                                onBandGainChanged(activeBand, newGain)
                            }

                            applyGain(down.position.y)

                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!pointer.pressed) break

                                    applyGain(pointer.position.y)
                                    pointer.consume()
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
                        val gain = bandGains.getOrElse(i) { 0f }.coerceIn(-15f, 15f)
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
                                    radius = 13.dp.toPx(),
                                    center = pt
                                )
                            }
                            drawCircle(
                                color = colors.background,
                                radius = if (isDraggingThis) 8.dp.toPx() else 7.dp.toPx(),
                                center = pt
                            )
                            drawCircle(
                                color = curveLineColor,
                                radius = if (isDraggingThis) 6.dp.toPx() else 5.dp.toPx(),
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
