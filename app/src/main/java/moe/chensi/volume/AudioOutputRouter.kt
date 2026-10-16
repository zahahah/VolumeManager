package moe.chensi.volume

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.util.Log
import moe.chensi.volume.data.App
import org.joor.Reflect

/** Routes app UIDs through system audio policy mixes without capturing or muting their audio. */
internal class AudioOutputRouter(
    private val context: Context
) {
    companion object {
        private const val TAG = "VolumeManager.Routing"
        private const val ROUTE_FLAG_RENDER = 1
        private const val RULE_MATCH_UID = 4
    }

    private var policy: Any? = null

    @SuppressLint("PrivateApi")
    fun update(apps: Collection<App>, devices: List<AudioDeviceInfo>) {
        val byAddress = devices.associateBy { it.type to it.address }
        val groups = apps.asSequence()
            .filter { it.outputDeviceType != null }
            .mapNotNull { app ->
                val type = app.outputDeviceType ?: return@mapNotNull null
                val address = app.outputDeviceAddress ?: ""
                val device = byAddress[type to address] ?: return@mapNotNull null
                device to app.applicationInfo.uid
            }
            .groupBy({ it.first }, { it.second })

        if (groups.isEmpty()) {
            unregister()
            return
        }

        try {
            val mixes = groups.map { (device, uids) -> buildMix(device, uids.distinct()) }
            val builder = Reflect.onClass("android.media.audiopolicy.AudioPolicy\$Builder")
                .create(context)
            mixes.forEach { builder.call("addMix", it) }
            val newPolicy = builder.call("build").get<Any>()

            // AudioManager's public wrapper performs an app-UID permission check. The privileged
            // Root/Shizuku binder proxy performs this service call under its authorized identity.
            val result = Reflect.onClass("android.media.AudioManager")
                .call("registerAudioPolicyStatic", newPolicy).get<Int>()
            check(result == 0) { "Audio policy registration failed ($result)" }

            val previous = policy
            policy = newPolicy
            previous?.let(::unregisterPolicy)
        } catch (error: Exception) {
            Log.e(TAG, "Could not apply per-app output routing", error)
            unregister()
        }
    }

    @SuppressLint("PrivateApi")
    fun unregister() {
        policy?.let(::unregisterPolicy)
        policy = null
    }

    private fun buildMix(device: AudioDeviceInfo, uids: List<Int>): Any {
        val ruleBuilder = Reflect.onClass("android.media.audiopolicy.AudioMixingRule\$Builder")
            .create()
        uids.forEach { uid -> ruleBuilder.call("addMixRule", RULE_MATCH_UID, uid) }
        val rule = ruleBuilder.call("build").get<Any>()

        val mixBuilder = Reflect.onClass("android.media.audiopolicy.AudioMix\$Builder").create()
        mixBuilder.call("setMixingRule", rule)
        mixBuilder.call("setRouteFlags", ROUTE_FLAG_RENDER)
        mixBuilder.call("setDevice", device)
        return mixBuilder.call("build").get<Any>()
    }

    private fun unregisterPolicy(value: Any) {
        runCatching {
            Reflect.onClass("android.media.AudioManager")
                .call("unregisterAudioPolicyAsyncStatic", value)
        }.onFailure { Log.w(TAG, "Could not unregister audio policy", it) }
    }
}
