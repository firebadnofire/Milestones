package org.archuser.milestones

import kotlin.ConsistentCopyVisibility
import java.util.Calendar
import java.util.Locale

@ConsistentCopyVisibility
data class LocalDay private constructor(
    val year: Int,
    val month: Int,
    val dayOfMonth: Int
) {
    fun key(): String {
        return String.format(Locale.US, "%04d-%02d-%02d", year, month, dayOfMonth)
    }

    fun startOfDayMillis(): Long {
        val calendar = Calendar.getInstance().apply {
            clear()
            set(year, month - 1, dayOfMonth, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }

    fun dayOfWeek(): Int {
        return Calendar.getInstance().apply {
            clear()
            set(year, month - 1, dayOfMonth, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.get(Calendar.DAY_OF_WEEK)
    }

    fun daysUntil(other: LocalDay): Long = other.epochDay() - epochDay()

    fun plusDays(days: Int): LocalDay = fromEpochDay(epochDay() + days.toLong())

    fun minusDays(days: Int): LocalDay = plusDays(-days)

    private fun epochDay(): Long {
        var adjustedYear = year.toLong()
        val adjustedMonth = month.toLong()
        adjustedYear -= if (adjustedMonth <= 2L) 1L else 0L
        val era = if (adjustedYear >= 0L) adjustedYear / 400L else (adjustedYear - 399L) / 400L
        val yearOfEra = adjustedYear - era * 400L
        val dayOfYear = (153L * (adjustedMonth + if (adjustedMonth > 2L) -3L else 9L) + 2L) / 5L +
            dayOfMonth.toLong() - 1L
        val dayOfEra = yearOfEra * 365L + yearOfEra / 4L - yearOfEra / 100L + dayOfYear
        return era * 146097L + dayOfEra - 719468L
    }

    companion object {
        private val LOCAL_DAY_PATTERN = Regex("""^(\d{4})-(\d{2})-(\d{2})$""")

        fun today(nowMillis: Long = System.currentTimeMillis()): LocalDay = fromTimestamp(nowMillis)

        fun fromTimestamp(timestampMillis: Long): LocalDay {
            val calendar = Calendar.getInstance().apply { timeInMillis = timestampMillis }
            return of(
                year = calendar.get(Calendar.YEAR),
                month = calendar.get(Calendar.MONTH) + 1,
                dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH)
            )
        }

        fun parse(key: String): LocalDay {
            val match = LOCAL_DAY_PATTERN.matchEntire(key)
                ?: throw IllegalArgumentException("Invalid local day key: $key")
            return of(
                year = match.groupValues[1].toInt(),
                month = match.groupValues[2].toInt(),
                dayOfMonth = match.groupValues[3].toInt()
            )
        }

        fun of(year: Int, month: Int, dayOfMonth: Int): LocalDay {
            validate(year, month, dayOfMonth)
            return LocalDay(year, month, dayOfMonth)
        }

        private fun fromEpochDay(epochDay: Long): LocalDay {
            var zeroDay = epochDay + 719468L
            val era = if (zeroDay >= 0L) zeroDay / 146097L else (zeroDay - 146096L) / 146097L
            val dayOfEra = zeroDay - era * 146097L
            val yearOfEra = (dayOfEra - dayOfEra / 1460L + dayOfEra / 36524L - dayOfEra / 146096L) / 365L
            var year = yearOfEra + era * 400L
            val dayOfYear = dayOfEra - (365L * yearOfEra + yearOfEra / 4L - yearOfEra / 100L)
            val monthPrime = (5L * dayOfYear + 2L) / 153L
            val day = dayOfYear - (153L * monthPrime + 2L) / 5L + 1L
            val month = monthPrime + if (monthPrime < 10L) 3L else -9L
            year += if (month <= 2L) 1L else 0L
            return LocalDay(year.toInt(), month.toInt(), day.toInt())
        }

        private fun validate(year: Int, month: Int, dayOfMonth: Int) {
            val calendar = Calendar.getInstance().apply {
                isLenient = false
                clear()
                set(year, month - 1, dayOfMonth, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }
            calendar.timeInMillis
        }
    }
}
