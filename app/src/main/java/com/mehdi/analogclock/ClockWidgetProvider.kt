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
import android.graphics.Path
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
        private const val KEY_TITLE_SIZE = "info_title_size"
        private const val KEY_VALUE1_SIZE = "info_value1_size"
        private const val KEY_VALUE2_SIZE = "info_value2_size"
        private const val KEY_VALUE3_SIZE = "info_value3_size"
        private const val KEY_VALUE4_SIZE = "info_value4_size"

        // Colors
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
            val bitmap = drawWidget(
                clockSize = p.getFloat(KEY_CLOCK_SIZE, 78f),
                titleSize = p.getFloat(KEY_TITLE_SIZE, 15f),
                valueSizes = floatArrayOf(
                    p.getFloat(KEY_VALUE1_SIZE, 26f),
                    p.getFloat(KEY_VALUE2_SIZE, 26f),
                    p.getFloat(KEY_VALUE3_SIZE, 26f),
                    p.getFloat(KEY_VALUE4_SIZE, 24f)
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
            titleSize: Float,
            valueSizes: FloatArray
        ): Bitmap {
            val size = 512
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            // Background
            canvas.drawColor(COLOR_BG)

            // Subtle top radial glow
            val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    256f, 0f, 256f, 180f,
                    intArrayOf(0x181C2A3A, 0x000B0F17),
                    null,
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, size.toFloat(), 200f, glowPaint)

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

            // ── Digital time ──────────────────────────────────────────────
            val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_TEXT
                textSize = clockSize.coerceIn(48f, 110f)
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                isFakeBoldText = true
            }
            val timeText = "%02d:%02d".format(hour, minute)
            canvas.drawText(timeText, 256f, 108f, timePaint)

            // Subtitle
            val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_MUTED
                textSize = 18f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("تهران", 256f, 138f, subPaint)

            // ── Day progress bar ──────────────────────────────────────────
            val progressY = 162f
            val barLeft = 72f
            val barRight = 440f
            val barHeight = 6f
            val barRect = RectF(barLeft, progressY, barRight, progressY + barHeight)

            val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_PROGRESS_TRACK
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(barRect, 3f, 3f, trackPaint)

            val progress = when {
                currentMinutes <= today.sunrise -> 0f
                currentMinutes >= today.sunset -> 1f
                else -> (currentMinutes - today.sunrise).toFloat() / today.dayLengthMinutes.coerceAtLeast(1)
            }.coerceIn(0f, 1f)

            if (progress > 0.01f) {
                val fillRect = RectF(barLeft, progressY, barLeft + (barRight - barLeft) * progress, progressY + barHeight)
                val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = COLOR_PROGRESS_FILL
                    style = Paint.Style.FILL
                }
                canvas.drawRoundRect(fillRect, 3f, 3f, fillPaint)
            }

            // Progress labels (sunrise / sunset small)
            val tinyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_MUTED
                textSize = 13f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(SolarCalculator.formatTime(today.sunrise), barLeft, progressY + 22f, tinyPaint)
            canvas.drawText(SolarCalculator.formatTime(today.sunset), barRight, progressY + 22f, tinyPaint)

            // ── Info cards (2×2) ──────────────────────────────────────────
            val delta = tomorrow.dayLengthMinutes - today.dayLengthMinutes
            val deltaText = when {
                delta > 0 -> "↑ " + SolarCalculator.formatDelta(delta)
                delta < 0 -> "↓ " + SolarCalculator.formatDelta(-delta)
                else -> "بدون تغییر"
            }
            val deltaColor = when {
                delta > 0 -> COLOR_GREEN
                delta < 0 -> COLOR_RED
                else -> COLOR_MUTED
            }

            val cardW = 198f
            val cardH = 118f
            val gap = 16f
            val startX = (512f - (cardW * 2 + gap)) / 2f
            val startY = 214f

            drawCard(
                canvas,
                startX, startY, cardW, cardH,
                title = "طلوع",
                value = SolarCalculator.formatTime(today.sunrise),
                valueColor = COLOR_GOLD,
                titleSize = titleSize,
                valueSize = valueSizes[0],
                iconType = IconType.SUNRISE
            )
            drawCard(
                canvas,
                startX + cardW + gap, startY, cardW, cardH,
                title = "غروب",
                value = SolarCalculator.formatTime(today.sunset),
                valueColor = COLOR_GOLD,
                titleSize = titleSize,
                valueSize = valueSizes[1],
                iconType = IconType.SUNSET
            )
            drawCard(
                canvas,
                startX, startY + cardH + gap, cardW, cardH,
                title = "طول روز",
                value = SolarCalculator.formatDuration(today.dayLengthMinutes),
                valueColor = COLOR_TEXT,
                titleSize = titleSize,
                valueSize = valueSizes[2],
                iconType = IconType.DURATION
            )
            drawCard(
                canvas,
                startX + cardW + gap, startY + cardH + gap, cardW, cardH,
                title = "فردا",
                value = deltaText,
                valueColor = deltaColor,
                titleSize = titleSize,
                valueSize = valueSizes[3],
                iconType = if (delta >= 0) IconType.UP else IconType.DOWN
            )

            return bitmap
        }

        private enum class IconType { SUNRISE, SUNSET, DURATION, UP, DOWN }

        private fun drawCard(
            canvas: Canvas,
            x: Float,
            y: Float,
            w: Float,
            h: Float,
            title: String,
            value: String,
            valueColor: Int,
            titleSize: Float,
            valueSize: Float,
            iconType: IconType
        ) {
            val rect = RectF(x, y, x + w, y + h)

            // Card background
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_CARD
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(rect, 20f, 20f, bgPaint)

            // Subtle stroke
            val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_CARD_STROKE
                style = Paint.Style.STROKE
                strokeWidth = 1.5f
            }
            canvas.drawRoundRect(rect, 20f, 20f, strokePaint)

            // Icon
            drawIcon(canvas, x + 22f, y + 28f, iconType)

            // Title
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_MUTED
                textSize = titleSize.coerceIn(12f, 22f)
                textAlign = Paint.Align.LEFT
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }
            canvas.drawText(title, x + 52f, y + 34f, titlePaint)

            // Value
            val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = valueColor
                textSize = valueSize.coerceIn(18f, 36f)
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText(value, x + w / 2f, y + h - 28f, valuePaint)
        }

        private fun drawIcon(canvas: Canvas, cx: Float, cy: Float, type: IconType) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2.2f
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }

            when (type) {
                IconType.SUNRISE -> {
                    p.color = COLOR_GOLD
                    // Sun circle
                    canvas.drawCircle(cx, cy + 4f, 7f, p)
                    // Rays upward
                    canvas.drawLine(cx, cy - 10f, cx, cy - 4f, p)
                    canvas.drawLine(cx - 8f, cy - 6f, cx - 4f, cy - 2f, p)
                    canvas.drawLine(cx + 8f, cy - 6f, cx + 4f, cy - 2f, p)
                    // Horizon
                    p.strokeWidth = 2f
                    canvas.drawLine(cx - 12f, cy + 12f, cx + 12f, cy + 12f, p)
                }
                IconType.SUNSET -> {
                    p.color = COLOR_GOLD
                    canvas.drawCircle(cx, cy - 2f, 7f, p)
                    canvas.drawLine(cx, cy + 10f, cx, cy + 4f, p)
                    canvas.drawLine(cx - 8f, cy + 6f, cx - 4f, cy + 2f, p)
                    canvas.drawLine(cx + 8f, cy + 6f, cx + 4f, cy + 2f, p)
                    p.strokeWidth = 2f
                    canvas.drawLine(cx - 12f, cy + 12f, cx + 12f, cy + 12f, p)
                }
                IconType.DURATION -> {
                    p.color = COLOR_MUTED
                    // Simple clock arc
                    val arc = RectF(cx - 9f, cy - 9f, cx + 9f, cy + 9f)
                    canvas.drawArc(arc, -90f, 270f, false, p)
                    // Hand
                    canvas.drawLine(cx, cy, cx + 5f, cy - 5f, p)
                }
                IconType.UP -> {
                    p.color = COLOR_GREEN
                    p.strokeWidth = 2.5f
                    val path = Path().apply {
                        moveTo(cx, cy - 8f)
                        lineTo(cx + 8f, cy + 4f)
                        lineTo(cx - 8f, cy + 4f)
                        close()
                    }
                    p.style = Paint.Style.FILL
                    canvas.drawPath(path, p)
                }
                IconType.DOWN -> {
                    p.color = COLOR_RED
                    p.strokeWidth = 2.5f
                    val path = Path().apply {
                        moveTo(cx, cy + 8f)
                        lineTo(cx + 8f, cy - 4f)
                        lineTo(cx - 8f, cy - 4f)
                        close()
                    }
                    p.style = Paint.Style.FILL
                    canvas.drawPath(path, p)
                }
            }
        }

        private fun schedule(context: Context) {
            val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarm.cancel(pendingIntent(context))

            val now = System.currentTimeMillis()
            val next = now - (now % 60_000L) + 60_000L

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarm.setExactAndAllowWhileIdle(
                    AlarmManager.RTC,
                    next,
                    pendingIntent(context)
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
