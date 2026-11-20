package com.veltusstudio.veltpulse.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.veltusstudio.veltpulse.core.theme.VeltColors

@Composable
fun AnimatedEqualizerIndicator(
    modifier: Modifier = Modifier,
    barColor: Color = VeltColors.NeonViolet,
    accentColor: Color = VeltColors.MagentaGlow
) {
    val infiniteTransition = rememberInfiniteTransition(label = "equalizer_bars")
    val h1 by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(420, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "h1"
    )
    val h2 by infiniteTransition.animateFloat(
        initialValue = 0.90f,
        targetValue = 0.30f,
        animationSpec = infiniteRepeatable(
            animation = tween(580, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "h2"
    )
    val h3 by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(360, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "h3"
    )

    Row(
        modifier = modifier.height(16.dp),
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight(h1)
                .clip(RoundedCornerShape(1.5.dp))
                .background(barColor)
        )
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight(h2)
                .clip(RoundedCornerShape(1.5.dp))
                .background(accentColor)
        )
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight(h3)
                .clip(RoundedCornerShape(1.5.dp))
                .background(barColor)
        )
    }
}

@Composable
fun VeltPulseBrandMark(modifier: Modifier = Modifier, size: Dp = 32.dp) {
    Canvas(modifier = modifier.size(size)) {
        val heights = listOf(0.32f, 0.62f, 0.92f, 0.54f, 0.76f, 0.38f)
        val barWidth = this.size.width / (heights.size * 2f)
        val gap = barWidth
        val totalW = heights.size * barWidth + (heights.size - 1) * gap
        val startX = (this.size.width - totalW) / 2f

        heights.forEachIndexed { idx, frac ->
            val bh = this.size.height * frac
            val x = startX + idx * (barWidth + gap)
            val y = (this.size.height - bh) / 2f
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(x, y),
                size = Size(barWidth, bh),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}

@Composable
fun TurntableGraphic(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val cx = size.width * 0.74f
        val cy = size.height * 0.56f
        val maxR = size.minDimension * 0.68f

        for (i in 1..6) {
            val r = maxR * (i / 6f)
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.03f),
                        Color.White.copy(alpha = 0.14f),
                        Color.White.copy(alpha = 0.03f),
                        Color.White.copy(alpha = 0.10f)
                    ),
                    center = Offset(cx, cy)
                ),
                radius = r,
                center = Offset(cx, cy),
                style = Stroke(width = 1.6f)
            )
        }
        drawCircle(color = Color.White.copy(alpha = 0.25f), radius = maxR * 0.12f, center = Offset(cx, cy))
        drawCircle(color = Color.Black, radius = maxR * 0.04f, center = Offset(cx, cy))

        val pivot = Offset(size.width * 0.90f, size.height * 0.22f)
        val head = Offset(cx - maxR * 0.25f, cy - maxR * 0.15f)
        drawCircle(color = Color.White.copy(alpha = 0.35f), radius = 10f, center = pivot)
        drawLine(color = Color.White.copy(alpha = 0.55f), start = pivot, end = head, strokeWidth = 4f, cap = StrokeCap.Round)
        drawRoundRect(
            color = Color.White.copy(alpha = 0.75f),
            topLeft = Offset(head.x - 10f, head.y - 5f),
            size = Size(20f, 12f),
            cornerRadius = CornerRadius(3f, 3f)
        )
    }
}
