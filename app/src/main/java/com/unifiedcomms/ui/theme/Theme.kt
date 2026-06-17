package com.unifiedcomms.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

// Color palette - Colorful Material 3
private val LightColorScheme = lightColorScheme(
    primary = 0xFF6750A4,
    onPrimary = 0xFFFFFFFF,
    primaryContainer = 0xFFEADDFF,
    onPrimaryContainer = 0xFF21005D,
    secondary = 0xFF625B71,
    onSecondary = 0xFFFFFFFF,
    secondaryContainer = 0xFFE8DEF8,
    onSecondaryContainer = 0xFF1D192B,
    tertiary = 0xFF7D5260,
    onTertiary = 0xFFFFFFFF,
    tertiaryContainer = 0xFFFFD8E4,
    onTertiaryContainer = 0xFF31111D,
    error = 0xFFB3261E,
    onError = 0xFFFFFFFF,
    errorContainer = 0xFFF9DEDC,
    onErrorContainer = 0xFF410E0B,
    background = 0xFFFFFBFE,
    onBackground = 0xFF1C1B1F,
    surface = 0xFFFFFBFE,
    onSurface = 0xFF1C1B1F,
    surfaceVariant = 0xFFE7E0EC,
    onSurfaceVariant = 0xFF49454F,
    outline = 0xFF79747E,
    outlineVariant = 0xFFCAC4D0,
    shadow = 0xFF000000,
    scrim = 0xFF000000,
    inverseSurface = 0xFF313033,
    inverseOnSurface = 0xFFF4EFF4,
    inversePrimary = 0xFFD0BCFF
)

private val DarkColorScheme = darkColorScheme(
    primary = 0xFFD0BCFF,
    onPrimary = 0xFF381E72,
    primaryContainer = 0xFF4F378B,
    onPrimaryContainer = 0xFFEADDFF,
    secondary = 0xFFCCC2DC,
    onSecondary = 0xFF332D41,
    secondaryContainer = 0xFF4A4458,
    onSecondaryContainer = 0xFFE8DEF8,
    tertiary = 0xFFEFB8C8,
    onTertiary = 0xFF492532,
    tertiaryContainer = 0xFF633B48,
    onTertiaryContainer = 0xFFFFD8E4,
    error = 0xFFF2B8B5,
    onError = 0xFF601410,
    errorContainer = 0xFF8C1D18,
    onErrorContainer = 0xFFF9DEDC,
    background = 0xFF1C1B1F,
    onBackground = 0xFFE6E1E5,
    surface = 0xFF1C1B1F,
    onSurface = 0xFFE6E1E5,
    surfaceVariant = 0xFF49454F,
    onSurfaceVariant = 0xFFCAC4D0,
    outline = 0xFF938F99,
    outlineVariant = 0xFF49454F,
    shadow = 0xFF000000,
    scrim = 0xFF000000,
    inverseSurface = 0xFFE6E1E5,
    inverseOnSurface = 0xFF313033,
    inversePrimary = 0xFF6750A4
)

// Account-specific color schemes for colorful UI
object AccountColors {
    val palette = listOf(
        AccountColor(0xFFE57373, 0xFFFFFFFF, "Red"),       // Material Red 300
        AccountColor(0xFFF06292, 0xFFFFFFFF, "Pink"),      // Material Pink 300
        AccountColor(0xFFBA68C8, 0xFFFFFFFF, "Purple"),    // Material Purple 300
        AccountColor(0xFF9575CD, 0xFFFFFFFF, "Deep Purple"), // Material Deep Purple 300
        AccountColor(0xFF7986CB, 0xFFFFFFFF, "Indigo"),     // Material Indigo 300
        AccountColor(0xFF64B5F6, 0xFFFFFFFF, "Blue"),       // Material Blue 300
        AccountColor(0xFF4FC3F7, 0xFF000000, "Light Blue"), // Material Light Blue 300
        AccountColor(0xFF4DD0E1, 0xFF000000, "Cyan"),       // Material Cyan 300
        AccountColor(0xFF4DB6AC, 0xFFFFFFFF, "Teal"),       // Material Teal 300
        AccountColor(0xFF81C784, 0xFFFFFFFF, "Green"),      // Material Green 300
        AccountColor(0xFFAED581, 0xFF000000, "Light Green"), // Material Light Green 300
        AccountColor(0xFFDCE775, 0xFF000000, "Lime"),       // Material Lime 300
        AccountColor(0xFFFFF176, 0xFF000000, "Yellow"),     // Material Yellow 300
        AccountColor(0xFFFFD54F, 0xFF000000, "Amber"),      // Material Amber 300
        AccountColor(0xFFFFB74D, 0xFF000000, "Orange"),     // Material Orange 300
        AccountColor(0xFFFF8A65, 0xFFFFFFFF, "Deep Orange"), // Material Deep Orange 300
        AccountColor(0xFFA1887F, 0xFFFFFFFF, "Brown"),      // Material Brown 300
        AccountColor(0xFF90A4AE, 0xFFFFFFFF, "Grey"),       // Material Grey 300
        AccountColor(0xFF78909C, 0xFFFFFFFF, "Blue Grey")   // Material Blue Grey 300
    )

    fun getColor(index: Int): AccountColor = palette[index % palette.size]
    fun getColorForAccount(accountId: String): AccountColor {
        val hash = accountId.hashCode().absoluteValue
        return palette[hash % palette.size]
    }
}

data class AccountColor(
    val container: Int,
    val onContainer: Int,
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
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}

object Typography {
    // Material 3 typography scale
}

object Shapes {
    // Material 3 shapes
}