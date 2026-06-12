package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = DarkButterYellow,
    onPrimary = DarkSurfacePrimary,
    primaryContainer = DarkSurfaceTertiary,
    onPrimaryContainer = DarkTextPrimary,
    secondary = DarkTextSecondary,
    background = DarkSurfacePrimary,
    surface = DarkSurfacePrimary,
    surfaceVariant = DarkSurfaceSecondary,
    onBackground = DarkTextPrimary,
    onSurface = DarkTextPrimary,
    onSurfaceVariant = DarkTextSecondary,
    error = ErrorRed
)

private val LightColorScheme = lightColorScheme(
    primary = ButterYellow,
    onPrimary = TextPrimary,
    primaryContainer = ButterSoft,
    onPrimaryContainer = AccentDeep,
    secondary = TextSecondary,
    background = SurfacePrimary,
    surface = SurfacePrimary,
    surfaceVariant = SurfaceSecondary,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    error = ErrorRed,
    outline = BorderStrong
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Keep consistent branding colors by default
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
