package moe.chensi.volume.jamesdsp

import android.content.Context
import android.net.Uri
import android.os.Bundle

data class JamesDspState(
    val packageName: String,
    val postGainDb: Float,
    val effects: Map<String, Boolean>
)

/** Talks only to the narrow, caller-checked provider shipped by RootJamesDSP. */
class JamesDspClient(context: Context) {
    companion object {
        const val METHOD_GET_STATE = "get_state"
        const val METHOD_SET_POST_GAIN = "set_post_gain"
        const val METHOD_SET_EFFECT = "set_effect"
        const val EXTRA_POST_GAIN = "post_gain"
        const val EXTRA_EFFECT_ID = "effect_id"
        const val EXTRA_ENABLED = "enabled"

        val EFFECT_IDS = listOf(
            "compander", "bass", "equalizer", "graphic_eq", "reverb", "stereo_wide",
            "crossfeed", "tube", "ddc", "liveprog", "convolver"
        )

        val APP_PACKAGES = listOf(
            "james.dsp", "james.dsp.debug",
            "me.timschneeberger.rootlessjamesdsp", "me.timschneeberger.rootlessjamesdsp.debug"
        )
    }

    private val appContext = context.applicationContext

    fun getState(): JamesDspState? {
        for (packageName in APP_PACKAGES) {
            if (!isInstalled(packageName)) continue
            val result = runCatching { call(packageName, METHOD_GET_STATE, null) }.getOrNull() ?: continue
            val effects = EFFECT_IDS.associateWith { result.getBoolean("effect_$it", false) }
            return JamesDspState(
                packageName = packageName,
                postGainDb = result.getFloat(EXTRA_POST_GAIN, 0f).coerceIn(-15f, 15f),
                effects = effects
            )
        }
        return null
    }

    fun setPostGain(packageName: String, value: Float): Boolean {
        if (!value.isFinite() || value !in -15f..15f) return false
        return runCatching {
            call(packageName, METHOD_SET_POST_GAIN, Bundle().apply {
                putFloat(EXTRA_POST_GAIN, value)
            }).getBoolean("success")
        }.getOrDefault(false)
    }

    fun setEffect(packageName: String, effectId: String, enabled: Boolean): Boolean {
        if (effectId !in EFFECT_IDS) return false
        return runCatching {
            call(packageName, METHOD_SET_EFFECT, Bundle().apply {
                putString(EXTRA_EFFECT_ID, effectId)
                putBoolean(EXTRA_ENABLED, enabled)
            }).getBoolean("success")
        }.getOrDefault(false)
    }

    fun getAppIcon(packageName: String) = appContext.packageManager.getApplicationIcon(packageName)

    private fun isInstalled(packageName: String): Boolean = runCatching {
        appContext.packageManager.getPackageInfo(packageName, 0)
    }.isSuccess

    private fun call(packageName: String, method: String, extras: Bundle?): Bundle {
        val uri = Uri.parse("content://$packageName.volume_manager")
        return appContext.contentResolver.call(uri, method, null, extras)
            ?: throw IllegalStateException("JamesDSP did not respond")
    }
}
