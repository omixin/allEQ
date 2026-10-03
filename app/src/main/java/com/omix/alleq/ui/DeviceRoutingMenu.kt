package com.omix.alleq.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omix.alleq.R
import com.omix.alleq.model.AudioDeviceType
import com.omix.alleq.model.DevicePresetBinding
import com.omix.alleq.model.EqualizerPreset
import com.omix.alleq.ui.theme.BenzinFontFamily
import com.omix.alleq.ui.theme.LocalEqColors
import com.omix.alleq.ui.theme.WixFontFamily

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

    // track which device currently has the preset selector sub-menu open
    var pickingPresetForDeviceKey by remember { mutableStateOf<String?>(null) }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = {
            pickingPresetForDeviceKey = null
            onDismissRequest()
        },
        modifier = Modifier
            .widthIn(min = 280.dp, max = 340.dp)
            .background(colors.cardBg, RoundedCornerShape(18.dp))
            .border(BorderStroke(1.dp, colors.cardBorder), RoundedCornerShape(18.dp))
            .padding(vertical = 6.dp),
    ) {
        // top bar: title and auto-switch toggle pill
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
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
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (autoSwitchEnabled) colors.accent.copy(alpha = 0.15f)
                        else colors.surfaceElevated,
                    )
                    .border(
                        1.dp,
                        if (autoSwitchEnabled) colors.accent.copy(alpha = 0.6f)
                        else colors.cardBorder,
                        RoundedCornerShape(8.dp),
                    )
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
            modifier = Modifier.padding(vertical = 4.dp),
        )

        // active output section
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
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
        )

        DeviceRowItem(
            device = activeDevice,
            isActive = true,
            onPresetClick = {
                pickingPresetForDeviceKey = if (pickingPresetForDeviceKey == activeDevice.deviceKey) null else activeDevice.deviceKey
            },
        )

        // sub-menu for picking preset for active device
        if (pickingPresetForDeviceKey == activeDevice.deviceKey) {
            PresetPickerSubMenu(
                currentPresetName = activeDevice.assignedPresetName,
                allPresets = allPresets,
                onSelectPreset = { presetName ->
                    onSelectDevicePreset(activeDevice.deviceKey, presetName)
                    pickingPresetForDeviceKey = null
                },
            )
        }

        // other known outputs (if any exist)
        val otherDevices = knownDevices.filter { it.deviceKey != activeDevice.deviceKey }
        if (otherDevices.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            HorizontalDivider(
                color = colors.cardBorder.copy(alpha = 0.5f),
                thickness = 0.5.dp,
                modifier = Modifier.padding(vertical = 4.dp),
            )

            Text(
                text = stringResource(R.string.routing_saved_outputs),
                fontFamily = WixFontFamily,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textMuted,
                letterSpacing = 0.5.sp,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
            )

            otherDevices.forEach { device ->
                DeviceRowItem(
                    device = device,
                    isActive = false,
                    onPresetClick = {
                        pickingPresetForDeviceKey = if (pickingPresetForDeviceKey == device.deviceKey) null else device.deviceKey
                    },
                )

                if (pickingPresetForDeviceKey == device.deviceKey) {
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

        // subtle hint footer
        Spacer(modifier = Modifier.height(4.dp))
        HorizontalDivider(
            color = colors.cardBorder.copy(alpha = 0.5f),
            thickness = 0.5.dp,
            modifier = Modifier.padding(vertical = 4.dp),
        )

        Text(
            text = stringResource(R.string.routing_hint),
            fontFamily = WixFontFamily,
            fontSize = 9.sp,
            fontWeight = FontWeight.Normal,
            color = colors.textMuted.copy(alpha = 0.8f),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun DeviceRowItem(
    device: DevicePresetBinding,
    isActive: Boolean,
    onPresetClick: () -> Unit,
) {
    val colors = LocalEqColors.current
    val haptic = LocalHapticFeedback.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 5.dp),
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
                    if (isActive) colors.cardBorder else colors.cardBorder.copy(alpha = 0.5f),
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
                color = colors.textPrimary,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.width(3.dp))
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = colors.textMuted,
                modifier = Modifier.size(12.dp),
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
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surfaceElevated)
            .border(1.dp, colors.cardBorder, RoundedCornerShape(10.dp))
            .padding(4.dp),
    ) {
        allPresets.forEach { preset ->
            val isSelected = preset.name.equals(currentPresetName, ignoreCase = true)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isSelected) colors.accent.copy(alpha = 0.12f) else colors.surfaceElevated)
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelectPreset(preset.name)
                    }
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = preset.name,
                    fontFamily = BenzinFontFamily,
                    fontSize = 10.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) colors.accent else colors.textPrimary,
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
