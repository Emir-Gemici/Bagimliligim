package com.bagimliligim.app.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bagimliligim.app.AppContainer
import com.bagimliligim.app.data.local.CigaretteBrandEntity
import com.bagimliligim.app.data.local.CigarettePriceEntity
import com.bagimliligim.app.data.local.ComparisonPriceEntity
import com.bagimliligim.app.data.local.SmokingLogEntity
import com.bagimliligim.app.domain.Analytics
import com.bagimliligim.app.domain.BrandSpending
import com.bagimliligim.app.domain.SmokingBucket
import com.bagimliligim.app.domain.SmokingTotals
import com.bagimliligim.app.domain.StatsPeriod
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
import java.time.LocalDate

data class SmokingWindow(
    val totals: SmokingTotals = SmokingTotals(),
    val logs: List<SmokingLogEntity> = emptyList(),
    val buckets: List<SmokingBucket> = emptyList(),
    val brands: List<BrandSpending> = emptyList(),
    val period: StatsPeriod = StatsPeriod.WEEKLY,
    val today: LocalDate = LocalDate.now(),
)
data class SmokingState(
    val brands: List<CigaretteBrandEntity> = emptyList(),
    val prices: List<CigarettePriceEntity> = emptyList(),
    val window: SmokingWindow = SmokingWindow(),
    val comparisons: List<ComparisonPriceEntity> = emptyList(),
    val error: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class SmokingViewModel(private val container: AppContainer) : ViewModel() {
    private val period = MutableStateFlow(StatsPeriod.WEEKLY)
    private val day = MutableStateFlow(LocalDate.now())
    private val error = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)
    val syncing = MutableStateFlow(false)
    val configured get() = container.catalog.configured
    private val dao = container.smoking.dao
    private val window = combine(period, day) { p, d -> p to d }.flatMapLatest { (p, d) ->
        val range = Analytics.range(p, d)
        val start = range.start.toString()
        val end = range.end.toString()
        combine(dao.totals(start, end), dao.logs(start, end), dao.buckets(start, end), dao.spending(start, end)) { totals, logs, buckets, brands ->
            SmokingWindow(totals, logs, buckets, brands, p, d)
        }
    }
    val state = combine(dao.brands(), dao.prices(), window, dao.comparisons(), error) { brands, prices, data, comparisons, problem ->
        SmokingState(brands, prices, data, comparisons, problem)
    }.catch { emit(SmokingState(error = "Kayıtlar okunamadı. Yeniden açarak dene.")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SmokingState())

    init { viewModelScope.launch { while (true) { delay(60_000); day.value = LocalDate.now() } } }
    fun selectPeriod(value: StatsPeriod) { period.value = value }
    fun onResume() {
        day.value = LocalDate.now()
        if (container.preferences.value?.onlinePrices == true && configured) refreshPrices(false)
    }
    private fun operation(block: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try { block(); error.value = null }
            catch (cancel: CancellationException) { throw cancel }
            catch (failure: IllegalArgumentException) { error.value = failure.message ?: "Girilen bilgileri kontrol et." }
            catch (_: Exception) { error.value = "İşlem tamamlanamadı. Yeniden dene." }
            finally { busy.value = false }
        }
    }
    fun record(id: String, brandId: String, count: Int, date: LocalDate, price: Long?, done: () -> Unit) = operation {
        container.smoking.record(id, brandId, count, date, price); done()
    }
    fun addBrand(name: String, size: Int, done: (String) -> Unit) = operation { done(container.smoking.addCustomBrand(name, size)) }
    fun smokeFree() = operation { container.smoking.markSmokeFree(LocalDate.now()) }
    fun delete(id: String) = operation { container.smoking.delete(id) }
    fun clearError() { error.value = null }
    fun setOnline(enabled: Boolean) = operation {
        container.settings.setOnlinePrices(enabled)
        if (enabled && configured) startPriceRefresh(true)
    }
    fun refreshPrices(force: Boolean = true) {
        if (container.preferences.value?.onlinePrices != true) return
        startPriceRefresh(force)
    }
    private fun startPriceRefresh(force: Boolean) {
        if (syncing.value) return
        syncing.value = true
        viewModelScope.launch {
            try { container.catalog.refresh(force); error.value = null }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { error.value = "Fiyatlar doğrulanamadı. Eski fiyatlar güncel olarak gösterilmiyor; ödediğin tutarı girebilirsin." }
            finally { syncing.value = false }
        }
    }
    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(SmokingViewModel::class.java))
            return SmokingViewModel(container) as T
        }
    }
}
