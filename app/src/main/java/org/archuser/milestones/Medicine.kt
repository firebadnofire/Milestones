package org.archuser.milestones

data class Medicine(
    val id: Long,
    val name: String,
    val scheduledTimes: List<Int>,
    val doseLogs: List<MedicineDoseLog> = emptyList()
)

data class MedicineDoseLog(
    val localDay: String,
    val scheduledDoseIndex: Int
)
