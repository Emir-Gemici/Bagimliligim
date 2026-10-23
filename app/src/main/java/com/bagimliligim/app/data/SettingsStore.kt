package com.bagimliligim.app.data

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bagimliligim.app.domain.PreferencesRepository
import com.bagimliligim.app.domain.UserPreferences
import kotlinx.coroutines.flow.map
import java.time.LocalDate

private val Context.swipeDataStore by preferencesDataStore(
    name = "swipemeter_settings",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

class SettingsStore(context: Context) : PreferencesRepository {
    private val store = context.applicationContext.swipeDataStore
    private object Keys {
        val quota = intPreferencesKey("daily_quota")
        val length = floatPreferencesKey("swipe_length_cm")
        val warnings = booleanPreferencesKey("limit_warnings")
        val onboarded = booleanPreferencesKey("onboarding_complete")
        val consent = booleanPreferencesKey("accessibility_consent_v1")
        val dark = booleanPreferencesKey("dark_mode")
        val dynamic = booleanPreferencesKey("dynamic_color")
        val onlinePrices = booleanPreferencesKey("online_prices_v2")
        val warned80 = stringPreferencesKey("warned_80_date")
        val warned100 = stringPreferencesKey("warned_100_date")
    }
    override val preferences = store.data.map { p ->
        UserPreferences(
            dailyQuota = (p[Keys.quota] ?: 200).coerceIn(50, 1000),
            swipeLengthCm = (p[Keys.length] ?: 12f).let { if (it.isFinite()) it.coerceIn(3f, 25f) else 12f },
            warningsEnabled = p[Keys.warnings] ?: true,
            onboardingComplete = p[Keys.onboarded] ?: false,
            accessibilityConsent = p[Keys.consent] ?: false,
            darkMode = p[Keys.dark] ?: true,
            dynamicColor = p[Keys.dynamic] ?: false,
            onlinePrices = p[Keys.onlinePrices] ?: false,
        )
    }
    override suspend fun setQuota(value: Int) { store.edit { it[Keys.quota] = value.coerceIn(50, 1000) } }
    override suspend fun setSwipeLength(value: Float) {
        require(value.isFinite())
        store.edit { it[Keys.length] = value.coerceIn(3f, 25f) }
    }
    override suspend fun setWarnings(enabled: Boolean) { store.edit { it[Keys.warnings] = enabled } }
    override suspend fun setOnlinePrices(enabled: Boolean) { store.edit { it[Keys.onlinePrices] = enabled } }
    override suspend fun completeOnboarding(consent: Boolean) {
        store.edit { it[Keys.onboarded] = true; it[Keys.consent] = consent }
    }
    override suspend fun setAppearance(dark: Boolean, dynamic: Boolean) {
        store.edit { it[Keys.dark] = dark; it[Keys.dynamic] = dynamic }
    }
    override suspend fun claimWarning(date: LocalDate, threshold: Int): Boolean {
        require(threshold == 80 || threshold == 100)
        var claimed = false
        store.edit { p ->
            val key = if (threshold == 80) Keys.warned80 else Keys.warned100
            if (p[key] != date.toString()) {
                p[key] = date.toString()
                if (threshold == 100) p[Keys.warned80] = date.toString()
                claimed = true
            }
        }
        return claimed
    }
}
