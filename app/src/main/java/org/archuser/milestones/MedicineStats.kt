package org.archuser.milestones

object MedicineStats {
    data class DoseSummary(
        val takenCount: Int,
        val totalCount: Int
    )

    fun todayDoseSummary(
        medicine: Medicine,
        today: LocalDay = LocalDay.today()
    ): DoseSummary = doseSummaryForDay(medicine, today)

    fun currentStreak(
        medicine: Medicine,
        today: LocalDay = LocalDay.today()
    ): Int {
        if (medicine.scheduledTimes.isEmpty()) return 0

        var streakDay = if (isDayComplete(medicine, today)) {
            today
        } else {
            today.minusDays(1)
        }
        var streak = 0

        while (isDayComplete(medicine, streakDay)) {
            streak += 1
            streakDay = streakDay.minusDays(1)
        }

        return streak
    }

    fun toggleDoseTaken(
        medicine: Medicine,
        day: LocalDay,
        scheduledDoseIndex: Int,
        isTaken: Boolean
    ): Medicine {
        require(scheduledDoseIndex in medicine.scheduledTimes.indices) {
            "Invalid scheduled dose index: $scheduledDoseIndex"
        }
        val targetKey = day.key()
        val existingLogs = medicine.doseLogs.filterNot {
            it.localDay == targetKey && it.scheduledDoseIndex == scheduledDoseIndex
        }.toMutableList()

        if (isTaken) {
            existingLogs += MedicineDoseLog(
                localDay = targetKey,
                scheduledDoseIndex = scheduledDoseIndex
            )
        }

        return medicine.copy(
            doseLogs = existingLogs.sortedWith(
                compareBy<MedicineDoseLog>({ it.localDay }, { it.scheduledDoseIndex })
            )
        )
    }

    fun isDoseTaken(
        medicine: Medicine,
        day: LocalDay,
        scheduledDoseIndex: Int
    ): Boolean {
        val targetKey = day.key()
        return medicine.doseLogs.any {
            it.localDay == targetKey && it.scheduledDoseIndex == scheduledDoseIndex
        }
    }

    private fun doseSummaryForDay(
        medicine: Medicine,
        day: LocalDay
    ): DoseSummary {
        val totalCount = medicine.scheduledTimes.size
        if (totalCount == 0) {
            return DoseSummary(takenCount = 0, totalCount = 0)
        }

        val validDoseIndices = medicine.doseLogs
            .asSequence()
            .filter { it.localDay == day.key() }
            .map { it.scheduledDoseIndex }
            .filter { it in medicine.scheduledTimes.indices }
            .distinct()
            .count()

        return DoseSummary(
            takenCount = validDoseIndices,
            totalCount = totalCount
        )
    }

    private fun isDayComplete(
        medicine: Medicine,
        day: LocalDay
    ): Boolean {
        val summary = doseSummaryForDay(medicine, day)
        return summary.totalCount > 0 && summary.takenCount == summary.totalCount
    }
}
