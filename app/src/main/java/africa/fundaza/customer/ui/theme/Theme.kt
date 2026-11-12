package africa.fundaza.customer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Forest800,
    onPrimary = Paper,
    primaryContainer = Forest100,
    onPrimaryContainer = Forest900,
    secondary = Marigold,
    onSecondary = Forest900,
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFFFCEBCB),
    onSecondaryContainer = MarigoldDeep,
    tertiary = Forest500,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = PaperDeep,
    onSurfaceVariant = InkSoft,
    surfaceContainerLowest = Card,
    surfaceContainerLow = Card,
    surfaceContainer = Card,
    surfaceContainerHigh = PaperDeep,
    surfaceContainerHighest = PaperDeep,
    outline = Line,
    outlineVariant = Line,
    error = Clay,
    onError = Paper,
    errorContainer = androidx.compose.ui.graphics.Color(0xFFF6DAD3),
    onErrorContainer = Clay,
)

private val DarkColors = darkColorScheme(
    primary = Forest200,
    onPrimary = Forest900,
    primaryContainer = Forest700,
    onPrimaryContainer = Forest100,
    secondary = Marigold,
    onSecondary = Forest900,
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFF3A2C12),
    onSecondaryContainer = Marigold,
    tertiary = Forest200,
    background = NightBg,
    onBackground = NightInk,
    surface = NightBg,
    onSurface = NightInk,
    surfaceVariant = NightSurface,
    onSurfaceVariant = NightInkSoft,
    surfaceContainerLowest = NightCard,
    surfaceContainerLow = NightCard,
    surfaceContainer = NightCard,
    surfaceContainerHigh = NightSurface,
    surfaceContainerHighest = NightSurface,
    outline = NightLine,
    outlineVariant = NightLine,
    error = androidx.compose.ui.graphics.Color(0xFFF0A08F),
    onError = Forest900,
    errorContainer = androidx.compose.ui.graphics.Color(0xFF3D1E18),
    onErrorContainer = androidx.compose.ui.graphics.Color(0xFFF0A08F),
)

private val FundazaShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun FundazaTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalStatusColors provides if (darkTheme) DarkStatus else LightStatus) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = FundazaTypography,
            shapes = FundazaShapes,
            content = content,
        )
    }
}
