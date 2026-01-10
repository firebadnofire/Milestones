package org.archuser.milestones

import android.app.DatePickerDialog
import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.recyclerview.widget.LinearLayoutManager
import org.archuser.milestones.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: MilestoneAdapter
    private val milestones = mutableListOf<Milestone>()
    private val dateFormatter = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    private var selectedDateMillis: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        adapter = MilestoneAdapter(
            dateFormatter = dateFormatter,
            onRemove = { milestone ->
                milestones.removeAll { it.id == milestone.id }
                persistMilestones()
                updateMilestoneList()
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
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putString(PREFS_KEY, MilestoneStorage.encode(milestones))
        }
    }

    private fun loadMilestones() {
        val stored = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(PREFS_KEY, null)
            ?: return
        milestones.clear()
        milestones.addAll(MilestoneStorage.decode(stored))
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

    companion object {
        private const val PREFS_NAME = "milestones_prefs"
        private const val PREFS_KEY = "milestone_entries"
    }
}
