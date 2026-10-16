package moe.chensi.volume

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import moe.chensi.volume.data.App
import moe.chensi.volume.data.normalizeMasterVolume
import moe.chensi.volume.data.AppPreferencesStore
import moe.chensi.volume.system.AudioPlaybackConfigurationProxy
import moe.chensi.volume.system.NotificationManagerProxy
import moe.chensi.volume.system.PackageManagerProxy
import org.joor.Reflect
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuProvider

@SuppressLint("PrivateApi")
class Manager(context: Context, dataStore: DataStore<Preferences>) {
    companion object {
        const val SHIZUKU_PACKAGE_NAME = "moe.shizuku.privileged.api"

        // Per-app output routing is deferred until the AudioPolicy lifecycle is stable.
        const val OUTPUT_ROUTING_ENABLED = false
    }

    enum class ShizukuStatus {
        Uninstalled, Disconnected, PermissionDenied, Connected
    }

    enum class AccessMode { Shizuku, Root }

    private val appContext = context.applicationContext
    private val settings = context.getSharedPreferences("access", Context.MODE_PRIVATE)
    var accessMode by mutableStateOf(
        if (settings.getString("mode", "shizuku") == "root") AccessMode.Root else AccessMode.Shizuku
    )
        private set
    var startAtBoot by mutableStateOf(settings.getBoolean("start_at_boot", false))
        private set
    var jamesDspControlsEnabled by mutableStateOf(settings.getBoolean("jamesdsp_controls_enabled", false))
        private set
    var hideJamesDspWhenInactive by mutableStateOf(settings.getBoolean("hide_jamesdsp_when_inactive", true))
        private set
    var rootError by mutableStateOf<String?>(null)
        private set
    private var rootConnected by mutableStateOf(false)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var started = false
    private var volumeReceiverRegistered = false
    var masterVolumeScale by mutableStateOf(1f)
        private set
    private var allAppsLoaded = false
    val isConnected: Boolean
        get() = if (accessMode == AccessMode.Root) rootConnected else shizukuStatus == ShizukuStatus.Connected

    private var _shizukuStatus by mutableStateOf(ShizukuStatus.Disconnected)
    val shizukuStatus
        get() = _shizukuStatus

    val audioManager = context.getSystemService(AudioManager::class.java)!!.apply {
        Reflect.onClass(AudioManager::class.java).call("getService").get<Any>()
            .apply { ToggleableBinderProxy.wrap(this) }
    }

    val activityManager = context.getSystemService(ActivityManager::class.java)!!.apply {
        Reflect.onClass(ActivityManager::class.java).call("getService").get<Any>()
            .apply { ToggleableBinderProxy.wrap(this) }
    }
    private val packageManager by lazy { PackageManagerProxy.get(context) }
    val notificationManagerProxy = NotificationManagerProxy(context)

    private val appPreferencesStore = AppPreferencesStore(dataStore)
    private val outputRouter = AudioOutputRouter(context.applicationContext)
    private var _outputDevices by mutableStateOf(
        audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .filter(AudioDeviceInfo::isSink)
            .distinctBy { it.type to it.address }
    )
    val outputDevices: List<AudioDeviceInfo> get() = _outputDevices
    private var _outputRoutingError by mutableStateOf<String?>(null)
    val outputRoutingError: String? get() = _outputRoutingError
    private val _systemSliderVisibility = mutableStateMapOf<String, Boolean>()
    val systemSliderVisibility: Map<String, Boolean>
        get() = _systemSliderVisibility

    fun isSystemSliderVisible(id: String): Boolean {
        return _systemSliderVisibility[id] ?: true
    }

    fun setSystemSliderVisible(id: String, visible: Boolean) {
        if ((_systemSliderVisibility[id] ?: true) == visible) {
            return
        }

        _systemSliderVisibility[id] = visible
        appPreferencesStore.setSystemSliderVisible(id, visible)
    }

    val apps = mutableStateMapOf<String, App>()

    private fun addApp(packageInfo: android.content.pm.PackageInfo): App? {
        val appInfo = packageInfo.applicationInfo ?: return null
        return apps.getOrPut(packageInfo.packageName) {
            App(
                packageManager,
                packageInfo,
                { packageManager.loadLabel(appInfo) },
                appPreferencesStore.getOrCreate(packageInfo.packageName),
                appPreferencesStore::save,
                ::onAppOutputDeviceChanged
            )
        }
    }

    private fun getApp(packageName: String): App? {
        return apps[packageName] ?: packageManager.getPackageInfo(packageName)?.let(::addApp)
    }

    fun loadAllApps() {
        if (allAppsLoaded) return
        packageManager.getInstalledPackagesForAllUsers().forEach(::addApp)
        allAppsLoaded = true
    }

    @EnableBinderProxy
    private fun initialize() {
        updateAppsForMasterVolume()
        if (!volumeReceiverRegistered) {
            appContext.registerReceiver(
                object : BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: Intent?) {
                        updateAppsForMasterVolume()
                    }
                },
                IntentFilter("android.media.VOLUME_CHANGED_ACTION"),
                Context.RECEIVER_EXPORTED
            )
            volumeReceiverRegistered = true
        }

        val playbackConfigurations = audioManager.activePlaybackConfigurations
        processAudioPlaybackConfigurations(playbackConfigurations)

        audioManager.registerAudioPlaybackCallback(
            object : AudioManager.AudioPlaybackCallback() {
                override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>) {
                    for (app in apps.values) {
                        app.clearPlayers()
                    }
                    processAudioPlaybackConfigurations(configs)
                }
            }, null
        )

        audioManager.registerAudioDeviceCallback(object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                mainHandler.post { refreshOutputDevices() }
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                mainHandler.post { refreshOutputDevices() }
            }
        }, mainHandler)
    }

    private fun updateAppsForMasterVolume() {
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val masterVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val scale = normalizeMasterVolume(masterVolume, maxVolume)
        masterVolumeScale = scale
    }

    @EnableBinderProxy
    private fun refreshOutputDevices() {
        _outputDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .filter(AudioDeviceInfo::isSink)
            .distinctBy { it.type to it.address }
        applyOutputRouting()
    }

    private fun onAppOutputDeviceChanged() {
        applyOutputRouting()
    }

    @EnableBinderProxy
    private fun clearOutputRouting() {
        outputRouter.unregister()
    }

    @EnableBinderProxy
    private fun applyOutputRouting() {
        if (!OUTPUT_ROUTING_ENABLED || !isConnected) return
        outputRouter.update(apps.values, outputDevices)
        _outputRoutingError = outputRouter.lastError
    }

    @SuppressLint("DiscouragedPrivateApi")
    @EnableBinderProxy
    fun processAudioPlaybackConfigurations(configs: List<AudioPlaybackConfiguration>) {
        val runningProcesses = activityManager.runningAppProcesses

        for (config in configs) {
            val proxy = AudioPlaybackConfigurationProxy(config)

            val pid = proxy.clientPid
            val process = runningProcesses.find { process -> process.pid == pid } ?: continue

            val packageName = process.pkgList[0] ?: continue
            val app = getApp(packageName) ?: continue

            app.addPlayer(proxy)
        }
    }

    init {
        val isShizukuInstalled = try {
            context.packageManager.getPackageInfo(SHIZUKU_PACKAGE_NAME, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }

        if (!isShizukuInstalled) {
            _shizukuStatus = ShizukuStatus.Uninstalled
        } else if (!Shizuku.pingBinder()) {
            _shizukuStatus = ShizukuStatus.Disconnected
        }

        RootAccess.active = accessMode == AccessMode.Root
        RootAccess.onConnected = {
            mainHandler.post {
                rootConnected = true
                rootError = null
                if (accessMode == AccessMode.Root) {
                    start()
                    applyOutputRouting()
                }
            }
        }
        RootAccess.onDisconnected = {
            mainHandler.post {
                rootConnected = false
                if (accessMode == AccessMode.Root) rootError = "Root bridge disconnected"
            }
        }
        if (accessMode == AccessMode.Root) connectRoot(context)

        Shizuku.addBinderReceivedListenerSticky {
            if (Shizuku.isPreV11()) {
                return@addBinderReceivedListenerSticky
            }

            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                _shizukuStatus = ShizukuStatus.Connected
                if (accessMode == AccessMode.Shizuku) {
                    start()
                    applyOutputRouting()
                }
            } else {
                _shizukuStatus = ShizukuStatus.PermissionDenied
            }
        }

        Shizuku.addBinderDeadListener {
            _shizukuStatus = ShizukuStatus.Disconnected
        }

        Shizuku.addRequestPermissionResultListener { _, grantResult ->
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                _shizukuStatus = ShizukuStatus.Connected
                if (accessMode == AccessMode.Shizuku) {
                    start()
                    applyOutputRouting()
                }
            }
        }

        ShizukuProvider.requestBinderForNonProviderProcess(context)
    }

    private fun start() {
        if (started) return
        started = true
        appPreferencesStore.track { first ->
            for ((packageName, index) in appPreferencesStore.indices) {
                if (!first) {
                    // Replace with new reference
                    apps[packageName]?.setPreferences(appPreferencesStore.values[index])
                }
            }

            _systemSliderVisibility.clear()
            _systemSliderVisibility.putAll(appPreferencesStore.systemSliderVisibility)

            if (first) {
                initialize()
            }
            applyOutputRouting()
        }
    }

    fun selectShizuku() {
        if (accessMode == AccessMode.Root && rootConnected) clearOutputRouting()
        accessMode = AccessMode.Shizuku
        RootAccess.active = false
        RootAccess.disconnect()
        rootConnected = false
        settings.edit().putString("mode", "shizuku").apply()
        RootKeepAliveService.stop(appContext)
        if (shizukuStatus == ShizukuStatus.Connected) {
            start()
            applyOutputRouting()
        }
    }

    fun updateStartAtBoot(enabled: Boolean) {
        startAtBoot = enabled
        settings.edit().putBoolean("start_at_boot", enabled).apply()
        if (enabled && accessMode == AccessMode.Root) {
            RootKeepAliveService.start(appContext)
        } else {
            RootKeepAliveService.stop(appContext)
        }
    }

    fun updateJamesDspControls(enabled: Boolean) {
        jamesDspControlsEnabled = enabled
        settings.edit().putBoolean("jamesdsp_controls_enabled", enabled).apply()
    }

    fun updateHideJamesDspWhenInactive(hide: Boolean) {
        hideJamesDspWhenInactive = hide
        settings.edit().putBoolean("hide_jamesdsp_when_inactive", hide).apply()
    }

    fun connectRoot(context: Context) {
        if (accessMode == AccessMode.Shizuku && shizukuStatus == ShizukuStatus.Connected) {
            clearOutputRouting()
        }
        accessMode = AccessMode.Root
        RootAccess.active = true
        rootConnected = RootAccess.connected
        rootError = null
        settings.edit().putString("mode", "root").apply()
        if (startAtBoot && context !is RootKeepAliveService) {
            RootKeepAliveService.start(appContext)
        }
        RootAccess.connect(context) { message ->
            mainHandler.post { if (accessMode == AccessMode.Root) rootError = message }
        }
    }
}
