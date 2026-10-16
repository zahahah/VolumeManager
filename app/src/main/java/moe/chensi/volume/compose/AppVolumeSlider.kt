package moe.chensi.volume.compose

import android.media.AudioDeviceInfo
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import moe.chensi.volume.data.App
import moe.chensi.volume.data.cappedAppVolumePercent
import moe.chensi.volume.icons.Hook
import moe.chensi.volume.icons.HookOff
import moe.chensi.volume.ui.theme.Typography
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppVolumeSlider(
    app: App,
    showOptions: Boolean,
    enableHide: Boolean = true,
    onChange: (() -> Unit)? = null,
    outputDevices: List<AudioDeviceInfo> = emptyList(),
    onOutputDeviceSelected: ((App, AudioDeviceInfo?) -> Unit)? = null,
    masterVolumeScale: Float = 1f
) {
    var showOutputMenu by remember(app.packageName) { mutableStateOf(false) }
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TrackSlider(
            modifier = Modifier.weight(1f),
            cornerRadius = 20.dp,
            value = app.volume,
            onValueChange = { value ->
                app.volume = value
                onChange?.invoke()
            }) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(16.dp, 8.dp)
            ) {
                if (app.icon != null) {
                    Image(
                        bitmap = app.icon!!,
                        contentDescription = "App icon",
                        modifier = Modifier.size(32.dp),
                        contentScale = ContentScale.FillWidth
                    )
                } else {
                    Box(
                        Modifier
                            .size(32.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    )
                }

                Text(
                    text = app.name,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = "${cappedAppVolumePercent(app.volume, masterVolumeScale)}/${(masterVolumeScale.coerceIn(0f, 1f) * 100).roundToInt()}",
                    style = Typography.bodySmall,
                    maxLines = 1,
                )
            }
        }

        if (onOutputDeviceSelected != null) {
            Box {
                IconButton(
                    onClick = { showOutputMenu = true }
                ) {
                    Icon(
                        imageVector = Icons.Default.Speaker,
                        contentDescription = "Choose output device for ${app.name}"
                    )
                }
                DropdownMenu(
                    expanded = showOutputMenu,
                    onDismissRequest = { showOutputMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("System default") },
                        onClick = {
                            onOutputDeviceSelected(app, null)
                            showOutputMenu = false
                        },
                        trailingIcon = if (app.outputDeviceType == null) {
                            { Text("✓") }
                        } else null
                    )
                    val outputNameCounts = outputDevices.groupingBy {
                        it.productName.toString().takeUnless(String::isBlank) ?: "Audio output"
                    }.eachCount()
                    outputDevices.forEach { device ->
                        val productName = device.productName.toString()
                            .takeUnless(String::isBlank) ?: "Audio output"
                        val duplicateName = (outputNameCounts[productName] ?: 0) > 1
                        val deviceName = if (duplicateName) {
                            "$productName · ${deviceTypeLabel(device.type)} (port ${device.id})"
                        } else {
                            productName
                        }
                        DropdownMenuItem(
                            text = { Text(deviceName) },
                            onClick = {
                                onOutputDeviceSelected(app, device)
                                showOutputMenu = false
                            },
                            trailingIcon = if (
                                app.outputDeviceType == device.type &&
                                app.outputDeviceAddress == device.address
                            ) {
                                { Text("✓") }
                            } else null
                        )
                    }
                }
            }
        }

        if (showOptions) {
            if (enableHide) {
                ToggleButton(
                    checked = app.hidden,
                    checkedIcon = Icons.Default.VisibilityOff,
                    checkedDescription = "Unhide app",
                    uncheckedIcon = Icons.Default.Visibility,
                    uncheckedDescription = "Hide app"
                ) {
                    app.hidden = it
                }
            }

            ToggleButton(
                checked = app.disableVolumeButtons,
                checkedIcon = HookOff,
                checkedDescription = "Enable volume buttons",
                uncheckedIcon = Hook,
                uncheckedDescription = "Disable volume buttons"
            ) {
                app.disableVolumeButtons = it
            }
        }
    }
}

private fun deviceTypeLabel(type: Int): String = when (type) {
    AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Earpiece"
    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Speaker"
    AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired headset"
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired headphones"
    AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth call audio"
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth media audio"
    AudioDeviceInfo.TYPE_HDMI, AudioDeviceInfo.TYPE_HDMI_ARC -> "HDMI"
    AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB audio"
    AudioDeviceInfo.TYPE_USB_HEADSET -> "USB headset"
    AudioDeviceInfo.TYPE_HEARING_AID -> "Hearing aid"
    AudioDeviceInfo.TYPE_BLE_HEADSET -> "Bluetooth LE headset"
    AudioDeviceInfo.TYPE_BLE_SPEAKER -> "Bluetooth LE speaker"
    else -> "Output type $type"
}
