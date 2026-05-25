package com.andyxu.readmd.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = ReadMdBlue,
    onPrimary = Color.White,
    secondary = Color(0xFF3B82F6),
    onSecondary = Color.White,
    tertiary = Color(0xFFB45309),
    background = ReadMdSurface,
    onBackground = ReadMdInk,
    surface = Color.White,
    onSurface = ReadMdInk,
    surfaceVariant = Color(0xFFEFF3FB),
    onSurfaceVariant = Color(0xFF4B5563),
    outline = Color(0xFFCBD5E1),
)

private val DarkColors = darkColorScheme(
    primary = ReadMdAccentSoft,
    onPrimary = Color(0xFF10233E),
    secondary = Color(0xFF8DB2FF),
    onSecondary = Color(0xFF132842),
    tertiary = Color(0xFFF5C56B),
    background = ReadMdNightBackground,
    onBackground = ReadMdNightText,
    surface = ReadMdNightSurface,
    onSurface = ReadMdNightText,
    surfaceVariant = ReadMdNightCard,
    onSurfaceVariant = ReadMdNightMuted,
    outline = ReadMdNightOutline,
)

private val ElderLightColors = lightColorScheme(
    primary = ReadMdBlueDark,
    onPrimary = Color.White,
    secondary = ReadMdBlue,
    onSecondary = Color.White,
    tertiary = Color(0xFF92400E),
    background = ReadMdElderYellow,
    onBackground = Color(0xFF18181B),
    surface = ReadMdWarmPaper,
    onSurface = Color(0xFF18181B),
    surfaceVariant = ReadMdWarmCard,
    onSurfaceVariant = Color(0xFF5B5563),
    outline = ReadMdWarmOutline,
)

private val ElderDarkColors = darkColorScheme(
    primary = ReadMdAccentSoft,
    onPrimary = Color(0xFF0F2038),
    secondary = Color(0xFF98B8FF),
    onSecondary = Color(0xFF0F2038),
    tertiary = Color(0xFFF5D08A),
    background = ReadMdNightBackground,
    onBackground = ReadMdNightText,
    surface = ReadMdNightSurface,
    onSurface = ReadMdNightText,
    surfaceVariant = ReadMdNightCard,
    onSurfaceVariant = ReadMdNightMuted,
    outline = ReadMdNightOutline,
)

@Composable
fun ReadMDTheme(
    darkTheme: Boolean = false,
    elderMode: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = when {
        elderMode && darkTheme -> ElderDarkColors
        elderMode -> ElderLightColors
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colors,
        content = content,
    )
}
