package com.bagimliligim.app.service

import com.bagimliligim.app.domain.AppPlatform
import com.bagimliligim.app.domain.PreferencesRepository
import com.bagimliligim.app.domain.QuotaPolicy
import com.bagimliligim.app.domain.SwipeBatch
import com.bagimliligim.app.domain.SwipeBuffer
import com.bagimliligim.app.domain.SwipeRepository
import com.bagimliligim.app.domain.Totals
import com.bagimliligim.app.domain.UserPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** An application-owned single consumer serializes records, flushes and threshold checks. */
class TrackingCoordinator(
    private val repository: SwipeRepository,
    private val settings: PreferencesRepository,
    private val preferences: StateFlow<UserPreferences?>,
    scope: CoroutineScope,
    private val notifyQuota: suspend (Int, Float) -> Unit,
    private val error: MutableStateFlow<String?>,
    private val onFlushed: suspend () -> Unit,
) {
    private sealed interface Command {
        data class Record(val platform: AppPlatform, val timestamp: Long, val zone: ZoneId, val length: Float) : Command
        data class Flush(val completion: CompletableDeferred<Unit>? = null) : Command
    }
    private val commands = Channel<Command>(Channel.UNLIMITED)
    private val buffer = SwipeBuffer()
    private val unsaved = ArrayDeque<List<SwipeBatch>>()
    private var activeDate: LocalDate? = null
    private var today: Totals? = null
    private val checkedWarnings = mutableSetOf<Pair<LocalDate, Int>>()

    init {
        scope.launch {
            for (command in commands) {
                try {
                    when (command) {
                        is Command.Record -> recordInternal(command)
                        is Command.Flush -> flushInternal()
                    }
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (_: Exception) {
                    error.value = "Kayıtlar şu anda saklanamıyor. Boş alanı kontrol edip yeniden deneyin."
                } finally {
                    if (command is Command.Flush) command.completion?.complete(Unit)
                }
            }
        }
    }

    fun record(platform: AppPlatform, timestamp: Long) {
        val prefs = preferences.value ?: return
        if (!prefs.accessibilityConsent) return
        commands.trySend(Command.Record(platform, timestamp, ZoneId.systemDefault(), prefs.swipeLengthCm))
    }
    fun flush() { commands.trySend(Command.Flush()) }
    suspend fun flushAndAwait() {
        val completion = CompletableDeferred<Unit>()
        commands.send(Command.Flush(completion))
        completion.await()
    }

    private suspend fun recordInternal(command: Command.Record) {
        val date = Instant.ofEpochMilli(command.timestamp).atZone(command.zone).toLocalDate()
        if (activeDate != date) {
            flushInternal()
            activeDate = date
            today = null
            checkedWarnings.removeAll { it.first != date }
            reconcileTotals()
        }
        buffer.add(command.platform, command.timestamp, command.length, command.zone)
        today = today?.let { Totals(it.swipes + 1, it.distanceMeters + command.length / 100f) }
        // Always keep the swipe even if notification delivery or DataStore fails.
        if (buffer.size() >= 10) flushInternal()
        val prefs = preferences.value ?: return
        val total = today ?: return
        if (prefs.warningsEnabled) {
            val threshold = QuotaPolicy.threshold(total.swipes, prefs.dailyQuota)
            if (threshold != null && (date to threshold) !in checkedWarnings) {
                val claimed = settings.claimWarning(date, threshold)
                checkedWarnings.add(date to threshold)
                if (threshold == 100) checkedWarnings.add(date to 80)
                if (claimed) notifyQuota(threshold, total.distanceMeters)
            }
        }
    }

    private suspend fun reconcileTotals() {
        val date = activeDate ?: return
        if (today != null) return
        // A failed write may already have committed. Wait for idempotent retries before
        // combining persisted totals with memory; otherwise the same UUID can be counted twice.
        if (unsaved.any { batch -> batch.any { it.dateString == date.toString() } }) return
        try {
            val saved = repository.totals(date)
            val buffered = buffer.totals(date.toString())
            today = Totals(saved.swipes + buffered.swipes, saved.distanceMeters + buffered.distanceMeters)
            if (unsaved.isEmpty()) error.value = null
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) {
            error.value = "Günlük toplam okunamıyor. Kaydırmalar korunuyor; toplam doğrulanana kadar bütçe uyarıları bekletilecek."
        }
    }

    private suspend fun flushInternal() {
        val drained = buffer.drain()
        if (drained.isNotEmpty()) unsaved.addLast(drained)
        var wrote = false
        while (unsaved.isNotEmpty()) {
            val next = unsaved.first()
            var saved = false
            for (attempt in 0..2) {
                try {
                    repository.save(next)
                    saved = true
                    break
                } catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { if (attempt < 2) delay(250L shl attempt) }
            }
            if (!saved) {
                error.value = "Son kaydırmalar bellekte bekliyor. Depolama alanını kontrol edin ve Yenile’ye dokunun."
                break
            }
            unsaved.removeFirst()
            wrote = true
            error.value = null
        }
        reconcileTotals()
        // Widget failures must never enqueue a committed batch a second time.
        if (wrote) {
            try { onFlushed() } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { error.value = "Kayıtlar saklandı; bileşeni Yenile ile güncelleyebilirsiniz." }
        }
    }
}
