package com.example.sendit.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SendItColorScheme = darkColorScheme(
    primary = SendItOrange,
    onPrimary = SendItBackground,
    primaryContainer = SendItAccentContainer,
    onPrimaryContainer = SendItPeach,
    inversePrimary = SendItAccentContainer,
    secondary = SendItPeach,
    onSecondary = SendItBackground,
    secondaryContainer = SendItControls,
    onSecondaryContainer = SendItPeach,
    tertiary = SendItPeach,
    onTertiary = SendItBackground,
    tertiaryContainer = SendItRaised,
    onTertiaryContainer = SendItPeach,
    background = SendItBackground,
    onBackground = SendItText,
    surface = SendItCard,
    onSurface = SendItText,
    surfaceVariant = SendItRaised,
    onSurfaceVariant = SendItMutedText,
    surfaceTint = SendItOrange,
    inverseSurface = SendItText,
    inverseOnSurface = SendItBackground,
    surfaceDim = SendItBackground,
    surfaceBright = SendItControls,
    surfaceContainerLowest = SendItBackground,
    surfaceContainerLow = SendItInset,
    surfaceContainer = SendItCard,
    surfaceContainerHigh = SendItRaised,
    surfaceContainerHighest = SendItControls,
    outline = SendItOutline,
    outlineVariant = SendItOutlineVariant,
    error = SendItError,
    onError = SendItOnError,
    errorContainer = SendItErrorContainer,
    onErrorContainer = SendItOnErrorContainer,
    scrim = Color.Black
)

// The supplied design is dark. Keep its colours consistent across device settings.
@Composable
fun SendItTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SendItColorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
