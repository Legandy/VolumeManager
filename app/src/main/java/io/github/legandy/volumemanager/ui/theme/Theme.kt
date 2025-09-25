package io.github.legandy.volumemanager.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.legandy.volumemanager.MyApplication
import io.github.legandy.volumemanager.ThemeMode

val VolumeManagerTypography = Typography(
    bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 16.sp),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF1976D2),
    onPrimary = Color.White,
    surface = Color(0xFFFBFCFF),
    onSurface = Color(0xFF191C20),
    surfaceVariant = Color(0xFFDFE2EB),
    onSurfaceVariant = Color(0xFF42474E),
    surfaceContainerHigh = Color(0xFFE9EAF0)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9ECCFF),
    onPrimary = Color(0xFF003258),
    surface = Color(0xFF111318),
    onSurface = Color(0xFFE1E2E5),
    surfaceVariant = Color(0xFF42474E),
    onSurfaceVariant = Color(0xFFC2C7CF),
    surfaceContainerHigh = Color(0xFF282C31)
)

@Composable
private fun getColorScheme(themeMode: ThemeMode): ColorScheme {
    val context = LocalContext.current
    val isSystemInDarkTheme = isSystemInDarkTheme()
    val supportsDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    return when (themeMode) {
        ThemeMode.LIGHT -> if (supportsDynamicColor) dynamicLightColorScheme(context) else LightColors
        ThemeMode.DARK -> if (supportsDynamicColor) dynamicDarkColorScheme(context) else DarkColors
        ThemeMode.SYSTEM -> when {
            supportsDynamicColor && isSystemInDarkTheme -> dynamicDarkColorScheme(context)
            supportsDynamicColor && !isSystemInDarkTheme -> dynamicLightColorScheme(context)
            isSystemInDarkTheme -> DarkColors
            else -> LightColors
        }
    }
}

@Composable
fun VolumeManagerTheme(
    themeMode: ThemeMode? = null,
    content: @Composable () -> Unit
) {
    // Use the global singleton settings, not the instance property
    val settingsThemeMode by MyApplication.settings.themeMode.collectAsState(
        initial = ThemeMode.SYSTEM
    )

    val effectiveThemeMode = themeMode ?: settingsThemeMode
    val colorScheme = getColorScheme(effectiveThemeMode)

    MaterialTheme(
        colorScheme = colorScheme,
        typography = VolumeManagerTypography,
        content = content
    )
}
