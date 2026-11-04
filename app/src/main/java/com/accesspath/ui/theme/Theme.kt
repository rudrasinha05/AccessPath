package com.accesspath.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Colours with meaning on the floor plan. All pass WCAG AA against their background. */
@Immutable
data class MapColors(
    val route: Color,
    val closed: Color,
    val unsuitable: Color,
    val path: Color,
    val place: Color,
    val you: Color,
    val destination: Color,
    val label: Color,
    val background: Color
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF1A73E8), onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E3FD), onPrimaryContainer = Color(0xFF041E49),
    secondary = Color(0xFF4C626B), onSecondary = Color.White,
    secondaryContainer = Color(0xFFD3E3FD), onSecondaryContainer = Color(0xFF041E49),
    tertiary = Color(0xFF8B4A00), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDCC2), onTertiaryContainer = Color(0xFF2D1600),
    error = Color(0xFFBA1A1A), errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF6FAFD), surface = Color(0xFFF6FAFD),
    surfaceVariant = Color(0xFFDBE4E8), onSurfaceVariant = Color(0xFF40484C),
    outline = Color(0xFF70787C), outlineVariant = Color(0xFFBFC8CC),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF0F4F7),
    surfaceContainer = Color(0xFFEAEEF1), surfaceContainerHigh = Color(0xFFE4E9EC),
    surfaceContainerHighest = Color(0xFFDFE3E6), surfaceTint = Color(0xFF1A73E8)
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF8AB4F8), onPrimary = Color(0xFF062E6F),
    primaryContainer = Color(0xFF0842A0), onPrimaryContainer = Color(0xFFD3E3FD),
    secondary = Color(0xFFB3CAD5), onSecondary = Color(0xFF1E333C),
    secondaryContainer = Color(0xFF0842A0), onSecondaryContainer = Color(0xFFD3E3FD),
    tertiary = Color(0xFFFFB77C), onTertiary = Color(0xFF4A2800),
    tertiaryContainer = Color(0xFF6A3800), onTertiaryContainer = Color(0xFFFFDCC2),
    error = Color(0xFFFFB4AB), errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0F1416), surface = Color(0xFF0F1416),
    surfaceVariant = Color(0xFF40484C), onSurfaceVariant = Color(0xFFBFC8CC),
    outline = Color(0xFF8A9296), outlineVariant = Color(0xFF40484C),
    surfaceContainerLowest = Color(0xFF0A0F11), surfaceContainerLow = Color(0xFF171C1F),
    surfaceContainer = Color(0xFF1B2023), surfaceContainerHigh = Color(0xFF252B2D),
    surfaceContainerHighest = Color(0xFF303638), surfaceTint = Color(0xFF8AB4F8)
)

private val LightMap = MapColors(
    route = Color(0xFF00677F), closed = Color(0xFFBA1A1A), unsuitable = Color(0xFF8A9296),
    path = Color(0xFF9AA5AA), place = Color(0xFF40484C), you = Color(0xFF00677F),
    destination = Color(0xFF8B4A00), label = Color(0xFF171C1F), background = Color(0xFFFFFFFF)
)

private val DarkMap = MapColors(
    route = Color(0xFF5FD4FA), closed = Color(0xFFFF8A80), unsuitable = Color(0xFF70787C),
    path = Color(0xFF5C6569), place = Color(0xFFBFC8CC), you = Color(0xFF5FD4FA),
    destination = Color(0xFFFFB77C), label = Color(0xFFDEE3E6), background = Color(0xFF171C1F)
)

val LocalMapColors = staticCompositionLocalOf { LightMap }

@Composable
fun AccessPathTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(LocalMapColors provides if (dark) DarkMap else LightMap) {
        MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, content = content)
    }
}
