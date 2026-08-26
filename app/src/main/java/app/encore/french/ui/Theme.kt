package app.encore.french.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF3F6F8F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5EAF5),
    onPrimaryContainer = Color(0xFF163446),
    secondary = Color(0xFF536F7F),
    background = Color(0xFFF7FAFC),
    onBackground = Color(0xFF182126),
    surface = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFF0F5F8),
    surfaceContainerHigh = Color(0xFFE7EFF3),
    onSurface = Color(0xFF182126),
    onSurfaceVariant = Color(0xFF5B6870),
    outline = Color(0xFF8B9AA3),
    outlineVariant = Color(0xFFD9E2E7)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF91C5E2),
    onPrimary = Color(0xFF0B354A),
    primaryContainer = Color(0xFF244E65),
    onPrimaryContainer = Color(0xFFC7E8F8),
    secondary = Color(0xFFAFC9D7),
    background = Color(0xFF10171C),
    onBackground = Color(0xFFE8F0F4),
    surface = Color(0xFF182229),
    surfaceContainer = Color(0xFF1D2931),
    surfaceContainerHigh = Color(0xFF26343D),
    onSurface = Color(0xFFE8F0F4),
    onSurfaceVariant = Color(0xFFB5C3CB),
    outline = Color(0xFF82939D),
    outlineVariant = Color(0xFF34454F)
)

@Composable
fun EncoreTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}
