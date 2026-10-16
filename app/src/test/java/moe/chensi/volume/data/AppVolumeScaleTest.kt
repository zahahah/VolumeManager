package moe.chensi.volume.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AppVolumeScaleTest {
    @Test
    fun masterVolumeIsNormalizedToZeroToOne() {
        assertEquals(0f, normalizeMasterVolume(0, 25), 0f)
        assertEquals(0.8f, normalizeMasterVolume(20, 25), 0.0001f)
        assertEquals(1f, normalizeMasterVolume(30, 25), 0f)
    }

    @Test
    fun appSliderDisplayUsesTheMediaVolumeCeiling() {
        assertEquals(80, cappedAppVolumePercent(1f, 0.8f))
        assertEquals(40, cappedAppVolumePercent(0.5f, 0.8f))
        assertEquals(0, cappedAppVolumePercent(1f, 0f))
    }

    @Test
    fun gainsAreClampedToValidRange() {
        assertEquals(100, cappedAppVolumePercent(2f, 2f))
        assertEquals(1f, normalizeMasterVolume(100, 0), 0f)
    }
}
