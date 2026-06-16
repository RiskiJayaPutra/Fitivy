package com.fitivy.app.ui.dashboard

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.animation.DecelerateInterpolator
import android.widget.ProgressBar
import android.widget.TextView
import com.fitivy.app.R
import com.google.android.material.card.MaterialCardView
import java.text.NumberFormat
import java.util.Locale

class SummaryCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : MaterialCardView(context, attrs, defStyleAttr) {

    private val progressRing: ProgressBar
    private val tvStepCount: TextView
    private val tvCalories: TextView
    private val tvDuration: TextView
    private val tvDistance: TextView
    private val tvDistanceUnit: TextView
    private val tvTargetPercent: TextView
    private val ivNotification: android.widget.ImageView

    private val numberFormat = NumberFormat.getNumberInstance(Locale("id", "ID"))
    private var currentSteps = 0

    init {
        LayoutInflater.from(context).inflate(R.layout.view_summary_card, this, true)

        progressRing = findViewById(R.id.progressRing)
        tvStepCount = findViewById(R.id.tvStepCount)
        tvCalories = findViewById(R.id.tvCalories)
        tvDuration = findViewById(R.id.tvDuration)
        tvDistance = findViewById(R.id.tvDistance)
        tvDistanceUnit = findViewById(R.id.tvDistanceUnit)
        tvTargetPercent = findViewById(R.id.tvTargetPercent)
        ivNotification = findViewById(R.id.ivNotification)

        // Modern card styling — no elevation, rounded
        radius = resources.getDimension(R.dimen.card_radius)
        cardElevation = 0f
        strokeWidth = 0
    }

    fun updateData(
        steps: Int,
        targetSteps: Int,
        caloriesBurned: Double,
        durationSeconds: Long,
        distanceMeters: Double
    ) {
        progressRing.max = targetSteps

        // Animate Step Count & Progress Ring with decelerate
        val animator = ValueAnimator.ofInt(currentSteps, steps)
        animator.duration = 800
        animator.interpolator = DecelerateInterpolator()
        animator.addUpdateListener { animation ->
            val value = animation.animatedValue as Int
            tvStepCount.text = numberFormat.format(value)
            progressRing.progress = value
        }
        animator.start()
        currentSteps = steps

        // Stats
        tvCalories.text = String.format(Locale.US, "%.0f", caloriesBurned)

        val minutes = durationSeconds / 60
        tvDuration.text = "$minutes"

        if (distanceMeters < 1000.0) {
            tvDistance.text = String.format(Locale.US, "%.0f", distanceMeters)
            tvDistanceUnit.text = "m"
        } else {
            val distanceKm = distanceMeters / 1000.0
            tvDistance.text = String.format(Locale.US, "%.1f", distanceKm)
            tvDistanceUnit.text = "km"
        }

        // Target text
        tvTargetPercent.text = "dari ${numberFormat.format(targetSteps)}"
    }

    fun setOnNotificationClickListener(listener: () -> Unit) {
        ivNotification.setOnClickListener { listener() }
    }
}
