package com.omix.alleq.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.omix.alleq.R
import com.omix.alleq.model.AudioDeviceType
import com.omix.alleq.model.DevicePresetBinding
import com.omix.alleq.model.EqualizerPreset
import com.omix.alleq.ui.theme.BenzinFontFamily
import com.omix.alleq.ui.theme.LocalEqColors
import com.omix.alleq.ui.theme.WixFontFamily
import kotlinx.coroutines.delay

@Composable
fun DeviceRoutingDropdown(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    autoSwitchEnabled: Boolean,
    onToggleAutoSwitch: () -> Unit,
    activeKey: String,
    knownDevices: List<DevicePresetBinding>,
    allPresets: List<EqualizerPreset>,
    onSelectDevicePreset: (String, String) -> Unit,
) {
    val colors = LocalEqColors.current
    val haptic = LocalHapticFeedback.current

    var isVisible by remember { mutableStateOf(false) }
    var pickingPresetForDeviceKey by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(expanded) {
        if (expanded) {
            isVisible = true
        } else {
            isVisible = false
            pickingPresetForDeviceKey = null
        }
    }

    // delay actual dismissal to allow smooth exit animation
    LaunchedEffect(isVisible) {
        if (!isVisible && expanded) {
            delay(190)
            onDismissRequest()
        }
    }

    if (expanded || isVisible) {
        Popup(
            alignment = Alignment.TopCenter,
            onDismissRequest = {
                isVisible = false
            },
            properties = PopupProperties(
                focusable = true,
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
            ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        isVisible = false
                    },
                contentAlignment = Alignment.TopCenter,
            ) {
                // subtle dim scrim with smooth fade
                val scrimAlpha by animateFloatAsState(
                    targetValue = if (isVisible) 0.35f else 0f,
                    animationSpec = tween(durationMillis = 200),
                    label = "scrim_alpha",
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = scrimAlpha)),
                )

                // animated floating card container
                AnimatedVisibility(
                    visible = isVisible,
                    enter = fadeIn(animationSpec = tween(220, easing = LinearOutSlowInEasing)) +
                            expandVertically(
                                animationSpec = spring(
                                    dampingRatio = 0.82f,
                                    stiffness = Spring.StiffnessMediumLow,
                                ),
                                expandFrom = Alignment.Top,
                            ) +
                            scaleIn(
                                initialScale = 0.88f,
                                transformOrigin = TransformOrigin(0.5f, 0f),
                                animationSpec = spring(
                                    dampingRatio = 0.82f,
                                    stiffness = Spring.StiffnessMediumLow,
                                ),
                            ),
                    exit = fadeOut(animationSpec = tween(150, easing = FastOutLinearInEasing)) +
                            shrinkVertically(
                                animationSpec = tween(170, easing = FastOutSlowInEasing),
                                shrinkTowards = Alignment.Top,
                            ) +
                            scaleOut(
                                targetScale = 0.90f,
                                transformOrigin = TransformOrigin(0.5f, 0f),
                                animationSpec = tween(170),
                            ),
                    modifier = Modifier.padding(top = 64.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(min = 290.dp, max = 340.dp)
                            .shadow(24.dp, RoundedCornerShape(20.dp), spotColor = colors.accent.copy(alpha = 0.25f))
                            .clip(RoundedCornerShape(20.dp))
                            .background(colors.cardBg)
                            .border(BorderStroke(1.dp, colors.cardBorder), RoundedCornerShape(20.dp))
                            .animateContentSize(
                                animationSpec = spring(
                                    dampingRatio = 0.85f,
                                    stiffness = Spring.StiffnessMediumLow,
                                ),
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { /* prevent clicking through card to scrim */ }
                            .padding(vertical = 10.dp),
                    ) {
                        // 1. Top header row: Title & Auto-switch pill
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.routing_title),
                                fontFamily = BenzinFontFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp,
                                color = colors.textPrimary,
                            )

                            // auto-switch toggle pill
                            val pillBgColor by animateColorAsState(
                                targetValue = if (autoSwitchEnabled) colors.accent.copy(alpha = 0.15f) else colors.surfaceElevated,
                                animationSpec = tween(180),
                                label = "pill_bg",
                            )
                            val pillBorderColor by animateColorAsState(
                                targetValue = if (autoSwitchEnabled) colors.accent.copy(alpha = 0.6f) else colors.cardBorder,
                                animationSpec = tween(180),
                                label = "pill_border",
                            )

                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(pillBgColor)
                                    .border(1.dp, pillBorderColor, RoundedCornerShape(8.dp))
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onToggleAutoSwitch()
                                    }
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .clip(CircleShape)
                                        .background(if (autoSwitchEnabled) colors.accent else colors.textMuted),
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = if (autoSwitchEnabled) "AUTO" else "MANUAL",
                                    fontFamily = BenzinFontFamily,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (autoSwitchEnabled) colors.accent else colors.textMuted,
                                )
                            }
                        }

                        HorizontalDivider(
                            color = colors.cardBorder.copy(alpha = 0.5f),
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(vertical = 6.dp),
                        )

                        // 2. Active output section
                        val activeDevice = knownDevices.firstOrNull { it.deviceKey == activeKey }
                            ?: knownDevices.firstOrNull { it.isCurrentlyActive }
                            ?: DevicePresetBinding(
                                deviceKey = "speaker",
                                deviceName = stringResource(R.string.device_speaker),
                                deviceType = AudioDeviceType.SPEAKER,
                                assignedPresetName = "Flat",
                                isCurrentlyActive = true,
                            )

                        Text(
                            text = stringResource(R.string.routing_active_output),
                            fontFamily = WixFontFamily,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textMuted,
                            letterSpacing = 0.5.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 3.dp),
                        )

                        DeviceRowItem(
                            device = activeDevice,
                            isActive = true,
                            isPickingPreset = pickingPresetForDeviceKey == activeDevice.deviceKey,
                            onPresetClick = {
                                pickingPresetForDeviceKey = if (pickingPresetForDeviceKey == activeDevice.deviceKey) null else activeDevice.deviceKey
                            },
                        )

                        // Animated cascading submenu for active device
                        AnimatedVisibility(
                            visible = pickingPresetForDeviceKey == activeDevice.deviceKey,
                            enter = expandVertically(
                                animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
                                expandFrom = Alignment.Top,
                            ) + fadeIn(tween(160)),
                            exit = shrinkVertically(
                                animationSpec = tween(140, easing = FastOutSlowInEasing),
                                shrinkTowards = Alignment.Top,
                            ) + fadeOut(tween(120)),
                        ) {
                            PresetPickerSubMenu(
                                currentPresetName = activeDevice.assignedPresetName,
                                allPresets = allPresets,
                                onSelectPreset = { presetName ->
                                    onSelectDevicePreset(activeDevice.deviceKey, presetName)
                                    pickingPresetForDeviceKey = null
                                },
                            )
                        }

                        // 3. Other known outputs section
                        val otherDevices = knownDevices.filter { it.deviceKey != activeDevice.deviceKey }
                        if (otherDevices.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            HorizontalDivider(
                                color = colors.cardBorder.copy(alpha = 0.5f),
                                thickness = 0.5.dp,
                                modifier = Modifier.padding(vertical = 6.dp),
                            )

                            Text(
                                text = stringResource(R.string.routing_saved_outputs),
                                fontFamily = WixFontFamily,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textMuted,
                                letterSpacing = 0.5.sp,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 3.dp),
                            )

                            otherDevices.forEach { device ->
                                val isPicking = pickingPresetForDeviceKey == device.deviceKey
                                DeviceRowItem(
                                    device = device,
                                    isActive = false,
                                    isPickingPreset = isPicking,
                                    onPresetClick = {
                                        pickingPresetForDeviceKey = if (isPicking) null else device.deviceKey
                                    },
                                )

                                AnimatedVisibility(
                                    visible = isPicking,
                                    enter = expandVertically(
                                        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
                                        expandFrom = Alignment.Top,
                                    ) + fadeIn(tween(160)),
                                    exit = shrinkVertically(
                                        animationSpec = tween(140, easing = FastOutSlowInEasing),
                                        shrinkTowards = Alignment.Top,
                                    ) + fadeOut(tween(120)),
                                ) {
                                    PresetPickerSubMenu(
                                        currentPresetName = device.assignedPresetName,
                                        allPresets = allPresets,
                                        onSelectPreset = { presetName ->
                                            onSelectDevicePreset(device.deviceKey, presetName)
                                            pickingPresetForDeviceKey = null
                                        },
                                    )
                                }
                            }
                        }

                        // 4. Subtle hint footer
                        Spacer(modifier = Modifier.height(4.dp))
                        HorizontalDivider(
                            color = colors.cardBorder.copy(alpha = 0.5f),
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(vertical = 6.dp),
                        )

                        Text(
                            text = stringResource(R.string.routing_hint),
                            fontFamily = WixFontFamily,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Normal,
                            color = colors.textMuted.copy(alpha = 0.75f),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceRowItem(
    device: DevicePresetBinding,
    isActive: Boolean,
    isPickingPreset: Boolean,
    onPresetClick: () -> Unit,
) {
    val colors = LocalEqColors.current
    val haptic = LocalHapticFeedback.current

    val subChevronRotation by animateFloatAsState(
        targetValue = if (isPickingPreset) 180f else 0f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow),
        label = "sub_chevron_rotation",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f, fill = false)
                .padding(end = 8.dp),
        ) {
            val icon: ImageVector = when (device.deviceType) {
                AudioDeviceType.SPEAKER -> Icons.AutoMirrored.Filled.VolumeUp
                AudioDeviceType.BLUETOOTH -> Icons.Default.Bluetooth
                AudioDeviceType.WIRED -> Icons.Default.Headphones
                AudioDeviceType.USB -> Icons.Default.Usb
            }
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isActive) colors.accent else colors.textSecondary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = device.deviceName,
                fontFamily = WixFontFamily,
                fontSize = 12.sp,
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium,
                color = if (isActive) colors.textPrimary else colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // preset selector badge button
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(colors.surfaceElevated)
                .border(
                    1.dp,
                    if (isPickingPreset) colors.accent.copy(alpha = 0.8f)
                    else if (isActive) colors.cardBorder
                    else colors.cardBorder.copy(alpha = 0.5f),
                    RoundedCornerShape(8.dp),
                )
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onPresetClick()
                }
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = device.assignedPresetName,
                fontFamily = BenzinFontFamily,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = if (isPickingPreset) colors.accent else colors.textPrimary,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.width(3.dp))
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = if (isPickingPreset) colors.accent else colors.textMuted,
                modifier = Modifier
                    .size(12.dp)
                    .graphicsLayer { rotationZ = subChevronRotation },
            )
        }
    }
}

@Composable
private fun PresetPickerSubMenu(
    currentPresetName: String,
    allPresets: List<EqualizerPreset>,
    onSelectPreset: (String) -> Unit,
) {
    val colors = LocalEqColors.current
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceElevated)
            .border(1.dp, colors.cardBorder, RoundedCornerShape(12.dp))
            .padding(4.dp),
    ) {
        allPresets.forEach { preset ->
            val isSelected = preset.name.equals(currentPresetName, ignoreCase = true)
            val itemBg by animateColorAsState(
                targetValue = if (isSelected) colors.accent.copy(alpha = 0.15f) else Color.Transparent,
                animationSpec = tween(150),
                label = "preset_item_bg",
            )
            val textColor by animateColorAsState(
                targetValue = if (isSelected) colors.accent else colors.textPrimary,
                animationSpec = tween(150),
                label = "preset_text_color",
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(itemBg)
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelectPreset(preset.name)
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = preset.name,
                    fontFamily = BenzinFontFamily,
                    fontSize = 10.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = textColor,
                )
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
    }
}
