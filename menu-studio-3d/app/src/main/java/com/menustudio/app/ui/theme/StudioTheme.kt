package com.menustudio.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object StudioColors {
    val StageVoid = Color(0xFF0E0C0A)
    val GlassCharcoal = Color(0xC7100E0C) // rgba(16,14,12,0.78)
    val GlassCharcoalElevated = Color(0xEB181512)
    val GlassSurfaceMuted = Color(0x991C1814)
    val GlassBorder = Color(0x2BF5E9DC)
    val GlassBorderStrong = Color(0x4DF5E9DC)

    val WarmAmber = Color(0xFFF5A623)
    val WarmAmberDeep = Color(0xFFD68610)
    val WarmCream = Color(0xFFF7F2EC)
    val TextSecondary = Color(0xFFC5B9AC)
    val DangerRed = Color(0xFFE5534B)
    val SuccessGreen = Color(0xFF56C271)
}

private val StudioColorScheme = darkColorScheme(
    primary = StudioColors.WarmAmber,
    onPrimary = Color(0xFF17110A),
    secondary = StudioColors.WarmAmberDeep,
    onSecondary = Color(0xFF17110A),
    background = StudioColors.StageVoid,
    onBackground = StudioColors.WarmCream,
    surface = StudioColors.GlassCharcoalElevated,
    onSurface = StudioColors.WarmCream,
    surfaceVariant = StudioColors.GlassSurfaceMuted,
    onSurfaceVariant = StudioColors.TextSecondary,
    error = StudioColors.DangerRed
)

private val StudioTypography = Typography(
    titleMedium = TextStyle(
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 20.sp
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 18.sp
    ),
    labelLarge = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 18.sp
    ),
    labelMedium = TextStyle(
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 16.sp
    )
)

@Composable
fun MenuStudioTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = StudioColorScheme,
        typography = StudioTypography,
        content = content
    )
}
