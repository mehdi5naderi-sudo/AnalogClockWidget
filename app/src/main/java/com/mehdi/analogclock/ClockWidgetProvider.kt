package com.mehdi.analogclock

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
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
        schedule(context)
    }

    override fun onEnabled(context: Context) {
        refreshAll(context)
    }

    override fun onDisabled(context: Context) {
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
            .cancel(pendingIntent(context))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action
        if (action == ACTION_REFRESH) {
            WeatherData.refreshIfNeeded(context, force = true)
            refreshAll(context)
        } else if (action == ACTION_TICK ||
            action == Intent.ACTION_USER_PRESENT ||
            action == Intent.ACTION_SCREEN_ON ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == Intent.ACTION_BOOT_COMPLETED
        ) {
            refreshAll(context)
        }
    }

    companion object {
        private const val ACTION_TICK = "com.mehdi.analogclock.TICK"
        private const val ACTION_REFRESH = "com.mehdi.analogclock.REFRESH"
        private const val PREFS = "clock_widget_prefs"

        private fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, ClockWidgetProvider::class.java)
            )
            if (ids.isNotEmpty()) {
                update(context, manager, ids)
                schedule(context)
            } else {
                (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
                    .cancel(pendingIntent(context))
            }
        }

        fun update(context: Context, manager: AppWidgetManager, ids: IntArray) {
            if (ids.isEmpty()) return

            var weather = WeatherData.read(context)
            val needForce = weather == null || weather.hourly.size < 6
            WeatherData.refreshIfNeeded(context, force = needForce)
            weather = WeatherData.read(context)

            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

            val showJalali = p.getBoolean("show_jalali", true)
            val showGregorian = p.getBoolean("show_gregorian", true)
            val showSolar = p.getBoolean("show_solar", true)
            val showCurrentWeather = p.getBoolean("show_current_weather", true)
            val showHourly = p.getBoolean("show_hourly", true)
            val showDaily = p.getBoolean("show_daily", true)
            val showFeels = p.getBoolean("show_feels_like", true)
            val use24h = p.getBoolean("use_24h", true)

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

            val today = SolarCalculator.solarDay(year, dayOfYear, lat, lon)
            val jalaliText = SolarCalculator.formatJalaliFull(year, month, day)
            val gregorianText = SolarCalculator.formatGregorianFa(year, month, day)
            val weekday = SolarCalculator.weekdayFa(dayOfWeek)

            val timeText = if (use24h) {
                "%02d:%02d".format(hour, minute)
            } else {
                val h12 = when {
                    hour == 0 -> 12
                    hour > 12 -> hour - 12
                    else -> hour
                }
                "%d:%02d".format(h12, minute)
            }

            val views = RemoteViews(context.packageName, R.layout.widget_clock)

            // ===== Time =====
            views.setTextViewText(R.id.tv_time, timeText)

            // ===== Weekday =====
            views.setTextViewText(R.id.tv_weekday, weekday)

            // ===== Dates =====
            val jalaliShort = jalaliText.substringBeforeLast(" ")
            val gregorianShort = gregorianText.substringBeforeLast(" ")
            val datesText = when {
                showJalali && showGregorian -> "$jalaliShort  ·  $gregorianShort"
                showJalali -> jalaliShort
                showGregorian -> gregorianShort
                else -> ""
            }
            views.setTextViewText(R.id.tv_dates, datesText)
            views.setViewVisibility(R.id.tv_dates, if (datesText.isNotEmpty()) View.VISIBLE else View.GONE)

            // ===== Solar (Sunrise / Sunset) =====
            if (showSolar) {
                val sunrise = SolarCalculator.formatTime(today.sunrise, use24h)
                val sunset = SolarCalculator.formatTime(today.sunset, use24h)
                views.setTextViewText(R.id.tv_solar, "$sunrise  ·  $sunset")
                views.setViewVisibility(R.id.tv_solar, View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.tv_solar, View.GONE)
            }

            // ===== Current Weather =====
            if (showCurrentWeather && weather != null) {
                views.setTextViewText(R.id.tv_temp, "${weather.temperature}°")
                val condition = if (showFeels) {
                    "${weatherLabel(weather.code)}  ·  ${weather.feelsLike}°"
                } else {
                    weatherLabel(weather.code)
                }
                views.setTextViewText(R.id.tv_condition, condition)

                if (weather.updatedAt > 0L) {
                    val updatedCal = Calendar.getInstance(TimeZone.getTimeZone(SolarCalculator.TIME_ZONE))
                    updatedCal.timeInMillis = weather.updatedAt
                    val stamp = "%02d:%02d".format(
                        updatedCal.get(Calendar.HOUR_OF_DAY),
                        updatedCal.get(Calendar.MINUTE)
                    )
                    views.setTextViewText(R.id.tv_updated, "آخرین به‌روزرسانی: $stamp")
                } else {
                    views.setTextViewText(R.id.tv_updated, "")
                }

                views.setViewVisibility(R.id.tv_temp, View.VISIBLE)
                views.setViewVisibility(R.id.tv_condition, View.VISIBLE)
                views.setViewVisibility(R.id.tv_updated, View.VISIBLE)
            } else if (showCurrentWeather) {
                views.setTextViewText(R.id.tv_temp, "--")
                views.setTextViewText(R.id.tv_condition, "دریافت هوا…")
                views.setTextViewText(R.id.tv_updated, "")
                views.setViewVisibility(R.id.tv_temp, View.VISIBLE)
                views.setViewVisibility(R.id.tv_condition, View.VISIBLE)
                views.setViewVisibility(R.id.tv_updated, View.GONE)
            } else {
                views.setViewVisibility(R.id.tv_temp, View.GONE)
                views.setViewVisibility(R.id.tv_condition, View.GONE)
                views.setViewVisibility(R.id.tv_updated, View.GONE)
            }

            // ===== Hourly Forecast =====
            if (showHourly && weather != null && weather.hourly.isNotEmpty()) {
                val hourlyText = weather.hourly.take(6).joinToString("  ") { item ->
                    "${item.temperature}°"
                }
                views.setTextViewText(R.id.tv_hourly, hourlyText)
                views.setViewVisibility(R.id.tv_hourly_title, View.VISIBLE)
                views.setViewVisibility(R.id.tv_hourly, View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.tv_hourly_title, View.GONE)
                views.setViewVisibility(R.id.tv_hourly, View.GONE)
            }

            // ===== Daily Forecast =====
            if (showDaily && weather != null && weather.daily.isNotEmpty()) {
                val dayNames = listOf("ش", "ی", "د", "س", "چ", "پ", "ج")
                val startDow = (dayOfWeek + 5) % 7 // adjust if needed
                val dailyText = weather.daily.take(3).mapIndexed { index, item ->
                    val name = dayNames.getOrElse((startDow + index) % 7) { "·" }
                    "$name ${item.max}/${item.min}"
                }.joinToString("  ")
                views.setTextViewText(R.id.tv_daily, dailyText)
                views.setViewVisibility(R.id.tv_daily_title, View.VISIBLE)
                views.setViewVisibility(R.id.tv_daily, View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.tv_daily_title, View.GONE)
                views.setViewVisibility(R.id.tv_daily, View.GONE)
            }

            // Tap anywhere to refresh weather
            val refreshIntent = Intent(context, ClockWidgetProvider::class.java)
                .setAction(ACTION_REFRESH)
            val refreshPi = PendingIntent.getBroadcast(
                context, 1002, refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, refreshPi)

            manager.updateAppWidget(ids, views)
        }

        private fun weatherLabel(code: Int): String = when (code) {
            0 -> "صاف"
            1, 2 -> "کمی ابر"
            3 -> "ابری"
            45, 48 -> "مه"
            in 51..67, in 80..82 -> "باران"
            in 71..77, in 85..86 -> "برف"
            in 95..99 -> "رعد"
            else -> "—"
        }

        private fun pendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, ClockWidgetProvider::class.java).setAction(ACTION_TICK)
            return PendingIntent.getBroadcast(
                context, 1001, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun schedule(context: Context) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pi = pendingIntent(context)
            val cal = Calendar.getInstance()
            cal.add(Calendar.MINUTE, 1)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)

            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC, cal.timeInMillis, pi)
                } else {
                    am.setExact(AlarmManager.RTC, cal.timeInMillis, pi)
                }
            } catch (_: Exception) {
                am.set(AlarmManager.RTC, cal.timeInMillis, pi)
            }
        }
    }
}
