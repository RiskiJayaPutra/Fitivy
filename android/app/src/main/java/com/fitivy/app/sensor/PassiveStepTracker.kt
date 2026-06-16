package com.fitivy.app.sensor

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import com.fitivy.app.data.local.TokenManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PassiveStepTracker @Inject constructor(
    private val sensorManager: SensorManager,
    private val tokenManager: TokenManager // Gunakan SharedPreferences untuk simpan baseline
) : SensorEventListener {

    private val _passiveStepsToday = MutableStateFlow(0)
    val passiveStepsToday: StateFlow<Int> = _passiveStepsToday.asStateFlow()

    private var isListening = false
    private val scope = CoroutineScope(Dispatchers.IO)

    // Key untuk SharedPreferences
    private val PREF_KEY_BASELINE = "passive_step_baseline"
    private val PREF_KEY_LAST_KNOWN = "passive_step_last_known"
    private val PREF_KEY_DATE = "passive_step_date"
    private val PREF_KEY_ACCUMULATED = "passive_step_accumulated" // Jika HP mati/reboot di hari yang sama

    fun startListening() {
        if (isListening) return

        val stepCounter = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (stepCounter != null) {
            sensorManager.registerListener(this, stepCounter, SensorManager.SENSOR_DELAY_NORMAL)
            isListening = true
            Log.i("PassiveStepTracker", "Started listening to passive steps")
        }
    }

    fun stopListening() {
        if (!isListening) return
        sensorManager.unregisterListener(this)
        isListening = false
        Log.i("PassiveStepTracker", "Stopped listening to passive steps")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_STEP_COUNTER) return
        val currentHardwareSteps = event.values[0].toInt()

        scope.launch {
            processPassiveSteps(currentHardwareSteps)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun processPassiveSteps(currentHardwareSteps: Int) {
        val prefs = tokenManager.getSharedPreferences() // Butuh akses ke SharedPreferences
        
        val todayStart = getStartOfDay(System.currentTimeMillis())
        val savedDate = prefs.getLong(PREF_KEY_DATE, 0L)
        
        var baseline = prefs.getInt(PREF_KEY_BASELINE, -1)
        var lastKnown = prefs.getInt(PREF_KEY_LAST_KNOWN, -1)
        var accumulated = prefs.getInt(PREF_KEY_ACCUMULATED, 0)

        // Hari baru? Reset baseline!
        if (savedDate != todayStart) {
            baseline = currentHardwareSteps
            lastKnown = currentHardwareSteps
            accumulated = 0
            prefs.edit()
                .putLong(PREF_KEY_DATE, todayStart)
                .putInt(PREF_KEY_BASELINE, baseline)
                .putInt(PREF_KEY_LAST_KNOWN, lastKnown)
                .putInt(PREF_KEY_ACCUMULATED, accumulated)
                .apply()
        }

        // Deteksi Reboot (currentHardwareSteps < lastKnown)
        if (lastKnown > 0 && currentHardwareSteps < lastKnown) {
            Log.w("PassiveStepTracker", "Reboot detected in passive tracker")
            // HP direboot. Langkah hari ini sebelumnya harus disimpan ke accumulated.
            val stepsBeforeReboot = lastKnown - baseline
            accumulated += if (stepsBeforeReboot > 0) stepsBeforeReboot else 0
            
            // Baseline baru adalah setelah reboot
            baseline = currentHardwareSteps
            
            prefs.edit()
                .putInt(PREF_KEY_ACCUMULATED, accumulated)
                .putInt(PREF_KEY_BASELINE, baseline)
                .apply()
        }

        // Simpan last known agar bisa mendeteksi reboot selanjutnya
        prefs.edit().putInt(PREF_KEY_LAST_KNOWN, currentHardwareSteps).apply()

        // Hitung total hari ini
        val stepsSinceBaseline = currentHardwareSteps - baseline
        val totalToday = accumulated + stepsSinceBaseline
        
        if (totalToday >= 0) {
            _passiveStepsToday.value = totalToday
        }
    }

    private fun getStartOfDay(timestamp: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timestamp
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
