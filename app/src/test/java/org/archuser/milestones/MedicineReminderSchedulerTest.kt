package org.archuser.milestones

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class MedicineReminderSchedulerTest {
    @Test
    fun nextTriggerAtMillis_usesTodayWhenDoseTimeIsStillAhead() = withUtcTimeZone {
        val now = utcMillis(2026, Calendar.APRIL, 15, 7, 30)
        val expected = utcMillis(2026, Calendar.APRIL, 15, 8, 0)

        assertEquals(
            expected,
            MedicineReminderScheduler.nextTriggerAtMillis(
                8 * 60,
                Medicine.ALL_SCHEDULED_WEEKDAYS,
                now
            )
        )
    }

    @Test
    fun nextTriggerAtMillis_usesTomorrowWhenDoseTimeAlreadyPassed() = withUtcTimeZone {
        val now = utcMillis(2026, Calendar.APRIL, 15, 8, 30)
        val expected = utcMillis(2026, Calendar.APRIL, 16, 8, 0)

        assertEquals(
            expected,
            MedicineReminderScheduler.nextTriggerAtMillis(
                8 * 60,
                Medicine.ALL_SCHEDULED_WEEKDAYS,
                now
            )
        )
    }

    @Test
    fun nextTriggerAtMillis_usesTomorrowWhenDoseTimeIsNow() = withUtcTimeZone {
        val now = utcMillis(2026, Calendar.APRIL, 15, 8, 0)
        val expected = utcMillis(2026, Calendar.APRIL, 16, 8, 0)

        assertEquals(
            expected,
            MedicineReminderScheduler.nextTriggerAtMillis(
                8 * 60,
                Medicine.ALL_SCHEDULED_WEEKDAYS,
                now
            )
        )
    }

    @Test
    fun nextTriggerAtMillis_skipsDaysOutsideTheScheduledWeekdays() = withUtcTimeZone {
        val now = utcMillis(2026, Calendar.APRIL, 15, 8, 30)
        val expected = utcMillis(2026, Calendar.APRIL, 17, 8, 0)

        assertEquals(
            expected,
            MedicineReminderScheduler.nextTriggerAtMillis(
                8 * 60,
                listOf(Calendar.MONDAY, Calendar.WEDNESDAY, Calendar.FRIDAY),
                now
            )
        )
    }

    private fun withUtcTimeZone(block: () -> Unit) {
        val originalTimeZone = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            block()
        } finally {
            TimeZone.setDefault(originalTimeZone)
        }
    }

    private fun utcMillis(
        year: Int,
        month: Int,
        dayOfMonth: Int,
        hourOfDay: Int,
        minute: Int
    ): Long {
        return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(year, month, dayOfMonth, hourOfDay, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
