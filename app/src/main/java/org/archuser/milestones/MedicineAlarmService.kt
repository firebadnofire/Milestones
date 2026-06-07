package org.archuser.milestones

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

class MedicineAlarmService : Service() {
    private var mediaPlayer: MediaPlayer? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val medicineId = intent?.getLongExtra(EXTRA_MEDICINE_ID, INVALID_MEDICINE_ID)
            ?: INVALID_MEDICINE_ID
        val doseIndex = intent?.getIntExtra(EXTRA_DOSE_INDEX, INVALID_DOSE_INDEX)
            ?: INVALID_DOSE_INDEX
        val dueDay = intent?.getStringExtra(EXTRA_DUE_DAY)
        val medicineName = intent?.getStringExtra(EXTRA_MEDICINE_NAME)
        val scheduledMinutes = intent?.getIntExtra(EXTRA_SCHEDULED_MINUTES, INVALID_SCHEDULED_MINUTES)
            ?: INVALID_SCHEDULED_MINUTES
        if (
            medicineId == INVALID_MEDICINE_ID ||
            doseIndex == INVALID_DOSE_INDEX ||
            dueDay.isNullOrBlank() ||
            medicineName.isNullOrBlank() ||
            scheduledMinutes == INVALID_SCHEDULED_MINUTES
        ) {
            Log.w(TAG, "Medicine alarm service started without required extras.")
            stopSelfResult(startId)
            return START_NOT_STICKY
        }

        startForeground(
            FOREGROUND_NOTIFICATION_ID,
            buildForegroundNotification(
                medicineId = medicineId,
                doseIndex = doseIndex,
                dueDay = dueDay,
                medicineName = medicineName,
                scheduledMinutes = scheduledMinutes
            )
        )
        playAlarmSound()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopAlarmPlayback()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildForegroundNotification(
        medicineId: Long,
        doseIndex: Int,
        dueDay: String,
        medicineName: String,
        scheduledMinutes: Int
    ): Notification {
        val channelId = ensureForegroundServiceChannel()
        val scheduledTime = MedicineReminderScheduler.formatScheduledTime(this, scheduledMinutes)
        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_medicine_notification)
            .setContentTitle(getString(R.string.medicine_alarm_notification_title, medicineName))
            .setContentText(getString(R.string.medicine_alarm_notification_text, scheduledTime))
            .setContentIntent(openMedicinesPendingIntent())
            .setFullScreenIntent(fullScreenPendingIntent(), true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .addAction(
                0,
                getString(R.string.take_dose),
                takeDosePendingIntent(medicineId, doseIndex, dueDay, scheduledMinutes)
            )
        return builder.build()
    }

    private fun playAlarmSound() {
        val soundUri = resolveAlarmSoundUri()
            ?: run {
                Log.e(TAG, "Unable to resolve an alarm sound URI.")
                stopSelf()
                return
            }

        stopAlarmPlayback()

        val player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            isLooping = true
        }

        runCatching {
            player.setDataSource(this, soundUri)
            player.prepare()
            player.start()
        }.onSuccess {
            mediaPlayer = player
        }.onFailure { error ->
            player.release()
            Log.e(TAG, "Unable to play medicine alarm sound.", error)
            stopSelf()
        }
    }

    private fun stopAlarmPlayback() {
        mediaPlayer?.let { player ->
            runCatching {
                if (player.isPlaying) {
                    player.stop()
                }
            }
            player.release()
        }
        mediaPlayer = null
    }

    private fun ensureForegroundServiceChannel(): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return DEFAULT_FOREGROUND_CHANNEL_ID
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        if (notificationManager == null) {
            Log.e(TAG, "Unable to create medicine alarm service channel.")
            return DEFAULT_FOREGROUND_CHANNEL_ID
        }

        val channel = NotificationChannel(
            DEFAULT_FOREGROUND_CHANNEL_ID,
            getString(R.string.medicine_alarm_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.medicine_alarm_channel_description)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            enableVibration(true)
            setSound(null, null)
        }
        notificationManager.createNotificationChannel(channel)
        return DEFAULT_FOREGROUND_CHANNEL_ID
    }

    private fun openMedicinesPendingIntent(): PendingIntent {
        val intent = Intent(this, MedicinesActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this,
            FOREGROUND_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun fullScreenPendingIntent(): PendingIntent {
        val intent = Intent(this, MedicinesActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MedicinesActivity.EXTRA_ALARM_FULL_SCREEN, true)
        }
        return PendingIntent.getActivity(
            this,
            FULL_SCREEN_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun defaultAlarmSoundUri(): Uri? {
        return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    }

    private fun resolveAlarmSoundUri(): Uri? {
        return MedicineReminderScheduler.resolveCustomReminderSoundUri(this)
            ?: defaultAlarmSoundUri()
    }

    private fun alarmAudioAttributes(): AudioAttributes {
        return AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
    }

    private fun takeDosePendingIntent(
        medicineId: Long,
        doseIndex: Int,
        dueDay: String,
        scheduledMinutes: Int
    ): PendingIntent {
        val intent = Intent(this, MedicineReminderReceiver::class.java).apply {
            action = MedicineReminderScheduler.ACTION_TAKE_DOSE
            data = Uri.parse("milestones://medicine-reminders/take/$medicineId/$doseIndex/$dueDay")
            putExtra(MedicineReminderScheduler.EXTRA_MEDICINE_ID, medicineId)
            putExtra(MedicineReminderScheduler.EXTRA_DOSE_INDEX, doseIndex)
            putExtra(MedicineReminderScheduler.EXTRA_DUE_DAY, dueDay)
            putExtra(MedicineReminderScheduler.EXTRA_SCHEDULED_MINUTES, scheduledMinutes)
        }
        return PendingIntent.getBroadcast(
            this,
            TAKE_DOSE_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        private const val TAG = "MedicineAlarmService"
        private const val DEFAULT_FOREGROUND_CHANNEL_ID = "medicine_alarm_full_screen_v4"
        private const val FOREGROUND_NOTIFICATION_ID = 30_001
        private const val TAKE_DOSE_REQUEST_CODE = 30_002
        private const val FULL_SCREEN_REQUEST_CODE = 30_003
        private const val INVALID_MEDICINE_ID = Long.MIN_VALUE
        private const val INVALID_DOSE_INDEX = -1
        private const val INVALID_SCHEDULED_MINUTES = -1
        private const val EXTRA_MEDICINE_ID = "medicine_id"
        private const val EXTRA_DOSE_INDEX = "dose_index"
        private const val EXTRA_DUE_DAY = "due_day"
        private const val EXTRA_MEDICINE_NAME = "medicine_name"
        private const val EXTRA_SCHEDULED_MINUTES = "scheduled_minutes"

        fun start(
            context: Context,
            medicineId: Long,
            doseIndex: Int,
            dueDay: String,
            medicineName: String,
            scheduledMinutes: Int
        ) {
            val intent = Intent(context, MedicineAlarmService::class.java).apply {
                putExtra(EXTRA_MEDICINE_ID, medicineId)
                putExtra(EXTRA_DOSE_INDEX, doseIndex)
                putExtra(EXTRA_DUE_DAY, dueDay)
                putExtra(EXTRA_MEDICINE_NAME, medicineName)
                putExtra(EXTRA_SCHEDULED_MINUTES, scheduledMinutes)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MedicineAlarmService::class.java))
        }
    }
}
