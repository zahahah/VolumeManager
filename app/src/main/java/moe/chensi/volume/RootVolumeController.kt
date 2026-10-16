package moe.chensi.volume

import android.content.Context
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
        // An app_process started via su has no IApplicationThread, so ActivityTaskManager
        // rejects Context.startActivity() with BAL_BLOCK. Hand off the start to Android's
        // shell uid, which is permitted to launch this user-visible panel without requiring
        // an AccessibilityService or changing persistent system settings.
        runCatching {
            val process = ProcessBuilder(
                "/system/bin/su", "2000", "-c",
                "am start --user 0 -f 0x30000000 -n moe.chensi.volume/.PopupActivity"
            ).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
            check(process.waitFor() == 0) { "Activity launch failed: $output" }
            Log.d(TAG, "Root volume panel launch requested: $output")
        }.onFailure { Log.e(TAG, "Could not open the root volume panel", it) }
    }
}
