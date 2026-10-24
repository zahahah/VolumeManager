package moe.chensi.volume.lightningdsp

import android.content.Context
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log

data class LightningDspState(
    val packageName: String,
    val postGainDb: Float,
    val effects: Map<String, Boolean>,
    val active: Boolean,
    val parameters: Map<String, Float>
)

/** Talks only to LightningDSP's narrow, caller-checked VolumeManager provider. */
class LightningDspClient(context: Context) {
    companion object {
        const val METHOD_GET_STATE = "get_state"
        const val METHOD_SET_POST_GAIN = "set_post_gain"
        const val METHOD_SET_EFFECT = "set_effect"
        const val METHOD_SET_PARAMETER = "set_parameter"
        const val METHOD_SET_POWERED_ON = "set_powered_on"
        const val EXTRA_POST_GAIN = "post_gain"
        const val EXTRA_EFFECT_ID = "effect_id"
        const val EXTRA_ENABLED = "enabled"
        const val EXTRA_PARAMETER_ID = "parameter_id"
        const val EXTRA_VALUE = "value"
        val PARAMETER_RANGES = mapOf(
            "bass" to (3f..15f),
            "stereo_wide" to (30f..75f),
            "tube" to (-3f..12f)
        )
        val EFFECT_IDS = listOf(
            "compander", "bass", "equalizer", "graphic_eq", "reverb", "stereo_wide",
            "crossfeed", "tube", "ddc", "liveprog", "convolver"
        )

        val APP_PACKAGES = listOf(
            "com.lightning.dsp", "com.lightning.dsp.debug",
            "me.timschneeberger.rootlessjamesdsp", "me.timschneeberger.rootlessjamesdsp.debug"
        )

        @Volatile
        private var lastKnownState: LightningDspState? = null
    }

    private val appContext = context.applicationContext
    var lastError: String? = null
        private set

    /** Returns the last successful provider response so short-lived panels can render immediately. */
    fun getCachedState(): LightningDspState? = lastKnownState

    fun cacheState(state: LightningDspState?) {
        if (state != null) lastKnownState = state
    }

    fun getState(): LightningDspState? {
        lastError = null
        for (packageName in APP_PACKAGES) {
            val result = try {
                call(packageName, METHOD_GET_STATE, null)
            } catch (error: Exception) {
                Log.w("LightningDspClient", "Integration call to $packageName failed", error)
                if (lastError == null) {
                    lastError = "$packageName: ${error.javaClass.simpleName}: ${error.message.orEmpty()}"
                }
                continue
            }
            val effects = EFFECT_IDS.associateWith { result.getBoolean("effect_$it", false) }
            return LightningDspState(
                packageName = packageName,
                postGainDb = result.getFloat(EXTRA_POST_GAIN, 0f).coerceIn(-15f, 15f),
                effects = effects,
                active = result.getBoolean("active", true),
                parameters = PARAMETER_RANGES.mapValues { (id, range) ->
                    result.getFloat("parameter_$id", range.start).coerceIn(range.start, range.endInclusive)
                }
            ).also(::cacheState)
        }
        lastKnownState = null
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

    fun setParameter(packageName: String, parameterId: String, value: Float): Boolean {
        val range = PARAMETER_RANGES[parameterId] ?: return false
        if (!value.isFinite() || value !in range) return false
        return runCatching {
            call(packageName, METHOD_SET_PARAMETER, Bundle().apply {
                putString(EXTRA_PARAMETER_ID, parameterId)
                putFloat(EXTRA_VALUE, value)
            }).getBoolean("success")
        }.getOrDefault(false)
    }

    fun setPoweredOn(packageName: String, enabled: Boolean): Boolean = runCatching {
        call(packageName, METHOD_SET_POWERED_ON, Bundle().apply {
            putBoolean(EXTRA_ENABLED, enabled)
        }).getBoolean("success")
    }.getOrDefault(false)

    fun openEffect(packageName: String, effectId: String): Boolean {
        if (effectId !in EFFECT_IDS) return false
        return runCatching {
            appContext.startActivity(Intent().apply {
                component = ComponentName(packageName,
                    "lightning.dsp.activity.MainActivity")
                action = "$packageName.action.OPEN_EFFECT"
                putExtra("effect_id", effectId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            true
        }.getOrDefault(false)
    }

    fun getAppIcon(packageName: String) = appContext.packageManager.getApplicationIcon(packageName)

    private fun call(packageName: String, method: String, extras: Bundle?): Bundle {
        val uri = Uri.parse("content://$packageName.volume_manager")
        return appContext.contentResolver.call(uri, method, null, extras)
            ?: throw IllegalStateException("LightningDSP provider did not respond")
    }
}
