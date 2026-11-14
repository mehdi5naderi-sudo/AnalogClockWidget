package com.mehdi.analogclock

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
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
            ACTION_REFRESH, ACTION_TICK,
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

                // Time
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
                views.setTextViewText(R.id.tv_time, timeText)

                // Weather
                val weather = try { WeatherData.read(context) } catch (_: Exception) { null }
                if (weather != null) {
                    views.setTextViewText(R.id.tv_temp, "${weather.temperature}°")
                    views.setTextViewText(R.id.tv_condition, weatherLabel(weather.code))
                } else {
                    views.setTextViewText(R.id.tv_temp, "--°")
                    views.setTextViewText(R.id.tv_condition, "")
                }

                // Trigger background refresh
                try { WeatherData.refreshIfNeeded(context, force = false) } catch (_: Exception) {}

                // Dates + Weekday
                val weekday = SolarCalculator.weekdayFa(dayOfWeek)
                val jalaliText = SolarCalculator.formatJalaliFull(year, month, day)
                val gregorianText = SolarCalculator.formatGregorianFa(year, month, day)
                val jalaliShort = jalaliText.substringBeforeLast(" ")
                val gregorianShort = gregorianText.substringBeforeLast(" ")

                val datesText = buildString {
                    append(weekday)
                    if (showJalali || showGregorian) append("  ·  ")
                    when {
                        showJalali && showGregorian -> append("$jalaliShort  $gregorianShort")
                        showJalali -> append(jalaliShort)
                        showGregorian -> append(gregorianShort)
                    }
                }
                views.setTextViewText(R.id.tv_dates, datesText)

                // Sunrise / Sunset
                if (showSolar) {
                    val today = SolarCalculator.solarDay(year, dayOfYear, lat, lon)
                    val sunrise = SolarCalculator.formatTime(today.sunrise, use24h)
                    val sunset = SolarCalculator.formatTime(today.sunset, use24h)
                    views.setTextViewText(R.id.tv_solar, "$sunrise  ·  $sunset")
                    views.setViewVisibility(R.id.tv_solar, View.VISIBLE)
                } else {
                    views.setViewVisibility(R.id.tv_solar, View.GONE)
                }

            } catch (e: Exception) {
                Log.e(TAG, "update content", e)
                views.setTextViewText(R.id.tv_time, "--:--")
                views.setTextViewText(R.id.tv_temp, "--°")
                views.setTextViewText(R.id.tv_condition, "خطا")
                views.setTextViewText(R.id.tv_dates, "")
                views.setTextViewText(R.id.tv_solar, "")
            }

            // Click to refresh
            try {
                val intent = Intent(context, ClockWidgetProvider::class.java).setAction(ACTION_REFRESH)
                val pi = PendingIntent.getBroadcast(
                    context, 1002, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_root, pi)
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
            45, 48 -> "مه"
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
