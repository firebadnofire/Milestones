package org.archuser.milestones

object MilestoneStats {
    private const val ROLLING_WINDOW_DAYS = 7L

    fun recentResetCount(
        milestone: Milestone,
        today: LocalDay = LocalDay.today()
    ): Int {
        return milestone.resetHistory.count { resetDayKey ->
            val resetDay = LocalDay.parse(resetDayKey)
            val dayDifference = resetDay.daysUntil(today)
            dayDifference in 0 until ROLLING_WINDOW_DAYS
        }
    }

    fun daysFromToday(
        milestone: Milestone,
        today: LocalDay = LocalDay.today()
    ): Long {
        return LocalDay.fromTimestamp(milestone.startDateMillis).daysUntil(today)
    }
}
