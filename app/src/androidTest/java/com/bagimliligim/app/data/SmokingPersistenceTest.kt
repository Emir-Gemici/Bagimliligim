package com.bagimliligim.app.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bagimliligim.app.data.local.CigarettePriceEntity
import com.bagimliligim.app.data.local.SwipeDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class SmokingPersistenceTest {
    @Test fun v1UpgradeKeepsEverySwipeAndAddsSmokingTables() = runTest {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val name = "migration-v2-${java.util.UUID.randomUUID()}"
        // Load the actual exported V1 schema, then let Room's production open path
        // migrate and validate the entire V2 schema on the device's real SQLite engine.
        val schema = JSONObject(instrumentation.context.assets.open(
            "com.bagimliligim.app.data.local.SwipeDatabase/1.json").bufferedReader().use { it.readText() }).getJSONObject("database")
        context.openOrCreateDatabase(name, 0, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (index in 0 until entities.length()) {
                val entity = entities.getJSONObject(index)
                val table = entity.getString("tableName")
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.getJSONArray("indices")
                for (j in 0 until indices.length()) old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (index in 0 until setup.length()) old.execSQL(setup.getString(index))
            old.execSQL("INSERT INTO swipe_logs(batchId,platform,swipeCount,distanceMeters,timestamp,dateString,hourOfDay) VALUES ('old','YOUTUBE',23,2.76,1000,'2026-10-01',13)")
            old.version = 1
        }
        val upgraded = Room.databaseBuilder(context, SwipeDatabase::class.java, name).addMigrations(SwipeDatabase.MIGRATION_1_2).build()
        try {
            val total = upgraded.swipeDao().totals("2026-10-01")
            assertEquals(23, total.swipes)
            assertEquals(2.76f, total.distanceMeters, .0001f)
            assertTrue(upgraded.smokingDao().logs("2000-01-01", "2099-12-31").first().isEmpty())
        } finally { upgraded.close(); context.deleteDatabase(name) }
    }

    @Test fun futurePricesAndCorrectionsNeverRevalueRecordedExpenses() = runTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, SwipeDatabase::class.java).build()
        try {
            val repository = SmokingRepository(db)
            repository.initialize()
            val brand = "winston-dark-blue"
            val date = LocalDate.of(2026, 1, 1)
            repository.record("first", brand, 3, date, 12000)
            repository.record("first", brand, 3, date, 12000)
            db.smokingDao().insertPrice(CigarettePriceEntity("2027", brand, 18000, "2027-01-01", 5000, "Test", "https://example.org/test"))
            repository.savePrice(brand, 14000, date)
            val original = db.smokingDao().logs("2026-01-01", "2026-12-31").first().single()
            assertEquals(12000L, original.packPriceKurus)
            assertEquals(18_000_000L, original.costMicros)
            assertEquals(3, db.smokingDao().totals("2026-01-01", "2026-12-31").first().cigarettes)
            assertEquals(18000L, db.smokingDao().priceOn(brand, "2027-01-02")!!.packPriceKurus)
            try {
                repository.markSmokeFree(date)
                fail("An existing consumption record cannot become a smoke-free day")
            } catch (_: IllegalArgumentException) { }
            assertEquals(3, db.smokingDao().countOn(date.toString()))
            repository.markSmokeFree(date.plusDays(1))
            assertEquals(2, db.smokingDao().totals("2026-01-01", "2026-12-31").first().days)
            assertEquals(0, db.smokingDao().buckets("2026-01-02", "2026-01-02").first().single().cigarettes)
        } finally { db.close() }
    }
}
