package org.archuser.milestones

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class MedicineStatsTest {
    @Test
    fun currentStreak_usesYesterdayWhenTodayIsIncomplete() {
        val today = LocalDay.of(2026, 4, 15)
        val yesterday = today.minusDays(1)
        val twoDaysAgo = today.minusDays(2)
        val medicine = Medicine(
            id = 1L,
            name = "Vitamin D",
            scheduledTimes = listOf(8 * 60, 20 * 60),
            doseLogs = listOf(
                MedicineDoseLog(yesterday.key(), 0),
                MedicineDoseLog(yesterday.key(), 1),
                MedicineDoseLog(twoDaysAgo.key(), 0),
                MedicineDoseLog(twoDaysAgo.key(), 1),
                MedicineDoseLog(today.key(), 0)
            )
        )

        assertEquals(2, MedicineStats.currentStreak(medicine, today))
        assertEquals(
            MedicineStats.DoseSummary(takenCount = 1, totalCount = 2),
            MedicineStats.todayDoseSummary(medicine, today)
        )
    }

    @Test
    fun currentStreak_includesTodayWhenAllDosesAreTaken() {
        val today = LocalDay.of(2026, 4, 15)
        val yesterday = today.minusDays(1)
        val medicine = Medicine(
            id = 1L,
            name = "Antibiotic",
            scheduledTimes = listOf(9 * 60, 21 * 60),
            doseLogs = listOf(
                MedicineDoseLog(today.key(), 0),
                MedicineDoseLog(today.key(), 1),
                MedicineDoseLog(yesterday.key(), 0),
                MedicineDoseLog(yesterday.key(), 1)
            )
        )

        assertEquals(2, MedicineStats.currentStreak(medicine, today))
    }

    @Test
    fun currentStreak_skipsDaysThatAreNotScheduled() {
        val wednesday = LocalDay.of(2026, 4, 15)
        val monday = LocalDay.of(2026, 4, 13)
        val medicine = Medicine(
            id = 1L,
            name = "Vitamin D",
            scheduledTimes = listOf(8 * 60),
            scheduledWeekdays = listOf(Calendar.MONDAY, Calendar.WEDNESDAY, Calendar.FRIDAY),
            doseLogs = listOf(
                MedicineDoseLog(wednesday.key(), 0),
                MedicineDoseLog(monday.key(), 0)
            )
        )

        assertEquals(2, MedicineStats.currentStreak(medicine, wednesday))
    }

    @Test
    fun todayDoseSummary_returnsZeroOnUnscheduledDays() {
        val tuesday = LocalDay.of(2026, 4, 14)
        val medicine = Medicine(
            id = 1L,
            name = "Vitamin D",
            scheduledTimes = listOf(8 * 60),
            scheduledWeekdays = listOf(Calendar.MONDAY, Calendar.WEDNESDAY, Calendar.FRIDAY)
        )

        assertEquals(
            MedicineStats.DoseSummary(takenCount = 0, totalCount = 0),
            MedicineStats.todayDoseSummary(medicine, tuesday)
        )
    }
}
