package com.looknote.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Amber = Color(0xFFF2B33D)
val AmberDark = Color(0xFF8A5A00)

private val Dark = darkColorScheme(
    primary = Amber,
    onPrimary = Color(0xFF1E1400),
    primaryContainer = Color(0xFF3D2C00),
    onPrimaryContainer = Color(0xFFFFDEA6),
    secondary = Color(0xFFCFC5B4),
    background = Color(0xFF121212),
    surface = Color(0xFF121212),
    surfaceVariant = Color(0xFF2A2A2E),
    surfaceContainer = Color(0xFF1C1C1F),
)

private val Light = lightColorScheme(
    primary = AmberDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDEA6),
    onPrimaryContainer = Color(0xFF2B1D00),
    secondary = Color(0xFF6B5D3F),
)

@Composable
fun LookNoteTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        content = content,
    )
}
