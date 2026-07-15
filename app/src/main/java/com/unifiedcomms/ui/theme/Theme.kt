package com.unifiedcomms.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlin.math.abs

// Color palette - Colorful Material 3
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF6750A4),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF21005D),
    secondary = Color(0xFF625B71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE8DEF8),
    onSecondaryContainer = Color(0xFF1D192B),
    tertiary = Color(0xFF7D5260),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD8E4),
    onTertiaryContainer = Color(0xFF31111D),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    background = Color(0xFFFFFBFE),
    onBackground = Color(0xFF1C1B1F),
    surface = Color(0xFFFFFBFE),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF79747E),
    outlineVariant = Color(0xFFCAC4D0),
    inverseSurface = Color(0xFF313033),
    inverseOnSurface = Color(0xFFF4EFF4),
    inversePrimary = Color(0xFFD0BCFF)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFFCCC2DC),
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = Color(0xFFEFB8C8),
    onTertiary = Color(0xFF492532),
    tertiaryContainer = Color(0xFF633B48),
    onTertiaryContainer = Color(0xFFFFD8E4),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    background = Color(0xFF1C1B1F),
    onBackground = Color(0xFFE6E1E5),
    surface = Color(0xFF1C1B1F),
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF49454F),
    inverseSurface = Color(0xFFE6E1E5),
    inverseOnSurface = Color(0xFF313033),
    inversePrimary = Color(0xFF6750A4)
)

// Account-specific color schemes for colorful UI
object AccountColors {
    val palette = listOf(
        AccountColor(Color(0xFFE57373), Color(0xFFFFFFFF), "Red"),
        AccountColor(Color(0xFFF06292), Color(0xFFFFFFFF), "Pink"),
        AccountColor(Color(0xFFBA68C8), Color(0xFFFFFFFF), "Purple"),
        AccountColor(Color(0xFF9575CD), Color(0xFFFFFFFF), "Deep Purple"),
        AccountColor(Color(0xFF7986CB), Color(0xFFFFFFFF), "Indigo"),
        AccountColor(Color(0xFF64B5F6), Color(0xFFFFFFFF), "Blue"),
        AccountColor(Color(0xFF4FC3F7), Color(0xFF000000), "Light Blue"),
        AccountColor(Color(0xFF4DD0E1), Color(0xFF000000), "Cyan"),
        AccountColor(Color(0xFF4DB6AC), Color(0xFFFFFFFF), "Teal"),
        AccountColor(Color(0xFF81C784), Color(0xFFFFFFFF), "Green"),
        AccountColor(Color(0xFFAED581), Color(0xFF000000), "Light Green"),
        AccountColor(Color(0xFFDCE775), Color(0xFF000000), "Lime"),
        AccountColor(Color(0xFFFFF176), Color(0xFF000000), "Yellow"),
        AccountColor(Color(0xFFFFD54F), Color(0xFF000000), "Amber"),
        AccountColor(Color(0xFFFFB74D), Color(0xFF000000), "Orange"),
        AccountColor(Color(0xFFFF8A65), Color(0xFFFFFFFF), "Deep Orange"),
        AccountColor(Color(0xFFA1887F), Color(0xFFFFFFFF), "Brown"),
        AccountColor(Color(0xFF90A4AE), Color(0xFFFFFFFF), "Grey"),
        AccountColor(Color(0xFF78909C), Color(0xFFFFFFFF), "Blue Grey")
    )

    fun getColor(index: Int): AccountColor = palette[index % palette.size]
    fun getColorForAccount(accountId: String): AccountColor {
        val hash = abs(accountId.hashCode())
        return palette[hash % palette.size]
    }
}

data class AccountColor(
    val container: Color,
    val onContainer: Color,
    val name: String
)

var currentColorScheme by mutableStateOf(LightColorScheme)
    private set

@Composable
fun UnifiedCommsTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    currentColorScheme = colorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        shapes = ShapesDefault,
        content = content
    )
}

val ShapesDefault = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)