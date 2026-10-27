package com.omix.alleq.model

data class EqualizerBand(
    val index: Int,
    val frequencyHz: Int,
    val label: String
)

data class EqualizerPreset(
    val name: String,
    val description: String,
    val bandGains: List<Float>, // 10 bands from -15 to +15 dB
    val preAmpDb: Float = 0f,
    val bassBoostPercent: Float = 0f,
    val limiterEnabled: Boolean = false,
    val isCustom: Boolean = false
)

enum class AudioDeviceType {
    SPEAKER,
    BLUETOOTH,
    WIRED,
    USB,
    CAR,
}

data class DevicePresetBinding(
    val deviceKey: String,
    val deviceName: String,
    val deviceType: AudioDeviceType,
    val assignedPresetName: String,
    val isCurrentlyActive: Boolean = false
)

object EqualizerDefaults {
    private const val MAX_PRESET_NAME_LENGTH = 40
    private const val MAX_PRESET_DESCRIPTION_LENGTH = 200

    val BANDS = listOf(
        EqualizerBand(0, 31, "31 Hz"),
        EqualizerBand(1, 62, "62 Hz"),
        EqualizerBand(2, 125, "125 Hz"),
        EqualizerBand(3, 250, "250 Hz"),
        EqualizerBand(4, 500, "500 Hz"),
        EqualizerBand(5, 1000, "1 kHz"),
        EqualizerBand(6, 2000, "2 kHz"),
        EqualizerBand(7, 4000, "4 kHz"),
        EqualizerBand(8, 8000, "8 kHz"),
        EqualizerBand(9, 16000, "16 kHz")
    )

    val PRESETS = listOf(
        EqualizerPreset(
            name = "Flat",
            description = "Reference flat response without coloration",
            bandGains = listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
            preAmpDb = 0f,
            bassBoostPercent = 0f,
            limiterEnabled = false
        ),
        EqualizerPreset(
            name = "Bass Punch",
            description = "Tight focused punch without boominess",
            bandGains = listOf(-2f, 3f, 5f, 3f, 0f, -1f, 1f, 2f, 1f, 0f),
            preAmpDb = 0f,
            bassBoostPercent = 35f,
            limiterEnabled = false
        ),
        EqualizerPreset(
            name = "Vocal Clarity",
            description = "Crystal clear voice and dialogue intelligibility",
            bandGains = listOf(-5f, -2f, 0f, 1f, 3f, 4f, 3f, 2f, 0f, -1f),
            preAmpDb = 0f,
            bassBoostPercent = 0f,
            limiterEnabled = false
        ),
        EqualizerPreset(
            name = "Club",
            description = "Energetic sub-bass and sparkling highs",
            bandGains = listOf(2f, 4f, 4f, 1f, 0f, 1f, 2f, 3f, 4f, 4f),
            preAmpDb = 0f,
            bassBoostPercent = 35f,
            limiterEnabled = false
        ),
        EqualizerPreset(
            name = "Rock",
            description = "Classic V-curve drive for guitars and drums",
            bandGains = listOf(4f, 3f, 2f, 0f, -2f, -1f, 2f, 4f, 5f, 4f),
            preAmpDb = 0f,
            bassBoostPercent = 25f,
            limiterEnabled = false
        ),
        EqualizerPreset(
            name = "Cinematic",
            description = "Wide immersive soundstage for movies and videos",
            bandGains = listOf(2f, 2f, 1f, 0f, 1f, 2f, 3f, 3f, 2f, 1f),
            preAmpDb = 1f,
            bassBoostPercent = 20f,
            limiterEnabled = false
        ),
        EqualizerPreset(
            name = "Acoustic",
            description = "Warm natural timbre of instruments",
            bandGains = listOf(1f, 2f, 2f, 1f, 1f, 2f, 1f, 1f, 2f, 2f),
            preAmpDb = 0f,
            bassBoostPercent = 10f,
            limiterEnabled = false
        )
    )

    fun customPresetsToJson(presets: List<EqualizerPreset>): String {
        val array = org.json.JSONArray()
        presets.filter { it.isCustom }.forEach { preset ->
            val obj = org.json.JSONObject().apply {
                put("name", preset.name)
                put("description", preset.description)
                put("preAmpDb", preset.preAmpDb.toDouble())
                put("bassBoostPercent", preset.bassBoostPercent.toDouble())
                put("limiterEnabled", preset.limiterEnabled)
                val gainsArray = org.json.JSONArray()
                preset.bandGains.forEach { gainsArray.put(it.toDouble()) }
                put("bandGains", gainsArray)
            }
            array.put(obj)
        }
        return array.toString()
    }

    fun customPresetsFromJson(json: String?): List<EqualizerPreset> {
        if (json.isNullOrBlank()) return emptyList()
        val list = mutableListOf<EqualizerPreset>()
        try {
            val array = org.json.JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val gainsArray = obj.getJSONArray("bandGains")
                val gains = mutableListOf<Float>()
                for (g in 0 until gainsArray.length()) {
                    gains.add(gainsArray.getDouble(g).toFloat())
                }
                list.add(
                    EqualizerPreset(
                        name = obj.getString("name"),
                        description = obj.optString("description", "Custom preset"),
                        bandGains = gains,
                        preAmpDb = obj.optDouble("preAmpDb", 0.0).toFloat(),
                        bassBoostPercent = obj.optDouble("bassBoostPercent", 0.0).toFloat(),
                        limiterEnabled = obj.optBoolean("limiterEnabled", false),
                        isCustom = true
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    fun allPresetsToJson(presets: List<EqualizerPreset>): String {
        val array = org.json.JSONArray()
        presets.forEach { preset ->
            val obj = org.json.JSONObject().apply {
                put("name", preset.name)
                put("description", preset.description)
                put("preAmpDb", preset.preAmpDb.toDouble())
                put("bassBoostPercent", preset.bassBoostPercent.toDouble())
                put("limiterEnabled", preset.limiterEnabled)
                put("isCustom", preset.isCustom)
                val gainsArray = org.json.JSONArray()
                preset.bandGains.forEach { gainsArray.put(it.toDouble()) }
                put("bandGains", gainsArray)
            }
            array.put(obj)
        }
        return array.toString()
    }

    fun allPresetsFromJson(json: String?): List<EqualizerPreset> {
        if (json.isNullOrBlank()) return emptyList()
        val list = mutableListOf<EqualizerPreset>()
        try {
            val array = org.json.JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val gainsArray = obj.getJSONArray("bandGains")
                val gains = mutableListOf<Float>()
                for (g in 0 until gainsArray.length()) {
                    gains.add(gainsArray.getDouble(g).toFloat())
                }
                list.add(
                    EqualizerPreset(
                        name = obj.getString("name"),
                        description = obj.optString("description", "Preset"),
                        bandGains = gains,
                        preAmpDb = obj.optDouble("preAmpDb", 0.0).toFloat(),
                        bassBoostPercent = obj.optDouble("bassBoostPercent", 0.0).toFloat(),
                        limiterEnabled = obj.optBoolean("limiterEnabled", false),
                        isCustom = obj.optBoolean("isCustom", false)
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    // serialize single preset to .alleq json format
    fun presetToAlleqJson(preset: EqualizerPreset): String {
        val root = org.json.JSONObject().apply {
            put("format", "allEQ")
            put("version", 1)
            put("type", "single_preset")
            val presetObj = presetToJsonObject(preset)
            val array = org.json.JSONArray().apply { put(presetObj) }
            put("presets", array)
        }
        return root.toString(2)
    }

    // serialize list of presets to .alleq pack format
    fun presetPackToAlleqJson(presets: List<EqualizerPreset>): String {
        val root = org.json.JSONObject().apply {
            put("format", "allEQ")
            put("version", 1)
            put("type", "preset_pack")
            val array = org.json.JSONArray()
            presets.forEach { array.put(presetToJsonObject(it)) }
            put("presets", array)
        }
        return root.toString(2)
    }

    private fun presetToJsonObject(preset: EqualizerPreset): org.json.JSONObject {
        return org.json.JSONObject().apply {
            put("name", preset.name)
            put("description", preset.description)
            put("preAmpDb", preset.preAmpDb.toDouble())
            put("bassBoostPercent", preset.bassBoostPercent.toDouble())
            put("limiterEnabled", preset.limiterEnabled)
            put("isCustom", true)
            val gainsArray = org.json.JSONArray()
            preset.bandGains.forEach { gainsArray.put(it.toDouble()) }
            put("bandGains", gainsArray)
        }
    }

    // parse any .alleq payload or raw json into list of presets
    fun parseAlleqPayload(payload: String?): List<EqualizerPreset> {
        if (payload.isNullOrBlank()) return emptyList()
        val trimmed = payload.trim()
        val result = mutableListOf<EqualizerPreset>()

        try {
            if (trimmed.startsWith("{")) {
                val obj = org.json.JSONObject(trimmed)
                if (obj.has("presets")) {
                    val array = obj.getJSONArray("presets")
                    for (i in 0 until array.length()) {
                        parsePresetObject(array.getJSONObject(i))?.let { result.add(it) }
                    }
                } else if (obj.has("bandGains")) {
                    parsePresetObject(obj)?.let { result.add(it) }
                }
            } else if (trimmed.startsWith("[")) {
                val array = org.json.JSONArray(trimmed)
                for (i in 0 until array.length()) {
                    parsePresetObject(array.getJSONObject(i))?.let { result.add(it) }
                }
            }
        } catch (_: Exception) {}

        return result
    }

    private fun parsePresetObject(obj: org.json.JSONObject): EqualizerPreset? {
        return try {
            // imported files are untrusted: nan / infinity reach the dsp as silence, built-in names get shadowed
            val rawName = obj.optString("name", "").trim().take(MAX_PRESET_NAME_LENGTH)
            val baseName = rawName.ifBlank { "Imported Preset" }
            val name = if (PRESETS.any { it.name.equals(baseName, ignoreCase = true) }) "$baseName (imported)" else baseName
            val desc = obj.optString("description", "Custom preset").take(MAX_PRESET_DESCRIPTION_LENGTH)
            val gainsArray = obj.getJSONArray("bandGains")
            val gains = mutableListOf<Float>()
            for (g in 0 until gainsArray.length()) {
                gains.add(gainsArray.getDouble(g).toFloat())
            }
            if (gains.size != 10) return null
            val preAmp = obj.optDouble("preAmpDb", 0.0).toFloat()
            val bassBoost = obj.optDouble("bassBoostPercent", 0.0).toFloat()
            if (gains.any { !it.isFinite() } || !preAmp.isFinite() || !bassBoost.isFinite()) return null
            EqualizerPreset(
                name = name,
                description = desc,
                bandGains = gains.map { it.coerceIn(-15f, 15f) },
                preAmpDb = preAmp.coerceIn(-12f, 12f),
                bassBoostPercent = bassBoost.coerceIn(0f, 100f),
                limiterEnabled = obj.optBoolean("limiterEnabled", false),
                isCustom = true
            )
        } catch (_: Exception) {
            null
        }
    }

    val DEFAULT_DEVICE_BINDINGS = listOf(
        DevicePresetBinding(
            deviceKey = "speaker",
            deviceName = "Built-in Speaker",
            deviceType = AudioDeviceType.SPEAKER,
            assignedPresetName = "Flat",
            isCurrentlyActive = true
        )
    )

    fun deviceBindingsToJson(bindings: List<DevicePresetBinding>): String {
        val array = org.json.JSONArray()
        bindings.forEach { b ->
            val obj = org.json.JSONObject().apply {
                put("deviceKey", b.deviceKey)
                put("deviceName", b.deviceName)
                put("deviceType", b.deviceType.name)
                put("assignedPresetName", b.assignedPresetName)
            }
            array.put(obj)
        }
        return array.toString()
    }

    fun deviceBindingsFromJson(json: String?): List<DevicePresetBinding> {
        if (json.isNullOrBlank()) return emptyList()
        val list = mutableListOf<DevicePresetBinding>()
        try {
            val array = org.json.JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val type = try {
                    AudioDeviceType.valueOf(obj.optString("deviceType", AudioDeviceType.SPEAKER.name))
                } catch (_: Exception) {
                    AudioDeviceType.SPEAKER
                }
                list.add(
                    DevicePresetBinding(
                        deviceKey = obj.getString("deviceKey"),
                        deviceName = obj.optString("deviceName", "Device"),
                        deviceType = type,
                        assignedPresetName = obj.optString("assignedPresetName", "Flat")
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }
}
