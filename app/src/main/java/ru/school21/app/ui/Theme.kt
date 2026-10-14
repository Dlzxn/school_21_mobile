package ru.school21.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.colorResource
import ru.school21.app.R

/** Тема нативных элементов (прогресс, экран ошибки). Следует системной тёмной теме. */
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val brand = colorResource(R.color.brand)
    val background = colorResource(R.color.system_bar)
    val colorScheme = if (isSystemInDarkTheme()) {
        darkColorScheme(primary = brand, background = background, surface = background)
    } else {
        lightColorScheme(primary = brand, background = background, surface = background)
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
