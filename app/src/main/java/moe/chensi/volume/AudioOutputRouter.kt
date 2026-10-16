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
    private var policyUids: Set<Int> = emptySet()
    private var usingRootBridge = false
    var lastError: String? = null
        private set

    @SuppressLint("PrivateApi")
    fun update(apps: Collection<App>, devices: List<AudioDeviceInfo>) {
        val byAddress = devices.distinctBy { it.type to it.address }
            .associateBy { it.type to it.address }
        val selectedByUid = apps.asSequence()
            .mapNotNull { app ->
                val type = app.outputDeviceType ?: return@mapNotNull null
                val address = app.outputDeviceAddress ?: ""
                val device = byAddress[type to address] ?: return@mapNotNull null
                app.applicationInfo.uid to device
            }
            .groupBy({ it.first }, { it.second })
            .mapNotNull { (uid, selectedDevices) ->
                val uniqueDevices = selectedDevices.distinctBy { it.type to it.address }
                if (uniqueDevices.size == 1) uid to uniqueDevices.single() else null
            }
            .toMap()
        val groups = selectedByUid.entries.groupBy(
            { it.value.type to it.value.address },
            { it.key to it.value }
        )

        if (RootAccess.active) {
            val error = RootAccess.updateOutputRoutes(
                selectedByUid.map { (uid, device) ->
                    RootAccess.OutputRoute(uid, device.id, device.type, device.address)
                }
            )
            usingRootBridge = true
            lastError = error
            if (error != null) Log.e(TAG, "Root bridge could not apply output routing: $error")
            return
        }

        if (groups.isEmpty()) {
            unregister()
            lastError = null
            return
        }

        // UID device affinities belong to a registered policy. Remove the prior policy and its
        // affinities before registering the replacement so the old policy cannot undo new routes.
        unregister()
        try {
            val mixes = groups.values.map { uidDevices ->
                val device = uidDevices.first().second
                buildMix(device, uidDevices.map { it.first }.distinct())
            }
            val builder = Reflect.onClass("android.media.audiopolicy.AudioPolicy\$Builder")
                .create(context)
            mixes.forEach { builder.call("addMix", it) }
            val newPolicy = builder.call("build").get<Any>()

            // AudioManager's public wrapper performs an app-UID permission check. The privileged
            // Root/Shizuku binder proxy performs this service call under its authorized identity.
            val result = Reflect.onClass("android.media.AudioManager")
                .call("registerAudioPolicyStatic", newPolicy).get<Int>()
            check(result == 0) { "Audio policy registration failed ($result)" }

            policy = newPolicy
            selectedByUid.forEach { (uid, device) ->
                val routed = Reflect.on(newPolicy)
                    .call("setUidDeviceAffinity", uid, listOf(device)).get<Boolean>()
                check(routed) { "Android rejected the output route for app UID $uid" }
                policyUids = policyUids + uid
            }
            lastError = null
        } catch (error: Exception) {
            Log.e(TAG, "Could not apply per-app output routing", error)
            unregister()
            lastError = error.cause?.message ?: error.message ?: "Audio routing is unavailable"
        }
    }

    @SuppressLint("PrivateApi")
    fun unregister() {
        if (usingRootBridge) {
            RootAccess.updateOutputRoutes(emptyList())?.let {
                Log.w(TAG, "Could not clear root audio routing: $it")
            }
            usingRootBridge = false
        }
        policy?.let { current ->
            policyUids.forEach { uid ->
                runCatching {
                    val removed = Reflect.on(current)
                        .call("removeUidDeviceAffinity", uid).get<Boolean>()
                    if (!removed) Log.w(TAG, "System did not clear output affinity for UID $uid")
                }
                    .onFailure { Log.w(TAG, "Could not clear output affinity for UID $uid", it) }
            }
            unregisterPolicy(current)
        }
        policy = null
        policyUids = emptySet()
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
