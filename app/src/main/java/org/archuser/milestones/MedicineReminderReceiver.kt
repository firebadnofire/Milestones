package org.archuser.milestones

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class MedicineReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            MedicineReminderScheduler.ACTION_DOSE_DUE -> handleDoseDue(context, intent)
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> rescheduleAll(context)
            else -> Log.w(TAG, "Unsupported medicine reminder action: ${intent.action}")
        }
    }

    private fun handleDoseDue(context: Context, intent: Intent) {
        val medicineId = intent.getLongExtra(MedicineReminderScheduler.EXTRA_MEDICINE_ID, MISSING_ID)
        val doseIndex = intent.getIntExtra(MedicineReminderScheduler.EXTRA_DOSE_INDEX, MISSING_INDEX)
        val scheduledMinutes = intent.getIntExtra(
            MedicineReminderScheduler.EXTRA_SCHEDULED_MINUTES,
            MISSING_INDEX
        )
        val dueDay = intent.getStringExtra(MedicineReminderScheduler.EXTRA_DUE_DAY)
            ?.let { dueDayKey ->
                runCatching { LocalDay.parse(dueDayKey) }
                    .onFailure { error ->
                        Log.w(TAG, "Medicine reminder has an invalid due day: $dueDayKey", error)
                    }
                    .getOrNull()
            }

        if (
            medicineId == MISSING_ID ||
            doseIndex == MISSING_INDEX ||
            scheduledMinutes == MISSING_INDEX ||
            dueDay == null
        ) {
            Log.w(TAG, "Medicine reminder alarm is missing required extras.")
            return
        }

        val appState = loadAppState(context) ?: return
        val medicine = appState.medicines.firstOrNull { it.id == medicineId }
        if (medicine == null) {
            Log.i(TAG, "Medicine reminder skipped because medicine $medicineId no longer exists.")
            return
        }

        if (medicine.scheduledTimes.getOrNull(doseIndex) != scheduledMinutes) {
            Log.i(TAG, "Medicine reminder schedule changed for medicine $medicineId; rescheduling.")
            MedicineReminderScheduler.scheduleMedicine(context, medicine)
            return
        }

        if (!MedicineStats.isDoseTaken(medicine, dueDay, doseIndex)) {
            MedicineReminderScheduler.showDoseDueNotification(
                context = context,
                medicine = medicine,
                scheduledMinutes = scheduledMinutes
            )
        }
        MedicineReminderScheduler.scheduleDose(context, medicine, doseIndex)
    }

    private fun rescheduleAll(context: Context) {
        val appState = loadAppState(context) ?: return
        MedicineReminderScheduler.scheduleAll(context, appState.medicines)
    }

    private fun loadAppState(context: Context): AppState? {
        return runCatching { AppStatePreferences.load(context) }
            .onFailure { error ->
                Log.e(TAG, "Unable to load app state for medicine reminders.", error)
            }
            .getOrNull()
    }

    companion object {
        private const val TAG = "MedicineReminders"
        private const val MISSING_ID = Long.MIN_VALUE
        private const val MISSING_INDEX = -1
    }
}
