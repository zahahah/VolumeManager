package moe.chensi.volume

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.util.Log
import org.joor.Reflect

/** Replaces only the transient framework volume UI while the root bridge is alive. */
internal class RootVolumeController(private val context: Context) {
    companion object {
        private const val TAG = "VolumeManager.RootVolume"
        private const val DESCRIPTOR = "android.media.IVolumeController"
        private const val TRANSACTION_VOLUME_CHANGED = IBinder.FIRST_CALL_TRANSACTION + 1
    }

    private val audioManager = context.getSystemService(AudioManager::class.java)
    private var previousController: Any? = null
    private var registered = false

    private val controllerBinder = object : Binder() {
        init { attachInterface(null, DESCRIPTOR) }

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == IBinder.INTERFACE_TRANSACTION) {
                reply?.writeString(DESCRIPTOR)
                return true
            }
            if (code != TRANSACTION_VOLUME_CHANGED) return super.onTransact(code, data, reply, flags)

            data.enforceInterface(DESCRIPTOR)
            data.readInt() // streamType: the panel displays all app and system volumes.
            val volumeFlags = data.readInt()
            if (volumeFlags and AudioManager.FLAG_SHOW_UI != 0) showPanel()
            return true
        }
    }

    fun register() {
        previousController = Reflect.on(audioManager).call("getVolumeController").get<Any?>()
        val stub = Reflect.onClass("android.media.IVolumeController\$Stub")
            .call("asInterface", controllerBinder).get<Any>()
        Reflect.on(audioManager).call("setVolumeController", stub)
        registered = true
        Log.i(TAG, "Root volume controller registered")
    }

    fun restore() {
        if (!registered) return
        runCatching {
            Reflect.on(audioManager).call("setVolumeController", previousController)
        }.onFailure { Log.w(TAG, "Could not restore the previous volume controller", it) }
        registered = false
        previousController = null
    }

    private fun showPanel() {
        val intent = Intent().setComponent(
            ComponentName("moe.chensi.volume", "moe.chensi.volume.PopupActivity")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        runCatching { context.startActivity(intent) }
            .onFailure { Log.e(TAG, "Could not open the root volume panel", it) }
    }
}
