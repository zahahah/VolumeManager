package moe.chensi.volume.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import moe.chensi.volume.R

@Composable
fun VolumeManagerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // minSdk 33: wallpaper-derived Material 3 colors are always available.
    val context = LocalContext.current
    val systemColorScheme = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    val colorScheme = systemColorScheme.copy(
        primary = colorResource(R.color.celestial_blue),
        onPrimary = colorResource(R.color.celestial_blue_on),
        primaryContainer = if (darkTheme) Color(0xFF004C70) else Color(0xFFC8E6FF),
        onPrimaryContainer = if (darkTheme) Color(0xFFD1E8FF) else Color(0xFF001E2C)
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
