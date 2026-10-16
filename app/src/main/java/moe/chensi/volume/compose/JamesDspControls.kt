package moe.chensi.volume.compose

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    onChange: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (!enabled) return
    val context = androidx.compose.ui.platform.LocalContext.current
    val client = remember(context) { JamesDspClient(context) }
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<JamesDspState?>(null) }
    var expanded by remember { mutableStateOf(false) }
    var loadFinished by remember { mutableStateOf(false) }
    var gainUpdateJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(enabled) {
        state = withContext(Dispatchers.IO) { client.getState() }
        loadFinished = true
    }

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
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
                            .background(JamesDspLilac)
                            .clickable { expanded = !expanded }
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
                                modifier = Modifier.size(30.dp)
                            )
                        } else {
                            androidx.compose.material3.Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = JamesDspLilacOn
                            )
                        }
                    }
                    TrackSlider(
                        modifier = Modifier.weight(1f),
                        value = current.postGainDb,
                        valueRange = -15f..15f,
                        onValueChange = { rawValue ->
                            val snappedValue = (rawValue * 10f).toInt() / 10f
                            state = current.copy(postGainDb = snappedValue)
                            gainUpdateJob?.cancel()
                            gainUpdateJob = scope.launch {
                                delay(140)
                                withContext(Dispatchers.IO) {
                                    client.setPostGain(current.packageName, snappedValue)
                                }
                                onChange?.invoke()
                            }
                        },
                        cornerRadius = 20.dp,
                        trackColor = JamesDspLilac.copy(alpha = 0.22f),
                        onTrackColor = MaterialTheme.colorScheme.onSurface,
                        fillColor = JamesDspLilac,
                        onFillColor = JamesDspLilacOn
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Post gain", fontWeight = FontWeight.Medium)
                            Spacer(Modifier.weight(1f))
                            Text(
                                String.format(Locale.getDefault(), "%+.1f dB", current.postGainDb),
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
                                            if (isEnabled) JamesDspLilac
                                            else JamesDspLilac.copy(alpha = 0.24f)
                                        )
                                        .clickable {
                                            val updated = current.effects.toMutableMap()
                                            updated[effect.id] = !isEnabled
                                            state = current.copy(effects = updated)
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
                                        .semantics {
                                            contentDescription = effect.label
                                            role = Role.Switch
                                            stateDescription = if (isEnabled) "On" else "Off"
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = effect.shortLabel,
                                        color = if (isEnabled) JamesDspLilacOn
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
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
