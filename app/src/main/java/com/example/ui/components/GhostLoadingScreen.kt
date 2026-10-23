package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.GhostSky
import com.example.ui.theme.GhostTeal

@Composable
fun GhostLoadingScreen() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    
    val rotation by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rotation"
    )

    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(240.dp),
            contentAlignment = Alignment.Center
        ) {
            // Pulsing background glows
            Canvas(modifier = Modifier.size(200.dp * scale)) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            GhostSky.copy(alpha = alpha * 0.3f),
                            GhostTeal.copy(alpha = alpha * 0.1f),
                            Color.Transparent
                        )
                    )
                )
            }
            
            // Stylized Ghost Shape with float animation
            Box(modifier = Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
                rotationZ = rotation
            }) {
                GhostIcon(
                    modifier = Modifier.size(110.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        
        Spacer(modifier = Modifier.height(48.dp))
        
        Text(
            text = "GHOSTGRAM",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Black,
                letterSpacing = 10.sp
            ),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        LinearProgressIndicator(
            modifier = Modifier.width(140.dp).height(2.dp).clip(CircleShape),
            color = GhostTeal,
            trackColor = GhostTeal.copy(alpha = 0.1f)
        )
        
    }
}

@Composable
private fun GhostIcon(modifier: Modifier, color: Color) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        
        val ghostPath = Path().apply {
            moveTo(width * 0.5f, height * 0.1f)
            // Head
            cubicTo(
                width * 0.2f, height * 0.1f,
                width * 0.1f, height * 0.4f,
                width * 0.1f, height * 0.6f
            )
            // Left side
            lineTo(width * 0.1f, height * 0.85f)
            // Bottom waves
            lineTo(width * 0.25f, height * 0.75f)
            lineTo(width * 0.4f, height * 0.85f)
            lineTo(width * 0.55f, height * 0.75f)
            lineTo(width * 0.7f, height * 0.85f)
            lineTo(width * 0.85f, height * 0.75f)
            lineTo(width * 0.9f, height * 0.85f)
            // Right side
            lineTo(width * 0.9f, height * 0.6f)
            // Top back
            cubicTo(
                width * 0.9f, height * 0.4f,
                width * 0.8f, height * 0.1f,
                width * 0.5f, height * 0.1f
            )
            close()
        }
        
        drawPath(
            path = ghostPath,
            color = color,
            style = Stroke(width = 4.dp.toPx())
        )
        
        // Eyes
        drawCircle(
            color = color,
            radius = 4.dp.toPx(),
            center = androidx.compose.ui.geometry.Offset(width * 0.35f, height * 0.4f)
        )
        drawCircle(
            color = color,
            radius = 4.dp.toPx(),
            center = androidx.compose.ui.geometry.Offset(width * 0.65f, height * 0.4f)
        )
    }
}
