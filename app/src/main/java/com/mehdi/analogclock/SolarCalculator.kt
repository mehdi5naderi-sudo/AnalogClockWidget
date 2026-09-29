package com.mehdi.analogclock

import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * Approximate solar sunrise/sunset calculator (NOAA-style equations).
 * Location is fixed to Tehran by default.
 *
 * Also provides simple Gregorian → Jalali date conversion for the widget.
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

    data class JalaliDate(
        val year: Int,
        val month: Int,   // 1..12
        val day: Int
    )

    private val PERSIAN_MONTHS = arrayOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
    )

    private val PERSIAN_DIGITS = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')

    // Cache: key = year * 1000 + dayOfYear
    private val cache = HashMap<Int, SolarDay>(4)

    fun solarDay(year: Int, dayOfYear: Int): SolarDay {
        val key = year * 1000 + dayOfYear
        cache[key]?.let { return it }

        val sunrise = solarEventMinutes(year, dayOfYear, sunrise = true)
        val sunset = solarEventMinutes(year, dayOfYear, sunrise = false)
        val day = SolarDay(sunrise, sunset, sunset - sunrise)

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

    /** Convert Gregorian date to Jalali. */
    fun toJalali(gYear: Int, gMonth: Int, gDay: Int): JalaliDate {
        val g_d_m = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        var gy = gYear - 1600
        var gm = gMonth - 1
        var gd = gDay - 1

        var g_day_no = 365 * gy + (gy + 3) / 4 - (gy + 99) / 100 + (gy + 399) / 400
        g_day_no += g_d_m[gm] + gd
        if (gm > 1 && ((gy % 4 == 0 && gy % 100 != 0) || (gy % 400 == 0))) {
            g_day_no++
        }

        var j_day_no = g_day_no - 79

        val j_np = j_day_no / 12053
        j_day_no %= 12053

        var jy = 979 + 33 * j_np + 4 * (j_day_no / 1461)
        j_day_no %= 1461

        if (j_day_no >= 366) {
            jy += (j_day_no - 1) / 365
            j_day_no = (j_day_no - 1) % 365
        }

        val jm: Int
        val jd: Int
        if (j_day_no < 186) {
            jm = 1 + j_day_no / 31
            jd = 1 + j_day_no % 31
        } else {
            jm = 7 + (j_day_no - 186) / 30
            jd = 1 + (j_day_no - 186) % 30
        }

        return JalaliDate(jy, jm, jd)
    }

    /** Format as "۸ مهر" (Persian digits + month name). */
    fun formatJalaliShort(gYear: Int, gMonth: Int, gDay: Int): String {
        val j = toJalali(gYear, gMonth, gDay)
        val dayStr = toPersianDigits(j.day.toString())
        val monthName = PERSIAN_MONTHS[j.month - 1]
        return "$dayStr $monthName"
    }

    private fun toPersianDigits(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) {
            sb.append(if (c in '0'..'9') PERSIAN_DIGITS[c - '0'] else c)
        }
        return sb.toString()
    }
}
