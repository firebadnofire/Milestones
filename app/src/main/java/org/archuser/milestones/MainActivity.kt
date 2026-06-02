package org.archuser.milestones

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
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
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.color.DynamicColors
import com.google.android.material.materialswitch.MaterialSwitch
import org.archuser.milestones.databinding.ActivityMainBinding
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: MilestoneAdapter
    private val milestones = mutableListOf<Milestone>()
    private val medicines = mutableListOf<Medicine>()
    private val dateFormatter = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    private var selectedDateMillis: Long? = null
    private lateinit var exportLauncher: ActivityResultLauncher<String>
    private lateinit var importLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var customNotificationSoundLauncher: ActivityResultLauncher<Array<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyMaterialYouIfEnabled()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        setupDrawer()
        setupMenuActions()
        setupImportExportLaunchers()
        setupCustomNotificationSoundLauncher()

        adapter = MilestoneAdapter(
            dateFormatter = dateFormatter,
            onRemove = { milestone ->
                confirmRemove(milestone)
            },
            onReset = { milestone ->
                confirmReset(milestone)
            }
        )

        binding.milestoneList.layoutManager = LinearLayoutManager(this)
        binding.milestoneList.adapter = adapter
        binding.milestoneList.isNestedScrollingEnabled = false

        binding.dateInputEditText.setOnClickListener { showDatePicker() }
        binding.addMilestoneButton.setOnClickListener { addMilestone() }
    }

    override fun onResume() {
        super.onResume()
        refreshAppState()
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        selectedDateMillis?.let { calendar.timeInMillis = it }

        val datePickerDialog = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val chosenCalendar = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                }
                selectedDateMillis = normalizeToMidnight(chosenCalendar.timeInMillis)
                binding.dateInputLayout.error = null
                binding.dateInputEditText.setText(dateFormatter.format(chosenCalendar.time))
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePickerDialog.show()
    }

    private fun addMilestone() {
        val name = binding.nameInputEditText.text?.toString()?.trim().orEmpty()

        binding.nameInputLayout.error = null
        binding.dateInputLayout.error = null

        var hasError = false
        if (name.isBlank()) {
            binding.nameInputLayout.error = getString(R.string.error_name_required)
            hasError = true
        }

        val dateMillis = selectedDateMillis
        if (dateMillis == null) {
            binding.dateInputLayout.error = getString(R.string.error_date_required)
            hasError = true
        }

        if (hasError) return

        milestones.add(
            Milestone(
                id = nextMilestoneId(),
                name = name,
                startDateMillis = dateMillis!!
            )
        )

        binding.nameInputEditText.setText("")
        binding.dateInputEditText.setText("")
        selectedDateMillis = null

        persistAppState()
        updateMilestoneList()
    }

    private fun updateMilestoneList() {
        val sorted = milestones.sortedByDescending { it.startDateMillis }
        adapter.submitList(sorted)
        binding.emptyStateText.visibility = if (sorted.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun refreshAppState() {
        runCatching { AppStatePreferences.load(this) }
            .onSuccess { appState ->
                milestones.clear()
                milestones.addAll(appState.milestones)
                medicines.clear()
                medicines.addAll(appState.medicines)
                updateMilestoneList()
                syncMedicineReminders()
            }
            .onFailure {
                milestones.clear()
                medicines.clear()
                updateMilestoneList()
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
            true
        }
        menu.findItem(R.id.action_medicines).setOnMenuItemClickListener {
            binding.drawerLayout.closeDrawer(binding.navigationView)
            startActivity(
                Intent(this, MedicinesActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            )
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

    private fun setupCustomNotificationSoundLauncher() {
        customNotificationSoundLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                importCustomNotificationSound(uri)
            }
        }
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
            updateMilestoneList()
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
        if (medicines.isNotEmpty()) {
            MedicineReminderScheduler.scheduleAll(this, medicines)
        }
    }

    private fun normalizeToMidnight(timestampMillis: Long): Long {
        return LocalDay.fromTimestamp(timestampMillis).startOfDayMillis()
    }

    private fun confirmRemove(milestone: Milestone) {
        AlertDialog.Builder(this)
            .setTitle(R.string.confirm_remove_title)
            .setMessage(getString(R.string.confirm_remove_message, milestone.name))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.remove) { _, _ ->
                milestones.removeAll { it.id == milestone.id }
                persistAppState()
                updateMilestoneList()
            }
            .show()
    }

    private fun confirmReset(milestone: Milestone) {
        AlertDialog.Builder(this)
            .setTitle(R.string.confirm_reset_title)
            .setMessage(getString(R.string.confirm_reset_message, milestone.name))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.reset) { _, _ ->
                val today = LocalDay.today()
                val index = milestones.indexOfFirst { it.id == milestone.id }
                if (index != -1) {
                    milestones[index] = milestone.copy(
                        startDateMillis = today.startOfDayMillis(),
                        resetHistory = milestone.resetHistory + today.key()
                    )
                }
                persistAppState()
                updateMilestoneList()
            }
            .show()
    }

    private fun nextMilestoneId(): Long {
        val nextExistingId = (milestones.maxOfOrNull(Milestone::id) ?: 0L) + 1L
        return maxOf(System.currentTimeMillis(), nextExistingId)
    }

    private fun showToast(@StringRes messageRes: Int) {
        Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
    }
}
