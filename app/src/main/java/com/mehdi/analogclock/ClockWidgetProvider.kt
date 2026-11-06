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
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
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

        private const val COLOR_BG = 0xFF0B0F17.toInt()
        private const val COLOR_TEXT = 0xFFF3F5F7.toInt()
        private const val COLOR_MUTED = 0xFF8B9AAB.toInt()
        private const val COLOR_GOLD = 0xFFFFC857.toInt()
        private const val COLOR_ORANGE = 0xFFFF8A3D.toInt()
        private const val COLOR_DIVIDER = 0xFF2A3548.toInt()

        private const val W = 520f
        private const val H = 1000f

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

            var weather = WeatherData.read(context)
            val needForce = weather == null || weather.hourly.size < 6
            WeatherData.refreshIfNeeded(context, force = needForce)
            weather = WeatherData.read(context)

            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

            val showJalali = p.getBoolean("show_jalali", true)
            val showGregorian = p.getBoolean("show_gregorian", true)
            val showSolar = p.getBoolean("show_solar", true)
            val showCurrentWeather = p.getBoolean("show_current_weather", true)
            val showHourly = p.getBoolean("show_hourly", true)
            val showDaily = p.getBoolean("show_daily", true)
            val showFeels = p.getBoolean("show_feels_like", true)
            val hourlyCount = 6
            val dailyCount = 3
            val backgroundAlpha = p.getFloat("background_alpha", 100f)
            val use24h = p.getBoolean("use_24h", true)
            val clockSize = p.getFloat("digital_clock_size", 0f)
            val dateSize = p.getFloat("date_size", 0f)
            val labelSize = p.getFloat("label_size", 0f)

            val lat = p.getString("location_lat", null)?.toDoubleOrNull() ?: SolarCalculator.LATITUDE
            val lon = p.getString("location_lon", null)?.toDoubleOrNull() ?: SolarCalculator.LONGITUDE

            val cal = Calendar.getInstance(TimeZone.getTimeZone(SolarCalculator.TIME_ZONE))
            val year = cal.get(Calendar.YEAR)
            val dayOfYear = cal.get(Calendar.DAY_OF_YEAR)
            val month = cal.get(Calendar.MONTH) + 1
            val day = cal.get(Calendar.DAY_OF_MONTH)
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            val minute = cal.get(Calendar.MINUTE)
            val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)

            val today = SolarCalculator.solarDay(year, dayOfYear, lat, lon)
            val jalaliText = SolarCalculator.formatJalaliFull(year, month, day)
            val gregorianText = SolarCalculator.formatGregorianFa(year, month, day)
            val weekday = SolarCalculator.weekdayFa(dayOfWeek)

            val key = buildString {
                append(year).append('-').append(dayOfYear).append('|')
                append(hour).append(':').append(minute).append('|')
                append(showJalali).append(showGregorian).append(showSolar)
                append(showCurrentWeather).append(showHourly).append(showDaily)
                append(hourlyCount).append(dailyCount).append(backgroundAlpha)
                append(use24h).append(clockSize).append(dateSize).append(labelSize)
                append(showFeels)
                if (weather != null) {
                    append("|w:").append(weather.temperature).append(',').append(weather.code)
                    append(',').append(weather.hourly.size).append(',').append(weather.daily.size)
                    append(',').append(weather.updatedAt)
                } else {
                    append("|w:null")
                }
            }

            val bitmap = if (key == cachedKey && cachedBitmap != null) {
                cachedBitmap!!
            } else {
                val fresh = drawWidget(
                    hour, minute, weekday,
                    jalaliText, gregorianText, today,
                    showJalali, showGregorian, showSolar,
                    showCurrentWeather, showHourly, showDaily, showFeels,
                    hourlyCount, dailyCount,
                    backgroundAlpha, use24h,
                    clockSize, dateSize, labelSize,
                    weather
                )
                cachedKey = key
                cachedBitmap = fresh
                fresh
            }

            val views = RemoteViews(context.packageName, R.layout.widget_clock)
            views.setImageViewBitmap(R.id.clock_image, bitmap)

            val settingsIntent = Intent(context, SettingsActivity::class.java)
            val pi = PendingIntent.getActivity(
                context, 0, settingsIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.clock_image, pi)

            manager.updateAppWidget(ids, views)
        }

        private fun weatherLabel(code: Int): String = when (code) {
            0 -> "صاف"
            1, 2 -> "کمی ابر"
            3 -> "ابری"
            45, 48 -> "مه"
            in 51..67, in 80..82 -> "باران"
            in 71..77, in 85..86 -> "برف"
            in 95..99 -> "رعد"
            else -> "—"
        }

        private fun drawWidget(
            hour: Int,
            minute: Int,
            weekday: String,
            jalaliText: String,
            gregorianText: String,
            today: SolarCalculator.SolarDay,
            showJalali: Boolean,
            showGregorian: Boolean,
            showSolar: Boolean,
            showCurrentWeather: Boolean,
            showHourly: Boolean,
            showDaily: Boolean,
            showFeels: Boolean,
            hourlyCount: Int,
            dailyCount: Int,
            backgroundAlpha: Float,
            use24h: Boolean,
            clockSize: Float,
            dateSize: Float,
            labelSize: Float,
            weather: WeatherData.Snapshot?
        ): Bitmap {
            val bitmap = Bitmap.createBitmap(W.toInt(), H.toInt(), Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val bgAlpha = ((backgroundAlpha / 100f) * 255f).toInt().coerceIn(0, 255)
            val bgColor = (COLOR_BG and 0x00FFFFFF) or (bgAlpha shl 24)
            canvas.drawColor(bgColor)

            val pad = 26f
            val divPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_DIVIDER
                strokeWidth = 2f
            }
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_GOLD
                textAlign = Paint.Align.RIGHT
                textSize = 24f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val mutedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_MUTED
                textAlign = Paint.Align.RIGHT
                textSize = 19f
            }
            val timeText = if (use24h) "%02d:%02d".format(hour, minute) else {
                val h12 = when { hour == 0 -> 12; hour > 12 -> hour - 12; else -> hour }
                "%d:%02d".format(h12, minute)
            }
            val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_TEXT
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.LEFT
                isFakeBoldText = true
                textSize = if (clockSize > 0f) clockSize.coerceAtMost(76f) else 70f
            }
            val timeBaseline = pad + timePaint.textSize
            canvas.drawText(timeText, pad, timeBaseline, timePaint)

            if (showCurrentWeather) {
                if (weather != null) {
                    val tempPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = COLOR_TEXT
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        textAlign = Paint.Align.RIGHT
                        textSize = 48f
                    }
                    val detailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = COLOR_MUTED
                        textAlign = Paint.Align.RIGHT
                        textSize = 18f
                    }
                    canvas.drawText("${weather.temperature}°", W - pad, timeBaseline - 4f, tempPaint)
                    val sub = if (showFeels) "${weatherLabel(weather.code)} · احساس ${weather.feelsLike}°"
                              else weatherLabel(weather.code)
                    canvas.drawText(sub, W - pad, timeBaseline + 25f, detailPaint)
                } else {
                    mutedPaint.textSize = 18f
                    canvas.drawText("در حال دریافت هوا…", W - pad, timeBaseline, mutedPaint)
                }
            }

            var y = 145f
            val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_MUTED
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                textAlign = Paint.Align.RIGHT
                textSize = if (dateSize > 0f) dateSize.coerceAtMost(30f) else 22f
            }
            val dateLine = buildString {
                append(weekday)
                if (showJalali) { if (isNotEmpty()) append("  ·  "); append(jalaliText) }
                if (showGregorian) { if (isNotEmpty()) append("  ·  "); append(gregorianText) }
            }
            if (dateLine.isNotEmpty()) {
                canvas.drawText(dateLine, W - pad, y, datePaint)
                y += 42f
            }

            if (showSolar) {
                val solarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textAlign = Paint.Align.RIGHT
                    textSize = if (labelSize > 0f) labelSize.coerceAtMost(26f) else 20f
                    color = COLOR_GOLD
                }
                canvas.drawText("طلوع ${SolarCalculator.formatTime(today.sunrise, use24h)}", W - pad, y, solarPaint)
                solarPaint.color = COLOR_ORANGE
                solarPaint.textAlign = Paint.Align.LEFT
                canvas.drawText("غروب ${SolarCalculator.formatTime(today.sunset, use24h)}", pad, y, solarPaint)
                y += 36f
            }

            y += 4f
            canvas.drawLine(pad, y, W - pad, y, divPaint)
            y += 36f

            if (showHourly) {
                canvas.drawText("۶ ساعت آینده", W - pad, y, titlePaint)
                y += 22f
                if (weather != null && weather.hourly.isNotEmpty()) {
                    val items = weather.hourly.take(6)
                    val gapX = 12f
                    val cardW = (W - pad * 2 - gapX) / 2f
                    val cardH = 76f
                    val gapY = 10f
                    items.forEachIndexed { i, item ->
                        val col = i % 2
                        val row = i / 2
                        val left = pad + col * (cardW + gapX)
                        val top = y + row * (cardH + gapY)
                        val rectPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = 0xFF151E2B.toInt()
                            style = Paint.Style.FILL
                        }
                        canvas.drawRoundRect(left, top, left + cardW, top + cardH, 12f, 12f, rectPaint)
                        val hourLabel = if (use24h) "%02d:00".format(item.hour) else {
                            val h12 = when { item.hour == 0 -> 12; item.hour > 12 -> item.hour - 12; else -> item.hour }
                            "%d:00".format(h12)
                        }
                        val hourPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = COLOR_MUTED
                            textSize = 19f
                            textAlign = Paint.Align.LEFT
                        }
                        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = COLOR_TEXT
                            textSize = 26f
                            typeface = Typeface.DEFAULT_BOLD
                            textAlign = Paint.Align.RIGHT
                        }
                        val rainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = 0xFF79C8FF.toInt()
                            textSize = 18f
                            textAlign = Paint.Align.CENTER
                        }
                        canvas.drawText(hourLabel, left + 14f, top + 29f, hourPaint)
                        canvas.drawText("${item.temperature}°", left + cardW - 14f, top + 31f, valuePaint)
                        canvas.drawText("احتمال بارش ${item.rainProb}٪", left + cardW / 2f, top + 60f, rainPaint)
                    }
                    y += 3 * (cardH + gapY) - gapY + 20f
                } else {
                    mutedPaint.textAlign = Paint.Align.CENTER
                    canvas.drawText("پیش‌بینی ساعتی در دسترس نیست", W / 2f, y + 35f, mutedPaint)
                    mutedPaint.textAlign = Paint.Align.RIGHT
                    y += 70f
                }
                canvas.drawLine(pad, y, W - pad, y, divPaint)
                y += 36f
            }

            if (showDaily) {
                canvas.drawText("۳ روز آینده", W - pad, y, titlePaint)
                y += 22f
                if (weather != null && weather.daily.isNotEmpty()) {
                    val items = weather.daily.take(3)
                    items.forEachIndexed { i, item ->
                        val top = y + i * 82f
                        val rectPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = 0xFF151E2B.toInt()
                            style = Paint.Style.FILL
                        }
                        canvas.drawRoundRect(pad, top, W - pad, top + 70f, 12f, 12f, rectPaint)
                        val day = when (item.dayIndex) {
                            0 -> "امروز"
                            1 -> "فردا"
                            else -> SolarCalculator.weekdayFaOffset(item.dayIndex)
                        }
                        val dayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = COLOR_MUTED
                            textSize = 20f
                            textAlign = Paint.Align.LEFT
                        }
                        val tempPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = COLOR_TEXT
                            textSize = 25f
                            typeface = Typeface.DEFAULT_BOLD
                            textAlign = Paint.Align.CENTER
                        }
                        val rainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = 0xFF79C8FF.toInt()
                            textSize = 18f
                            textAlign = Paint.Align.RIGHT
                        }
                        canvas.drawText(day, pad + 14f, top + 42f, dayPaint)
                        canvas.drawText("${item.max}° / ${item.min}°", W / 2f, top + 42f, tempPaint)
                        canvas.drawText("بارش ${item.rainProb}٪", W - pad - 14f, top + 42f, rainPaint)
                    }
                } else {
                    mutedPaint.textAlign = Paint.Align.CENTER
                    canvas.drawText("پیش‌بینی روزانه در دسترس نیست", W / 2f, y + 35f, mutedPaint)
                }
            }
            return bitmap
        }

        private fun schedule(context: Context) {
            val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pending = pendingIntent(context)
            alarm.cancel(pending)

            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val intervalMin = p.getInt("tick_interval_min", 1).coerceIn(1, 15)
            val interval = intervalMin * 60_000L
            val now = System.currentTimeMillis()
            val next = ((now / interval) + 1L) * interval

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarm.canScheduleExactAlarms()) {
                    alarm.setExactAndAllowWhileIdle(AlarmManager.RTC, next, pending)
                } else {
                    alarm.setAndAllowWhileIdle(AlarmManager.RTC, next, pending)
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarm.setExactAndAllowWhileIdle(AlarmManager.RTC, next, pending)
            } else {
                @Suppress("DEPRECATION")
                alarm.set(AlarmManager.RTC, next, pending)
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
