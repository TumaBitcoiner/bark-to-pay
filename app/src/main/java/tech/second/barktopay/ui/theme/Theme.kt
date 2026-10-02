package tech.second.barktopay.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Fixed "bark" palette (amber/brown seed). No dynamic color.
private val LightColors = lightColorScheme(
    primary = Color(0xFF8C4A2F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDBCF),
    onPrimaryContainer = Color(0xFF380D00),
    secondary = Color(0xFF77574C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDBCF),
    onSecondaryContainer = Color(0xFF2C150D),
    tertiary = Color(0xFF6B5E2F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF5E2A7),
    onTertiaryContainer = Color(0xFF221B00),
    background = Color(0xFFFFF8F6),
    onBackground = Color(0xFF221A16),
    surface = Color(0xFFFFF8F6),
    onSurface = Color(0xFF221A16),
    surfaceVariant = Color(0xFFF4DED5),
    onSurfaceVariant = Color(0xFF53433D),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB59B),
    onPrimary = Color(0xFF5C1900),
    primaryContainer = Color(0xFF6F331A),
    onPrimaryContainer = Color(0xFFFFDBCF),
    secondary = Color(0xFFE7BDB0),
    onSecondary = Color(0xFF442A21),
    secondaryContainer = Color(0xFF5D4036),
    onSecondaryContainer = Color(0xFFFFDBCF),
    tertiary = Color(0xFFD8C68D),
    onTertiary = Color(0xFF3A3005),
    tertiaryContainer = Color(0xFF52461A),
    onTertiaryContainer = Color(0xFFF5E2A7),
    background = Color(0xFF1A110D),
    onBackground = Color(0xFFF1DFD8),
    surface = Color(0xFF1A110D),
    onSurface = Color(0xFFF1DFD8),
    surfaceVariant = Color(0xFF53433D),
    onSurfaceVariant = Color(0xFFD8C2BA),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

@Composable
fun BarkToPayTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
