package com.wordrush.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF4A6CF7),
    secondary = Color(0xFF7C4DFF),
    tertiary = Color(0xFF00BFA5),
    background = Color(0xFFF7F8FC),
    surface = Color(0xFFFFFFFF),
)

@Composable
fun WordRushTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LightColors, content = content)
}
