package org.archuser.milestones

import android.app.DatePickerDialog
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
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
    private val dateFormatter = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    private var selectedDateMillis: Long? = null
    private lateinit var exportLauncher: ActivityResultLauncher<String>
    private lateinit var importLauncher: ActivityResultLauncher<Array<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyMaterialYouIfEnabled()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        setupDrawer()
        setupMenuActions()
        setupImportExportLaunchers()

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

        loadMilestones()
        updateMilestoneList()
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
                id = System.currentTimeMillis(),
                name = name,
                startDateMillis = dateMillis!!
            )
        )

        binding.nameInputEditText.setText("")
        binding.dateInputEditText.setText("")
        selectedDateMillis = null

        persistMilestones()
        updateMilestoneList()
    }

    private fun updateMilestoneList() {
        val sorted = milestones.sortedByDescending { it.startDateMillis }
        adapter.submitList(sorted)
        binding.emptyStateText.visibility = if (sorted.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun persistMilestones() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit {
            putString(PREFS_KEY, MilestoneStorage.encode(milestones))
        }
    }

    private fun loadMilestones() {
        val stored = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getString(PREFS_KEY, null)
            ?: return
        milestones.clear()
        milestones.addAll(MilestoneStorage.decode(stored))
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

    private fun exportToUri(uri: Uri) {
        val payload = MilestoneStorage.encode(milestones)
        try {
            contentResolver.openOutputStream(uri)?.use { output ->
                output.write(payload.toByteArray())
            }
            Toast.makeText(this, R.string.export_success, Toast.LENGTH_SHORT).show()
        } catch (error: IOException) {
            Toast.makeText(this, R.string.export_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun importFromUri(uri: Uri) {
        try {
            val payload = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (payload.isBlank()) {
                Toast.makeText(this, R.string.import_failed, Toast.LENGTH_SHORT).show()
                return
            }
            val imported = MilestoneStorage.decode(payload)
            milestones.clear()
            milestones.addAll(imported)
            persistMilestones()
            updateMilestoneList()
            Toast.makeText(this, R.string.import_success, Toast.LENGTH_SHORT).show()
        } catch (error: Exception) {
            Toast.makeText(this, R.string.import_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun applyMaterialYouIfEnabled() {
        if (isMaterialYouEnabled()) {
            DynamicColors.applyToActivityIfAvailable(this)
        }
    }

    private fun isMaterialYouEnabled(): Boolean {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getBoolean(PREFS_KEY_MATERIAL_YOU, false)
    }

    private fun setMaterialYouEnabled(enabled: Boolean) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit {
            putBoolean(PREFS_KEY_MATERIAL_YOU, enabled)
        }
    }

    private fun normalizeToMidnight(timestampMillis: Long): Long {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = timestampMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }

    private fun confirmRemove(milestone: Milestone) {
        AlertDialog.Builder(this)
            .setTitle(R.string.confirm_remove_title)
            .setMessage(getString(R.string.confirm_remove_message, milestone.name))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.remove) { _, _ ->
                milestones.removeAll { it.id == milestone.id }
                persistMilestones()
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
                val today = normalizeToMidnight(System.currentTimeMillis())
                val index = milestones.indexOfFirst { it.id == milestone.id }
                if (index != -1) {
                    milestones[index] = milestone.copy(startDateMillis = today)
                }
                persistMilestones()
                updateMilestoneList()
            }
            .show()
    }

    companion object {
        private const val PREFS_NAME = "milestones_prefs"
        private const val PREFS_KEY = "milestone_entries"
        private const val PREFS_KEY_MATERIAL_YOU = "material_you_enabled"
    }
}
