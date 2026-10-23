package com.bagimliligim.app.data

import com.bagimliligim.app.data.local.SwipeDao
import com.bagimliligim.app.data.local.SwipeLogEntity
import com.bagimliligim.app.domain.Analytics
import com.bagimliligim.app.domain.StatsPeriod
import com.bagimliligim.app.domain.SwipeBatch
import com.bagimliligim.app.domain.SwipeRepository
import java.time.LocalDate

class RoomSwipeRepository(private val dao: SwipeDao) : SwipeRepository {
    override fun observeTotals(start: LocalDate, end: LocalDate) = dao.observeTotals(start.toString(), end.toString())
    override fun observePlatforms(date: LocalDate) = dao.observePlatforms(date.toString())
    override fun observeAllTime() = dao.observeAllTime()
    override suspend fun totals(date: LocalDate) = dao.totals(date.toString())
    override fun observeBuckets(period: StatsPeriod, today: LocalDate) = Analytics.range(period, today).let { range ->
        when (period) {
            StatsPeriod.DAILY -> dao.observeHourly(today.toString())
            StatsPeriod.YEARLY -> dao.observeMonthly(range.start.toString(), range.end.toString())
            else -> dao.observeDaily(range.start.toString(), range.end.toString())
        }
    }
    override suspend fun save(batches: List<SwipeBatch>) = dao.insertBatches(batches.map {
        SwipeLogEntity(batchId = it.batchId, platform = it.platform, swipeCount = it.swipeCount,
            distanceMeters = it.distanceMeters, timestamp = it.timestamp,
            dateString = it.dateString, hourOfDay = it.hourOfDay)
    })
}
