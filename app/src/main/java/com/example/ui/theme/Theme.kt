package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Teal400,
    onPrimary = Slate950,
    primaryContainer = Teal900,
    onPrimaryContainer = Teal100,
    secondary = Amber400,
    onSecondary = Slate950,
    secondaryContainer = Amber900,
    onSecondaryContainer = Amber100,
    tertiary = Indigo500,
    onTertiary = Color.White,
    tertiaryContainer = Indigo900,
    onTertiaryContainer = Indigo100,
    background = Slate950,
    onBackground = Limestone50,
    surface = Slate900,
    onSurface = Limestone50,
    surfaceVariant = Slate800,
    onSurfaceVariant = Slate400,
    outline = Slate700,
    error = Rose500,
    onError = Color.White,
    errorContainer = Rose900,
    onErrorContainer = Rose100,
)

private val LightColorScheme = lightColorScheme(
    primary = Teal600,
    onPrimary = Color.White,
    primaryContainer = Teal100,
    onPrimaryContainer = Teal900,
    secondary = Amber600,
    onSecondary = Color.White,
    secondaryContainer = Amber100,
    onSecondaryContainer = Amber900,
    tertiary = Indigo500,
    onTertiary = Color.White,
    tertiaryContainer = Indigo100,
    onTertiaryContainer = Indigo900,
    background = Limestone100,
    onBackground = Slate900,
    surface = Color.White,
    onSurface = Slate900,
    surfaceVariant = Slate200,
    onSurfaceVariant = Slate500,
    outline = Slate200,
    error = Rose500,
    onError = Color.White,
    errorContainer = Rose100,
    onErrorContainer = Rose900,
)

@Composable
fun WaymarkTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = WaymarkTypography,
        content = content,
    )
}
