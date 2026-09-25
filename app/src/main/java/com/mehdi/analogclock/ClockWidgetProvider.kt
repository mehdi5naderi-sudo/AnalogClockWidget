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
                update(context, manager, ids); schedule(context)
            }
            ACTION_TICK -> {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(ComponentName(context, ClockWidgetProvider::class.java))
                update(context, manager, ids); schedule(context)
            }
        }
    }

    companion object {
        private const val ACTION_TICK = "com.mehdi.analogclock.TICK"
        private const val ACTION_TOGGLE_SECONDS = "com.mehdi.analogclock.TOGGLE_SECONDS"
        private const val PREFS = "clock_widget_prefs"
        private const val KEY_SECONDS = "show_seconds"
        private const val KEY_NUMBER_SIZE = "number_size"
        private const val KEY_POS_12 = "pos_12"
        private const val KEY_POS_3 = "pos_3"
        private const val KEY_POS_6 = "pos_6"
        private const val KEY_POS_9 = "pos_9"
        private const val LATITUDE = 35.6892
        private const val LONGITUDE = 51.3890
        private const val TIME_ZONE = "Asia/Tehran"

        const val CORNER_TOP_LEFT = 0
        const val CORNER_TOP_RIGHT = 1
        const val CORNER_BOTTOM_RIGHT = 2
        const val CORNER_BOTTOM_LEFT = 3

        fun update(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val bitmap = drawClock(
                prefs.getBoolean(KEY_SECONDS, true),
                prefs.getFloat(KEY_NUMBER_SIZE, 30f),
                intArrayOf(
                    prefs.getInt(KEY_POS_12, CORNER_TOP_LEFT),
                    prefs.getInt(KEY_POS_3, CORNER_TOP_RIGHT),
                    prefs.getInt(KEY_POS_6, CORNER_BOTTOM_RIGHT),
                    prefs.getInt(KEY_POS_9, CORNER_BOTTOM_LEFT)
                )
            )
            ids.forEach { id ->
                val views = RemoteViews(context.packageName, R.layout.widget_clock)
                views.setImageViewBitmap(R.id.clock_image, bitmap)
                views.setOnClickPendingIntent(R.id.clock_image, togglePendingIntent(context))
                manager.updateAppWidget(id, views)
            }
        }

        private fun drawClock(showSeconds: Boolean, numberSize: Float, positions: IntArray): Bitmap {
            val size = 512
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val center = size / 2f
            val radius = 184f
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            paint.style = Paint.Style.FILL
            paint.color = 0xFF10141C.toInt()
            canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
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
                    center + cos(angle).toFloat() * inner, center + sin(angle).toFloat() * inner,
                    center + cos(angle).toFloat() * outer, center + sin(angle).toFloat() * outer, paint
                )
            }

            val cal = java.util.Calendar.getInstance()
            val hour = cal.get(java.util.Calendar.HOUR)
            val minute = cal.get(java.util.Calendar.MINUTE)
            val second = cal.get(java.util.Calendar.SECOND)
            val millis = cal.get(java.util.Calendar.MILLISECOND)
            val today = solarDay(cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.DAY_OF_YEAR))
            val tomorrowCal = cal.clone() as java.util.Calendar
            tomorrowCal.add(java.util.Calendar.DAY_OF_YEAR, 1)
            val tomorrow = solarDay(tomorrowCal.get(java.util.Calendar.YEAR), tomorrowCal.get(java.util.Calendar.DAY_OF_YEAR))

            paint.style = Paint.Style.FILL
            paint.color = 0xCC111821.toInt()
            canvas.drawRoundRect(115f, 321f, 397f, 425f, 22f, 22f, paint)
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

            paint.textSize = 17f
            paint.color = 0xFFFFC857.toInt()
            canvas.drawText("☀", 151f, 346f, paint)
            paint.color = 0xFFF3F5F7.toInt()
            canvas.drawText(formatTime(today.sunrise), 194f, 347f, paint)
            paint.color = 0xFFFFC857.toInt()
            canvas.drawText("☀", 361f, 346f, paint)
            paint.color = 0xFFF3F5F7.toInt()
            canvas.drawText(formatTime(today.sunset), 318f, 347f, paint)

            paint.textSize = 16f
            paint.color = 0xFF8FA0B5.toInt()
            canvas.drawText("طول روز", center, 373f, paint)
            paint.textSize = 23f
            paint.color = 0xFFF3F5F7.toInt()
            canvas.drawText(formatDuration(today.dayLengthMinutes), center, 397f, paint)

            val delta = tomorrow.dayLengthMinutes - today.dayLengthMinutes
            val arrow = if (delta > 0) "↑" else if (delta < 0) "↓" else "—"
            val deltaText = if (delta == 0) "فردا  بدون تغییر" else "فردا  " + arrow + " " + formatDelta(abs(delta))
            paint.textSize = 15f
            paint.color = if (delta > 0) 0xFF6FD08C.toInt() else if (delta < 0) 0xFFE58A8A.toInt() else 0xFF9AA6B2.toInt()
            canvas.drawText(deltaText, center, 417f, paint)

            drawHand(canvas, center, 150f, (hour + minute / 60f) * 30f, 18f, 0xFFF3F5F7.toInt())
            drawHand(canvas, center, 170f, (minute + second / 60f) * 6f, 12f, 0xFFD7DEE8.toInt())
            if (showSeconds) drawHand(canvas, center, 178f, (second + millis / 1000f) * 6f, 5f, 0xFFE05B5B.toInt())

            paint.color = 0xFFE05B5B.toInt()
            canvas.drawCircle(center, center, 13f, paint)
            paint.color = 0xFFF3F5F7.toInt()
            canvas.drawCircle(center, center, 5f, paint)

            drawCornerNumbers(canvas, numberSize, positions)
            return bitmap
        }

        private fun drawCornerNumbers(canvas: Canvas, numberSize: Float, positions: IntArray) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color = 0xFFF3F5F7.toInt()
            paint.textSize = numberSize.coerceIn(16f, 56f)
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textAlign = Paint.Align.CENTER
            val anchors = arrayOf(
                floatArrayOf(72f, 62f), floatArrayOf(440f, 62f),
                floatArrayOf(440f, 458f), floatArrayOf(72f, 458f)
            )
            val baselineOffset = paint.textSize * 0.34f
            arrayOf("12", "3", "6", "9").forEachIndexed { index, label ->
                val a = anchors[positions[index].coerceIn(0, 3)]
                canvas.drawText(label, a[0], a[1] + baselineOffset, paint)
            }
        }

        private fun drawHand(canvas: Canvas, center: Float, length: Float, degrees: Float, width: Float, color: Int) {
            val angle = Math.toRadians(degrees - 90.0)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color = color; paint.strokeWidth = width; paint.strokeCap = Paint.Cap.ROUND
            canvas.drawLine(center, center, center + cos(angle).toFloat() * length, center + sin(angle).toFloat() * length, paint)
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
            if (minutes >= 60) (minutes / 60).toString() + "س " + "%02d".format(minutes % 60) + "د"
            else minutes.toString() + " دقیقه"

        private fun schedule(context: Context) {
            val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val showSeconds = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_SECONDS, true)
            alarm.cancel(pendingIntent(context))
            val now = System.currentTimeMillis()
            val next = if (showSeconds) now + 1000L else now - (now % 60000L) + 60000L
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M)
                alarm.setExactAndAllowWhileIdle(AlarmManager.RTC, next, pendingIntent(context))
            else alarm.setExact(AlarmManager.RTC, next, pendingIntent(context))
        }

        private fun pendingIntent(context: Context): PendingIntent =
            PendingIntent.getBroadcast(context, 1001, Intent(context, ClockWidgetProvider::class.java).setAction(ACTION_TICK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        private fun togglePendingIntent(context: Context): PendingIntent =
            PendingIntent.getBroadcast(context, 1002, Intent(context, ClockWidgetProvider::class.java).setAction(ACTION_TOGGLE_SECONDS),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}