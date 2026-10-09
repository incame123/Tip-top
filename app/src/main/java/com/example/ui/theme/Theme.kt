package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val ProDarkColorScheme = darkColorScheme(
    primary = ElectricBlueX,
    onPrimary = NavyBackground,
    primaryContainer = NavySurfaceElevated,
    onPrimaryContainer = TextPrimary,
    secondary = NeonPurpleO,
    onSecondary = NavyBackground,
    secondaryContainer = NavySurfaceHighlight,
    onSecondaryContainer = TextPrimary,
    tertiary = CyberAccentIndigo,
    onTertiary = TextPrimary,
    background = NavyBackground,
    onBackground = TextPrimary,
    surface = NavySurface,
    onSurface = TextPrimary,
    surfaceVariant = NavySurfaceElevated,
    onSurfaceVariant = TextSecondary,
    error = RoseError,
    onError = NavyBackground,
    outline = BorderSubtle
)

@Composable
fun TicTacToeProTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ProDarkColorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    TicTacToeProTheme(content = content)
}
