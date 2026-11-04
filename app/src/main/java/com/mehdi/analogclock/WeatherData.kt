package com.mehdi.analogclock

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

/** Open-Meteo client: current, hourly and daily forecasts (Tehran by default). */
object WeatherData {
    private const val PREFS = "clock_widget_prefs"
    private const val KEY_UPDATED = "weather_updated_at"
    private const val KEY_ATTEMPT = "weather_attempt_at"
    private const val KEY_JSON = "weather_json"
    private const val REFRESH_MS = 30 * 60 * 1000L
    private val fetching = AtomicBoolean(false)

    data class HourlyItem(
        val hour: Int,
        val temperature: Int,
        val code: Int,
        val rainProb: Int
    )

    data class DailyItem(
        val dayIndex: Int,
        val code: Int,
        val max: Int,
        val min: Int,
        val rainProb: Int
    )

    data class Snapshot(
        val temperature: Int,
        val feelsLike: Int,
        val code: Int,
        val max: Int,
        val min: Int,
        val rain: Int,
        val hourly: List<HourlyItem>,
        val daily: List<DailyItem>,
        val updatedAt: Long
    )

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("weather_enabled", true)

    fun read(context: Context): Snapshot? {
        if (!isEnabled(context)) return null
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = p.getString(KEY_JSON, null) ?: return null
        return try {
            parseSnapshot(raw, p.getLong(KEY_UPDATED, 0L))
        } catch (_: Exception) {
            null
        }
    }

    private fun parseSnapshot(raw: String, updatedAt: Long): Snapshot {
        val root = JSONObject(raw)
        val current = root.getJSONObject("current")
        val daily = root.getJSONObject("daily")
        val hourly = root.optJSONObject("hourly")

        val hourlyList = mutableListOf<HourlyItem>()
        if (hourly != null) {
            val times = hourly.getJSONArray("time")
            val temps = hourly.getJSONArray("temperature_2m")
            val codes = hourly.getJSONArray("weather_code")
            val rains = hourly.optJSONArray("precipitation_probability")
            val nowMs = System.currentTimeMillis()
            val cal = java.util.Calendar.getInstance(
                java.util.TimeZone.getTimeZone(SolarCalculator.TIME_ZONE)
            )
            for (i in 0 until times.length()) {
                val t = times.getString(i)
                // ISO: 2024-10-05T14:00
                val hourPart = t.substringAfter('T').substringBefore(':').toIntOrNull() ?: continue
                val dayPart = t.substringBefore('T')
                cal.timeInMillis = nowMs
                val todayStr = "%04d-%02d-%02d".format(
                    cal.get(java.util.Calendar.YEAR),
                    cal.get(java.util.Calendar.MONTH) + 1,
                    cal.get(java.util.Calendar.DAY_OF_MONTH)
                )
                // Keep from current hour onward, up to 24 entries
                if (dayPart < todayStr) continue
                if (dayPart == todayStr && hourPart < cal.get(java.util.Calendar.HOUR_OF_DAY)) continue
                hourlyList.add(
                    HourlyItem(
                        hour = hourPart,
                        temperature = temps.getDouble(i).toInt(),
                        code = codes.getInt(i),
                        rainProb = rains?.optInt(i, 0) ?: 0
                    )
                )
                if (hourlyList.size >= 24) break
            }
        }

        val dailyList = mutableListOf<DailyItem>()
        val dCodes = daily.getJSONArray("weather_code")
        val dMax = daily.getJSONArray("temperature_2m_max")
        val dMin = daily.getJSONArray("temperature_2m_min")
        val dRain = daily.optJSONArray("precipitation_probability_max")
        for (i in 0 until dCodes.length()) {
            dailyList.add(
                DailyItem(
                    dayIndex = i,
                    code = dCodes.getInt(i),
                    max = dMax.getDouble(i).toInt(),
                    min = dMin.getDouble(i).toInt(),
                    rainProb = dRain?.optInt(i, 0) ?: 0
                )
            )
        }

        return Snapshot(
            temperature = current.getDouble("temperature_2m").toInt(),
            feelsLike = current.getDouble("apparent_temperature").toInt(),
            code = current.getInt("weather_code"),
            max = if (dMax.length() > 0) dMax.getDouble(0).toInt() else current.getDouble("temperature_2m").toInt(),
            min = if (dMin.length() > 0) dMin.getDouble(0).toInt() else current.getDouble("temperature_2m").toInt(),
            rain = dRain?.optInt(0, 0) ?: 0,
            hourly = hourlyList,
            daily = dailyList,
            updatedAt = updatedAt
        )
    }

    fun weatherIcon(code: Int): String = when (code) {
        0 -> "☀"
        1, 2 -> "🌤"
        3 -> "☁"
        45, 48 -> "🌫"
        in 51..67, in 80..82 -> "🌧"
        in 71..77, in 85..86 -> "❄"
        in 95..99 -> "⛈"
        else -> "☀"
    }

    fun refreshIfNeeded(context: Context) {
        if (!isEnabled(context) || !fetching.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        val p = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val refreshMs = p.getInt("weather_refresh_min", 30).coerceIn(15, 120) * 60_000L
        if (now - p.getLong(KEY_UPDATED, 0L) < refreshMs ||
            now - p.getLong(KEY_ATTEMPT, 0L) < 5 * 60 * 1000L
        ) {
            fetching.set(false)
            return
        }
        p.edit().putLong(KEY_ATTEMPT, now).apply()

        val lat = p.getString("location_lat", null)?.toDoubleOrNull() ?: SolarCalculator.LATITUDE
        val lon = p.getString("location_lon", null)?.toDoubleOrNull() ?: SolarCalculator.LONGITUDE
        val tz = p.getString("location_tz", SolarCalculator.TIME_ZONE) ?: SolarCalculator.TIME_ZONE

        Thread {
            try {
                val url = URL(
                    "https://api.open-meteo.com/v1/forecast" +
                        "?latitude=$lat&longitude=$lon" +
                        "&current=temperature_2m,apparent_temperature,weather_code,precipitation" +
                        "&hourly=temperature_2m,weather_code,precipitation_probability" +
                        "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
                        "&forecast_days=7&timezone=${tz.replace("/", "%2F")}"
                )
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 12000
                    readTimeout = 12000
                    setRequestProperty("Accept", "application/json")
                }
                try {
                    if (connection.responseCode !in 200..299) {
                        throw IllegalStateException("Open-Meteo HTTP " + connection.responseCode)
                    }
                    val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    // Validate JSON before storing
                    JSONObject(body)
                    p.edit()
                        .putString(KEY_JSON, body)
                        .putLong(KEY_UPDATED, System.currentTimeMillis())
                        .apply()

                    val manager = AppWidgetManager.getInstance(appContext)
                    val ids = manager.getAppWidgetIds(
                        ComponentName(appContext, ClockWidgetProvider::class.java)
                    )
                    if (ids.isNotEmpty()) ClockWidgetProvider.update(appContext, manager, ids)
                } finally {
                    connection.disconnect()
                }
            } catch (_: Exception) {
                // Keep last successful data
            } finally {
                fetching.set(false)
            }
        }.apply {
            name = "OpenMeteoWeather"
            isDaemon = true
            start()
        }
    }
}
