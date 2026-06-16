package com.fitivy.app.sensor

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.util.Log
import com.fitivy.app.data.local.dao.StepCounterBaselineDao
import com.fitivy.app.data.local.dao.StepLogDao
import com.fitivy.app.data.local.entity.StepCounterBaselineEntity
import com.fitivy.app.data.local.entity.StepLogEntity
import com.fitivy.app.sensor.model.ActivityType
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * StepCounterManager — mengelola step counting menggunakan TYPE_STEP_COUNTER.
 *
 * KENAPA TYPE_STEP_COUNTER dan bukan TYPE_STEP_DETECTOR?
 *   - TYPE_STEP_COUNTER: cumulative sejak boot, hardware-accelerated, rendah battery
 *   - TYPE_STEP_DETECTOR: event per langkah, software-based pada beberapa device, lebih boros
 *   - Counter lebih akurat karena firmware chip menghitung di low-power coprocessor
 *
 * PROBLEM: TYPE_STEP_COUNTER reset ke 0 setelah device reboot.
 * SOLUTION: Simpan baseline (nilai sensor saat session dimulai) di Room DB.
 *   - Jika sensor value baru < last known value → reboot detected → adjust baseline
 *
 * PERSISTENCE: Step log disimpan ke Room setiap 10 detik via coroutine ticker.
 *
 * KALORI: steps × weight_kg × 0.0005 (simplified, bisa di-override dengan MET formula)
 */
@Singleton
class StepCounterManager @Inject constructor(
    private val sensorManager: SensorManager,
    private val stepLogDao: StepLogDao,
    private val baselineDao: StepCounterBaselineDao,
) : SensorEventListener {

    companion object {
        private const val TAG = "StepCounterManager"
        private const val SAVE_INTERVAL_MS = 10_000L       // Simpan ke Room setiap 10 detik
        private const val CALORIE_FACTOR = 0.0005           // kcal per step per kg
    }

    // === STATE ===
    private val _totalSteps = MutableStateFlow(0)
    val totalSteps: StateFlow<Int> = _totalSteps.asStateFlow()

    private val _caloriesBurned = MutableStateFlow(0.0)
    val caloriesBurned: StateFlow<Double> = _caloriesBurned.asStateFlow()

    private val _cadence = MutableStateFlow(0)
    val cadence: StateFlow<Int> = _cadence.asStateFlow()

    // === INTERNAL ===
    private var currentSessionId: String? = null
    private var userWeightKg: Float = 65f                    // Default, di-update dari profil
    private var isTracking = false

    // Step counter baseline — untuk handle reboot
    private var sensorBaseline: Int? = null                  // Nilai sensor saat session start
    private var lastKnownSensorValue: Int = 0
    private var accumulatedStepsBeforeReboot: Int = 0
    
    // Detector state untuk instant UI update
    private var isUsingStepDetector = false
    private var instantSteps = 0

    // Cadence calculation
    private var stepsInLastMinute = mutableListOf<Long>()    // Timestamps tiap step

    // Periodic save
    private var saveJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Last save data — untuk delta calculation
    private var lastSavedTotalSteps = 0
    private var lastSaveTimestamp = 0L

    // =========================================================================
    // LIFECYCLE
    // =========================================================================

    /**
     * Mulai tracking steps untuk session tertentu.
     */
    fun startTracking(sessionId: String, weightKg: Float = 65f) {
        if (isTracking) {
            Log.w(TAG, "Already tracking, ignoring start")
            return
        }

        currentSessionId = sessionId
        userWeightKg = weightKg
        isTracking = true

        // Reset state
        _totalSteps.value = 0
        _caloriesBurned.value = 0.0
        _cadence.value = 0
        sensorBaseline = null
        lastSavedTotalSteps = 0
        lastSaveTimestamp = System.currentTimeMillis()
        stepsInLastMinute.clear()

        // Load baseline dari DB (jika ada session sebelumnya yang crash)
        scope.launch {
            val savedBaseline = baselineDao.getBaseline()
            if (savedBaseline != null) {
                accumulatedStepsBeforeReboot = savedBaseline.accumulatedSteps
                lastKnownSensorValue = savedBaseline.lastKnownSensorValue
            }
        }

        // Register step detector (Instant)
        val stepDetector = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        if (stepDetector != null) {
            sensorManager.registerListener(this, stepDetector, SensorManager.SENSOR_DELAY_FASTEST)
            isUsingStepDetector = true
            Log.i(TAG, "Step detector sensor registered for instant updates")
        }

        // Register step counter sensor (Batch / cumulative)
        val stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (stepSensor != null) {
            sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_FASTEST)
            Log.i(TAG, "Step counter sensor registered")
        } else if (stepDetector == null) {
            Log.e(TAG, "No step sensors available on this device!")
        }

        // Start periodic save ke Room
        startPeriodicSave()
    }

    /**
     * Stop tracking — unregister sensor dan save final data.
     */
    fun stopTracking() {
        if (!isTracking) return

        isTracking = false
        sensorManager.unregisterListener(this)
        saveJob?.cancel()

        // Save final step log
        scope.launch {
            saveStepLog()
            baselineDao.clear()
        }

        Log.i(TAG, "Step counter stopped. Total steps: ${_totalSteps.value}")
    }

    /**
     * Pause tracking — unregister sensor tapi simpan state.
     */
    fun pauseTracking() {
        if (!isTracking) return
        sensorManager.unregisterListener(this)
        saveJob?.cancel()
        Log.d(TAG, "Step counter paused")
    }

    /**
     * Resume tracking — re-register sensor.
     */
    fun resumeTracking() {
        if (!isTracking) return

        val stepDetector = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        if (stepDetector != null) {
            sensorManager.registerListener(this, stepDetector, SensorManager.SENSOR_DELAY_FASTEST)
        }

        val stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (stepSensor != null) {
            sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_FASTEST)
        }
        startPeriodicSave()
        Log.d(TAG, "Step counter resumed")
    }

    // =========================================================================
    // SENSOR EVENT
    // =========================================================================

    override fun onSensorChanged(event: SensorEvent?) {
        if (!isTracking || event == null) return

        if (event.sensor.type == Sensor.TYPE_STEP_DETECTOR) {
            // STEP DETECTOR: 1 event = 1 langkah (Instant, minim delay)
            if (event.values[0] == 1.0f) {
                instantSteps++
                updateStepMetrics(instantSteps)
            }
        } else if (event.sensor.type == Sensor.TYPE_STEP_COUNTER) {
            // STEP COUNTER: cumulative, bisa delay/batch
            val currentSensorValue = event.values[0].toInt()

            if (currentSensorValue < lastKnownSensorValue && lastKnownSensorValue > 0) {
                Log.w(TAG, "Device reboot detected! Sensor reset from $lastKnownSensorValue to $currentSensorValue")
                accumulatedStepsBeforeReboot += (lastKnownSensorValue - (sensorBaseline ?: lastKnownSensorValue))
                sensorBaseline = currentSensorValue
            }

            if (sensorBaseline == null) {
                sensorBaseline = currentSensorValue
                Log.d(TAG, "Step counter baseline set: $currentSensorValue")
            }
            lastKnownSensorValue = currentSensorValue

            // Jika step detector tidak jalan, kita pakai counter sebagai fallback
            if (!isUsingStepDetector) {
                val stepsSinceBaseline = currentSensorValue - (sensorBaseline ?: currentSensorValue)
                val totalStepsNow = accumulatedStepsBeforeReboot + stepsSinceBaseline
                updateStepMetrics(totalStepsNow)
            } else {
                // Kalibrasi periodik: Jika counter hardware lebih besar dari hitungan manual, sync.
                val hardwareSteps = accumulatedStepsBeforeReboot + (currentSensorValue - (sensorBaseline ?: currentSensorValue))
                if (hardwareSteps > instantSteps + 5) { // Toleransi 5 langkah
                    instantSteps = hardwareSteps
                    updateStepMetrics(instantSteps)
                }
            }

            // Persist baseline
            scope.launch {
                baselineDao.upsert(
                    StepCounterBaselineEntity(
                        sensorValueAtSessionStart = sensorBaseline ?: currentSensorValue,
                        lastKnownSensorValue = currentSensorValue,
                        accumulatedSteps = instantSteps.coerceAtLeast(accumulatedStepsBeforeReboot),
                    )
                )
            }
        }
    }

    private fun updateStepMetrics(totalStepsNow: Int) {
        if (totalStepsNow > _totalSteps.value) {
            _totalSteps.value = totalStepsNow
            _caloriesBurned.value = totalStepsNow * userWeightKg * CALORIE_FACTOR

            val now = System.currentTimeMillis()
            stepsInLastMinute.add(now)
            stepsInLastMinute.removeAll { it < now - 60_000 }
            _cadence.value = stepsInLastMinute.size
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        Log.d(TAG, "Step counter accuracy changed: $accuracy")
    }

    // =========================================================================
    // PERIODIC SAVE TO ROOM
    // =========================================================================

    private fun startPeriodicSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            while (isActive && isTracking) {
                delay(SAVE_INTERVAL_MS)
                saveStepLog()
            }
        }
    }

    /**
     * Simpan delta step log ke Room (bukan total, tapi selisih dari save terakhir).
     */
    private suspend fun saveStepLog() {
        val sessionId = currentSessionId ?: return
        val currentTotal = _totalSteps.value
        val deltaSteps = currentTotal - lastSavedTotalSteps

        if (deltaSteps <= 0) return  // Tidak ada langkah baru

        val now = System.currentTimeMillis()
        val deltaTimeSec = (now - lastSaveTimestamp) / 1000.0
        val intervalCadence = if (deltaTimeSec > 0) {
            (deltaSteps / deltaTimeSec * 60).toInt()
        } else {
            _cadence.value
        }

        val stepLog = StepLogEntity(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            stepCount = deltaSteps,
            cadence = intervalCadence,
            caloriesBurned = deltaSteps * userWeightKg * CALORIE_FACTOR,
            confidence = null,  // TYPE_STEP_COUNTER tidak report confidence
            accelerometerMagnitude = null,
            recordedAt = now,
        )

        try {
            stepLogDao.insert(stepLog)
            lastSavedTotalSteps = currentTotal
            lastSaveTimestamp = now
            Log.d(TAG, "Step log saved: +$deltaSteps steps, total=$currentTotal")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save step log", e)
        }
    }

    // =========================================================================
    // PUBLIC HELPERS
    // =========================================================================

    /**
     * Hitung kalori dengan MET formula yang lebih akurat.
     * Calories = MET × weight_kg × duration_hours
     */
    fun calculateCaloriesWithMET(
        activityType: ActivityType,
        durationSeconds: Long,
        weightKg: Float
    ): Double {
        val durationHours = durationSeconds / 3600.0
        return activityType.metValue * weightKg * durationHours
    }

    fun isStepCounterAvailable(): Boolean {
        return sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null
    }

    /** Update sessionId setelah DB berhasil membuat sesi nyata. */
    fun updateSessionId(newSessionId: String) {
        currentSessionId = newSessionId
    }

    fun destroy() {
        stopTracking()
        scope.cancel()
    }
}
