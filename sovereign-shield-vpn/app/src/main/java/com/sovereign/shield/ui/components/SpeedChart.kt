package com.sovereign.shield.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sovereign.shield.ui.theme.*

/**
 * Real-time speed chart with gradient fill — shows download/upload throughput history.
 * Smooth animated line with area fill under the curve.
 */
@Composable
fun SpeedChart(
    rxHistory: List<Float>,
    txHistory: List<Float>,
    modifier: Modifier = Modifier,
    maxPoints: Int = 60
) {
    val rxColor = ChartDownload
    val txColor = ChartUpload

    Column(modifier = modifier) {
        // Legend
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(modifier = Modifier.size(8.dp)) {
                    drawCircle(color = rxColor)
                }
                Spacer(Modifier.width(4.dp))
                Text("Download", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(modifier = Modifier.size(8.dp)) {
                    drawCircle(color = txColor)
                }
                Spacer(Modifier.width(4.dp))
                Text("Upload", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
        ) {
            val width = size.width
            val height = size.height
            val padding = 4f

            // Find max value for scaling
            val allValues = rxHistory + txHistory
            val maxValue = (allValues.maxOrNull() ?: 1f).coerceAtLeast(1024f)

            // Draw grid lines
            for (i in 0..3) {
                val y = padding + (height - 2 * padding) * i / 3
                drawLine(
                    color = ChartGrid,
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = 1f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
                )
            }

            // Draw RX line with area fill
            if (rxHistory.size > 1) {
                val rxPath = Path()
                val rxAreaPath = Path()
                val step = width / maxPoints.toFloat()

                rxHistory.forEachIndexed { index, value ->
                    val x = index * step
                    val y = height - padding - (value / maxValue) * (height - 2 * padding)
                    if (index == 0) {
                        rxPath.moveTo(x, y)
                        rxAreaPath.moveTo(x, height)
                        rxAreaPath.lineTo(x, y)
                    } else {
                        rxPath.lineTo(x, y)
                        rxAreaPath.lineTo(x, y)
                    }
                }

                // Close area path
                rxAreaPath.lineTo((rxHistory.size - 1) * step, height)
                rxAreaPath.close()

                // Draw area fill
                drawPath(
                    path = rxAreaPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            rxColor.copy(alpha = 0.15f),
                            rxColor.copy(alpha = 0.02f)
                        )
                    )
                )

                // Draw line
                drawPath(
                    path = rxPath,
                    color = rxColor,
                    style = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }

            // Draw TX line with area fill
            if (txHistory.size > 1) {
                val txPath = Path()
                val txAreaPath = Path()
                val step = width / maxPoints.toFloat()

                txHistory.forEachIndexed { index, value ->
                    val x = index * step
                    val y = height - padding - (value / maxValue) * (height - 2 * padding)
                    if (index == 0) {
                        txPath.moveTo(x, y)
                        txAreaPath.moveTo(x, height)
                        txAreaPath.lineTo(x, y)
                    } else {
                        txPath.lineTo(x, y)
                        txAreaPath.lineTo(x, y)
                    }
                }

                txAreaPath.lineTo((txHistory.size - 1) * step, height)
                txAreaPath.close()

                drawPath(
                    path = txAreaPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            txColor.copy(alpha = 0.1f),
                            txColor.copy(alpha = 0.01f)
                        )
                    )
                )

                drawPath(
                    path = txPath,
                    color = txColor,
                    style = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }
    }
}
