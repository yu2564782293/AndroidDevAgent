package com.example.androiddevagent.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = DevBlue80,
    onPrimary = Color(0xFF062A60),
    primaryContainer = Color(0xFF173D74),
    onPrimaryContainer = Color(0xFFD8E6FF),
    secondary = DevTeal80,
    onSecondary = Color(0xFF00382F),
    secondaryContainer = Color(0xFF0A4F45),
    onSecondaryContainer = Color(0xFFA9F2E2),
    tertiary = DevAmber80,
    onTertiary = Color(0xFF462B00),
    tertiaryContainer = Color(0xFF624000),
    onTertiaryContainer = Color(0xFFFFDEA3),
    background = Color(0xFF101418),
    onBackground = Color(0xFFE2E8EF),
    surface = Color(0xFF161B21),
    onSurface = Color(0xFFE2E8EF),
    surfaceVariant = Color(0xFF27313A),
    onSurfaceVariant = Color(0xFFC3CCD6),
    outline = Color(0xFF8D98A5),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val LightColorScheme = lightColorScheme(
    primary = DevBlue40,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8E6FF),
    onPrimaryContainer = Color(0xFF001B3F),
    secondary = DevTeal40,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFA9F2E2),
    onSecondaryContainer = Color(0xFF00201A),
    tertiary = DevAmber40,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDEA3),
    onTertiaryContainer = Color(0xFF2C1B00),
    background = Color(0xFFFAFCFF),
    onBackground = Color(0xFF171B20),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF171B20),
    surfaceVariant = Color(0xFFE8EEF5),
    onSurfaceVariant = Color(0xFF424A54),
    outline = Color(0xFF737D89),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

@Immutable
data class DevAgentColors(
    val aiResponseContainer: Color,
    val onAiResponseContainer: Color,
    val codeBlockContainer: Color,
    val onCodeBlockContainer: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warningContainer: Color,
    val onWarningContainer: Color
)

private val LightDevAgentColors = DevAgentColors(
    aiResponseContainer = AiResponseLight,
    onAiResponseContainer = Color(0xFF123048),
    codeBlockContainer = CodeBlockLight,
    onCodeBlockContainer = Color(0xFF17212B),
    successContainer = SuccessLight,
    onSuccessContainer = Color(0xFF123322),
    warningContainer = WarningLight,
    onWarningContainer = Color(0xFF3A2B06)
)

private val DarkDevAgentColors = DevAgentColors(
    aiResponseContainer = AiResponseDark,
    onAiResponseContainer = Color(0xFFD9EBFF),
    codeBlockContainer = CodeBlockDark,
    onCodeBlockContainer = Color(0xFFD9E2EC),
    successContainer = SuccessDark,
    onSuccessContainer = Color(0xFFBFE8CF),
    warningContainer = WarningDark,
    onWarningContainer = Color(0xFFFFE2A6)
)

val LocalDevAgentColors = staticCompositionLocalOf { LightDevAgentColors }

object DevAgentTheme {
    val colors: DevAgentColors
        @Composable
        get() = LocalDevAgentColors.current
}

@Composable
fun AndroidDevAgentTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) DarkColorScheme else LightColorScheme
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val devAgentColors = if (darkTheme) DarkDevAgentColors else LightDevAgentColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.surface.toArgb()
            window.navigationBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(
        LocalDevAgentColors provides devAgentColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
