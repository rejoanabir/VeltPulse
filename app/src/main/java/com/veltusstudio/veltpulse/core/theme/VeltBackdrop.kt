package com.veltusstudio.veltpulse.core.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke

@Composable
fun VeltBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(VeltColors.DeepBg)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Top ambient neon violet glow wave (matches 16475.png header vibe)
            val wave1 = Path().apply {
                moveTo(-w * 0.15f, h * 0.12f)
                cubicTo(w * 0.20f, h * 0.02f, w * 0.50f, h * 0.24f, w * 1.15f, h * 0.06f)
                lineTo(w * 1.15f, h * 0.20f)
                cubicTo(w * 0.60f, h * 0.32f, w * 0.20f, h * 0.14f, -w * 0.15f, h * 0.24f)
                close()
            }
            drawPath(
                path = wave1,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFF7C3AED).copy(alpha = 0.06f),
                        Color(0xFFA855F7).copy(alpha = 0.14f),
                        Color(0xFFD946EF).copy(alpha = 0.08f)
                    )
                )
            )
            drawPath(
                path = wave1,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFFA855F7).copy(alpha = 0.18f),
                        Color(0xFFC084FC).copy(alpha = 0.32f),
                        Color(0xFF7C3AED).copy(alpha = 0.12f)
                    )
                ),
                style = Stroke(width = 1.2f)
            )

            // Bottom ambient violet aura
            val wave2 = Path().apply {
                moveTo(-w * 0.2f, h * 0.82f)
                cubicTo(w * 0.25f, h * 0.74f, w * 0.65f, h * 0.92f, w * 1.2f, h * 0.78f)
                lineTo(w * 1.2f, h * 0.98f)
                cubicTo(w * 0.60f, h * 1.04f, w * 0.25f, h * 0.88f, -w * 0.2f, h * 0.98f)
                close()
            }
            drawPath(
                path = wave2,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFF4C1D95).copy(alpha = 0.04f),
                        Color(0xFF7C3AED).copy(alpha = 0.10f),
                        Color(0xFF9333EA).copy(alpha = 0.04f)
                    )
                )
            )
        }
        content()
    }
}
