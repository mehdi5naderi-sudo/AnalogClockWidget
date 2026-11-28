package com.mehdi.analogclock

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

object IrimoWeatherData {
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

    fun refreshIfNeeded(
        context: Context,
        force: Boolean = false,
        onComplete: ((String) -> Unit)? = null
    ) {
        val app = context.applicationContext
        val p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val interval = p.getInt("irimo_refresh_min", 15).coerceIn(5, 120) * 60_000L
        if (!force && now - p.getLong(KEY_FETCHED, 0L) < interval) {
            onComplete?.let { callback ->
                main.post { callback(p.getString("irimo_last_status", "اطلاعات قبلی هنوز معتبر است").orEmpty()) }
            }
            return
        }
        if (!fetching.compareAndSet(false, true)) {
            onComplete?.let { callback -> main.post { callback("درخواست قبلی هنوز در حال اجراست؛ چند ثانیه بعد دوباره بزنید.") } }
            return
        }

        // Always use Tehran; ignore any station selection saved by older versions.
        p.edit().remove("irimo_station_url").remove("irimo_station_name").apply()
        val stationUrl = TEHRAN
        Thread {
            var resultMessage: String
            try {
                val html = get(stationUrl)
                val snapshot = parseStation(html)
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
                    resultMessage = "دریافت موفق از تهران: دما ${snapshot.temperature}°، کمینه ${snapshot.minimum}°، بیشینه ${snapshot.maximum}°، دید ${snapshot.visibilityMeters} متر، باد ${snapshot.windMetersPerSecond} متر/ثانیه؛ ساعت منبع ${snapshot.updateTime.ifBlank { "در صفحه پیدا نشد" }}"
                    main.post {
                        val manager = AppWidgetManager.getInstance(app)
                        val ids = manager.getAppWidgetIds(
                            ComponentName(app, ClockWidgetProvider::class.java)
                        )
                        if (ids.isNotEmpty()) ClockWidgetProvider.update(app, manager, ids)
                    }
                } else {
                    val visible = clean(html)
                    val title = Regex("(?is)<title[^>]*>(.*?)</title>").find(html)
                        ?.groupValues?.get(1)?.let { clean(it) }?.take(100).orEmpty()
                    val found = listOf("هوای حاضر", "دمای فعلی", "دمای کنونی", "دید افقی", "سرعت باد", "آخرین بروز رسانی")
                        .filter { visible.contains(it, true) }
                    resultMessage = "اتصال به IRIMO برقرار شد (HTTP 200، ${html.length} نویسه)، اما دمای فعلی استخراج نشد. عنوان صفحه: ${title.ifBlank { "ندارد" }}. عبارت‌های پیدا شده: ${found.joinToString("، ").ifBlank { "هیچ‌کدام" }}"
                    p.edit().putLong(KEY_FETCHED, System.currentTimeMillis() - interval + 60_000L).apply()
                }
            } catch (e: Exception) {
                resultMessage = "خطای دریافت IRIMO: ${e.javaClass.simpleName}: ${e.message ?: "بدون توضیح"}"
                p.edit().putLong(KEY_FETCHED, System.currentTimeMillis() - interval + 60_000L).apply()
            } finally {
                fetching.set(false)
            }
            p.edit().putString("irimo_last_status", resultMessage).apply()
            onComplete?.let { callback -> main.post { callback(resultMessage) } }
        }.apply {
            name = "IRIMOWeatherFetch"
            isDaemon = true
            start()
        }
    }

    private fun parseStation(html: String): Snapshot? {
        // IRIMO pages contain nested tables and inconsistent label/value cell layouts.
        // Read both individual table cells and the complete visible text.
        val cells = Regex("(?is)<t[dh]\\b[^>]*>(.*?)</t[dh]>")
            .findAll(html)
            .map { clean(it.groupValues[1]).replace(Regex("\\s+"), " ").trim() }
            .filter { it.isNotBlank() }
            .toList()
        val text = clean(html).replace('\u200c', ' ').replace(Regex("\\s+"), " ").trim()

        fun digits(value: String): String = persianToEnglish(value)

        fun numberIn(value: String?): Int {
            if (value.isNullOrBlank()) return -1
            val match = Regex("-?\\d+(?:[.,]\\d+)?").find(digits(value)) ?: return -1
            return match.value.replace(",", ".").toDoubleOrNull()?.toInt() ?: -1
        }

        fun valueFor(vararg labels: String): String? {
            for (i in cells.indices) {
                val cell = cells[i]
                val label = labels.firstOrNull { cell.contains(it, true) } ?: continue
                val after = cell.substringAfter(label, "").trim().trim(':', '：', '-', ' ')
                if (numberIn(after) >= 0) return after
                for (j in i + 1 until minOf(i + 4, cells.size)) {
                    if (numberIn(cells[j]) >= 0) return cells[j]
                }
            }
            for (label in labels.sortedByDescending { it.length }) {
                val pattern = Regex(
                    "(?is)" + Regex.escape(label) + "\\s*[:：]?\\s*([^،؛|]{0,35}?[0-9۰-۹٠-٩][0-9۰-۹٠-٩.,]*)"
                )
                val match = pattern.find(text)
                if (match != null) return match.groupValues[1]
            }
            return null
        }

        val temp = numberIn(valueFor("دمای فعلی", "دمای کنونی", "هوای حاضر", "درجه حرارت"))
        if (temp == -1) return null

        var min = numberIn(valueFor("کمینه دما", "حداقل دما", "دمای کمینه", "کمینه"))
        var max = numberIn(valueFor("بیشینه دما", "حداکثر دما", "دمای بیشینه", "بیشینه"))
        if (min < 0 || max < 0) {
            val range = Regex("(?is)(?:کمینه\\s*/\\s*بیشینه|حداقل\\s*/\\s*حداکثر)[^0-9۰-۹٠-٩]{0,20}([0-9۰-۹٠-٩]+)\\s*/\\s*([0-9۰-۹٠-٩]+)")
                .find(text)
            if (range != null) {
                if (min < 0) min = numberIn(range.groupValues[1])
                if (max < 0) max = numberIn(range.groupValues[2])
            }
        }

        val visibility = numberIn(valueFor("دید افقی", "عمق دید"))
        val wind = numberIn(valueFor("سرعت باد"))

        val updateMatch = Regex(
            "(?is)(?:آخرین\\s*بروز\\s*رسانی|آخرین\\s*بروزرسانی|آخرین\\s*به\\s*روز\\s*رسانی|زمان\\s*به\\s*روزرسانی).{0,80}?([0-9۰-۹٠-٩]{1,2}\\s*:\\s*[0-9۰-۹٠-٩]{2})"
        ).find(text)
        val time = updateMatch?.groupValues?.get(1)
            ?.let { digits(it).replace(" ", "") }.orEmpty()

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
            .replace(Regex("(?i)<br\\s*/?>|</(td|th|tr|li|p|div|h[1-6])\\s*>"), "\n")
            .replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ")
            .replace("&zwnj;", "‌")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace(Regex("[\\t ]+"), " ")
            .replace(Regex(" *\\n *"), "\n")
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
