package com.bagimliligim.app.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bagimliligim.app.AppContainer
import com.bagimliligim.app.domain.Analytics
import com.bagimliligim.app.domain.ChartPoint
import com.bagimliligim.app.domain.PlatformTotal
import com.bagimliligim.app.domain.StatsPeriod
import com.bagimliligim.app.domain.Totals
import com.bagimliligim.app.domain.UserPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime

data class DashboardData(
    val today: Totals = Totals(),
    val platforms: List<PlatformTotal> = emptyList(),
    val week: Totals = Totals(),
    val allTime: Totals = Totals(),
    val chart: List<ChartPoint> = emptyList(),
    val date: LocalDate = LocalDate.now(),
    val period: StatsPeriod = StatsPeriod.DAILY,
    val loaded: Boolean = false,
)
data class SwipeUiState(
    val preferences: UserPreferences? = null,
    val data: DashboardData = DashboardData(),
    val error: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class SwipeViewModel(private val container: AppContainer) : ViewModel() {
    private val date = MutableStateFlow(LocalDate.now())
    private val period = MutableStateFlow(StatsPeriod.DAILY)
    private val operationError = MutableStateFlow<String?>(null)
    private val dashboard = combine(date, period) { day, selection -> day to selection }.flatMapLatest { (day, selection) ->
        combine(
            container.repository.observeTotals(day, day),
            container.repository.observePlatforms(day),
            container.repository.observeTotals(day.minusDays(6), day),
            container.repository.observeAllTime(),
            container.repository.observeBuckets(selection, day),
        ) { today, platforms, week, all, buckets ->
            DashboardData(today, platforms, week, all, Analytics.points(selection, day, buckets), day, selection, true)
        }
    }.catch {
        operationError.value = "İstatistikler okunamadı. Uygulamayı yeniden açarak tekrar deneyin."
        emit(DashboardData())
    }
    val state = combine(container.preferences, dashboard, container.storageError, operationError) { prefs, data, storage, operation ->
        SwipeUiState(prefs, data, operation ?: storage)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SwipeUiState())

    init {
        viewModelScope.launch {
            while (true) {
                val now = ZonedDateTime.now()
                val nextDay = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
                delay(Duration.between(now, nextDay).toMillis().coerceAtLeast(1_000))
                date.value = LocalDate.now()
            }
        }
    }
    fun onResume() { date.value = LocalDate.now(); container.tracker.flush() }
    fun selectPeriod(value: StatsPeriod) { period.value = value }
    private fun update(block: suspend () -> Unit) {
        viewModelScope.launch {
            try { block(); operationError.value = null }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { operationError.value = "Değişiklik saklanamadı. Lütfen yeniden deneyin." }
        }
    }
    fun setQuota(value: Int) = update { container.settings.setQuota(value); refreshWidget() }
    fun setLength(value: Float) = update { container.settings.setSwipeLength(value) }
    fun setWarnings(value: Boolean) = update { container.settings.setWarnings(value) }
    fun setAppearance(dark: Boolean, dynamic: Boolean) = update { container.settings.setAppearance(dark, dynamic) }
    fun completeOnboarding(consent: Boolean, done: () -> Unit) = update {
        container.settings.completeOnboarding(consent)
        done()
    }
    fun refresh() = update { container.tracker.flushAndAwait(); refreshWidget() }
    private suspend fun refreshWidget() = com.bagimliligim.app.widget.WidgetRenderer.updateAll(appContext, container)
    private lateinit var appContext: android.content.Context

    class Factory(private val context: android.content.Context, private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(SwipeViewModel::class.java))
            return SwipeViewModel(container).also { it.appContext = context.applicationContext } as T
        }
    }
}
