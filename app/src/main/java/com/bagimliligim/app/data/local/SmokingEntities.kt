package com.bagimliligim.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "cigarette_brands")
data class CigaretteBrandEntity(
    @PrimaryKey val id: String,
    val name: String,
    val family: String,
    val packSize: Int = 20,
    val custom: Boolean = false,
)

@Entity(tableName = "cigarette_prices", indices = [Index(value = ["brandId", "validFrom"])])
data class CigarettePriceEntity(
    @PrimaryKey val id: String,
    val brandId: String,
    val packPriceKurus: Long,
    val validFrom: String,
    val observedAt: Long,
    val source: String,
    val sourceUrl: String,
)

@Entity(tableName = "smoking_logs", indices = [Index("date"), Index("brandId")])
data class SmokingLogEntity(
    @PrimaryKey val id: String,
    val brandId: String,
    val brandName: String,
    val count: Int,
    val packSize: Int,
    val packPriceKurus: Long,
    val costMicros: Long,
    val priceId: String,
    val priceSource: String,
    val timestamp: Long,
    val date: String,
)

@Entity(tableName = "smoking_days")
data class SmokingDayEntity(@PrimaryKey val date: String)

@Entity(tableName = "comparison_prices")
data class ComparisonPriceEntity(
    @PrimaryKey val id: String,
    val title: String,
    val category: String,
    val priceKurus: Long,
    val source: String,
    val url: String,
    val checkedAt: Long,
    val available: Boolean,
)
