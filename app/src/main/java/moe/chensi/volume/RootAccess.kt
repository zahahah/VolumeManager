package moe.chensi.volume

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/** A transient Binder relay hosted by an app_process started through su. No files are mounted. */
object RootAccess {
    private const val TAG = "VolumeManager.Root"
    internal const val AUTHORITY = "moe.chensi.volume.root_bridge"
    internal const val INSTALL = "install"
    internal val serviceNames = listOf("audio", "activity", "package", "notification", "activity_task")

    @Volatile private var binders: Map<String, IBinder> = emptyMap()
    @Volatile private var control: IBinder? = null
    internal val owner = Binder()
    @Volatile var active: Boolean = false
    @Volatile var onConnected: (() -> Unit)? = null
    @Volatile var onDisconnected: (() -> Unit)? = null
    private val launching = AtomicBoolean(false)

    val connected: Boolean get() = binders.isNotEmpty()

    fun binderFor(descriptor: String?): IBinder? = descriptor?.let(binders::get)

    internal fun install(values: Bundle) {
        val newBinders = serviceNames.mapNotNull { name ->
            values.getBinder(name)?.let { binder ->
                binder.interfaceDescriptor?.let { descriptor -> descriptor to binder }
            }
        }.toMap()
        val newControl = values.getBinder("control") ?: return
        if (newBinders.size != serviceNames.size) return
        val death = IBinder.DeathRecipient {
            binders = emptyMap()
            onDisconnected?.invoke()
        }
        try {
            newBinders.values.first().linkToDeath(death, 0)
        } catch (e: Exception) {
            Log.e(TAG, "Root bridge died before connection", e)
            return
        }
        binders = newBinders
        control = newControl
        launching.set(false)
        onConnected?.invoke()
    }

    fun disconnect() {
        val data = android.os.Parcel.obtain()
        try {
            control?.transact(1, data, null, IBinder.FLAG_ONEWAY)
        } catch (_: Exception) {
            // A dead bridge needs no cleanup.
        } finally {
            data.recycle()
        }
        control = null
        binders = emptyMap()
    }

    fun connect(context: Context, onError: (String) -> Unit) {
        if (connected) {
            onConnected?.invoke()
            return
        }
        if (!launching.compareAndSet(false, true)) return
        val apk = context.applicationInfo.sourceDir
        Thread {
            try {
                // The shell only starts a temporary process from the installed APK.
                val quotedApk = "'" + apk.replace("'", "'\\''") + "'"
                val command = "CLASSPATH=$quotedApk exec /system/bin/app_process /system/bin moe.chensi.volume.RootBridge"
                val process = ProcessBuilder("su", "-c", command)
                    .redirectErrorStream(true).start()
                val output = process.inputStream.bufferedReader().readText()
                val exit = process.waitFor()
                if (!connected) {
                    onError("Root bridge stopped ($exit): ${output.take(300)}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Cannot start root bridge", e)
                onError(e.message ?: "Cannot start root bridge")
            } finally {
                launching.set(false)
            }
        }.apply { name = "VolumeManager-root-launch"; isDaemon = true }.start()
    }
}

/** Accepts only a root-owned bridge; other apps cannot request or install a privileged Binder. */
class RootBridgeProvider : ContentProvider() {
    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method != RootAccess.INSTALL || Binder.getCallingUid() != 0 || extras == null) {
            throw SecurityException("Root-only bridge installation")
        }
        RootAccess.install(extras)
        return Bundle().apply {
            putBoolean("connected", RootAccess.connected)
            putBinder("owner", RootAccess.owner)
        }
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
