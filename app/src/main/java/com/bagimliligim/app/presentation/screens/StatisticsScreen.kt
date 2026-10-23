package com.bagimliligim.app.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bagimliligim.app.domain.Analytics
import com.bagimliligim.app.domain.StatsPeriod
import com.bagimliligim.app.presentation.DashboardData
import com.bagimliligim.app.presentation.components.HealthCard
import com.bagimliligim.app.presentation.components.Metric
import com.bagimliligim.app.presentation.components.SectionTitle
import com.bagimliligim.app.presentation.components.TrendChart
import java.time.format.DateTimeFormatter

@Composable
fun StatisticsScreen(data: DashboardData, onPeriod: (StatsPeriod) -> Unit) {
    val total = data.chart.sumOf { it.swipes }
    val distance = data.chart.sumOf { it.distanceMeters.toDouble() }.toFloat()
    val range = Analytics.range(data.period, data.date)
    val peak = data.chart.maxByOrNull { it.swipes }?.takeIf { it.swipes > 0 }
    val dateFormat = DateTimeFormatter.ofPattern("d MMM", Analytics.turkish)
    LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                StatsPeriod.entries.forEachIndexed { index, period ->
                    SegmentedButton(selected = data.period == period, onClick = { onPeriod(period) },
                        shape = SegmentedButtonDefaults.itemShape(index, StatsPeriod.entries.size), icon = {}) {
                        Text(period.title, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }
            }
        }
        item {
            HealthCard {
                SectionTitle(when (data.period) {
                    StatsPeriod.DAILY -> "Bugünün ritmi"
                    StatsPeriod.WEEKLY -> "Son 7 gün"
                    StatsPeriod.MONTHLY -> "Son 30 gün"
                    StatsPeriod.YEARLY -> "${data.date.year} yılı"
                })
                Text("${range.start.format(dateFormat)} – ${range.end.format(dateFormat)}", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Metric(total.toString(), "toplam kaydırma", color = MaterialTheme.colorScheme.primary)
                TrendChart(data.chart, line = data.period == StatsPeriod.MONTHLY || data.period == StatsPeriod.YEARLY)
                Text("Bir noktaya dokunarak ayrıntısını gör. Boş günler de hesaba katılır.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            HealthCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Metric(String.format(Analytics.turkish, "%.1f", total.toFloat() / range.days), "günlük ortalama", Modifier.weight(1f))
                    Metric(Analytics.distance(distance), "parmak maratonu", Modifier.weight(1f))
                }
                SectionTitle(when (data.period) {
                    StatsPeriod.DAILY -> "En yoğun saat"
                    StatsPeriod.YEARLY -> "En yoğun ay"
                    else -> "En yoğun gün"
                })
                Text(peak?.let { "${it.fullLabel} · ${it.swipes} kaydırma" } ?: "Henüz yeterli veri yok.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            HealthCard {
                Text("Bir sayıdan fazlası", style = MaterialTheme.typography.titleLarge)
                Text(Analytics.milestone(distance), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Daha az kaydırma, kendine daha çok zaman. Bütçeni sana iyi gelecek bir hedef olarak düşün.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
