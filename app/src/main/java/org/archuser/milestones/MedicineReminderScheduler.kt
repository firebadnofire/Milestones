package org.archuser.milestones

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import java.util.Calendar

object MedicineReminderScheduler {
    const val ACTION_DOSE_DUE = "org.archuser.milestones.action.MEDICINE_DOSE_DUE"
    const val ACTION_TAKE_DOSE = "org.archuser.milestones.action.MEDICINE_TAKE_DOSE"
    const val EXTRA_MEDICINE_ID = "medicine_id"
    const val EXTRA_DOSE_INDEX = "dose_index"
    const val EXTRA_SCHEDULED_MINUTES = "scheduled_minutes"
    const val EXTRA_DUE_DAY = "due_day"

    private const val TAG = "MedicineReminders"
    private const val MINUTES_PER_HOUR = 60
    private const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR
    private const val DAYS_PER_WEEK = 7

    fun scheduleAll(
        context: Context,
        medicines: List<Medicine>,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        if (!canScheduleExactAlarms(context)) {
            Log.w(TAG, "Skipping medicine reminder scheduling because exact alarm access is unavailable.")
            return
        }
        medicines.forEach { medicine ->
            scheduleMedicine(context, medicine, nowMillis)
        }
    }

    fun scheduleMedicine(
        context: Context,
        medicine: Medicine,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        medicine.scheduledTimes.forEachIndexed { doseIndex, _ ->
            scheduleDose(context, medicine, doseIndex, nowMillis)
        }
    }

    fun scheduleDose(
        context: Context,
        medicine: Medicine,
        doseIndex: Int,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        val scheduledMinutes = medicine.scheduledTimes.getOrNull(doseIndex)
        if (scheduledMinutes == null) {
            Log.w(TAG, "Unable to schedule missing dose $doseIndex for medicine ${medicine.id}.")
            return
        }

        val alarmManager = context.getSystemService(AlarmManager::class.java)
        if (alarmManager == null) {
            Log.e(TAG, "Unable to schedule medicine reminder because AlarmManager is unavailable.")
            return
        }
        if (!canScheduleExactAlarms(context, alarmManager)) {
            Log.w(TAG, "Unable to schedule medicine reminder because exact alarm access is unavailable.")
            return
        }

        val triggerAtMillis = nextTriggerAtMillis(
            minutesAfterMidnight = scheduledMinutes,
            scheduledWeekdays = medicine.scheduledWeekdays,
            nowMillis = nowMillis
        )
        val dueDay = LocalDay.fromTimestamp(triggerAtMillis).key()
        val pendingIntent = dosePendingIntent(
            context = context,
            medicineId = medicine.id,
            doseIndex = doseIndex,
            scheduledMinutes = scheduledMinutes,
            dueDay = dueDay,
            flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent == null) {
            Log.e(TAG, "Unable to create reminder PendingIntent for medicine ${medicine.id}.")
            return
        }

        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
            else -> {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        }
    }

    fun cancelMedicine(context: Context, medicine: Medicine) {
        medicine.scheduledTimes.indices.forEach { doseIndex ->
            cancelDose(context, medicine.id, doseIndex)
        }
    }

    fun cancelMedicines(context: Context, medicines: List<Medicine>) {
        medicines.forEach { medicine ->
            cancelMedicine(context, medicine)
        }
    }

    internal fun nextTriggerAtMillis(
        minutesAfterMidnight: Int,
        scheduledWeekdays: List<Int>,
        nowMillis: Long
    ): Long {
        require(minutesAfterMidnight in 0 until MINUTES_PER_DAY) {
            "Scheduled time must be between 0 and 1439 minutes."
        }
        require(scheduledWeekdays.isNotEmpty()) {
            "Scheduled weekdays must not be empty."
        }

        val calendar = Calendar.getInstance().apply {
            timeInMillis = nowMillis
        }
        repeat(DAYS_PER_WEEK + 1) { dayOffset ->
            val candidate = (calendar.clone() as Calendar).apply {
                if (dayOffset > 0) {
                    add(Calendar.DAY_OF_YEAR, dayOffset)
                }
                set(Calendar.HOUR_OF_DAY, minutesAfterMidnight / MINUTES_PER_HOUR)
                set(Calendar.MINUTE, minutesAfterMidnight % MINUTES_PER_HOUR)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (
                candidate.get(Calendar.DAY_OF_WEEK) in scheduledWeekdays &&
                candidate.timeInMillis > nowMillis
            ) {
                return candidate.timeInMillis
            }
        }
        error("Unable to find the next trigger time for the configured weekdays.")
    }

    fun resolveCustomReminderSoundUri(context: Context): Uri? {
        if (!AppStatePreferences.isCustomNotificationSoundEnabled(context)) {
            return null
        }

        val storedUri = AppStatePreferences.getCustomNotificationSoundUri(context)
            ?.let(Uri::parse)
            ?: return null

        return runCatching {
            val mimeType = context.contentResolver.getType(storedUri)
            require(mimeType?.startsWith("audio/") == true) {
                "Stored custom reminder sound is not audio."
            }
            context.contentResolver.openAssetFileDescriptor(storedUri, "r")?.use { asset ->
                require(asset.length != 0L) {
                    "Stored custom reminder sound is empty."
                }
            } ?: error("Unable to open stored custom reminder sound.")
            storedUri
        }.onFailure { error ->
            Log.e(TAG, "Falling back to the system alarm sound because the custom sound is unavailable.", error)
        }.getOrNull()
    }

    private fun dosePendingIntent(
        context: Context,
        medicineId: Long,
        doseIndex: Int,
        scheduledMinutes: Int,
        dueDay: String,
        flags: Int
    ): PendingIntent? {
        val intent = Intent(context, MedicineReminderReceiver::class.java).apply {
            action = ACTION_DOSE_DUE
            data = Uri.parse("milestones://medicine-reminders/$medicineId/$doseIndex")
            putExtra(EXTRA_MEDICINE_ID, medicineId)
            putExtra(EXTRA_DOSE_INDEX, doseIndex)
            putExtra(EXTRA_SCHEDULED_MINUTES, scheduledMinutes)
            putExtra(EXTRA_DUE_DAY, dueDay)
        }
        return PendingIntent.getBroadcast(
            context,
            reminderRequestCode(medicineId, doseIndex),
            intent,
            flags
        )
    }

    private fun cancelDose(context: Context, medicineId: Long, doseIndex: Int) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        if (alarmManager == null) {
            Log.e(TAG, "Unable to cancel medicine reminder because AlarmManager is unavailable.")
            return
        }

        val pendingIntent = dosePendingIntent(
            context = context,
            medicineId = medicineId,
            doseIndex = doseIndex,
            scheduledMinutes = 0,
            dueDay = "",
            flags = PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    fun formatScheduledTime(context: Context, minutesAfterMidnight: Int): String {
        val calendar = Calendar.getInstance().apply {
            clear()
            set(Calendar.HOUR_OF_DAY, minutesAfterMidnight / MINUTES_PER_HOUR)
            set(Calendar.MINUTE, minutesAfterMidnight % MINUTES_PER_HOUR)
        }
        return android.text.format.DateFormat.getTimeFormat(context).format(calendar.time)
    }

    fun canScheduleExactAlarms(context: Context): Boolean {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return false
        return canScheduleExactAlarms(context, alarmManager)
    }

    private fun canScheduleExactAlarms(context: Context, alarmManager: AlarmManager): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    private fun reminderRequestCode(medicineId: Long, doseIndex: Int): Int {
        val medicineHash = (medicineId xor (medicineId ushr 32)).toInt()
        return (31 * medicineHash + doseIndex) and Int.MAX_VALUE
    }
}
