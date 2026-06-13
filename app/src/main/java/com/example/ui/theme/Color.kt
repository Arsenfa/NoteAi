package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

// Light Mode NoteAi colors (static definitions for theme configuration)
val LightButterYellow = Color(0xFFF2C14E)
val LightButterSoft = Color(0xFFFBE9A8)
val LightAccentDeep = Color(0xFFC8960F)

val LightSurfacePrimary = Color(0xFFFFFFFF)
val LightSurfaceSecondary = Color(0xFFFAF8F3)
val LightSurfaceTertiary = Color(0xFFF3EFE6)
val LightSurfaceElevated = Color(0xFFFFFFFF)

val LightTextPrimary = Color(0xFF1A1A1A)
val LightTextSecondary = Color(0xFF6B6B6B)
val LightTextTertiary = Color(0xFFA3A3A3)

val LightBorderSubtle = Color(0xFFEDEAE3)
val LightBorderStrong = Color(0xFFD9D4C7)

val LightSuccessSage = Color(0xFF4D7C0F)
val LightErrorRed = Color(0xFFD94F4F)

val DarkSuccessSage = Color(0xFF81C784)
val DarkErrorRed = Color(0xFFEF9A9A)

// Dark Theme Colors (static definitions for theme configuration)
val DarkSurfacePrimary = Color(0xFF141414)
val DarkSurfaceSecondary = Color(0xFF1C1C1C)
val DarkSurfaceTertiary = Color(0xFF242424)
val DarkSurfaceElevated = Color(0xFF1F1F1F)
val DarkTextPrimary = Color(0xFFF5F3EE)
val DarkTextSecondary = Color(0xFFA8A49A)
val DarkTextTertiary = Color(0xFF6E6A60)
val DarkButterYellow = Color(0xFFE6B23A)
val DarkButterSoft = Color(0xFF242424)
val DarkAccentDeep = Color(0xFFE6B23A)
val DarkBorderSubtle = Color(0xFF242424)
val DarkBorderStrong = Color(0xFF333333)

// Dynamic theme colors that automatically switch between Light and Dark mode
val ButterYellow: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.primary

val ButterSoft: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.primaryContainer

val AccentDeep: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.onPrimaryContainer

val SurfacePrimary: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.surface

val SurfaceSecondary: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.surfaceVariant

val SurfaceTertiary: Color
    @Composable
    @ReadOnlyComposable
    get() = if (MaterialTheme.colorScheme.primary == LightButterYellow) LightSurfaceTertiary else DarkSurfaceTertiary

val SurfaceElevated: Color
    @Composable
    @ReadOnlyComposable
    get() = if (MaterialTheme.colorScheme.primary == LightButterYellow) LightSurfaceElevated else DarkSurfaceElevated

val TextPrimary: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.onSurface

val TextSecondary: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.onSurfaceVariant

val TextTertiary: Color
    @Composable
    @ReadOnlyComposable
    get() = if (MaterialTheme.colorScheme.primary == LightButterYellow) LightTextTertiary else DarkTextTertiary

val BorderSubtle: Color
    @Composable
    @ReadOnlyComposable
    get() = if (MaterialTheme.colorScheme.primary == LightButterYellow) LightBorderSubtle else DarkBorderSubtle

val BorderStrong: Color
    @Composable
    @ReadOnlyComposable
    get() = if (MaterialTheme.colorScheme.primary == LightButterYellow) LightBorderStrong else DarkBorderStrong

val SuccessSage: Color
    @Composable
    @ReadOnlyComposable
    get() = if (MaterialTheme.colorScheme.primary == LightButterYellow) LightSuccessSage else DarkSuccessSage

val ErrorRed: Color
    @Composable
    @ReadOnlyComposable
    get() = if (MaterialTheme.colorScheme.primary == LightButterYellow) LightErrorRed else DarkErrorRed
