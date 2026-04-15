package org.archuser.milestones

import org.junit.Assert.assertEquals
import org.junit.Test

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
}
