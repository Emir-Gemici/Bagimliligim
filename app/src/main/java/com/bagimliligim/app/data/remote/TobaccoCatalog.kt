package com.bagimliligim.app.data.remote

import android.text.Html
import com.bagimliligim.app.data.BrandCatalog
import com.bagimliligim.app.data.local.CigarettePriceEntity
import com.bagimliligim.app.data.local.SmokingDao
import com.bagimliligim.app.domain.Money
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.URI
import java.time.Instant
import java.time.LocalDate
import java.util.Locale
import javax.net.ssl.HttpsURLConnection

class TobaccoCatalog(private val dao: SmokingDao) {
    companion object {
        const val SOURCE_URL = "https://www.medyaradar.net/guncel-zamli-sigara-fiyatlari-9-ekim-2026-parliament-marlboro-camel-winston-fiyati-haberi-2245484"
        const val INDEX_URL = "https://www.medyaradar.net/etiket/sigara-fiyatlari"
    }
    suspend fun refresh(): Int = withContext(Dispatchers.IO) {
        val links = try { TobaccoPriceParser.articleUrls(download(INDEX_URL)) }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { emptyList() }
        for (url in (links + SOURCE_URL).distinct().take(3)) {
            val rows = try { TobaccoPriceParser.parse(download(url), LocalDate.now(), System.currentTimeMillis(), url) }
                catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { emptyList() }
            if (rows.size >= 10) {
                rows.forEach { dao.insertPrice(it) }
                return@withContext rows.size
            }
        }
        error("Güncel fiyat tablosu doğrulanamadı.")
    }
    private fun download(url: String): String {
        val uri = URI(url)
        require(uri.scheme == "https" && uri.host == "www.medyaradar.net" && uri.userInfo == null && uri.port == -1)
        val connection = uri.toURL().openConnection() as HttpsURLConnection
        return try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 12_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("User-Agent", "Bagimliligim/2.0 price-check")
            check(connection.responseCode == 200 && connection.contentType?.contains("text/html") == true)
            connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    check(output.size() + count <= 2_000_000)
                    output.write(buffer, 0, count)
                }
                output.toString("UTF-8")
            }
        } finally { connection.disconnect() }
    }
}

object TobaccoPriceParser {
    fun articleUrls(html: String): List<String> = Regex("""href=["'](https://www\.medyaradar\.net/[a-z0-9-]*sigara[a-z0-9-]*-haberi-[0-9]+)["']""")
        .findAll(html).map { it.groupValues[1] }.distinct().take(3).toList()

    /** An observed price is valid from observation onward; never infer a historical effective date. */
    fun parse(html: String, observedDate: LocalDate, checkedAt: Long, sourceUrl: String = TobaccoCatalog.SOURCE_URL): List<CigarettePriceEntity> {
        val modified = Regex(""""dateModified"\s*:\s*"([^"]+)"""").find(html)?.groupValues?.get(1)
            ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: return emptyList()
        if (checkedAt - modified !in 0..14 * 24 * 60 * 60 * 1000L) return emptyList()
        val brands = BrandCatalog.brands.associateBy { normalized(it.name) }
        val result = mutableMapOf<String, CigarettePriceEntity>()
        val rows = Regex("<tr\\b[^>]*>([\\s\\S]*?)</tr>", RegexOption.IGNORE_CASE)
        val cells = Regex("<td\\b[^>]*>([\\s\\S]*?)</td>", RegexOption.IGNORE_CASE)
        for (row in rows.findAll(html)) {
            val values = cells.findAll(row.groupValues[1]).map { clean(it.groupValues[1]) }.toList()
            if (values.size != 2) continue
            val amount = Regex("^(\\d{2,4}(?:[.,]\\d{1,2})?)\\s*(?:TL|₺)$").matchEntire(values[1])
                ?.groupValues?.get(1)?.let(Money::parseKurus) ?: continue
            val names = when (values[0]) {
                "Camel Black & White" -> listOf("Camel Black", "Camel White")
                "Camel Yellow & Brown" -> listOf("Camel Yellow", "Camel Brown")
                else -> listOf(values[0].substringBefore('(').trim())
            }
            for (name in names) {
                val brand = brands[normalized(name)] ?: continue
                val price = CigarettePriceEntity("observed-${brand.id}-$observedDate-$amount", brand.id, amount,
                    observedDate.toString(), checkedAt, "Medyaradar · bildirilen fiyat", sourceUrl)
                val existing = result[brand.id]
                // Ambiguous conflicting rows invalidate the whole response, never pick silently.
                if (existing != null && existing.packPriceKurus != amount) return emptyList()
                result[brand.id] = price
            }
        }
        return result.values.toList()
    }
    private fun clean(value: String): String = Html.fromHtml(value, Html.FROM_HTML_MODE_LEGACY).toString()
        .replace('\u00a0', ' ').replace(Regex("\\s+"), " ").trim()
    private fun normalized(value: String): String = value.lowercase(Locale.ROOT).replace("grey", "gray")
        .replace("kırmızı", "red").replace("mavi", "blue").replace("d-range", "d range")
        .removeSuffix(" standart").replace(Regex("\\s+"), " ").trim()
}
