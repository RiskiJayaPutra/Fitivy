package com.fitivy.app.ui.history

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fitivy.app.R
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

            // Icon
            val iconRes = when (session.activityType.lowercase()) {
                "running" -> R.drawable.ic_run
                "cycling" -> R.drawable.ic_bike
                "walking" -> R.drawable.ic_walk
                else -> R.drawable.ic_run
            }
            binding.ivActivityIcon.setImageResource(iconRes)

            // Activity Type
            binding.tvActivityType.text = session.activityType.replaceFirstChar { it.uppercase() }

            // Date formatting
            val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID"))
            binding.tvDate.text = dateFormat.format(Date(session.startedAt))

            // Stats (Distance, Duration, Calories)
            val km = session.distanceMeters / 1000.0
            val minutes = session.durationSeconds / 60
            val kcal = session.caloriesBurned.toInt()
            binding.tvStats.text = String.format(Locale.US, "%.1f km • %d mnt • %d kcal", km, minutes, kcal)
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
