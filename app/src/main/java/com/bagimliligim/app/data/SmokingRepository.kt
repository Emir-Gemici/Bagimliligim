package com.bagimliligim.app.data

import androidx.room.withTransaction
import com.bagimliligim.app.data.local.CigaretteBrandEntity
import com.bagimliligim.app.data.local.CigarettePriceEntity
import com.bagimliligim.app.data.local.SmokingDayEntity
import com.bagimliligim.app.data.local.SmokingLogEntity
import com.bagimliligim.app.data.local.SwipeDatabase
import com.bagimliligim.app.domain.Money
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

class SmokingRepository(private val database: SwipeDatabase) {
    val dao = database.smokingDao()
    suspend fun initialize() = dao.insertBrands(BrandCatalog.brands)

    suspend fun addCustomBrand(name: String, packSize: Int): String {
        val trimmed = name.trim()
        require(trimmed.length in 2..80 && packSize in 1..100)
        val id = "custom-${UUID.randomUUID()}"
        dao.insertBrands(listOf(CigaretteBrandEntity(id, trimmed, "Diğer", packSize, true)))
        return id
    }

    suspend fun savePrice(brandId: String, packPriceKurus: Long, validFrom: LocalDate): CigarettePriceEntity {
        require(packPriceKurus in 1..100_000_000 && validFrom <= LocalDate.now())
        requireNotNull(dao.brand(brandId))
        val price = CigarettePriceEntity(UUID.randomUUID().toString(), brandId, packPriceKurus,
            validFrom.toString(), System.currentTimeMillis(), "Kendi girdiğin fiyat", "")
        dao.insertPrice(price)
        return price
    }

    /** Snapshot the historical price transactionally. No price sync ever updates this table. */
    suspend fun record(id: String, brandId: String, count: Int, date: LocalDate, manualPriceKurus: Long? = null) {
        require(count in 1..200 && date <= LocalDate.now() && date >= LocalDate.of(2000, 1, 1))
        database.withTransaction {
            if (dao.log(id) != null) return@withTransaction
            val brand = requireNotNull(dao.brand(brandId)) { "Marka bulunamadı." }
            val price = if (manualPriceKurus != null) savePrice(brandId, manualPriceKurus, date)
                else requireNotNull(dao.priceOn(brandId, date.toString())) { "Bu tarih için paket fiyatını gir." }
            val time = if (date == LocalDate.now()) System.currentTimeMillis()
                else date.atTime(ZonedDateTime.now().toLocalTime()).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            dao.insertLog(SmokingLogEntity(id, brandId, brand.name, count, brand.packSize, price.packPriceKurus,
                Money.costMicros(price.packPriceKurus, brand.packSize, count), price.id, price.source, time, date.toString()))
            dao.markDay(SmokingDayEntity(date.toString()))
        }
    }

    suspend fun markSmokeFree(date: LocalDate) {
        require(date <= LocalDate.now() && date >= LocalDate.of(2000, 1, 1))
        database.withTransaction {
            require(dao.countOn(date.toString()) == 0) { "Bu gün için sigara kaydı var. Yanlışsa önce ilgili kaydı geri al." }
            dao.markDay(SmokingDayEntity(date.toString()))
        }
    }
    suspend fun delete(id: String) = dao.deleteLog(id)
}
