package com.bagimliligim.app.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.bagimliligim.app.presentation.components.Tag
import com.bagimliligim.app.presentation.theme.Amber
import com.bagimliligim.app.presentation.theme.Emerald

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisclosureSheet(active: Boolean, onAccept: () -> Unit, onDecline: () -> Unit) {
    var accepted by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDecline, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Tag("SENİN TEMPO • SENİN BÜTÇEN", MaterialTheme.colorScheme.primary)
            Text("Kaydırmanın\nfarkına var.", style = MaterialTheme.typography.headlineLarge)
            Text("Erişilebilirlik izni neden gerekli?", style = MaterialTheme.typography.titleLarge)
            Text("Bu uygulama sadece kısa video kaydırma hareketlerini saymak ve günlük limit aşımında sizi uyarmak için Erişilebilirlik Servisini kullanır. Sayaç çevrimdışı çalışır. Ham erişilebilirlik olayları cihazından dışarı çıkmaz.")
            Text("Instagram Reels, TikTok ve YouTube Shorts için kaydırma olayları, ekran öğelerinin tür ve teknik kimlikleri incelenir. Sayım uygulamalar arasında geçiş yapıldığında kaydedilir; bu amaçla öndeki uygulamanın paket adı geçici olarak işlenir. Mesajlar, ekrandaki metinler ve video içeriği okunmaz veya saklanmaz.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Yalnızca platform, kaydırma sayısı, tahmini mesafe ve zaman cihazında saklanır. İzin isteğe bağlıdır; reddedersen takip başlamaz. Android ayarlarından istediğin an kapatabilirsin.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Tag(if (active) "Servis: Aktif" else "Servis: Pasif", if (active) Emerald else Amber)
            Row(Modifier.fillMaxWidth().toggleable(accepted, role = Role.Checkbox, onValueChange = { accepted = it }),
                verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = accepted, onCheckedChange = null)
                Text("Bu amaçla erişilebilirlik kullanımını kabul ediyorum.", Modifier.padding(start = 10.dp), style = MaterialTheme.typography.bodyMedium)
            }
            Button(onClick = onAccept, enabled = accepted, modifier = Modifier.fillMaxWidth()) { Text("Kabul et ve erişilebilirlik ayarlarını aç") }
            TextButton(onClick = onDecline, modifier = Modifier.fillMaxWidth()) { Text("Şimdilik takip olmadan devam et") }
        }
    }
}

@Composable
fun PrivacyDialog(onClose: () -> Unit) {
    AlertDialog(onDismissRequest = onClose, title = { Text("Gizlilik ve sayım") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Bağımlılığım’ın sayacı, kişisel sigara kayıtları ve istatistikleri çevrimdışı çalışır. İsteğe bağlı fiyat indirme ve Çevrem özellikleri internet kullanır. Reklam, analiz SDK’sı veya otomatik bulut yedeği yoktur.")
                Text("Fiyat indirme: açarsan ürün fiyatları IKEA Türkiye ve Kitapyurdu’ndan, bildirilen sigara fiyatları Medyaradar’dan veya bağlı katalog sunucusundan alınır. Kişisel sayımlar gönderilmez. Bu siteler IP adresini teknik olarak görebilir. Fiyatlar kaynak ve kontrol tarihiyle saklanır. Satış noktasında ödediğin fiyatı değiştirebilirsin. Son 24 saatte doğrulanmayan ürün fiyatları karşılaştırmalarda kullanılmaz. Gözlemlenen yeni sigara fiyatları geçmişe uygulanmaz.")
                Text("Çevrem: Google ile giriş yaparsan Google hesap kimliğin, e-posta adresin ve temel profil bilgilerin hesap oluşturmak için Supabase'e iletilir. Google şifren uygulamaya verilmez. Arkadaşlarına göstereceğin adı sen seçersin. Ayrı olarak seçtiğin kaydırma/mesafe ve sigara/marka/harcama özetleri yalnızca Paylaş düğmesine basınca son 14 gün için gönderilir. Sadece kabul ettiğin arkadaşlar görebilir. Paylaşımı kapatıp kaydetmek ilgili sunucu özetlerini siler. Ham olaylar ve ekran içeriği gönderilmez. Sunucudaki günlük özetler en fazla 365 gün tutulur.")
                Text("Hesabını Çevrem > Hesabımı sil üzerinden silebilirsin. Bu işlem sunucudaki hesap, arkadaşlık ve özetleri kaldırır; cihaz kayıtlarını silmez. Cihaz kayıtları Android uygulama ayarlarından depolamayı temizleyerek silinir.")
                Text("Kaydedilenler: platform, kaydırma adedi, tahmini mesafe, tarih ve saat. Erişilebilirlikte yalnızca sayım için gereken kaydırma ve pencere değişimi olayları ile teknik öğe kimlikleri kullanılır. Metinler, mesajlar, kullanıcı adları ve videolar okunmaz.")
                Text("Sayım tahminidir. Uygulamaların arayüzü değiştiğinde bazı kaydırmalar algılanmayabilir. İlk olay başlangıç konumudur; hızlı yinelenen olaylar 450 ms boyunca elenir. Reklam ve otomatik geçişler bazen kaydırma gibi görünebilir.")
                Text("Pil tüketimini azaltmak için her 10 kaydırmada, uygulamadan çıkıldığında veya ekran kapandığında kayıt yapılır. Sistem işlemi aniden sonlandırırsa henüz kaydedilmemiş son hareketler kaybolabilir. Bileşen, son kaydedilen değerleri gösterir; Yenile ile güncellenir.")
                Text("Veriler cihazındaki uygulama alanında süresiz tutulur. Android uygulama ayarlarından depolamayı temizleyerek veya uygulamayı kaldırarak silebilirsin. Takibi erişilebilirlik ayarlarından kapatabilirsin.")
                Text("Günlük saatler kaydırmanın gerçekleştiği yerel saat dilimine göre saklanır. Haftalık görünüm son 7, aylık görünüm son 30 gündür. Yıllık görünüm bu takvim yılının 12 ayını gösterir; ortalama bugüne kadar geçen günlerle hesaplanır.")
            }
        }, confirmButton = { TextButton(onClick = onClose) { Text("Anladım") } })
}
