package com.omix.alleq

import com.omix.alleq.model.EqualizerDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EqualizerDefaultsTest {

    @Test
    fun bandsCountIsTen() {
        assertEquals(10, EqualizerDefaults.BANDS.size)
    }

    @Test
    fun bandsAreOrderedByFrequency() {
        val freqs = EqualizerDefaults.BANDS.map { it.frequencyHz }
        assertEquals(freqs, freqs.sorted())
    }

    @Test
    fun presetsHaveTenBands() {
        EqualizerDefaults.PRESETS.forEach { preset ->
            assertEquals("Preset ${preset.name} must have 10 bands", 10, preset.bandGains.size)
        }
    }

    @Test
    fun presetsGainsWithinRange() {
        EqualizerDefaults.PRESETS.forEach { preset ->
            preset.bandGains.forEach { gain ->
                assertTrue("Gain $gain in ${preset.name} out of range", gain in -15f..15f)
            }
        }
    }

    @Test
    fun presetNamesAreUnique() {
        val names = EqualizerDefaults.PRESETS.map { it.name }
        assertEquals(names.size, names.distinct().size)
    }
}
