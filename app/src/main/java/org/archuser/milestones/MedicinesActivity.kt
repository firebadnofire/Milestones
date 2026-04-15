package org.archuser.milestones

import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
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
    private lateinit var exportLauncher: ActivityResultLauncher<String>
    private lateinit var importLauncher: ActivityResultLauncher<Array<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyMaterialYouIfEnabled()

        binding = ActivityMedicinesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        setupDrawer()
        setupMenuActions()
        setupImportExportLaunchers()

        adapter = MedicineAdapter(
            onDoseChecked = { medicine, doseIndex, isChecked ->
                updateDoseStatus(medicine, doseIndex, isChecked)
            },
            onRemove = { medicine ->
                confirmRemoveMedicine(medicine)
            },
            formatScheduledTime = { minutesAfterMidnight ->
                formatScheduledTime(minutesAfterMidnight)
            }
        )

        binding.medicineList.layoutManager = LinearLayoutManager(this)
        binding.medicineList.adapter = adapter
        binding.medicineList.isNestedScrollingEnabled = false

        binding.addDoseTimeButton.setOnClickListener { showTimePicker() }
        binding.addMedicineButton.setOnClickListener { addMedicine() }
        renderSelectedScheduleTimes()
    }

    override fun onResume() {
        super.onResume()
        refreshAppState()
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

    private fun refreshAppState() {
        runCatching { AppStatePreferences.load(this) }
            .onSuccess { appState ->
                milestones.clear()
                milestones.addAll(appState.milestones)
                medicines.clear()
                medicines.addAll(appState.medicines)
                updateMedicineList()
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

    private fun addMedicine() {
        val name = binding.medicineNameInputEditText.text?.toString()?.trim().orEmpty()

        binding.medicineNameInputLayout.error = null
        clearScheduleError()

        var hasError = false
        if (name.isBlank()) {
            binding.medicineNameInputLayout.error = getString(R.string.error_medicine_name_required)
            hasError = true
        }
        if (selectedScheduleTimes.isEmpty()) {
            showScheduleError(R.string.error_schedule_required)
            hasError = true
        }
        if (hasError) return

        medicines.add(
            Medicine(
                id = nextMedicineId(),
                name = name,
                scheduledTimes = selectedScheduleTimes.toList()
            )
        )

        binding.medicineNameInputEditText.setText("")
        selectedScheduleTimes.clear()
        renderSelectedScheduleTimes()
        persistAppState()
        updateMedicineList()
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
            milestones.clear()
            milestones.addAll(importedState.milestones)
            medicines.clear()
            medicines.addAll(importedState.medicines)
            persistAppState()
            updateMedicineList()
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

    private fun showToast(@StringRes messageRes: Int) {
        Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val MINUTES_PER_HOUR = 60
    }
}
