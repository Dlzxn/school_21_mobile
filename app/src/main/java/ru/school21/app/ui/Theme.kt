package ru.school21.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Палитра платформы School 21 (снята с сайта). Сайт всегда тёмный, поэтому и нативные
 * экраны приложения тёмные — чтобы не было «белой вспышки» между сайтом и настройками.
 */
object S21Colors {
    val Background = Color(0xFF1D2633)
    val Card = Color(0xFF2C384A)
    val CardRaised = Color(0xFF34425A)
    val Divider = Color(0xFF25303F)
    val Outline = Color(0xFF3E4C63)
    val Cyan = Color(0xFF36C5F0)
    val Teal = Color(0xFF3DDABF)
    val Green = Color(0xFF44EB99)
    val Logo = Color(0xFF27BDCB)
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFF9AA6B8)
    val Error = Color(0xFFFF6B7A)

    /** Фирменный градиент — как полоска уровня на сайте. */
    val Accent = Brush.linearGradient(listOf(Cyan, Teal, Green))
}

private val colorScheme = darkColorScheme(
    primary = S21Colors.Teal,
    onPrimary = Color(0xFF07211D),
    primaryContainer = Color(0xFF1F4A4A),
    onPrimaryContainer = Color(0xFFBFF6EC),
    secondary = S21Colors.Cyan,
    onSecondary = Color(0xFF00202B),
    tertiary = S21Colors.Green,
    background = S21Colors.Background,
    onBackground = S21Colors.TextPrimary,
    surface = S21Colors.Background,
    onSurface = S21Colors.TextPrimary,
    surfaceVariant = S21Colors.Card,
    onSurfaceVariant = S21Colors.TextSecondary,
    surfaceContainerLowest = S21Colors.Background,
    surfaceContainerLow = S21Colors.Card,
    surfaceContainer = S21Colors.Card,
    surfaceContainerHigh = S21Colors.CardRaised,
    surfaceContainerHighest = S21Colors.CardRaised,
    outline = S21Colors.Outline,
    outlineVariant = S21Colors.Divider,
    error = S21Colors.Error,
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colorScheme, content = content)
}
