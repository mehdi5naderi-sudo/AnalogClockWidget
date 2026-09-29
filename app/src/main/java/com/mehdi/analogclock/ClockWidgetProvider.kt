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
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.widget.RemoteViews
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.cos
import kotlin.math.sin

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
        cachedKey = null
        cachedBitmap = null
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TICK ||
            intent.action == Intent.ACTION_USER_PRESENT ||
            intent.action == Intent.ACTION_SCREEN_ON ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            refreshAll(context)
        }
    }

    companion object {
        private const val ACTION_TICK = "com.mehdi.analogclock.TICK"
        private const val PREFS = "clock_widget_prefs"

        private const val KEY_CLOCK_SIZE = "digital_clock_size"
        private const val KEY_DATE_SIZE = "date_size"
        private const val KEY_LABEL_SIZE = "label_size"

        private const val COLOR_BG = 0xFF0B0F17.toInt()
        private const val COLOR_RING = 0xFF1E2738.toInt()
        private const val COLOR_RING_STROKE = 0xFF2A3548.toInt()
        private const val COLOR_TEXT = 0xFFF3F5F7.toInt()
        private const val COLOR_MUTED = 0xFF8B9AAB.toInt()
        private const val COLOR_GOLD = 0xFFFFC857.toInt()
        private const val COLOR_ORANGE = 0xFFFF8A3D.toInt()
        private const val COLOR_DAY_ARC = 0x33FFC857
        private const val COLOR_MARKER = 0xFFFFFFFF.toInt()

        private const val SIZE = 512f
        private const val CX = 256f
        private const val CY = 256f
        private const val RADIUS = 198f

        @Volatile private var cachedKey: String? = null
        @Volatile private var cachedBitmap: Bitmap? = null

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

            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val clockSize = p.getFloat(KEY_CLOCK_SIZE, 0f)
            val dateSize = p.getFloat(KEY_DATE_SIZE, 0f)
            val labelSize = p.getFloat(KEY_LABEL_SIZE, 0f)

            val cal = Calendar.getInstance(TimeZone.getTimeZone(SolarCalculator.TIME_ZONE))
            val year = cal.get(Calendar.YEAR)
            val dayOfYear = cal.get(Calendar.DAY_OF_YEAR)
            val month = cal.get(Calendar.MONTH) + 1
            val day = cal.get(Calendar.DAY_OF_MONTH)
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            val minute = cal.get(Calendar.MINUTE)
            val currentMinutes = hour * 60 + minute

            val today = SolarCalculator.solarDay(year, dayOfYear)
            val jalaliText = SolarCalculator.formatJalaliShort(year, month, day)

            val key = buildString {
                append(year).append('-').append(dayOfYear).append('|')
                append(hour).append(':').append(minute).append('|')
                append(clockSize).append(',').append(dateSize).append(',').append(labelSize)
            }

            val bitmap = if (key == cachedKey && cachedBitmap != null) {
                cachedBitmap!!
            } else {
                val fresh = drawWidget(
                    today, currentMinutes, hour, minute,
                    jalaliText, clockSize, dateSize, labelSize
                )
                cachedKey = key
                cachedBitmap = fresh
                fresh
            }

            val views = RemoteViews(context.packageName, R.layout.widget_clock)
            views.setImageViewBitmap(R.id.clock_image, bitmap)
            manager.updateAppWidget(ids, views)
        }

        /**
         * Angle mapping:
         *   0°   = top     = 12:00 (noon)
         *  90°   = right   = 18:00
         * 180°   = bottom  = 00:00
         * 270°   = left    = 06:00
         * Clockwise from noon.
         */
        private fun minutesToAngle(minutes: Int): Float {
            // minutes from noon, then to degrees (clockwise)
            val fromNoon = (minutes - 720 + 1440) % 1440
            return fromNoon / 1440f * 360f
        }

        private fun angleToPoint(angleDeg: Float, radius: Float = RADIUS): Pair<Float, Float> {
            val rad = Math.toRadians(angleDeg.toDouble())
            val x = CX + (sin(rad) * radius).toFloat()
            val y = CY - (cos(rad) * radius).toFloat()
            return x to y
        }

        private fun drawWidget(
            today: SolarCalculator.SolarDay,
            currentMinutes: Int,
            hour: Int,
            minute: Int,
            jalaliText: String,
            clockSize: Float,
            dateSize: Float,
            labelSize: Float
        ): Bitmap {
            val bitmap = Bitmap.createBitmap(SIZE.toInt(), SIZE.toInt(), Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(COLOR_BG)

            // Outer soft ring fill
            canvas.drawCircle(CX, CY, RADIUS + 14f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_RING
                style = Paint.Style.STROKE
                strokeWidth = 28f
            })

            // Main circle stroke
            canvas.drawCircle(CX, CY, RADIUS, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_RING_STROKE
                style = Paint.Style.STROKE
                strokeWidth = 3.5f
            })

            // Daylight arc (sunrise → sunset)
            val sunriseAngle = minutesToAngle(today.sunrise)
            val sunsetAngle = minutesToAngle(today.sunset)
            val dayArcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_DAY_ARC
                style = Paint.Style.STROKE
                strokeWidth = 10f
                strokeCap = Paint.Cap.ROUND
            }
            // Canvas arc: 0° is 3-o'clock, positive is clockwise.
            // Our 0° is top (12 o'clock) = Canvas -90° or 270°.
            val startCanvas = sunriseAngle - 90f
            var sweep = sunsetAngle - sunriseAngle
            if (sweep < 0f) sweep += 360f
            canvas.drawArc(
                RectF(CX - RADIUS, CY - RADIUS, CX + RADIUS, CY + RADIUS),
                startCanvas, sweep, false, dayArcPaint
            )

            // Subtle hour ticks (0, 6, 12, 18)
            val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_MUTED
                strokeWidth = 2.5f
                strokeCap = Paint.Cap.ROUND
            }
            for (h in listOf(0, 6, 12, 18)) {
                val a = minutesToAngle(h * 60)
                val (x1, y1) = angleToPoint(a, RADIUS - 8f)
                val (x2, y2) = angleToPoint(a, RADIUS + 8f)
                canvas.drawLine(x1, y1, x2, y2, tickPaint)
            }

            // Sunrise marker + label
            drawSolarLabel(
                canvas, today.sunrise, "طلوع",
                SolarCalculator.formatTime(today.sunrise),
                COLOR_GOLD, labelSize, isSunrise = true
            )

            // Sunset marker + label
            drawSolarLabel(
                canvas, today.sunset, "غروب",
                SolarCalculator.formatTime(today.sunset),
                COLOR_ORANGE, labelSize, isSunrise = false
            )

            // Current time marker (simple dot)
            val currentAngle = minutesToAngle(currentMinutes)
            val (mx, my) = angleToPoint(currentAngle, RADIUS)
            canvas.drawCircle(mx, my, 11f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_MARKER
            })
            canvas.drawCircle(mx, my, 11f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_BG
                style = Paint.Style.STROKE
                strokeWidth = 2.5f
            })
            canvas.drawCircle(mx, my, 5f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_GOLD
            })

            // Center digital time
            val timeText = "%02d:%02d".format(hour, minute)
            val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_TEXT
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                isFakeBoldText = true
            }
            timePaint.textSize = if (clockSize > 0f) clockSize else 72f
            val timeBounds = Rect()
            timePaint.getTextBounds(timeText, 0, timeText.length, timeBounds)
            val timeY = CY - 8f + timeBounds.height() / 2f - timeBounds.bottom
            canvas.drawText(timeText, CX, timeY, timePaint)

            // Jalali date under the time
            val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_MUTED
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                textAlign = Paint.Align.CENTER
            }
            datePaint.textSize = if (dateSize > 0f) dateSize else 28f
            val dateBounds = Rect()
            datePaint.getTextBounds(jalaliText, 0, jalaliText.length, dateBounds)
            val dateY = timeY + timeBounds.height() * 0.55f + dateBounds.height() + 6f
            canvas.drawText(jalaliText, CX, dateY, datePaint)

            return bitmap
        }

        private fun drawSolarLabel(
            canvas: Canvas,
            minutes: Int,
            title: String,
            timeStr: String,
            accent: Int,
            preferredSize: Float,
            isSunrise: Boolean
        ) {
            val angle = minutesToAngle(minutes)
            val (px, py) = angleToPoint(angle, RADIUS)

            // Small colored dot on the ring
            canvas.drawCircle(px, py, 7f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = accent
            })

            // Place label slightly outside
            val labelRadius = RADIUS + 38f
            val (lx, ly) = angleToPoint(angle, labelRadius)

            val size = if (preferredSize > 0f) preferredSize else 22f

            // Title (طلوع / غروب)
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = accent
                textSize = size * 0.85f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }
            canvas.drawText(title, lx, ly - 6f, titlePaint)

            // Time value
            val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_TEXT
                textSize = size
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isFakeBoldText = true
            }
            canvas.drawText(timeStr, lx, ly + size * 0.9f, timePaint)
        }

        private fun schedule(context: Context) {
            val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pending = pendingIntent(context)
            alarm.cancel(pending)

            val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val interactive = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
                power.isInteractive
            } else {
                @Suppress("DEPRECATION")
                power.isScreenOn
            }

            if (!interactive) return

            val interval = 60_000L
            val now = System.currentTimeMillis()
            val nextMinute = ((now / interval) + 1L) * interval

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarm.canScheduleExactAlarms()) {
                    alarm.setExactAndAllowWhileIdle(AlarmManager.RTC, nextMinute, pending)
                } else {
                    alarm.setAndAllowWhileIdle(AlarmManager.RTC, nextMinute, pending)
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarm.setExactAndAllowWhileIdle(AlarmManager.RTC, nextMinute, pending)
            } else {
                @Suppress("DEPRECATION")
                alarm.set(AlarmManager.RTC_WAKEUP, nextMinute, pending)
            }
        }

        private fun pendingIntent(context: Context): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                1001,
                Intent(context, ClockWidgetProvider::class.java).setAction(ACTION_TICK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
    }
}
