package com.fitivy.app.ui.tracker

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.fitivy.app.R
import com.fitivy.app.databinding.FragmentActiveTrackingBinding
import com.fitivy.app.service.ActivityDetectionService
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import dagger.hilt.android.AndroidEntryPoint
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

@AndroidEntryPoint
class ActiveTrackingFragment : Fragment() {

    private var _binding: FragmentActiveTrackingBinding? = null
    private val binding get() = _binding!!

    private var trackingService: ActivityDetectionService? = null
    private var isBound = false
    
    private var routeOverlay: Polyline? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as ActivityDetectionService.LocalBinder
            trackingService = binder.getService()
            isBound = true
            observeTrackingState()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            trackingService = null
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sharedPrefs = requireContext().getSharedPreferences("osmdroid", Context.MODE_PRIVATE)
        Configuration.getInstance().load(requireContext(), sharedPrefs)
        Configuration.getInstance().userAgentValue = requireContext().packageName
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentActiveTrackingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupMap()
        setupListeners()
    }

    override fun onStart() {
        super.onStart()
        val intent = Intent(requireContext(), ActivityDetectionService::class.java)
        requireContext().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            requireContext().unbindService(serviceConnection)
            isBound = false
            trackingService = null
        }
    }

    private fun setupMap() {
        binding.mapViewTracking.setTileSource(TileSourceFactory.MAPNIK)
        binding.mapViewTracking.zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        binding.mapViewTracking.setMultiTouchControls(true)
        
        // 1. Setup Raw GPS Tracker (Blue Dot) - Instan memusatkan peta
        val locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(requireContext()), binding.mapViewTracking)
        locationOverlay.enableMyLocation()
        locationOverlay.enableFollowLocation()
        
        locationOverlay.runOnFirstFix {
            requireActivity().runOnUiThread {
                // Menghilangkan loading seketika setelah sinyal raw OS muncul (hitungan milidetik)
                binding.pbMapLoadingTracking.visibility = View.GONE
                binding.mapViewTracking.controller.animateTo(locationOverlay.myLocation)
                binding.mapViewTracking.controller.setZoom(18.0)
            }
        }
        binding.mapViewTracking.overlays.add(locationOverlay)

        // 2. Setup Route Overlay (Garis Orange Terfilter ala Strava) - Muncul asinkron dari Service
        routeOverlay = Polyline()
        routeOverlay?.outlinePaint?.color = requireContext().getColor(R.color.strava_orange)
        routeOverlay?.outlinePaint?.strokeWidth = 15f
        routeOverlay?.outlinePaint?.strokeCap = android.graphics.Paint.Cap.ROUND
        routeOverlay?.outlinePaint?.strokeJoin = android.graphics.Paint.Join.ROUND
        binding.mapViewTracking.overlays.add(routeOverlay)
    }

    private fun setupListeners() {
        binding.btnPauseResume.setOnClickListener {
            if (trackingService?.isCurrentlyPaused() == true) {
                val intent = Intent(requireContext(), ActivityDetectionService::class.java).apply {
                    action = ActivityDetectionService.ACTION_RESUME
                }
                requireContext().startService(intent)
            } else {
                val intent = Intent(requireContext(), ActivityDetectionService::class.java).apply {
                    action = ActivityDetectionService.ACTION_PAUSE
                }
                requireContext().startService(intent)
            }
        }

        binding.btnStop.setOnClickListener {
            val intent = Intent(requireContext(), ActivityDetectionService::class.java).apply {
                action = ActivityDetectionService.ACTION_STOP
            }
            requireContext().startService(intent)
            findNavController().navigateUp()
        }
    }

    private fun observeTrackingState() {
        val service = trackingService ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            service.trackingState.collect { state ->
                if (_binding == null) return@collect

                // Update texts
                binding.tvSteps.text = state.totalSteps.toString()
                
                if (state.totalDistance < 1000.0) {
                    binding.tvDistance.text = String.format("%.0f", state.totalDistance)
                    binding.tvDistanceUnitTracker.text = "Meter"
                } else {
                    binding.tvDistance.text = String.format("%.2f", state.totalDistance / 1000.0)
                    binding.tvDistanceUnitTracker.text = "Kilometer"
                }
                
                binding.tvCalories.text = state.totalCalories.toInt().toString()

                val hours = state.durationSeconds / 3600
                val minutes = (state.durationSeconds % 3600) / 60
                val seconds = state.durationSeconds % 60
                binding.tvDuration.text = String.format("%02d:%02d:%02d", hours, minutes, seconds)

                binding.tvActivityType.text = state.currentActivity.displayName.uppercase()

                if (state.isPaused) {
                    binding.btnPauseResume.text = "LANJUTKAN"
                    binding.btnPauseResume.setIconResource(android.R.drawable.ic_media_play)
                } else {
                    binding.btnPauseResume.text = "JEDA"
                    binding.btnPauseResume.setIconResource(android.R.drawable.ic_media_pause)
                }

                // Update Map Route (Garis Merah Terfilter Kalman)
                if (state.gpsPoints.isNotEmpty()) {
                    val geoPoints = state.gpsPoints.map { GeoPoint(it.latitude, it.longitude) }
                    routeOverlay?.setPoints(geoPoints)
                    binding.mapViewTracking.invalidate()
                    
                    // Opsional: Kamera juga bisa terus mengikuti ujung garis filter
                    // val latest = geoPoints.last()
                    // binding.mapViewTracking.controller.setCenter(latest)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        binding.mapViewTracking.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.mapViewTracking.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
