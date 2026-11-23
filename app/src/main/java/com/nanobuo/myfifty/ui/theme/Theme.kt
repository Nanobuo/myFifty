package com.nanobuo.myfifty.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.nanobuo.myfifty.data.ThemeMode

// Palette ricavata dal logo
private val Green = Color(0xFF4E9E6E)
private val GreenDeep = Color(0xFF2F7A50)
private val GreenLight = Color(0xFF8FCB9B)
private val Gold = Color(0xFFE6B23C)
private val Cream = Color(0xFFFBF8F1)
private val Beige = Color(0xFFF1E8D6)
private val Ink = Color(0xFF1B2620)
private val Night = Color(0xFF13201A)
private val Forest = Color(0xFF1F3A2C)

private val LightColors = lightColorScheme(
    primary = GreenDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEBD5),
    onPrimaryContainer = Color(0xFF0B2E19),
    secondary = Color(0xFF7A5A12),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF7DFA4),
    onSecondaryContainer = Color(0xFF2A1D00),
    tertiary = Green,
    onTertiary = Color.White,
    background = Cream,
    onBackground = Ink,
    surface = Cream,
    onSurface = Ink,
    surfaceVariant = Beige,
    onSurfaceVariant = Color(0xFF4A5249),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7F2E7),
    surfaceContainer = Color(0xFFF4EDDF),
    surfaceContainerHigh = Beige,
    surfaceContainerHighest = Color(0xFFEBE1CC),
    outline = Color(0xFF7D857B),
    outlineVariant = Color(0xFFD9D0BE),
    error = Color(0xFFB3261E),
)

private val DarkColors = darkColorScheme(
    primary = GreenLight,
    onPrimary = Color(0xFF0A2A16),
    primaryContainer = Color(0xFF2E6B47),
    onPrimaryContainer = Color(0xFFD5F2DC),
    secondary = Gold,
    onSecondary = Color(0xFF2A1D00),
    secondaryContainer = Color(0xFF5C4410),
    onSecondaryContainer = Color(0xFFFBE3AD),
    tertiary = Green,
    onTertiary = Color.White,
    background = Night,
    onBackground = Color(0xFFE3EAE3),
    surface = Night,
    onSurface = Color(0xFFE3EAE3),
    surfaceVariant = Forest,
    onSurfaceVariant = Color(0xFFB9C7BC),
    surfaceContainerLowest = Color(0xFF0E1813),
    surfaceContainerLow = Color(0xFF172821),
    surfaceContainer = Color(0xFF1A2D24),
    surfaceContainerHigh = Forest,
    surfaceContainerHighest = Color(0xFF284636),
    outline = Color(0xFF86968A),
    outlineVariant = Color(0xFF34503F),
    error = Color(0xFFF2B8B5),
)

/** Colori semantici per crediti / debiti. */
data class MoneyColors(val positive: Color, val negative: Color)

val LocalMoneyColors = staticCompositionLocalOf { MoneyColors(GreenDeep, Color(0xFFB3261E)) }
val LocalIsDark = staticCompositionLocalOf { false }

@Composable
fun isAppDark(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun MyFiftyTheme(dark: Boolean, content: @Composable () -> Unit) {
    val base = Typography()
    val typography = base.copy(
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    )
    val money = if (dark) MoneyColors(GreenLight, Color(0xFFFF9C8F)) else MoneyColors(GreenDeep, Color(0xFFB3261E))
    androidx.compose.runtime.CompositionLocalProvider(LocalMoneyColors provides money, LocalIsDark provides dark) {
        MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, typography = typography, content = content)
    }
}
