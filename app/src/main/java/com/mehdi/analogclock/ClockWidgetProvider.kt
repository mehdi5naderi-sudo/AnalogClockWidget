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
        val action = intent.action
        if (action == ACTION_TICK ||
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
        private const val PREFS = "clock_widget_prefs"

        private const val KEY_CLOCK_SIZE = "digital_clock_size"
        private const val KEY_DATE_SIZE = "date_size"
        private const val KEY_LABEL_SIZE = "label_size"
        private const val KEY_SUNRISE_OFFSET = "sunrise_offset_y"
        private const val KEY_SUNSET_OFFSET = "sunset_offset_y"
        private const val KEY_DATE_OFFSET = "date_offset_y"
        private const val KEY_BACKGROUND_ALPHA = "background_alpha"

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
        private const val RADIUS = 216f

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
            val sunriseOffset = p.getFloat(KEY_SUNRISE_OFFSET, 0f)
            val sunsetOffset = p.getFloat(KEY_SUNSET_OFFSET, 0f)
            val dateOffset = p.getFloat(KEY_DATE_OFFSET, 0f)
            val backgroundAlpha = p.getFloat(KEY_BACKGROUND_ALPHA, 100f)

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
                append(',').append(sunriseOffset).append(',').append(sunsetOffset).append(',').append(dateOffset)
                append(',').append(backgroundAlpha)
            }

            val bitmap = if (key == cachedKey && cachedBitmap != null) {
                cachedBitmap!!
            } else {
                val fresh = drawWidget(
                    today, currentMinutes, hour, minute,
                    jalaliText, clockSize, dateSize, labelSize,
                    sunriseOffset, sunsetOffset, dateOffset, backgroundAlpha
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
         * Angle mapping (counter-clockwise from noon so that 6 is on the right):
         *   0°   = top     = 12:00 (noon)
         *  90°   = right   = 06:00
         * 180°   = bottom  = 00:00
         * 270°   = left    = 18:00
         */
        private fun minutesToAngle(minutes: Int): Float {
            val fromNoonCcw = (720 - minutes + 1440) % 1440
            return fromNoonCcw / 1440f * 360f
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
            labelSize: Float,
            sunriseOffset: Float,
            sunsetOffset: Float,
            dateOffset: Float,
            backgroundAlpha: Float
        ): Bitmap {
            val bitmap = Bitmap.createBitmap(SIZE.toInt(), SIZE.toInt(), Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val bgAlpha = ((backgroundAlpha / 100f) * 255f).toInt().coerceIn(0, 255)
            val bgColor = (COLOR_BG and 0x00FFFFFF) or (bgAlpha shl 24)
            canvas.drawColor(bgColor)

            // Outer soft ring
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

            // Daylight arc (sunrise → sunset, upper path through noon)
            val sunriseAngle = minutesToAngle(today.sunrise)
            val dayArcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_DAY_ARC
                style = Paint.Style.STROKE
                strokeWidth = 10f
                strokeCap = Paint.Cap.ROUND
            }
            val startCanvas = sunriseAngle - 90f
            val sweep = -(today.dayLengthMinutes / 1440f * 360f)
            canvas.drawArc(
                RectF(CX - RADIUS, CY - RADIUS, CX + RADIUS, CY + RADIUS),
                startCanvas, sweep, false, dayArcPaint
            )

            // Subtle hour ticks at 0, 6, 12, 18
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

            // Sunrise / sunset icons only on the rim
            drawSunIconOnRim(canvas, today.sunrise, COLOR_GOLD, sunset = false)
            drawSunIconOnRim(canvas, today.sunset, COLOR_ORANGE, sunset = true)

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

            // --- Center content ---
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

            // Solar times above the digital clock (no titles)
            // Default: sunrise on the RIGHT, sunset on the LEFT
            val sunriseStr = SolarCalculator.formatTime(today.sunrise)
            val sunsetStr = SolarCalculator.formatTime(today.sunset)
            val solarSize = if (labelSize > 0f) labelSize else 26f
            val solarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = solarSize
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isFakeBoldText = true
            }

            val baseSolarY = CY - timeBounds.height() * 0.75f - solarSize - 10f

            // Sunset (orange) on the LEFT
            solarPaint.color = COLOR_ORANGE
            canvas.drawText(sunsetStr, CX - 70f, baseSolarY + sunsetOffset, solarPaint)

            // Sunrise (gold) on the RIGHT
            solarPaint.color = COLOR_GOLD
            canvas.drawText(sunriseStr, CX + 70f, baseSolarY + sunriseOffset, solarPaint)

            // Digital time (center, fixed)
            val timeY = CY + timeBounds.height() / 2f - timeBounds.bottom - 4f
            canvas.drawText(timeText, CX, timeY, timePaint)

            // Jalali date under the time
            val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_MUTED
                // Medium Sans gives the Persian date a cleaner, more polished look
                // while keeping the existing size, position and format unchanged.
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                textAlign = Paint.Align.CENTER
            }
            datePaint.textSize = if (dateSize > 0f) dateSize else 28f
            val dateBounds = Rect()
            datePaint.getTextBounds(jalaliText, 0, jalaliText.length, dateBounds)
            val baseDateY = timeY + timeBounds.height() * 0.55f + dateBounds.height() + 8f
            canvas.drawText(jalaliText, CX, baseDateY + dateOffset, datePaint)

            return bitmap
        }

        private fun drawSunIconOnRim(
            canvas: Canvas,
            minutes: Int,
            color: Int,
            sunset: Boolean
        ) {
            val angle = minutesToAngle(minutes)
            val (cx, cy) = angleToPoint(angle, RADIUS)

            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                style = Paint.Style.STROKE
                strokeWidth = 3f
                strokeCap = Paint.Cap.ROUND
            }
            canvas.drawCircle(cx, cy, 8f, p)
            for (i in 0 until 8) {
                val a = Math.toRadians(i * 45.0)
                canvas.drawLine(
                    cx + cos(a).toFloat() * 12f,
                    cy + sin(a).toFloat() * 12f,
                    cx + cos(a).toFloat() * 17f,
                    cy + sin(a).toFloat() * 17f,
                    p
                )
            }
            if (sunset) {
                canvas.drawLine(cx - 14f, cy + 12f, cx + 14f, cy + 12f, p)
                canvas.drawLine(cx - 10f, cy + 16f, cx + 10f, cy + 16f, p)
            }
        }

        /**
         * Always schedule the next minute tick.
         * Uses RTC (not RTC_WAKEUP) so the device is never woken from sleep.
         * When the screen turns back on, any due RTC alarm fires and the chain resumes.
         * Previously we skipped scheduling while the screen was off; combined with
         * Android 8+ restrictions on manifest SCREEN_ON receivers, that left the
         * widget stuck until the next manual update.
         */
        private fun schedule(context: Context) {
            val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pending = pendingIntent(context)
            alarm.cancel(pending)

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
                alarm.set(AlarmManager.RTC, nextMinute, pending)
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
