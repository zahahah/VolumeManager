package moe.chensi.volume.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

@Composable
fun TrackSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    trackColor: Color = MaterialTheme.colorScheme.primaryContainer,
    onTrackColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    fillColor: Color = MaterialTheme.colorScheme.primary,
    onFillColor: Color = MaterialTheme.colorScheme.onPrimary,
    cornerRadius: Dp = 8.dp,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    content: @Composable BoxScope.() -> Unit = {}
) {
    val coercedValue = value.coerceIn(valueRange.start, valueRange.endInclusive)
    val latestValue by rememberUpdatedState(coercedValue)
    val latestOnValueChange by rememberUpdatedState(onValueChange)
    val density = LocalDensity.current
    val cornerRadiusPx = with(density) { cornerRadius.toPx() }

    val fillWidthPercentage =
        (coercedValue - valueRange.start) / (valueRange.endInclusive - valueRange.start)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(GenericShape { size, _ ->
                addRoundRect(
                    RoundRect(
                        0f, 0f, size.width, size.height, cornerRadius = CornerRadius(cornerRadiusPx)
                    )
                )
            })
            .background(trackColor)
            .pointerInput(enabled, valueRange) {
                if (enabled) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val startX = down.position.x
                        val startValue = latestValue
                        val totalRange = valueRange.endInclusive - valueRange.start
                        var dragging = false
                        var allowTap = true
                        var change: PointerInputChange = down

                        while (change.pressed) {
                            val nextChange = awaitPointerEvent().changes
                                .firstOrNull { it.id == down.id } ?: break
                            val deltaX = nextChange.position.x - startX
                            val deltaY = nextChange.position.y - down.position.y

                            if (!dragging && nextChange.isConsumed) {
                                allowTap = false
                            }
                            if (!dragging && abs(deltaX) > viewConfiguration.touchSlop &&
                                abs(deltaX) >= abs(deltaY)
                            ) {
                                dragging = true
                            } else if (!dragging && abs(deltaY) > viewConfiguration.touchSlop &&
                                abs(deltaY) > abs(deltaX)
                            ) {
                                allowTap = false
                            }

                            if (dragging) {
                                nextChange.consume()
                                val newValue = startValue + deltaX / size.width * totalRange
                                latestOnValueChange(newValue.coerceIn(valueRange.start, valueRange.endInclusive))
                            }
                            change = nextChange
                        }

                        if (!dragging && allowTap && size.width > 0) {
                            val percentage = (startX / size.width).coerceIn(0f, 1f)
                            latestOnValueChange(valueRange.start + percentage * totalRange)
                        }
                    }
                }
            },
    ) {
        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            CompositionLocalProvider(LocalContentColor provides onTrackColor) {
                content()
            }
        }

        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(GenericShape { size, _ ->
                    addRoundRect(
                        RoundRect(
                            0f,
                            0f,
                            fillWidthPercentage * size.width,
                            size.height,
                            cornerRadius = CornerRadius(with(density) { 2.dp.toPx() })
                        )
                    )
                })
                .background(fillColor)
        ) {
            CompositionLocalProvider(LocalContentColor provides onFillColor) {
                content()
            }
        }
    }
}

