package com.bagimliligim.app.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.Locale

object Money {
    fun parseKurus(input: String): Long? = runCatching {
        val decimal = BigDecimal(input.trim().replace(',', '.'))
        require(decimal > BigDecimal.ZERO && decimal <= BigDecimal("1000000"))
        decimal.movePointRight(2).longValueExact()
    }.getOrNull()

    fun costMicros(packPriceKurus: Long, packSize: Int, count: Int): Long {
        require(packPriceKurus in 1..100_000_000 && packSize in 1..100 && count in 1..200)
        return BigDecimal(packPriceKurus).multiply(BigDecimal(10_000)).multiply(BigDecimal(count))
            .divide(BigDecimal(packSize), 0, RoundingMode.HALF_UP).longValueExact()
    }

    fun formatMicros(value: Long): String = String.format(Locale.forLanguageTag("tr-TR"),
        "%,.2f ₺", BigDecimal(value).movePointLeft(6))
    fun formatKurus(value: Long): String = formatMicros(Math.multiplyExact(value, 10_000))
}

data class SmokingTotals(val cigarettes: Int = 0, val costMicros: Long = 0, val days: Int = 0)
data class SmokingBucket(val date: String, val cigarettes: Int, val costMicros: Long)
data class BrandSpending(val brandId: String, val name: String, val cigarettes: Int, val costMicros: Long)

/** Equal observation windows are necessary: a missing entry is never a smoke-free day. */
object Reduction {
    fun percent(previous: Int, current: Int, previousDays: Int, currentDays: Int): Int? {
        if (previous <= 0 || previousDays != 7 || currentDays != 7) return null
        return ((previous - current) * 100.0 / previous).toInt().coerceIn(-999, 100)
    }
    fun rankTitle(percent: Int?): String = when {
        percent == null -> "Kendi ritminde"
        percent >= 50 -> "Büyük adım"
        percent >= 25 -> "İstikrarlı adım"
        percent > 0 -> "İlk adım"
        else -> "Yeni bir başlangıç"
    }
}

data class SmokingPeriod(val start: LocalDate, val end: LocalDate)
