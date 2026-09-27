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
import android.widget.RemoteViews
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

class ClockWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        update(context, manager, ids)
        schedule(context)
    }

    override fun onEnabled(context: Context) { schedule(context) }

    override fun onDisabled(context: Context) {
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pendingIntent(context))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_TOGGLE_SECONDS -> {
                val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                prefs.edit().putBoolean(KEY_SECONDS, !prefs.getBoolean(KEY_SECONDS, true)).apply()
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
        private const val KEY_CLOCK_SIZE = "digital_clock_size"
        private const val KEY_TITLE_SIZE = "info_title_size"
        private const val KEY_VALUE1_SIZE = "info_value1_size"
        private const val KEY_VALUE2_SIZE = "info_value2_size"
        private const val KEY_VALUE3_SIZE = "info_value3_size"
        private const val KEY_VALUE4_SIZE = "info_value4_size"
        private const val LATITUDE = 35.6892
        private const val LONGITUDE = 51.3890
        private const val TIME_ZONE = "Asia/Tehran"

        const val CORNER_TOP_LEFT = 0
        const val CORNER_TOP_RIGHT = 1
        const val CORNER_BOTTOM_RIGHT = 2
        const val CORNER_BOTTOM_LEFT = 3

        fun update(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val bitmap = drawWidget(
                p.getFloat(KEY_CLOCK_SIZE, 76f),
                p.getFloat(KEY_TITLE_SIZE, 18f),
                floatArrayOf(
                    p.getFloat(KEY_VALUE1_SIZE, 30f),
                    p.getFloat(KEY_VALUE2_SIZE, 30f),
                    p.getFloat(KEY_VALUE3_SIZE, 30f),
                    p.getFloat(KEY_VALUE4_SIZE, 27f)
                )
            )
            ids.forEach { id ->
                val views = RemoteViews(context.packageName, R.layout.widget_clock)
                views.setImageViewBitmap(R.id.clock_image, bitmap)
                manager.updateAppWidget(id, views)
            }
        }

        private fun drawWidget(clockSize: Float, titleSize: Float, valueSizes: FloatArray): Bitmap {
            val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF10141C.toInt() }
            canvas.drawRect(0f, 0f, 512f, 512f, bg)
            val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone(TIME_ZONE))
            val today = solarDay(cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.DAY_OF_YEAR))
            val tomorrowCal = cal.clone() as java.util.Calendar
            tomorrowCal.add(java.util.Calendar.DAY_OF_YEAR, 1)
            val tomorrow = solarDay(tomorrowCal.get(java.util.Calendar.YEAR), tomorrowCal.get(java.util.Calendar.DAY_OF_YEAR))
            val clockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFF3F5F7.toInt()
                textSize = clockSize.coerceIn(42f, 130f)
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("%02d:%02d".format(cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE)), 256f, 142f, clockPaint)
            val delta = tomorrow.dayLengthMinutes - today.dayLengthMinutes
            val deltaText = if (delta > 0) "↑ " + formatDelta(delta) else if (delta < 0) "↓ " + formatDelta(-delta) else "بدون تغییر"
            val deltaColor = if (delta > 0) 0xFF6FD08C.toInt() else if (delta < 0) 0xFFE58A8A.toInt() else 0xFF9AA6B2.toInt()
            drawInfoCell(canvas, 128f, 278f, "طلوع", formatTime(today.sunrise), 0xFFFFC857.toInt(), titleSize, valueSizes[0])
            drawInfoCell(canvas, 384f, 278f, "غروب", formatTime(today.sunset), 0xFFFFC857.toInt(), titleSize, valueSizes[1])
            drawInfoCell(canvas, 128f, 430f, "طول روز", formatDuration(today.dayLengthMinutes), 0xFF8FA0B5.toInt(), titleSize, valueSizes[2])
            drawInfoCell(canvas, 384f, 430f, "فردا", deltaText, deltaColor, titleSize, valueSizes[3])
            return bitmap
        }

        private fun drawInfoCell(canvas: Canvas, x: Float, y: Float, title: String, value: String, valueColor: Int, titleSize: Float, valueSize: Float) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val ts = titleSize.coerceIn(11f, 34f)
            val vs = valueSize.coerceIn(16f, 48f)
            paint.textSize = ts
            paint.color = 0xFF9AA6B2.toInt()
            canvas.drawText(title, x, y, paint)
            paint.textSize = vs
            paint.color = valueColor
            canvas.drawText(value, x, y + vs * 0.95f, paint)
        }

        private data class SolarDay(canvas: Canvas, center: Float, length: Float, degrees: Float, width: Float, color: Int) {
            val angle = Math.toRadians(degrees - 90.0)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color = color
            paint.strokeWidth = width
            paint.strokeCap = Paint.Cap.ROUND
            canvas.drawLine(
                center, center,
                center + cos(angle).toFloat() * length,
                center + sin(angle).toFloat() * length,
                paint
            )
        }

        private data class SolarDay(val sunrise: Int, val sunset: Int, val dayLengthMinutes: Int)

        private fun solarDay(year: Int, dayOfYear: Int): SolarDay {
            val sunrise = solarEventMinutes(year, dayOfYear, true)
            val sunset = solarEventMinutes(year, dayOfYear, false)
            return SolarDay(sunrise, sunset, sunset - sunrise)
        }

        private fun solarEventMinutes(year: Int, dayOfYear: Int, sunrise: Boolean): Int {
            val gamma = 2.0 * Math.PI / 365.0 * (dayOfYear - 1)
            val equationOfTime = 229.18 * (0.000075 + 0.001868 * cos(gamma) - 0.032077 * sin(gamma) -
                0.014615 * cos(2 * gamma) - 0.040849 * sin(2 * gamma))
            val declination = 0.006918 - 0.399912 * cos(gamma) + 0.070257 * sin(gamma) -
                0.006758 * cos(2 * gamma) + 0.000907 * sin(2 * gamma) -
                0.002697 * cos(3 * gamma) + 0.00148 * sin(3 * gamma)
            val latitudeRad = Math.toRadians(LATITUDE)
            val zenithRad = Math.toRadians(90.833)
            val cosHourAngle = (cos(zenithRad) / (cos(latitudeRad) * cos(declination))) -
                (sin(latitudeRad) * sin(declination) / (cos(latitudeRad) * cos(declination)))
            if (cosHourAngle <= -1.0) return if (sunrise) 0 else 1439
            if (cosHourAngle >= 1.0) return 720
            val hourAngle = Math.toDegrees(acos(cosHourAngle))
            val solarNoon = 720.0 - 4.0 * LONGITUDE - equationOfTime
            val utcMinutes = if (sunrise) solarNoon - hourAngle * 4.0 else solarNoon + hourAngle * 4.0
            val tz = java.util.TimeZone.getTimeZone(TIME_ZONE)
            val reference = java.util.Calendar.getInstance(tz)
            val zoneOffsetMinutes = tz.getOffset(reference.timeInMillis) / 60000.0
            return floor(utcMinutes + zoneOffsetMinutes + 0.5).toInt().coerceIn(0, 1439)
        }

        private fun formatTime(minutes: Int) = "%02d:%02d".format(minutes / 60, minutes % 60)
        private fun formatDuration(minutes: Int) = "%02d:%02d".format(minutes / 60, minutes % 60)

        private fun formatDelta(minutes: Int): String =
            if (minutes >= 60) minutes.div(60).toString() + "س " + "%02d".format(minutes % 60) + "د"
            else minutes.toString() + " دقیقه"

        private fun schedule(context: Context) {
            val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val showSeconds = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_SECONDS, true)
            alarm.cancel(pendingIntent(context))
            val now = System.currentTimeMillis()
            val next = if (showSeconds) now + 1000L else now - (now % 60000L) + 60000L
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M)
                alarm.setExactAndAllowWhileIdle(AlarmManager.RTC, next, pendingIntent(context))
            else
                alarm.setExact(AlarmManager.RTC, next, pendingIntent(context))
        }

        private fun pendingIntent(context: Context): PendingIntent =
            PendingIntent.getBroadcast(
                context, 1001,
                Intent(context, ClockWidgetProvider::class.java).setAction(ACTION_TICK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        private fun togglePendingIntent(context: Context): PendingIntent =
            PendingIntent.getBroadcast(
                context, 1002,
                Intent(context, ClockWidgetProvider::class.java).setAction(ACTION_TOGGLE_SECONDS),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
    }
}