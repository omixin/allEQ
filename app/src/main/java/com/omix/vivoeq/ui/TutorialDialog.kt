package com.omix.vivoeq.ui

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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

@Composable
fun TutorialDialog(
    shizukuRunning: Boolean,
    shizukuHasPermission: Boolean,
    whitelistApplied: Boolean,
    onRequestPermission: () -> Unit,
    onApplyWhitelist: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
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
                // Заголовок
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Настройка VivoEq",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "Чтобы эквалайзер работал на динамиках и не убивался в фоне OriginOS, выполни пару простых шагов:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp, bottom = 16.dp)
                )

                // Шаг 1: Shizuku статус
                TutorialStepItem(
                    stepNumber = "1",
                    title = "Запуск Shizuku",
                    description = if (shizukuRunning) "Shizuku активна и подключена" else "Открой Shizuku и запусти службу через беспроводную отладку (Wi-Fi)",
                    icon = Icons.Default.Security,
                    isDone = shizukuRunning,
                    actionButton = null
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Шаг 2: Разрешение Shizuku
                TutorialStepItem(
                    stepNumber = "2",
                    title = "Права доступа",
                    description = if (shizukuHasPermission) "Доступ к Shizuku получен" else "Предоставь приложению доступ к системным API",
                    icon = Icons.Default.CheckCircle,
                    isDone = shizukuHasPermission,
                    actionButton = {
                        if (!shizukuHasPermission) {
                            Button(
                                onClick = onRequestPermission,
                                enabled = shizukuRunning,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Выдать доступ в Shizuku")
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Шаг 3: Защита от выгрузки в OriginOS
                TutorialStepItem(
                    stepNumber = "3",
                    title = "Защита от фонового убийцы",
                    description = if (whitelistApplied) "Белый список Doze и фоновый режим активны" else "Команды shell разрешат приложению жить в фоне на Vivo/Samsung",
                    icon = Icons.Default.BatteryAlert,
                    isDone = whitelistApplied,
                    actionButton = {
                        if (shizukuHasPermission && !whitelistApplied) {
                            Button(
                                onClick = onApplyWhitelist,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Активировать защиту Vivo")
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Всё понятно, начать слушать!")
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
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDone) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(
                            if (isDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stepNumber,
                        color = MaterialTheme.colorScheme.surface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )

                if (isDone) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = if (actionButton != null) 8.dp else 0.dp)
            )

            actionButton?.invoke()
        }
    }
}
