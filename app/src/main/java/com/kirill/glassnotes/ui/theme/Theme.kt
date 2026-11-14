package com.kirill.glassnotes.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Цветовая схема «стекла», зависящая от темы и прозрачности карточек. */
data class GlassColors(
    val background: Color,
    val card: Color,
    val cardBorder: Color,
    val innerBlock: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val onTextPrimary: Color,
    val divider: Color,
    val ripple: Color
)

fun darkGlassColors(glassAlpha: Float): GlassColors = GlassColors(
    background = DarkBackground.copy(alpha = 0.35f),
    card = DarkCard.copy(alpha = glassAlpha),
    cardBorder = Color.White.copy(alpha = 0.14f),
    innerBlock = DarkInnerBlock.copy(alpha = (glassAlpha + 0.18f).coerceAtMost(0.92f)),
    textPrimary = DarkTextPrimary,
    textSecondary = DarkTextSecondary,
    onTextPrimary = DarkBackground,
    divider = Color.White.copy(alpha = 0.10f),
    ripple = Color.White.copy(alpha = 0.12f)
)

fun lightGlassColors(glassAlpha: Float): GlassColors = GlassColors(
    background = LightBackground.copy(alpha = 0.25f),
    card = LightCard.copy(alpha = glassAlpha),
    cardBorder = Color.Black.copy(alpha = 0.10f),
    innerBlock = LightInnerBlock.copy(alpha = (glassAlpha + 0.15f).coerceAtMost(0.90f)),
    textPrimary = LightTextPrimary,
    textSecondary = LightTextSecondary,
    onTextPrimary = Color.White,
    divider = Color.Black.copy(alpha = 0.08f),
    ripple = Color.Black.copy(alpha = 0.10f)
)

val LocalGlassColors = staticCompositionLocalOf { darkGlassColors(0.7f) }

@Composable
fun GlassNotesTheme(
    isDarkGlass: Boolean = true,
    glassAlpha: Float = 0.7f,
    content: @Composable () -> Unit
) {
    val colors = if (isDarkGlass) darkGlassColors(glassAlpha) else lightGlassColors(glassAlpha)
    val colorScheme = if (isDarkGlass) {
        darkColorScheme(
            primary = colors.textPrimary,
            onPrimary = DarkBackground,
            secondary = colors.textSecondary,
            onSecondary = DarkBackground,
            background = Color.Transparent,
            onBackground = colors.textPrimary,
            surface = colors.card,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.innerBlock,
            onSurfaceVariant = colors.textSecondary,
            primaryContainer = colors.innerBlock,
            onPrimaryContainer = colors.textPrimary,
            outline = colors.cardBorder
        )
    } else {
        lightColorScheme(
            primary = colors.textPrimary,
            onPrimary = Color.White,
            secondary = colors.textSecondary,
            onSecondary = Color.White,
            background = Color.Transparent,
            onBackground = colors.textPrimary,
            surface = colors.card,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.innerBlock,
            onSurfaceVariant = colors.textSecondary,
            primaryContainer = colors.innerBlock,
            onPrimaryContainer = colors.textPrimary,
            outline = colors.cardBorder
        )
    }

    CompositionLocalProvider(LocalGlassColors provides colors) {
        MaterialTheme(colorScheme = colorScheme, content = content)
    }
}