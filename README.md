# Volume Manager

Fork of [yume-chan/VolumeManager](https://github.com/yume-chan/VolumeManager), with a standalone root mode and system-color theming.

**Work in progress**

Control each app's volume independently. Privileged APIs can be accessed with either [Shizuku](https://shizuku.rikka.app/) or root (`su`).

Requires Android 13.

<img width="360" alt="Screenshot" src="https://github.com/user-attachments/assets/7ad75000-a04b-4eba-95d9-1b9bd284d3bd" />
<img width="360" alt="Screenshot" src="https://github.com/user-attachments/assets/2853d3cc-1fef-4b5f-8e3d-50fef1aee3c3" />

(Screenshot from beta version, download available from action artifacts)

## Download

Latest development APKs can be downloaded from [this fork's build artifacts](https://github.com/zahahah/VolumeManager/actions/workflows/build.yml). The workflow uploads a debug APK and, when signing secrets are configured, a signed release APK.

This fork keeps the original package ID. A release signed with a different certificate cannot update an installation signed by the upstream certificate; uninstall the upstream app first if Android rejects the update.

## Usage

1. Install and enable [Shizuku](https://shizuku.rikka.app/) **or** have a working root manager (`su`).
2. Launch Volume Manager. Use the button in the top bar to choose Shizuku or Root. Grant the selected access request.
3. In Shizuku mode, the app attempts to enable its accessibility service. In Root mode, allow **Display over other apps** when prompted; the volume-button panel then floats without accessibility or pausing the app underneath.
4. You can change volume either from
   1. The main interface
   2. Press any volume button and a popup should appear, completely replace the default volume popup
   3. Enable accessibility button and click the button

Root mode starts a temporary `app_process` Binder bridge from the installed APK. It does not remount or write to `/system`, `/vendor`, or other system partitions and does not install a persistent root module. In Settings, **Start Root mode after boot** keeps the opted-in session alive with a foreground-service notification and reconnects after the user unlocks Android. Turning the option off or switching to Shizuku stops that service; the bridge also stops if the app process dies. App volume preferences remain normal Android user settings. Root compatibility depends on the device's `su` implementation and SELinux policy.

## Compare to [SoundMaster from ShizuTools](https://github.com/legendsayantan/ShizuTools/wiki/SoundMaster)

This app uses hidden API to directly change each audio stream's volume.

SoundMaster uses MediaProjection API to record audio from each app and apply post-effects.

| Feature                        | Volume Manager  | SoundMaster     |
| ------------------------------ | --------------- | --------------- |
| Minimal Android version        | 13              | 10              |
| Control volume of each app     | ✅              | ✅              |
| Set output device for each app | ⏸️ Temporarily disabled | ✅              |
| Change left-right balance      | ❌ <sup>2</sup> | ✅              |
| Equalizer (EQ)                 | ❌              | ✅              |
| Control protected apps         | ✅              | ❌ <sup>3</sup> |
| Zero latency added             | ✅              | ❌              |

Per-app output routing is temporarily disabled while its Android audio-policy lifecycle is fixed.
Saved selections are preserved, but no output-device menu or routing policy is applied in this build.

<sup>2</sup>: There are other APIs to do that, but not implemented in this app

<sup>3</sup>: Can be worked around by patching the app

## How does it work

1. Use [`AudioManager#getActivePlaybackConfigurations()`](<https://developer.android.com/reference/android/media/AudioManager#getActivePlaybackConfigurations()>) to get list of [`AudioPlaybackConfiguration`](https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/media/java/android/media/AudioPlaybackConfiguration.java;drc=e282cc572ef848b1cb8d622c2c4939aac37c3b27).

   Each `AudioPlaybackConfiguration` represents a audio player, like [`AudioTrack`](https://developer.android.com/reference/android/media/AudioTrack) and [`MediaPlayer`](https://developer.android.com/media/platform/mediaplayer)

2. Use [`ActivityManager#getRunningAppProcesses()`](<https://developer.android.com/reference/android/app/ActivityManager#getRunningAppProcesses()>) and [`PackageManager#getApplicationInfo()`](<https://developer.android.com/reference/android/content/pm/PackageManager#getApplicationInfo(java.lang.String,%20android.content.pm.PackageManager.ApplicationInfoFlags)>) to map and group `AudioPlaybackConfiguration#getClientPid()` to apps
3. Use `AudioPlaybackConfiguration#getPlayerProxy()` and [`IPlayer.setVolume()`](https://cs.android.com/android/platform/superproject/main/+/main:frameworks/av/media/libaudioclient/aidl/android/media/IPlayer.aidl;l=29;drc=75e48fea431b1de2bf1715eb5c22ba4c794200bd) to update the internal volume multiplier
4. Use [`AudioManager#registerAudioPlaybackCallback()`](<https://developer.android.com/reference/android/media/AudioManager?hl=en#registerAudioPlaybackCallback(android.media.AudioManager.AudioPlaybackCallback,%20android.os.Handler)>) to listen for new `AudioPlaybackConfiguration`s and apply current volume to them.

## Note

This app uses the same API as MIUI's "Adjust media sound in multiple apps". Because this API can only setting volume, not reading, the volume set by one app will not be reflected in the other one.
# JamesDSP integration (experimental)

On the `pixel-test` branch, enable **JamesDSP controls** in Settings to show a
global post-gain slider and effect toggles in the volume panel. The integration
is global to the JamesDSP engine, not per application. It requires a compatible
RootJamesDSP build that includes the protected `volume_manager` content-provider
API; older installations are detected as unavailable. Gain is limited to
−15..+15 dB and starts at the JamesDSP default of 0 dB.

GitHub Actions builds debug APKs for this branch. RootJamesDSP preview APKs are
published as separate workflow artifacts in the RootJamesDSP fork.
