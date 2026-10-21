package com.omix.alleq.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import com.omix.alleq.ui.haptics.AppHaptics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.omix.alleq.ui.theme.BenzinFontFamily
import com.omix.alleq.ui.theme.LocalEqColors
import com.omix.alleq.ui.theme.WixFontFamily

enum class AllEqButtonVariant {
    PRIMARY,
    SECONDARY,
    DESTRUCTIVE,
    GHOST,
}

@Composable
fun AllEqCustomDialog(
    onDismissRequest: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color? = null,
    buttons: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalEqColors.current
    val effectiveIconTint = iconTint ?: colors.accent

    var animateIn by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        animateIn = true
    }

    val scale by animateFloatAsState(
        targetValue = if (animateIn) 1f else 0.92f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 420f),
        label = "dialog_scale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (animateIn) 1f else 0f,
        animationSpec = tween(durationMillis = 160),
        label = "dialog_alpha",
    )

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Card(
                modifier = modifier
                    .fillMaxWidth()
                    .widthIn(max = 380.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                    }
                    .shadow(
                        elevation = 24.dp,
                        shape = RoundedCornerShape(22.dp),
                        spotColor = effectiveIconTint.copy(alpha = 0.22f),
                        ambientColor = Color.Black,
                    ),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = colors.cardBg),
                border = BorderStroke(1.dp, colors.cardBorder),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (icon != null) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(effectiveIconTint.copy(alpha = 0.12f))
                                .border(1.dp, effectiveIconTint.copy(alpha = 0.35f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = effectiveIconTint,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    Text(
                        text = title.uppercase(),
                        fontFamily = BenzinFontFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        color = colors.textPrimary,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    content()

                    Spacer(modifier = Modifier.height(20.dp))

                    buttons()
                }
            }
        }
    }
}

@Composable
fun AllEqDialogButton(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    variant: AllEqButtonVariant = AllEqButtonVariant.PRIMARY,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    height: Dp = 42.dp,
) {
    val colors = LocalEqColors.current
    val context = LocalContext.current
    val view = LocalView.current
    val haptic = LocalHapticFeedback.current

    val containerColor = when (variant) {
        AllEqButtonVariant.PRIMARY -> colors.accent
        AllEqButtonVariant.SECONDARY -> colors.surfaceElevated
        AllEqButtonVariant.DESTRUCTIVE -> Color(0xFFFF3B30).copy(alpha = 0.14f)
        AllEqButtonVariant.GHOST -> Color.Transparent
    }

    val contentColor = when (variant) {
        AllEqButtonVariant.PRIMARY -> if (colors.accent.luminance() < 0.45f) Color.White else Color.Black
        AllEqButtonVariant.SECONDARY -> colors.textPrimary
        AllEqButtonVariant.DESTRUCTIVE -> Color(0xFFFF5252)
        AllEqButtonVariant.GHOST -> colors.textSecondary
    }

    val borderStroke = when (variant) {
        AllEqButtonVariant.PRIMARY -> null
        AllEqButtonVariant.SECONDARY -> BorderStroke(1.dp, colors.cardBorder)
        AllEqButtonVariant.DESTRUCTIVE -> BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.38f))
        AllEqButtonVariant.GHOST -> null
    }

    val shape = RoundedCornerShape(12.dp)

    Box(
        modifier = modifier
            .height(height)
            .clip(shape)
            .then(if (containerColor != Color.Transparent) Modifier.background(containerColor) else Modifier)
            .then(if (borderStroke != null) Modifier.border(borderStroke, shape) else Modifier)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                if (variant == AllEqButtonVariant.DESTRUCTIVE) {
                    AppHaptics.heavyClick(view, context)
                } else {
                    AppHaptics.click(view, context)
                }
                onClick()
            }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (enabled) contentColor else contentColor.copy(alpha = 0.4f),
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = text,
                fontFamily = WixFontFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) contentColor else contentColor.copy(alpha = 0.4f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun AllEqDialogTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    onDone: (() -> Unit)? = null,
    singleLine: Boolean = true,
) {
    val colors = LocalEqColors.current
    val haptic = LocalHapticFeedback.current
    var isFocused by remember { mutableStateOf(false) }

    val borderColor by animateColorAsState(
        targetValue = if (isFocused) colors.accent else colors.cardBorder,
        animationSpec = tween(durationMillis = 180),
        label = "field_border",
    )

    val shape = RoundedCornerShape(12.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(shape)
            .background(colors.surfaceElevated)
            .border(1.dp, borderColor, shape)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty() && placeholder.isNotEmpty()) {
                    Text(
                        text = placeholder,
                        fontFamily = WixFontFamily,
                        fontSize = 14.sp,
                        color = colors.textMuted,
                    )
                }

                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { isFocused = it.isFocused },
                    singleLine = singleLine,
                    textStyle = TextStyle(
                        fontFamily = WixFontFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.textPrimary,
                    ),
                    cursorBrush = SolidColor(colors.accent),
                    keyboardOptions = KeyboardOptions(
                        imeAction = if (onDone != null) ImeAction.Done else ImeAction.Default,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { onDone?.invoke() },
                    ),
                )
            }

            if (value.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            if (com.omix.alleq.ui.haptics.AppHaptics.enabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onValueChange("")
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear",
                        tint = colors.textMuted,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}
