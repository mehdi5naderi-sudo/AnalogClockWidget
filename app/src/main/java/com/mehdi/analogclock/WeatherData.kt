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

/**
 * Weather sources:
 * - Open-Meteo Forecast API: current conditions, visibility, wind, clouds, hourly and daily forecasts
 * - Open-Meteo Air Quality API: US AQI
 */
object WeatherData {
    private const val PREFS = "clock_widget_prefs"
    private const val KEY_UPDATED = "weather_updated_at"
    private const val KEY_ATTEMPT = "weather_attempt_at"
    private const val KEY_JSON = "weather_compact_v6"
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
        val windSpeed: Int,       // km/h from Open-Meteo
        val usAqi: Int,           // from Open-Meteo AQ
        val obsTime: String,      // Open-Meteo current observation time HH:mm
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
            parseCompact(JSONObject(raw), p.getLong(KEY_UPDATED, 0L))
        } catch (_: Exception) {
            null
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
            windSpeed = root.optInt("w", 0),
            usAqi = root.optInt("aqi", -1),
            obsTime = root.optString("ot", ""),
            hourly = hourly,
            daily = daily,
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
        root.put("w", snapshot.windSpeed)
        root.put("aqi", snapshot.usAqi)
        root.put("ot", snapshot.obsTime)
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

    fun invalidate(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_UPDATED, 0L)
            .putLong(KEY_ATTEMPT, 0L)
            .remove(KEY_JSON)
            .remove("weather_compact_v5")
            .remove("weather_compact_v4")
            .remove("weather_compact_v3")
            .commit()
    }

    private fun fetchUsAqi(lat: Double, lon: Double): Int {
        val url = URL(
            "https://air-quality-api.open-meteo.com/v1/air-quality" +
                "?latitude=$lat&longitude=$lon&current=us_aqi"
        )
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12000
            readTimeout = 12000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "SolarClockWidget/2.5")
        }
        try {
            if (conn.responseCode !in 200..299) return -1
            val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val root = JSONObject(body)
            return root.optJSONObject("current")?.optInt("us_aqi", -1) ?: -1
        } finally {
            conn.disconnect()
        }
    }

    private fun extractHourly(
        hourly: JSONObject?,
        count: Int = 6,
        referenceTime: String
    ): List<HourlyItem> {
        if (hourly == null) return emptyList()
        val times = hourly.optJSONArray("time") ?: return emptyList()
        val temps = hourly.optJSONArray("temperature_2m") ?: return emptyList()
        val codes = hourly.optJSONArray("weather_code")
        val rains = hourly.optJSONArray("precipitation_probability")
        val n = minOf(times.length(), temps.length())
        if (n == 0) return emptyList()

        val nowFloor = referenceTime.take(13).let { value ->
            if (value.length == 13 && value[10] == 'T') "$value:00"
            else {
                val cal = Calendar.getInstance(TimeZone.getTimeZone(SolarCalculator.TIME_ZONE))
                "%04d-%02d-%02dT%02d:00".format(
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH) + 1,
                    cal.get(Calendar.DAY_OF_MONTH),
                    cal.get(Calendar.HOUR_OF_DAY)
                )
            }
        }

        var start = 0
        for (i in 0 until n) {
            val t = times.optString(i, "")
            if (t.isNotEmpty() && t >= nowFloor) {
                start = i
                break
            }
            start = i
        }
        if (times.optString(n - 1, "") < nowFloor) {
            start = maxOf(0, n - count)
        }

        val result = ArrayList<HourlyItem>(count)
        var i = start
        while (i < n && result.size < count) {
            val t = times.optString(i, "")
            val hourPart = t.substringAfter('T', "").substringBefore(':').toIntOrNull()
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
                // 1) Open-Meteo forecast
                val forecastUrl = URL(
                    "https://api.open-meteo.com/v1/forecast" +
                        "?latitude=$lat&longitude=$lon" +
                        "&current=temperature_2m,apparent_temperature,weather_code,wind_speed_10m" +
                        "&hourly=temperature_2m,weather_code,precipitation_probability" +
                        "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
                        "&forecast_days=3&timezone=${tz.replace("/", "%2F")}"
                )
                val fConn = (forecastUrl.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15000
                    readTimeout = 15000
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("User-Agent", "SolarClockWidget/2.5")
                }

                var temperature = 0
                var feelsLike = 0
                var code = 0
                var windKmh = 0
                var obsTime = ""
                var hourlyList = emptyList<HourlyItem>()
                var dailyList = emptyList<DailyItem>()

                try {
                    if (fConn.responseCode in 200..299) {
                        val body = fConn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                        val root = JSONObject(body)
                        val current = root.getJSONObject("current")
                        val daily = root.getJSONObject("daily")
                        temperature = current.getDouble("temperature_2m").toInt()
                        feelsLike = current.optDouble("apparent_temperature", temperature.toDouble()).toInt()
                        code = current.optInt("weather_code", 0)
                        windKmh = current.optDouble("wind_speed_10m", 0.0).toInt()
                        obsTime = current.optString("time", "").substringAfter("T", "").take(5)
                        hourlyList = extractHourly(root.optJSONObject("hourly"), 5, current.optString("time", ""))
                        dailyList = extractDaily(daily)
                    }
                } finally {
                    fConn.disconnect()
                }

                // 2) US AQI from Open-Meteo Air Quality API
                // US AQI
                var usAqi = -1
                try {
                    usAqi = fetchUsAqi(lat, lon)
                } catch (_: Exception) {}

                if (hourlyList.size < 3 && dailyList.isEmpty()) return@Thread

                val snapshot = Snapshot(
                    temperature = temperature,
                    feelsLike = feelsLike,
                    code = code,
                    max = dailyList.firstOrNull()?.max ?: temperature,
                    min = dailyList.firstOrNull()?.min ?: temperature,
                    rain = dailyList.firstOrNull()?.rainProb ?: 0,
                    windSpeed = windKmh,
                    usAqi = usAqi,
                    obsTime = obsTime,
                    hourly = hourlyList,
                    daily = dailyList,
                    updatedAt = System.currentTimeMillis()
                )

                p.edit()
                    .putString(KEY_JSON, toCompact(snapshot))
                    .putLong(KEY_UPDATED, System.currentTimeMillis())
                    .commit()

                val manager = AppWidgetManager.getInstance(appContext)
                val ids = manager.getAppWidgetIds(
                    ComponentName(appContext, ClockWidgetProvider::class.java)
                )
                if (ids.isNotEmpty()) {
                    ClockWidgetProvider.update(appContext, manager, ids)
                }
            } catch (_: Exception) {
            } finally {
                fetching.set(false)
            }
        }.apply {
            name = "WeatherFetch"
            isDaemon = true
            start()
        }
    }
}
