package org.archuser.milestones

data class AppState(
    val version: Int = AppStateStorage.CURRENT_VERSION,
    val milestones: List<Milestone> = emptyList(),
    val medicines: List<Medicine> = emptyList()
)
