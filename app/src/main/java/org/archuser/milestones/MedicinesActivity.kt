package org.archuser.milestones

import android.Manifest
import android.app.AlertDialog
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.provider.OpenableColumns
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.google.android.material.color.DynamicColors
import com.google.android.material.materialswitch.MaterialSwitch
import org.archuser.milestones.databinding.ActivityMedicinesBinding
import java.io.IOException
import java.util.Calendar
import java.util.Locale

class MedicinesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMedicinesBinding
    private lateinit var adapter: MedicineAdapter
    private val milestones = mutableListOf<Milestone>()
    private val medicines = mutableListOf<Medicine>()
    private val selectedScheduleTimes = mutableListOf<Int>()
    private val selectedWeekdays = Medicine.ALL_SCHEDULED_WEEKDAYS.toMutableSet()
    private lateinit var exportLauncher: ActivityResultLauncher<String>
    private lateinit var importLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var exactAlarmSettingsLauncher: ActivityResultLauncher<Intent>
    private lateinit var fullScreenIntentSettingsLauncher: ActivityResultLauncher<Intent>
    private lateinit var notificationPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var customNotificationSoundLauncher: ActivityResultLauncher<Array<String>>
    private var exactAlarmPromptShownThisSession = false
    private var fullScreenIntentPromptShownThisSession = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyMaterialYouIfEnabled()

        binding = ActivityMedicinesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        setupDrawer()
        setupMenuActions()
        setupImportExportLaunchers()
        setupExactAlarmSettingsLauncher()
        setupFullScreenIntentSettingsLauncher()
        setupNotificationPermissionLauncher()
        setupCustomNotificationSoundLauncher()
        handleAlarmFullScreenIntent(intent)

        adapter = MedicineAdapter(
            onDoseChecked = { medicine, doseIndex, isChecked ->
                updateDoseStatus(medicine, doseIndex, isChecked)
            },
            onRemove = { medicine ->
                confirmRemoveMedicine(medicine)
            },
            formatScheduledTime = { minutesAfterMidnight ->
                formatScheduledTime(minutesAfterMidnight)
            },
            formatScheduledWeekdays = { scheduledWeekdays ->
                formatScheduledWeekdays(scheduledWeekdays)
            }
        )

        binding.medicineList.layoutManager = LinearLayoutManager(this)
        binding.medicineList.adapter = adapter
        binding.medicineList.isNestedScrollingEnabled = false

        binding.addDoseTimeButton.setOnClickListener { showTimePicker() }
        binding.addMedicineButton.setOnClickListener { addMedicine() }
        renderSelectedWeekdays()
        renderSelectedScheduleTimes()
    }

    override fun onResume() {
        super.onResume()
        refreshAppState()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAlarmFullScreenIntent(intent)
    }

    private fun setupDrawer() {
        val toggle = ActionBarDrawerToggle(
            this,
            binding.drawerLayout,
            binding.toolbar,
            R.string.drawer_open,
            R.string.drawer_close
        )
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
    }

    private fun setupMenuActions() {
        val menu = binding.navigationView.menu
        menu.findItem(R.id.action_milestones).setOnMenuItemClickListener {
            binding.drawerLayout.closeDrawer(binding.navigationView)
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            )
            finish()
            true
        }
        menu.findItem(R.id.action_medicines).setOnMenuItemClickListener {
            binding.drawerLayout.closeDrawer(binding.navigationView)
            true
        }
        menu.findItem(R.id.action_export).setOnMenuItemClickListener {
            exportLauncher.launch("milestones.json")
            binding.drawerLayout.closeDrawer(binding.navigationView)
            true
        }
        menu.findItem(R.id.action_import).setOnMenuItemClickListener {
            importLauncher.launch(arrayOf("application/json"))
            binding.drawerLayout.closeDrawer(binding.navigationView)
            true
        }

        val materialYouItem = menu.findItem(R.id.action_material_you)
        val switchView = materialYouItem.actionView?.findViewById<MaterialSwitch>(R.id.material_you_switch)
        switchView?.isChecked = isMaterialYouEnabled()
        switchView?.setOnCheckedChangeListener { _, isChecked ->
            setMaterialYouEnabled(isChecked)
            recreate()
        }
        materialYouItem.setOnMenuItemClickListener {
            switchView?.isChecked = switchView?.isChecked?.not() ?: false
            true
        }

        val customNotificationSoundItem = menu.findItem(R.id.action_custom_notification_sound)
        val customNotificationSoundSwitch = customNotificationSoundItem.actionView
            ?.findViewById<MaterialSwitch>(R.id.notification_sound_switch)
        customNotificationSoundSwitch?.isChecked = isCustomNotificationSoundEnabled()
        customNotificationSoundSwitch?.setOnCheckedChangeListener { _, isChecked ->
            setCustomNotificationSoundEnabled(isChecked)
            updateCustomNotificationSoundMenu()
            if (isChecked && getCustomNotificationSoundUri() == null) {
                showToast(R.string.custom_notification_sound_pick_prompt)
            }
        }
        customNotificationSoundItem.setOnMenuItemClickListener {
            customNotificationSoundSwitch?.isChecked = customNotificationSoundSwitch?.isChecked?.not() ?: false
            true
        }

        menu.findItem(R.id.action_pick_notification_sound).setOnMenuItemClickListener {
            customNotificationSoundLauncher.launch(arrayOf("audio/*"))
            binding.drawerLayout.closeDrawer(binding.navigationView)
            true
        }

        updateCustomNotificationSoundMenu()
    }

    private fun setupImportExportLaunchers() {
        exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) {
                exportToUri(uri)
            }
        }
        importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                importFromUri(uri)
            }
        }
    }

    private fun setupExactAlarmSettingsLauncher() {
        exactAlarmSettingsLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            if (MedicineReminderScheduler.canScheduleExactAlarms(this)) {
                exactAlarmPromptShownThisSession = false
                MedicineReminderScheduler.scheduleAll(this, medicines)
                requestNotificationPermissionIfNeeded()
            } else {
                showToast(R.string.medicine_exact_alarm_permission_denied)
            }
        }
    }

    private fun setupNotificationPermissionLauncher() {
        notificationPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                requestFullScreenIntentPermissionIfNeeded()
            } else {
                showToast(R.string.medicine_reminders_permission_denied)
            }
        }
    }

    private fun setupFullScreenIntentSettingsLauncher() {
        fullScreenIntentSettingsLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            if (canUseFullScreenIntent()) {
                fullScreenIntentPromptShownThisSession = false
                MedicineReminderScheduler.scheduleAll(this, medicines)
            } else {
                showToast(R.string.medicine_full_screen_intent_permission_denied)
            }
        }
    }

    private fun setupCustomNotificationSoundLauncher() {
        customNotificationSoundLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                importCustomNotificationSound(uri)
            }
        }
    }

    private fun refreshAppState() {
        runCatching { AppStatePreferences.load(this) }
            .onSuccess { appState ->
                milestones.clear()
                milestones.addAll(appState.milestones)
                medicines.clear()
                medicines.addAll(appState.medicines)
                updateMedicineList()
                syncMedicineReminders()
            }
            .onFailure {
                milestones.clear()
                medicines.clear()
                updateMedicineList()
                Toast.makeText(this, R.string.load_failed_invalid, Toast.LENGTH_LONG).show()
            }
    }

    private fun persistAppState() {
        AppStatePreferences.save(
            this,
            AppState(
                milestones = milestones.toList(),
                medicines = medicines.toList()
            )
        )
    }

    private fun showTimePicker() {
        val calendar = Calendar.getInstance()
        TimePickerDialog(
            this,
            { _, hourOfDay, minute ->
                addScheduledTime(hourOfDay * MINUTES_PER_HOUR + minute)
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            android.text.format.DateFormat.is24HourFormat(this)
        ).show()
    }

    private fun addScheduledTime(minutesAfterMidnight: Int) {
        clearScheduleError()
        if (selectedScheduleTimes.contains(minutesAfterMidnight)) {
            showScheduleError(R.string.error_schedule_duplicate)
            return
        }
        selectedScheduleTimes.add(minutesAfterMidnight)
        selectedScheduleTimes.sort()
        renderSelectedScheduleTimes()
    }

    private fun renderSelectedScheduleTimes() {
        binding.scheduleChipGroup.removeAllViews()
        selectedScheduleTimes.forEach { scheduledTime ->
            val chip = Chip(this).apply {
                text = formatScheduledTime(scheduledTime)
                isCloseIconVisible = true
                setOnCloseIconClickListener {
                    selectedScheduleTimes.remove(scheduledTime)
                    renderSelectedScheduleTimes()
                    if (selectedScheduleTimes.isNotEmpty()) {
                        clearScheduleError()
                    }
                }
            }
            binding.scheduleChipGroup.addView(chip)
        }
    }

    private fun renderSelectedWeekdays() {
        binding.weekdayChipGroup.removeAllViews()
        orderedWeekdays().forEach { dayOfWeek ->
            val chip = Chip(this).apply {
                text = weekdayDisplayName(dayOfWeek)
                isCheckable = true
                isChecked = dayOfWeek in selectedWeekdays
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        selectedWeekdays.add(dayOfWeek)
                    } else {
                        selectedWeekdays.remove(dayOfWeek)
                    }
                    if (selectedWeekdays.isNotEmpty()) {
                        clearScheduleError()
                    }
                }
            }
            binding.weekdayChipGroup.addView(chip)
        }
    }

    private fun addMedicine() {
        val name = binding.medicineNameInputEditText.text?.toString()?.trim().orEmpty()

        binding.medicineNameInputLayout.error = null
        clearScheduleError()

        var hasError = false
        if (name.isBlank()) {
            binding.medicineNameInputLayout.error = getString(R.string.error_medicine_name_required)
            hasError = true
        }
        if (selectedWeekdays.isEmpty()) {
            showScheduleError(R.string.error_weekdays_required)
            hasError = true
        }
        if (selectedScheduleTimes.isEmpty()) {
            showScheduleError(R.string.error_schedule_required)
            hasError = true
        }
        if (hasError) return

        val medicine = Medicine(
            id = nextMedicineId(),
            name = name,
            scheduledTimes = selectedScheduleTimes.toList(),
            scheduledWeekdays = persistableScheduledWeekdays()
        )
        medicines.add(medicine)

        binding.medicineNameInputEditText.setText("")
        selectedScheduleTimes.clear()
        selectedWeekdays.clear()
        selectedWeekdays.addAll(Medicine.ALL_SCHEDULED_WEEKDAYS)
        renderSelectedWeekdays()
        renderSelectedScheduleTimes()
        persistAppState()
        updateMedicineList()
        syncMedicineReminders()
    }

    private fun updateMedicineList() {
        val sorted = medicines.sortedBy { it.name.lowercase(Locale.getDefault()) }
        adapter.submitList(sorted)
        binding.emptyStateText.visibility = if (sorted.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun updateDoseStatus(
        medicine: Medicine,
        doseIndex: Int,
        isChecked: Boolean
    ) {
        val index = medicines.indexOfFirst { it.id == medicine.id }
        if (index == -1) return

        medicines[index] = MedicineStats.toggleDoseTaken(
            medicine = medicines[index],
            day = LocalDay.today(),
            scheduledDoseIndex = doseIndex,
            isTaken = isChecked
        )
        if (isChecked) {
            MedicineAlarmService.stop(this)
        }
        persistAppState()
        updateMedicineList()
    }

    private fun confirmRemoveMedicine(medicine: Medicine) {
        AlertDialog.Builder(this)
            .setTitle(R.string.confirm_remove_medicine_title)
            .setMessage(getString(R.string.confirm_remove_medicine_message, medicine.name))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.remove) { _, _ ->
                medicines.removeAll { it.id == medicine.id }
                MedicineReminderScheduler.cancelMedicine(this, medicine)
                persistAppState()
                updateMedicineList()
            }
            .show()
    }

    private fun exportToUri(uri: Uri) {
        val payload = AppStateStorage.encode(
            AppState(
                milestones = milestones.toList(),
                medicines = medicines.toList()
            )
        )
        val exportResult = runCatching {
            contentResolver.openOutputStream(uri)?.use { output ->
                output.write(payload.toByteArray())
            } ?: throw IOException("Unable to open export destination.")
        }
        showToast(if (exportResult.isSuccess) R.string.export_success else R.string.export_failed)
    }

    private fun importFromUri(uri: Uri) {
        runCatching {
            val payload = contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: throw IOException("Unable to open import source.")
            require(payload.isNotBlank()) {
                "Import file is empty."
            }
            AppStateStorage.decode(payload)
        }.onSuccess { importedState ->
            val previousMedicines = medicines.toList()
            milestones.clear()
            milestones.addAll(importedState.milestones)
            medicines.clear()
            medicines.addAll(importedState.medicines)
            MedicineReminderScheduler.cancelMedicines(this, previousMedicines)
            persistAppState()
            updateMedicineList()
            syncMedicineReminders()
            showToast(R.string.import_success)
        }.onFailure { error ->
            val messageRes = if (error is IOException) {
                R.string.import_failed_io
            } else {
                R.string.import_failed_invalid
            }
            showToast(messageRes)
        }
    }

    private fun applyMaterialYouIfEnabled() {
        if (isMaterialYouEnabled()) {
            DynamicColors.applyToActivityIfAvailable(this)
        }
    }

    private fun isMaterialYouEnabled(): Boolean {
        return AppStatePreferences.isMaterialYouEnabled(this)
    }

    private fun setMaterialYouEnabled(enabled: Boolean) {
        AppStatePreferences.setMaterialYouEnabled(this, enabled)
    }

    private fun isCustomNotificationSoundEnabled(): Boolean {
        return AppStatePreferences.isCustomNotificationSoundEnabled(this)
    }

    private fun setCustomNotificationSoundEnabled(enabled: Boolean) {
        AppStatePreferences.setCustomNotificationSoundEnabled(this, enabled)
    }

    private fun getCustomNotificationSoundUri(): String? {
        return AppStatePreferences.getCustomNotificationSoundUri(this)
    }

    private fun updateCustomNotificationSoundMenu() {
        val menu = binding.navigationView.menu
        val pickerItem = menu.findItem(R.id.action_pick_notification_sound)
        pickerItem.isVisible = isCustomNotificationSoundEnabled()
        pickerItem.actionView?.let { actionView ->
            val fileNameView = actionView.findViewById<TextView>(R.id.notification_sound_file_name)
            val removeButton = actionView.findViewById<ImageButton>(R.id.remove_notification_sound_button)
            val displayName = getSelectedNotificationSoundDisplayName()

            actionView.setOnClickListener {
                customNotificationSoundLauncher.launch(arrayOf("audio/*"))
                binding.drawerLayout.closeDrawer(binding.navigationView)
            }

            fileNameView.text = displayName ?: getString(R.string.menu_notification_sound_none)
            fileNameView.isSelected = !displayName.isNullOrBlank()

            removeButton.visibility = if (displayName.isNullOrBlank()) View.GONE else View.VISIBLE
            removeButton.setOnClickListener {
                confirmRemoveCustomNotificationSound(displayName)
            }
        }
    }

    private fun importCustomNotificationSound(uri: Uri) {
        runCatching {
            validateCustomNotificationSound(uri)
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            replaceCustomNotificationSoundUri(uri.toString())
        }.onSuccess {
            updateCustomNotificationSoundMenu()
            showToast(R.string.custom_notification_sound_saved)
        }.onFailure { error ->
            showToast(
                if (error is IOException || error is IllegalArgumentException) {
                    R.string.custom_notification_sound_invalid
                } else {
                    R.string.custom_notification_sound_failed
                }
            )
        }
    }

    private fun confirmRemoveCustomNotificationSound(displayName: String?) {
        val resolvedDisplayName = displayName ?: getString(R.string.menu_notification_sound_none)
        AlertDialog.Builder(this)
            .setTitle(R.string.confirm_remove_custom_notification_sound_title)
            .setMessage(
                getString(
                    R.string.confirm_remove_custom_notification_sound_message,
                    resolvedDisplayName
                )
            )
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.remove) { _, _ ->
                removeCustomNotificationSound()
            }
            .show()
    }

    private fun removeCustomNotificationSound() {
        runCatching {
            releasePersistedCustomNotificationSoundUri()
            AppStatePreferences.setCustomNotificationSoundUri(this, null)
        }.onSuccess {
            updateCustomNotificationSoundMenu()
            showToast(R.string.custom_notification_sound_removed)
        }.onFailure {
            showToast(R.string.custom_notification_sound_remove_failed)
        }
    }

    private fun replaceCustomNotificationSoundUri(newUri: String) {
        val currentUri = getCustomNotificationSoundUri()
        if (currentUri == newUri) {
            AppStatePreferences.setCustomNotificationSoundUri(this, newUri)
            return
        }

        releasePersistedUri(currentUri)
        AppStatePreferences.setCustomNotificationSoundUri(this, newUri)
    }

    private fun releasePersistedCustomNotificationSoundUri() {
        releasePersistedUri(getCustomNotificationSoundUri())
    }

    private fun releasePersistedUri(uriString: String?) {
        if (uriString.isNullOrBlank()) return

        runCatching {
            contentResolver.releasePersistableUriPermission(
                Uri.parse(uriString),
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    }

    private fun getSelectedNotificationSoundDisplayName(): String? {
        return getCustomNotificationSoundUri()
            ?.let(Uri::parse)
            ?.let(::resolveDocumentDisplayName)
    }

    private fun validateCustomNotificationSound(uri: Uri) {
        val mimeType = contentResolver.getType(uri)
        require(mimeType?.startsWith("audio/") == true) {
            "Selected document must be an audio file."
        }
        contentResolver.openAssetFileDescriptor(uri, "r")?.use { asset ->
            require(asset.length != 0L) {
                "Selected audio file is empty."
            }
        } ?: throw IOException("Unable to open selected audio file.")
    }

    private fun resolveDocumentDisplayName(uri: Uri): String? {
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
        return contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex == -1 || !cursor.moveToFirst()) {
                null
            } else {
                cursor.getString(nameIndex)
            }
        }
    }

    private fun syncMedicineReminders() {
        if (medicines.isEmpty()) return
        if (!MedicineReminderScheduler.canScheduleExactAlarms(this)) {
            promptForExactAlarmPermissionIfNeeded()
            return
        }
        MedicineReminderScheduler.scheduleAll(this, medicines)
        requestNotificationPermissionIfNeeded()
    }

    private fun promptForExactAlarmPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        if (exactAlarmPromptShownThisSession) return

        exactAlarmPromptShownThisSession = true
        AlertDialog.Builder(this)
            .setTitle(R.string.medicine_exact_alarm_permission_title)
            .setMessage(R.string.medicine_exact_alarm_permission_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.allow) { _, _ ->
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:$packageName")
                }
                exactAlarmSettingsLauncher.launch(intent)
            }
            .show()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            requestFullScreenIntentPermissionIfNeeded()
            return
        }
        if (hasNotificationPermission()) {
            requestFullScreenIntentPermissionIfNeeded()
            return
        }
        if (AppStatePreferences.hasRequestedNotificationPermission(this)) return

        AppStatePreferences.setNotificationPermissionRequested(this, true)
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun requestFullScreenIntentPermissionIfNeeded() {
        if (canUseFullScreenIntent()) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        if (fullScreenIntentPromptShownThisSession) return

        fullScreenIntentPromptShownThisSession = true
        AlertDialog.Builder(this)
            .setTitle(R.string.medicine_full_screen_intent_permission_title)
            .setMessage(R.string.medicine_full_screen_intent_permission_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.allow) { _, _ ->
                val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                    data = Uri.parse("package:$packageName")
                }
                fullScreenIntentSettingsLauncher.launch(intent)
            }
            .show()
    }

    private fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true
        }
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun canUseFullScreenIntent(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return true
        }
        val notificationManager = getSystemService(NotificationManager::class.java) ?: return false
        return notificationManager.canUseFullScreenIntent()
    }

    private fun handleAlarmFullScreenIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_ALARM_FULL_SCREEN, false) != true) return
        setShowWhenLocked(true)
        setTurnScreenOn(true)
    }

    private fun showScheduleError(@StringRes messageRes: Int) {
        binding.scheduleErrorText.setText(messageRes)
        binding.scheduleErrorText.visibility = View.VISIBLE
    }

    private fun clearScheduleError() {
        binding.scheduleErrorText.visibility = View.GONE
        binding.scheduleErrorText.text = ""
    }

    private fun nextMedicineId(): Long {
        val nextExistingId = (medicines.maxOfOrNull(Medicine::id) ?: 0L) + 1L
        return maxOf(System.currentTimeMillis(), nextExistingId)
    }

    private fun formatScheduledTime(minutesAfterMidnight: Int): String {
        val calendar = Calendar.getInstance().apply {
            clear()
            set(Calendar.HOUR_OF_DAY, minutesAfterMidnight / MINUTES_PER_HOUR)
            set(Calendar.MINUTE, minutesAfterMidnight % MINUTES_PER_HOUR)
        }
        return android.text.format.DateFormat.getTimeFormat(this).format(calendar.time)
    }

    private fun formatScheduledWeekdays(scheduledWeekdays: List<Int>): String {
        if (scheduledWeekdays.size == Medicine.ALL_SCHEDULED_WEEKDAYS.size) {
            return getString(R.string.medicine_schedule_days_every_day)
        }
        return orderedWeekdays()
            .filter { it in scheduledWeekdays }
            .joinToString(", ") { weekdayDisplayName(it) }
    }

    private fun persistableScheduledWeekdays(): List<Int> {
        return Medicine.ALL_SCHEDULED_WEEKDAYS.filter { it in selectedWeekdays }
    }

    private fun orderedWeekdays(): List<Int> {
        val firstDayOfWeek = Calendar.getInstance().firstDayOfWeek
        val zeroBasedFirstDay = firstDayOfWeek - Calendar.SUNDAY
        return (0 until Medicine.ALL_SCHEDULED_WEEKDAYS.size).map { offset ->
            ((zeroBasedFirstDay + offset) % Medicine.ALL_SCHEDULED_WEEKDAYS.size) + Calendar.SUNDAY
        }
    }

    private fun weekdayDisplayName(dayOfWeek: Int): String {
        return Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, dayOfWeek)
        }.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.getDefault())
            ?: dayOfWeek.toString()
    }

    private fun showToast(@StringRes messageRes: Int) {
        Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val MINUTES_PER_HOUR = 60
        const val EXTRA_ALARM_FULL_SCREEN = "alarm_full_screen"
    }
}
