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
    private const val KEY_JSON = "weather_compact_v2"
    private const val KEY_JSON_V1 = "weather_compact"
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
            ?: p.getString(KEY_JSON_V1, null)
            ?: p.getString(KEY_JSON_LEGACY, null)
            ?: return null
        return try {
            parseStored(raw, p.getLong(KEY_UPDATED, 0L))
        } catch (_: Exception) {
            null
        }
    }

    private fun parseStored(raw: String, updatedAt: Long): Snapshot {
        val root = JSONObject(raw)
        return if (root.has("t") && !root.has("current")) {
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
                val o = hArr.optJSONObject(i) ?: continue
                hourly.add(
                    HourlyItem(
                        hour = o.optInt("hr", 0),
                        temperature = o.optInt("t", 0),
                        code = o.optInt("c", 0),
                        rainProb = o.optInt("r", 0)
                    )
                )
            }
        }
        val daily = mutableListOf<DailyItem>()
        val dArr = root.optJSONArray("d")
        if (dArr != null) {
            for (i in 0 until dArr.length()) {
                val o = dArr.optJSONObject(i) ?: continue
                daily.add(
                    DailyItem(
                        dayIndex = o.optInt("i", i),
                        code = o.optInt("c", 0),
                        max = o.optInt("x", 0),
                        min = o.optInt("n", 0),
                        rainProb = o.optInt("r", 0)
                    )
                )
            }
        }
        return Snapshot(
            temperature = root.optInt("t", 0),
            feelsLike = root.optInt("f", root.optInt("t", 0)),
            code = root.optInt("c", 0),
            max = root.optInt("x", root.optInt("t", 0)),
            min = root.optInt("n", root.optInt("t", 0)),
            rain = root.optInt("r", 0),
            hourly = hourly,
            daily = daily,
            updatedAt = updatedAt
        )
    }

    /**
     * Build hourly list: find first index with time >= current hour (Tehran),
     * then take the next [count] consecutive slots. Never skip holes.
     */
    private fun extractHourly(hourly: JSONObject?, count: Int = 12): List<HourlyItem> {
        if (hourly == null) return emptyList()
        val times = hourly.optJSONArray("time") ?: return emptyList()
        val temps = hourly.optJSONArray("temperature_2m") ?: return emptyList()
        val codes = hourly.optJSONArray("weather_code")
        val rains = hourly.optJSONArray("precipitation_probability")
        val n = minOf(times.length(), temps.length())
        if (n == 0) return emptyList()

        val cal = Calendar.getInstance(TimeZone.getTimeZone(SolarCalculator.TIME_ZONE))
        val nowFloor = "%04d-%02d-%02dT%02d:00".format(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH),
            cal.get(Calendar.HOUR_OF_DAY)
        )

        var start = 0
        for (i in 0 until n) {
            val t = times.optString(i, "")
            if (t.isNotEmpty() && t >= nowFloor) {
                start = i
                break
            }
            start = i // if all past, last index; loop below still fills what it can
        }
        // If every timestamp is still in the past, start from 0 to show something
        if (times.optString(n - 1, "") < nowFloor) {
            start = maxOf(0, n - count)
        }

        val result = ArrayList<HourlyItem>(count)
        var i = start
        while (i < n && result.size < count) {
            val t = times.optString(i, "")
            val hourPart = t.substringAfter('T', "")
                .substringBefore(':')
                .toIntOrNull()
            val temp = temps.optDouble(i, Double.NaN)
            if (hourPart != null && !temp.isNaN()) {
                result.add(
                    HourlyItem(
                        hour = hourPart,
                        temperature = temp.toInt(),
                        code = codes?.optInt(i, 0) ?: 0,
                        rainProb = rains?.optInt(i, 0) ?: 0
                    )
                )
            }
            i++
        }
        return result
    }

    private fun extractDaily(daily: JSONObject): List<DailyItem> {
        val dCodes = daily.optJSONArray("weather_code") ?: return emptyList()
        val dMax = daily.optJSONArray("temperature_2m_max") ?: return emptyList()
        val dMin = daily.optJSONArray("temperature_2m_min") ?: return emptyList()
        val dRain = daily.optJSONArray("precipitation_probability_max")
        val list = ArrayList<DailyItem>(dCodes.length())
        for (i in 0 until dCodes.length()) {
            list.add(
                DailyItem(
                    dayIndex = i,
                    code = dCodes.optInt(i, 0),
                    max = dMax.optDouble(i, 0.0).toInt(),
                    min = dMin.optDouble(i, 0.0).toInt(),
                    rainProb = dRain?.optInt(i, 0) ?: 0
                )
            )
        }
        return list
    }

    private fun parseOpenMeteo(root: JSONObject, updatedAt: Long): Snapshot {
        val current = root.getJSONObject("current")
        val daily = root.getJSONObject("daily")
        val hourlyObj = root.optJSONObject("hourly")

        val hourlyList = extractHourly(hourlyObj, 6)
        val dailyList = extractDaily(daily)

        val temp = current.getDouble("temperature_2m").toInt()
        return Snapshot(
            temperature = temp,
            feelsLike = current.optDouble("apparent_temperature", temp.toDouble()).toInt(),
            code = current.optInt("weather_code", 0),
            max = dailyList.firstOrNull()?.max ?: temp,
            min = dailyList.firstOrNull()?.min ?: temp,
            rain = dailyList.firstOrNull()?.rainProb ?: 0,
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
        for (item in snapshot.hourly) {
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
        for (item in snapshot.daily) {
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

    fun invalidate(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_UPDATED, 0L)
            .putLong(KEY_ATTEMPT, 0L)
            .remove(KEY_JSON)
            .remove(KEY_JSON_V1)
            .remove(KEY_JSON_LEGACY)
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
            if (attempted > 0L && now - attempted < 45_000L) {
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
                        "&forecast_days=3&timezone=${tz.replace("/", "%2F")}"
                )
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15000
                    readTimeout = 15000
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("User-Agent", "SolarClockWidget/2.2")
                }
                try {
                    val code = connection.responseCode
                    if (code !in 200..299) {
                        throw IllegalStateException("Open-Meteo HTTP $code")
                    }
                    val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    val full = JSONObject(body)
                    val snapshot = parseOpenMeteo(full, System.currentTimeMillis())
                    // Guard: never store a nearly-empty hourly list as "success"
                    if (snapshot.hourly.size < 3) {
                        throw IllegalStateException("Hourly parse too short: ${snapshot.hourly.size}")
                    }
                    val compact = toCompact(snapshot)

                    p.edit()
                        .putString(KEY_JSON, compact)
                        .remove(KEY_JSON_V1)
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
