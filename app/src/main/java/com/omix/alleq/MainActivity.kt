package com.omix.alleq

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import java.io.File
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.activity.compose.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.net.Uri
import com.omix.alleq.model.EqualizerDefaults
import com.omix.alleq.model.EqualizerPreset
import com.omix.alleq.service.AudioProcessorService
import com.omix.alleq.shizuku.ShizukuManager
import com.omix.alleq.telemetry.TelemetryManager
import com.omix.alleq.ui.PresetManagerSheet
import com.omix.alleq.ui.SettingsSheet
import com.omix.alleq.ui.TactileCapsuleFader
import com.omix.alleq.ui.TutorialDialog
import com.omix.alleq.ui.WaveformEqualizerCard
import com.omix.alleq.ui.theme.AccentTheme
import com.omix.alleq.ui.theme.BenzinFontFamily
import com.omix.alleq.ui.theme.LocalEqColors
import com.omix.alleq.ui.theme.UiScale
import com.omix.alleq.ui.theme.AllEqTheme
import com.omix.alleq.ui.theme.WixFontFamily
import kotlin.math.roundToInt
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

    private var shizukuRunning by mutableStateOf(ShizukuManager.isRunning())
    private var shizukuHasPermission by mutableStateOf(ShizukuManager.hasPermission())
    private var isBatteryOptimizationIgnored by mutableStateOf(false)
    private var pendingImportPresets by mutableStateOf<List<EqualizerPreset>?>(null)

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { _, _ ->
        shizukuHasPermission = ShizukuManager.hasPermission()
    }
    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        shizukuRunning = ShizukuManager.isRunning()
        shizukuHasPermission = ShizukuManager.hasPermission()
    }
    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        shizukuRunning = false
        shizukuHasPermission = false
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    // handle intent with .alleq file payload
    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_VIEW -> {
                handleIncomingPresetUri(intent.data)
            }
            Intent.ACTION_SEND -> {
                val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                handleIncomingPresetUri(uri)
            }
        }
    }

    // read .alleq file contents from uri and parse presets
    private fun handleIncomingPresetUri(uri: Uri?) {
        if (uri == null) return
        try {
            val content = contentResolver.openInputStream(uri)?.use { stream ->
                stream.bufferedReader().readText()
            }
            val parsed = EqualizerDefaults.parseAlleqPayload(content)
            if (parsed.isNotEmpty()) {
                pendingImportPresets = parsed
            } else {
                Toast.makeText(this, getString(R.string.toast_import_failed), Toast.LENGTH_SHORT).show()
            }
        } catch (_: Exception) {
            Toast.makeText(this, getString(R.string.toast_import_failed), Toast.LENGTH_SHORT).show()
        }
    }

    // share a single preset as a .alleq file
    private fun sharePreset(preset: EqualizerPreset) {
        try {
            val presetsDir = File(cacheDir, "presets").apply { mkdirs() }
            val sanitized = preset.name.trim().replace("[\\\\/:*?\"<>|\\x00-\\x1F]".toRegex(), "_")
            val cleanName = sanitized.ifBlank { "preset" }
            val file = File(presetsDir, "$cleanName.alleq")
            file.writeText(EqualizerDefaults.presetToAlleqJson(preset))

            val uri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "allEQ Preset: ${preset.name}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, getString(R.string.share_preset_chooser_title)))
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to share preset: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // share all custom presets as a .alleq pack file
    private fun exportAllPresets(presets: List<EqualizerPreset>) {
        try {
            if (presets.isEmpty()) {
                Toast.makeText(this, "No custom presets to export", Toast.LENGTH_SHORT).show()
                return
            }
            val presetsDir = File(cacheDir, "presets").apply { mkdirs() }
            val file = File(presetsDir, "allEQ_Presets_Backup.alleq")
            file.writeText(EqualizerDefaults.presetPackToAlleqJson(presets))

            val uri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "allEQ Presets Backup")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, getString(R.string.share_preset_chooser_title)))
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to export presets: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        shizukuRunning = ShizukuManager.isRunning()
        shizukuHasPermission = ShizukuManager.hasPermission()
        isBatteryOptimizationIgnored = ShizukuManager.isBatteryOptimizationIgnored(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isBatteryOptimizationIgnored = ShizukuManager.isBatteryOptimizationIgnored(this)
        handleIncomingIntent(intent)

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
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(permissionListener)
        } catch (_: Exception) {
        }

        val prefs = getSharedPreferences("alleq_prefs", Context.MODE_PRIVATE)
        val isFirstRun = prefs.getBoolean("is_first_run", true)

        val savedScaleName = prefs.getString("ui_scale", UiScale.STANDARD.name) ?: UiScale.STANDARD.name
        val initialUiScale = try { UiScale.valueOf(savedScaleName) } catch (_: Exception) { UiScale.STANDARD }

        val savedAccentName = prefs.getString("accent_theme", AccentTheme.GREEN.name) ?: AccentTheme.GREEN.name
        val initialAccentTheme = try { AccentTheme.valueOf(savedAccentName) } catch (_: Exception) { AccentTheme.GREEN }

        val savedCustomColor = prefs.getInt("custom_accent_color", 0xFFE91E63.toInt())
        val initialCustomColor = Color(savedCustomColor)

        setContent {
            var currentUiScale by remember { mutableStateOf(initialUiScale) }
            var currentAccentTheme by remember { mutableStateOf(initialAccentTheme) }
            var customAccentColor by remember { mutableStateOf(initialCustomColor) }

            AllEqTheme(
                accentTheme = currentAccentTheme,
                customAccentColor = customAccentColor,
                uiScale = currentUiScale
            ) {
                MainScreen(
                    isFirstRunInitial = isFirstRun,
                    shizukuRunning = shizukuRunning,
                    shizukuHasPermission = shizukuHasPermission,
                    isBatteryOptimizationIgnored = isBatteryOptimizationIgnored,
                    currentUiScale = currentUiScale,
                    onUiScaleChanged = { scale ->
                        currentUiScale = scale
                        prefs.edit().putString("ui_scale", scale.name).apply()
                    },
                    currentAccentTheme = currentAccentTheme,
                    onAccentThemeChanged = { theme ->
                        currentAccentTheme = theme
                        prefs.edit().putString("accent_theme", theme.name).apply()
                    },
                    customAccentColor = customAccentColor,
                    onCustomColorChanged = { color ->
                        customAccentColor = color
                        prefs.edit().putInt("custom_accent_color", color.toArgb()).apply()
                    },
                    onOpenGitHubIssues = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/omixin/allEQ/issues"))
                        startActivity(intent)
                    },
                    onDismissTutorial = {
                        prefs.edit().putBoolean("is_first_run", false).apply()
                    },
                    onRequestShizuku = {
                        ShizukuManager.requestPermission()
                    },
                    onApplyWhitelist = {
                        lifecycleScope.launch {
                            var shizukuSuccess = false
                            if (ShizukuManager.hasPermission()) {
                                val res = withContext(Dispatchers.IO) {
                                    ShizukuManager.applyOriginOsWhitelist(this@MainActivity)
                                }
                                shizukuSuccess = res.isSuccess
                            }

                            val isIgnored = ShizukuManager.isBatteryOptimizationIgnored(this@MainActivity)
                            if (!isIgnored) {
                                try {
                                    val intent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                        data = Uri.parse("package:$packageName")
                                    }
                                    startActivity(intent)
                                } catch (_: Exception) {
                                    try {
                                        startActivity(Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                                    } catch (_: Exception) {}
                                }
                            } else if (shizukuSuccess || isIgnored) {
                                Toast.makeText(this@MainActivity, getString(R.string.toast_protection_activated), Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    onToggleMaster = { service?.toggleEnabled() },
                    onSelectPreset = { name -> service?.applyPreset(name) },
                    onSaveCustomPreset = { name -> service?.saveCustomPreset(name) },
                    onDeleteCustomPreset = { name -> service?.deleteCustomPreset(name) },
                    onReorderPresets = { list -> service?.reorderPresets(list) },
                    onDeletePreset = { name -> service?.deletePreset(name) },
                    onRestorePreset = { name -> service?.restorePreset(name) },
                    onResetPresets = { service?.resetPresetsToDefault() },
                    onSetTemporaryBypass = { bypass -> service?.setTemporaryBypass(bypass) },
                    onBandChanged = { index, gain -> service?.updateBand(index, gain) },
                    onPreAmpChanged = { db -> service?.setPreAmp(db) },
                    onBassBoostChanged = { percent -> service?.setBassBoost(percent) },
                    onDynamicBassToggle = { enabled -> service?.setDynamicBassEnabled(enabled) },
                    onDynamicBassStrengthChanged = { strength -> service?.setDynamicBassStrength(strength) },
                    onCopyDiagnostics = {
                        val report = service?.getDiagnosticsReport() ?: """
                            === allEQ SYSTEM DIAGNOSTIC REPORT ===
                            Service Offline.
                            Device: ${Build.MANUFACTURER} ${Build.MODEL}
                            Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})
                            Shizuku: Running=${ShizukuManager.isRunning()}, Granted=${ShizukuManager.hasPermission()}
                        """.trimIndent()

                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("allEQ Diagnostic Report", report)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(this, getString(R.string.toast_report_copied), Toast.LENGTH_LONG).show()
                    },
                    pendingImportPresets = pendingImportPresets,
                    onDismissImportDialog = { pendingImportPresets = null },
                    onImportSinglePreset = { preset, applyNow ->
                        val ok = service?.importPreset(preset, applyNow) ?: false
                        if (ok) {
                            Toast.makeText(this@MainActivity, getString(R.string.toast_preset_imported, preset.name), Toast.LENGTH_SHORT).show()
                        }
                    },
                    onImportPresetsPack = { pack ->
                        val count = service?.importPresets(pack) ?: 0
                        if (count > 0) {
                            Toast.makeText(this@MainActivity, getString(R.string.toast_preset_pack_imported, count), Toast.LENGTH_SHORT).show()
                        }
                    },
                    onSharePreset = { preset -> sharePreset(preset) },
                    onExportAllPresets = { presets -> exportAllPresets(presets) },
                    onFilePicked = { uri -> handleIncomingPresetUri(uri) }
                )
            }
        }
    }

    override fun onDestroy() {
        try {
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            Shizuku.removeBinderDeadListener(binderDeadListener)
            Shizuku.removeRequestPermissionResultListener(permissionListener)
        } catch (_: Exception) {
        }
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
        service = null
        super.onDestroy()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainScreen(
    isFirstRunInitial: Boolean,
    shizukuRunning: Boolean,
    shizukuHasPermission: Boolean,
    isBatteryOptimizationIgnored: Boolean = false,
    currentUiScale: UiScale,
    onUiScaleChanged: (UiScale) -> Unit,
    currentAccentTheme: AccentTheme,
    onAccentThemeChanged: (AccentTheme) -> Unit,
    customAccentColor: Color,
    onCustomColorChanged: (Color) -> Unit,
    onOpenGitHubIssues: () -> Unit,
    onDismissTutorial: () -> Unit,
    onRequestShizuku: () -> Unit,
    onApplyWhitelist: () -> Unit,
    onToggleMaster: () -> Unit,
    onSelectPreset: (String) -> Unit,
    onSaveCustomPreset: (String) -> Unit,
    onDeleteCustomPreset: (String) -> Unit,
    onReorderPresets: (List<EqualizerPreset>) -> Unit,
    onDeletePreset: (String) -> Unit,
    onRestorePreset: (String) -> Unit,
    onResetPresets: () -> Unit,
    onSetTemporaryBypass: (Boolean) -> Unit,
    onBandChanged: (Int, Float) -> Unit,
    onPreAmpChanged: (Float) -> Unit,
    onBassBoostChanged: (Float) -> Unit,
    onDynamicBassToggle: (Boolean) -> Unit,
    onDynamicBassStrengthChanged: (Float) -> Unit,
    onCopyDiagnostics: () -> Unit,
    pendingImportPresets: List<EqualizerPreset>? = null,
    onDismissImportDialog: () -> Unit = {},
    onImportSinglePreset: (EqualizerPreset, Boolean) -> Unit = { _, _ -> },
    onImportPresetsPack: (List<EqualizerPreset>) -> Unit = {},
    onSharePreset: (EqualizerPreset) -> Unit = {},
    onExportAllPresets: (List<EqualizerPreset>) -> Unit = {},
    onFilePicked: (Uri?) -> Unit = {}
) {
    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        onFilePicked(uri)
    }

    val isEnabled by AudioProcessorService.isEnabled.collectAsState()
    val bandGains by AudioProcessorService.bandGains.collectAsState()
    val preAmpDb by AudioProcessorService.preAmpDb.collectAsState()
    val bassBoostPercent by AudioProcessorService.bassBoostPercent.collectAsState()
    val dynamicBassEnabled by AudioProcessorService.dynamicBassEnabled.collectAsState()
    val dynamicBassStrength by AudioProcessorService.dynamicBassStrength.collectAsState()
    val dynamicBassModeStatus by AudioProcessorService.dynamicBassModeStatus.collectAsState()
    val currentVolumeRatio by AudioProcessorService.currentVolumeRatio.collectAsState()
    val activePresetName by AudioProcessorService.activePresetName.collectAsState()
    val allPresets by AudioProcessorService.allPresets.collectAsState()
    val deletedPresets by AudioProcessorService.deletedPresets.collectAsState()
    val customPresets by AudioProcessorService.customPresets.collectAsState()
    val currentAudioDevice by AudioProcessorService.currentAudioDevice.collectAsState()
    val isTemporaryBypass by AudioProcessorService.isTemporaryBypass.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var newPresetName by remember { mutableStateOf("") }
    var presetToDelete by remember { mutableStateOf<String?>(null) }
    var pendingPresetSwitch by remember { mutableStateOf<String?>(null) }

    val currentPresetObj = remember(activePresetName, allPresets) {
        allPresets.firstOrNull { it.name.equals(activePresetName, ignoreCase = true) }
    }

    val isCurrentPresetModified = remember(currentPresetObj, bandGains, preAmpDb, bassBoostPercent) {
        currentPresetObj?.let { p ->
            val gainsDiff = p.bandGains.zip(bandGains).any { (a, b) -> kotlin.math.abs(a - b) > 0.05f }
            val preAmpDiff = kotlin.math.abs(p.preAmpDb - preAmpDb) > 0.05f
            val bassDiff = kotlin.math.abs(p.bassBoostPercent - bassBoostPercent) > 0.1f
            gainsDiff || preAmpDiff || bassDiff
        } ?: false
    }

    var showSettingsSheet by remember { mutableStateOf(false) }
    var showPresetManagerSheet by remember { mutableStateOf(false) }
    var showTutorial by remember { mutableStateOf(isFirstRunInitial) }
    var whitelistApplied by remember(isBatteryOptimizationIgnored) { mutableStateOf(isBatteryOptimizationIgnored) }

    var lastActiveBassBoostPercent by remember { mutableStateOf(if (bassBoostPercent > 0f) bassBoostPercent else 35f) }
    var lastActivePreAmpDb by remember { mutableStateOf(if (preAmpDb != 0f) preAmpDb else 2.0f) }

    LaunchedEffect(bassBoostPercent) {
        if (bassBoostPercent > 0f) {
            lastActiveBassBoostPercent = bassBoostPercent
        }
    }

    LaunchedEffect(preAmpDb) {
        if (preAmpDb != 0f) {
            lastActivePreAmpDb = preAmpDb
        }
    }

    val colors = LocalEqColors.current
    val haptic = LocalHapticFeedback.current
    var activeEditingFader by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = pendingPresetSwitch != null) {
        pendingPresetSwitch = null
    }

    BackHandler(enabled = activeEditingFader != null) {
        activeEditingFader = null
    }

    BackHandler(enabled = showPresetManagerSheet) {
        showPresetManagerSheet = false
    }

    BackHandler(enabled = showSettingsSheet && !showPresetManagerSheet) {
        showSettingsSheet = false
    }

    BackHandler(enabled = showTutorial) {
        showTutorial = false
        onDismissTutorial()
    }

    BackHandler(enabled = showCreateDialog) {
        showCreateDialog = false
    }

    BackHandler(enabled = presetToDelete != null) {
        presetToDelete = null
    }

    BackHandler(enabled = pendingImportPresets != null) {
        onDismissImportDialog()
    }

    if (showSettingsSheet) {
        SettingsSheet(
            onDismiss = { showSettingsSheet = false },
            shizukuRunning = shizukuRunning,
            shizukuHasPermission = shizukuHasPermission,
            whitelistApplied = whitelistApplied,
            allPresetsCount = allPresets.size,
            currentUiScale = currentUiScale,
            onUiScaleChanged = onUiScaleChanged,
            currentAccentTheme = currentAccentTheme,
            onAccentThemeChanged = onAccentThemeChanged,
            customAccentColor = customAccentColor,
            onCustomColorChanged = onCustomColorChanged,
            onRequestShizuku = onRequestShizuku,
            onApplyWhitelist = {
                onApplyWhitelist()
                whitelistApplied = true
            },
            onOpenPresetManager = {
                showSettingsSheet = false
                showPresetManagerSheet = true
            },
            onCopyDiagnostics = onCopyDiagnostics,
            onOpenTutorial = {
                showSettingsSheet = false
                showTutorial = true
            },
            onOpenGitHubIssues = onOpenGitHubIssues
        )
    }

    if (showPresetManagerSheet) {
        PresetManagerSheet(
            onDismiss = { showPresetManagerSheet = false },
            allPresets = allPresets,
            deletedPresets = deletedPresets,
            activePresetName = activePresetName,
            onReorder = onReorderPresets,
            onDelete = onDeletePreset,
            onRestore = onRestorePreset,
            onResetToDefaults = onResetPresets,
            onSharePreset = onSharePreset,
            onExportAllPresets = { onExportAllPresets(customPresets) },
            onImportPresetsFile = { filePickerLauncher.launch("*/*") }
        )
    }

    if (showTutorial) {
        TutorialDialog(
            shizukuRunning = shizukuRunning,
            shizukuHasPermission = shizukuHasPermission,
            whitelistApplied = whitelistApplied,
            onRequestPermission = onRequestShizuku,
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

    Surface(
        color = colors.background,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(colors.cardBg)
                            .border(1.dp, colors.cardBorder, CircleShape)
                            .clickable { showSettingsSheet = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 2.dp, end = 2.dp)
                            .size(10.dp)
                            .background(
                                if (shizukuHasPermission) Color(0xFF4CAF50) else Color(0xFFFF9800),
                                CircleShape
                            )
                            .border(1.5.dp, colors.background, CircleShape)
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "allEQ",
                        fontFamily = BenzinFontFamily,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = colors.textPrimary,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = getLocalizedAudioDevice(currentAudioDevice),
                        fontFamily = WixFontFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val powerIconColor = if (isEnabled) {
                        if (colors.accent.luminance() < 0.45f) Color.White else Color.Black
                    } else colors.textMuted
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(
                                if (isEnabled) colors.accent
                                else colors.cardBg
                            )
                            .border(
                                1.dp,
                                if (isEnabled) colors.accent
                                else colors.cardBorder,
                                CircleShape
                            )
                            .clickable { onToggleMaster() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = "Power",
                            tint = powerIconColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(colors.cardBg)
                        .border(1.dp, colors.cardBorder, RoundedCornerShape(50))
                        .clickable {
                            newPresetName = "Preset ${customPresets.size + 1}"
                            showCreateDialog = true
                        }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "New Preset",
                            tint = colors.accent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.btn_new_preset),
                            fontFamily = WixFontFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary
                        )
                    }
                }

                allPresets.forEach { preset ->
                    val isSelected = activePresetName.equals(preset.name, ignoreCase = true)
                    val showUnsavedDot = isSelected && isCurrentPresetModified
                    val selectedTextColor = if (isSelected) {
                        if (colors.accent.luminance() < 0.45f) Color.White else Color.Black
                    } else colors.textSecondary

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (isSelected) colors.accent
                                else colors.cardBg
                            )
                            .border(
                                1.dp,
                                if (isSelected) colors.accent
                                else colors.cardBorder,
                                RoundedCornerShape(50)
                            )
                            .combinedClickable(
                                onClick = {
                                    if (!isSelected) {
                                        if (isCurrentPresetModified) {
                                            pendingPresetSwitch = preset.name
                                        } else {
                                            onSelectPreset(preset.name)
                                        }
                                    }
                                },
                                onLongClick = if (preset.isCustom) {
                                    {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        presetToDelete = preset.name
                                    }
                                } else null
                            )
                            .padding(
                                horizontal = 16.dp,
                                vertical = 9.dp
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = preset.name.uppercase(),
                                fontFamily = BenzinFontFamily,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = selectedTextColor
                            )
                            if (showUnsavedDot) {
                                Spacer(modifier = Modifier.width(5.dp))
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(Color(0xFFFF9800), CircleShape)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isTemporaryBypass) Color(0xFF381515) else colors.cardBg)
                    .border(
                        width = 1.dp,
                        color = if (isTemporaryBypass) Color(0xFFFF5252) else colors.cardBorder,
                        shape = RoundedCornerShape(16.dp)
                    )
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSetTemporaryBypass(true)
                                tryAwaitRelease()
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSetTemporaryBypass(false)
                            }
                        )
                    }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isTemporaryBypass) Icons.AutoMirrored.Filled.VolumeMute else Icons.AutoMirrored.Filled.CompareArrows,
                        contentDescription = "A/B",
                        tint = if (isTemporaryBypass) Color(0xFFFF5252) else colors.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isTemporaryBypass) androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.bypass_active) else androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.hold_to_bypass),
                        fontFamily = WixFontFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isTemporaryBypass) Color(0xFFFF5252) else colors.textSecondary,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            WaveformEqualizerCard(
                presetName = activePresetName,
                bandGains = bandGains,
                onBandGainChanged = onBandChanged,
                isModified = isCurrentPresetModified,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                TactileCapsuleFader(
                    title = "BASS BOOST",
                    valueText = "${bassBoostPercent.toInt()}%",
                    progress = bassBoostPercent / 100f,
                    onProgressChanged = { newProgress ->
                        val percent = (newProgress * 100f).roundToInt().toFloat()
                        onBassBoostChanged(percent)
                    },
                    switchEnabled = (bassBoostPercent > 0f),
                    onSwitchToggle = { enabled ->
                        if (enabled) {
                            val restore = if (lastActiveBassBoostPercent > 0f) lastActiveBassBoostPercent else 35f
                            onBassBoostChanged(restore)
                        } else {
                            onBassBoostChanged(0f)
                            if (activeEditingFader == "bass") activeEditingFader = null
                        }
                    },
                    isEditing = (activeEditingFader == "bass"),
                    onActivate = {
                        activeEditingFader = "bass"
                        if (bassBoostPercent <= 0f) {
                            val restore = if (lastActiveBassBoostPercent > 0f) lastActiveBassBoostPercent else 35f
                            onBassBoostChanged(restore)
                        }
                    },
                    onDeactivate = {
                        if (activeEditingFader == "bass") activeEditingFader = null
                    },
                    modifier = Modifier.weight(1f)
                )

                val preAmpProgress = ((preAmpDb + 12f) / 24f).coerceIn(0f, 1f)
                TactileCapsuleFader(
                    title = "PRE-AMP",
                    valueText = "${if (preAmpDb > 0) "+" else ""}${String.format(java.util.Locale.US, "%.1f", preAmpDb)} dB",
                    progress = preAmpProgress,
                    onProgressChanged = { newProgress ->
                        val rawDb = newProgress * 24f - 12f
                        val roundedDb = (rawDb * 2).roundToInt() / 2f
                        onPreAmpChanged(roundedDb)
                    },
                    switchEnabled = (preAmpDb != 0f),
                    onSwitchToggle = { enabled ->
                        if (enabled) {
                            val restore = if (lastActivePreAmpDb != 0f) lastActivePreAmpDb else 2.0f
                            onPreAmpChanged(restore)
                        } else {
                            onPreAmpChanged(0f)
                            if (activeEditingFader == "preamp") activeEditingFader = null
                        }
                    },
                    isEditing = (activeEditingFader == "preamp"),
                    onActivate = {
                        activeEditingFader = "preamp"
                        if (preAmpDb == 0f) {
                            val restore = if (lastActivePreAmpDb != 0f) lastActivePreAmpDb else 2.0f
                            onPreAmpChanged(restore)
                        }
                    },
                    onDeactivate = {
                        if (activeEditingFader == "preamp") activeEditingFader = null
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = colors.cardBg),
                border = BorderStroke(1.dp, if (dynamicBassEnabled) colors.accent.copy(alpha = 0.3f) else colors.cardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "SMART DYNAMIC BASS",
                                    fontFamily = BenzinFontFamily,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp,
                                    color = colors.textPrimary,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (dynamicBassEnabled) colors.accent.copy(alpha = 0.18f) else colors.badgeBg)
                                        .border(if (dynamicBassEnabled) 1.dp else 0.dp, if (dynamicBassEnabled) colors.accent.copy(alpha = 0.35f) else Color.Transparent, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (dynamicBassEnabled) androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.dynamic_bass_atmos_dsp) else androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.dynamic_bass_off),
                                        fontFamily = WixFontFamily,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (dynamicBassEnabled) colors.accent else colors.textSecondary,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.dynamic_bass_desc),
                                fontFamily = WixFontFamily,
                                fontSize = 11.sp,
                                color = colors.textSecondary
                            )
                        }
                        Switch(
                            checked = dynamicBassEnabled,
                            onCheckedChange = onDynamicBassToggle,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = colors.background,
                                checkedTrackColor = colors.accent,
                                uncheckedThumbColor = colors.textSecondary,
                                uncheckedTrackColor = colors.badgeBg
                            )
                        )
                    }

                    AnimatedVisibility(
                        visible = dynamicBassEnabled,
                        enter = expandVertically(animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)),
                        exit = shrinkVertically(animationSpec = tween(250)) + fadeOut(animationSpec = tween(200))
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(12.dp))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colors.faderTrack)
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(
                                                    when {
                                                        currentVolumeRatio >= 0.80f -> Color(0xFFFF9800)
                                                        currentVolumeRatio <= 0.45f -> Color(0xFF4CAF50)
                                                        else -> Color(0xFF2196F3)
                                                    },
                                                    CircleShape
                                                )
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = getLocalizedDynamicBassStatus(
                                                isEnabled = isEnabled,
                                                isBypass = isTemporaryBypass,
                                                isDynamicBassEnabled = dynamicBassEnabled,
                                                isSpeaker = currentAudioDevice.lowercase().let { it.contains("динамик") || it.contains("speaker") },
                                                volumeRatio = currentVolumeRatio,
                                                dynamicBassStrength = dynamicBassStrength,
                                                fallbackStatus = dynamicBassModeStatus
                                            ),
                                            fontFamily = WixFontFamily,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = colors.textPrimary
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.dynamic_bass_intensity),
                                    fontFamily = WixFontFamily,
                                    fontSize = 11.sp,
                                    color = colors.textSecondary
                                )
                                Text(
                                    text = "${(dynamicBassStrength * 100).roundToInt()}%",
                                    fontFamily = BenzinFontFamily,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary
                                )
                            }

                            Slider(
                                value = dynamicBassStrength,
                                onValueChange = onDynamicBassStrengthChanged,
                                valueRange = 0.2f..1.0f,
                                colors = SliderDefaults.colors(
                                    thumbColor = colors.accent,
                                    activeTrackColor = colors.accent,
                                    inactiveTrackColor = colors.faderTrack
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(32.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        if (showCreateDialog) {
            AlertDialog(
                onDismissRequest = { showCreateDialog = false },
                title = {
                    Text(
                        text = "SAVE PRESET",
                        fontFamily = BenzinFontFamily,
                        fontSize = 15.sp,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                },
                text = {
                    OutlinedTextField(
                        value = newPresetName,
                        onValueChange = { newPresetName = it },
                        label = { Text(androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.dialog_preset_name_label), fontFamily = WixFontFamily) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary,
                            focusedBorderColor = colors.accent,
                            unfocusedBorderColor = colors.cardBorder,
                            focusedLabelColor = colors.accent,
                            unfocusedLabelColor = colors.textSecondary
                        )
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (newPresetName.isNotBlank()) {
                                onSaveCustomPreset(newPresetName)
                                showCreateDialog = false
                            }
                        }
                    ) {
                        Text(
                            text = androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.dialog_btn_save),
                            fontFamily = WixFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateDialog = false }) {
                        Text(
                            text = androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.dialog_btn_cancel),
                            fontFamily = WixFontFamily,
                            color = colors.textSecondary
                        )
                    }
                },
                containerColor = colors.cardBg
            )
        }

        presetToDelete?.let { name ->
            AlertDialog(
                onDismissRequest = { presetToDelete = null },
                title = {
                    Text(
                        text = "DELETE PRESET",
                        fontFamily = BenzinFontFamily,
                        fontSize = 15.sp,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                },
                text = {
                    Text(
                        text = androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.dialog_delete_preset_confirm, name),
                        fontFamily = WixFontFamily,
                        color = colors.textSecondary
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onDeleteCustomPreset(name)
                            presetToDelete = null
                        }
                    ) {
                        Text(
                            text = androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.dialog_btn_delete),
                            fontFamily = WixFontFamily,
                            color = Color(0xFFFF5252),
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { presetToDelete = null }) {
                        Text(
                            text = androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.dialog_btn_cancel),
                            fontFamily = WixFontFamily,
                            color = colors.textSecondary
                        )
                    }
                },
                containerColor = colors.cardBg
            )
        }

        pendingPresetSwitch?.let { targetPreset ->
            AlertDialog(
                onDismissRequest = { pendingPresetSwitch = null },
                title = {
                    Text(
                        text = "UNSAVED PRESET",
                        fontFamily = BenzinFontFamily,
                        fontSize = 15.sp,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                },
                text = {
                    Text(
                        text = androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.dialog_unsaved_message, activePresetName, targetPreset),
                        fontFamily = WixFontFamily,
                        fontSize = 13.sp,
                        color = colors.textSecondary
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onSelectPreset(targetPreset)
                            pendingPresetSwitch = null
                        }
                    ) {
                        Text(
                            text = androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.dialog_btn_discard_switch),
                            fontFamily = WixFontFamily,
                            color = Color(0xFFFF5252),
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                dismissButton = {
                    Row {
                        TextButton(
                            onClick = {
                                pendingPresetSwitch = null
                                showCreateDialog = true
                            }
                        ) {
                            Text(
                                text = androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.dialog_btn_save_as_new),
                                fontFamily = WixFontFamily,
                                color = colors.accent,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        TextButton(onClick = { pendingPresetSwitch = null }) {
                            Text(
                                text = androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.dialog_btn_cancel),
                                fontFamily = WixFontFamily,
                                color = colors.textSecondary
                            )
                        }
                    }
                },
                containerColor = colors.cardBg
            )
        }

        pendingImportPresets?.let { importList ->
            if (importList.size == 1) {
                val preset = importList.first()
                AlertDialog(
                    onDismissRequest = onDismissImportDialog,
                    title = {
                        Text(
                            text = androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.dialog_import_preset_title),
                            fontFamily = BenzinFontFamily,
                            fontSize = 15.sp,
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    },
                    text = {
                        Column {
                            Text(
                                text = androidx.compose.ui.res.stringResource(
                                    com.omix.alleq.R.string.dialog_import_preset_desc,
                                    preset.name,
                                    if (preset.preAmpDb > 0) "+${preset.preAmpDb}" else "${preset.preAmpDb}",
                                    preset.bassBoostPercent.toInt()
                                ),
                                fontFamily = WixFontFamily,
                                fontSize = 13.sp,
                                color = colors.textSecondary
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Bands: " + preset.bandGains.joinToString(" / ") { "${it.toInt()}dB" },
                                fontFamily = WixFontFamily,
                                fontSize = 11.sp,
                                color = colors.textMuted
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                onImportSinglePreset(preset, true)
                                onDismissImportDialog()
                            }
                        ) {
                            Text(
                                text = androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.dialog_btn_import_and_apply),
                                fontFamily = WixFontFamily,
                                color = colors.accent,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    dismissButton = {
                        Row {
                            TextButton(
                                onClick = {
                                    onImportSinglePreset(preset, false)
                                    onDismissImportDialog()
                                }
                            ) {
                                Text(
                                    text = androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.dialog_btn_import_save_only),
                                    fontFamily = WixFontFamily,
                                    color = colors.textPrimary
                                )
                            }
                            TextButton(onClick = onDismissImportDialog) {
                                Text(
                                    text = androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.dialog_btn_cancel),
                                    fontFamily = WixFontFamily,
                                    color = colors.textSecondary
                                )
                            }
                        }
                    },
                    containerColor = colors.cardBg
                )
            } else if (importList.size > 1) {
                AlertDialog(
                    onDismissRequest = onDismissImportDialog,
                    title = {
                        Text(
                            text = androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.dialog_import_pack_title),
                            fontFamily = BenzinFontFamily,
                            fontSize = 15.sp,
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    },
                    text = {
                        val namesList = importList.take(6).joinToString(", ") { it.name } + if (importList.size > 6) "..." else ""
                        Text(
                            text = androidx.compose.ui.res.stringResource(
                                com.omix.alleq.R.string.dialog_import_pack_desc,
                                importList.size,
                                namesList
                            ),
                            fontFamily = WixFontFamily,
                            fontSize = 13.sp,
                            color = colors.textSecondary
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                onImportPresetsPack(importList)
                                onDismissImportDialog()
                            }
                        ) {
                            Text(
                                text = androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.dialog_btn_import_all),
                                fontFamily = WixFontFamily,
                                color = colors.accent,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = onDismissImportDialog) {
                            Text(
                                text = androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.dialog_btn_cancel),
                                fontFamily = WixFontFamily,
                                color = colors.textSecondary
                            )
                        }
                    },
                    containerColor = colors.cardBg
                )
            }
        }
    }
}

@Composable
fun getLocalizedAudioDevice(raw: String): String {
    val lower = raw.lowercase()
    return when {
        lower.contains("динамик") || lower.contains("speaker") -> androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.device_speaker)
        lower.contains("bluetooth") -> androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.device_bluetooth)
        lower.contains("проводные") || lower.contains("wired") || lower.contains("наушники") || lower.contains("headphone") -> androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.device_wired)
        lower.contains("usb") -> androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.device_usb)
        else -> raw.lowercase()
    }
}

@Composable
fun getLocalizedDynamicBassStatus(
    isEnabled: Boolean,
    isBypass: Boolean,
    isDynamicBassEnabled: Boolean,
    isSpeaker: Boolean,
    volumeRatio: Float,
    dynamicBassStrength: Float,
    fallbackStatus: String
): String {
    if (!isEnabled) return androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.dynamic_bass_status_master_off)
    if (isBypass) return androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.dynamic_bass_status_bypass)
    if (!isDynamicBassEnabled) return androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.dynamic_bass_status_disabled)
    if (!isSpeaker) return androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.dynamic_bass_status_headphones)

    val percentVol = (volumeRatio * 100).toInt()
    return when {
        volumeRatio <= 0.45f -> {
            val punch125 = 6.0f * dynamicBassStrength * 1.0f
            androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.dynamic_bass_status_punch, punch125, percentVol)
        }
        volumeRatio < 0.80f -> androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.dynamic_bass_status_adaptive, percentVol)
        else -> {
            val cutFactor = when {
                volumeRatio <= 0.70f -> 0.0f
                volumeRatio >= 1.0f -> 1.0f
                else -> {
                    val t = (volumeRatio - 0.70f) / (1.0f - 0.70f)
                    t * t * (3.0f - 2.0f * t)
                }
            }
            val cut31 = -6.0f * cutFactor
            androidx.compose.ui.res.stringResource(com.omix.alleq.R.string.dynamic_bass_status_protection, cut31, percentVol)
        }
    }
}