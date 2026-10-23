package com.bagimliligim.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bagimliligim.app.domain.BucketTotal
import com.bagimliligim.app.domain.PlatformTotal
import com.bagimliligim.app.domain.Totals
import kotlinx.coroutines.flow.Flow

@Dao
interface SwipeDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBatches(rows: List<SwipeLogEntity>)

    @Query("SELECT COALESCE(SUM(swipeCount), 0) AS swipes, COALESCE(SUM(distanceMeters), 0.0) AS distanceMeters FROM swipe_logs WHERE dateString BETWEEN :start AND :end")
    fun observeTotals(start: String, end: String): Flow<Totals>

    @Query("SELECT COALESCE(SUM(swipeCount), 0) AS swipes, COALESCE(SUM(distanceMeters), 0.0) AS distanceMeters FROM swipe_logs WHERE dateString = :date")
    suspend fun totals(date: String): Totals

    @Query("SELECT platform, SUM(swipeCount) AS swipes, SUM(distanceMeters) AS distanceMeters FROM swipe_logs WHERE dateString = :date GROUP BY platform")
    fun observePlatforms(date: String): Flow<List<PlatformTotal>>

    @Query("SELECT printf('%02d', hourOfDay) AS bucket, SUM(swipeCount) AS swipes, SUM(distanceMeters) AS distanceMeters FROM swipe_logs WHERE dateString = :date GROUP BY hourOfDay ORDER BY hourOfDay")
    fun observeHourly(date: String): Flow<List<BucketTotal>>

    @Query("SELECT dateString AS bucket, SUM(swipeCount) AS swipes, SUM(distanceMeters) AS distanceMeters FROM swipe_logs WHERE dateString BETWEEN :start AND :end GROUP BY dateString ORDER BY dateString")
    fun observeDaily(start: String, end: String): Flow<List<BucketTotal>>

    @Query("SELECT substr(dateString, 1, 7) AS bucket, SUM(swipeCount) AS swipes, SUM(distanceMeters) AS distanceMeters FROM swipe_logs WHERE dateString BETWEEN :start AND :end GROUP BY substr(dateString, 1, 7) ORDER BY bucket")
    fun observeMonthly(start: String, end: String): Flow<List<BucketTotal>>

    @Query("SELECT COALESCE(SUM(swipeCount), 0) AS swipes, COALESCE(SUM(distanceMeters), 0.0) AS distanceMeters FROM swipe_logs")
    fun observeAllTime(): Flow<Totals>
}
