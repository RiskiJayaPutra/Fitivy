package com.fitivy.app.ui.history

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fitivy.app.data.local.entity.ActivitySessionEntity
import com.fitivy.app.databinding.ItemHistoryBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryAdapter(
    private val onClick: (ActivitySessionEntity) -> Unit
) : ListAdapter<ActivitySessionEntity, HistoryAdapter.HistoryViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = ItemHistoryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return HistoryViewHolder(binding, onClick)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class HistoryViewHolder(
        private val binding: ItemHistoryBinding,
        private val onClick: (ActivitySessionEntity) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(session: ActivitySessionEntity) {
            binding.root.setOnClickListener { onClick(session) }

            // Activity Type
            binding.tvActivityType.text = session.activityType.replaceFirstChar { it.uppercase() }

            // Date formatting
            val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID"))
            binding.tvDate.text = dateFormat.format(Date(session.startedAt))

            // Duration
            val minutes = session.durationSeconds / 60
            binding.tvDuration.text = "$minutes mnt"

            // Distance
            val km = session.distanceMeters / 1000.0
            binding.tvDistance.text = String.format(Locale.US, "%.1f km", km)

            // Calories
            binding.tvCalories.text = "${session.caloriesBurned.toInt()} kcal"
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<ActivitySessionEntity>() {
            override fun areItemsTheSame(
                oldItem: ActivitySessionEntity,
                newItem: ActivitySessionEntity
            ): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(
                oldItem: ActivitySessionEntity,
                newItem: ActivitySessionEntity
            ): Boolean {
                return oldItem == newItem
            }
        }
    }
}
