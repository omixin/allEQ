package com.omix.alleq.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omix.alleq.model.EqualizerPreset
import com.omix.alleq.ui.theme.BenzinFontFamily
import com.omix.alleq.ui.theme.LocalEqColors
import com.omix.alleq.ui.theme.WixFontFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetManagerSheet(
    onDismiss: () -> Unit,
    allPresets: List<EqualizerPreset>,
    deletedPresets: List<EqualizerPreset>,
    activePresetName: String,
    onReorder: (List<EqualizerPreset>) -> Unit,
    onDelete: (String) -> Unit,
    onRestore: (String) -> Unit,
    onResetToDefaults: () -> Unit
) {
    val colors = LocalEqColors.current
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val itemHeightPx = with(density) { 56.dp.toPx() }

    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var dragAccumulator by remember { mutableFloatStateOf(0f) }

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
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Column {
                    Text(
                        text = "PRESET QUEUE & MANAGER",
                        fontFamily = BenzinFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        letterSpacing = 0.5.sp,
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.preset_manager_drag_hint),
                        fontFamily = WixFontFamily,
                        fontSize = 12.sp,
                        color = colors.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ACTIVE QUEUE",
                            fontFamily = BenzinFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            letterSpacing = 0.5.sp,
                            color = colors.textSecondary
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (colors.isDark) Color(0xFF222222) else Color(0xFFE8ECEF))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${allPresets.size}",
                                fontFamily = WixFontFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                        }
                    }
                }

                itemsIndexed(allPresets, key = { _, item -> item.name }) { index, preset ->
                    val isActive = activePresetName.equals(preset.name, ignoreCase = true)
                    val isBeingDragged = (draggedIndex == index)

                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isBeingDragged) colors.surfaceElevated else colors.sheetCardBg
                        ),
                        border = BorderStroke(
                            width = if (isBeingDragged) 1.5.dp else if (isActive) 1.2.dp else 1.dp,
                            color = if (isBeingDragged) colors.textPrimary
                            else if (isActive) colors.accent
                            else colors.borderSubtle
                        ),
                        elevation = CardDefaults.cardElevation(if (isBeingDragged) 8.dp else 0.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .scale(if (isBeingDragged) 1.02f else 1f)
                    ) {
                        Row(
                            modifier = Modifier
                                 .fillMaxWidth()
                                 .padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "${index + 1}.",
                                        fontFamily = WixFontFamily,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.textMuted
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = preset.name.uppercase(),
                                        fontFamily = BenzinFontFamily,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.3.sp,
                                        color = colors.textPrimary
                                    )
                                    if (isActive) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(colors.accent.copy(alpha = 0.15f))
                                                .padding(horizontal = 5.dp, vertical = 1.dp)
                                        ) {
                                            Text(
                                                text = androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.preset_active),
                                                fontFamily = WixFontFamily,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = colors.accent
                                            )
                                        }
                                    }
                                    if (preset.isCustom) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(if (colors.isDark) Color(0xFF2A2A38) else Color(0xFFEDE7F6))
                                                .padding(horizontal = 5.dp, vertical = 1.dp)
                                        ) {
                                            Text(
                                                text = androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.preset_custom),
                                                fontFamily = WixFontFamily,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF7C4DFF)
                                            )
                                        }
                                    }
                                }

                                val localizedDesc = getLocalizedPresetDescription(preset)
                                if (localizedDesc.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = localizedDesc.lowercase(),
                                        fontFamily = WixFontFamily,
                                        fontSize = 11.sp,
                                        color = colors.textSecondary,
                                        maxLines = 1
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { onDelete(preset.name) },
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Delete",
                                        tint = Color(0xFFFF5252),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .pointerInput(allPresets) {
                                            detectVerticalDragGestures(
                                                onDragStart = {
                                                    draggedIndex = index
                                                    dragAccumulator = 0f
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                },
                                                onDragEnd = {
                                                    draggedIndex = null
                                                    dragAccumulator = 0f
                                                },
                                                onDragCancel = {
                                                    draggedIndex = null
                                                    dragAccumulator = 0f
                                                },
                                                onVerticalDrag = { change, dragAmount ->
                                                    change.consume()
                                                    dragAccumulator += dragAmount
                                                    val current = draggedIndex ?: return@detectVerticalDragGestures
                                                    val steps = (dragAccumulator / itemHeightPx).toInt()
                                                    if (steps != 0) {
                                                        val target = (current + steps).coerceIn(0, allPresets.size - 1)
                                                        if (target != current) {
                                                            val mutable = allPresets.toMutableList()
                                                            val item = mutable.removeAt(current)
                                                            mutable.add(target, item)
                                                            draggedIndex = target
                                                            dragAccumulator -= steps * itemHeightPx
                                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                            onReorder(mutable)
                                                        }
                                                    }
                                                }
                                            )
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DragHandle,
                                        contentDescription = "Drag to reorder",
                                        tint = if (isBeingDragged) colors.textPrimary else colors.textMuted,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                if (deletedPresets.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "DELETED PRESETS",
                                fontFamily = BenzinFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                letterSpacing = 0.5.sp,
                                color = Color(0xFFFF8A80)
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (colors.isDark) Color(0xFF331818) else Color(0xFFFFEBEE))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${deletedPresets.size}",
                                    fontFamily = WixFontFamily,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFF5252)
                                )
                            }
                        }
                    }

                    itemsIndexed(deletedPresets, key = { _, item -> "del_" + item.name }) { _, preset ->
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (colors.isDark) Color(0xFF151212) else Color(0xFFFFF7F7)
                            ),
                            border = BorderStroke(1.dp, if (colors.isDark) Color(0xFF261818) else Color(0xFFFFE0E0)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = preset.name.uppercase(),
                                        fontFamily = BenzinFontFamily,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.textSecondary
                                    )
                                    val localizedDesc = getLocalizedPresetDescription(preset)
                                    if (localizedDesc.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = localizedDesc.lowercase(),
                                            fontFamily = WixFontFamily,
                                            fontSize = 11.sp,
                                            color = colors.textMuted,
                                            maxLines = 1
                                        )
                                    }
                                }

                                Button(
                                    onClick = { onRestore(preset.name) },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (colors.isDark) Color(0xFF242020) else Color(0xFFE8F5E9),
                                        contentColor = Color(0xFF4CAF50)
                                    ),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Restore,
                                        contentDescription = "Restore",
                                        modifier = Modifier.size(15.dp),
                                        tint = Color(0xFF4CAF50)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.btn_restore),
                                        fontFamily = WixFontFamily,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF4CAF50)
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onResetToDefaults,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, colors.border),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textSecondary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = "Reset",
                            modifier = Modifier.size(16.dp),
                            tint = colors.textSecondary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.btn_reset_defaults),
                            fontFamily = WixFontFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
fun getLocalizedPresetDescription(preset: EqualizerPreset): String {
    if (preset.isCustom) return preset.description
    return when (preset.name.lowercase()) {
        "flat" -> androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.preset_desc_flat)
        "bass punch" -> androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.preset_desc_bass_punch)
        "vocal clarity" -> androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.preset_desc_vocal_clarity)
        "club" -> androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.preset_desc_club)
        "rock" -> androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.preset_desc_rock)
        "cinematic" -> androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.preset_desc_cinematic)
        "acoustic" -> androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.preset_desc_acoustic)
        else -> preset.description
    }
}
