package moe.chensi.volume

import android.content.Context
import android.net.Uri
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
        val services = Bundle()
        for (name in RootAccess.serviceNames) {
            val base = Reflect.onClass("android.os.ServiceManager")
                .call("getService", name).get<IBinder?>() ?: error("Missing service: $name")
            services.putBinder(name, Relay(base))
        }
        services.putBinder("control", object : Binder() {
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                if (code == 1) {
                    Process.killProcess(Process.myPid())
                    return true
                }
                return super.onTransact(code, data, reply, flags)
            }
        })
        val uri = Uri.parse("content://${RootAccess.AUTHORITY}")
        val result = context.contentResolver.call(uri, RootAccess.INSTALL, null, services)
        check(result?.getBoolean("connected") == true) { "Bridge handoff failed" }
        val owner = result.getBinder("owner") ?: error("Missing app owner Binder")
        owner.linkToDeath({ Process.killProcess(Process.myPid()) }, 0)
        Looper.loop()
    }

    private class Relay(private val base: IBinder) : Binder() {
        init { attachInterface(null, base.interfaceDescriptor) }

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            return base.transact(code, data, reply, flags)
        }
    }
}
