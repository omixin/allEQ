package com.omix.vivoeq.model

data class EqualizerBand(
    val index: Int,
    val frequencyHz: Int,
    val label: String
)

data class EqualizerPreset(
    val name: String,
    val description: String,
    val bandGains: List<Float>, // 10 bands from -10 to +10 dB
    val preAmpDb: Float = 0f,
    val bassBoostPercent: Float = 0f,
    val limiterEnabled: Boolean = true
)

object EqualizerDefaults {
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
            description = "Линейный исходный звук без коррекции",
            bandGains = listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
            preAmpDb = 0f,
            bassBoostPercent = 0f,
            limiterEnabled = true
        ),
        EqualizerPreset(
            name = "Анти-Хрип (Динамик)",
            description = "Специально для динамиков: срез инфраниза и защита лимитером",
            bandGains = listOf(-8f, -4f, 3f, 1f, 0f, 2f, 3f, 2f, 1f, 0f),
            preAmpDb = 1f,
            bassBoostPercent = 15f,
            limiterEnabled = true
        ),
        EqualizerPreset(
            name = "Голос / YouTube",
            description = "Максимальная четкость речи и вокала",
            bandGains = listOf(-6f, -3f, 0f, 1f, 3f, 4f, 3f, 1f, -1f, -2f),
            preAmpDb = 0f,
            bassBoostPercent = 0f,
            limiterEnabled = true
        ),
        EqualizerPreset(
            name = "Бас-Панч",
            description = "Плотный читаемый мидбас без паразитного гула",
            bandGains = listOf(-3f, 2f, 5f, 3f, 0f, -1f, 1f, 2f, 0f, -1f),
            preAmpDb = 0f,
            bassBoostPercent = 40f,
            limiterEnabled = true
        ),
        EqualizerPreset(
            name = "Рок",
            description = "Классический V-образный драйвовый профиль",
            bandGains = listOf(4f, 3f, 2f, 0f, -2f, -1f, 2f, 4f, 5f, 4f),
            preAmpDb = 0f,
            bassBoostPercent = 25f,
            limiterEnabled = true
        ),
        EqualizerPreset(
            name = "Электроника / Клуб",
            description = "Сочный упругий бас и детальный верх",
            bandGains = listOf(2f, 4f, 4f, 1f, 0f, 1f, 2f, 3f, 4f, 4f),
            preAmpDb = 0f,
            bassBoostPercent = 35f,
            limiterEnabled = true
        ),
        EqualizerPreset(
            name = "Кино / Объем",
            description = "Эффект присутствия для видео и фильмов",
            bandGains = listOf(2f, 1f, 0f, 0f, 1f, 2f, 3f, 3f, 2f, 1f),
            preAmpDb = 2f,
            bassBoostPercent = 20f,
            limiterEnabled = true
        )
    )
}
