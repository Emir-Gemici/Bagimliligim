package com.bagimliligim.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [SwipeLogEntity::class, CigaretteBrandEntity::class, CigarettePriceEntity::class,
    SmokingLogEntity::class, SmokingDayEntity::class, ComparisonPriceEntity::class], version = 2, exportSchema = true)
@TypeConverters(PlatformConverters::class)
abstract class SwipeDatabase : RoomDatabase() {
    abstract fun swipeDao(): SwipeDao
    abstract fun smokingDao(): SmokingDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS cigarette_brands (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, family TEXT NOT NULL, packSize INTEGER NOT NULL, custom INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS cigarette_prices (id TEXT NOT NULL PRIMARY KEY, brandId TEXT NOT NULL, packPriceKurus INTEGER NOT NULL, validFrom TEXT NOT NULL, observedAt INTEGER NOT NULL, source TEXT NOT NULL, sourceUrl TEXT NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_cigarette_prices_brandId_validFrom ON cigarette_prices (brandId, validFrom)")
                db.execSQL("CREATE TABLE IF NOT EXISTS smoking_logs (id TEXT NOT NULL PRIMARY KEY, brandId TEXT NOT NULL, brandName TEXT NOT NULL, count INTEGER NOT NULL, packSize INTEGER NOT NULL, packPriceKurus INTEGER NOT NULL, costMicros INTEGER NOT NULL, priceId TEXT NOT NULL, priceSource TEXT NOT NULL, timestamp INTEGER NOT NULL, date TEXT NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_smoking_logs_date ON smoking_logs (date)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_smoking_logs_brandId ON smoking_logs (brandId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS smoking_days (date TEXT NOT NULL PRIMARY KEY)")
                db.execSQL("CREATE TABLE IF NOT EXISTS comparison_prices (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, category TEXT NOT NULL, priceKurus INTEGER NOT NULL, source TEXT NOT NULL, url TEXT NOT NULL, checkedAt INTEGER NOT NULL, available INTEGER NOT NULL)")
            }
        }
    }
}
