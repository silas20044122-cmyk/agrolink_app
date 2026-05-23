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
    primary = ElegantDarkPrimary,
    onPrimary = ElegantDarkOnPrimary,
    primaryContainer = ElegantDarkPrimaryContainer,
    onPrimaryContainer = ElegantDarkOnPrimaryContainer,
    secondary = ElegantDarkPrimary,
    onSecondary = ElegantDarkOnPrimary,
    secondaryContainer = ElegantDarkSecondaryContainer,
    onSecondaryContainer = ElegantDarkOnSecondaryContainer,
    tertiary = ElegantDarkSecondary,
    onTertiary = ElegantDarkOnSecondary,
    background = ElegantDarkBg,
    onBackground = ElegantDarkText,
    surface = ElegantDarkSurface,
    onSurface = ElegantDarkText,
    outline = ElegantDarkOutline,
    outlineVariant = ElegantDarkBorder,
    error = ElegantDarkError,
    onError = ElegantDarkOnError,
    errorContainer = ElegantDarkErrorContainer,
    onErrorContainer = ElegantDarkOnErrorContainer
)

private val LightColorScheme = DarkColorScheme // Set both to the premium Elegant Dark theme


@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Disable dynamic coloring by default to project our hand-crafted, beautiful corporate agro styling!
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
        typography = Typography,
        content = content
    )
}
