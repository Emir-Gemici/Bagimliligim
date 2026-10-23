package com.bagimliligim.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.bagimliligim.app.appContainer
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.CancellationException

open class SwipeWidgetProvider : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) refresh(context)
    }
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) { refresh(context) }
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) { refresh(context) }

    private fun refresh(context: Context) {
        val pending = goAsync()
        context.appContainer.scope.launch {
            try {
                withTimeout(8_000) {
                    context.appContainer.tracker.flushAndAwait()
                    WidgetRenderer.updateAll(context, context.appContainer)
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) {
                context.appContainer.storageError.value = "Bileşen güncellenemedi. Uygulamayı açıp yeniden deneyin."
            } finally { pending.finish() }
        }
    }
    companion object { const val ACTION_REFRESH = "com.bagimliligim.app.widget.REFRESH" }
}

class CompactSwipeWidgetProvider : SwipeWidgetProvider()
