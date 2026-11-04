package com.mehdi.analogclock

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

/** Open-Meteo client. Initial version uses the widget's existing Tehran location. */
object WeatherData {
    private const val PREFS = "clock_widget_prefs"
    private const val KEY_UPDATED = "weather_updated_at"
    private const val KEY_ATTEMPT = "weather_attempt_at"
    private const val KEY_TEMP = "weather_temperature"
    private const val KEY_FEELS = "weather_apparent_temperature"
    private const val KEY_CODE = "weather_code"
    private const val KEY_MAX = "weather_max"
    private const val KEY_MIN = "weather_min"
    private const val KEY_RAIN = "weather_rain_probability"
    private const val REFRESH_MS = 30 * 60 * 1000L
    private val fetching = AtomicBoolean(false)

    data class Snapshot(
        val temperature: Int, val feelsLike: Int, val code: Int,
        val max: Int, val min: Int, val rain: Int, val updatedAt: Long
    )

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("weather_enabled", true)

    fun read(context: Context): Snapshot? {
        if (!isEnabled(context)) return null
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!p.contains(KEY_TEMP)) return null
        return Snapshot(
            p.getInt(KEY_TEMP, 0), p.getInt(KEY_FEELS, 0), p.getInt(KEY_CODE, 0),
            p.getInt(KEY_MAX, 0), p.getInt(KEY_MIN, 0), p.getInt(KEY_RAIN, 0),
            p.getLong(KEY_UPDATED, 0L)
        )
    }

    fun refreshIfNeeded(context: Context) {
        if (!isEnabled(context) || !fetching.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        val p = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - p.getLong(KEY_UPDATED, 0L) < REFRESH_MS ||
            now - p.getLong(KEY_ATTEMPT, 0L) < 5 * 60 * 1000L) {
            fetching.set(false)
            return
        }
        p.edit().putLong(KEY_ATTEMPT, now).apply()
        Thread {
            try {
                val url = URL(
                    "https://api.open-meteo.com/v1/forecast" +
                        "?latitude=35.6892&longitude=51.3890" +
                        "&current=temperature_2m,apparent_temperature,weather_code,precipitation" +
                        "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
                        "&forecast_days=1&timezone=Asia%2FTehran"
                )
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 10000
                    readTimeout = 10000
                    setRequestProperty("Accept", "application/json")
                }
                try {
                    if (connection.responseCode !in 200..299) {
                        throw IllegalStateException("Open-Meteo HTTP " + connection.responseCode)
                    }
                    val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    val root = JSONObject(body)
                    val current = root.getJSONObject("current")
                    val daily = root.getJSONObject("daily")
                    p.edit()
                        .putInt(KEY_TEMP, current.getDouble("temperature_2m").toInt())
                        .putInt(KEY_FEELS, current.getDouble("apparent_temperature").toInt())
                        .putInt(KEY_CODE, current.getInt("weather_code"))
                        .putInt(KEY_MAX, daily.getJSONArray("temperature_2m_max").getDouble(0).toInt())
                        .putInt(KEY_MIN, daily.getJSONArray("temperature_2m_min").getDouble(0).toInt())
                        .putInt(KEY_RAIN, daily.getJSONArray("precipitation_probability_max").optInt(0, 0))
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
                // Preserve the last successful data and retry after the cooldown.
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
