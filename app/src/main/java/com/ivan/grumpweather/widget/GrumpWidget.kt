package com.ivan.grumpweather.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.ivan.grumpweather.MainActivity
import com.ivan.grumpweather.R
import com.ivan.grumpweather.data.Forecast
import com.ivan.grumpweather.data.Place
import com.ivan.grumpweather.data.Prefs
import com.ivan.grumpweather.data.WeatherApi
import com.ivan.grumpweather.grumpy.Grumpy
import com.ivan.grumpweather.grumpy.Mood
import com.ivan.grumpweather.ui.fmtTemp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random

/** Виджет на рабочий стол: обсирает погоду прямо на экране. */
class GrumpWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        refreshAsync(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            showStatus(context, "щас, бля…")
            refreshAsync(context)
        }
    }

    private fun refreshAsync(context: Context) {
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                fetchAndUpdate(app)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.ivan.grumpweather.WIDGET_REFRESH"

        private fun ids(context: Context): IntArray =
            AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, GrumpWidget::class.java))

        /** Приложение само получило свежую погоду — сразу кидаем её в виджеты, без лишнего запроса. */
        fun push(context: Context, place: Place, forecast: Forecast) {
            val ids = ids(context)
            if (ids.isEmpty()) return
            AppWidgetManager.getInstance(context).updateAppWidget(ids, weatherViews(context, place, forecast))
        }

        private suspend fun fetchAndUpdate(context: Context) {
            val ids = ids(context)
            if (ids.isEmpty()) return
            val mgr = AppWidgetManager.getInstance(context)
            val place = Prefs(context).place
            if (place == null) {
                mgr.updateAppWidget(ids, messageViews(context, "Где ты, блядь?", "Открой приложение и выбери город"))
                return
            }
            val forecast = withTimeoutOrNull(25_000L) {
                runCatching { WeatherApi.forecast(place.lat, place.lon) }.getOrNull()
            }
            if (forecast == null) {
                showStatus(context, "инет сдох 🤬")
                return
            }
            mgr.updateAppWidget(ids, weatherViews(context, place, forecast))
        }

        private fun showStatus(context: Context, text: String) {
            val ids = ids(context)
            if (ids.isEmpty()) return
            val v = RemoteViews(context.packageName, R.layout.widget_grump)
            v.setTextViewText(R.id.w_time, text)
            AppWidgetManager.getInstance(context).partiallyUpdateAppWidget(ids, v)
        }

        private fun weatherViews(context: Context, place: Place, f: Forecast): RemoteViews {
            val c = f.current
            val mood = Grumpy.classify(
                code = c.code, temp = c.temp, feels = c.feelsLike, humidity = c.humidity,
                wind = c.windMs, gust = c.gustMs, precip = c.precipitation,
            )
            val rnd = Random(System.nanoTime())
            val today = f.days.firstOrNull()
            val sub = buildString {
                append("ощущ. ").append(fmtTemp(c.feelsLike))
                if (today != null) {
                    append(" · ").append(fmtTemp(today.tMax)).append(" / ").append(fmtTemp(today.tMin))
                    if (today.precipProb >= 30) append(" · 💧").append(today.precipProb).append('%')
                }
            }

            val v = RemoteViews(context.packageName, R.layout.widget_grump)
            v.setInt(R.id.widget_root, "setBackgroundResource", background(mood, c.isDay))
            v.setTextViewText(R.id.w_city, place.name)
            v.setTextViewText(R.id.w_time, c.time.takeLast(5))
            v.setTextViewText(R.id.w_emoji, Grumpy.emoji(c.code, c.isDay))
            v.setTextViewText(R.id.w_temp, fmtTemp(c.temp))
            v.setTextViewText(R.id.w_headline, Grumpy.headline(mood, c.isDay, rnd))
            v.setTextViewText(R.id.w_sub, sub)
            bindClicks(context, v)
            return v
        }

        private fun messageViews(context: Context, title: String, sub: String): RemoteViews {
            val v = RemoteViews(context.packageName, R.layout.widget_grump)
            v.setInt(R.id.widget_root, "setBackgroundResource", R.drawable.widget_bg_default)
            v.setTextViewText(R.id.w_city, "Ворчливая погода")
            v.setTextViewText(R.id.w_time, "")
            v.setTextViewText(R.id.w_emoji, "🤷")
            v.setTextViewText(R.id.w_temp, "")
            v.setTextViewText(R.id.w_headline, title)
            v.setTextViewText(R.id.w_sub, sub)
            bindClicks(context, v)
            return v
        }

        private fun bindClicks(context: Context, v: RemoteViews) {
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val open = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                flags,
            )
            val refresh = PendingIntent.getBroadcast(
                context, 1,
                Intent(context, GrumpWidget::class.java).setAction(ACTION_REFRESH),
                flags,
            )
            v.setOnClickPendingIntent(R.id.widget_root, open)
            v.setOnClickPendingIntent(R.id.w_refresh, refresh)
        }

        private fun background(mood: Mood, isDay: Boolean): Int {
            if (!isDay) return R.drawable.widget_bg_night
            return when (mood) {
                Mood.THUNDER -> R.drawable.widget_bg_storm
                Mood.DOWNPOUR, Mood.RAIN, Mood.DRIZZLE, Mood.STORM_WIND -> R.drawable.widget_bg_rain
                Mood.FOG, Mood.DREARY, Mood.SWAMP -> R.drawable.widget_bg_grey
                Mood.SNOW, Mood.BLIZZARD, Mood.ICE, Mood.FROSTY, Mood.CHILLY -> R.drawable.widget_bg_snow
                Mood.EXTREME_COLD, Mood.COLD -> R.drawable.widget_bg_cold
                Mood.HOT, Mood.HAMSIN -> R.drawable.widget_bg_hot
                Mood.INFERNO, Mood.SAUNA -> R.drawable.widget_bg_inferno
                Mood.NICE, Mood.MILD -> R.drawable.widget_bg_nice
            }
        }
    }
}
