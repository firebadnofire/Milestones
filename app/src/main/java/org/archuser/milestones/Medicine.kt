package org.archuser.milestones

data class Medicine(
    val id: Long,
    val name: String,
    val scheduledTimes: List<Int>,
    val scheduledWeekdays: List<Int> = ALL_SCHEDULED_WEEKDAYS,
    val doseLogs: List<MedicineDoseLog> = emptyList()
) {
    companion object {
        val ALL_SCHEDULED_WEEKDAYS: List<Int> = listOf(
            java.util.Calendar.SUNDAY,
            java.util.Calendar.MONDAY,
            java.util.Calendar.TUESDAY,
            java.util.Calendar.WEDNESDAY,
            java.util.Calendar.THURSDAY,
            java.util.Calendar.FRIDAY,
            java.util.Calendar.SATURDAY
        )
    }
}

data class MedicineDoseLog(
    val localDay: String,
    val scheduledDoseIndex: Int
)
