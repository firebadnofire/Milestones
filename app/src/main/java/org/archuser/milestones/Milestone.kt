package org.archuser.milestones

data class Milestone(
    val id: Long,
    val name: String,
    val startDateMillis: Long,
    val resetHistory: List<String> = emptyList()
)
