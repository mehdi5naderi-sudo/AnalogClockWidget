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
                val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Tehran"))
                val hour = cal.get(Calendar.HOUR_OF_DAY)
                val minute = cal.get(Calendar.MINUTE)
                val timeText = "%02d:%02d".format(hour, minute)
                views.setTextViewText(R.id.tv_time, timeText)

                // Try to show weather if available
                val weather = try {
                    WeatherData.read(context)
                } catch (_: Exception) {
                    null
                }

                if (weather != null) {
                    views.setTextViewText(R.id.tv_temp, "${weather.temperature}°")
                    views.setTextViewText(R.id.tv_condition, weatherLabel(weather.code))
                } else {
                    views.setTextViewText(R.id.tv_temp, "--°")
                    views.setTextViewText(R.id.tv_condition, "")
                }

                // Trigger background refresh (non-blocking)
                try {
                    WeatherData.refreshIfNeeded(context, force = false)
                } catch (_: Exception) {}

            } catch (e: Exception) {
                Log.e(TAG, "update content", e)
                views.setTextViewText(R.id.tv_time, "--:--")
                views.setTextViewText(R.id.tv_temp, "--°")
                views.setTextViewText(R.id.tv_condition, "خطا")
            }

            // Click listener
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
