package moe.chensi.volume

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

/** Keeps the opted-in root session alive without installing or mounting persistent files. */
class RootKeepAliveService : Service() {
    companion object {
        private const val CHANNEL_ID = "root_session"
        private const val NOTIFICATION_ID = 1703

        fun start(context: Context) {
            context.startForegroundService(Intent(context, RootKeepAliveService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RootKeepAliveService::class.java))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val settings = getSharedPreferences("access", MODE_PRIVATE)
        if (settings.getString("mode", "shizuku") != "root" ||
            !settings.getBoolean("start_at_boot", false)
        ) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        val notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Root volume session", NotificationManager.IMPORTANCE_LOW)
        )
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Volume Manager")
            .setContentText("Root volume control is active")
            .setContentIntent(openApp)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        val manager = (application as MyApplication).manager
        manager.connectRoot(this)
        return START_STICKY
    }
}
