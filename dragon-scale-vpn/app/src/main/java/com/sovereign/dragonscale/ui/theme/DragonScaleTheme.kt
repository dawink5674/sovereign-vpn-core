package com.sovereign.dragonscale.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DragonScaleColorScheme = darkColorScheme(
    primary = DragonCyan,
    onPrimary = SurfaceDeep,
    primaryContainer = DragonCyanDim,
    onPrimaryContainer = TextPrimary,

    secondary = DragonViolet,
    onSecondary = SurfaceDeep,
    secondaryContainer = DragonVioletDim,
    onSecondaryContainer = TextPrimary,

    tertiary = DragonRed,
    onTertiary = TextPrimary,

    background = SurfaceDeep,
    onBackground = TextPrimary,

    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceCard,
    onSurfaceVariant = TextSecondary,

    error = DragonRed,
    onError = TextPrimary,

    outline = TextMuted,
)

@Composable
fun DragonScaleTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DragonScaleColorScheme,
        typography = DragonScaleTypography,
        content = content
    )
}
