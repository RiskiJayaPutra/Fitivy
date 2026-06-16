package com.fitivy.app.ui.tracker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.fitivy.app.R
import com.fitivy.app.databinding.FragmentTrackerBinding
import com.fitivy.app.service.ActivityDetectionService
import dagger.hilt.android.AndroidEntryPoint
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

@AndroidEntryPoint
class TrackerFragment : Fragment() {

    private var _binding: FragmentTrackerBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: TrackerViewModel by viewModels()

    private val activityRecognitionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            checkNotificationPermissionAndStart()
        } else {
            Toast.makeText(requireContext(), "Permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        doStartTracking()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().userAgentValue = requireContext().packageName
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTrackerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupListeners()
        setupObservers()
        setupMap()
        
        binding.chipWalking.isChecked = true
    }

    private fun setupMap() {
        binding.mapView.setTileSource(TileSourceFactory.MAPNIK)
        binding.mapView.zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        binding.mapView.setMultiTouchControls(true)
        
        val mapController = binding.mapView.controller
        mapController.setZoom(15.0)
        mapController.setCenter(GeoPoint(-6.2088, 106.8456))

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            enableMyLocation()
        } else {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1001)
        }
    }

    private fun enableMyLocation() {
        val locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(requireContext()), binding.mapView)
        locationOverlay.enableMyLocation()
        locationOverlay.enableFollowLocation()
        binding.mapView.overlays.add(locationOverlay)
    }

    private fun setupListeners() {
        binding.chipWalking.setOnClickListener { viewModel.setActivityType("walking") }
        binding.chipRunning.setOnClickListener { viewModel.setActivityType("running") }
        binding.chipCycling.setOnClickListener { viewModel.setActivityType("cycling") }
        binding.chipHiking.setOnClickListener { viewModel.setActivityType("hiking") }

        binding.targetSlider.addOnChangeListener { _, value, _ ->
            viewModel.setTargetValue(value)
        }

        binding.btnTargetDistance.setOnClickListener { viewModel.setTargetType("distance") }
        binding.btnTargetTime.setOnClickListener { viewModel.setTargetType("time") }
        binding.btnTargetCalories.setOnClickListener { viewModel.setTargetType("calories") }

        binding.btnStart.setOnClickListener {
            checkAndStartTracking()
        }
    }

    private fun setupObservers() {
        viewModel.targetValue.observe(viewLifecycleOwner) { value ->
            val unit = when(viewModel.targetType.value) {
                "time" -> " min"
                "calories" -> " kcal"
                else -> " km"
            }
            binding.tvTargetValue.text = String.format("%.1f%s", value, unit)
        }
        
        viewModel.targetType.observe(viewLifecycleOwner) { type ->
            // Update slider range and text based on type
            when(type) {
                "time" -> {
                    binding.targetSlider.valueFrom = 5f
                    binding.targetSlider.valueTo = 120f
                    binding.targetSlider.value = 30f
                }
                "calories" -> {
                    binding.targetSlider.valueFrom = 50f
                    binding.targetSlider.valueTo = 1000f
                    binding.targetSlider.value = 200f
                }
                else -> {
                    binding.targetSlider.valueFrom = 1f
                    binding.targetSlider.valueTo = 20f
                    binding.targetSlider.value = 5f
                }
            }
        }
    }

    private fun checkAndStartTracking() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
                activityRecognitionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                return
            }
        }
        checkNotificationPermissionAndStart()
    }

    private fun checkNotificationPermissionAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        doStartTracking()
    }

    private fun doStartTracking() {
        val intent = android.content.Intent(requireContext(), ActivityDetectionService::class.java).apply {
            action = ActivityDetectionService.ACTION_START
            putExtra(ActivityDetectionService.EXTRA_ACTIVITY_TYPE, viewModel.selectedActivity.value)
        }
        ContextCompat.startForegroundService(requireContext(), intent)
        findNavController().navigate(R.id.action_trackerFragment_to_activeTrackingFragment)
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
