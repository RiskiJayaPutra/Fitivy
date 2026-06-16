package com.fitivy.app.ui.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.util.Pair
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.fitivy.app.R
import com.fitivy.app.databinding.FragmentHistoryBinding
import com.google.android.material.datepicker.MaterialDatePicker
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: HistoryViewModel by viewModels()
    private lateinit var adapter: HistoryAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupObservers()
        setupListeners()
    }

    private fun setupRecyclerView() {
        adapter = HistoryAdapter { session ->
            val bundle = Bundle().apply {
                putString("sessionId", session.id)
            }
            findNavController().navigate(R.id.historyDetailFragment, bundle)
        }
        binding.rvHistory.layoutManager = LinearLayoutManager(requireContext())
        binding.rvHistory.adapter = adapter
    }

    private fun setupObservers() {
        viewModel.historyList.observe(viewLifecycleOwner) { sessions ->
            adapter.submitList(sessions)
            binding.layoutEmpty.visibility = if (sessions.isEmpty()) View.VISIBLE else View.GONE
            binding.rvHistory.visibility = if (sessions.isEmpty()) View.GONE else View.VISIBLE
        }

        viewModel.totalSessions.observe(viewLifecycleOwner) { total ->
            binding.tvTotalSessions.text = "$total"
        }

        viewModel.totalDistance.observe(viewLifecycleOwner) { dist ->
            val km = dist / 1000.0
            binding.tvTotalDistance.text = String.format(java.util.Locale.US, "%.1f", km)
        }

        viewModel.totalCalories.observe(viewLifecycleOwner) { cal ->
            binding.tvTotalCalories.text = "${cal.toInt()}"
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.overlayLoading.visibility = if (isLoading) View.VISIBLE else View.GONE
        }
    }

    private fun setupListeners() {
        binding.btnFilterDate.setOnClickListener {
            showDateRangePicker()
        }
    }

    private fun showDateRangePicker() {
        val datePicker = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText("Pilih Rentang Tanggal")
            .build()

        datePicker.addOnPositiveButtonClickListener { selection ->
            val startMs = selection.first
            val endMs = selection.second
            
            // endMs biasanya 00:00 hari itu, tambahkan 24 jam agar mencakup akhir hari
            val endMsAdjusted = endMs + (24 * 60 * 60 * 1000) - 1
            
            viewModel.filterHistoryByDate(startMs, endMsAdjusted)
        }

        datePicker.show(childFragmentManager, "DATE_PICKER")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
