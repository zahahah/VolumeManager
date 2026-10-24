package moe.chensi.volume.lightningdsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LightningDspClientContractTest {
    @Test
    fun targetsRebrandedRootAndRootlessPackageIds() {
        assertEquals(
            listOf(
                "com.lightning.dsp",
                "com.lightning.dsp.debug",
                "me.timschneeberger.rootlessjamesdsp",
                "me.timschneeberger.rootlessjamesdsp.debug"
            ),
            LightningDspClient.APP_PACKAGES
        )
        assertFalse("james.dsp" in LightningDspClient.APP_PACKAGES)
    }

    @Test
    fun matchesTheLightningDspVolumeManagerProviderContract() {
        assertEquals("get_state", LightningDspClient.METHOD_GET_STATE)
        assertEquals("set_post_gain", LightningDspClient.METHOD_SET_POST_GAIN)
        assertEquals("set_effect", LightningDspClient.METHOD_SET_EFFECT)
        assertEquals("set_parameter", LightningDspClient.METHOD_SET_PARAMETER)
        assertEquals("set_powered_on", LightningDspClient.METHOD_SET_POWERED_ON)
        assertEquals(
            listOf(
                "compander", "bass", "equalizer", "graphic_eq", "reverb", "stereo_wide",
                "crossfeed", "tube", "ddc", "liveprog", "convolver"
            ),
            LightningDspClient.EFFECT_IDS
        )
    }
}
