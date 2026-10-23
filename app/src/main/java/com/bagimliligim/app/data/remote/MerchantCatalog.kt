package com.bagimliligim.app.data.remote

import android.content.Context
import com.bagimliligim.app.data.local.ComparisonPriceEntity
import com.bagimliligim.app.data.local.SmokingDao
import com.bagimliligim.app.domain.ComparisonRotation
import com.bagimliligim.app.domain.Money
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.URI
import java.time.LocalDate
import javax.net.ssl.HttpsURLConnection

/** Reads public Product/Offer JSON-LD. No cookies, account, personal metrics or screen data. */
class MerchantCatalog(context: Context, private val dao: SmokingDao) {
    private val assets = context.applicationContext.assets
    suspend fun seedVerifiedPrices() = withContext(Dispatchers.IO) {
        val seed = JSONArray(assets.open("comparison-prices.json").bufferedReader().use { it.readText() })
        val current = dao.comparisons().first().associateBy { it.id }
        val rows = (0 until seed.length()).mapNotNull { index ->
            val p = seed.getJSONObject(index)
            val checkedAt = java.time.Instant.parse(p.getString("checked_at")).toEpochMilli()
            val id = p.getString("id")
            if ((current[id]?.checkedAt ?: 0) >= checkedAt) null else ComparisonPriceEntity(id,
                p.getString("title"), p.getString("category"), p.getLong("price_kurus"), p.getString("source"),
                p.getString("url"), checkedAt, p.getBoolean("available"))
        }
        dao.upsertComparisons(rows)
    }
    suspend fun refresh(): Int = withContext(Dispatchers.IO) {
        val catalog = JSONArray(assets.open("comparison-products.json").bufferedReader().use { it.readText() })
        val cached = dao.comparisons().first().associateBy { it.id }
        val now = System.currentTimeMillis()
        val candidates = (0 until catalog.length()).map { catalog.getJSONObject(it) }.shuffled()
            .filter { cached[it.getString("id")]?.let { old -> !ComparisonRotation.fresh(old.checkedAt, now) } ?: true }.take(8)
        if (candidates.isEmpty()) return@withContext 0
        val semaphore = Semaphore(2)
        val prices = coroutineScope {
            candidates.map { product -> async {
                semaphore.withPermit {
                    try {
                        val url = product.getString("url")
                        val html = download(url)
                        ProductOfferParser.parse(html, product.getString("id"), product.getString("category"), url, System.currentTimeMillis())
                    } catch (cancel: CancellationException) { throw cancel }
                    catch (_: Exception) { null }
                }
            } }.awaitAll().filterNotNull()
        }
        check(prices.isNotEmpty()) { "Mağaza fiyatları doğrulanamadı." }
        dao.upsertComparisons(prices)
        prices.size
    }
    private fun download(url: String): String {
        val uri = URI(url)
        require(MerchantSource.name(url) != null)
        val connection = uri.toURL().openConnection() as HttpsURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10_000
            connection.readTimeout = 12_000
            connection.setRequestProperty("User-Agent", "Bagimliligim/2.0 price-check")
            connection.setRequestProperty("Accept", "text/html")
            check(connection.responseCode == 200)
            check(connection.contentType?.contains("text/html") == true)
            return connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    check(output.size() + count <= 3_000_000)
                    output.write(buffer, 0, count)
                }
                output.toString("UTF-8")
            }
        } finally { connection.disconnect() }
    }
}

object ProductOfferParser {
    fun parse(html: String, id: String, category: String, url: String, checkedAt: Long): ComparisonPriceEntity? {
        val source = MerchantSource.name(url) ?: return null
        val scripts = Regex("""<script\b[^>]*type\s*=\s*["']application/ld\+json["'][^>]*>([\s\S]*?)</script>""", RegexOption.IGNORE_CASE)
        for (match in scripts.findAll(html)) {
            val value = runCatching { org.json.JSONTokener(match.groupValues[1]).nextValue() }.getOrNull() ?: continue
            for (product in objects(value).filter { item -> types(item).contains("Product") }) {
                val offers = product.opt("offers") ?: continue
                for (offer in objects(offers).filter { types(it).contains("Offer") }) {
                    if (offer.optString("priceCurrency") != "TRY") continue
                    val price = Money.parseKurus(offer.optString("price")) ?: continue
                    val availability = offer.optString("availability")
                    if (availability !in setOf("https://schema.org/InStock", "http://schema.org/InStock")) continue
                    val until = offer.optString("priceValidUntil")
                    if (until.isNotBlank() && runCatching { LocalDate.parse(until) < LocalDate.now() }.getOrDefault(true)) continue
                    val title = product.optString("name").trim()
                    if (title.length !in 2..160 || title.any(Char::isISOControl)) continue
                    return ComparisonPriceEntity(id, title, category, price, source, url, checkedAt, true)
                }
            }
        }
        return null
    }
    private fun types(item: JSONObject): List<String> = when (val type = item.opt("@type")) {
        is String -> listOf(type)
        is JSONArray -> (0 until type.length()).map { type.optString(it) }
        else -> emptyList()
    }
    private fun objects(value: Any): List<JSONObject> = when (value) {
        is JSONObject -> listOf(value) + (value.opt("@graph")?.let(::objects) ?: emptyList())
        is JSONArray -> (0 until value.length()).flatMap { objects(value.get(it)) }
        else -> emptyList()
    }
}

object MerchantSource {
    fun name(url: String): String? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        if (uri.scheme != "https" || uri.userInfo != null || uri.port !in listOf(-1, 443)) return null
        return when {
            uri.host == "www.ikea.com.tr" && uri.path.startsWith("/urun/") -> "IKEA Türkiye"
            uri.host == "www.kitapyurdu.com" && Regex("/kitap/[a-z0-9-]+/[0-9]+\\.html").matches(uri.path) -> "Kitapyurdu"
            else -> null
        }
    }
}
