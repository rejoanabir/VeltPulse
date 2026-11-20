package com.veltusstudio.veltpulse.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veltusstudio.veltpulse.R
import com.veltusstudio.veltpulse.core.theme.VeltBackdrop
import com.veltusstudio.veltpulse.core.theme.VeltColors
import kotlinx.coroutines.launch

@Composable
fun SplashScreen() {
    val scale = remember { Animatable(0.78f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch {
            scale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = 0.72f,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
        launch {
            alpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(500, easing = FastOutSlowInEasing)
            )
        }
    }

    VeltBackdrop {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .graphicsLayer {
                        scaleX = scale.value
                        scaleY = scale.value
                        this.alpha = alpha.value
                    },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(id = R.drawable.veltpulse_logo),
                    contentDescription = "VeltPulse Logo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(120.dp).clip(RoundedCornerShape(28.dp))
                )
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "VeltPulse",
                    style = MaterialTheme.typography.displaySmall,
                    color = VeltColors.SilverPrimary,
                    fontSize = 30.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Feel the Pulse",
                    color = VeltColors.SilverMuted,
                    fontSize = 13.sp,
                    letterSpacing = 1.4.sp
                )
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 36.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(modifier = Modifier.width(24.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(VeltColors.SilverPrimary))
                Box(modifier = Modifier.width(8.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(VeltColors.SilverMuted.copy(alpha = 0.4f)))
            }
        }
    }
}
