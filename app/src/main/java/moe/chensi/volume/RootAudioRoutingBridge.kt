package moe.chensi.volume

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.util.Log
import org.joor.Reflect
import java.lang.reflect.InvocationTargetException

/** Owns the audio policy in app_process, where privileged Binder calls retain matching attribution. */
internal class RootAudioRoutingBridge(private val context: Context) : Binder() {
    companion object {
        private const val TAG = "VolumeManager.RootRouting"
        private const val DESCRIPTOR = "moe.chensi.volume.IRootAudioRouting"
        private const val ROUTE_FLAG_RENDER = 1
        private const val RULE_MATCH_UID = 4
    }

    private var policy: Any? = null
    private val policyUids = mutableSetOf<Int>()
    private val audioManager by lazy { context.getSystemService(AudioManager::class.java) }

    init {
        attachInterface(null, DESCRIPTOR)
    }

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        if (code != IBinder.FIRST_CALL_TRANSACTION) return super.onTransact(code, data, reply, flags)
        data.enforceInterface(DESCRIPTOR)
        val count = data.readInt().coerceIn(0, 256)
        val routes = List(count) {
            RootAccess.OutputRoute(
                uid = data.readInt(),
                deviceType = data.readInt(),
                address = data.readString().orEmpty()
            )
        }
        val error = runCatching { apply(routes) }.exceptionOrNull()?.let(::describe)
        reply?.writeNoException()
        reply?.writeString(error)
        return true
    }

    @Suppress("PrivateApi")
    private fun apply(routes: List<RootAccess.OutputRoute>): String? {
        unregisterCurrent()
        if (routes.isEmpty()) return null

        return try {
            val available = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                .filter(AudioDeviceInfo::isSink)
                .associateBy { it.type to it.address }
            val resolved = routes.map { route ->
                val device = available[route.deviceType to route.address]
                    ?: error("Selected audio output is no longer connected")
                route.uid to device
            }
            val mixes = resolved.groupBy({ it.second.type to it.second.address }, { it })
                .values.map { uidDevices ->
                    val device = uidDevices.first().second
                    buildMix(device, uidDevices.map { it.first }.distinct())
                }
            val builder = Reflect.onClass("android.media.audiopolicy.AudioPolicy\$Builder")
                .create(context)
            mixes.forEach { builder.call("addMix", it) }
            val newPolicy = builder.call("build").get<Any>()

            val result = Reflect.onClass(AudioManager::class.java)
                .call("registerAudioPolicyStatic", newPolicy).get<Int>()
            check(result == 0) { "Audio policy registration failed ($result)" }

            policy = newPolicy
            resolved.forEach { (uid, device) ->
                val routed = Reflect.on(newPolicy)
                    .call("setUidDeviceAffinity", uid, listOf(device)).get<Boolean>()
                check(routed) { "Android rejected the output route for app UID $uid" }
                policyUids += uid
            }
            null
        } catch (error: Throwable) {
            Log.e(TAG, "Could not apply per-app output routing in root bridge", error)
            unregisterCurrent()
            describe(error)
        }
    }

    private fun buildMix(device: AudioDeviceInfo, uids: List<Int>): Any {
        val ruleBuilder = Reflect.onClass("android.media.audiopolicy.AudioMixingRule\$Builder").create()
        uids.forEach { uid -> ruleBuilder.call("addMixRule", RULE_MATCH_UID, uid) }
        val rule = ruleBuilder.call("build").get<Any>()

        val mixBuilder = Reflect.onClass("android.media.audiopolicy.AudioMix\$Builder").create()
        mixBuilder.call("setMixingRule", rule)
        mixBuilder.call("setRouteFlags", ROUTE_FLAG_RENDER)
        mixBuilder.call("setDevice", device)
        return mixBuilder.call("build").get<Any>()
    }

    private fun unregisterCurrent() {
        val current = policy ?: return
        policyUids.forEach { uid ->
            runCatching {
                Reflect.on(current).call("removeUidDeviceAffinity", uid)
            }.onFailure { Log.w(TAG, "Could not clear output affinity for UID $uid", it) }
        }
        runCatching {
            Reflect.onClass(AudioManager::class.java)
                .call("unregisterAudioPolicyAsyncStatic", current)
        }.onFailure { Log.w(TAG, "Could not unregister root audio policy", it) }
        policy = null
        policyUids.clear()
    }

    private fun describe(error: Throwable): String {
        var root = error
        while (true) {
            val cause = when (root) {
                is InvocationTargetException -> root.targetException ?: root.cause
                else -> root.cause
            } ?: break
            if (cause === root) break
            root = cause
        }
        return root.message ?: root.javaClass.simpleName
    }
}
