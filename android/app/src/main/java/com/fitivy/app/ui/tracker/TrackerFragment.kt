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

    // Permission launcher untuk ACTIVITY_RECOGNITION
    private val activityRecognitionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            checkNotificationPermissionAndStart()
        } else {
            Toast.makeText(
                requireContext(),
                "Izin Activity Recognition diperlukan agar langkah kaki bisa dihitung",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // Permission launcher untuk POST_NOTIFICATIONS (Android 13+)
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Regardless of result, start tracking (notification is nice-to-have)
        doStartTracking()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // OsmDroid Configuration MUST be done before layout inflation!
        val sharedPrefs = requireContext().getSharedPreferences("osmdroid", android.content.Context.MODE_PRIVATE)
        Configuration.getInstance().load(requireContext(), sharedPrefs)
        Configuration.getInstance().userAgentValue = requireContext().packageName
        
        val basePath = java.io.File(requireContext().cacheDir.absolutePath, "osmdroid")
        basePath.mkdirs()
        Configuration.getInstance().osmdroidBasePath = basePath
        Configuration.getInstance().osmdroidTileCache = basePath
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
        
        // Default selections
        binding.chipWalking.isChecked = true
        binding.chipTargetBebas.isChecked = true
    }

    private fun setupMap() {
        binding.mapView.setTileSource(TileSourceFactory.MAPNIK)
        binding.mapView.zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        binding.mapView.setMultiTouchControls(true)
        
        val mapController = binding.mapView.controller
        mapController.setZoom(15.0)
        
        // Center to Jakarta by default
        val startPoint = GeoPoint(-6.2088, 106.8456)
        mapController.setCenter(startPoint)

        checkLocationPermissionAndEnableLocation()
    }

    private fun checkLocationPermissionAndEnableLocation() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            enableMyLocation()
        } else {
            binding.pbMapLoading.visibility = View.GONE
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1001)
        }
    }

    private fun enableMyLocation() {
        val locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(requireContext()), binding.mapView)
        locationOverlay.enableMyLocation()
        locationOverlay.enableFollowLocation()
        
        locationOverlay.runOnFirstFix {
            requireActivity().runOnUiThread {
                binding.pbMapLoading.visibility = View.GONE
                binding.mapView.controller.animateTo(locationOverlay.myLocation)
                binding.mapView.controller.setZoom(17.0)
            }
        }
        
        binding.mapView.overlays.add(locationOverlay)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == 1001 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            enableMyLocation()
        }
    }

    private fun setupListeners() {
        binding.chipWalking.setOnClickListener { viewModel.setActivityType("walking") }
        binding.chipRunning.setOnClickListener { viewModel.setActivityType("running") }
        binding.chipCycling.setOnClickListener { viewModel.setActivityType("cycling") }

        binding.chipTargetBebas.setOnClickListener { viewModel.setTarget("Bebas") }
        binding.chipTarget3Km.setOnClickListener { viewModel.setTarget("Jarak 3 Km") }
        binding.chipTarget5Km.setOnClickListener { viewModel.setTarget("Jarak 5 Km") }
        binding.chipTarget30Min.setOnClickListener { viewModel.setTarget("Waktu 30 Menit") }

        binding.btnStart.setOnClickListener {
            checkAndStartTracking()
        }
    }

    private fun checkAndStartTracking() {
        // Cek ACTIVITY_RECOGNITION (wajib di Android 10+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(), Manifest.permission.ACTIVITY_RECOGNITION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                activityRecognitionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                return
            }
        }
        checkNotificationPermissionAndStart()
    }

    private fun checkNotificationPermissionAndStart() {
        // Cek POST_NOTIFICATIONS (wajib di Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(), Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        doStartTracking()
    }

    private fun doStartTracking() {
        val intent = android.content.Intent(requireContext(), ActivityDetectionService::class.java).apply {
            action = ActivityDetectionService.ACTION_START
            putExtra(ActivityDetectionService.EXTRA_WEIGHT_KG, 65f)
        }
        androidx.core.content.ContextCompat.startForegroundService(requireContext(), intent)
        findNavController().navigate(R.id.action_trackerFragment_to_activeTrackingFragment)
    }

    private fun setupObservers() {
        viewModel.selectedActivity.observe(viewLifecycleOwner) { type ->
            val icon = when (type) {
                "running" -> R.drawable.ic_run
                "cycling" -> R.drawable.ic_bike
                else -> R.drawable.ic_walk
            }
            binding.btnStart.setIconResource(icon)
        }
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
