package com.bagimliligim.app.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bagimliligim.app.data.BrandCatalog
import com.bagimliligim.app.domain.Analytics
import com.bagimliligim.app.domain.Money
import com.bagimliligim.app.domain.Reduction
import com.bagimliligim.app.presentation.SocialViewModel
import com.bagimliligim.app.presentation.requestGoogleIdentity
import kotlinx.coroutines.launch
import com.bagimliligim.app.presentation.components.HealthCard
import com.bagimliligim.app.presentation.components.GoogleSignInCard
import com.bagimliligim.app.presentation.components.SectionTitle
import com.bagimliligim.app.presentation.components.Tag

@Composable
fun SocialScreen(model: SocialViewModel) {
    val signedIn by model.repository.signedIn.collectAsStateWithLifecycle()
    val snapshot by model.snapshot.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    val selectedBrand by model.brand.collectAsStateWithLifecycle()
    val metric by model.metric.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    var code by rememberSaveable { mutableStateOf("") }
    var showProfile by remember { mutableStateOf(false) }
    var showBrands by remember { mutableStateOf(false) }
    var deleteAccount by remember { mutableStateOf(false) }
    LaunchedEffect(signedIn) { if (signedIn) model.refresh() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp, 0.dp, 24.dp, 32.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)) {
        item {
            Text("Birlikte, biraz daha iyi.", fontFamily = FontFamily.Serif, fontSize = 31.sp, color = MaterialTheme.colorScheme.primary)
            Text("Birbirinizin küçük adımlarına eşlik edin.", Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!model.repository.configured) item {
            HealthCard {
                Text("Çevrem bağlantısı hazır değil", style = MaterialTheme.typography.titleLarge)
                Text("Bu test sürümünde arkadaş hizmeti henüz bağlanmadı. Kişisel kayıtlarını kullanmaya devam edebilirsin.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else if (!signedIn) {
            item {
                GoogleSignInCard(configured = activity != null && model.repository.googleConfigured, busy = busy) {
                    activity?.let { owner -> scope.launch { model.signInWithGoogle { requestGoogleIdentity(owner) } } }
                }
            }
        } else {
            if (snapshot == null) item {
                HealthCard {
                    Text(if (busy) "Çevren yükleniyor…" else "Çevren şu anda yüklenemedi.")
                    TextButton(onClick = model::refresh, enabled = !busy) { Text("Yeniden dene") }
                }
            }
            snapshot?.let { data ->
                item {
                    HealthCard {
                        SectionTitle(data.profile.name, "ARKADAŞ KODUN")
                        Text(data.profile.code, style = MaterialTheme.typography.headlineMedium, letterSpacing = 2.sp)
                        Text("Yalnızca kabul ettiğin arkadaşlar, paylaşmayı seçtiğin özetleri görür.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { showProfile = true }) { Text("Profil ve paylaşım") }
                        OutlinedTextField(code, { code = it.uppercase().filter(Char::isLetterOrDigit).take(12) }, label = { Text("Arkadaşının kodu") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Button(onClick = { model.friend("request", code); code = "" }, enabled = !busy && code.length == 12) { Text("Arkadaş ekle") }
                    }
                }
                val requests = data.friends.filter { it.status == "pending" }
                if (requests.isNotEmpty()) item { SectionTitle("Arkadaşlık istekleri") }
                items(requests, key = { "request-${it.id}" }) { friend ->
                    Column {
                        Text(friend.name, style = MaterialTheme.typography.titleMedium)
                        if (friend.incoming) Row {
                            TextButton(onClick = { model.friend("accept", friend.id) }, enabled = !busy) { Text("Kabul et") }
                            TextButton(onClick = { model.friend("remove", friend.id) }, enabled = !busy) { Text("Reddet") }
                            TextButton(onClick = { model.friend("block", friend.id) }, enabled = !busy) { Text("Engelle") }
                        } else TextButton(onClick = { model.friend("remove", friend.id) }, enabled = !busy) { Text("İstek bekliyor · İptal et") }
                    }
                }
                item {
                    SectionTitle("Haftanın adımları", "SON 7 GÜN")
                    Text("Dereceler tüketim azalmasına göre verilir. Harcama ve kaydırma görünümleri azdan çoğa sıralanır.",
                        Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("reduction" to "Azalma", "spending" to "Harcama", "swipes" to "Kaydırma").forEach { (key, title) ->
                            FilterChip(metric == key, { model.filter(selectedBrand, key) }, label = { Text(title) }, enabled = !busy)
                        }
                    }
                    OutlinedButton(onClick = { showBrands = true }) { Text(BrandCatalog.brands.firstOrNull { it.id == selectedBrand }?.name ?: "Tüm markalar") }
                }
                items(data.leaders, key = { "leader-${it.id}" }) { leader ->
                    HealthCard {
                        SectionTitle(leader.name, Reduction.rankTitle(leader.reduction))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(leader.reduction?.let { "%$it azalma" } ?: "Yeterli kayıt yok", color = MaterialTheme.colorScheme.primary)
                            Text(leader.costMicros?.let(Money::formatMicros) ?: "Harcama gizli")
                        }
                        Text("${leader.cigarettes?.let { "$it sigara" } ?: "Sigara gizli"} · ${leader.trackedDays}/7 gün kayıt",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${leader.swipes?.let { "$it kaydırma" } ?: "Kaydırma gizli"} · ${leader.distanceMeters?.let(Analytics::distance) ?: "Mesafe gizli"}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                item {
                    Text("Azalma derecesi için bu hafta ve önceki haftanın 7 günü de kayıtlı olmalı. Sayımlar kişilerin kendi kayıtlarına dayanır.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = model::sync, enabled = !busy && (data.profile.shareReels || data.profile.shareSmoking), modifier = Modifier.fillMaxWidth()) {
                        Text(if (busy) "Güncelleniyor…" else "Seçtiğim özetleri paylaş ve yenile")
                    }
                    TextButton(onClick = model::refresh, enabled = !busy) { Text("Sadece tabloyu yenile") }
                }
                val friends = data.friends.filter { it.status == "accepted" }
                if (friends.isNotEmpty()) item { SectionTitle("Çevrendekiler") }
                items(friends, key = { "friend-${it.id}" }) { friend ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(friend.name, Modifier.weight(1f))
                        TextButton(onClick = { model.friend("remove", friend.id) }, enabled = !busy) { Text("Çıkar") }
                        TextButton(onClick = { model.friend("block", friend.id) }, enabled = !busy) { Text("Engelle") }
                    }
                }
            }
            item {
                Row {
                    TextButton(onClick = model::signOut, enabled = !busy) { Text("Çıkış yap") }
                    TextButton(onClick = { deleteAccount = true }, enabled = !busy) { Text("Hesabımı sil") }
                }
                Text("Çıkış yapmak paylaşımlarını silmez. Paylaşımı durdurmak için Profil ve paylaşım bölümünü kullan.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        message?.let { item { Text(it, color = MaterialTheme.colorScheme.primary) } }
    }
    if (showProfile && snapshot != null) {
        val profile = snapshot!!.profile
        var name by remember { mutableStateOf(profile.name) }
        var reels by remember { mutableStateOf(profile.shareReels) }
        var smoking by remember { mutableStateOf(profile.shareSmoking) }
        AlertDialog(onDismissRequest = { showProfile = false }, title = { Text("Sen seç, sen paylaş") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it.take(40) }, label = { Text("Görünecek adın") }, singleLine = true)
                Text("Yalnızca son 14 günün günlük özetleri, Paylaş düğmesine bastığında sunucuya gider. Ham erişilebilirlik olayları gönderilmez.")
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(reels, { reels = it }); Text("Kaydırma adedi ve mesafe") }
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(smoking, { smoking = it }); Text("Sigara adedi, marka, harcama ve kayıtlı günler") }
                Text("Bir seçeneği kapatıp kaydettiğinde ilgili sunucu kayıtları silinir. Cihazındaki kayıtlar korunur.", style = MaterialTheme.typography.bodySmall)
            }
        }, confirmButton = { TextButton(onClick = { model.profile(name, reels, smoking); showProfile = false }, enabled = !busy && name.trim().length >= 2) { Text("Tercihlerimi kaydet") } },
            dismissButton = { TextButton(onClick = { showProfile = false }) { Text("Vazgeç") } })
    }
    if (showBrands) AlertDialog(onDismissRequest = { showBrands = false }, title = { Text("Markaya göre göster") }, text = {
        var query by remember { mutableStateOf("") }
        Column {
            OutlinedTextField(query, { query = it }, label = { Text("Marka ara") }, singleLine = true)
            LazyColumn(Modifier.heightIn(max = 300.dp)) {
                item { TextButton(onClick = { model.filter(null, metric); showBrands = false }) { Text("Tüm markalar") } }
                items(BrandCatalog.brands.filter { it.name.contains(query, true) }) { brand ->
                    TextButton(onClick = { model.filter(brand.id, metric); showBrands = false }) { Text(brand.name) }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = { showBrands = false }) { Text("Kapat") } })
    if (deleteAccount) AlertDialog(onDismissRequest = { deleteAccount = false }, title = { Text("Hesabın silinsin mi?") },
        text = { Text("Hesabın, arkadaş bağlantıların ve sunucudaki özetlerin kalıcı olarak silinir. Cihazındaki kişisel kayıtların korunur.") },
        confirmButton = { TextButton(onClick = { model.deleteAccount(); deleteAccount = false }, enabled = !busy) { Text("Hesabımı kalıcı sil") } },
        dismissButton = { TextButton(onClick = { deleteAccount = false }) { Text("Vazgeç") } })
}
