package moe.chensi.volume.compose

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.chensi.volume.jamesdsp.JamesDspClient
import moe.chensi.volume.jamesdsp.JamesDspState
import java.util.Locale

private val JamesDspLilac = Color(0xFFD0BCFF)
private val JamesDspLilacOn = Color(0xFF381E72)

private data class JamesDspEffect(
    val id: String,
    val label: String,
    val shortLabel: String
)

private val jamesDspEffects = listOf(
    JamesDspEffect("compander", "Compander", "CP"),
    JamesDspEffect("bass", "Bass", "BS"),
    JamesDspEffect("equalizer", "Equalizer", "EQ"),
    JamesDspEffect("graphic_eq", "Graphic EQ", "GE"),
    JamesDspEffect("reverb", "Reverb", "RV"),
    JamesDspEffect("stereo_wide", "Stereo wide", "SW"),
    JamesDspEffect("crossfeed", "Crossfeed", "CF"),
    JamesDspEffect("tube", "Tube", "TU"),
    JamesDspEffect("ddc", "DDC", "DD"),
    JamesDspEffect("liveprog", "LiveProg", "LP"),
    JamesDspEffect("convolver", "Convolver", "CV")
)

/** Global JamesDSP controls, opt-in from app settings; these are not per-app effects. */
@Composable
fun JamesDspControls(
    enabled: Boolean,
    hideWhenInactive: Boolean = true,
    onChange: (() -> Unit)? = null,
    onOpenEffect: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (!enabled) return
    val context = androidx.compose.ui.platform.LocalContext.current
    val client = remember(context) { JamesDspClient(context) }
    val scope = rememberCoroutineScope()
    var state by remember(client) { mutableStateOf(client.getCachedState()) }
    var expanded by remember { mutableStateOf(false) }
    var loadFinished by remember { mutableStateOf(false) }
    var gainUpdateJob by remember { mutableStateOf<Job?>(null) }
    var selectedParameter by remember { mutableStateOf<String?>(null) }
    var refreshTick by remember { mutableStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshTick++
            if (event == Lifecycle.Event.ON_STOP) selectedParameter = null
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            gainUpdateJob?.cancel()
            selectedParameter = null
        }
    }

    LaunchedEffect(enabled, refreshTick) {
        state = withContext(Dispatchers.IO) { client.getState() }
        loadFinished = true
    }

    if ((!loadFinished && state == null) || (state?.active == false && hideWhenInactive)) return

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = if (state?.active == false) Color(0xFF303030)
        else MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val current = state
            if (current == null) {
                Text(
                    text = if (loadFinished) client.lastError
                        ?: "JamesDSP not detected or integration API unavailable"
                    else "Connecting to JamesDSP…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val inactive = !current.active
                val buttonColor = if (inactive) Color(0xFF555555) else JamesDspLilac
                val buttonOnColor = if (inactive) Color(0xFF111111) else JamesDspLilacOn
                val mutedColor = if (inactive) Color(0xFFB0B0B0) else MaterialTheme.colorScheme.onSurfaceVariant
                val monochrome = remember { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) }
                val appIcon = remember(current.packageName) {
                    runCatching {
                        client.getAppIcon(current.packageName).toBitmap(48, 48).asImageBitmap()
                    }.getOrNull()
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(buttonColor)
                            .clickable {
                                selectedParameter = null
                                expanded = !expanded
                            }
                            .semantics {
                                contentDescription = "JamesDSP effects"
                                role = Role.Button
                                stateDescription = if (expanded) "Expanded" else "Collapsed"
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (appIcon != null) {
                            Image(
                                bitmap = appIcon,
                                contentDescription = "JamesDSP",
                                modifier = Modifier.size(30.dp),
                                colorFilter = if (inactive) monochrome else null
                            )
                        } else {
                            androidx.compose.material3.Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = buttonOnColor
                            )
                        }
                    }
                    val parameterRange = selectedParameter?.let { JamesDspClient.PARAMETER_RANGES[it] }
                    val sliderValue = if (selectedParameter == null) current.postGainDb
                        else current.parameters[selectedParameter] ?: parameterRange?.start ?: 0f
                    TrackSlider(
                        modifier = Modifier.weight(1f),
                        value = sliderValue,
                        valueRange = parameterRange ?: (-15f..15f),
                        onValueChange = { rawValue: Float ->
                            val snappedValue = (kotlin.math.round(rawValue * 10f) / 10f)
                            val parameter = selectedParameter
                            state = if (parameter == null) current.copy(postGainDb = snappedValue)
                                else current.copy(parameters = current.parameters + (parameter to snappedValue))
                            client.cacheState(state)
                            gainUpdateJob?.cancel()
                            gainUpdateJob = scope.launch {
                                delay(140)
                                val success = withContext(Dispatchers.IO) {
                                    if (parameter == null) client.setPostGain(current.packageName, snappedValue)
                                    else client.setParameter(current.packageName, parameter, snappedValue)
                                }
                                if (!success) state = withContext(Dispatchers.IO) { client.getState() }
                                onChange?.invoke()
                            }
                        },
                        cornerRadius = 20.dp,
                        trackColor = buttonColor.copy(alpha = 0.22f),
                        onTrackColor = MaterialTheme.colorScheme.onSurface,
                        fillColor = buttonColor,
                        onFillColor = buttonOnColor
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(when (selectedParameter) {
                                "bass" -> "Bass gain"
                                "stereo_wide" -> "Stereo width"
                                "tube" -> "Tube drive"
                                else -> "Post gain"
                            }, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.weight(1f))
                            Text(
                                if (selectedParameter == "stereo_wide")
                                    String.format(Locale.getDefault(), "%.1f", sliderValue)
                                else String.format(Locale.getDefault(), "%+.1f dB", sliderValue),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                if (expanded) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Column(
                            modifier = Modifier.width(58.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(if (current.active) JamesDspLilac else Color(0xFF555555))
                                    .clickable {
                                        val next = !current.active
                                        state = current.copy(active = next)
                                        client.cacheState(state)
                                        scope.launch {
                                            val success = withContext(Dispatchers.IO) {
                                                client.setPoweredOn(current.packageName, next)
                                            }
                                            if (!success) state = withContext(Dispatchers.IO) { client.getState() }
                                            onChange?.invoke()
                                        }
                                    }
                                    .semantics {
                                        contentDescription = "JamesDSP power"
                                        role = Role.Switch
                                        stateDescription = if (current.active) "On" else "Off"
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                androidx.compose.material3.Icon(
                                    imageVector = Icons.Default.PowerSettingsNew,
                                    contentDescription = null,
                                    tint = if (current.active) JamesDspLilacOn else Color(0xFF111111)
                                )
                            }
                            Text(
                                text = "Power",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                maxLines = 1,
                                color = if (inactive) Color(0xFFB0B0B0) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        jamesDspEffects.forEach { effect ->
                            val isEnabled = current.effects[effect.id] == true
                            Column(
                                modifier = Modifier.width(58.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (inactive) Color(0xFF555555)
                                            else if (isEnabled) JamesDspLilac
                                            else JamesDspLilac.copy(alpha = 0.24f)
                                        )
                                        .combinedClickable(
                                            onLongClick = {
                                                gainUpdateJob?.cancel()
                                                if (effect.id in JamesDspClient.PARAMETER_RANGES) {
                                                    selectedParameter = effect.id
                                                    onChange?.invoke()
                                                } else {
                                                    if (client.openEffect(current.packageName, effect.id)) {
                                                        onOpenEffect?.invoke()
                                                    }
                                                }
                                            },
                                            onClick = {
                                                val updated = current.effects.toMutableMap()
                                                updated[effect.id] = !isEnabled
                                                state = current.copy(effects = updated)
                                                client.cacheState(state)
                                                scope.launch {
                                                    val success = withContext(Dispatchers.IO) {
                                                        client.setEffect(current.packageName, effect.id, !isEnabled)
                                                    }
                                                    if (!success) {
                                                        state = withContext(Dispatchers.IO) {
                                                            client.getState()
                                                        }
                                                    }
                                                    onChange?.invoke()
                                                }
                                            }
                                        )
                                        .semantics {
                                            contentDescription = effect.label
                                            role = Role.Switch
                                            stateDescription = if (isEnabled) "On" else "Off"
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = effect.shortLabel,
                                        color = if (inactive) Color(0xFF111111)
                                        else if (isEnabled) JamesDspLilacOn
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Text(
                                    text = effect.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 9.sp,
                                    maxLines = 1,
                                    color = mutedColor
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
