package com.omix.alleq.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.omix.alleq.model.EqualizerDefaults
import com.omix.alleq.model.EqualizerPreset
import com.omix.alleq.service.AudioProcessorService
import com.omix.alleq.ui.theme.BenzinFontFamily
import com.omix.alleq.ui.theme.LocalEqColors
import com.omix.alleq.ui.theme.AllEqTheme
import com.omix.alleq.ui.theme.WixFontFamily
import com.omix.alleq.widget.AllEqWidgetManager
import kotlinx.coroutines.delay

class FloatingPresetActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AllEqTheme {
                FloatingPresetContent(onDismiss = { finish() })
            }
        }
    }
}

@Composable
private fun FloatingPresetContent(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val colors = LocalEqColors.current
    val haptic = LocalHapticFeedback.current

    val allPresetsState by AudioProcessorService.allPresets.collectAsState()
    val activePresetNameState by AudioProcessorService.activePresetName.collectAsState()
    val currentAudioDeviceState by AudioProcessorService.currentAudioDevice.collectAsState()

    // fallback to saved prefs if service is cold
    val presets = remember(allPresetsState) {
        if (allPresetsState.isNotEmpty()) allPresetsState else {
            val prefs = context.getSharedPreferences("alleq_audio_state", Context.MODE_PRIVATE)
            val json = prefs.getString("all_presets_json", null)
            EqualizerDefaults.allPresetsFromJson(json).ifEmpty { EqualizerDefaults.PRESETS }
        }
    }

    var selectedPresetName by remember(activePresetNameState) {
        mutableStateOf(activePresetNameState)
    }

    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isVisible = true
    }

    fun applyPreset(name: String) {
        selectedPresetName = name
        val service = AudioProcessorService.instance
        if (service != null) {
            service.applyPreset(name, isAutoSwitch = false)
        } else {
            val intent = Intent(context, AudioProcessorService::class.java).apply {
                action = AudioProcessorService.ACTION_APPLY_PRESET
                putExtra(AudioProcessorService.EXTRA_PRESET_NAME, name)
            }
            ContextCompat.startForegroundService(context, intent)
        }
        AllEqWidgetManager.updateWidgets(context)
    }

    val scrimAlpha by animateFloatAsState(
        targetValue = if (isVisible) 0.45f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "scrim_alpha",
    )

    val scale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.92f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 420f),
        label = "dialog_scale",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = scrimAlpha))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                onDismiss()
            }
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 380.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    alpha = if (isVisible) 1f else 0f
                }
                .shadow(
                    elevation = 28.dp,
                    shape = RoundedCornerShape(22.dp),
                    spotColor = colors.accent.copy(alpha = 0.28f),
                    ambientColor = Color.Black,
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    // prevent dismiss when clicking inside the card
                },
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = colors.cardBg),
            border = BorderStroke(1.dp, colors.cardBorder),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Header with glowing icon & device pill
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(colors.accent.copy(alpha = 0.14f))
                                .border(1.dp, colors.accent.copy(alpha = 0.35f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "SELECT PRESET",
                            fontFamily = BenzinFontFamily,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            color = colors.textPrimary,
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.surfaceElevated)
                            .border(1.dp, colors.cardBorder, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = currentAudioDeviceState.uppercase(),
                            fontFamily = WixFontFamily,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // The calibrated 3D Cylindrical Wheel Picker
                PresetPickerWheel(
                    currentPresetName = selectedPresetName,
                    allPresets = presets,
                    onSelectPreset = { name, _ ->
                        applyPreset(name)
                    },
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Done Button
                AllEqDialogButton(
                    onClick = onDismiss,
                    text = "Done",
                    variant = AllEqButtonVariant.PRIMARY,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
