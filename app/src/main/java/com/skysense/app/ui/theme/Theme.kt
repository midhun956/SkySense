package com.skysense.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SecondaryContainer  = Color(0xFF1A3A30)
private val OutlineVariant      = Color(0xFF1E2A3D)
private val ErrorContainer      = Color(0xFF3D1A1A)
private val Scrim               = Color(0xCC08090D)

private val SkySenseDarkColorScheme = darkColorScheme(
    primary              = CosmicBlue,
    onPrimary            = SpaceBlack,
    primaryContainer     = CosmicBlueDark,
    onPrimaryContainer   = CosmicBlueLight,
    secondary            = SignalExcellent,
    onSecondary          = SpaceBlack,
    secondaryContainer   = SecondaryContainer,
    onSecondaryContainer = SignalExcellent,
    tertiary             = GalileoColor,
    onTertiary           = SpaceBlack,
    background           = SpaceBlack,
    onBackground         = StarWhite,
    surface              = SpaceDeep,
    onSurface            = StarWhite,
    surfaceVariant       = SpaceCard,
    onSurfaceVariant     = MoonGrey,
    surfaceTint          = CosmicBlue,
    outline              = SpaceDivider,
    outlineVariant       = OutlineVariant,
    error                = SignalPoor,
    onError              = SpaceBlack,
    errorContainer       = ErrorContainer,
    onErrorContainer     = SignalPoor,
    inverseSurface       = StarWhite,
    inverseOnSurface     = SpaceDark,
    inversePrimary       = CosmicBlueDark,
    scrim                = Scrim,
)

@Composable
fun SkySenseTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SkySenseDarkColorScheme,
        typography = SkySenseTypography,
        content = content
    )
}
