package moe.chensi.volume

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/** Reconnects the opted-in, systemless root bridge after the user unlocks the device. */
class RootStartupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val settings = context.getSharedPreferences("access", Context.MODE_PRIVATE)
        if (settings.getString("mode", "shizuku") != "root" ||
            !settings.getBoolean("start_at_boot", false)
        ) return

        Log.i(TAG, "Starting opted-in root mode after boot")
        // Manager reconnects the transient app_process bridge; it does not mount or
        // write anything to system/vendor/product partitions.
        (context.applicationContext as MyApplication).manager
    }

    private companion object {
        const val TAG = "VolumeManager.Boot"
    }
}
