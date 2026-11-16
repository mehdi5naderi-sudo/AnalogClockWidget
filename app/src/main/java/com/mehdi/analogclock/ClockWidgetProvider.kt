package com.mehdi.analogclock

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import java.util.Calendar
import java.util.TimeZone

class ClockWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        update(context, manager, ids)
        schedule(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, manager, appWidgetId, newOptions)
        update(context, manager, intArrayOf(appWidgetId))
    }

    override fun onEnabled(context: Context) {
        refreshAll(context)
    }

    override fun onDisabled(context: Context) {
        try {
            (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
                .cancel(pendingIntent(context))
        } catch (_: Exception) {}
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_REFRESH -> {
                try {
                    WeatherData.invalidate(context)
                    WeatherData.refreshIfNeeded(context, force = true)
                } catch (_: Exception) {}
                refreshAll(context)
            }
            ACTION_TICK,
            Intent.ACTION_USER_PRESENT,
            Intent.ACTION_SCREEN_ON,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_BOOT_COMPLETED -> refreshAll(context)
        }
    }

    companion object {
        private const val TAG = "ClockWidget"
        private const val ACTION_TICK = "com.mehdi.analogclock.TICK"
        private const val ACTION_REFRESH = "com.mehdi.analogclock.REFRESH"
        private const val PREFS = "clock_widget_prefs"

        private fun pd(n: Int): String = SolarCalculator.toPersianDigits(n.toString())
        private fun pd(s: String): String = SolarCalculator.toPersianDigits(s)

        private fun refreshAll(context: Context) {
            try {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(
                    ComponentName(context, ClockWidgetProvider::class.java)
                )
                if (ids.isNotEmpty()) {
                    update(context, manager, ids)
                    schedule(context)
                }
            } catch (e: Exception) {
                Log.e(TAG, "refreshAll", e)
            }
        }

        fun update(context: Context, manager: AppWidgetManager, ids: IntArray) {
            if (ids.isEmpty()) return

            val views = RemoteViews(context.packageName, R.layout.widget_clock)

            try {
                val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val showJalali = p.getBoolean("show_jalali", true)
                val showGregorian = p.getBoolean("show_gregorian", true)
                val showSolar = p.getBoolean("show_solar", true)
                val showHourly = p.getBoolean("show_hourly", true)
                val showDaily = p.getBoolean("show_daily", true)
                val use24h = p.getBoolean("use_24h", true)
                val backgroundAlpha = p.getFloat("background_alpha", 100f).coerceIn(0f, 100f)

                val lat = p.getString("location_lat", null)?.toDoubleOrNull() ?: SolarCalculator.LATITUDE
                val lon = p.getString("location_lon", null)?.toDoubleOrNull() ?: SolarCalculator.LONGITUDE

                val cal = Calendar.getInstance(TimeZone.getTimeZone(SolarCalculator.TIME_ZONE))
                val year = cal.get(Calendar.YEAR)
                val dayOfYear = cal.get(Calendar.DAY_OF_YEAR)
                val month = cal.get(Calendar.MONTH) + 1
                val day = cal.get(Calendar.DAY_OF_MONTH)
                val hour = cal.get(Calendar.HOUR_OF_DAY)
                val minute = cal.get(Calendar.MINUTE)
                val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)

                // Background
                val alpha = ((backgroundAlpha / 100f) * 255f).toInt().coerceIn(0, 255)
                views.setInt(R.id.widget_root, "setBackgroundColor", Color.argb(alpha, 11, 15, 23))

                // Per-element font sizes (0 means keep XML default)
                fun applySize(viewId: Int, key: String, defaultSize: Float) {
                    val configured = p.getFloat(key, 0f)
                    val size = if (configured > 0f) configured else defaultSize
                    views.setTextViewTextSize(
                        viewId,
                        android.util.TypedValue.COMPLEX_UNIT_SP,
                        size
                    )
                }
                applySize(R.id.tv_time, "digital_clock_size", 28f)
                applySize(R.id.tv_condition, "condition_size", 12f)
                applySize(R.id.tv_meta, "meta_size", 10f)
                applySize(R.id.tv_dates, "date_size", 11f)
                applySize(R.id.tv_solar, "label_size", 11f)
                applySize(R.id.tv_hourly, "hourly_size", 11f)
                applySize(R.id.tv_daily, "daily_size", 11f)
                applySize(R.id.tv_notes_link, "notes_size", 12f)

                // Clock time
                val timeText = if (use24h) {
                    pd("%02d:%02d".format(hour, minute))
                } else {
                    val h12 = when {
                        hour == 0 -> 12
                        hour > 12 -> hour - 12
                        else -> hour
                    }
                    pd("%d:%02d".format(h12, minute))
                }
                views.setTextViewText(R.id.tv_time, timeText)

                // Weather
                var weather = try { WeatherData.read(context) } catch (_: Exception) { null }
                val needForce = weather == null || weather.hourly.isEmpty() || weather.daily.isEmpty()
                try { WeatherData.refreshIfNeeded(context, force = needForce) } catch (_: Exception) {}
                weather = try { WeatherData.read(context) } catch (_: Exception) { null }

                if (weather != null) {
                    // One line: temp · condition · cloud · wind · vis · AQI
                    val parts = mutableListOf<String>()
                    parts.add("${pd(weather.temperature)}°")
                    val label = weatherLabel(weather.code)
                    if (label.isNotEmpty()) parts.add(label)
                    // Always show wind
                    parts.add(if (weather.windSpeed > 0) "باد ${pd(weather.windSpeed)}" else "آرام")
                    if (weather.usAqi >= 0) parts.add("AQI ${pd(weather.usAqi)}")
                    views.setTextViewText(R.id.tv_condition, parts.joinToString(" · "))

                    // Times: obs · last update
                    val metaParts = mutableListOf<String>()
                    if (weather.obsTime.isNotBlank()) {
                        metaParts.add("داده ${pd(weather.obsTime)}")
                    }
                    if (weather.updatedAt > 0L) {
                        val u = Calendar.getInstance(TimeZone.getTimeZone(SolarCalculator.TIME_ZONE))
                        u.timeInMillis = weather.updatedAt
                        val stamp = pd("%02d:%02d".format(u.get(Calendar.HOUR_OF_DAY), u.get(Calendar.MINUTE)))
                        metaParts.add("آخرین $stamp")
                    }
                    views.setTextViewText(R.id.tv_meta, metaParts.joinToString(" · "))
                } else {
                    views.setTextViewText(R.id.tv_condition, "—")
                    views.setTextViewText(R.id.tv_meta, "")
                }

                // Dates
                val weekday = SolarCalculator.weekdayFa(dayOfWeek)
                val jalaliText = SolarCalculator.formatJalaliFull(year, month, day)
                val gregorianText = SolarCalculator.formatGregorianFa(year, month, day)
                val jalaliShort = jalaliText.substringBeforeLast(" ")
                val gregorianShort = gregorianText.substringBeforeLast(" ")
                val datesText = buildString {
                    append(weekday)
                    if (showJalali || showGregorian) append(" · ")
                    when {
                        showJalali && showGregorian -> append("$jalaliShort  $gregorianShort")
                        showJalali -> append(jalaliShort)
                        showGregorian -> append(gregorianShort)
                    }
                }
                views.setTextViewText(R.id.tv_dates, datesText)

                // Solar
                if (showSolar) {
                    val today = SolarCalculator.solarDay(year, dayOfYear, lat, lon)
                    val sunrise = pd(SolarCalculator.formatTime(today.sunrise, use24h))
                    val sunset = pd(SolarCalculator.formatTime(today.sunset, use24h))
                    views.setTextViewText(R.id.tv_solar, "$sunrise · $sunset")
                    views.setViewVisibility(R.id.tv_solar, View.VISIBLE)
                } else {
                    views.setViewVisibility(R.id.tv_solar, View.GONE)
                }

                // Hourly — two forecast hours on each line
                if (showHourly && weather != null && weather.hourly.isNotEmpty()) {
                    val lines = weather.hourly.take(6).chunked(2).map { pair ->
                        pair.asReversed().joinToString("  │  ") { item ->
                            val h = pd("%02d".format(item.hour))
                            val t = "${pd(item.temperature)}°"
                            val r = if (item.rainProb > 0) pd("${item.rainProb}٪") else "—"
                            "$r  $t  :$h"
                        }
                    }
                    views.setTextViewText(R.id.tv_hourly, lines.joinToString("\n"))
                    views.setViewVisibility(R.id.tv_hourly, View.VISIBLE)
                } else if (showHourly) {
                    views.setTextViewText(R.id.tv_hourly, "…")
                    views.setViewVisibility(R.id.tv_hourly, View.VISIBLE)
                } else {
                    views.setViewVisibility(R.id.tv_hourly, View.GONE)
                }

                // Daily — full weekday name, fixed DOW mapping
                // Calendar: Sun=1 .. Sat=7  →  list index: Sat=0, Sun=1, ... Fri=6
                // index = dayOfWeek % 7
                if (showDaily && weather != null && weather.daily.isNotEmpty()) {
                    val fullNames = listOf(
                        "شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه"
                    )
                    val startIdx = dayOfWeek % 7
                    val lines = weather.daily.take(3).mapIndexed { index, item ->
                        val name = fullNames.getOrElse((startIdx + index) % 7) { "·" }
                        val temps = "${pd(item.max)}/${pd(item.min)}"
                        val r = if (item.rainProb > 0) pd("${item.rainProb}٪") else "—"
                        "$name  $temps  $r"
                    }
                    views.setTextViewText(R.id.tv_daily, lines.joinToString("\n"))
                    views.setViewVisibility(R.id.tv_daily, View.VISIBLE)
                } else if (showDaily) {
                    views.setTextViewText(R.id.tv_daily, "…")
                    views.setViewVisibility(R.id.tv_daily, View.VISIBLE)
                } else {
                    views.setViewVisibility(R.id.tv_daily, View.GONE)
                }

            } catch (e: Exception) {
                Log.e(TAG, "update content", e)
                views.setTextViewText(R.id.tv_time, "--:--")
                views.setTextViewText(R.id.tv_condition, "خطا")
                views.setTextViewText(R.id.tv_meta, "")
                views.setTextViewText(R.id.tv_dates, "")
                views.setTextViewText(R.id.tv_solar, "")
                views.setTextViewText(R.id.tv_hourly, "")
                views.setTextViewText(R.id.tv_daily, "")
            }

            try {
                // Each interactive area has its own action.
                val notesIntent = Intent(context, NotesActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                val notesPi = PendingIntent.getActivity(
                    context, 2001, notesIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.tv_notes_link, notesPi)

                val settingsIntent = Intent(context, SettingsActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                val settingsPi = PendingIntent.getActivity(
                    context, 2002, settingsIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.clock_panel, settingsPi)

                fun refreshPendingIntent(requestCode: Int): PendingIntent {
                    val refreshIntent = Intent(context, ClockWidgetProvider::class.java)
                        .setAction(ACTION_REFRESH)
                    return PendingIntent.getBroadcast(
                        context, requestCode, refreshIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                }
                views.setOnClickPendingIntent(R.id.tv_hourly, refreshPendingIntent(2003))
                views.setOnClickPendingIntent(R.id.tv_daily, refreshPendingIntent(2004))
            } catch (_: Exception) {}

            try {
                manager.updateAppWidget(ids, views)
            } catch (e: Exception) {
                Log.e(TAG, "updateAppWidget", e)
            }
        }

        private fun weatherLabel(code: Int): String = when (code) {
            0 -> "صاف"
            1, 2 -> "کمی ابر"
            3 -> "ابری"
            45, 48 -> "مه/غبار"
            in 51..67, in 80..82 -> "باران"
            in 71..77, in 85..86 -> "برف"
            in 95..99 -> "رعد"
            else -> ""
        }

        private fun pendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, ClockWidgetProvider::class.java).setAction(ACTION_TICK)
            return PendingIntent.getBroadcast(
                context, 1001, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun schedule(context: Context) {
            try {
                val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val pi = pendingIntent(context)
                val cal = Calendar.getInstance()
                cal.add(Calendar.MINUTE, 1)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC, cal.timeInMillis, pi)
                } else {
                    am.setExact(AlarmManager.RTC, cal.timeInMillis, pi)
                }
            } catch (_: Exception) {}
        }
    }
}
