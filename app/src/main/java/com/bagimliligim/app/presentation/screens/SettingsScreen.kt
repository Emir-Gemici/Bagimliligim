package com.bagimliligim.app.presentation.screens

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bagimliligim.app.domain.Analytics
import com.bagimliligim.app.BuildConfig
import com.bagimliligim.app.R
import com.bagimliligim.app.domain.UserPreferences
import com.bagimliligim.app.presentation.components.HealthCard
import com.bagimliligim.app.presentation.components.SectionTitle
import com.bagimliligim.app.presentation.components.Tag
import com.bagimliligim.app.presentation.theme.Amber
import com.bagimliligim.app.presentation.theme.Emerald
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(preferences: UserPreferences, active: Boolean, notificationsAllowed: Boolean,
    onQuota: (Int) -> Unit, onLength: (Float) -> Unit, onWarnings: (Boolean) -> Unit,
    onAppearance: (Boolean, Boolean) -> Unit, onAccessibility: () -> Unit,
    onNotifications: () -> Unit, onConsent: () -> Unit, onPrivacy: () -> Unit,
    onOnlinePrices: (Boolean) -> Unit, pricesConfigured: Boolean) {
    var quota by remember(preferences.dailyQuota) { mutableFloatStateOf(preferences.dailyQuota.toFloat()) }
    var length by remember(preferences.swipeLengthCm) { mutableFloatStateOf(preferences.swipeLengthCm) }
    LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            HealthCard {
                SectionTitle("Günlük Kaydırma Bütçesi")
                Text("${quota.roundToInt()} kaydırma", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                Slider(value = quota, onValueChange = { quota = (it / 10).roundToInt() * 10f }, valueRange = 50f..1000f, steps = 94,
                    onValueChangeFinished = { onQuota(quota.roundToInt()) }, modifier = Modifier.semantics { contentDescription = "Günlük kaydırma bütçesi" })
                Text("50 – 1000 kaydırma. Bütçen her gün gece yarısında yenilenir.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            HealthCard {
                SectionTitle("Parmak mesafesi")
                Text("Ortalama Parmak Kaydırma Mesafesi", style = MaterialTheme.typography.bodyMedium)
                Text(String.format(Analytics.turkish, "%.1f cm", length), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                Slider(value = length, onValueChange = { length = (it * 2).roundToInt() / 2f }, valueRange = 3f..25f, steps = 43,
                    onValueChangeFinished = { onLength(length) }, modifier = Modifier.semantics { contentDescription = "Ortalama parmak kaydırma mesafesi" })
                Text("Bir kaydırmada parmağının ekranda aldığı yolu ölç. Varsayılan 12 cm. Yeni değer yalnızca sonraki kaydırmalara uygulanır.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            HealthCard {
                ToggleRow("Limit uyarıları", "%80 ve %100’de nazik bir hatırlatma.", preferences.warningsEnabled, onWarnings)
                Text("Bildirim izni isteğe bağlıdır; yalnızca kendi bütçe hatırlatmalarımızı gösterir. Diğer uygulamaların bildirimleri okunmaz.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (preferences.warningsEnabled && !notificationsAllowed) {
                    Text("Sistem bildirimleri kapalı. Kısa ekran mesajları kullanılacak.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = onNotifications) { Text("Bildirim iznini yönet") }
                }
            }
        }
        item {
            HealthCard {
                SectionTitle("Görünüm")
                ToggleRow("Koyu tema", "Göz yormayan sade kartlar.", preferences.darkMode) { onAppearance(it, preferences.dynamicColor) }
                if (Build.VERSION.SDK_INT >= 31) ToggleRow("Duvar kâğıdı renkleri", "Cihazının dinamik renklerini kullan.", preferences.dynamicColor) {
                    onAppearance(preferences.darkMode, it)
                }
            }
        }
        item {
            HealthCard {
                SectionTitle("Erişilebilirlik servisi")
                Tag(if (active) "Aktif" else "Pasif", if (active) Emerald else Amber)
                Text("Takibi istediğin an Android erişilebilirlik ayarlarından kapatabilirsin.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = onAccessibility) { Text(if (active) "Erişilebilirlik ayarları" else "Takibi etkinleştir") }
                TextButton(onClick = onConsent) { Text("İzin açıklamasını ve onayı yönet") }
            }
        }
        item {
            HealthCard {
                SectionTitle("Fiyat güncellemeleri")
                Text("İnternetten yalnızca fiyat listeleri indirilir. Kişisel kayıtların fiyat güncellemesi sırasında gönderilmez.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                if (pricesConfigured) ToggleRow("Güncel fiyatları indir", "Kaynak ve kontrol tarihiyle birlikte.", preferences.onlinePrices, onOnlinePrices)
                else Text("Bu test sürümünün fiyat hizmeti henüz bağlanmadı.", style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            HealthCard {
                Text("Verilerin seninle kalır", style = MaterialTheme.typography.titleLarge)
                Text("Sayaç ve kişisel kayıtlar çevrimdışı çalışır. Fiyat indirme isteğe bağlıdır. Çevrem bölümünde yalnızca seçtiğin özetler, Paylaş düğmesine bastığında gönderilir. Reklam ve analiz SDK’sı yok.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onPrivacy) { Text("Gizlilik ve sayım hakkında") }
                Text("${stringResource(R.string.app_name)} · ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = value, onCheckedChange = onChange, modifier = Modifier.semantics { contentDescription = title })
    }
}
