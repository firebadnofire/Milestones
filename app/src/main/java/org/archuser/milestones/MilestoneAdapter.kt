package org.archuser.milestones

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.archuser.milestones.databinding.ItemMilestoneBinding
import java.text.SimpleDateFormat

class MilestoneAdapter(
    private val dateFormatter: SimpleDateFormat,
    private val onRemove: (Milestone) -> Unit,
    private val onReset: (Milestone) -> Unit
) : RecyclerView.Adapter<MilestoneAdapter.MilestoneViewHolder>() {

    private val items = mutableListOf<Milestone>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MilestoneViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemMilestoneBinding.inflate(inflater, parent, false)
        return MilestoneViewHolder(binding, onRemove, onReset, dateFormatter)
    }

    override fun onBindViewHolder(holder: MilestoneViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    fun submitList(newItems: List<Milestone>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    class MilestoneViewHolder(
        private val binding: ItemMilestoneBinding,
        private val onRemove: (Milestone) -> Unit,
        private val onReset: (Milestone) -> Unit,
        private val dateFormatter: SimpleDateFormat
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(milestone: Milestone) {
            binding.milestoneName.text = milestone.name
            binding.milestoneDate.text =
                binding.root.context.getString(
                    R.string.milestone_date_label,
                    dateFormatter.format(milestone.startDateMillis)
                )
            binding.resetCountLabel.text =
                binding.root.context.getString(
                    R.string.milestone_resets_last_7_days,
                    MilestoneStats.recentResetCount(milestone)
                )

            val days = MilestoneStats.daysFromToday(milestone)
            if (days >= 0) {
                binding.milestoneDays.text =
                    binding.root.context.getString(R.string.milestone_days_since, days)
            } else {
                binding.milestoneDays.text =
                    binding.root.context.getString(R.string.milestone_days_until, -days)
            }

            binding.removeButton.setOnClickListener { onRemove(milestone) }
            binding.resetButton.setOnClickListener { onReset(milestone) }
        }
    }
}
