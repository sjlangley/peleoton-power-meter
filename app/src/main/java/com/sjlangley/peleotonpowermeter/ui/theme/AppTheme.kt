package com.sjlangley.peleotonpowermeter.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors =
    lightColorScheme(
        primary = Color(0xFF2E6B5F),
        onPrimary = Color(0xFFF8FAF7),
        secondary = Color(0xFF6D6656),
        background = Color(0xFFF4F0E8),
        surface = Color(0xFFFFFBF5),
        onSurface = Color(0xFF1E1F1B),
        error = Color(0xFFB64C32),
    )

private val DarkColors =
    darkColorScheme(
        primary = Color(0xFF89B5A7),
        secondary = Color(0xFFD2C7AF),
        background = Color(0xFF1C1C18),
        surface = Color(0xFF262621),
        onSurface = Color(0xFFF2F0E9),
        error = Color(0xFFFF8E73),
    )

@Composable
fun PeleotonPowerMeterTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
