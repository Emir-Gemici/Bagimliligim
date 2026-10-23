package com.bagimliligim.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bagimliligim.app.domain.BrandSpending
import com.bagimliligim.app.domain.SmokingBucket
import com.bagimliligim.app.domain.SmokingTotals
import kotlinx.coroutines.flow.Flow

@Dao
interface SmokingDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBrands(brands: List<CigaretteBrandEntity>)
    @Query("SELECT * FROM cigarette_brands ORDER BY family, name")
    fun brands(): Flow<List<CigaretteBrandEntity>>
    @Query("SELECT * FROM cigarette_brands WHERE id = :id")
    suspend fun brand(id: String): CigaretteBrandEntity?
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPrice(price: CigarettePriceEntity)
    @Query("SELECT * FROM cigarette_prices WHERE brandId = :brandId AND validFrom <= :date ORDER BY validFrom DESC, observedAt DESC LIMIT 1")
    suspend fun priceOn(brandId: String, date: String): CigarettePriceEntity?
    @Query("SELECT * FROM cigarette_prices ORDER BY validFrom DESC, observedAt DESC")
    fun prices(): Flow<List<CigarettePriceEntity>>
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLog(row: SmokingLogEntity)
    @Query("SELECT * FROM smoking_logs WHERE id = :id")
    suspend fun log(id: String): SmokingLogEntity?
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun markDay(day: SmokingDayEntity)
    @Query("SELECT COALESCE(SUM(count), 0) FROM smoking_logs WHERE date = :date")
    suspend fun countOn(date: String): Int
    @Query("DELETE FROM smoking_logs WHERE id = :id")
    suspend fun deleteLog(id: String)
    @Query("SELECT * FROM smoking_logs WHERE date BETWEEN :start AND :end ORDER BY timestamp DESC")
    fun logs(start: String, end: String): Flow<List<SmokingLogEntity>>
    @Query("SELECT COALESCE(SUM(count), 0) AS cigarettes, COALESCE(SUM(costMicros), 0) AS costMicros, (SELECT COUNT(*) FROM smoking_days WHERE date BETWEEN :start AND :end) AS days FROM smoking_logs WHERE date BETWEEN :start AND :end")
    fun totals(start: String, end: String): Flow<SmokingTotals>
    @Query("SELECT d.date AS date, COALESCE(SUM(l.count), 0) AS cigarettes, COALESCE(SUM(l.costMicros), 0) AS costMicros FROM smoking_days d LEFT JOIN smoking_logs l ON l.date = d.date WHERE d.date BETWEEN :start AND :end GROUP BY d.date ORDER BY d.date")
    fun buckets(start: String, end: String): Flow<List<SmokingBucket>>
    @Query("SELECT brandId, brandName AS name, SUM(count) AS cigarettes, SUM(costMicros) AS costMicros FROM smoking_logs WHERE date BETWEEN :start AND :end GROUP BY brandId, brandName ORDER BY costMicros DESC")
    fun spending(start: String, end: String): Flow<List<BrandSpending>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertComparisons(rows: List<ComparisonPriceEntity>)
    @Query("SELECT * FROM comparison_prices WHERE available = 1 ORDER BY category, id")
    fun comparisons(): Flow<List<ComparisonPriceEntity>>
}
