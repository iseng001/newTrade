package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val TerminalDarkColorScheme = darkColorScheme(
    primary = BullionGold,
    onPrimary = OnBullionGold,
    primaryContainer = BullionGoldContainer,
    onPrimaryContainer = BullionGold,
    secondary = SignalEmerald,
    onSecondary = ObsidianCanvas,
    secondaryContainer = SignalEmeraldContainer,
    onSecondaryContainer = SignalEmerald,
    tertiary = SignalCyan,
    onTertiary = ObsidianCanvas,
    background = ObsidianCanvas,
    onBackground = TextPrimary,
    surface = SurfaceCard,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceElevated,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = ObsidianCanvas,
    surfaceContainerLow = SurfaceCard,
    surfaceContainer = SurfaceElevated,
    surfaceContainerHigh = SurfaceHighlight,
    outline = HairlineBorder,
    outlineVariant = HairlineBorder,
    error = SignalCrimson,
    onError = TextPrimary,
    errorContainer = SignalCrimsonContainer,
    onErrorContainer = SignalCrimson
)

private val TerminalShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun ScalpShizukuTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TerminalDarkColorScheme,
        typography = AppTypography,
        shapes = TerminalShapes,
        content = content
    )
}
