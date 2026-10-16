package moe.chensi.volume

import android.content.Context
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import android.os.Process
import org.joor.Reflect
import org.lsposed.hiddenapibypass.HiddenApiBypass

/** Entry point of the short-lived root app_process. Binder calls execute under its UID. */
object RootBridge {
    @JvmStatic
    fun main(args: Array<String>) {
        HiddenApiBypass.addHiddenApiExemptions("")
        Looper.prepareMainLooper()
        val activityThread = Reflect.onClass("android.app.ActivityThread").call("systemMain")
        val context = activityThread.call("getSystemContext").get<Context>()
        val volumeController = RootVolumeController(context)
        val services = Bundle()
        for (name in RootAccess.serviceNames) {
            val base = Reflect.onClass("android.os.ServiceManager")
                .call("getService", name).get<IBinder?>() ?: error("Missing service: $name")
            services.putBinder(name, Relay(base))
        }
        services.putBinder("routing", RootAudioRoutingBridge(context))
        services.putBinder("control", object : Binder() {
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                if (code == 1) {
                    volumeController.restore()
                    Process.killProcess(Process.myPid())
                    return true
                }
                return super.onTransact(code, data, reply, flags)
            }
        })
        // ContentResolver's normal path requires a registered IApplicationThread. An
        // app_process started through su is external to ActivityManager instead.
        val manager = Reflect.onClass("android.app.ActivityManager").call("getService").get<Any>()
        val token = Binder()
        val holder = Reflect.on(manager).call(
            "getContentProviderExternal", RootAccess.AUTHORITY, 0, token, "VolumeManager"
        ).get<Any?>() ?: error("Root bridge provider unavailable")
        val provider = Reflect.on(holder).get<Any>("provider")
        val result = try {
            Reflect.on(provider).call(
                "call", context.attributionSource, RootAccess.AUTHORITY,
                RootAccess.INSTALL, null, services
            ).get<Bundle?>()
        } finally {
            Reflect.on(manager).call(
                "removeContentProviderExternalAsUser", RootAccess.AUTHORITY, token, 0
            )
        }
        check(result?.getBoolean("connected") == true) { "Bridge handoff failed" }
        val owner = result.getBinder("owner") ?: error("Missing app owner Binder")
        owner.linkToDeath({
            volumeController.restore()
            Process.killProcess(Process.myPid())
        }, 0)
        volumeController.onShowPanel = {
            Reflect.on(provider).call(
                "call", context.attributionSource, RootAccess.AUTHORITY,
                RootAccess.SHOW_PANEL, null, null
            )
        }
        runCatching { volumeController.register() }
            .onFailure { android.util.Log.e("VolumeManager.Root", "Could not register root volume controller", it) }
        Looper.loop()
    }

    private class Relay(private val base: IBinder) : Binder() {
        init { attachInterface(null, base.interfaceDescriptor) }

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            return base.transact(code, data, reply, flags)
        }
    }
}
