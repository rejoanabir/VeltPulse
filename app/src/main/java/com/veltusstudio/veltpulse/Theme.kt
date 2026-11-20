package com.veltusstudio.veltpulse.core.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val VeltDarkColorScheme = darkColorScheme(
    primary = VeltColors.SilverPrimary,
    onPrimary = VeltColors.PureBlack,
    secondary = VeltColors.PulseCyanHint,
    onSecondary = VeltColors.PureBlack,
    background = VeltColors.PureBlack,
    onBackground = VeltColors.SilverPrimary,
    surface = VeltColors.Obsidian,
    onSurface = VeltColors.SilverPrimary,
    surfaceVariant = VeltColors.SurfaceDark,
    onSurfaceVariant = VeltColors.SilverSecondary,
    outline = VeltColors.Hairline
)

@Composable
fun VeltPulseTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = VeltDarkColorScheme,
        typography = VeltTypography,
        content = content
    )
}
