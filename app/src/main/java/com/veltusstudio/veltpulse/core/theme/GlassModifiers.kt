package com.veltusstudio.veltpulse.core.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.liquidGlass(
    cornerRadius: Dp = 24.dp,
    fillAlpha: Float = 0.08f,
    borderAlpha: Float = 0.18f,
    enableShader: Boolean = false
): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    return this
        .clip(shape)
        .background(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF22153D).copy(alpha = 0.85f),
                    Color(0xFF120B22).copy(alpha = 0.95f)
                )
            )
        )
        .border(
            width = 1.dp,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFA855F7).copy(alpha = borderAlpha + 0.1f),
                    Color.White.copy(alpha = 0.06f)
                )
            ),
            shape = shape
        )
}

fun Modifier.velvetCard(
    cornerRadius: Dp = 22.dp,
    accentGlow: Boolean = false
): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    val bgColors = if (accentGlow) {
        listOf(Color(0xFF2E1754), Color(0xFF160C2A))
    } else {
        listOf(Color(0xFF1A122C), Color(0xFF0F0A1C))
    }
    val borderColors = if (accentGlow) {
        listOf(Color(0xFFA855F7).copy(alpha = 0.5f), Color(0xFF7C3AED).copy(alpha = 0.2f))
    } else {
        listOf(Color.White.copy(alpha = 0.12f), Color(0xFF7C3AED).copy(alpha = 0.08f))
    }
    return this
        .clip(shape)
        .background(brush = Brush.verticalGradient(bgColors))
        .border(width = 1.dp, brush = Brush.verticalGradient(borderColors), shape = shape)
}

fun Modifier.glossyGlassDeck(
    cornerRadius: Dp = 28.dp,
    fillAlpha: Float = 0.12f,
    borderAlpha: Float = 0.28f
): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    return this
        .clip(shape)
        .background(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF251742).copy(alpha = 0.92f),
                    Color(0xFF10091E).copy(alpha = 0.98f)
                )
            )
        )
        .border(
            width = 1.dp,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFA855F7).copy(alpha = borderAlpha),
                    Color.White.copy(alpha = 0.10f)
                )
            ),
            shape = shape
        )
}

fun Modifier.glossyPill(
    cornerRadius: Dp = 50.dp,
    active: Boolean = false
): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    val fillColors = if (active) {
        listOf(Color(0xFF3B1D6E), Color(0xFF251248))
    } else {
        listOf(Color(0xFF1A122C).copy(alpha = 0.85f), Color(0xFF100A1D).copy(alpha = 0.85f))
    }
    val borderColors = if (active) {
        listOf(Color(0xFFA855F7).copy(alpha = 0.70f), Color(0xFF7C3AED).copy(alpha = 0.35f))
    } else {
        listOf(Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.04f))
    }
    return this
        .clip(shape)
        .background(brush = Brush.verticalGradient(fillColors))
        .border(width = 1.dp, brush = Brush.verticalGradient(borderColors), shape = shape)
}
