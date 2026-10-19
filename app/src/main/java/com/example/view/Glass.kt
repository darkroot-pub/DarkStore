package com.example.view

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Glass-morphism helpers shared by the whole app.
 *
 * Real glass needs something colourful behind it, so the root of the app draws a soft
 * [ambientGlow] and every glass surface is a translucent fill + a light-catching edge.
 */

/** Soft accent-coloured light blobs painted behind the whole UI (cheap: drawn once, not per frame). */
fun Modifier.ambientGlow(isDark: Boolean, isAmoled: Boolean, accent: Color): Modifier = this.drawBehind {
    val w = size.width
    val h = size.height
    val a1 = if (isDark) (if (isAmoled) 0.12f else 0.22f) else 0.28f
    val a2 = if (isDark) (if (isAmoled) 0.09f else 0.17f) else 0.20f
    drawRect(Brush.radialGradient(listOf(accent.copy(alpha = a1), Color.Transparent), center = Offset(w * 0.04f, h * 0.06f), radius = w * 0.95f))
    drawRect(Brush.radialGradient(listOf(Color(0xFF6366F1).copy(alpha = a2), Color.Transparent), center = Offset(w * 0.98f, h * 0.52f), radius = w * 0.9f))
    drawRect(Brush.radialGradient(listOf(Color(0xFF06B6D4).copy(alpha = a2 * 0.8f), Color.Transparent), center = Offset(w * 0.15f, h * 0.99f), radius = w * 0.85f))
}

// PERF: these brushes used to be rebuilt (3 Brush objects + lists + colour copies) on EVERY recomposition of EVERY
// glass tile - pure garbage-collector pressure while scrolling. Brushes are immutable, so build them once.
private val GLASS_FILL_DARK = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.13f), Color.White.copy(alpha = 0.05f)))
private val GLASS_FILL_LIGHT = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.66f), Color.White.copy(alpha = 0.40f)))
private val GLASS_SPECULAR_DARK = Brush.linearGradient(
    colors = listOf(Color.White.copy(alpha = 0.16f), Color.Transparent), start = Offset(0f, 0f), end = Offset(520f, 360f)
)
private val GLASS_SPECULAR_LIGHT = Brush.linearGradient(
    colors = listOf(Color.White.copy(alpha = 0.70f), Color.Transparent), start = Offset(0f, 0f), end = Offset(520f, 360f)
)
private val GLASS_EDGE_DARK = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.34f), Color.White.copy(alpha = 0.05f)))
private val GLASS_EDGE_LIGHT = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.98f), Color(0xFF8FA0B3).copy(alpha = 0.32f)))
private val GLASS_SHADOW_AMBIENT = Color.Black.copy(alpha = 0.10f)
private val GLASS_SHADOW_SPOT = Color.Black.copy(alpha = 0.16f)

/**
 * "Liquid glass" panel: soft shadow, translucent frosted fill, a specular highlight and a bright edge.
 * Pass `elevation = 0.dp` for tiles that repeat in scrolling lists: a shadow per tile is the most expensive part.
 */
fun Modifier.glass(isDark: Boolean, shape: Shape, elevation: Dp = 6.dp): Modifier = this
    // clip = false: the clip below does the clipping once (shadow() clips by default = a second, wasted clip layer)
    .then(
        if (elevation > 0.dp) Modifier.shadow(elevation, shape, clip = false, ambientColor = GLASS_SHADOW_AMBIENT, spotColor = GLASS_SHADOW_SPOT)
        else Modifier
    )
    .clip(shape)
    .background(if (isDark) GLASS_FILL_DARK else GLASS_FILL_LIGHT)
    // specular highlight sweeping from the top-left corner - what makes it read as liquid
    .background(if (isDark) GLASS_SPECULAR_DARK else GLASS_SPECULAR_LIGHT)
    .border(1.dp, if (isDark) GLASS_EDGE_DARK else GLASS_EDGE_LIGHT, shape)

/** Light-catching edge for use on any shape (details tiles, thumbnails...). */
fun glassEdge(isDark: Boolean): Brush = if (isDark) GLASS_EDGE_STRONG_DARK else GLASS_EDGE_STRONG_LIGHT
private val GLASS_EDGE_STRONG_DARK = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.30f), Color.White.copy(alpha = 0.04f)))
private val GLASS_EDGE_STRONG_LIGHT = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.95f), Color(0xFF8FA0B3).copy(alpha = 0.28f)))

/** Subtle white sheen laid over a panel to make it read as frosted. */
fun glassSheen(isDark: Boolean): Brush = if (isDark) GLASS_SHEEN_DARK else GLASS_SHEEN_LIGHT
private val GLASS_SHEEN_DARK = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.03f)))
private val GLASS_SHEEN_LIGHT = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.80f), Color.White.copy(alpha = 0.50f)))
