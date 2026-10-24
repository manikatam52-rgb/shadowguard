package com.shadowguard.controller.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val DarkBackground = Color(0xFF121318)
val DarkSurface = Color(0xFF1E1F28)
val DarkSurfaceVariant = Color(0xFF282A36)
val PrimaryNeon = Color(0xFF00E676)
val PrimaryVariant = Color(0xFF00B0FF)
val AccentRed = Color(0xFFFF5252)
val TextPrimary = Color(0xFFF1F1F5)
val TextSecondary = Color(0xFF9E9EA8)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryNeon,
    onPrimary = Color.Black,
    secondary = PrimaryVariant,
    onSecondary = Color.Black,
    background = DarkBackground,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    error = AccentRed,
    onError = Color.White
)

@Composable
fun ShadowGuardTheme(
    darkTheme: Boolean = true, // Always default to modern dark theme
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
