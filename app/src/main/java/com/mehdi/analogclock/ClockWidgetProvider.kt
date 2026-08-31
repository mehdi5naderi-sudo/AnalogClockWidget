package com.mehdi.analogclock

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.SystemClock
import android.widget.RemoteViews
import kotlin.math.cos
import kotlin.math.sin

class ClockWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        update(context, manager, ids)
        schedule(context)
    }

    override fun onEnabled(context: Context) {
        schedule(context)
    }

    override fun onDisabled(context: Context) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.cancel(pendingIntent(context))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_TOGGLE_SECONDS -> {
                val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val enabled = !prefs.getBoolean(KEY_SECONDS, true)
                prefs.edit().putBoolean(KEY_SECONDS, enabled).apply()
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(ComponentName(context, ClockWidgetProvider::class.java))
                update(context, manager, ids)
                schedule(context)
            }
            ACTION_TICK -> {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(ComponentName(context, ClockWidgetProvider::class.java))
                update(context, manager, ids)
                schedule(context)
            }
        }
    }

    companion object {
        private const val ACTION_TICK = "com.mehdi.analogclock.TICK"
        private const val ACTION_TOGGLE_SECONDS = "com.mehdi.analogclock.TOGGLE_SECONDS"
        private const val PREFS = "clock_widget_prefs"
        private const val KEY_SECONDS = "show_seconds"

        fun update(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val showSeconds = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_SECONDS, true)
            val bitmap = drawClock(showSeconds)
            ids.forEach { id ->
                val views = RemoteViews(context.packageName, R.layout.widget_clock)
                views.setImageViewBitmap(R.id.clock_image, bitmap)
                views.setOnClickPendingIntent(R.id.clock_image, togglePendingIntent(context))
                manager.updateAppWidget(id, views)
            }
        }

        private fun drawClock(showSeconds: Boolean): Bitmap {
            val size = 512
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val center = size / 2f
            val radius = 235f
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            paint.color = 0xFF10141C.toInt()
            canvas.drawCircle(center, center, radius + 12f, paint)
            paint.color = 0xFF18202B.toInt()
            canvas.drawCircle(center, center, radius, paint)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 5f
            paint.color = 0xFF3B4655.toInt()
            canvas.drawCircle(center, center, radius, paint)

            paint.style = Paint.Style.FILL
            paint.strokeCap = Paint.Cap.ROUND
            for (i in 0 until 60) {
                val angle = Math.toRadians(i * 6.0)
                val outer = radius - 12f
                val inner = if (i % 5 == 0) radius - 34f else radius - 23f
                paint.strokeWidth = if (i % 5 == 0) 6f else 2.5f
                paint.color = if (i % 5 == 0) 0xFFE8EDF3.toInt() else 0xFF718096.toInt()
                canvas.drawLine(
                    center + cos(angle).toFloat() * inner,
                    center + sin(angle).toFloat() * inner,
                    center + cos(angle).toFloat() * outer,
                    center + sin(angle).toFloat() * outer,
                    paint
                )
            }

            val cal = java.util.Calendar.getInstance()
            val hour = cal.get(java.util.Calendar.HOUR)
            val minute = cal.get(java.util.Calendar.MINUTE)
            val second = cal.get(java.util.Calendar.SECOND)
            val millis = cal.get(java.util.Calendar.MILLISECOND)
            drawHand(canvas, center, 150f, (hour + minute / 60f) * 30f, 18f, 0xFFF3F5F7.toInt())
            drawHand(canvas, center, 195f, (minute + second / 60f) * 6f, 12f, 0xFFD7DEE8.toInt())
            if (showSeconds) {
                drawHand(canvas, center, 205f, (second + millis / 1000f) * 6f, 5f, 0xFFE05B5B.toInt())
            }

            paint.color = 0xFFE05B5B.toInt()
            canvas.drawCircle(center, center, 13f, paint)
            paint.color = 0xFFF3F5F7.toInt()
            canvas.drawCircle(center, center, 5f, paint)

            paint.color = 0xFFF3F5F7.toInt()
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textSize = 30f
            canvas.drawText("12", center, center - 178f, paint)
            canvas.drawText("6", center, center + 205f, paint)
            canvas.drawText("3", center + 190f, center + 10f, paint)
            canvas.drawText("9", center - 190f, center + 10f, paint)
            return bitmap
        }

        private fun drawHand(canvas: Canvas, center: Float, length: Float, degrees: Float, width: Float, color: Int) {
            val angle = Math.toRadians(degrees - 90.0)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color = color
            paint.strokeWidth = width
            paint.strokeCap = Paint.Cap.ROUND
            canvas.drawLine(center, center, center + cos(angle).toFloat() * length, center + sin(angle).toFloat() * length, paint)
        }

        private fun schedule(context: Context) {
            val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val showSeconds = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_SECONDS, true)
            alarm.cancel(pendingIntent(context))
            if (showSeconds) {
                val next = System.currentTimeMillis() + 1000L
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    alarm.setExactAndAllowWhileIdle(AlarmManager.RTC, next, pendingIntent(context))
                } else {
                    alarm.setExact(AlarmManager.RTC, next, pendingIntent(context))
                }
            } else {
                val now = System.currentTimeMillis()
                val next = now - (now % 60000L) + 60000L
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    alarm.setExactAndAllowWhileIdle(AlarmManager.RTC, next, pendingIntent(context))
                } else {
                    alarm.setExact(AlarmManager.RTC, next, pendingIntent(context))
                }
            }
        }

        private fun pendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, ClockWidgetProvider::class.java).setAction(ACTION_TICK)
            return PendingIntent.getBroadcast(
                context, 1001, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun togglePendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, ClockWidgetProvider::class.java).setAction(ACTION_TOGGLE_SECONDS)
            return PendingIntent.getBroadcast(
                context, 1002, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
