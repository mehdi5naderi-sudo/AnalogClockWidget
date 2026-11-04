package com.mehdi.analogclock

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicBoolean

/** Open-Meteo client: current, hourly and daily forecasts. */
object WeatherData {
    private const val PREFS = "clock_widget_prefs"
    private const val KEY_UPDATED = "weather_updated_at"
    private const val KEY_ATTEMPT = "weather_attempt_at"
    private const val KEY_JSON = "weather_compact"
    private const val KEY_JSON_LEGACY = "weather_json"
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
        val raw = p.getString(KEY_JSON, null)
            ?: p.getString(KEY_JSON_LEGACY, null)
            ?: return null
        return try {
            parseStored(raw, p.getLong(KEY_UPDATED, 0L))
        } catch (_: Exception) {
            null
        }
    }

    /** Parse either compact format or full Open-Meteo payload. */
    private fun parseStored(raw: String, updatedAt: Long): Snapshot {
        val root = JSONObject(raw)
        return if (root.has("t")) {
            parseCompact(root, updatedAt)
        } else {
            parseOpenMeteo(root, updatedAt)
        }
    }

    private fun parseCompact(root: JSONObject, updatedAt: Long): Snapshot {
        val hourly = mutableListOf<HourlyItem>()
        val hArr = root.optJSONArray("h")
        if (hArr != null) {
            for (i in 0 until hArr.length()) {
                val o = hArr.getJSONObject(i)
                hourly.add(
                    HourlyItem(
                        hour = o.getInt("hr"),
                        temperature = o.getInt("t"),
                        code = o.getInt("c"),
                        rainProb = o.optInt("r", 0)
                    )
                )
            }
        }
        val daily = mutableListOf<DailyItem>()
        val dArr = root.optJSONArray("d")
        if (dArr != null) {
            for (i in 0 until dArr.length()) {
                val o = dArr.getJSONObject(i)
                daily.add(
                    DailyItem(
                        dayIndex = o.optInt("i", i),
                        code = o.getInt("c"),
                        max = o.getInt("x"),
                        min = o.getInt("n"),
                        rainProb = o.optInt("r", 0)
                    )
                )
            }
        }
        return Snapshot(
            temperature = root.getInt("t"),
            feelsLike = root.optInt("f", root.getInt("t")),
            code = root.optInt("c", 0),
            max = root.optInt("x", root.getInt("t")),
            min = root.optInt("n", root.getInt("t")),
            rain = root.optInt("r", 0),
            hourly = hourly,
            daily = daily,
            updatedAt = updatedAt
        )
    }

    private fun parseOpenMeteo(root: JSONObject, updatedAt: Long): Snapshot {
        val current = root.getJSONObject("current")
        val daily = root.getJSONObject("daily")
        val hourly = root.optJSONObject("hourly")

        val cal = Calendar.getInstance(TimeZone.getTimeZone(SolarCalculator.TIME_ZONE))
        val todayStr = "%04d-%02d-%02d".format(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
        val currentHour = cal.get(Calendar.HOUR_OF_DAY)

        val hourlyList = mutableListOf<HourlyItem>()
        if (hourly != null) {
            val times = hourly.getJSONArray("time")
            val temps = hourly.getJSONArray("temperature_2m")
            val codes = hourly.getJSONArray("weather_code")
            val rains = hourly.optJSONArray("precipitation_probability")
            for (i in 0 until times.length()) {
                val t = times.getString(i)
                val hourPart = t.substringAfter('T').substringBefore(':').toIntOrNull() ?: continue
                val dayPart = t.substringBefore('T')
                if (dayPart < todayStr) continue
                if (dayPart == todayStr && hourPart < currentHour) continue
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

    private fun toCompact(snapshot: Snapshot): String {
        val root = JSONObject()
        root.put("t", snapshot.temperature)
        root.put("f", snapshot.feelsLike)
        root.put("c", snapshot.code)
        root.put("x", snapshot.max)
        root.put("n", snapshot.min)
        root.put("r", snapshot.rain)
        val hArr = JSONArray()
        snapshot.hourly.take(12).forEach { item ->
            hArr.put(
                JSONObject()
                    .put("hr", item.hour)
                    .put("t", item.temperature)
                    .put("c", item.code)
                    .put("r", item.rainProb)
            )
        }
        root.put("h", hArr)
        val dArr = JSONArray()
        snapshot.daily.take(7).forEach { item ->
            dArr.put(
                JSONObject()
                    .put("i", item.dayIndex)
                    .put("c", item.code)
                    .put("x", item.max)
                    .put("n", item.min)
                    .put("r", item.rainProb)
            )
        }
        root.put("d", dArr)
        return root.toString()
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

    /** Force a network refresh on next opportunity (e.g. after settings save). */
    fun invalidate(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_UPDATED, 0L)
            .putLong(KEY_ATTEMPT, 0L)
            .commit()
    }

    fun refreshIfNeeded(context: Context, force: Boolean = false) {
        if (!isEnabled(context)) return
        if (!fetching.compareAndSet(false, true)) return

        val appContext = context.applicationContext
        val p = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val refreshMs = p.getInt("weather_refresh_min", 30).coerceIn(15, 120) * 60_000L

        if (!force) {
            val updated = p.getLong(KEY_UPDATED, 0L)
            val attempted = p.getLong(KEY_ATTEMPT, 0L)
            if (updated > 0L && now - updated < refreshMs) {
                fetching.set(false)
                return
            }
            if (attempted > 0L && now - attempted < 60_000L) {
                fetching.set(false)
                return
            }
        }

        p.edit().putLong(KEY_ATTEMPT, now).commit()

        val lat = p.getString("location_lat", null)?.toDoubleOrNull() ?: SolarCalculator.LATITUDE
        val lon = p.getString("location_lon", null)?.toDoubleOrNull() ?: SolarCalculator.LONGITUDE
        val tz = (p.getString("location_tz", null) ?: SolarCalculator.TIME_ZONE)
            .ifBlank { SolarCalculator.TIME_ZONE }

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
                    connectTimeout = 15000
                    readTimeout = 15000
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("User-Agent", "SolarClockWidget/2.0")
                }
                try {
                    val code = connection.responseCode
                    if (code !in 200..299) {
                        throw IllegalStateException("Open-Meteo HTTP $code")
                    }
                    val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    val full = JSONObject(body)
                    val snapshot = parseOpenMeteo(full, System.currentTimeMillis())
                    val compact = toCompact(snapshot)

                    // commit() so the next read() on this or another thread sees data immediately
                    p.edit()
                        .putString(KEY_JSON, compact)
                        .remove(KEY_JSON_LEGACY)
                        .putLong(KEY_UPDATED, System.currentTimeMillis())
                        .commit()

                    val manager = AppWidgetManager.getInstance(appContext)
                    val ids = manager.getAppWidgetIds(
                        ComponentName(appContext, ClockWidgetProvider::class.java)
                    )
                    if (ids.isNotEmpty()) {
                        ClockWidgetProvider.update(appContext, manager, ids)
                    }
                } finally {
                    connection.disconnect()
                }
            } catch (_: Exception) {
                // Keep previous data; retry after cooldown
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
