package com.bagimliligim.app.data.remote

import androidx.room.withTransaction
import com.bagimliligim.app.data.local.CigaretteBrandEntity
import com.bagimliligim.app.data.local.CigarettePriceEntity
import com.bagimliligim.app.data.local.ComparisonPriceEntity
import com.bagimliligim.app.data.local.SwipeDatabase
import com.bagimliligim.app.domain.ComparisonRotation
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.json.JSONObject
import java.net.URI
import java.time.Instant
import java.time.LocalDate

class CatalogSync(private val database: SwipeDatabase, private val merchant: MerchantCatalog, private val client: BackendClient = BackendClient()) {
    val configured get() = true // The public merchant source works independently of the social server.
    private val mutex = Mutex()
    private var lastRefresh = 0L

    suspend fun refresh(force: Boolean = false) = mutex.withLock {
        if (!force && System.currentTimeMillis() - lastRefresh < 60 * 60 * 1000) return@withLock
        val coverage = if (client.configured) {
            try { refreshRemote() }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { Coverage(false, false) }
        } else Coverage(false, false)
        val failures = coroutineScope {
            listOf(async { !coverage.products && attempt { merchant.refresh() } },
                async { !coverage.tobacco && attempt { TobaccoCatalog(database.smokingDao()).refresh() } }).map { it.await() }
        }
        check(failures.none { it }) { "Bazı fiyat kaynakları doğrulanamadı. Önceki kayıtlar korunuyor." }
        lastRefresh = System.currentTimeMillis()
    }

    private data class Coverage(val products: Boolean, val tobacco: Boolean)

    private suspend fun refreshRemote(): Coverage {
        val json = JSONObject(client.request("/rest/v1/rpc/catalog_snapshot", JSONObject()))
        require(json.getInt("schema_version") == 1)
        val brands = mutableListOf<CigaretteBrandEntity>()
        val prices = mutableListOf<CigarettePriceEntity>()
        val priceArray = json.getJSONArray("prices")
        require(priceArray.length() <= 5000)
        for (index in 0 until priceArray.length()) {
            val p = priceArray.getJSONObject(index)
            val id = p.getString("brand_id").also { require(it.matches(Regex("[a-z0-9-]{2,100}"))) }
            val name = bounded(p.getString("brand_name"), 80)
            val size = p.getInt("pack_size").also { require(it in 1..100) }
            val price = p.getLong("price_kurus").also { require(it in 1..100_000_000) }
            val date = LocalDate.parse(p.getString("valid_from")).also { require(it.year in 2000..2100) }
            val checked = checkedAt(p.getString("checked_at"))
            val source = bounded(p.getString("source"), 100)
            val url = https(p.getString("source_url"))
            brands += CigaretteBrandEntity(id, name, bounded(p.getString("family"), 60), size)
            prices += CigarettePriceEntity("remote-${bounded(p.getString("id"), 100)}", id, price, date.toString(), checked, source, url)
        }
        val comparisons = mutableListOf<ComparisonPriceEntity>()
        val goods = json.getJSONArray("comparisons")
        require(goods.length() <= 2000)
        for (index in 0 until goods.length()) {
            val p = goods.getJSONObject(index)
            val amount = p.getLong("price_kurus").also { require(it in 1..10_000_000_000) }
            comparisons += ComparisonPriceEntity(bounded(p.getString("id"), 100), bounded(p.getString("title"), 160),
                bounded(p.getString("category"), 50), amount, bounded(p.getString("source"), 100),
                https(p.getString("url")), checkedAt(p.getString("checked_at")), p.getBoolean("available"))
        }
        database.withTransaction {
            val dao = database.smokingDao()
            dao.insertBrands(brands)
            prices.forEach { dao.insertPrice(it) }
            dao.upsertComparisons(comparisons)
        }
        val now = System.currentTimeMillis()
        val today = LocalDate.now().toString()
        return Coverage(comparisons.count { it.available && ComparisonRotation.fresh(it.checkedAt, now) } >= 10,
            prices.count { it.validFrom <= today && now - it.observedAt in 0..14 * ComparisonRotation.MAX_AGE_MILLIS } >= 10)
    }

    private fun bounded(value: String, max: Int): String = value.also { require(it.length in 1..max && it.none(Char::isISOControl)) }
    private suspend fun attempt(block: suspend () -> Unit): Boolean = try { block(); false }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { true }
    private fun https(value: String): String = value.also {
        val uri = URI(it)
        require(it.length < 2000 && uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null)
    }
    private fun checkedAt(value: String): Long = Instant.parse(value).toEpochMilli().also {
        require(it in 946684800000..System.currentTimeMillis() + 300_000)
    }
}
