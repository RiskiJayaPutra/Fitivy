package com.fitivy.app.ui.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.fitivy.app.R
import com.fitivy.app.databinding.FragmentHistoryDetailBinding
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class HistoryDetailFragment : Fragment() {

    private var _binding: FragmentHistoryDetailBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: HistoryDetailViewModel by viewModels()
    private val args: HistoryDetailFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        setupObservers()
        viewModel.loadSessionDetail(args.sessionId)
    }

    private fun setupObservers() {
        viewModel.session.observe(viewLifecycleOwner) { session ->
            session?.let {
                binding.tvActivityType.text = it.activityType.replaceFirstChar { char -> char.uppercase() }
                
                val dateFormat = SimpleDateFormat("EEEE, dd MMM yyyy • HH:mm", Locale("id", "ID"))
                binding.tvDate.text = dateFormat.format(Date(it.startedAt))

                val minutes = it.durationSeconds / 60
                val seconds = it.durationSeconds % 60
                binding.tvDuration.text = "${minutes}m ${seconds}s"

                val km = it.distanceMeters / 1000.0
                binding.tvDistance.text = String.format(Locale.US, "%.2f km", km)

                binding.tvCalories.text = "${it.caloriesBurned.toInt()} kcal"
                binding.tvSteps.text = "${it.totalSteps}"

                binding.tvAvgSpeed.text = String.format(Locale.US, "%.1f km/j", it.avgSpeedKmh ?: 0.0)
                binding.tvMaxSpeed.text = String.format(Locale.US, "%.1f km/j", it.maxSpeedKmh ?: 0.0)

                // Set icon based on activity type
                val iconRes = when (it.activityType.lowercase()) {
                    "running" -> R.drawable.ic_run
                    "cycling" -> R.drawable.ic_bike // Assuming you have this or use nav_history
                    else -> R.drawable.ic_walk
                }
                binding.ivBigIcon.setImageResource(iconRes)
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.overlayLoading.visibility = if (isLoading) View.VISIBLE else View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
