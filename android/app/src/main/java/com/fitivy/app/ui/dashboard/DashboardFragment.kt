package com.fitivy.app.ui.dashboard

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.core.graphics.toColorInt
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.navigation.fragment.findNavController
import com.fitivy.app.R
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.RadarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DashboardFragment : Fragment(R.layout.fragment_dashboard) {

    private val viewModel: DashboardViewModel by viewModels()

    private lateinit var summaryCardView: SummaryCardView
    private lateinit var tvStreak: TextView
    private lateinit var tvCalories: TextView
    private lateinit var tvDuration: TextView
    private lateinit var tvDistance: TextView

    private lateinit var weeklyBarChart: BarChart
    private lateinit var tvWeeklyEmpty: TextView
    private lateinit var monthlyLineChart: LineChart
    private lateinit var tvMonthlyEmpty: TextView
    private lateinit var activityRadarChart: RadarChart
    private lateinit var tvRadarEmpty: TextView

    private val activityRecognitionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Permission granted, tracker will work
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Request Activity Recognition for passive step tracking (Android 10+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
                activityRecognitionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
            }
        }

        summaryCardView = view.findViewById(R.id.summaryCardView)
        tvStreak = view.findViewById(R.id.tvStreak)
        tvCalories = view.findViewById(R.id.tvCalories)
        tvDuration = view.findViewById(R.id.tvDuration)
        tvDistance = view.findViewById(R.id.tvDistance)

        weeklyBarChart = view.findViewById(R.id.weeklyBarChart)
        tvWeeklyEmpty = view.findViewById(R.id.tvWeeklyEmpty)
        monthlyLineChart = view.findViewById(R.id.monthlyLineChart)
        tvMonthlyEmpty = view.findViewById(R.id.tvMonthlyEmpty)
        activityRadarChart = view.findViewById(R.id.activityRadarChart)
        tvRadarEmpty = view.findViewById(R.id.tvRadarEmpty)

        observeViewModel()
        
        summaryCardView.setOnNotificationClickListener {
            findNavController().navigate(R.id.notificationFragment)
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    if (!state.isLoading) {
                        updateUi(state)
                    }
                }
            }
        }
    }

    private fun updateUi(state: DashboardUiState) {
        summaryCardView.updateData(
            steps = state.todaySteps,
            targetSteps = state.todayTarget
        )

        tvStreak.text = "${state.currentStreak} Days"
        tvCalories.text = String.format("%.0f kcal", state.todayCalories)
        
        val minutes = state.todayDuration / 60
        val hours = minutes / 60
        val remainingMinutes = minutes % 60
        tvDuration.text = if (hours > 0) "${hours}h ${remainingMinutes}m" else "${remainingMinutes}m"

        if (state.todayDistance < 1000.0) {
            tvDistance.text = String.format("%.0f m", state.todayDistance)
        } else {
            tvDistance.text = String.format("%.1f km", state.todayDistance / 1000.0)
        }

        updateWeeklyChart(state.weeklyData, state.todayTarget)
        updateMonthlyChart(state.monthlyData)
        updateRadarChart(state.activityRatio)
    }

    private fun updateWeeklyChart(weeklyData: List<DailySteps>, target: Int) {
        if (weeklyData.all { it.steps == 0 }) {
            weeklyBarChart.visibility = View.INVISIBLE
            tvWeeklyEmpty.visibility = View.VISIBLE
            return
        }

        weeklyBarChart.visibility = View.VISIBLE
        tvWeeklyEmpty.visibility = View.GONE

        val entries = weeklyData.mapIndexed { index, data ->
            BarEntry(index.toFloat(), data.steps.toFloat())
        }

        val colors = weeklyData.map {
            when {
                it.steps >= target -> "#22C55E".toColorInt()
                it.steps >= target * 0.5 -> "#FB923C".toColorInt()
                else -> "#EF4444".toColorInt()
            }
        }

        val dataSet = BarDataSet(entries, "Langkah Harian").apply {
            setColors(colors)
            valueTextColor = Color.TRANSPARENT
        }

        weeklyBarChart.data = BarData(dataSet).apply {
            barWidth = 0.5f
        }
        weeklyBarChart.xAxis.valueFormatter = IndexAxisValueFormatter(weeklyData.map { it.dayName })
        weeklyBarChart.animateY(1200)
    }

    private fun updateMonthlyChart(monthlyData: List<WeeklyAvg>) {
        if (monthlyData.all { it.avgSteps == 0 }) {
            monthlyLineChart.visibility = View.INVISIBLE
            tvMonthlyEmpty.visibility = View.VISIBLE
            return
        }

        monthlyLineChart.visibility = View.VISIBLE
        tvMonthlyEmpty.visibility = View.GONE

        val entries = monthlyData.mapIndexed { index, data ->
            Entry(index.toFloat(), data.avgSteps.toFloat())
        }

        val dataSet = LineDataSet(entries, "Rata-rata Mingguan").apply {
            color = "#06B6D4".toColorInt()
            setCircleColor("#0891B2".toColorInt())
            lineWidth = 3f
            circleRadius = 5f
            setDrawCircleHole(true)
            circleHoleRadius = 2.5f
            setDrawFilled(true)
            fillColor = "#CFFAFE".toColorInt()
            fillAlpha = 150
            mode = LineDataSet.Mode.CUBIC_BEZIER
            valueTextColor = Color.TRANSPARENT
        }

        monthlyLineChart.data = LineData(dataSet)
        monthlyLineChart.xAxis.valueFormatter = IndexAxisValueFormatter(monthlyData.map { it.weekLabel })
        monthlyLineChart.animateX(1200)
    }

    private fun updateRadarChart(ratio: ActivityRatio) {
        if (ratio.walking == 0f && ratio.running == 0f && ratio.cycling == 0f) {
            activityRadarChart.visibility = View.INVISIBLE
            tvRadarEmpty.visibility = View.VISIBLE
            return
        }

        activityRadarChart.visibility = View.VISIBLE
        tvRadarEmpty.visibility = View.GONE

        val entries = listOf(
            RadarEntry(ratio.walking),
            RadarEntry(ratio.running),
            RadarEntry(ratio.cycling)
        )

        val dataSet = RadarDataSet(entries, "Aktivitas").apply {
            color = "#8B5CF6".toColorInt()
            fillColor = "#DDD6FE".toColorInt()
            setDrawFilled(true)
            fillAlpha = 180
            lineWidth = 2f
            isDrawHighlightCircleEnabled = true
            setDrawHighlightIndicators(false)
            valueTextColor = Color.TRANSPARENT
        }

        activityRadarChart.data = RadarData(dataSet)
        activityRadarChart.animateY(1200)
    }
}
