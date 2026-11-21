package com.example.core

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Timezone-aware date/time utilities pinned to Egypt local time (Africa/Cairo).
 */
object EgyptTime {
    val timeZone: TimeZone = TimeZone.getTimeZone("Africa/Cairo")
    private val arabicLocale = Locale("ar", "EG")

    fun nowMillis(): Long = System.currentTimeMillis()

    fun todayEpochDay(nowMillis: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance(timeZone).apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        // Return YYYYMMDD as clean readable Long day key in Egypt timezone
        val y = cal.get(Calendar.YEAR).toLong()
        val m = (cal.get(Calendar.MONTH) + 1).toLong()
        val d = cal.get(Calendar.DAY_OF_MONTH).toLong()
        return y * 10000L + m * 100L + d
    }

    fun startOfDayMillis(millis: Long = System.currentTimeMillis()): Long {
        return Calendar.getInstance(timeZone).apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    fun endOfDayMillis(millis: Long = System.currentTimeMillis()): Long {
        return Calendar.getInstance(timeZone).apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis
    }

    fun startOfMonthMillis(millis: Long = System.currentTimeMillis()): Long {
        return Calendar.getInstance(timeZone).apply {
            timeInMillis = millis
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    fun addDays(millis: Long, days: Int): Long {
        return Calendar.getInstance(timeZone).apply {
            timeInMillis = millis
            add(Calendar.DAY_OF_MONTH, days)
        }.timeInMillis
    }

    fun formatDate(millis: Long): String {
        val sdf = SimpleDateFormat("yyyy/MM/dd", Locale.US)
        sdf.timeZone = timeZone
        return sdf.format(Date(millis))
    }

    fun formatDateArabic(millis: Long): String {
        val sdf = SimpleDateFormat("dd MMMM yyyy", arabicLocale)
        sdf.timeZone = timeZone
        return sdf.format(Date(millis))
    }

    fun formatTimeHm(millis: Long): String {
        val cal = Calendar.getInstance(timeZone).apply { timeInMillis = millis }
        val h12 = cal.get(Calendar.HOUR).let { if (it == 0) 12 else it }
        val min = cal.get(Calendar.MINUTE)
        val amPm = if (cal.get(Calendar.AM_PM) == Calendar.AM) "ص" else "م"
        return String.format(Locale.US, "%02d:%02d %s", h12, min, amPm)
    }

    fun formatTimeHms12(millis: Long): String {
        val cal = Calendar.getInstance(timeZone).apply { timeInMillis = millis }
        val h12 = cal.get(Calendar.HOUR).let { if (it == 0) 12 else it }
        val min = cal.get(Calendar.MINUTE)
        val sec = cal.get(Calendar.SECOND)
        val amPm = if (cal.get(Calendar.AM_PM) == Calendar.AM) "ص" else "م"
        return String.format(Locale.US, "%02d:%02d:%02d %s", h12, min, sec, amPm)
    }

    fun formatHourMinute12(hour24: Int, minute: Int): String {
        val normalizedHour = hour24.coerceIn(0, 23)
        val normalizedMin = minute.coerceIn(0, 59)
        val h12 = when {
            normalizedHour == 0 -> 12
            normalizedHour > 12 -> normalizedHour - 12
            else -> normalizedHour
        }
        val amPm = if (normalizedHour < 12) "ص" else "م"
        return String.format(Locale.US, "%02d:%02d %s", h12, normalizedMin, amPm)
    }

    fun formatFileStampTime(millis: Long): String {
        val sdf = SimpleDateFormat("HHmmss", Locale.US)
        sdf.timeZone = timeZone
        return sdf.format(Date(millis))
    }

    fun formatDateTime(millis: Long): String {
        return "${formatDate(millis)} - ${formatTimeHm(millis)}"
    }

    fun formatDateTimeSeconds(millis: Long): String {
        return "${formatDate(millis)} - ${formatTimeHms12(millis)}"
    }

    fun formatEpochDay(epochDayYyyymmdd: Long): String {
        val y = epochDayYyyymmdd / 10000L
        val m = (epochDayYyyymmdd % 10000L) / 100L
        val d = epochDayYyyymmdd % 100L
        return String.format(Locale.US, "%04d/%02d/%02d", y, m, d)
    }
}
