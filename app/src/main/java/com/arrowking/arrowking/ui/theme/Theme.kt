package com.arrowking.arrowking.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.arrowking.arrowking.data.GameThemePalette
import com.arrowking.arrowking.data.ThemeCatalog

val LocalGameTheme = staticCompositionLocalOf { ThemeCatalog.classicParchment }

private val ArrowKingShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun ArrowKingTheme(
    palette: GameThemePalette = ThemeCatalog.classicParchment,
    content: @Composable () -> Unit
) {
    val colorScheme = if (palette.isDark) {
        darkColorScheme(
            primary = palette.accentGold,
            onPrimary = Color(0xFF1A140B),
            secondary = palette.arrowColors.getOrElse(1) { WaterDropBlue },
            background = palette.paperBg,
            onBackground = palette.primaryText,
            surface = palette.paperSurface,
            onSurface = palette.primaryText,
            surfaceVariant = palette.boardWellBg,
            outline = palette.dividerColor
        )
    } else {
        lightColorScheme(
            primary = palette.accentGold,
            onPrimary = Color.White,
            secondary = palette.arrowColors.getOrElse(1) { WaterDropBlue },
            background = palette.paperBg,
            onBackground = palette.primaryText,
            surface = palette.paperSurface,
            onSurface = palette.primaryText,
            surfaceVariant = palette.boardWellBg,
            outline = palette.dividerColor
        )
    }

    CompositionLocalProvider(LocalGameTheme provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = ArrowKingTypography,
            shapes = ArrowKingShapes,
            content = content
        )
    }
}
