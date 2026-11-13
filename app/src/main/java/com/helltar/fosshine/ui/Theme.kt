package com.helltar.fosshine.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// the icon's blue and yellow, for phones without wallpaper colors: blue leads in light mode, yellow in dark
private val LightColors = lightColorScheme(
    primary = Color(0xFF1D4ED8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE1FF),
    onPrimaryContainer = Color(0xFF00174B),
    secondary = Color(0xFF6D5E0F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF8E287),
    onSecondaryContainer = Color(0xFF221B00),
    // neutrals tinted toward the blue instead of material's default violet
    background = Color(0xFFFAF8FF),
    surface = Color(0xFFFAF8FF),
    surfaceVariant = Color(0xFFE2E1EC),
    onSurfaceVariant = Color(0xFF45464F),
    surfaceContainer = Color(0xFFEEEDF4),
    surfaceContainerHigh = Color(0xFFE8E7EF),
    surfaceContainerHighest = Color(0xFFE3E1E9)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFACC15),
    onPrimary = Color(0xFF3A3000),
    primaryContainer = Color(0xFF544600),
    onPrimaryContainer = Color(0xFFFFE170),
    secondary = Color(0xFFB4C5FF),
    onSecondary = Color(0xFF00287A),
    secondaryContainer = Color(0xFF1D4ED8),
    onSecondaryContainer = Color(0xFFDCE1FF),
    background = Color(0xFF121318),
    surface = Color(0xFF121318),
    surfaceVariant = Color(0xFF45464F),
    onSurfaceVariant = Color(0xFFC5C6D0),
    surfaceContainer = Color(0xFF1F1F25),
    surfaceContainerHigh = Color(0xFF292A2F),
    surfaceContainerHighest = Color(0xFF34343A)
)

@Composable
fun FosshineTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current

    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }

    MaterialTheme(colorScheme = colors, content = content)
}
