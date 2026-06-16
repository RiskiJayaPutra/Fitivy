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
        weeklyBarChart = view.findViewById(R.id.weeklyBarChart)
        tvWeeklyEmpty = view.findViewById(R.id.tvWeeklyEmpty)
        monthlyLineChart = view.findViewById(R.id.monthlyLineChart)
        tvMonthlyEmpty = view.findViewById(R.id.tvMonthlyEmpty)
        activityRadarChart = view.findViewById(R.id.activityRadarChart)
        tvRadarEmpty = view.findViewById(R.id.tvRadarEmpty)

        setupCharts()
        observeViewModel()
        
        summaryCardView.setOnNotificationClickListener {
            findNavController().navigate(R.id.notificationFragment)
        }
    }

    private fun setupCharts() {
        val gridColor = "#F1F5F9".toColorInt()
        val labelColor = "#94A3B8".toColorInt()

        weeklyBarChart.apply {
            description.isEnabled = false
            legend.isEnabled = false
            setDrawGridBackground(false)
            setDrawBorders(false)
            setTouchEnabled(false)
            setExtraOffsets(0f, 0f, 0f, 8f)

            axisLeft.apply {
                axisMinimum = 0f
                setDrawGridLines(true)
                this.gridColor = gridColor
                textColor = labelColor
                textSize = 10f
                enableGridDashedLine(10f, 10f, 0f)
                setDrawAxisLine(false)
            }
            axisRight.isEnabled = false

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                textColor = labelColor
                textSize = 11f
                granularity = 1f
                setDrawAxisLine(false)
            }
        }

        monthlyLineChart.apply {
            description.isEnabled = false
            legend.isEnabled = false
            setDrawGridBackground(false)
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(false)
            setExtraOffsets(0f, 0f, 0f, 8f)

            axisLeft.apply {
                axisMinimum = 0f
                setDrawGridLines(true)
                this.gridColor = gridColor
                textColor = labelColor
                textSize = 10f
                enableGridDashedLine(10f, 10f, 0f)
                setDrawAxisLine(false)
            }
            axisRight.isEnabled = false

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                textColor = labelColor
                textSize = 11f
                granularity = 1f
                setDrawAxisLine(false)
            }
        }

        activityRadarChart.apply {
            description.isEnabled = false
            legend.isEnabled = false
            webColor = "#E2E8F0".toColorInt()
            webLineWidth = 1f
            webColorInner = "#E2E8F0".toColorInt()
            webLineWidthInner = 1f
            setTouchEnabled(false)

            xAxis.apply {
                textSize = 12f
                textColor = "#64748B".toColorInt()
                valueFormatter = IndexAxisValueFormatter(listOf("Berjalan", "Berlari", "Bersepeda"))
            }

            yAxis.apply {
                setDrawLabels(false)
                axisMinimum = 0f
                axisMaximum = 1f
            }
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
            targetSteps = state.todayTarget,
            caloriesBurned = state.todayCalories,
            durationSeconds = state.todayDuration,
            distanceMeters = state.todayDistance
        )

        tvStreak.text = "${state.currentStreak}"

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
