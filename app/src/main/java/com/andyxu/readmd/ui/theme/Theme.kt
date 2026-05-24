package com.andyxu.readmd.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = ReadMdBlue,
    onPrimary = Color.White,
    secondary = Color(0xFF0F766E),
    onSecondary = Color.White,
    tertiary = Color(0xFFB45309),
    background = ReadMdSurface,
    onBackground = ReadMdInk,
    surface = Color.White,
    onSurface = ReadMdInk,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF93C5FD),
    onPrimary = Color(0xFF082F49),
    secondary = Color(0xFF5EEAD4),
    onSecondary = Color(0xFF042F2E),
    tertiary = Color(0xFFFBBF24),
    background = Color(0xFF0F172A),
    onBackground = Color(0xFFE5E7EB),
    surface = Color(0xFF111827),
    onSurface = Color(0xFFE5E7EB),
)

fun elderColorScheme(base: ColorScheme): ColorScheme = base.copy(
    background = ReadMdElderYellow,
    surface = Color(0xFFFFFBEB),
    onBackground = Color.Black,
    onSurface = Color.Black,
    primary = ReadMdBlueDark,
)

@Composable
fun ReadMDTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    elderMode: Boolean = false,
    content: @Composable () -> Unit,
) {
    val baseColors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = if (elderMode) elderColorScheme(baseColors) else baseColors,
        content = content,
    )
}

