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
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.widget.RemoteViews
import java.util.Calendar
import java.util.TimeZone

class ClockWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        update(context, manager, ids)
        schedule(context)
    }

    override fun onEnabled(context: Context) {
        schedule(context)
    }

    override fun onDisabled(context: Context) {
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
            .cancel(pendingIntent(context))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TICK) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, ClockWidgetProvider::class.java)
            )
            update(context, manager, ids)
            schedule(context)
        }
    }

    companion object {
        private const val ACTION_TICK = "com.mehdi.analogclock.TICK"
        private const val PREFS = "clock_widget_prefs"

        private const val KEY_CLOCK_SIZE = "digital_clock_size"
        private const val KEY_VALUE1_SIZE = "info_value1_size"
        private const val KEY_VALUE2_SIZE = "info_value2_size"
        private const val KEY_VALUE3_SIZE = "info_value3_size"
        private const val KEY_VALUE4_SIZE = "info_value4_size"

        private const val COLOR_BG = 0xFF0B0F17.toInt()
        private const val COLOR_CARD = 0xFF151B27.toInt()
        private const val COLOR_CARD_STROKE = 0xFF222A3A.toInt()
        private const val COLOR_TEXT = 0xFFF3F5F7.toInt()
        private const val COLOR_MUTED = 0xFF8B9AAB.toInt()
        private const val COLOR_GOLD = 0xFFFFC857.toInt()
        private const val COLOR_GREEN = 0xFF6FD08C.toInt()
        private const val COLOR_RED = 0xFFE58A8A.toInt()
        private const val COLOR_PROGRESS_TRACK = 0xFF1E2636.toInt()
        private const val COLOR_PROGRESS_FILL = 0xFFFFC857.toInt()

        fun update(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            // 0 = auto-fit to box (default). Positive = user override from settings.
            val bitmap = drawWidget(
                clockSize = p.getFloat(KEY_CLOCK_SIZE, 0f),
                valueSizes = floatArrayOf(
                    p.getFloat(KEY_VALUE1_SIZE, 0f),
                    p.getFloat(KEY_VALUE2_SIZE, 0f),
                    p.getFloat(KEY_VALUE3_SIZE, 0f),
                    p.getFloat(KEY_VALUE4_SIZE, 0f)
                )
            )
            ids.forEach { id ->
                val views = RemoteViews(context.packageName, R.layout.widget_clock)
                views.setImageViewBitmap(R.id.clock_image, bitmap)
                manager.updateAppWidget(id, views)
            }
        }

        private fun drawWidget(
            clockSize: Float,
            valueSizes: FloatArray
        ): Bitmap {
            val size = 512
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            canvas.drawColor(COLOR_BG)

            val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    256f, 0f, 256f, 120f,
                    intArrayOf(0x181C2A3A, 0x000B0F17),
                    null,
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, size.toFloat(), 130f, glowPaint)

            val cal = Calendar.getInstance(TimeZone.getTimeZone(SolarCalculator.TIME_ZONE))
            val today = SolarCalculator.solarDay(
                cal.get(Calendar.YEAR),
                cal.get(Calendar.DAY_OF_YEAR)
            )

            val tomorrowCal = cal.clone() as Calendar
            tomorrowCal.add(Calendar.DAY_OF_YEAR, 1)
            val tomorrow = SolarCalculator.solarDay(
                tomorrowCal.get(Calendar.YEAR),
                tomorrowCal.get(Calendar.DAY_OF_YEAR)
            )

            val hour = cal.get(Calendar.HOUR_OF_DAY)
            val minute = cal.get(Calendar.MINUTE)
            val currentMinutes = hour * 60 + minute
            val timeText = "%02d:%02d".format(hour, minute)

            // Time area: full width, from y=8 to y=100
            val timeArea = RectF(16f, 8f, 496f, 100f)
            val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_TEXT
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                isFakeBoldText = true
            }
            timePaint.textSize = if (clockSize > 0f) {
                clockSize
            } else {
                fitTextSize(timeText, timePaint, timeArea.width() * 0.92f, timeArea.height() * 0.90f)
            }
            val timeBounds = Rect()
            timePaint.getTextBounds(timeText, 0, timeText.length, timeBounds)
            val timeY = timeArea.centerY() + timeBounds.height() / 2f - timeBounds.bottom
            canvas.drawText(timeText, timeArea.centerX(), timeY, timePaint)

            // Progress bar
            val progressY = 108f
            val barLeft = 20f
            val barRight = 492f
            val barHeight = 5f

            canvas.drawRoundRect(
                RectF(barLeft, progressY, barRight, progressY + barHeight),
                3f, 3f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = COLOR_PROGRESS_TRACK
                    style = Paint.Style.FILL
                }
            )

            val progress = when {
                currentMinutes <= today.sunrise -> 0f
                currentMinutes >= today.sunset -> 1f
                else -> (currentMinutes - today.sunrise).toFloat() /
                    today.dayLengthMinutes.coerceAtLeast(1)
            }.coerceIn(0f, 1f)

            if (progress > 0.01f) {
                canvas.drawRoundRect(
                    RectF(barLeft, progressY, barLeft + (barRight - barLeft) * progress, progressY + barHeight),
                    3f, 3f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = COLOR_PROGRESS_FILL
                        style = Paint.Style.FILL
                    }
                )
            }

            val delta = tomorrow.dayLengthMinutes - today.dayLengthMinutes
            val deltaText = when {
                delta > 0 -> "↑" + SolarCalculator.formatDelta(delta)
                delta < 0 -> "↓" + SolarCalculator.formatDelta(-delta)
                else -> "—"
            }
            val deltaColor = when {
                delta > 0 -> COLOR_GREEN
                delta < 0 -> COLOR_RED
                else -> COLOR_MUTED
            }

            // 2×2 cards — almost full remaining space
            val pad = 10f
            val gap = 8f
            val cardW = (512f - pad * 2 - gap) / 2f
            val cardTop = 124f
            val cardH = (512f - cardTop - pad - gap) / 2f
            val startX = pad

            // RTL: sunset LEFT, sunrise RIGHT
            drawCard(canvas, startX, cardTop, cardW, cardH,
                SolarCalculator.formatTime(today.sunset), COLOR_GOLD, valueSizes[1])
            drawCard(canvas, startX + cardW + gap, cardTop, cardW, cardH,
                SolarCalculator.formatTime(today.sunrise), COLOR_GOLD, valueSizes[0])

            drawCard(canvas, startX, cardTop + cardH + gap, cardW, cardH,
                deltaText, deltaColor, valueSizes[3])
            drawCard(canvas, startX + cardW + gap, cardTop + cardH + gap, cardW, cardH,
                SolarCalculator.formatDuration(today.dayLengthMinutes), COLOR_TEXT, valueSizes[2])

            return bitmap
        }

        private fun drawCard(
            canvas: Canvas,
            x: Float,
            y: Float,
            w: Float,
            h: Float,
            value: String,
            valueColor: Int,
            preferredSize: Float
        ) {
            val rect = RectF(x, y, x + w, y + h)

            canvas.drawRoundRect(rect, 14f, 14f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_CARD
                style = Paint.Style.FILL
            })
            canvas.drawRoundRect(rect, 14f, 14f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_CARD_STROKE
                style = Paint.Style.STROKE
                strokeWidth = 1.5f
            })

            // Text fills the card (with small inner padding)
            val innerPad = 10f
            val textAreaW = w - innerPad * 2
            val textAreaH = h - innerPad * 2

            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = valueColor
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                isFakeBoldText = true
            }

            paint.textSize = if (preferredSize > 0f) {
                preferredSize
            } else {
                fitTextSize(value, paint, textAreaW, textAreaH)
            }

            val bounds = Rect()
            paint.getTextBounds(value, 0, value.length, bounds)
            val textY = y + h / 2f + bounds.height() / 2f - bounds.bottom
            canvas.drawText(value, x + w / 2f, textY, paint)
        }

        /** Largest text size that fits inside maxW × maxH. */
        private fun fitTextSize(text: String, paint: Paint, maxW: Float, maxH: Float): Float {
            var lo = 8f
            var hi = maxH.coerceAtLeast(8f)
            val bounds = Rect()
            while (hi - lo > 0.5f) {
                val mid = (lo + hi) / 2f
                paint.textSize = mid
                paint.getTextBounds(text, 0, text.length, bounds)
                val tw = paint.measureText(text)
                val th = bounds.height().toFloat()
                if (tw <= maxW && th <= maxH) lo = mid else hi = mid
            }
            return lo
        }

        private fun schedule(context: Context) {
            val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarm.cancel(pendingIntent(context))

            val now = System.currentTimeMillis()
            val next = now - (now % 60_000L) + 60_000L

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarm.setExactAndAllowWhileIdle(
                    AlarmManager.RTC, next, pendingIntent(context)
                )
            } else {
                alarm.setExact(AlarmManager.RTC, next, pendingIntent(context))
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
