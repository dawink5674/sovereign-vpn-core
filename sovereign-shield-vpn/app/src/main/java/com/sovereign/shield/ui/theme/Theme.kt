package com.sovereign.shield.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val SovereignColorScheme = darkColorScheme(
    primary = ShieldBlue,
    onPrimary = SpaceBlack,
    primaryContainer = ShieldBlueDim,
    onPrimaryContainer = TextPrimary,

    secondary = ShieldCyan,
    onSecondary = SpaceBlack,
    secondaryContainer = ShieldCyanDim,
    onSecondaryContainer = TextPrimary,

    tertiary = ShieldViolet,
    onTertiary = TextPrimary,
    tertiaryContainer = ShieldVioletDim,
    onTertiaryContainer = TextPrimary,

    background = SpaceBlack,
    onBackground = TextPrimary,

    surface = SpaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SpaceCard,
    onSurfaceVariant = TextSecondary,

    error = StatusDisconnected,
    onError = TextPrimary,

    outline = TextMuted,
    outlineVariant = TextFaint,

    inverseSurface = TextBright,
    inverseOnSurface = SpaceBlack,
)

@Composable
fun SovereignShieldTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SovereignColorScheme,
        typography = SovereignTypography,
        content = content
    )
}
