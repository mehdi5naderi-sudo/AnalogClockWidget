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
            val bitmap = drawWidget(
                clockSize = p.getFloat(KEY_CLOCK_SIZE, 72f),
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
            valueSizes: FloatArray
        ): Bitmap {
            val size = 512
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            canvas.drawColor(COLOR_BG)

            val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    256f, 0f, 256f, 140f,
                    intArrayOf(0x181C2A3A, 0x000B0F17),
                    null,
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, size.toFloat(), 150f, glowPaint)

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

            // Compact time section (top ~30%)
            val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_TEXT
                textSize = clockSize.coerceIn(44f, 96f)
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                isFakeBoldText = true
            }
            canvas.drawText("%02d:%02d".format(hour, minute), 256f, 78f, timePaint)

            val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_MUTED
                textSize = 14f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("تهران", 256f, 100f, subPaint)

            // Progress bar
            val progressY = 116f
            val barLeft = 56f
            val barRight = 456f
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

            // 2×2 cards — fill remaining space
            val delta = tomorrow.dayLengthMinutes - today.dayLengthMinutes
            val deltaText = when {
                delta > 0 -> "↑ " + SolarCalculator.formatDelta(delta)
                delta < 0 -> "↓ " + SolarCalculator.formatDelta(-delta)
                else -> "—"
            }
            val deltaColor = when {
                delta > 0 -> COLOR_GREEN
                delta < 0 -> COLOR_RED
                else -> COLOR_MUTED
            }

            val pad = 20f
            val gap = 12f
            val cardW = (512f - pad * 2 - gap) / 2f
            val cardH = (512f - 140f - pad - gap) / 2f   // from y=140 to bottom
            val startX = pad
            val startY = 140f

            drawCard(canvas, startX, startY, cardW, cardH,
                SolarCalculator.formatTime(today.sunrise), COLOR_GOLD, valueSizes[0], IconType.SUNRISE)
            drawCard(canvas, startX + cardW + gap, startY, cardW, cardH,
                SolarCalculator.formatTime(today.sunset), COLOR_GOLD, valueSizes[1], IconType.SUNSET)
            drawCard(canvas, startX, startY + cardH + gap, cardW, cardH,
                SolarCalculator.formatDuration(today.dayLengthMinutes), COLOR_TEXT, valueSizes[2], IconType.DURATION)
            drawCard(canvas, startX + cardW + gap, startY + cardH + gap, cardW, cardH,
                deltaText, deltaColor, valueSizes[3], if (delta >= 0) IconType.UP else IconType.DOWN)

            return bitmap
        }

        private enum class IconType { SUNRISE, SUNSET, DURATION, UP, DOWN }

        private fun drawCard(
            canvas: Canvas,
            x: Float,
            y: Float,
            w: Float,
            h: Float,
            value: String,
            valueColor: Int,
            valueSize: Float,
            iconType: IconType
        ) {
            val rect = RectF(x, y, x + w, y + h)

            canvas.drawRoundRect(rect, 18f, 18f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_CARD
                style = Paint.Style.FILL
            })
            canvas.drawRoundRect(rect, 18f, 18f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_CARD_STROKE
                style = Paint.Style.STROKE
                strokeWidth = 1.5f
            })

            // Icon upper third
            drawIcon(canvas, x + w / 2f, y + h * 0.32f, iconType)

            // Value lower third
            val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = valueColor
                textSize = valueSize.coerceIn(16f, 34f)
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText(value, x + w / 2f, y + h * 0.78f, valuePaint)
        }

        private fun drawIcon(canvas: Canvas, cx: Float, cy: Float, type: IconType) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2.4f
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }

            when (type) {
                IconType.SUNRISE -> {
                    p.color = COLOR_GOLD
                    canvas.drawCircle(cx, cy + 5f, 8f, p)
                    canvas.drawLine(cx, cy - 11f, cx, cy - 4f, p)
                    canvas.drawLine(cx - 9f, cy - 7f, cx - 4.5f, cy - 2f, p)
                    canvas.drawLine(cx + 9f, cy - 7f, cx + 4.5f, cy - 2f, p)
                    p.strokeWidth = 2.2f
                    canvas.drawLine(cx - 14f, cy + 14f, cx + 14f, cy + 14f, p)
                }
                IconType.SUNSET -> {
                    p.color = COLOR_GOLD
                    canvas.drawCircle(cx, cy - 2f, 8f, p)
                    canvas.drawLine(cx, cy + 12f, cx, cy + 5f, p)
                    canvas.drawLine(cx - 9f, cy + 8f, cx - 4.5f, cy + 3f, p)
                    canvas.drawLine(cx + 9f, cy + 8f, cx + 4.5f, cy + 3f, p)
                    p.strokeWidth = 2.2f
                    canvas.drawLine(cx - 14f, cy + 14f, cx + 14f, cy + 14f, p)
                }
                IconType.DURATION -> {
                    p.color = COLOR_MUTED
                    val arc = RectF(cx - 10f, cy - 10f, cx + 10f, cy + 10f)
                    canvas.drawArc(arc, -90f, 270f, false, p)
                    canvas.drawLine(cx, cy, cx + 6f, cy - 6f, p)
                }
                IconType.UP -> {
                    p.color = COLOR_GREEN
                    p.style = Paint.Style.FILL
                    val path = Path().apply {
                        moveTo(cx, cy - 10f)
                        lineTo(cx + 10f, cy + 6f)
                        lineTo(cx - 10f, cy + 6f)
                        close()
                    }
                    canvas.drawPath(path, p)
                }
                IconType.DOWN -> {
                    p.color = COLOR_RED
                    p.style = Paint.Style.FILL
                    val path = Path().apply {
                        moveTo(cx, cy + 10f)
                        lineTo(cx + 10f, cy - 6f)
                        lineTo(cx - 10f, cy - 6f)
                        close()
                    }
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
