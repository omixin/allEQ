package com.omix.vivoeq

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omix.vivoeq.model.EqualizerDefaults
import com.omix.vivoeq.service.AudioProcessorService
import com.omix.vivoeq.shizuku.ShizukuManager
import com.omix.vivoeq.ui.TutorialDialog
import com.omix.vivoeq.ui.theme.VivoeqTheme
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    private var service: AudioProcessorService? = null
    private var isBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = (binder as? AudioProcessorService.LocalBinder)?.getService()
            isBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            isBound = false
        }
    }

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        // Shizuku permission updated
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Запуск фонового аудио-сервиса
        val serviceIntent = Intent(this, AudioProcessorService::class.java).apply {
            action = AudioProcessorService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE)

        try {
            Shizuku.addRequestPermissionResultListener(permissionListener)
        } catch (_: Exception) {
        }

        val prefs = getSharedPreferences("vivoeq_prefs", Context.MODE_PRIVATE)
        val isFirstRun = prefs.getBoolean("is_first_run", true)

        setContent {
            VivoeqTheme {
                MainScreen(
                    isFirstRunInitial = isFirstRun,
                    onDismissTutorial = {
                        prefs.edit().putBoolean("is_first_run", false).apply()
                    },
                    onRequestShizuku = {
                        ShizukuManager.requestPermission()
                    },
                    onApplyWhitelist = {
                        val res = ShizukuManager.applyOriginOsWhitelist(this)
                        if (res.isSuccess) {
                            Toast.makeText(this, "Защита Vivo успешно активирована в системе!", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(this, "Ошибка: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                        }
                    },
                    onToggleMaster = { service?.toggleEnabled() },
                    onSelectPreset = { name -> service?.applyPreset(name) },
                    onBandChanged = { index, gain -> service?.updateBand(index, gain) },
                    onPreAmpChanged = { db -> service?.setPreAmp(db) },
                    onBassBoostChanged = { percent -> service?.setBassBoost(percent) },
                    onLimiterToggle = { enabled -> service?.setLimiterEnabled(enabled) },
                    onResetBands = { service?.updateBands(List(10) { 0f }) },
                    onCopyDiagnostics = {
                        val report = """
                            === VIVOEQ ОТЧЕТ ===
                            Устройство: ${Build.MANUFACTURER} ${Build.MODEL}
                            Прошивка: ${Build.DISPLAY} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})
                            Shizuku: Служба=${ShizukuManager.isRunning()}, Права=${ShizukuManager.hasPermission()}
                            Активных аудиосессий: ${AudioProcessorService.activeSessionsCount.value}
                            Пресет: ${AudioProcessorService.activePresetName.value}
                            Pre-Amp: ${AudioProcessorService.preAmpDb.value} dB
                            Bass Boost: ${AudioProcessorService.bassBoostPercent.value}%
                            Limiter: ${AudioProcessorService.limiterEnabled.value}
                            Полосы dB: ${AudioProcessorService.bandGains.value.map { it.toInt() }}
                        """.trimIndent()

                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("VivoEq Log", report)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(this, "Диагностика скопирована в буфер!", Toast.LENGTH_LONG).show()
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        try {
            Shizuku.removeRequestPermissionResultListener(permissionListener)
        } catch (_: Exception) {
        }
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    isFirstRunInitial: Boolean,
    onDismissTutorial: () -> Unit,
    onRequestShizuku: () -> Unit,
    onApplyWhitelist: () -> Unit,
    onToggleMaster: () -> Unit,
    onSelectPreset: (String) -> Unit,
    onBandChanged: (Int, Float) -> Unit,
    onPreAmpChanged: (Float) -> Unit,
    onBassBoostChanged: (Float) -> Unit,
    onLimiterToggle: (Boolean) -> Unit,
    onResetBands: () -> Unit,
    onCopyDiagnostics: () -> Unit
) {
    val isEnabled by AudioProcessorService.isEnabled.collectAsState()
    val bandGains by AudioProcessorService.bandGains.collectAsState()
    val preAmpDb by AudioProcessorService.preAmpDb.collectAsState()
    val bassBoostPercent by AudioProcessorService.bassBoostPercent.collectAsState()
    val limiterEnabled by AudioProcessorService.limiterEnabled.collectAsState()
    val activePresetName by AudioProcessorService.activePresetName.collectAsState()
    val activeSessionsCount by AudioProcessorService.activeSessionsCount.collectAsState()

    var showTutorial by remember { mutableStateOf(isFirstRunInitial) }
    var shizukuRunning by remember { mutableStateOf(ShizukuManager.isRunning()) }
    var shizukuHasPermission by remember { mutableStateOf(ShizukuManager.hasPermission()) }
    var whitelistApplied by remember { mutableStateOf(false) }

    // Обновляем статус Shizuku периодически
    LaunchedEffect(Unit) {
        while (true) {
            shizukuRunning = ShizukuManager.isRunning()
            shizukuHasPermission = ShizukuManager.hasPermission()
            kotlinx.coroutines.delay(2000)
        }
    }

    if (showTutorial) {
        TutorialDialog(
            shizukuRunning = shizukuRunning,
            shizukuHasPermission = shizukuHasPermission,
            whitelistApplied = whitelistApplied,
            onRequestPermission = {
                onRequestShizuku()
                shizukuHasPermission = ShizukuManager.hasPermission()
            },
            onApplyWhitelist = {
                onApplyWhitelist()
                whitelistApplied = true
            },
            onDismiss = {
                showTutorial = false
                onDismissTutorial()
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Equalizer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "VivoEq",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                },
                actions = {
                    // Статус Shizuku
                    AssistChip(
                        onClick = { showTutorial = true },
                        label = {
                            Text(
                                text = if (shizukuHasPermission) "Shizuku ✓" else "Shizuku ✗",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        if (shizukuHasPermission) Color(0xFF4CAF50) else Color(0xFFFF9800),
                                        CircleShape
                                    )
                            )
                        },
                        modifier = Modifier.padding(end = 4.dp)
                    )

                    // Кнопка копирования диагностики
                    IconButton(onClick = onCopyDiagnostics) {
                        Icon(
                            imageVector = Icons.Default.BugReport,
                            contentDescription = "Скопировать диагностику",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }

                    // Кнопка защиты от выгрузки Vivo
                    IconButton(onClick = onApplyWhitelist) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Защита Vivo",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Кнопка туториала
                    IconButton(onClick = { showTutorial = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                            contentDescription = "Справка"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // 1. Главный переключатель питания
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isEnabled) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = if (isEnabled) "Эквалайзер включен" else "Эквалайзер выключен",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isEnabled) "Обработка активна • Сессий: $activeSessionsCount" else "Звук идет без изменений",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = { onToggleMaster() }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Пресеты
            Text(
                text = "Пресеты звучания",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EqualizerDefaults.PRESETS.forEach { preset ->
                    val isSelected = activePresetName == preset.name
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectPreset(preset.name) },
                        label = { Text(preset.name, fontSize = 13.sp) },
                        leadingIcon = if (isSelected) {
                            {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else null
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 3. Модули Pre-Amp, Bass Boost и Limiter
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Pre-Amp
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Pre-Amp (Усиление)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text("${if (preAmpDb > 0) "+" else ""}${String.format("%.1f", preAmpDb)} dB", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = preAmpDb,
                        onValueChange = onPreAmpChanged,
                        valueRange = -12f..12f,
                        steps = 23
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Bass Boost
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Bass Boost (Динамики)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text("${bassBoostPercent.toInt()}%", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = bassBoostPercent,
                        onValueChange = onBassBoostChanged,
                        valueRange = 0f..100f
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Limiter (Анти-хрип)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Anti-Clip Limiter", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text("Защита динамиков от треска и перегрузок", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = limiterEnabled,
                            onCheckedChange = onLimiterToggle
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 4. 10-полосный эквалайзер
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "10 Полос (DynamicsProcessing)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = onResetBands) {
                    Text("Сброс в 0 dB", fontSize = 12.sp)
                }
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp, horizontal = 8.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    EqualizerDefaults.BANDS.forEach { band ->
                        val currentGain = bandGains.getOrElse(band.index) { 0f }
                        BandSliderColumn(
                            band = band,
                            gain = currentGain,
                            onGainChanged = { newGain ->
                                onBandChanged(band.index, newGain)
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun BandSliderColumn(
    band: com.omix.vivoeq.model.EqualizerBand,
    gain: Float,
    onGainChanged: (Float) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(62.dp)
    ) {
        // Значение усиления (+3 dB / -2 dB)
        Text(
            text = "${if (gain > 0) "+" else ""}${gain.toInt()} dB",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (gain != 0f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Вертикальный слайдер
        Box(
            modifier = Modifier
                .height(180.dp)
                .width(42.dp),
            contentAlignment = Alignment.Center
        ) {
            Slider(
                value = gain,
                onValueChange = onGainChanged,
                valueRange = -15f..15f,
                modifier = Modifier
                    .requiredWidth(170.dp)
                    .rotate(-90f)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Частота (31 Hz, 1 kHz...)
        Text(
            text = band.label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
    }
}