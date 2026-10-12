package ai.shadowvision.cleanup

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ShadowColors = darkColorScheme(
    primary = Color(0xFF79DCC6),
    onPrimary = Color(0xFF062B24),
    secondary = Color(0xFFAAB9FF),
    onSecondary = Color(0xFF18234D),
    background = Color(0xFF0B1220),
    onBackground = Color(0xFFF2F5FA),
    surface = Color(0xFF131D2D),
    onSurface = Color(0xFFF2F5FA),
    surfaceVariant = Color(0xFF202B3D),
    onSurfaceVariant = Color(0xFFC1CAD8),
    error = Color(0xFFFF9388)
)

@Composable
fun ShadowVisionTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ShadowColors,
        typography = Typography(),
        content = content
    )
}
