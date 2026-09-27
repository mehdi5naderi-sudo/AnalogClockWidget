package com.mehdi.analogclock

import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * Approximate solar sunrise/sunset calculator (NOAA-style equations).
 * Location is fixed to Tehran by default.
 *
 * Results are cached per calendar day — the four daily indicators only
 * change once a day, so we avoid recomputing on every minute tick.
 */
object SolarCalculator {

    const val LATITUDE = 35.6892
    const val LONGITUDE = 51.3890
    const val TIME_ZONE = "Asia/Tehran"

    data class SolarDay(
        val sunrise: Int,          // minutes from midnight
        val sunset: Int,
        val dayLengthMinutes: Int
    )

    // Cache: key = year * 1000 + dayOfYear
    private val cache = HashMap<Int, SolarDay>(4)

    fun solarDay(year: Int, dayOfYear: Int): SolarDay {
        val key = year * 1000 + dayOfYear
        cache[key]?.let { return it }

        val sunrise = solarEventMinutes(year, dayOfYear, sunrise = true)
        val sunset = solarEventMinutes(year, dayOfYear, sunrise = false)
        val day = SolarDay(sunrise, sunset, sunset - sunrise)

        // Keep cache small (today + tomorrow is enough)
        if (cache.size > 8) cache.clear()
        cache[key] = day
        return day
    }

    private fun solarEventMinutes(year: Int, dayOfYear: Int, sunrise: Boolean): Int {
        val gamma = 2.0 * Math.PI / 365.0 * (dayOfYear - 1)

        val equationOfTime = 229.18 * (
            0.000075 +
                0.001868 * cos(gamma) -
                0.032077 * sin(gamma) -
                0.014615 * cos(2 * gamma) -
                0.040849 * sin(2 * gamma)
            )

        val declination =
            0.006918 -
                0.399912 * cos(gamma) +
                0.070257 * sin(gamma) -
                0.006758 * cos(2 * gamma) +
                0.000907 * sin(2 * gamma) -
                0.002697 * cos(3 * gamma) +
                0.00148 * sin(3 * gamma)

        val latitudeRad = Math.toRadians(LATITUDE)
        val zenithRad = Math.toRadians(90.833)

        val cosHourAngle =
            (cos(zenithRad) / (cos(latitudeRad) * cos(declination))) -
                (sin(latitudeRad) * sin(declination) /
                    (cos(latitudeRad) * cos(declination)))

        if (cosHourAngle <= -1.0) return if (sunrise) 0 else 1439
        if (cosHourAngle >= 1.0) return 720

        val hourAngle = Math.toDegrees(acos(cosHourAngle))
        val solarNoon = 720.0 - 4.0 * LONGITUDE - equationOfTime

        val utcMinutes = if (sunrise) {
            solarNoon - hourAngle * 4.0
        } else {
            solarNoon + hourAngle * 4.0
        }

        val tz = java.util.TimeZone.getTimeZone(TIME_ZONE)
        val reference = java.util.Calendar.getInstance(tz)
        val zoneOffsetMinutes = tz.getOffset(reference.timeInMillis) / 60000.0

        return floor(utcMinutes + zoneOffsetMinutes + 0.5)
            .toInt()
            .coerceIn(0, 1439)
    }

    fun formatTime(minutes: Int): String =
        "%02d:%02d".format(minutes / 60, minutes % 60)

    fun formatDuration(minutes: Int): String =
        "%02d:%02d".format(minutes / 60, minutes % 60)

    fun formatDelta(minutes: Int): String =
        if (minutes >= 60) {
            "${minutes / 60}س ${"%02d".format(minutes % 60)}د"
        } else {
            "$minutes دقیقه"
        }
}
