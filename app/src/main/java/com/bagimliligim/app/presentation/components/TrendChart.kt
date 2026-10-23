package com.bagimliligim.app.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bagimliligim.app.domain.ChartPoint

@Composable
fun TrendChart(points: List<ChartPoint>, line: Boolean, modifier: Modifier = Modifier, metricName: String = "kaydırma") {
    val accent = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val max = (points.maxOfOrNull { it.swipes } ?: 0).coerceAtLeast(1)
    val anim by animateFloatAsState(max.toFloat(), tween(500), label = "Grafik ölçeği")
    var selected by remember(points) { mutableIntStateOf(-1) }
    val summary = points.joinToString("; ") { "${it.fullLabel}: ${it.swipes} $metricName" }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (selected in points.indices) "${points[selected].fullLabel} · ${points[selected].swipes} $metricName" else "${metricName.replaceFirstChar { it.uppercaseChar() }} sayısı",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(max.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Canvas(Modifier.fillMaxWidth().height(180.dp).semantics { contentDescription = summary }
            .pointerInput(points) {
                detectTapGestures { offset ->
                    if (points.isNotEmpty()) selected = (offset.x / size.width * points.size).toInt().coerceIn(points.indices)
                }
            }) {
            val bottom = size.height - 4.dp.toPx()
            val top = 8.dp.toPx()
            repeat(4) { n ->
                val y = top + (bottom - top) * n / 3
                drawLine(grid.copy(alpha = .55f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
            }
            if (points.isEmpty()) return@Canvas
            val step = size.width / points.size
            val coords = points.mapIndexed { index, point ->
                Offset(step * (index + .5f), bottom - (bottom - top) * (point.swipes / anim.coerceAtLeast(1f)).coerceIn(0f, 1f))
            }
            if (line && coords.size > 1) {
                val path = Path().apply {
                    moveTo(coords.first().x, coords.first().y)
                    coords.zipWithNext().forEach { (a, b) ->
                        val middle = (a.x + b.x) / 2
                        cubicTo(middle, a.y, middle, b.y, b.x, b.y)
                    }
                }
                val area = Path().apply {
                    addPath(path); lineTo(coords.last().x, bottom); lineTo(coords.first().x, bottom); close()
                }
                drawPath(area, Brush.verticalGradient(listOf(accent.copy(alpha = .24f), accent.copy(alpha = 0f))))
                drawPath(path, accent, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                if (selected in coords.indices) drawCircle(accent, 5.dp.toPx(), coords[selected])
            } else coords.forEachIndexed { index, point ->
                val height = bottom - point.y
                val width = minOf(step * .64f, 36.dp.toPx())
                if (height > 0f) drawRoundRect(accent.copy(alpha = if (selected == -1 || selected == index) 1f else .3f),
                    Offset(point.x - width / 2, point.y), Size(width, height), CornerRadius(4.dp.toPx()))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val indices = if (points.size <= 7) points.indices.toList()
                else listOf(0, points.size / 4, points.size / 2, points.size * 3 / 4, points.lastIndex).distinct()
            indices.forEach { index -> Text(points[index].label,
                modifier = if (points.size <= 7) Modifier.weight(1f) else Modifier,
                textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (points.all { it.swipes == 0 }) Text("Bu dönemde $metricName kaydı yok.", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
