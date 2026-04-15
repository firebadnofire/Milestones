package org.archuser.milestones

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.archuser.milestones.databinding.ItemMedicineBinding
import org.archuser.milestones.databinding.ItemMedicineDoseBinding

class MedicineAdapter(
    private val onDoseChecked: (Medicine, Int, Boolean) -> Unit,
    private val onRemove: (Medicine) -> Unit,
    private val formatScheduledTime: (Int) -> String
) : RecyclerView.Adapter<MedicineAdapter.MedicineViewHolder>() {

    private val items = mutableListOf<Medicine>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MedicineViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemMedicineBinding.inflate(inflater, parent, false)
        return MedicineViewHolder(binding, onDoseChecked, onRemove, formatScheduledTime)
    }

    override fun onBindViewHolder(holder: MedicineViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    fun submitList(newItems: List<Medicine>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    class MedicineViewHolder(
        private val binding: ItemMedicineBinding,
        private val onDoseChecked: (Medicine, Int, Boolean) -> Unit,
        private val onRemove: (Medicine) -> Unit,
        private val formatScheduledTime: (Int) -> String
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(medicine: Medicine) {
            val today = LocalDay.today()
            val todaySummary = MedicineStats.todayDoseSummary(medicine, today)

            binding.medicineName.text = medicine.name
            binding.medicineStreak.text = binding.root.context.getString(
                R.string.medicine_current_streak,
                MedicineStats.currentStreak(medicine, today)
            )
            binding.medicineTodaySummary.text = binding.root.context.getString(
                R.string.medicine_today_summary,
                todaySummary.takenCount,
                todaySummary.totalCount
            )

            bindDoseControls(medicine, today)
            binding.removeMedicineButton.setOnClickListener { onRemove(medicine) }
        }

        private fun bindDoseControls(medicine: Medicine, today: LocalDay) {
            binding.doseControlsContainer.removeAllViews()
            val inflater = LayoutInflater.from(binding.root.context)

            medicine.scheduledTimes.forEachIndexed { doseIndex, scheduledTime ->
                val doseBinding = ItemMedicineDoseBinding.inflate(
                    inflater,
                    binding.doseControlsContainer,
                    false
                )
                doseBinding.doseCheckBox.text = formatScheduledTime(scheduledTime)
                doseBinding.doseCheckBox.setOnCheckedChangeListener(null)
                doseBinding.doseCheckBox.isChecked = MedicineStats.isDoseTaken(
                    medicine,
                    today,
                    doseIndex
                )
                doseBinding.doseCheckBox.setOnCheckedChangeListener { _, isChecked ->
                    onDoseChecked(medicine, doseIndex, isChecked)
                }
                binding.doseControlsContainer.addView(doseBinding.root)
            }
        }
    }
}
