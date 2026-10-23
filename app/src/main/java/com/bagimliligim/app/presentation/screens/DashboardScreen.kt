package com.bagimliligim.app.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bagimliligim.app.domain.Analytics
import com.bagimliligim.app.domain.AppPlatform
import com.bagimliligim.app.domain.UserPreferences
import com.bagimliligim.app.presentation.DashboardData
import com.bagimliligim.app.presentation.components.BudgetGauge
import com.bagimliligim.app.presentation.components.Metric
import com.bagimliligim.app.presentation.components.SectionTitle
import com.bagimliligim.app.presentation.components.Tag
import com.bagimliligim.app.presentation.theme.*
import java.time.format.DateTimeFormatter

@Composable
fun DashboardScreen(data: DashboardData, preferences: UserPreferences, active: Boolean, onEnable: () -> Unit, onRefresh: () -> Unit) {
    LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(24.dp, 0.dp, 24.dp, 32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(data.date.format(DateTimeFormatter.ofPattern("d MMMM EEEE", Analytics.turkish)),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Tag(if (active) "Takip açık" else "Takip kapalı", if (active) Emerald else Amber)
            }
        }
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Birazı sana kalsın.", fontFamily = FontFamily.Serif, fontSize = 32.sp, color = MaterialTheme.colorScheme.primary)
                Text("Bugünkü bütçen", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                BudgetGauge(data.today.swipes, preferences.dailyQuota)
            }
        }
        item {
            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(20.dp)).padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Metric(data.today.swipes.toString(), "kaydırma / ${preferences.dailyQuota}", Modifier.weight(1f))
                VerticalDivider(Modifier.height(48.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Metric(Analytics.distance(data.today.distanceMeters), "parmağının yolu", Modifier.weight(1f))
            }
        }
        if (!active) item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Sayacın şu an kapalı.\nKaydırmalarını görmeye başla.", Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = onEnable) { Text("Takibi aç") }
            }
        }
        if (data.today.swipes >= preferences.dailyQuota) item {
            Text(if (data.today.swipes == preferences.dailyQuota) "Bugünün bütçesi doldu. Bir nefeslik ara?"
                else "Bugün bütçenin ${data.today.swipes - preferences.dailyQuota} kaydırma üzerindesin. Bir sonraki hareket senin seçimin.",
                style = MaterialTheme.typography.bodyMedium, color = Crimson)
        }
        item { SectionTitle("Nerede geçti?", "BUGÜN") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
                AppPlatform.entries.forEach { platform ->
                    val total = data.platforms.firstOrNull { it.platform == platform }
                    val accent = when (platform) { AppPlatform.INSTAGRAM -> Lavender; AppPlatform.TIKTOK -> Emerald; AppPlatform.YOUTUBE -> Crimson }
                    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(8.dp).background(accent, RoundedCornerShape(50)))
                            Text(platform.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            Text((total?.swipes ?: 0).toString(), fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
                        }
                        LinearProgressIndicator(progress = { (total?.swipes ?: 0).toFloat() / data.today.swipes.coerceAtLeast(1) },
                            modifier = Modifier.fillMaxWidth().height(3.dp), color = accent, trackColor = MaterialTheme.colorScheme.outlineVariant,
                            drawStopIndicator = {})
                        Text(Analytics.distance(total?.distanceMeters ?: 0f), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(22.dp)).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("BİR HAFTANIN İZİ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(Analytics.distance(data.week.distanceMeters), fontSize = 36.sp, fontFamily = FontFamily.Serif,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(Analytics.milestone(data.week.distanceMeters), style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .2f))
                Text("Başlangıçtan bugüne ${data.allTime.swipes} kaydırma\n${Analytics.distance(data.allTime.distanceMeters)} tahmini parmak mesafesi",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Sayacın çevrimdışı çalışır.\nMesafeler kalibrasyonuna göre tahmin edilir.", Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onRefresh) { Text("Yenile") }
            }
        }
    }
}
