package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val ConsoleColorScheme = darkColorScheme(
    primary = ConsoleWhite,
    onPrimary = ConsoleBackground,
    secondary = ConsoleText,
    onSecondary = ConsoleBackground,
    tertiary = ConsoleWhite,
    background = ConsoleBackground,
    onBackground = ConsoleWhite,
    surface = ConsoleSurface,
    onSurface = ConsoleWhite,
    surfaceVariant = ConsoleGrayDark,
    onSurfaceVariant = ConsoleGrayLight,
    outline = ConsoleBorder
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Force terminal to always be dark terminal
    dynamicColor: Boolean = false, // Disable dynamic colors for consistency
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = ConsoleColorScheme,
        typography = Typography,
        content = content
    )
}
