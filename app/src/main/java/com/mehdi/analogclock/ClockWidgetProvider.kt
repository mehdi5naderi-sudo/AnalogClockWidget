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

        private const val W = 900f
        private const val H = 420f

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

            WeatherData.refreshIfNeeded(context)
            val weather = WeatherData.read(context)
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

            val showJalali = p.getBoolean("show_jalali", true)
            val showGregorian = p.getBoolean("show_gregorian", true)
            val showSolar = p.getBoolean("show_solar", true)
            val showCurrentWeather = p.getBoolean("show_current_weather", true)
            val showHourly = p.getBoolean("show_hourly", true)
            val showDaily = p.getBoolean("show_daily", true)
            val showFeels = p.getBoolean("show_feels_like", true)
            val hourlyCount = p.getInt("hourly_count", 7).coerceIn(4, 12)
            val dailyCount = p.getInt("daily_count", 5).coerceIn(3, 7)
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
                weather?.let {
                    append("|w:").append(it.temperature).append(',').append(it.code)
                    append(',').append(it.hourly.size).append(',').append(it.daily.size)
                    append(',').append(it.updatedAt)
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

            val pad = 28f
            var y = pad + 8f

            // ── Header: time (left) + current weather (right) ──
            val timeText = if (use24h) {
                "%02d:%02d".format(hour, minute)
            } else {
                val h12 = when {
                    hour == 0 -> 12
                    hour > 12 -> hour - 12
                    else -> hour
                }
                "%d:%02d".format(h12, minute)
            }

            val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_TEXT
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.LEFT
                isFakeBoldText = true
                textSize = if (clockSize > 0f) clockSize else 72f
            }
            val timeBounds = Rect()
            timePaint.getTextBounds(timeText, 0, timeText.length, timeBounds)
            val timeBaseline = y + timeBounds.height().toFloat()
            canvas.drawText(timeText, pad, timeBaseline, timePaint)

            if (showCurrentWeather && weather != null) {
                val icon = WeatherData.weatherIcon(weather.code)
                val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = COLOR_GOLD
                    textAlign = Paint.Align.RIGHT
                    textSize = 40f
                }
                val tempPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = COLOR_TEXT
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textAlign = Paint.Align.RIGHT
                    textSize = 48f
                }
                val detailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = COLOR_MUTED
                    textAlign = Paint.Align.RIGHT
                    textSize = 22f
                }
                val rightX = W - pad
                canvas.drawText("${weather.temperature}°", rightX, timeBaseline - 4f, tempPaint)
                val tempW = tempPaint.measureText("${weather.temperature}°")
                canvas.drawText(icon, rightX - tempW - 12f, timeBaseline - 6f, iconPaint)
                val sub = buildString {
                    if (showFeels) append("احساس ${weather.feelsLike}°")
                    if (weather.rain > 0) {
                        if (isNotEmpty()) append(" · ")
                        append("بارش ${weather.rain}%")
                    }
                }
                if (sub.isNotEmpty()) {
                    canvas.drawText(sub, rightX, timeBaseline + 28f, detailPaint)
                }
            }

            y = timeBaseline + 18f

            // Weekday + dates
            val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_MUTED
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                textAlign = Paint.Align.LEFT
                textSize = if (dateSize > 0f) dateSize else 26f
            }
            val dateLine = buildString {
                append(weekday)
                if (showJalali) {
                    if (isNotEmpty()) append("  ·  ")
                    append(jalaliText)
                }
                if (showGregorian) {
                    if (isNotEmpty()) append("  ·  ")
                    append(gregorianText)
                }
            }
            if (dateLine.isNotEmpty()) {
                y += datePaint.textSize + 4f
                canvas.drawText(dateLine, pad, y, datePaint)
            }

            // Sunrise / sunset
            if (showSolar) {
                val solarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textAlign = Paint.Align.LEFT
                    textSize = if (labelSize > 0f) labelSize else 24f
                }
                y += solarPaint.textSize + 14f
                val sunriseStr = "طلوع ${SolarCalculator.formatTime(today.sunrise, use24h)}"
                val sunsetStr = "غروب ${SolarCalculator.formatTime(today.sunset, use24h)}"
                solarPaint.color = COLOR_GOLD
                canvas.drawText(sunriseStr, pad, y, solarPaint)
                val sunW = solarPaint.measureText(sunriseStr)
                solarPaint.color = COLOR_ORANGE
                canvas.drawText(sunsetStr, pad + sunW + 28f, y, solarPaint)
            }

            // Divider
            val divPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_DIVIDER
                strokeWidth = 2f
            }
            y += 18f
            canvas.drawLine(pad, y, W - pad, y, divPaint)
            y += 16f

            // Hourly forecast
            if (showHourly && weather != null && weather.hourly.isNotEmpty()) {
                val items = weather.hourly.take(hourlyCount)
                val colW = (W - pad * 2) / items.size.coerceAtLeast(1)
                val hourPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = COLOR_MUTED
                    textAlign = Paint.Align.CENTER
                    textSize = 20f
                }
                val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = COLOR_GOLD
                    textAlign = Paint.Align.CENTER
                    textSize = 28f
                }
                val tempPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = COLOR_TEXT
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textAlign = Paint.Align.CENTER
                    textSize = 24f
                }
                items.forEachIndexed { i, item ->
                    val cx = pad + colW * i + colW / 2f
                    val hLabel = if (use24h) {
                        "%02d".format(item.hour)
                    } else {
                        val h12 = when {
                            item.hour == 0 -> 12
                            item.hour > 12 -> item.hour - 12
                            else -> item.hour
                        }
                        h12.toString()
                    }
                    canvas.drawText(hLabel, cx, y + 18f, hourPaint)
                    canvas.drawText(WeatherData.weatherIcon(item.code), cx, y + 50f, iconPaint)
                    canvas.drawText("${item.temperature}°", cx, y + 82f, tempPaint)
                }
                y += 96f
                canvas.drawLine(pad, y, W - pad, y, divPaint)
                y += 14f
            }

            // Daily forecast
            if (showDaily && weather != null && weather.daily.isNotEmpty()) {
                val items = weather.daily.take(dailyCount)
                val colW = (W - pad * 2) / items.size.coerceAtLeast(1)
                val dayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = COLOR_MUTED
                    textAlign = Paint.Align.CENTER
                    textSize = 20f
                }
                val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = COLOR_GOLD
                    textAlign = Paint.Align.CENTER
                    textSize = 28f
                }
                val tempPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = COLOR_TEXT
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textAlign = Paint.Align.CENTER
                    textSize = 22f
                }
                items.forEachIndexed { i, item ->
                    val cx = pad + colW * i + colW / 2f
                    val label = when (item.dayIndex) {
                        0 -> "امروز"
                        1 -> "فردا"
                        else -> SolarCalculator.weekdayFaOffset(item.dayIndex)
                    }
                    // Shorten long weekday names
                    val shortLabel = when {
                        label.length > 5 -> label.take(4)
                        else -> label
                    }
                    canvas.drawText(shortLabel, cx, y + 18f, dayPaint)
                    canvas.drawText(WeatherData.weatherIcon(item.code), cx, y + 50f, iconPaint)
                    canvas.drawText("${item.max}°/${item.min}°", cx, y + 80f, tempPaint)
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
