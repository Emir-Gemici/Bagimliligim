package com.bagimliligim.app.presentation

import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bagimliligim.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetLayoutTest {
    @Test fun bothRemoteViewsInflateAndKeepRefreshInsideMinimumHeight() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.runOnMainSync {
            listOf(R.layout.widget_swipe to 280, R.layout.widget_swipe_compact to 160).forEach { (layout, widthDp) ->
                val remote = RemoteViews(context.packageName, layout)
                remote.setTextViewText(R.id.widget_count, "142 / 200 Reels")
                remote.setTextViewText(R.id.widget_remaining, "58 kaydırma kaldı")
                remote.setTextViewText(R.id.widget_distance, "17,0 m kaydırıldı")
                remote.setTextViewText(R.id.widget_platforms, "Instagram 70\nTikTok 42 · YouTube 30")
                val view = remote.apply(context, FrameLayout(context))
                val density = context.resources.displayMetrics.density
                val width = (widthDp * density).toInt()
                val height = (180 * density).toInt()
                view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                view.layout(0, 0, width, height)
                val refresh = view.findViewById<TextView>(R.id.widget_refresh)
                val refreshPosition = IntArray(2).also(refresh::getLocationInWindow)
                val rootPosition = IntArray(2).also(view::getLocationInWindow)
                val bottom = refreshPosition[1] - rootPosition[1] + refresh.height
                assertTrue("Refresh must fit within the widget", bottom <= height)
                assertTrue("Refresh has a 48dp touch target", refresh.height >= (48 * density).toInt())
                assertEquals("142 / 200 Reels", view.findViewById<TextView>(R.id.widget_count).text.toString())
            }
        }
    }
}
