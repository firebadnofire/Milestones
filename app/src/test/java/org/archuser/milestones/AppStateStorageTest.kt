package org.archuser.milestones

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class AppStateStorageTest {
    @Test
    fun decode_legacyMilestoneArray_defaultsResetHistoryAndMedicines() {
        val decoded = AppStateStorage.decode(
            """[
                {"id":1,"name":"Anniversary","startDateMillis":1744675200000}
            ]"""
        )

        assertEquals(
            AppState(
                version = AppStateStorage.CURRENT_VERSION,
                milestones = listOf(
                    Milestone(
                        id = 1L,
                        name = "Anniversary",
                        startDateMillis = LocalDay.fromTimestamp(1_744_675_200_000L).startOfDayMillis(),
                        resetHistory = emptyList()
                    )
                ),
                medicines = emptyList()
            ),
            decoded
        )
    }

    @Test
    fun decode_medicineWithoutWeekdays_defaultsToAllDays() {
        val decoded = AppStateStorage.decode(
            """{
                "version": 1,
                "milestones": [],
                "medicines": [
                    {
                        "id": 7,
                        "name": "Vitamin D",
                        "scheduledTimes": [480],
                        "doseLogs": []
                    }
                ]
            }"""
        )

        assertEquals(
            listOf(
                Medicine(
                    id = 7L,
                    name = "Vitamin D",
                    scheduledTimes = listOf(480),
                    scheduledWeekdays = listOf(
                        Calendar.SUNDAY,
                        Calendar.MONDAY,
                        Calendar.TUESDAY,
                        Calendar.WEDNESDAY,
                        Calendar.THURSDAY,
                        Calendar.FRIDAY,
                        Calendar.SATURDAY
                    )
                )
            ),
            decoded.medicines
        )
    }
}
