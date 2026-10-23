package com.bagimliligim.app.domain

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class AppPlatform(val title: String, val shortName: String) {
    INSTAGRAM("Instagram Reels", "Instagram"), TIKTOK("TikTok", "TikTok"), YOUTUBE("YouTube Shorts", "YouTube");

    companion object {
        fun fromPackage(name: String?): AppPlatform? = when (name) {
            "com.instagram.android" -> INSTAGRAM
            "com.zhiliaoapp.musically", "com.ss.android.ugc.trill" -> TIKTOK
            "com.google.android.youtube" -> YOUTUBE
            else -> null
        }
    }
}

data class UserPreferences(
    val dailyQuota: Int = 200,
    val swipeLengthCm: Float = 12f,
    val warningsEnabled: Boolean = true,
    val onboardingComplete: Boolean = false,
    val accessibilityConsent: Boolean = false,
    val dynamicColor: Boolean = false,
    val darkMode: Boolean = true,
    val onlinePrices: Boolean = false,
)

data class Totals(val swipes: Int = 0, val distanceMeters: Float = 0f)
data class PlatformTotal(val platform: AppPlatform, val swipes: Int, val distanceMeters: Float)
data class BucketTotal(val bucket: String, val swipes: Int, val distanceMeters: Float)
data class ChartPoint(val label: String, val fullLabel: String, val swipes: Int, val distanceMeters: Float)
enum class StatsPeriod(val title: String) { DAILY("Günlük"), WEEKLY("Haftalık"), MONTHLY("Aylık"), YEARLY("Yıllık") }
data class PeriodRange(val start: LocalDate, val end: LocalDate) {
    val days: Long get() = java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1
}

object Analytics {
    val turkish: Locale = Locale.forLanguageTag("tr-TR")
    fun range(period: StatsPeriod, today: LocalDate): PeriodRange = PeriodRange(when (period) {
        StatsPeriod.DAILY -> today
        StatsPeriod.WEEKLY -> today.minusDays(6)
        StatsPeriod.MONTHLY -> today.minusDays(29)
        StatsPeriod.YEARLY -> today.withDayOfYear(1)
    }, today)

    fun points(period: StatsPeriod, today: LocalDate, rows: List<BucketTotal>): List<ChartPoint> {
        val byKey = rows.associateBy { it.bucket }
        val range = range(period, today)
        val keys = when (period) {
            StatsPeriod.DAILY -> (0..23).map { it.toString().padStart(2, '0') }
            StatsPeriod.YEARLY -> (1..12).map { YearMonth.of(today.year, it).toString() }
            else -> (0 until range.days).map { range.start.plusDays(it).toString() }
        }
        return keys.map { key ->
            val row = byKey[key]
            val label = when (period) {
                StatsPeriod.DAILY -> key
                StatsPeriod.WEEKLY -> LocalDate.parse(key).format(DateTimeFormatter.ofPattern("EEE", turkish))
                StatsPeriod.MONTHLY -> LocalDate.parse(key).dayOfMonth.toString()
                StatsPeriod.YEARLY -> YearMonth.parse(key).format(DateTimeFormatter.ofPattern("MMM", turkish))
            }
            val full = when (period) {
                StatsPeriod.DAILY -> "$key:00"
                StatsPeriod.YEARLY -> YearMonth.parse(key).format(DateTimeFormatter.ofPattern("MMMM yyyy", turkish))
                else -> LocalDate.parse(key).format(DateTimeFormatter.ofPattern("d MMMM", turkish))
            }
            ChartPoint(label, full, row?.swipes ?: 0, row?.distanceMeters ?: 0f)
        }
    }
    fun distance(meters: Float): String = if (meters >= 1000f) {
        String.format(turkish, "%.2f km", meters / 1000f)
    } else String.format(turkish, "%.1f m", meters)

    fun milestone(meters: Float): String = when {
        meters >= 8848 -> "Başparmağınla Everest’in yüksekliğine ${(meters / 8848).toInt()} kez ulaştın."
        meters >= 1560 -> "Başparmağınla Boğaziçi Köprüsü uzunluğunu ${(meters / 1560).toInt()} kez katettin."
        meters >= 67 -> "Başparmağınla Galata Kulesi’nin yaklaşık yüksekliğini ${(meters / 67).toInt()} kez çıktın."
        meters > 0 -> "Galata Kulesi’nin yaklaşık 67 metrelik yüksekliğine ${distance((67 - meters).coerceAtLeast(0f))} kaldı."
        else -> "Küçük bir hareket, görünür bir alışkanlık. İlk kaydırmanla yolculuğun başlasın."
    }
}
