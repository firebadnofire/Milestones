package org.archuser.milestones

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.util.Calendar

object MedicineReminderScheduler {
    const val ACTION_DOSE_DUE = "org.archuser.milestones.action.MEDICINE_DOSE_DUE"
    const val EXTRA_MEDICINE_ID = "medicine_id"
    const val EXTRA_DOSE_INDEX = "dose_index"
    const val EXTRA_SCHEDULED_MINUTES = "scheduled_minutes"
    const val EXTRA_DUE_DAY = "due_day"

    private const val TAG = "MedicineReminders"
    private const val DEFAULT_CHANNEL_ID = "medicine_reminders_default"
    private const val CUSTOM_CHANNEL_ID_PREFIX = "medicine_reminders_custom_"
    private const val MINUTES_PER_HOUR = 60
    private const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR
    private const val CONTENT_INTENT_REQUEST_CODE = 20_000

    fun scheduleAll(
        context: Context,
        medicines: List<Medicine>,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        ensureNotificationChannels(context)
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

        val triggerAtMillis = nextTriggerAtMillis(scheduledMinutes, nowMillis)
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

        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent
        )
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

    fun showDoseDueNotification(
        context: Context,
        medicine: Medicine,
        scheduledMinutes: Int
    ) {
        if (!canPostNotifications(context)) {
            Log.i(TAG, "Medicine reminder notification skipped because notification permission is unavailable.")
            return
        }

        val customSoundUri = resolveCustomNotificationSoundUri(context)
        val channelId = ensureNotificationChannels(context, customSoundUri)
        val scheduledTime = formatScheduledTime(context, scheduledMinutes)
        val notificationBuilder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_medicine_notification)
            .setContentTitle(
                context.getString(R.string.medicine_reminder_notification_title, medicine.name)
            )
            .setContentText(
                context.getString(R.string.medicine_reminder_notification_text, scheduledTime)
            )
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setContentIntent(openMedicinesPendingIntent(context))

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O && customSoundUri != null) {
            notificationBuilder.setSound(customSoundUri)
        }

        val notification = notificationBuilder.build()

        notify(context, reminderRequestCode(medicine.id, scheduledMinutes), notification)
    }

    internal fun nextTriggerAtMillis(minutesAfterMidnight: Int, nowMillis: Long): Long {
        require(minutesAfterMidnight in 0 until MINUTES_PER_DAY) {
            "Scheduled time must be between 0 and 1439 minutes."
        }

        val calendar = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, minutesAfterMidnight / MINUTES_PER_HOUR)
            set(Calendar.MINUTE, minutesAfterMidnight % MINUTES_PER_HOUR)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= nowMillis) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return calendar.timeInMillis
    }

    private fun canPostNotifications(context: Context): Boolean {
        val notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return notificationsEnabled
        }
        return notificationsEnabled && ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun ensureNotificationChannels(
        context: Context,
        customSoundUri: Uri? = resolveCustomNotificationSoundUri(context)
    ): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return DEFAULT_CHANNEL_ID
        }

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        if (notificationManager == null) {
            Log.e(TAG, "Unable to create medicine reminder channel because NotificationManager is unavailable.")
            return DEFAULT_CHANNEL_ID
        }

        val defaultChannel = NotificationChannel(
            DEFAULT_CHANNEL_ID,
            context.getString(R.string.medicine_reminder_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.medicine_reminder_channel_description)
        }
        notificationManager.createNotificationChannel(defaultChannel)

        if (customSoundUri == null) {
            return DEFAULT_CHANNEL_ID
        }

        val customChannelId = customChannelId(customSoundUri)
        val customChannel = NotificationChannel(
            customChannelId,
            context.getString(R.string.medicine_reminder_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.medicine_reminder_channel_description)
            setSound(customSoundUri, notificationAudioAttributes())
        }
        notificationManager.createNotificationChannel(customChannel)
        return customChannelId
    }

    private fun resolveCustomNotificationSoundUri(context: Context): Uri? {
        if (!AppStatePreferences.isCustomNotificationSoundEnabled(context)) {
            return null
        }

        val storedUri = AppStatePreferences.getCustomNotificationSoundUri(context)
            ?.let(Uri::parse)
            ?: return null

        return runCatching {
            val mimeType = context.contentResolver.getType(storedUri)
            require(mimeType?.startsWith("audio/") == true) {
                "Stored custom notification sound is not audio."
            }
            context.contentResolver.openAssetFileDescriptor(storedUri, "r")?.use { asset ->
                require(asset.length != 0L) {
                    "Stored custom notification sound is empty."
                }
            } ?: error("Unable to open stored custom notification sound.")
            storedUri
        }.onFailure { error ->
            Log.e(TAG, "Falling back to the system notification sound because the custom sound is unavailable.", error)
        }.getOrNull()
    }

    private fun customChannelId(soundUri: Uri): String {
        return CUSTOM_CHANNEL_ID_PREFIX + soundUri.toString().hashCode().toUInt().toString(16)
    }

    private fun notificationAudioAttributes(): AudioAttributes {
        return AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
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

    private fun openMedicinesPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MedicinesActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            CONTENT_INTENT_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun formatScheduledTime(context: Context, minutesAfterMidnight: Int): String {
        val calendar = Calendar.getInstance().apply {
            clear()
            set(Calendar.HOUR_OF_DAY, minutesAfterMidnight / MINUTES_PER_HOUR)
            set(Calendar.MINUTE, minutesAfterMidnight % MINUTES_PER_HOUR)
        }
        return android.text.format.DateFormat.getTimeFormat(context).format(calendar.time)
    }

    private fun reminderRequestCode(medicineId: Long, doseIndex: Int): Int {
        val medicineHash = (medicineId xor (medicineId ushr 32)).toInt()
        return (31 * medicineHash + doseIndex) and Int.MAX_VALUE
    }

    @SuppressLint("MissingPermission")
    private fun notify(context: Context, notificationId: Int, notification: android.app.Notification) {
        runCatching {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        }.onFailure { error ->
            Log.e(TAG, "Unable to show medicine reminder notification.", error)
        }
    }
}
