package com.wordrush.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val SectorColors = listOf(
    Color(0xFFE57373), Color(0xFF81C784), Color(0xFF64B5F6), Color(0xFFFFD54F),
    Color(0xFFBA68C8), Color(0xFF4DB6AC), Color(0xFFFF8A65), Color(0xFF7986CB),
)

/** Декоративное колесо рулетки. Результат считает GameEngine.spin (равновероятно). */
@Composable
fun RouletteWheel(labels: List<String>, rotation: Float, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(240.dp)) {
            val n = labels.size.coerceAtLeast(1)
            val sweep = 360f / n
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2f
            labels.forEachIndexed { i, _ ->
                val start = rotation + i * sweep
                drawArc(
                    color = SectorColors[i % SectorColors.size],
                    startAngle = start,
                    sweepAngle = sweep,
                    useCenter = true,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2f, radius * 2f),
                )
            }
            // указатель сверху
            drawCircle(color = Color(0xFF212121), radius = 8.dp.toPx(), center = Offset(center.x, center.y - radius))
        }
        // ступица
        Canvas(Modifier.size(48.dp)) {
            drawCircle(color = Color.White, radius = size.minDimension / 2f)
            drawCircle(color = Color(0xFF212121), radius = size.minDimension / 6f)
        }
    }
}
