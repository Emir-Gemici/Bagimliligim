package com.bagimliligim.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.widget.RemoteViews
import androidx.core.graphics.createBitmap
import androidx.core.graphics.toColorInt
import com.bagimliligim.app.AppContainer
import com.bagimliligim.app.R
import com.bagimliligim.app.domain.Analytics
import com.bagimliligim.app.domain.AppPlatform
import com.bagimliligim.app.presentation.MainActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

object WidgetRenderer {
    private val mutex = Mutex()
    suspend fun updateAll(context: Context, container: AppContainer) = mutex.withLock {
        val manager = AppWidgetManager.getInstance(context)
        val providers = listOf(SwipeWidgetProvider::class.java, CompactSwipeWidgetProvider::class.java)
        val widgets = providers.flatMap { provider ->
            manager.getAppWidgetIds(ComponentName(context, provider)).map { it to provider }
        }
        if (widgets.isEmpty()) return@withLock
        val prefs = container.settings.preferences.first()
        val today = LocalDate.now()
        val totals = container.repository.totals(today)
        val platforms = container.repository.observePlatforms(today).first().associate { it.platform to it.swipes }
        val ratio = totals.swipes.toFloat() / prefs.dailyQuota
        val accent = (when { ratio < .6f -> "#53E3AF"; ratio <= .9f -> "#FFC361"; else -> "#FF6B7D" }).toColorInt()
        val progress = createBitmap(600, 16)
        val canvas = Canvas(progress)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = "#35463D".toColorInt() }
        canvas.drawRoundRect(0f, 0f, 600f, 16f, 8f, 8f, paint)
        paint.color = accent
        if (ratio > 0) canvas.drawRoundRect(0f, 0f, 600f * ratio.coerceIn(0f, 1f), 16f, 8f, 8f, paint)
        widgets.forEach { (id, provider) ->
            val minWidth = manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
            val compact = if (minWidth > 0) minWidth < 230 else provider == CompactSwipeWidgetProvider::class.java
            val views = RemoteViews(context.packageName, if (compact) R.layout.widget_swipe_compact else R.layout.widget_swipe)
            val count = context.getString(R.string.widget_swipes, totals.swipes, prefs.dailyQuota)
            views.setTextViewText(R.id.widget_count, count)
            views.setTextViewText(R.id.widget_distance, context.getString(R.string.widget_distance, Analytics.distance(totals.distanceMeters)))
            views.setTextViewText(R.id.widget_remaining, if (totals.swipes <= prefs.dailyQuota)
                context.getString(R.string.widget_remaining, prefs.dailyQuota - totals.swipes)
            else context.getString(R.string.widget_over, totals.swipes - prefs.dailyQuota))
            views.setTextColor(R.id.widget_remaining, accent)
            views.setTextViewText(R.id.widget_platforms, context.getString(R.string.widget_platforms,
                platforms[AppPlatform.INSTAGRAM] ?: 0, platforms[AppPlatform.TIKTOK] ?: 0, platforms[AppPlatform.YOUTUBE] ?: 0))
            views.setImageViewBitmap(R.id.widget_progress, progress)
            views.setContentDescription(R.id.widget_progress, count)
            val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val refresh = PendingIntent.getBroadcast(context, id, Intent(context, provider)
                .setAction(SwipeWidgetProvider.ACTION_REFRESH), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_root, open)
            views.setOnClickPendingIntent(R.id.widget_refresh, refresh)
            manager.updateAppWidget(id, views)
        }
    }
}
