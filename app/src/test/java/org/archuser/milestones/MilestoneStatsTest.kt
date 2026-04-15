package org.archuser.milestones

import org.junit.Assert.assertEquals
import org.junit.Test

class MilestoneStatsTest {
    @Test
    fun recentResetCount_usesRollingSevenDayWindow() {
        val today = LocalDay.of(2026, 4, 15)
        val milestone = Milestone(
            id = 1L,
            name = "Reading",
            startDateMillis = today.startOfDayMillis(),
            resetHistory = listOf(
                today.key(),
                today.minusDays(1).key(),
                today.minusDays(6).key(),
                today.minusDays(6).key(),
                today.minusDays(7).key()
            )
        )

        assertEquals(4, MilestoneStats.recentResetCount(milestone, today))
    }
}
