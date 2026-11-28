package com.mehdi.analogclock

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

object IrimoWeatherData {
    data class Station(val name: String, val url: String) {
        override fun toString() = name
    }

    data class Snapshot(
        val temperature: Int,
        val minimum: Int,
        val maximum: Int,
        val visibilityMeters: Int,
        val windMetersPerSecond: Int,
        val updateTime: String
    )

    private const val PREFS = "clock_widget_prefs"
    private const val HOME = "https://www.irimo.ir/far/index.php"
    private const val TEHRAN = "https://www.irimo.ir/far/wd/701-%D9%88%D8%B6%D8%B9-%D9%87%D9%88%D8%A7%DB%8C-%D8%AA%D9%87%D8%B1%D8%A7%D9%86.html?id=17561"
    private const val KEY_FETCHED = "irimo_fetched_at"
    private val pool = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val fetching = AtomicBoolean(false)

    fun loadStations(ctx: Context, cb: (List<Station>) -> Unit) {
        pool.execute {
            val found = linkedMapOf<String, Station>()
            try {
                val html = get(HOME)
                Regex("(?is)<a\\b[^>]*href=[\\\"']([^\\\"']+)[\\\"'][^>]*>(.*?)</a>")
                    .findAll(html).forEach {
                        val href = it.groupValues[1]
                        val name = clean(it.groupValues[2])
                        if (name.isNotBlank() &&
                            (href.contains("wd_id=701") || href.contains("/wd/701-") || href.contains("id=17561"))
                        ) {
                            found[name] = Station(name, absolute(href))
                        }
                    }
            } catch (_: Exception) {
            }
            if (found.values.none { it.url.contains("17561") }) {
                found["تهران"] = Station("تهران", TEHRAN)
            }
            main.post { cb(found.values.toList()) }
        }
    }

    fun read(context: Context): Snapshot? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val temperature = p.getInt("irimo_temperature", Int.MIN_VALUE)
        if (temperature == Int.MIN_VALUE) return null
        return Snapshot(
            temperature = temperature,
            minimum = p.getInt("irimo_minimum", -1),
            maximum = p.getInt("irimo_maximum", -1),
            visibilityMeters = p.getInt("irimo_visibility", -1),
            windMetersPerSecond = p.getInt("irimo_wind", -1),
            updateTime = p.getString("irimo_update_time", "").orEmpty()
        )
    }

    fun refreshIfNeeded(context: Context, force: Boolean = false) {
        val app = context.applicationContext
        val p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val interval = p.getInt("irimo_refresh_min", 15).coerceIn(5, 120) * 60_000L
        if (!force && now - p.getLong(KEY_FETCHED, 0L) < interval) return
        if (!fetching.compareAndSet(false, true)) return

        val stationUrl = p.getString("irimo_station_url", null)?.takeIf { it.isNotBlank() } ?: TEHRAN
        Thread {
            try {
                val snapshot = parseStation(get(stationUrl))
                if (snapshot != null) {
                    p.edit()
                        .putInt("irimo_temperature", snapshot.temperature)
                        .putInt("irimo_minimum", snapshot.minimum)
                        .putInt("irimo_maximum", snapshot.maximum)
                        .putInt("irimo_visibility", snapshot.visibilityMeters)
                        .putInt("irimo_wind", snapshot.windMetersPerSecond)
                        .putString("irimo_update_time", snapshot.updateTime)
                        .putLong(KEY_FETCHED, System.currentTimeMillis())
                        .apply()
                    main.post {
                        val manager = AppWidgetManager.getInstance(app)
                        val ids = manager.getAppWidgetIds(
                            ComponentName(app, ClockWidgetProvider::class.java)
                        )
                        if (ids.isNotEmpty()) ClockWidgetProvider.update(app, manager, ids)
                    }
                } else {
                    p.edit().putLong(KEY_FETCHED, System.currentTimeMillis() - interval + 60_000L).apply()
                }
            } catch (_: Exception) {
                p.edit().putLong(KEY_FETCHED, System.currentTimeMillis() - interval + 60_000L).apply()
            } finally {
                fetching.set(false)
            }
        }.apply {
            name = "IRIMOWeatherFetch"
            isDaemon = true
            start()
        }
    }

    private fun parseStation(html: String): Snapshot? {
        val rows = Regex("(?is)<tr\\b[^>]*>(.*?)</tr>").findAll(html).map { match ->
            Regex("(?is)<t[dh]\\b[^>]*>(.*?)</t[dh]>").findAll(match.groupValues[1])
                .map { clean(it.groupValues[1]) }.filter { it.isNotBlank() }.toList()
        }.filter { it.isNotEmpty() }.toList()

        fun valueFor(vararg labels: String): String? {
            for (cells in rows) {
                val index = cells.indexOfFirst { cell -> labels.any { cell.contains(it, true) } }
                if (index >= 0) {
                    val sameCell = cells[index].substringAfter(":").trim()
                    if (sameCell.isNotBlank() && sameCell != cells[index]) return sameCell
                    for (i in index + 1 until cells.size) {
                        if (cells[i].isNotBlank()) return cells[i]
                    }
                }
            }
            val lines = clean(html).lines()
            for (i in lines.indices) {
                if (labels.any { lines[i].contains(it, true) }) {
                    val sameLine = lines[i].substringAfter(":").trim()
                    if (sameLine.isNotBlank() && sameLine != lines[i]) return sameLine
                    return lines.getOrNull(i + 1)?.takeIf { it.isNotBlank() }
                }
            }
            return null
        }

        fun number(value: String?): Int {
            if (value.isNullOrBlank()) return -1
            val normalized = persianToEnglish(value)
            val match = Regex("-?\\d+(?:[.,]\\d+)?").find(normalized) ?: return -1
            return match.value.replace(",", ".").toDoubleOrNull()?.toInt() ?: -1
        }

        val temp = number(valueFor("هوای حاضر", "دمای فعلی", "دمای کنونی"))
        if (temp == -1) return null
        val min = number(valueFor("کمینه دما", "حداقل دما", "کمینه"))
        val max = number(valueFor("بیشینه دما", "حداکثر دما", "بیشینه"))
        val visibility = number(valueFor("دید افقی", "عمق دید"))
        val wind = number(valueFor("سرعت باد"))
        val updateRaw = valueFor("آخرین بروز رسانی", "آخرین بروزرسانی", "آخرین به روز رسانی", "زمان به روزرسانی")
        val time = updateRaw?.let {
            Regex("\\d{1,2}:\\d{2}").find(persianToEnglish(it))?.value
        }.orEmpty()

        return Snapshot(temp, min, max, visibility, wind, time)
    }

    private fun get(url: String): String {
        var connection: HttpURLConnection? = null
        try {
            connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 18000
            connection.readTimeout = 22000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36"
            )
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8")
            connection.setRequestProperty("Accept-Language", "fa-IR,fa;q=0.9,en;q=0.8")
            connection.setRequestProperty("Referer", HOME)
            connection.setRequestProperty("Cache-Control", "no-cache")
            val code = connection.responseCode
            if (code !in 200..299) error("HTTP $code")
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText().take(2_000_000) }
        } finally {
            connection?.disconnect()
        }
    }

    private fun clean(html: String): String =
        html.replace(Regex("(?is)<script[^>]*>.*?</script>|<style[^>]*>.*?</style>"), " ")
            .replace(Regex("(?i)<br\\s*/?>|</(td|th|tr|li|p|div|h[1-6])\\s*>"), "\\n")
            .replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ")
            .replace("&zwnj;", "‌")
            .replace("&amp;", "&")
            .replace("&quot;", "\\"")
            .replace("&#39;", "'")
            .replace(Regex("[\\t ]+"), " ")
            .replace(Regex(" *\\n *"), "\\n")
            .trim()

    private fun persianToEnglish(value: String): String {
        val out = StringBuilder(value.length)
        value.forEach { ch ->
            val converted = when (ch) {
                in '۰'..'۹' -> ('0'.code + (ch.code - '۰'.code)).toChar()
                in '٠'..'٩' -> ('0'.code + (ch.code - '٠'.code)).toChar()
                else -> ch
            }
            out.append(converted)
        }
        return out.toString()
    }

    private fun absolute(href: String): String = when {
        href.startsWith("http") -> href
        href.startsWith("//") -> "https:$href"
        href.startsWith("/") -> "https://www.irimo.ir$href"
        else -> "https://www.irimo.ir/far/$href"
    }
}
