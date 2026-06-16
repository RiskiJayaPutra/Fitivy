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
    private val tvTargetPercent: TextView
    private val ivNotification: android.widget.ImageView

    private val numberFormat = NumberFormat.getNumberInstance(Locale("id", "ID"))
    private var currentSteps = 0

    init {
        // MaterialCardView transparent to let inner card show
        setCardBackgroundColor(android.graphics.Color.TRANSPARENT)
        cardElevation = 0f
        strokeWidth = 0

        LayoutInflater.from(context).inflate(R.layout.view_summary_card, this, true)

        progressRing = findViewById(R.id.progressRing)
        tvStepCount = findViewById(R.id.tvStepCount)
        tvTargetPercent = findViewById(R.id.tvTargetPercent)
        ivNotification = findViewById(R.id.ivNotification)
    }

    fun updateData(
        steps: Int,
        targetSteps: Int
    ) {
        progressRing.max = targetSteps

        // Animate Step Count & Progress Ring
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

        // Target text
        tvTargetPercent.text = "Daily Goal: ${numberFormat.format(targetSteps)}"
    }

    fun setOnNotificationClickListener(listener: () -> Unit) {
        ivNotification.setOnClickListener { listener() }
    }
}
