package moe.chensi.volume

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/** Starts the opted-in root session after credential-encrypted settings become available. */
class RootStartupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val settings = context.getSharedPreferences("access", Context.MODE_PRIVATE)
        if (settings.getString("mode", "shizuku") != "root" ||
            !settings.getBoolean("start_at_boot", false)
        ) return

        Log.i(TAG, "Starting opted-in root mode after boot")
        RootKeepAliveService.start(context)
    }

    private companion object {
        const val TAG = "VolumeManager.Boot"
    }
}
