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

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
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
        cachedStaticKey = null
        cachedStaticBitmap = null
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TICK || intent.action == Intent.ACTION_USER_PRESENT || intent.action == Intent.ACTION_SCREEN_ON) {
            refreshAll(context)
        }
    }

    companion object {
        private const val ACTION_TICK = "com.mehdi.analogclock.TICK"
        private const val PREFS = "clock_widget_prefs"

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

        private const val KEY_CLOCK_SIZE = "digital_clock_size"
        private const val KEY_VALUE1_SIZE = "info_value1_size"
        private const val KEY_VALUE2_SIZE = "info_value2_size"
        private const val KEY_VALUE3_SIZE = "info_value3_size"
        private const val KEY_VALUE4_SIZE = "info_value4_size"

        private const val COLOR_BG = 0xFF0B0F17.toInt()
        private const val COLOR_CARD = 0xFF151B27.toInt()
        private const val COLOR_CARD_STROKE = 0xFF273247.toInt()
        private const val COLOR_TEXT = 0xFFF3F5F7.toInt()
        private const val COLOR_MUTED = 0xFFAAB6C8.toInt()
        private const val COLOR_GOLD = 0xFFFFC857.toInt()
        private const val COLOR_ORANGE = 0xFFFF8A3D.toInt()
        private const val COLOR_GREEN = 0xFF6FD08C.toInt()
        private const val COLOR_RED = 0xFFE58A8A.toInt()

        @Volatile private var cachedStaticKey: String? = null
        @Volatile private var cachedStaticBitmap: Bitmap? = null

        fun update(context: Context, manager: AppWidgetManager, ids: IntArray) {
            if (ids.isEmpty()) return

            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val clockSize = p.getFloat(KEY_CLOCK_SIZE, 0f)
            val valueSizes = floatArrayOf(
                p.getFloat(KEY_VALUE1_SIZE, 0f),
                p.getFloat(KEY_VALUE2_SIZE, 0f),
                p.getFloat(KEY_VALUE3_SIZE, 0f),
                p.getFloat(KEY_VALUE4_SIZE, 0f)
            )

            val cal = Calendar.getInstance(TimeZone.getTimeZone(SolarCalculator.TIME_ZONE))
            val today = SolarCalculator.solarDay(
                cal.get(Calendar.YEAR), cal.get(Calendar.DAY_OF_YEAR)
            )
            val tomorrowCal = cal.clone() as Calendar
            tomorrowCal.add(Calendar.DAY_OF_YEAR, 1)
            val tomorrow = SolarCalculator.solarDay(
                tomorrowCal.get(Calendar.YEAR), tomorrowCal.get(Calendar.DAY_OF_YEAR)
            )

            val staticKey = buildString {
                append(cal.get(Calendar.YEAR)).append('-')
                append(cal.get(Calendar.DAY_OF_YEAR)).append('|')
                valueSizes.forEach { append(it).append(',') }
            }

            val staticBitmap = if (staticKey == cachedStaticKey && cachedStaticBitmap != null) {
                cachedStaticBitmap!!
            } else {
                val fresh = drawStaticWidget(today, tomorrow, valueSizes)
                cachedStaticKey = staticKey
                cachedStaticBitmap = fresh
                fresh
            }

            val hour = cal.get(Calendar.HOUR_OF_DAY)
            val minute = cal.get(Calendar.MINUTE)
            val currentMinutes = hour * 60 + minute

            // All instances show the same data: render only once per tick.
            val bitmap = staticBitmap.copy(Bitmap.Config.ARGB_8888, true)
            drawDynamic(Canvas(bitmap), hour, minute, currentMinutes, today, clockSize)

            val views = RemoteViews(context.packageName, R.layout.widget_clock)
            views.setImageViewBitmap(R.id.clock_image, bitmap)
            manager.updateAppWidget(ids, views)
        }

        private fun drawStaticWidget(
            today: SolarCalculator.SolarDay,
            tomorrow: SolarCalculator.SolarDay,
            valueSizes: FloatArray
        ): Bitmap {
            val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(COLOR_BG)

            canvas.drawRect(
                0f, 0f, 512f, 170f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(
                        256f, 0f, 256f, 170f,
                        intArrayOf(0x1D24334A, 0x000B0F17),
                        null, Shader.TileMode.CLAMP
                    )
                }
            )

            val barLeft = 20f
            val barRight = 492f
            val barY = 132f
            val barHeight = 9f

            val gradient = LinearGradient(
                barLeft, barY, barRight, barY,
                intArrayOf(COLOR_ORANGE, 0xFF4F8DE8.toInt(), COLOR_GOLD),
                floatArrayOf(0f, 0.52f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(
                RectF(barLeft, barY, barRight, barY + barHeight),
                5f, 5f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = gradient }
            )

            drawSunIcon(canvas, 48f, 92f, COLOR_ORANGE, sunset = true)
            drawSunIcon(canvas, 464f, 92f, COLOR_GOLD, sunset = false)

            drawLabel(canvas, "غروب", 72f, 91f, Paint.Align.LEFT, COLOR_ORANGE, 23f)
            drawLabel(
                canvas, SolarCalculator.formatTime(today.sunset),
                72f, 116f, Paint.Align.LEFT, COLOR_TEXT, 28f, bold = true
            )
            drawLabel(canvas, "طلوع", 440f, 91f, Paint.Align.RIGHT, COLOR_GOLD, 23f)
            drawLabel(
                canvas, SolarCalculator.formatTime(today.sunrise),
                440f, 116f, Paint.Align.RIGHT, COLOR_TEXT, 28f, bold = true
            )

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

            val pad = 10f
            val gap = 8f
            val cardW = (512f - pad * 2 - gap) / 2f
            val cardTop = 170f
            val cardH = (512f - cardTop - pad - gap) / 2f
            val startX = pad

            drawCard(
                canvas, startX, cardTop, cardW, cardH,
                "غروب", SolarCalculator.formatTime(today.sunset),
                COLOR_ORANGE, valueSizes[1], icon = 0
            )
            drawCard(
                canvas, startX + cardW + gap, cardTop, cardW, cardH,
                "طلوع", SolarCalculator.formatTime(today.sunrise),
                COLOR_GOLD, valueSizes[0], icon = 1
            )
            drawCard(
                canvas, startX, cardTop + cardH + gap, cardW, cardH,
                "طول روز", SolarCalculator.formatDuration(today.dayLengthMinutes),
                COLOR_TEXT, valueSizes[2], icon = 2
            )
            drawCard(
                canvas, startX + cardW + gap, cardTop + cardH + gap, cardW, cardH,
                "تغییر فردا", deltaText,
                deltaColor, valueSizes[3], icon = 3
            )

            return bitmap
        }

        private fun drawDynamic(
            canvas: Canvas,
            hour: Int,
            minute: Int,
            currentMinutes: Int,
            today: SolarCalculator.SolarDay,
            clockSize: Float
        ) {
            val timeText = "%02d:%02d".format(hour, minute)
            val timeArea = RectF(16f, 8f, 496f, 76f)

            val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_TEXT
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                isFakeBoldText = true
            }
            timePaint.textSize = if (clockSize > 0f) {
                clockSize
            } else {
                fitTextSize(timeText, timePaint, timeArea.width() * 0.94f, timeArea.height() * 0.98f)
            }

            val bounds = Rect()
            timePaint.getTextBounds(timeText, 0, timeText.length, bounds)
            val timeY = timeArea.centerY() + bounds.height() / 2f - bounds.bottom
            canvas.drawText(timeText, timeArea.centerX(), timeY, timePaint)

            val barLeft = 20f
            val barRight = 492f
            val barY = 132f
            val barHeight = 9f

            val progress = when {
                currentMinutes <= today.sunrise -> 0f
                currentMinutes >= today.sunset -> 1f
                else -> (currentMinutes - today.sunrise).toFloat() /
                    today.dayLengthMinutes.coerceAtLeast(1)
            }.coerceIn(0f, 1f)

            // Sunrise = right, sunset = left.
            val markerX = barRight - (barRight - barLeft) * progress

            canvas.drawCircle(
                markerX, barY + barHeight / 2f, 10f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_TEXT }
            )
            canvas.drawCircle(
                markerX, barY + barHeight / 2f, 10f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = COLOR_BG
                    style = Paint.Style.STROKE
                    strokeWidth = 2f
                }
            )

            drawLabel(
                canvas, "اکنون",
                markerX.coerceIn(54f, 458f), 165f,
                Paint.Align.CENTER, COLOR_TEXT, 22f, bold = true
            )
        }

        private fun drawCard(
            canvas: Canvas,
            x: Float, y: Float, w: Float, h: Float,
            title: String, value: String, accent: Int,
            preferredSize: Float, icon: Int
        ) {
            val rect = RectF(x, y, x + w, y + h)

            canvas.drawRoundRect(rect, 16f, 16f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_CARD
            })
            canvas.drawRoundRect(rect, 16f, 16f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_CARD_STROKE
                style = Paint.Style.STROKE
                strokeWidth = 1.5f
            })
            canvas.drawRoundRect(
                RectF(x, y, x + 5f, y + h), 4f, 4f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
            )

            val iconX = x + 40f
            val centerY = y + h / 2f
            when (icon) {
                0 -> drawSunIcon(canvas, iconX, centerY, COLOR_ORANGE, sunset = true)
                1 -> drawSunIcon(canvas, iconX, centerY, COLOR_GOLD, sunset = false)
                2 -> drawSunIcon(canvas, iconX, centerY, COLOR_TEXT, sunset = false)
                3 -> drawArrowIcon(canvas, iconX, centerY, accent)
            }

            drawLabel(
                canvas, title, x + w / 2f + 14f, y + h * 0.43f,
                Paint.Align.CENTER, COLOR_MUTED, 25f
            )

            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = accent
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                isFakeBoldText = true
            }
            paint.textSize = if (preferredSize > 0f) {
                preferredSize
            } else {
                fitTextSize(value, paint, w - 62f, h * 0.45f)
            }

            val bounds = Rect()
            paint.getTextBounds(value, 0, value.length, bounds)
            val valueY = y + h * 0.72f + bounds.height() / 2f - bounds.bottom
            canvas.drawText(value, x + w / 2f + 12f, valueY, paint)
        }

        private fun drawSunIcon(
            canvas: Canvas, cx: Float, cy: Float, color: Int, sunset: Boolean
        ) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                style = Paint.Style.STROKE
                strokeWidth = 3.5f
                strokeCap = Paint.Cap.ROUND
            }
            canvas.drawCircle(cx, cy, 9f, p)
            for (i in 0 until 8) {
                val a = Math.toRadians(i * 45.0)
                canvas.drawLine(
                    cx + cos(a).toFloat() * 15f,
                    cy + sin(a).toFloat() * 15f,
                    cx + cos(a).toFloat() * 21f,
                    cy + sin(a).toFloat() * 21f,
                    p
                )
            }
            if (sunset) {
                canvas.drawLine(cx - 18f, cy + 15f, cx + 18f, cy + 15f, p)
                canvas.drawLine(cx - 12f, cy + 20f, cx + 12f, cy + 20f, p)
            }
        }

        private fun drawArrowIcon(canvas: Canvas, cx: Float, cy: Float, color: Int) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                style = Paint.Style.STROKE
                strokeWidth = 5f
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            canvas.drawLine(cx, cy + 17f, cx, cy - 17f, p)
            canvas.drawLine(cx, cy - 17f, cx - 10f, cy - 7f, p)
            canvas.drawLine(cx, cy - 17f, cx + 10f, cy - 7f, p)
        }

        private fun drawLabel(
            canvas: Canvas, text: String, x: Float, baseline: Float,
            align: Paint.Align, color: Int, size: Float, bold: Boolean = false
        ) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                textAlign = align
                textSize = size
                typeface = Typeface.create(
                    Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL
                )
            }
            canvas.drawText(text, x, baseline, p)
        }

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
            val pending = pendingIntent(context)
            alarm.cancel(pending)

            val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val interactive = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
                power.isInteractive
            } else {
                @Suppress("DEPRECATION")
                power.isScreenOn
            }

            // Do not schedule wakeups while screen is off. USER_PRESENT restarts refresh.
            if (!interactive) return

            // The clock must move every minute while the display is on.
            // With targetSdk 35, setAndAllowWhileIdle() is inexact and can be
            // delayed substantially on Android/MIUI. Use an exact one-shot
            // alarm when Android 12+ exact-alarm access is available.
            val interval = 60_000L
            val now = System.currentTimeMillis()
            val nextMinute = ((now / interval) + 1L) * interval

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarm.canScheduleExactAlarms()) {
                    alarm.setExactAndAllowWhileIdle(
                        AlarmManager.RTC,
                        nextMinute,
                        pending
                    )
                } else {
                    alarm.setAndAllowWhileIdle(
                        AlarmManager.RTC,
                        nextMinute,
                        pending
                    )
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarm.setExactAndAllowWhileIdle(
                    AlarmManager.RTC,
                    nextMinute,
                    pending
                )
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
