package moe.chensi.volume

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import moe.chensi.volume.compose.AppVolumeList
import moe.chensi.volume.compose.SystemVolumePanel
import moe.chensi.volume.ui.theme.VolumeManagerTheme

/** A touchable, non-focusable Root panel that does not pause the underlying Activity. */
class RootOverlayPanel(private val context: Context) {
    private val manager get() = (context.applicationContext as MyApplication).manager
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val dismissRunnable = Runnable { hide() }
    private var view: View? = null
    private var owner: PanelLifecycleOwner? = null

    fun show() {
        if (!Settings.canDrawOverlays(context)) {
            Log.w("VolumeManager.RootPanel", "Overlay permission not granted")
            return
        }
        if (view == null) {
            val lifecycleOwner = PanelLifecycleOwner()
            val panel = object : AbstractComposeView(context) {
                init {
                    setViewTreeLifecycleOwner(lifecycleOwner)
                    setViewTreeSavedStateRegistryOwner(lifecycleOwner)
                }

                override fun onTouchEvent(event: MotionEvent): Boolean {
                    if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                        hide()
                        return true
                    }
                    return super.onTouchEvent(event)
                }

                @Composable
                override fun Content() {
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
                                    onOutputDeviceSelected = null,
                                    masterVolumeScale = manager.masterVolumeScale
                                ) {
                                    item("system_volume_panel") {
                                        Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
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
                                            moe.chensi.volume.compose.JamesDspControls(
                                                enabled = manager.jamesDspControlsEnabled,
                                                onChange = ::restartDismissTimer
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.CENTER }
            try {
                windowManager.addView(panel, params)
                lifecycleOwner.lifecycleRegistry.currentState = Lifecycle.State.RESUMED
                owner = lifecycleOwner
                view = panel
            } catch (error: Exception) {
                lifecycleOwner.lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
                Log.e("VolumeManager.RootPanel", "Could not show Root overlay", error)
                return
            }
        }
        restartDismissTimer()
    }

    private fun restartDismissTimer() {
        handler.removeCallbacks(dismissRunnable)
        handler.postDelayed(dismissRunnable, 5_000L)
    }

    private fun hide() {
        handler.removeCallbacks(dismissRunnable)
        val panel = view ?: return
        view = null
        runCatching { windowManager.removeView(panel) }
            .onFailure { Log.w("VolumeManager.RootPanel", "Could not remove Root overlay", it) }
        owner?.lifecycleRegistry?.currentState = Lifecycle.State.DESTROYED
        owner = null
    }

    private class PanelLifecycleOwner : SavedStateRegistryOwner {
        val lifecycleRegistry = LifecycleRegistry(this)
        private val savedStateController = SavedStateRegistryController.create(this).apply {
            performRestore(null)
        }

        init { lifecycleRegistry.currentState = Lifecycle.State.STARTED }

        override val lifecycle: Lifecycle get() = lifecycleRegistry
        override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry
    }
}
