package com.bagimliligim.app.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bagimliligim.app.data.local.CigaretteBrandEntity
import com.bagimliligim.app.domain.*
import com.bagimliligim.app.presentation.SmokingState
import com.bagimliligim.app.presentation.SmokingViewModel
import com.bagimliligim.app.presentation.components.HealthCard
import com.bagimliligim.app.presentation.components.Metric
import com.bagimliligim.app.presentation.components.SectionTitle
import com.bagimliligim.app.presentation.components.Tag
import com.bagimliligim.app.presentation.components.TrendChart
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

@Composable
fun SmokingScreen(model: SmokingViewModel, onlinePrices: Boolean) {
    val state by model.state.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val syncing by model.syncing.collectAsStateWithLifecycle()
    var showRecord by rememberSaveable { mutableStateOf(false) }
    var showOnline by rememberSaveable { mutableStateOf(false) }
    val totals = state.window.totals
    LaunchedEffect(Unit) { model.onResume() }
    LazyColumn(Modifier.fillMaxSize().testTag("smoking-list"), contentPadding = PaddingValues(20.dp, 0.dp, 20.dp, 32.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)) {
        item { PeriodSelector(state.window.period, model::selectPeriod) }
        item {
            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(24.dp)).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("SİGARAYA AYRILAN", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(Money.formatMicros(totals.costMicros), fontSize = 38.sp, fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-1.5).sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("${totals.cigarettes} sigara · ${totals.days} gün kayıt", color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("Tutarlar, kayıt tarihindeki paket fiyatıyla hesaplandı.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .8f))
                Button(onClick = { model.clearError(); showRecord = true }, modifier = Modifier.fillMaxWidth(), enabled = !busy,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        contentColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Text("+  Sigara kaydet")
                }
            }
        }
        state.error?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) } }
        if (state.window.buckets.none { it.date == state.window.today.toString() && it.cigarettes > 0 }) item {
            val recorded = state.window.buckets.any { it.date == state.window.today.toString() }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (recorded) "Bugün içmediğin kaydedildi.\nİstersen daha sonra kayıt ekleyebilirsin." else "Bugün içmediysen bunu da kaydet.\nBoş bırakılan günler sıfır sayılmaz.", Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = model::smokeFree, enabled = !busy && !recorded) { Text(if (recorded) "Kaydedildi" else "İçmedim") }
            }
        }
        item { ComparisonCard(state, onlinePrices, syncing, onEnable = { showOnline = true }, onRefresh = { model.refreshPrices() }) }
        if (totals.days > 0) item {
            HealthCard {
                SectionTitle("Günlerin izi")
                val points = state.window.buckets.map { bucket ->
                    val date = LocalDate.parse(bucket.date)
                    ChartPoint(date.dayOfMonth.toString(), date.format(DateTimeFormatter.ofPattern("d MMMM", Analytics.turkish)), bucket.cigarettes, 0f)
                }
                TrendChart(points, line = points.size > 7, metricName = "sigara")
                Text("Yalnızca kayıt yaptığın günler gösterilir.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (state.window.brands.isNotEmpty()) {
            item { SectionTitle("Harcamanın dağılımı", "MARKAYA GÖRE") }
            items(state.window.brands, key = { "brand-${it.brandId}" }) { brand ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(brand.name, style = MaterialTheme.typography.titleMedium)
                        Text("${brand.cigarettes} sigara", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(Money.formatMicros(brand.costMicros), style = MaterialTheme.typography.titleMedium)
                }
                HorizontalDivider(Modifier.padding(top = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        item { SectionTitle("Kayıt defterin", if (state.window.logs.size > 50) "SON 50 KAYIT" else null) }
        if (state.window.logs.isEmpty()) item {
            Text("Henüz kayıt yok. İlk kaydında markanı ve ödediğin paket fiyatını seçebilirsin.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(state.window.logs.take(50), key = { it.id }) { log ->
            Row(Modifier.fillMaxWidth().testTag("smoking-log-${log.id}"), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${log.count} adet · ${log.brandName}", style = MaterialTheme.typography.titleSmall)
                    Text("${LocalDate.parse(log.date).format(DateTimeFormatter.ofPattern("d MMM yyyy", Analytics.turkish))} · ${Money.formatMicros(log.costMicros)}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Paket: ${Money.formatKurus(log.packPriceKurus)} / ${log.packSize} adet", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { model.delete(log.id) }, modifier = Modifier.testTag("undo-${log.id}"), enabled = !busy) { Text("Geri al") }
            }
        }
    }
    if (showRecord) RecordSheet(state, model, busy) { showRecord = false }
    if (showOnline) AlertDialog(onDismissRequest = { showOnline = false }, title = { Text("Fiyatları internetten güncelle") },
        text = { Text("Ürün fiyatları IKEA Türkiye ve Kitapyurdu’nun, sigara fiyatları Medyaradar’ın herkese açık listelerinden veya bağlı katalog hizmetinden indirilir. Bu siteler IP adresini görebilir. Kişisel kayıtların, harcamaların veya erişilebilirlik olayların gönderilmez. Bildirilen fiyat satış noktasına göre değişebilir; ödediğin fiyatı düzeltebilirsin. Ayarlardan kapatabilirsin." ) },
        confirmButton = { TextButton(onClick = { model.setOnline(true); showOnline = false }, enabled = model.configured) { Text("Fiyat güncellemeyi aç") } },
        dismissButton = { TextButton(onClick = { showOnline = false }) { Text("Şimdi değil") } })
}

@Composable
fun PeriodSelector(selected: StatsPeriod, onSelect: (StatsPeriod) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        StatsPeriod.entries.forEachIndexed { index, period ->
            SegmentedButton(selected == period, { onSelect(period) }, SegmentedButtonDefaults.itemShape(index, StatsPeriod.entries.size), icon = {}) {
                Text(period.title, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ComparisonCard(state: SmokingState, online: Boolean, syncing: Boolean, onEnable: () -> Unit, onRefresh: () -> Unit) {
    val context = LocalContext.current
    val memory = remember { context.getSharedPreferences("comparison_rotation", android.content.Context.MODE_PRIVATE) }
    val uriHandler = LocalUriHandler.current
    var selection by remember { mutableStateOf<String?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); kotlinx.coroutines.delay(60_000) } }
    val affordable = state.comparisons.filter { ComparisonRotation.fresh(it.checkedAt, now) &&
        it.priceKurus * 10_000 <= state.window.totals.costMicros }
    fun next() {
        val recent = memory.getString("recent", "").orEmpty().split('|').filter(String::isNotBlank)
        selection = ComparisonRotation.next(affordable.map { it.id }, recent)
        selection?.let { memory.edit().putString("recent", (recent + it).takeLast(30).joinToString("|")).apply() }
    }
    LaunchedEffect(affordable.map { it.id }) { if (selection !in affordable.map { it.id }) next() }
    val item = affordable.firstOrNull { it.id == selection }
    HealthCard {
        Text("BAŞKA BİR İHTİMAL", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        if (item != null) {
            val quantity = state.window.totals.costMicros / (item.priceKurus * 10_000)
            Text("Bu parayla $quantity ${item.title} alabilirdin.", style = MaterialTheme.typography.headlineSmall)
            val checked = Instant.ofEpochMilli(item.checkedAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM, HH:mm", Analytics.turkish))
            Text("${Money.formatKurus(item.priceKurus)} / adet · ${item.source}\nKontrol: $checked · Kargo hariç",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row {
                TextButton(onClick = { next() }, enabled = affordable.size > 1) { Text("Başka bir ihtimal") }
                TextButton(onClick = { uriHandler.openUri(item.url) }) { Text("Fiyat kaynağı ↗") }
            }
        } else {
            Text("Paranın başka bir karşılığı var.", style = MaterialTheme.typography.headlineSmall)
            Text(if (!online) "Güncel ürün fiyatlarını aç; harcamanın karşılığında neler alabileceğini keşfet."
                else if (state.window.totals.costMicros == 0L) "Bir harcama kaydettiğinde, güncel fiyatlarla alternatifleri burada göreceksin."
                else "Bu tutara uygun, son 24 saatte doğrulanmış bir ürün bulunamadı. Eski fiyatlarla karşılaştırma yapılmaz.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = if (online) onRefresh else onEnable, enabled = !syncing) {
                Text(if (syncing) "Fiyatlar kontrol ediliyor…" else if (online) "Fiyatları yenile" else "Güncel fiyatları aç")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordSheet(state: SmokingState, model: SmokingViewModel, busy: Boolean, onClose: () -> Unit) {
    val context = LocalContext.current
    val memory = remember { context.getSharedPreferences("smoking_selection", android.content.Context.MODE_PRIVATE) }
    var selectedId by rememberSaveable { mutableStateOf(memory.getString("brand", null)) }
    var query by rememberSaveable { mutableStateOf("") }
    var picking by rememberSaveable { mutableStateOf(selectedId == null) }
    var count by rememberSaveable { mutableStateOf("1") }
    var dateText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var priceText by rememberSaveable { mutableStateOf("") }
    var customName by rememberSaveable { mutableStateOf("") }
    var customSize by rememberSaveable { mutableStateOf("20") }
    var addingBrand by rememberSaveable { mutableStateOf(false) }
    val id = rememberSaveable { UUID.randomUUID().toString() }
    val date = runCatching { LocalDate.parse(dateText) }.getOrNull()?.takeIf { it <= LocalDate.now() && it.year >= 2000 }
    val brand = state.brands.firstOrNull { it.id == selectedId }
    val knownPrice = state.prices.firstOrNull { it.brandId == selectedId && date != null && it.validFrom <= date.toString() }
    val manualPrice = Money.parseKurus(priceText)
    val price = if (priceText.isNotBlank()) manualPrice else knownPrice?.packPriceKurus
    val amount = count.toIntOrNull()?.takeIf { it in 1..200 }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = { if (!busy) onClose() }, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Kendin için bir kayıt.", style = MaterialTheme.typography.headlineMedium)
            Text("Bu kayıt yalnızca cihazında saklanır.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (picking) {
                OutlinedTextField(query, { query = it }, label = { Text("Marka veya seri ara") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                val filtered = state.brands.filter { it.name.contains(query.trim(), true) || (query.equals("wiston", true) && it.family == "Winston") }
                Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                    filtered.forEach { item ->
                        TextButton(onClick = { selectedId = item.id; picking = false; priceText = "" }, modifier = Modifier.fillMaxWidth().testTag("brand-option-${item.id}")) {
                            Text(item.name, Modifier.fillMaxWidth())
                        }
                    }
                }
                TextButton(onClick = { addingBrand = !addingBrand }) { Text("Markam listede yok") }
                if (addingBrand) {
                    OutlinedTextField(customName, { customName = it.take(80) }, label = { Text("Marka ve seri adı") }, singleLine = true)
                    OutlinedTextField(customSize, { customSize = it.take(3) }, label = { Text("Paketteki adet") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                    Button(onClick = { model.addBrand(customName, customSize.toInt()) { selectedId = it; picking = false; addingBrand = false } },
                        enabled = !busy && customName.trim().length >= 2 && (customSize.toIntOrNull() ?: 0) in 1..100) { Text("Markayı ekle") }
                }
            } else {
                OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) { Text(brand?.name ?: "Marka seç") }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(count, { count = it.take(3) }, Modifier.weight(1f), label = { Text("Adet") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = amount == null)
                    OutlinedTextField(dateText, { dateText = it.take(10) }, Modifier.weight(2f), label = { Text("Tarih (YYYY-AA-GG)") }, singleLine = true, isError = date == null)
                }
                if (knownPrice != null) {
                    val uriHandler = LocalUriHandler.current
                    Text("Kayıtlı paket fiyatı: ${Money.formatKurus(knownPrice.packPriceKurus)}\n${knownPrice.source} · ${knownPrice.validFrom} tarihinden itibaren",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (knownPrice.sourceUrl.isNotBlank()) {
                        val checked = Instant.ofEpochMilli(knownPrice.observedAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Analytics.turkish))
                        Text("Kaynak kontrolü: $checked. Gerçekte ödediğin tutar farklı olabilir.", style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { uriHandler.openUri(knownPrice.sourceUrl) }) { Text("Fiyat kaynağını gör ↗") }
                    }
                }
                OutlinedTextField(priceText, { priceText = it.take(12) }, modifier = Modifier.fillMaxWidth().testTag("pack-price"), singleLine = true,
                    label = { Text(if (knownPrice == null) "Ödediğin paket fiyatı (₺)" else "Fiyat farklıysa yaz (₺)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), isError = priceText.isNotBlank() && manualPrice == null)
                Text("Paket: ${brand?.packSize ?: 20} adet. Bu fiyat kaydına sabitlenir; gelecekteki zamlar geçmiş harcamanı değiştirmez.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (price != null && amount != null && brand != null) {
                    Text(Money.formatMicros(Money.costMicros(price, brand.packSize, amount)), style = MaterialTheme.typography.headlineMedium)
                }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(onClick = {
                    model.record(id, brand!!.id, amount!!, date!!, manualPrice) {
                        memory.edit().putString("brand", brand.id).apply(); onClose()
                    }
                }, enabled = !busy && brand != null && amount != null && date != null && price != null,
                    modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Kaydediliyor…" else "Kaydı ekle") }
            }
        }
    }
}
