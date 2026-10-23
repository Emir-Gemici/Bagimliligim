package com.bagimliligim.app.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bagimliligim.app.data.local.SwipeDatabase
import com.bagimliligim.app.data.local.SwipeLogEntity
import com.bagimliligim.app.domain.AppPlatform
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SwipeDaoTest {
    private lateinit var db: SwipeDatabase
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, SwipeDatabase::class.java).build()
    }
    @After fun teardown() { db.close() }
    private fun row(id: String, date: String, hour: Int, platform: AppPlatform = AppPlatform.INSTAGRAM) =
        SwipeLogEntity(batchId = id, platform = platform, swipeCount = 10, distanceMeters = 1.2f,
            timestamp = 1000, dateString = date, hourOfDay = hour)

    @Test fun retryIsIdempotentAndAggregationsRespectBoundaries() = runTest {
        val dao = db.swipeDao()
        val batch = listOf(row("a", "2026-10-15", 23), row("b", "2026-10-16", 0),
            row("c", "2026-10-16", 1, AppPlatform.YOUTUBE), row("d", "2026-09-01", 12))
        dao.insertBatches(batch)
        dao.insertBatches(batch)
        assertEquals(40, dao.observeAllTime().first().swipes)
        assertEquals(20, dao.totals("2026-10-16").swipes)
        assertEquals(0, dao.totals("2026-10-17").swipes)
        assertEquals(2, dao.observePlatforms("2026-10-16").first().size)
        assertEquals(listOf("00", "01"), dao.observeHourly("2026-10-16").first().map { it.bucket })
        assertEquals(2, dao.observeDaily("2026-10-10", "2026-10-16").first().size)
        assertEquals(listOf("2026-09", "2026-10"), dao.observeMonthly("2026-01-01", "2026-10-16").first().map { it.bucket })
        assertEquals(4.8f, dao.observeAllTime().first().distanceMeters, .0001f)
    }
}
