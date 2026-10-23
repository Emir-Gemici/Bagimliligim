package com.bagimliligim.app.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bagimliligim.app.presentation.theme.Amber
import com.bagimliligim.app.presentation.theme.Crimson
import com.bagimliligim.app.presentation.theme.Emerald

@Composable
fun HealthCard(modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = tint)) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
    }
}

fun quotaColor(ratio: Float): Color = when { ratio < .6f -> Emerald; ratio <= .9f -> Amber; else -> Crimson }

@Composable
fun BudgetGauge(swipes: Int, quota: Int, modifier: Modifier = Modifier) {
    val ratio = swipes.toFloat() / quota.coerceAtLeast(1)
    val remaining = (quota - swipes).coerceAtLeast(0)
    val progress by animateFloatAsState((1 - ratio).coerceIn(0f, 1f), tween(900), label = "Kalan bütçe")
    val track = MaterialTheme.colorScheme.outlineVariant
    val accent = quotaColor(ratio)
    Box(modifier.size(238.dp).semantics(mergeDescendants = true) {
        contentDescription = "$quota kaydırma bütçesinden $remaining kaldı, $swipes kullanıldı"
    }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(226.dp)) {
            val inset = 12.dp.toPx()
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            drawArc(track, 135f, 270f, false, Offset(inset, inset), arcSize, style = Stroke(13.dp.toPx(), cap = StrokeCap.Round))
            if (progress > 0) drawArc(accent, 135f, 270f * progress, false, Offset(inset, inset), arcSize,
                style = Stroke(13.dp.toPx(), cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(remaining.toString(), style = MaterialTheme.typography.displayLarge)
            Text("kaydırma kaldı", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            Text("${((1 - ratio).coerceIn(0f, 1f) * 100).toInt()}% bütçe", color = accent,
                style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 10.dp))
        }
    }
}

@Composable
fun Metric(value: String, label: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = color)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SectionTitle(title: String, detail: String? = null) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun NavGlyph(index: Int, color: Color) {
    Canvas(Modifier.size(24.dp)) {
        val stroke = Stroke(2.dp.toPx(), cap = StrokeCap.Round)
        when (index) {
            0 -> {
                val path = Path().apply {
                    moveTo(size.width * .15f, size.height * .44f); lineTo(size.width * .5f, size.height * .15f)
                    lineTo(size.width * .85f, size.height * .44f); lineTo(size.width * .85f, size.height * .85f)
                    lineTo(size.width * .15f, size.height * .85f); close()
                }
                drawPath(path, color, style = stroke)
            }
            1 -> listOf(.45f, .8f, .6f).forEachIndexed { i, height ->
                val x = size.width * (.2f + i * .3f)
                drawLine(color, Offset(x, size.height * .85f), Offset(x, size.height * (1 - height)), 3.dp.toPx(), StrokeCap.Round)
            }
            2 -> {
                drawRoundRect(color, Offset(size.width * .2f, size.height * .15f), Size(size.width * .6f, size.height * .7f),
                    androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()), style = stroke)
                for (y in listOf(.35f, .55f, .75f)) drawLine(color, Offset(size.width * .35f, size.height * y), Offset(size.width * .65f, size.height * y), 1.5.dp.toPx())
            }
            3 -> {
                drawCircle(color, size.minDimension * .13f, Offset(size.width * .35f, size.height * .3f), style = stroke)
                drawCircle(color, size.minDimension * .13f, Offset(size.width * .72f, size.height * .4f), style = stroke)
                drawArc(color, 190f, 160f, false, Offset(size.width * .08f, size.height * .5f), Size(size.width * .53f, size.height * .65f), style = stroke)
                drawArc(color, 190f, 160f, false, Offset(size.width * .48f, size.height * .6f), Size(size.width * .46f, size.height * .5f), style = stroke)
            }
            else -> {
                drawCircle(color, size.minDimension * .35f, style = stroke)
                drawCircle(color, size.minDimension * .12f, style = stroke)
            }
        }
    }
}

@Composable
fun Tag(text: String, color: Color) {
    Text(text, Modifier.background(color.copy(alpha = .12f), RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 6.dp),
        color = color, style = MaterialTheme.typography.labelSmall)
}
