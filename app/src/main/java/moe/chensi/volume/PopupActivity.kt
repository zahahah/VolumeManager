package moe.chensi.volume

import android.graphics.PixelFormat
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import moe.chensi.volume.compose.AppVolumeList
import moe.chensi.volume.compose.SystemVolumePanel
import moe.chensi.volume.ui.theme.VolumeManagerTheme

/** Translucent root-mode panel launched by the temporary root volume-controller Binder. */
class PopupActivity : ComponentActivity() {
    private val manager by lazy { (application as MyApplication).manager }
    private val dismissHandler = Handler(Looper.getMainLooper())
    private val dismissRunnable = Runnable { finish() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setBackgroundDrawableResource(android.R.color.transparent)
        window.setFormat(PixelFormat.TRANSLUCENT)
        window.setGravity(Gravity.CENTER)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        )
        setFinishOnTouchOutside(true)

        setContent {
            VolumeManagerTheme {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp, 16.dp)) {
                        AppVolumeList(
                            apps = manager.apps.values,
                            showAll = false,
                            onChange = ::restartDismissTimer,
                            outputDevices = manager.outputDevices,
                            onOutputDeviceSelected = { app, device ->
                                app.setOutputDevice(device)
                                manager.outputRoutingError?.let { error ->
                                    Toast.makeText(
                                        this@PopupActivity,
                                        "Could not route audio: $error",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        ) {
                            item("system_volume_panel") {
                                SystemVolumePanel(
                                    audioManager = manager.audioManager,
                                    notificationManagerProxy = manager.notificationManagerProxy,
                                    showCallVolumeAlways = false,
                                    applyVisibilityFilter = true,
                                    allowVisibilityConfig = false,
                                    isSliderVisible = manager::isSystemSliderVisible,
                                    onSliderVisibilityChange = manager::setSystemSliderVisible,
                                    onChange = ::restartDismissTimer
                                )
                            }
                        }
                    }
                }
            }
        }
        window.setLayout(
            (resources.displayMetrics.widthPixels * 0.92f).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT
        )
        restartDismissTimer()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        restartDismissTimer()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        restartDismissTimer()
    }

    override fun onDestroy() {
        dismissHandler.removeCallbacks(dismissRunnable)
        super.onDestroy()
    }

    private fun restartDismissTimer() {
        dismissHandler.removeCallbacks(dismissRunnable)
        dismissHandler.postDelayed(dismissRunnable, 5_000L)
    }
}
