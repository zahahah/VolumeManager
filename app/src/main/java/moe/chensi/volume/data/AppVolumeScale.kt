package moe.chensi.volume.data

import kotlin.math.roundToInt

/** Converts the system media volume index into a normalized gain. */
internal fun normalizeMasterVolume(masterVolume: Int, maxMasterVolume: Int): Float =
    if (maxMasterVolume > 0) {
        (masterVolume.toFloat() / maxMasterVolume).coerceIn(0f, 1f)
    } else {
        1f
    }

/** The app slider is relative to the system media-volume ceiling. */
internal fun cappedAppVolumePercent(appVolume: Float, masterScale: Float): Int =
    (appVolume.coerceIn(0f, 1f) * masterScale.coerceIn(0f, 1f) * 100).roundToInt()
