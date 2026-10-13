package com.example.view

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

/** Admin-selectable look for the critical-alert banner (color + font family). */
object BannerStyles {
    val colors = listOf(
        "Red" to "#C62828", "Orange" to "#E65100", "Amber" to "#B45309", "Green" to "#2E7D32",
        "Blue" to "#1565C0", "Purple" to "#6A1B9A", "Pink" to "#AD1457", "Slate" to "#37474F"
    )
    val fonts = listOf(
        "default" to "Default", "sans" to "Sans", "serif" to "Serif", "mono" to "Mono", "cursive" to "Script"
    )

    fun color(hex: String): Color = try {
        if (hex.isBlank()) Color(0xFFC62828) else Color(android.graphics.Color.parseColor(hex))
    } catch (e: Exception) { Color(0xFFC62828) }

    fun font(key: String): FontFamily = when (key) {
        "sans" -> FontFamily.SansSerif
        "serif" -> FontFamily.Serif
        "mono" -> FontFamily.Monospace
        "cursive" -> FontFamily.Cursive
        else -> FontFamily.Default
    }
}
