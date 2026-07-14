package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val FuturisticDarkColorScheme = darkColorScheme(
    primary = Cyan500,
    secondary = Amber500,
    tertiary = Indigo500,
    background = BgDark,
    surface = Slate900,
    surfaceVariant = Slate800,
    onPrimary = Slate900,
    onSecondary = Slate900,
    onTertiary = TextLight,
    onBackground = TextLight,
    onSurface = TextLight,
    onSurfaceVariant = Slate300
)

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true, // Force dark theme for futuristic look
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  MaterialTheme(colorScheme = FuturisticDarkColorScheme, typography = Typography, content = content)
}
