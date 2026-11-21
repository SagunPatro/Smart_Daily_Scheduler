package com.smartscheduler.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A decorative header divider with a gentle organic wavy shape and a smooth multi-stop color
 * gradient transitioning from the primary purple down to soft lavender and the page background.
 * Replaces harsh straight partitions with a polished, fluid contour.
 */
@Composable
fun WavyHeaderDivider(
    modifier: Modifier = Modifier,
    height: Dp = 26.dp,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    backgroundColor: Color = MaterialTheme.colorScheme.background
) {
    val midViolet = Color(0xFF7C3AED)   // Vibrant deep violet
    val lavenderAccent = Color(0xFFA855F7) // Lavender purple
    val softLavender = Color(0xFFC084FC) // Soft pastel lavender
    val crestHighlight = Color(0xFFE9D5FF) // Light lavender highlight

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(backgroundColor)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Layer 1: Ambient background wave with soft translucency for depth
            val backWave = Path().apply {
                moveTo(0f, 0f)
                lineTo(0f, h * 0.70f)
                cubicTo(
                    w * 0.22f, h * 0.40f,
                    w * 0.52f, h * 1.0f,
                    w * 0.78f, h * 0.65f
                )
                cubicTo(
                    w * 0.88f, h * 0.48f,
                    w * 0.96f, h * 0.82f,
                    w, h * 0.72f
                )
                lineTo(w, 0f)
                close()
            }
            drawPath(
                path = backWave,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.50f),
                        softLavender.copy(alpha = 0.30f),
                        Color.Transparent
                    ),
                    startY = 0f,
                    endY = h
                )
            )

            // Layer 2: Main foreground wave with multi-stop rich color transition
            val frontWave = Path().apply {
                moveTo(0f, 0f)
                lineTo(0f, h * 0.48f)
                cubicTo(
                    w * 0.22f, h * 0.95f,
                    w * 0.44f, h * 0.15f,
                    w * 0.67f, h * 0.78f
                )
                cubicTo(
                    w * 0.82f, h * 1.02f,
                    w * 0.94f, h * 0.42f,
                    w, h * 0.58f
                )
                lineTo(w, 0f)
                close()
            }
            drawPath(
                path = frontWave,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        primaryColor,
                        midViolet,
                        lavenderAccent,
                        softLavender.copy(alpha = 0.85f)
                    ),
                    startY = 0f,
                    endY = h
                )
            )

            // Layer 3: Delicate crest highlight line for tactile finish
            val crestLine = Path().apply {
                moveTo(0f, h * 0.48f)
                cubicTo(
                    w * 0.22f, h * 0.95f,
                    w * 0.44f, h * 0.15f,
                    w * 0.67f, h * 0.78f
                )
                cubicTo(
                    w * 0.82f, h * 1.02f,
                    w * 0.94f, h * 0.42f,
                    w, h * 0.58f
                )
            }
            drawPath(
                path = crestLine,
                color = crestHighlight.copy(alpha = 0.55f),
                style = Stroke(width = 1.25.dp.toPx())
            )
        }
    }
}
