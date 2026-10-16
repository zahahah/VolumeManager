package moe.chensi.volume.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppPreferencesTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun outputDeviceSelectionSurvivesSerialization() {
        val preferences = AppPreferences(outputDeviceType = 8, outputDeviceAddress = "usb:1")

        val restored = json.decodeFromString<AppPreferences>(json.encodeToString(preferences))

        assertEquals(8, restored.outputDeviceType)
        assertEquals("usb:1", restored.outputDeviceAddress)
    }

    @Test
    fun oldPreferencesDefaultToSystemOutput() {
        val restored = json.decodeFromString<AppPreferences>(
            """{"isPlayer":true,"volume":0.5,"hidden":false,"disableVolumeButtons":false}"""
        )

        assertNull(restored.outputDeviceType)
        assertNull(restored.outputDeviceAddress)
    }
}
