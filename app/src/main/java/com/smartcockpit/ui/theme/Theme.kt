package com.smartcockpit.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = AccentColor,
    secondary = SecondaryText,
    tertiary = AccentColor,
    background = Background,
    surface = Surface,
    onPrimary = Surface,
    onSecondary = PrimaryText,
    onTertiary = Surface,
    onBackground = PrimaryText,
    onSurface = PrimaryText,
)

@Composable
fun HanemTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
