package com.fitivy.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.fitivy.app.R
import com.fitivy.app.sensor.ActivityClassifier
import com.fitivy.app.sensor.LocationTracker
import com.fitivy.app.sensor.dsp.ButterworthLowPassFilter
import com.fitivy.app.sensor.dsp.GravityRemovalFilter
import com.fitivy.app.sensor.dsp.ProcessedSample
import com.fitivy.app.sensor.dsp.SlidingWindowProcessor
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.fitivy.app.sensor.model.TrackingState

/**
 * ActivityDetectionService — Entry point sentral untuk sistem pelacakan (DSP & GPS).
 *
 * ORKESTRASI PIPELINE:
 * 1. Menerima data sensor Accelerometer di onSensorChanged (Sensor Thread / Main Thread).
 * 2. Melewatkan data ke ButterworthLowPassFilter.
 * 3. Melewatkan data ke GravityRemovalFilter.
 * 4. Mensubmit sample ke SlidingWindowProcessor (Zero-GC Channel).
 * 5. Coroutine mengumpulkan WindowFeatures, diteruskan ke ActivityClassifier.
 * 6. ActivityType yang dihasilkan diteruskan ke LocationTracker untuk merubah mode GPS
 *    (Adaptive Interval & Kalman Q-Matrix).
 */
@AndroidEntryPoint
class ActivityDetectionService : Service(), SensorEventListener {

    @Inject
    lateinit var locationTracker: LocationTracker

    private lateinit var sensorManager: SensorManager
    private var accelSensor: Sensor? = null
    private var stepSensor: Sensor? = null
    private var userWeightKg: Double = 70.0

    // Komponen DSP (Fase 1)
    private val butterworthFilter = ButterworthLowPassFilter()
    private val gravityFilter = GravityRemovalFilter()
    private val windowProcessor = SlidingWindowProcessor()

    // Scope khusus untuk background processing (Channel consumer)
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var featureCollectionJob: Job? = null

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "fitivy_tracking_channel"
        
        const val ACTION_START = "START_TRACKING"
        const val ACTION_RESUME = "RESUME_TRACKING"
        const val ACTION_PAUSE = "PAUSE_TRACKING"
        const val ACTION_STOP = "STOP_TRACKING"
        const val EXTRA_WEIGHT_KG = "EXTRA_WEIGHT_KG"
        const val EXTRA_ACTIVITY_TYPE = "EXTRA_ACTIVITY_TYPE"
    }

    private val binder = LocalBinder()
    private val _trackingState = MutableStateFlow(TrackingState())
    val trackingState: StateFlow<TrackingState> = _trackingState.asStateFlow()
    private var durationJob: Job? = null

    inner class LocalBinder : android.os.Binder() {
        fun getService(): ActivityDetectionService = this@ActivityDetectionService
    }

    fun isCurrentlyPaused(): Boolean = _trackingState.value.isPaused

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        createNotificationChannel()
        observeLocationTracker()
    }

    private fun observeLocationTracker() {
        serviceScope.launch {
            locationTracker.totalDistance.collect { distance ->
                _trackingState.value = _trackingState.value.copy(totalDistance = distance)
            }
        }
        serviceScope.launch {
            locationTracker.routePoints.collect { points ->
                _trackingState.value = _trackingState.value.copy(gpsPoints = points)
            }
        }
        serviceScope.launch {
            locationTracker.currentSpeed.collect { speed ->
                _trackingState.value = _trackingState.value.copy(currentSpeed = speed)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        userWeightKg = intent?.getDoubleExtra(EXTRA_WEIGHT_KG, 70.0) ?: 70.0
        when (intent?.action) {
            ACTION_START -> startTrackingSession(false)
            ACTION_RESUME -> startTrackingSession(true)
            ACTION_PAUSE -> pauseTrackingSession()
            ACTION_STOP -> stopTrackingSession()
        }
        return START_STICKY
    }

    private fun startTrackingSession(isResume: Boolean = false) {
        // 1. Jalankan Foreground Service
        startForeground(NOTIFICATION_ID, createNotification())

        if (!isResume) {
            // 2. Reset semua filter untuk menghindari "stale state" dari sesi sebelumnya
            butterworthFilter.reset()
            gravityFilter.reset()
            windowProcessor.reset()
            ActivityClassifier.setWarmup(3)
        }

        // 3. Start Window Processor Coroutine (Consumer)
        // Harus dipanggil SEBELUM mendaftar sensor, agar channel siap menampung
        windowProcessor.startProcessing(serviceScope)

        // 4. Start Feature Classification Coroutine
        startFeatureClassification()

        // 5. Start Location Tracker (GPS + Kalman)
        if (!isResume) {
            val sessionId = "SESSION_${System.currentTimeMillis()}"
            locationTracker.startTracking(sessionId)
        }
        locationTracker.updateActivityType(ActivityClassifier.getCurrentActivity())

        _trackingState.value = _trackingState.value.copy(
            isTracking = true,
            isPaused = false
        )
        startDurationTimer()

        // 6. Register Sensor (Producer)
        accelSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        stepSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST)
        }
    }

    private fun pauseTrackingSession() {
        // Hentikan sensor dan processor sementara, tapi biarkan service hidup
        sensorManager.unregisterListener(this)
        windowProcessor.stop()
        featureCollectionJob?.cancel()
        durationJob?.cancel()
        locationTracker.stopTracking()
        
        _trackingState.value = _trackingState.value.copy(isPaused = true)

        
        // Reset filter internal untuk mengantisipasi gerakan random selama pause
        butterworthFilter.reset()
        gravityFilter.reset()
    }

    private fun stopTrackingSession() {
        sensorManager.unregisterListener(this)
        windowProcessor.stop()
        featureCollectionJob?.cancel()
        locationTracker.stopTracking()
        stopForeground(true)
        stopSelf()
    }

    private fun startFeatureClassification() {
        featureCollectionJob?.cancel()
        featureCollectionJob = serviceScope.launch {
            // Collect flow dari window features (Aman dari replay leak karena Opsi A telah diimplementasi)
            windowProcessor.windowFeatures.collect { features ->
                // Klasifikasikan fitur menggunakan ActivityClassifier (Thread-safe)
                val detectedActivity = ActivityClassifier.classifyFromFeatures(features)
                
                // Teruskan state ke GPS Tracker untuk Adaptive Interval & Adaptive Q-Matrix
                locationTracker.updateActivityType(detectedActivity)
                
                _trackingState.value = _trackingState.value.copy(
                    currentActivity = detectedActivity
                )
            }
        }
    }

    private fun startDurationTimer() {
        durationJob?.cancel()
        durationJob = serviceScope.launch {
            while (true) {
                delay(1000)
                val currentState = _trackingState.value
                val met = currentState.currentActivity.metValue
                // Kalori_Per_Detik = (MET * Weight_KG) / 3600
                val caloriesPerSecond = (met * userWeightKg) / 3600.0

                _trackingState.value = currentState.copy(
                    durationSeconds = currentState.durationSeconds + 1,
                    totalCalories = currentState.totalCalories + caloriesPerSecond
                )
            }
        }
    }

    // =========================================================================
    // SENSOR EVENT CALLBACK (Producer)
    // =========================================================================

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        if (event.sensor.type == Sensor.TYPE_STEP_DETECTOR) {
            _trackingState.value = _trackingState.value.copy(
                totalSteps = _trackingState.value.totalSteps + 1
            )
            locationTracker.addDistanceFromStep()
            return
        }

        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        // 1. Butterworth Low Pass Filter (Isolasi Noise High Freq)
        val filteredValues = butterworthFilter.filter(event.values)

        // 2. Gravity Removal (Complementary Filter)
        val linearAccel = gravityFilter.filter(filteredValues)

        // 3. Submit ke Channel (Non-blocking trySend ke Coroutine)
        val processedSample = ProcessedSample(
            linearX = linearAccel[0],
            linearY = linearAccel[1],
            linearZ = linearAccel[2],
            timestampNs = event.timestamp
        )
        windowProcessor.submitSample(processedSample)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Tidak perlu di-handle untuk akselerometer pada konteks tracking ini
    }

    // =========================================================================
    // NOTIFICATION BOILERPLATE
    // =========================================================================

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Fitivy Tracking Aktif")
            .setContentText("Memantau rute dan aktivitas fisik Anda...")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Fitivy Tracking",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopTrackingSession()
        serviceScope.cancel() // Cegah memory leak
    }

    override fun onBind(intent: Intent?): IBinder = binder
}
