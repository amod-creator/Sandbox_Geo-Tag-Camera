package com.amod.geotagcamera
import android.Manifest
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.hardware.display.DisplayManager
import android.location.Geocoder
import android.location.Location
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.*
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.OrientationEventListener
import android.view.Surface
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.*
import android.widget.VideoView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.lifecycleScope
import com.amod.geotagcamera.utils.GpsOverlayRenderer
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class MainActivity : AppCompatActivity() {
    private lateinit var previewView: PreviewView
    private lateinit var captureButton: ImageButton
    private lateinit var imageCapture: ImageCapture
    private lateinit var videoCapture: androidx.camera.video.VideoCapture<androidx.camera.video.Recorder>
    private var recording: androidx.camera.video.Recording? = null
    private var isRecording = false
    private var currentLocation: Location? = null
    private var address: String = ""
    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>
    private var isBackCamera = true
    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            updateLiveOverlay()
            handler.postDelayed(this, 3000)
        }
    }

    private lateinit var orientationEventListener: OrientationEventListener

    private val capturedPhotos = mutableListOf<Pair<Bitmap, String>>() // Stores bitmap and filename
    private val MAX_PHOTOS = 6
    private enum class Screen { LIVE, PURVIEW, FORM, PDF }
    private var currentScreen = Screen.LIVE
    private var lastCapturedBitmap: Bitmap? = null
    private var lastCapturedFileName: String? = null

    private lateinit var gpsOverlayRenderer: GpsOverlayRenderer
    private var lastOverlayMapThumbnail: Bitmap? = null
    private var lastOverlayLatText: String = ""
    private var lastOverlayLonText: String = ""
    private var lastOverlayAddrText: String = ""
    private var lastOverlayDateTime: String = ""
    private lateinit var displayManager: DisplayManager
    private var displayRotation = Surface.ROTATION_0

    // Zoom control
    private var cameraControl: androidx.camera.core.CameraControl? = null
    private var cameraInfo: androidx.camera.core.CameraInfo? = null
    private var isZoom2x = false

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {}
        override fun onDisplayRemoved(displayId: Int) {}
        override fun onDisplayChanged(displayId: Int) {
            val newRotation = getDisplayRotation()
            if (newRotation != displayRotation) {
                displayRotation = newRotation
                Log.d("DisplayListener", "Rotation changed to $displayRotation")
                adjustGpsOverlayPosition(displayRotation)
            }
        }
    }

    /**
     * Hide system UI (status bar, navigation bar) for an immersive experience.
     */
    private fun hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            or View.SYSTEM_UI_FLAG_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    )
        }
    }

    companion object {
        private var isIntroShown = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val orientation = resources.configuration.orientation
        val orientationStr = if (orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) "LANDSCAPE" else "PORTRAIT"
        Log.d("MainActivity", "onCreate called - Orientation: $orientationStr, isIntroShown: $isIntroShown")

        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        permissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
                if (perms.all { it.value }) {
                    if (::previewView.isInitialized.not()) {
                        getLocation()
                        startCamera()
                        handler.post(updateRunnable)
                    }
                } else {
                    Toast.makeText(this, "Permissions denied", Toast.LENGTH_SHORT).show()
                    openAppSettings()
                }
            }

        if (!isIntroShown) {
            // Play intro video first
            Log.d("MainActivity", "Showing intro video")
            setContentView(R.layout.activity_intro)
            hideSystemUI()
            val videoView = findViewById<VideoView>(R.id.introVideoView)
            val introUri = Uri.parse("android.resource://$packageName/${R.raw.intro}")
            videoView.setVideoURI(introUri)
            videoView.setOnCompletionListener {
                isIntroShown = true
                Log.d("MainActivity", "Intro completed, calling initCameraAndListeners")
                initCameraAndListeners()
            }
            // Handle Skip intro
            val skipButton = findViewById<Button>(R.id.skipButton)
            skipButton.setOnClickListener {
                videoView.stopPlayback()
                isIntroShown = true
                Log.d("MainActivity", "Intro skipped, calling initCameraAndListeners")
                initCameraAndListeners()
            }
            videoView.start()
        } else {
            // After intro, let Android load the correct layout based on orientation
            Log.d("MainActivity", "Intro already shown, setting main layout for orientation: $orientationStr")
            setContentView(R.layout.activity_main)
            Log.d("MainActivity", "Content view set, calling initCameraAndListeners")
            initCameraAndListeners()
        }

        gpsOverlayRenderer = GpsOverlayRenderer(this)
        displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        setupOrientationListener()

        // Add back press handling
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when (currentScreen) {
                    Screen.LIVE -> {
                        // If on live camera, close the app
                        finish()
                    }
                    Screen.PURVIEW -> {
                        // Go back to live camera
                        currentScreen = Screen.LIVE
                    }
                    Screen.FORM -> {
                        // Go back to image preview dialog
                        currentScreen = Screen.PURVIEW
                        lastCapturedBitmap?.let { bmp ->
                            lastCapturedFileName?.let { name ->
                                showImagePurview(bmp, name)
                            }
                        } ?: initCameraAndListeners()
                    }
                    Screen.PDF -> {
                        // Go back to staff input form
                        currentScreen = Screen.FORM
                        launchStaffForm()
                    }
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        Log.d("MainActivity", "onResume called")
        displayManager.registerDisplayListener(displayListener, handler)
        if (orientationEventListener.canDetectOrientation()) {
            orientationEventListener.enable()
        }
        // Adjust overlay with current rotation if views are ready
        try {
            if (::previewView.isInitialized && previewView.parent != null) {
                val rot = previewView.display?.rotation ?: Surface.ROTATION_0
                adjustGpsOverlayPosition(rot)
            }
        } catch (_: Exception) { }
    }

    override fun onPause() {
        super.onPause()
        Log.d("MainActivity", "onPause called")
        displayManager.unregisterDisplayListener(displayListener)
        orientationEventListener.disable()
    }

    override fun onDestroy() {
        super.onDestroy()
        val orientation = resources.configuration.orientation
        val orientationStr = if (orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) "LANDSCAPE" else "PORTRAIT"
        Log.d("MainActivity", "onDestroy called - Orientation: $orientationStr")
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        val orientationStr = if (newConfig.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) "LANDSCAPE" else "PORTRAIT"
        Log.d("MainActivity", "onConfigurationChanged called - New Orientation: $orientationStr")
        Toast.makeText(this, "Orientation: $orientationStr", Toast.LENGTH_SHORT).show()
        val viewFinder = findViewById<PreviewView>(R.id.viewFinder)
        if (viewFinder != null) {
            Log.d("MainActivity", "Camera screen detected, reloading layout for orientation: $orientationStr")

            // Stop the camera and location updates temporarily
            handler.removeCallbacks(updateRunnable)

            try {
                // Force layout reload by getting layout inflater and inflating the correct layout
                val inflater = layoutInflater
                val newView = inflater.inflate(R.layout.activity_main, null)
                setContentView(newView)

                Log.d("MainActivity", "Layout inflated for orientation: $orientationStr")

                // Re-initialize the camera and all views
                initCameraAndListeners()

                Log.d("MainActivity", "Layout reloaded successfully for orientation: $orientationStr")
            } catch (e: Exception) {
                Log.e("MainActivity", "Error reloading layout: ${e.message}")
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } else {
            Log.d("MainActivity", "Not on camera screen, skipping layout reload")
        }
    }

    private fun setupOrientationListener() {
        orientationEventListener = object : OrientationEventListener(this) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return

                // Correct mapping: 90 degrees is Landscape (Top points Left), 270 is Reverse Landscape
                // Let's stick to standard Android:
                // 0 = Portrait
                // 90 = Landscape (Top points Left) -> ROTATION_90
                // 180 = Reverse Portrait
                // 270 = Reverse Landscape (Top points Right) -> ROTATION_270

                // My previous code:
                // 45..134 -> ROTATION_270. (Mapped sensor 90 to display 270).
                // 225..314 -> ROTATION_90. (Mapped sensor 270 to display 90).

                // The fix is to swap them.
                val correctedRotation = when (orientation) {
                    in 45..134 -> Surface.ROTATION_90 // Sensor 90 (Right side down) -> Surface.ROTATION_90 (Landscape)
                    in 135..224 -> Surface.ROTATION_180
                    in 225..314 -> Surface.ROTATION_270 // Sensor 270 (Left side down) -> Surface.ROTATION_270 (Reverse Landscape)
                    else -> Surface.ROTATION_0
                }

                if (correctedRotation != displayRotation) {
                    displayRotation = correctedRotation
                    Log.d("OrientationListener", "Rotation changed to $displayRotation")
                    adjustGpsOverlayPosition(displayRotation)
                }
            }
        }
    }

    private fun getDisplayRotation(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display?.rotation ?: Surface.ROTATION_0
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.rotation
        }
    }

    private enum class OverlayAnchor { BOTTOM_RIGHT, BOTTOM_LEFT, TOP_RIGHT, TOP_LEFT, CENTER }
    private var currentAnchor = OverlayAnchor.BOTTOM_RIGHT

    private fun adjustGpsOverlayPosition(rotation: Int) {
        // Update ImageCapture rotation to match physical device rotation
        if (::imageCapture.isInitialized) {
            val cameraRotation = when (rotation) {
                Surface.ROTATION_90 -> Surface.ROTATION_270
                Surface.ROTATION_270 -> Surface.ROTATION_90
                else -> rotation
            }
            imageCapture.targetRotation = cameraRotation
        }

        val rootLayout = findViewById<ConstraintLayout>(R.id.rootContainer) ?: return
        val buttonLayout = findViewById<LinearLayout>(R.id.buttonLayout)

        if (rootLayout.width == 0 || rootLayout.height == 0 || (buttonLayout != null && buttonLayout.height == 0)) {
            rootLayout.post { adjustGpsOverlayPosition(rotation) }
            return
        }

        runOnUiThread {
            val overlayWrapper = findViewById<FrameLayout>(R.id.gpsOverlayWrapper) ?: return@runOnUiThread
            val overlayCard = findViewById<MaterialCardView>(R.id.gpsOverlayCard) ?: return@runOnUiThread
            val buttonLayout = findViewById<LinearLayout>(R.id.buttonLayout) ?: return@runOnUiThread
            val contentLayout = findViewById<LinearLayout>(R.id.gpsOverlayContent) ?: return@runOnUiThread
            val textContainer = findViewById<LinearLayout>(R.id.gpsOverlayTextContainer) ?: return@runOnUiThread

            // Ensure Horizontal Layout
            contentLayout.orientation = LinearLayout.HORIZONTAL
            
            // Update content layout params to match parent
            contentLayout.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )

            val isLandscape = rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270
            val density = resources.displayMetrics.density

            // Reset transforms
            overlayWrapper.scaleX = 1.0f
            overlayWrapper.scaleY = 1.0f
            overlayWrapper.rotation = 0f
            overlayWrapper.translationX = 0f
            overlayWrapper.translationY = 0f
            
            // Adjust Map Size
            findViewById<ImageView>(R.id.liveMapThumbnail)?.let { mv ->
                val mapHeight = if (isLandscape) (60 * density).toInt() else (80 * density).toInt()
                mv.minimumHeight = mapHeight
                val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    weight = 0.7f
                }
                mv.layoutParams = params
            }
            
            // Update text container params
            val updatedTextParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                weight = 1.3f
                marginStart = (8 * density).toInt()
            }
            textContainer.layoutParams = updatedTextParams
            textContainer.setPadding(
                (8 * density).toInt(),
                (4 * density).toInt(),
                (8 * density).toInt(),
                (4 * density).toInt()
            )

            if (rotation == Surface.ROTATION_0) {
                // PORTRAIT: Use XML constraints — let ConstraintLayout chain handle positioning
                // overlay → above zoomButtonContainer → above buttonLayout → bottom of parent
                val set = ConstraintSet()
                set.clone(rootLayout)
                set.clear(R.id.gpsOverlayWrapper, ConstraintSet.TOP)
                set.clear(R.id.gpsOverlayWrapper, ConstraintSet.BOTTOM)
                set.clear(R.id.gpsOverlayWrapper, ConstraintSet.START)
                set.clear(R.id.gpsOverlayWrapper, ConstraintSet.END)
                // Constrain overlay bottom → top of zoom container
                set.connect(R.id.gpsOverlayWrapper, ConstraintSet.BOTTOM, R.id.zoomButtonContainer, ConstraintSet.TOP, (8 * density).toInt())
                set.connect(R.id.gpsOverlayWrapper, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, 0)
                set.connect(R.id.gpsOverlayWrapper, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END, 0)
                set.constrainWidth(R.id.gpsOverlayWrapper, ConstraintSet.MATCH_CONSTRAINT)
                set.applyTo(rootLayout)
                
                // Card fills wrapper width
                overlayCard.layoutParams.width = FrameLayout.LayoutParams.MATCH_PARENT
                overlayCard.requestLayout()
                overlayWrapper.requestLayout()
            } else {
                // LANDSCAPE / ROTATED: Use manual centering + rotation + translation
                val rootW = rootLayout.width
                val rootH = rootLayout.height
                val buttonH = buttonLayout.height

                val (screenEdgeLength, rotAngle) = when (rotation) {
                    Surface.ROTATION_90 -> Pair(rootH, -90f)
                    Surface.ROTATION_270 -> Pair(rootH, 90f)
                    Surface.ROTATION_180 -> Pair(rootW, 180f)
                    else -> Pair(rootW, 0f)
                }

                val targetWidth = if (isLandscape) (screenEdgeLength * 0.65f).toInt() else screenEdgeLength
                overlayCard.layoutParams.width = targetWidth

                // Measure to get height for translation calculations
                overlayCard.measure(
                    View.MeasureSpec.makeMeasureSpec(targetWidth, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                )
                val barHeight = overlayCard.measuredHeight

                // Center Wrapper then translate
                val set = ConstraintSet()
                set.clone(rootLayout)
                set.clear(R.id.gpsOverlayWrapper, ConstraintSet.TOP)
                set.clear(R.id.gpsOverlayWrapper, ConstraintSet.BOTTOM)
                set.clear(R.id.gpsOverlayWrapper, ConstraintSet.START)
                set.clear(R.id.gpsOverlayWrapper, ConstraintSet.END)
                set.centerHorizontally(R.id.gpsOverlayWrapper, ConstraintSet.PARENT_ID)
                set.centerVertically(R.id.gpsOverlayWrapper, ConstraintSet.PARENT_ID)
                set.applyTo(rootLayout)

                overlayWrapper.rotation = rotAngle

                val tx = when (rotation) {
                    Surface.ROTATION_90 -> (rootW - barHeight) / 2f
                    Surface.ROTATION_270 -> -(rootW - barHeight) / 2f
                    else -> 0f
                }
                val ty = when (rotation) {
                    Surface.ROTATION_180 -> -(rootH - barHeight) / 2f
                    Surface.ROTATION_90 -> (24 * density)
                    Surface.ROTATION_270 -> -(24 * density)
                    else -> 0f
                }
                
                overlayWrapper.translationX = tx
                overlayWrapper.translationY = ty
                
                overlayCard.requestLayout()
                overlayWrapper.requestLayout()
            }
        }
    }


    private fun requestPermissions() {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.RECORD_AUDIO
            )
        )
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        intent.data = Uri.fromParts("package", packageName, null)
        startActivity(intent)
    }

    private fun startCamera() {
        // Ensure SurfaceView mode so overlays render correctly
        previewView.implementationMode = PreviewView.ImplementationMode.PERFORMANCE
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            imageCapture = ImageCapture.Builder().build()

            // Setup video capture
            val recorder = androidx.camera.video.Recorder.Builder()
                .setQualitySelector(androidx.camera.video.QualitySelector.from(androidx.camera.video.Quality.HIGHEST))
                .build()
            videoCapture = androidx.camera.video.VideoCapture.withOutput(recorder)

            val cameraSelector =
                if (isBackCamera) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
            try {
                cameraProvider.unbindAll()
                val camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture, videoCapture)
                // Save camera reference for zoom control
                cameraControl = camera.cameraControl
                cameraInfo = camera.cameraInfo
                // Apply current zoom state
                if (isZoom2x) {
                    cameraControl?.setZoomRatio(2.0f)
                } else {
                    cameraControl?.setZoomRatio(1.0f)
                }
                updateLiveOverlay()
            } catch (e: Exception) {
                Log.e("Camera", "Use case binding failed", e)
                Toast.makeText(this, "Camera initialization failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun getLocation() {
        // Check if GPS is enabled
        val locationManager = getSystemService(LOCATION_SERVICE) as android.location.LocationManager
        val isGpsEnabled = locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
        val isNetworkEnabled = locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)

        if (!isGpsEnabled && !isNetworkEnabled) {
            // GPS is disabled, prompt user to enable it
            AlertDialog.Builder(this)
                .setTitle("GPS Disabled")
                .setMessage("GPS is required for geo-tagging photos. Please enable location services.")
                .setPositiveButton("Enable") { _, _ ->
                    // Open location settings
                    val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                    startActivity(intent)
                }
                .setNegativeButton("Cancel") { dialog, _ ->
                    dialog.dismiss()
                    Toast.makeText(this, "GPS disabled - location data will not be available", Toast.LENGTH_LONG).show()
                }
                .setCancelable(false)
                .show()
            return
        }

        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            try {
                // Create location request
                val locationRequest = com.google.android.gms.location.LocationRequest.Builder(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    5000 // Update interval in milliseconds
                ).apply {
                    setMinUpdateDistanceMeters(5f)
                    setMaxUpdateDelayMillis(10000)
                }.build()

                // Request location updates
                fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    object : com.google.android.gms.location.LocationCallback() {
                        override fun onLocationResult(locationResult: com.google.android.gms.location.LocationResult) {
                            super.onLocationResult(locationResult)
                            locationResult.lastLocation?.let { location ->
                                Log.d("Location", "Got location: ${location.latitude}, ${location.longitude}")
                                currentLocation = location
                                fetchAddress(location)
                            }
                        }
                    },
                    Looper.getMainLooper()
                )

                // Also get last known location
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    if (location != null) {
                        Log.d("Location", "Last known location: ${location.latitude}, ${location.longitude}")
                        currentLocation = location
                        fetchAddress(location)
                    } else {
                        Log.d("Location", "Last known location is null")
                    }
                }.addOnFailureListener { e ->
                    Log.e("Location", "Error getting last location: ${e.message}")
                    Toast.makeText(
                        this,
                        "Unable to get location. Please check if GPS is enabled.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Log.e("Location", "Error in location setup: ${e.message}")
                Toast.makeText(
                    this,
                    "Location services error. Please check settings.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } else {
            Log.e("Location", "Location permission not granted")
            Toast.makeText(
                this,
                "Location permission required",
                Toast.LENGTH_SHORT
            ).show()
            requestPermissions()
        }
    }

    /**
     * Extract city, state, country from address string for the bold header.
     * Returns something like "Surat, Gujarat, India 🇮🇳"
     */
    private fun extractCityStateCountry(fullAddress: String): String {
        if (fullAddress.isBlank() || fullAddress == "Fetching address..." || fullAddress == "GPS Details not fetched") {
            return "Location Unavailable"
        }
        val parts = fullAddress.split(",").map { it.trim() }
        // Try to extract city, state, country from the end of the address
        return when {
            parts.size >= 3 -> {
                // Last part is usually country or pincode+country
                val country = parts.last().replace(Regex("\\d+"), "").trim()
                val state = parts[parts.size - 2].replace(Regex("\\d+"), "").trim()
                val city = parts[parts.size - 3].replace(Regex("\\d+"), "").trim()
                val header = listOf(city, state, country).filter { it.isNotBlank() }.joinToString(", ")
                if (country.equals("India", ignoreCase = true)) "$header \uD83C\uDDEE\uD83C\uDDF3" else header
            }
            parts.size == 2 -> "${parts[0]}, ${parts[1]}"
            else -> fullAddress
        }
    }

    private fun updateLiveOverlay() {
        // Bring the overlay card to front
        val card = findViewById<MaterialCardView>(R.id.gpsOverlayCard) ?: return
        card.bringToFront()

        // Align renderer layout with UI orientation
        val isLandscape = isLandscapeLayout()
        gpsOverlayRenderer.layoutMode = if (isLandscape) GpsOverlayRenderer.LayoutMode.VERTICAL else GpsOverlayRenderer.LayoutMode.HORIZONTAL

        // Locate views with null checks — new layout IDs
        val cityHeaderView = findViewById<TextView>(R.id.geo_city_header) ?: return
        val addrView       = findViewById<TextView>(R.id.geo_address) ?: return
        val latLonView     = findViewById<TextView>(R.id.geo_latlon) ?: return
        val dateTimeView   = findViewById<TextView>(R.id.geo_datetime) ?: return
        val mapImageView   = findViewById<ImageView>(R.id.liveMapThumbnail) ?: return

        // Prepare values
        val lat = currentLocation?.latitude
        val lon = currentLocation?.longitude

        // Bold city/state header with flag
        cityHeaderView.text = extractCityStateCountry(address)

        // Full address
        addrView.text = address

        // Combined Lat/Long in one line
        latLonView.text = if (lat != null && lon != null) {
            "Lat %.5f°   Long %.5f°".format(lat, lon)
        } else {
            "Lat --°   Long --°"
        }

        // Date/time in 12hr format
        dateTimeView.text = SimpleDateFormat("EEEE, dd/MM/yyyy hh:mm a", Locale.getDefault()).format(Date())

        // For backward compatibility with PDF/gallery overlay
        lastOverlayLatText = lat?.let { "Lat: %.5f".format(it) } ?: "Lat: --"
        lastOverlayLonText = lon?.let { "Lon: %.5f".format(it) } ?: "Lon: --"

        // Fetch and display static map thumbnail with error handling
        if (lat != null && lon != null) {
            val apiKey = try {
                BuildConfig.MAPS_API_KEY
            } catch (e: Exception) {
                Log.e("MapsAPI", "API key not found in BuildConfig: ${e.message}")
                "AIzaSyCfJ2d9XnWCbbi2hMBoQLma19Pr8fVNeaU" // Fallback
            }

            val url = "https://maps.googleapis.com/maps/api/staticmap" +
                    "?center=$lat,$lon" +
                    "&zoom=17&size=400x400&scale=2" +
                    "&markers=color:red%7C$lat,$lon" +
                    "&key=$apiKey"

            lifecycleScope.launch {
                val bmp = withContext(Dispatchers.IO) {
                    try {
                        val client = OkHttpClient.Builder()
                            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                            .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                            .build()
                        val request = Request.Builder().url(url).build()
                        val response = client.newCall(request).execute()
                        if (response.isSuccessful) {
                            response.body?.byteStream()?.let { BitmapFactory.decodeStream(it) }
                        } else {
                            Log.e("OverlayMap", "Map API error: ${response.code}")
                            null
                        }
                    } catch (e: java.net.UnknownHostException) {
                        Log.e("OverlayMap", "Network unavailable: ${e.message}")
                        null
                    } catch (e: java.net.SocketTimeoutException) {
                        Log.e("OverlayMap", "Connection timeout: ${e.message}")
                        null
                    } catch (e: Exception) {
                        Log.e("OverlayMap", "Map load error: ${e.message}")
                        null
                    }
                }
                bmp?.let {
                    try {
                        mapImageView.setImageBitmap(it)
                    } catch (e: Exception) {
                        Log.e("OverlayMap", "Error setting bitmap: ${e.message}")
                    }
                }
                // Save overlay state for later PDF/gallery use
                lastOverlayMapThumbnail = bmp
                lastOverlayAddrText = addrView.text.toString()
                lastOverlayDateTime = dateTimeView.text.toString()
            }
        }
    }

    private fun capturePhoto() {
        val fileName = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
        val file = File(cacheDir, "$fileName.jpg")
        val output = ImageCapture.OutputFileOptions.Builder(file).build()

        imageCapture.takePicture(
            output, ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                    val rawBitmap = BitmapFactory.decodeFile(file.absolutePath)
                    val exif = ExifInterface(file.absolutePath)
                    val orientation = exif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                    val rotation = when (orientation) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                        else -> 0f
                    }
                    val matrix = Matrix().apply { postRotate(rotation) }
                    var rotatedBitmap = Bitmap.createBitmap(
                        rawBitmap,
                        0,
                        0,
                        rawBitmap.width,
                        rawBitmap.height,
                        matrix,
                        true
                    )
                    // If using front camera, un-mirror the image
                    if (!isBackCamera) {
                        val flipMatrix = Matrix().apply { preScale(-1f, 1f) }
                        rotatedBitmap = Bitmap.createBitmap(
                            rotatedBitmap,
                            0,
                            0,
                            rotatedBitmap.width,
                            rotatedBitmap.height,
                            flipMatrix,
                            true
                        )
                    }

                    // Apply GPS overlay before saving
                    // Retrieve the live overlay views directly with null checks
                    val mapThumbView = findViewById<ImageView>(R.id.liveMapThumbnail)
                    val mapThumbnail = (mapThumbView?.drawable as? BitmapDrawable)?.bitmap
                    val latText = lastOverlayLatText.ifBlank { "Lat: --" }
                    val lonText = lastOverlayLonText.ifBlank { "Lon: --" }
                    val addrText = findViewById<TextView>(R.id.geo_address)?.text?.toString() ?: "Address unavailable"
                    val dateTimeText = findViewById<TextView>(R.id.geo_datetime)?.text?.toString() ?: SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault()).format(Date())

                    // Apply GPS overlay using on-screen values
                    val isLandscape = isLandscapeLayout()
                    gpsOverlayRenderer.layoutMode = if (isLandscape) GpsOverlayRenderer.LayoutMode.VERTICAL else GpsOverlayRenderer.LayoutMode.HORIZONTAL
                    val overlaidBitmap = gpsOverlayRenderer.drawGpsOverlay(
                        rotatedBitmap,
                        mapThumbnail,
                        latText,
                        lonText,
                        addrText,
                        dateTimeText
                    )

                    if (visitMode) {
                        // Full workflow: preview then save
                        runOnUiThread {
                            showImagePurview(overlaidBitmap, fileName)
                        }
                        saveImageToGallery(overlaidBitmap, fileName)
                    } else {
                        // Only save to gallery and return to live camera
                        saveImageToGallery(overlaidBitmap, fileName)
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, "Image saved (Visit mode off)", Toast.LENGTH_SHORT).show()
                            initCameraAndListeners()
                        }
                    }
                    // Clean up the temporary file
                    file.delete()
                }

                override fun onError(e: ImageCaptureException) {
                    runOnUiThread {
                        Toast.makeText(
                            this@MainActivity,
                            "Failed to capture image: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        )
    }


    private fun showImagePurview(bitmap: Bitmap, fileName: String) {
        currentScreen = Screen.PURVIEW
        lastCapturedBitmap = bitmap
        lastCapturedFileName = fileName

        val dialogView = layoutInflater.inflate(R.layout.dialog_image_preview, null)
        val imageView = dialogView.findViewById<ImageView>(R.id.previewImageView)
        val photoCounterText = dialogView.findViewById<TextView>(R.id.photoCounterText)
        val thumbnailContainer = dialogView.findViewById<LinearLayout>(R.id.thumbnailContainer)

        // Show current photo in preview
        imageView.setImageBitmap(bitmap)
        // Tag the imageView with current fileName for delete logic
        imageView.tag = fileName

        // Create dialog instance
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        // Handle delete button: remove this photo and return to camera
        dialogView.findViewById<ImageButton>(R.id.deleteImageButton)
            .setOnClickListener {
                // Remove from capturedPhotos if it’s already been added
                capturedPhotos.removeAll { it.second == fileName }
                dialog.dismiss()
                initCameraAndListeners()
            }

        // Set cancel listener after dialog is created
        dialog.setOnCancelListener {
            currentScreen = Screen.LIVE
            dialog.dismiss()
            initCameraAndListeners()
            startCamera()
        }

        // Update photo counter
        photoCounterText.text = "Photos: ${capturedPhotos.size + 1}/6"

        // Clear and rebuild thumbnail container with delete buttons
        thumbnailContainer.removeAllViews()
        val dp8 = (8 * resources.displayMetrics.density).toInt()

        // Helper to add a framed thumbnail
        fun addFramedThumbnail(bmp: Bitmap, name: String) {
            val frame = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(dp8, 0, dp8, 0) }
            }
            val sizePx = (100 * resources.displayMetrics.density).toInt()
            val thumbView = ImageView(this).apply {
                setImageBitmap(bmp)
                adjustViewBounds = true
                layoutParams = FrameLayout.LayoutParams(
                    sizePx,
                    sizePx
                )
                scaleType = ImageView.ScaleType.CENTER_CROP
                setOnClickListener { imageView.setImageBitmap(bmp) }
            }
            frame.addView(thumbView)

            val deleteBtn = ImageButton(this).apply {
                setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                setBackgroundResource(0)
                layoutParams = FrameLayout.LayoutParams(dp8 * 2, dp8 * 2).apply {
                    gravity = Gravity.END or Gravity.TOP
                }
                setOnClickListener {
                    // Remove the photo from the list
                    capturedPhotos.removeAll { it.second == name }
                    // Remove this thumbnail view
                    thumbnailContainer.removeView(frame)
                    // Update photo counter
                    photoCounterText.text = "Photos: ${capturedPhotos.size}/6"
                    // If the deleted image was currently displayed, switch preview to the last remaining
                    if (imageView.tag == name) {
                        if (capturedPhotos.isNotEmpty()) {
                            val (newBmp, newName) = capturedPhotos.last()
                            imageView.setImageBitmap(newBmp)
                            imageView.tag = newName
                        } else {
                            // No photos left: return to camera
                            dialog.dismiss()
                            initCameraAndListeners()
                        }
                    }
                }
            }
            frame.addView(deleteBtn)

            thumbnailContainer.addView(frame)
        }

        // Add current and previous thumbnails
        addFramedThumbnail(bitmap, fileName)
        capturedPhotos.asReversed().forEach { (prevBmp, prevName) ->
            addFramedThumbnail(prevBmp, prevName)
        }

        // Handle retake button - replaces current photo
        dialogView.findViewById<Button>(R.id.retakeButton).setOnClickListener {
            dialog.dismiss()
            initCameraAndListeners()
        }

        // Handle add photos button - stores current photo and allows more captures
        dialogView.findViewById<Button>(R.id.addPhotosButton).setOnClickListener {
            if (capturedPhotos.size < MAX_PHOTOS - 1) {  // -1 because we're about to add one
                capturedPhotos.add(Pair(bitmap, fileName))
                dialog.dismiss()
                initCameraAndListeners()
            } else {
                Toast.makeText(this, "Maximum ${MAX_PHOTOS} photos allowed", Toast.LENGTH_SHORT).show()
            }
        }

        // Handle prepare report button
        dialogView.findViewById<Button>(R.id.prepareReportButton).setOnClickListener {
            try {
                val cameraProvider = ProcessCameraProvider.getInstance(this).get()
                cameraProvider.unbindAll()
                handler.removeCallbacks(updateRunnable)
                dialog.dismiss()
                capturedPhotos.add(Pair(bitmap, fileName))
                // capturedPhotos.forEach { (photoBitmap, photoFileName) ->
                //     saveImageToGallery(photoBitmap, photoFileName)
                // }
                launchStaffForm()
            } catch (e: Exception) {
                Log.e("Camera", "Error cleaning up camera: ${e.message}")
                dialog.dismiss()
                launchStaffForm()
            }
        }

        dialog.show()
    }

    private fun createThumbnailView(bitmap: Bitmap): ImageView {
        return ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(150, 150).apply {
                marginEnd = 8
            }
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageBitmap(bitmap)
            background = getDrawable(android.R.drawable.gallery_thumb)
            setPadding(4, 4, 4, 4)
            isClickable = true
            isFocusable = true
        }
    }

    private fun saveImageToGallery(bitmap: Bitmap, fileName: String) {
        // The bitmap passed here already has the overlay from capturePhoto().
        // We will save it directly and then separately create the PDF.

        // 1. Save the bitmap to the gallery
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$fileName.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/GPS Cam Visit Pro")
        }

        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        uri?.let {
            contentResolver.openOutputStream(it)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
                Toast.makeText(this, "Image saved to gallery", Toast.LENGTH_SHORT).show()
            }
        }

        // 2. Create and save the PDF document
        val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val pdfDir = File(docsDir, "GPS Cam Visit Pro")
        if (!pdfDir.exists()) {
            pdfDir.mkdirs()
        }
        val pdfFile = File(pdfDir, "$fileName.pdf")

        // Retrieve the latest overlay info for the PDF.
        // This should be consistent with what was stamped on the bitmap as it's fetched right after.
        val mapThumbView = findViewById<ImageView>(R.id.liveMapThumbnail)
        val mapThumbnail = (mapThumbView?.drawable as? BitmapDrawable)?.bitmap
        val latText = lastOverlayLatText.ifBlank { "Lat: --" }
        val lonText = lastOverlayLonText.ifBlank { "Lon: --" }
        val addrText = findViewById<TextView>(R.id.geo_address)?.text?.toString() ?: "Address unavailable"
        val dateTimeText = findViewById<TextView>(R.id.geo_datetime)?.text?.toString() ?: SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault()).format(Date())

        gpsOverlayRenderer.createPdfWithOverlay(
            bitmap, // Use the (singly) overlaid bitmap
            pdfFile,
            mapThumbnail,
            latText,
            lonText,
            addrText,
            dateTimeText
        )
    }

    private fun fetchAddress(location: Location) {
        lifecycleScope.launch {
            val addressList = withContext(Dispatchers.IO) {
                Geocoder(this@MainActivity, Locale.getDefault())
                    .getFromLocation(location.latitude, location.longitude, 1)
            }
            if (!addressList.isNullOrEmpty()) {
                val addr = addressList[0]
                val lines = (0..addr.maxAddressLineIndex).map { addr.getAddressLine(it) }.distinct()
                val rawLandmark = addr.featureName
                val landmark =
                    if (rawLandmark != null && !rawLandmark.matches(Regex("^[23456789CFGHJMPQRVWX]+\\+.*$"))) {
                        rawLandmark
                    } else {
                        addr.subLocality ?: addr.locality ?: addr.subAdminArea
                    }
                address = lines.joinToString("\n")
                // Update overlay after address is set
                updateLiveOverlay()
            }
        }
    }

    private fun startVideoRecording() {
        if (!::videoCapture.isInitialized) {
            Toast.makeText(this, "Video capture not initialized", Toast.LENGTH_SHORT).show()
            return
        }

        val fileName = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "$fileName.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/GPS Cam Visit Pro")
        }

        val mediaStoreOutput = androidx.camera.video.MediaStoreOutputOptions.Builder(
            contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(contentValues).build()

        try {
            recording = videoCapture.output
                .prepareRecording(this, mediaStoreOutput)
                .apply {
                    if (ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        withAudioEnabled()
                    }
                }
                .start(ContextCompat.getMainExecutor(this)) { event ->
                    when (event) {
                        is androidx.camera.video.VideoRecordEvent.Start -> {
                            isRecording = true
                            runOnUiThread {
                                findViewById<ImageButton>(R.id.video_record_button)?.apply {
                                    setColorFilter(android.graphics.Color.RED)
                                }
                                Toast.makeText(this@MainActivity, "Recording started", Toast.LENGTH_SHORT).show()
                            }
                        }
                        is androidx.camera.video.VideoRecordEvent.Finalize -> {
                            isRecording = false
                            runOnUiThread {
                                findViewById<ImageButton>(R.id.video_record_button)?.clearColorFilter()
                                if (!event.hasError()) {
                                    val uri = event.outputResults.outputUri
                                    // Add GPS metadata to video
                                    addGpsMetadataToVideo(uri, fileName)
                                    Toast.makeText(
                                        this@MainActivity,
                                        "Video saved successfully",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } else {
                                    Log.e("VideoCapture", "Video capture error: ${event.error}")
                                    Toast.makeText(
                                        this@MainActivity,
                                        "Video recording failed: ${event.cause?.message}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                            recording = null
                        }
                    }
                }
        } catch (e: Exception) {
            Log.e("VideoCapture", "Error starting video recording: ${e.message}")
            Toast.makeText(this, "Failed to start recording: ${e.message}", Toast.LENGTH_SHORT).show()
            isRecording = false
        }
    }

    private fun stopVideoRecording() {
        recording?.stop()
        recording = null
        isRecording = false
        findViewById<ImageButton>(R.id.video_record_button)?.clearColorFilter()
    }

    private fun addGpsMetadataToVideo(uri: Uri, fileName: String) {
        try {
            val location = currentLocation
            if (location != null) {
                // For video files, we'll save GPS data to a companion text file
                val videosDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                val gpsDir = File(videosDir, "GPS Cam Visit Pro")
                if (!gpsDir.exists()) {
                    gpsDir.mkdirs()
                }

                val gpsFile = File(gpsDir, "${fileName}_gps.txt")
                val gpsData = buildString {
                    appendLine("GPS Data for video: $fileName.mp4")
                    appendLine("Latitude: ${location.latitude}")
                    appendLine("Longitude: ${location.longitude}")
                    appendLine("Address: $address")
                    appendLine("Date/Time: ${SimpleDateFormat("dd/MM/yyyy hh:mm:ss a", Locale.getDefault()).format(Date())}")
                    appendLine("Accuracy: ${location.accuracy} meters")
                    appendLine("Altitude: ${location.altitude} meters")
                }

                FileOutputStream(gpsFile).use { output ->
                    output.write(gpsData.toByteArray())
                }

                // Scan the file so it appears in file browsers
                MediaScannerConnection.scanFile(
                    this,
                    arrayOf(gpsFile.absolutePath),
                    arrayOf("text/plain"),
                    null
                )

                Log.d("VideoGPS", "GPS metadata saved to: ${gpsFile.absolutePath}")
            } else {
                Log.w("VideoGPS", "No location available for video metadata")
            }
        } catch (e: Exception) {
            Log.e("VideoGPS", "Error saving GPS metadata: ${e.message}")
        }
    }

    private fun createAndOpenPdfFromImage(bitmap: Bitmap, address: String, filename: String) {
        currentScreen = Screen.PDF
        // This method would contain the full PDF generation logic
        // For now, we'll use the AgriPssPdfGenerator or similar
        Toast.makeText(this, "PDF generation feature - implementation in progress", Toast.LENGTH_SHORT).show()
    }

    private fun dp(i: Int): Int = (i * resources.displayMetrics.density).toInt()
    private val prefs by lazy { getSharedPreferences("overlay_prefs", Context.MODE_PRIVATE) }
    private val hintPrefs by lazy { getSharedPreferences("overlay_hints", Context.MODE_PRIVATE) }

    private data class SafeRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width get() = right - left
        val height get() = bottom - top
    }

    private fun getSafeRect(): SafeRect {
        val root = findViewById<ConstraintLayout>(R.id.rootContainer) ?: return SafeRect(0,0,0,0)
        val buttons = findViewById<LinearLayout>(R.id.buttonLayout)
        val topInset = dp(8)
        val sideInset = dp(8)
        val bottomInset = (buttons?.height ?: 0) + dp(8)
        return SafeRect(
            left = sideInset,
            top = topInset,
            right = root.width - sideInset,
            bottom = root.height - bottomInset
        )
    }

    private fun saveOverlayFraction(xFrac: Float, yFrac: Float) {
        prefs.edit().putFloat("overlay_xf", xFrac).putFloat("overlay_yf", yFrac).apply()
    }

    private fun loadOverlayFraction(): Pair<Float, Float>? {
        if (!prefs.contains("overlay_xf")) return null
        return Pair(prefs.getFloat("overlay_xf", 0.7f), prefs.getFloat("overlay_yf", 0.7f))
    }

    private fun applyOverlayPositionByFraction(overlay: View, safe: SafeRect, xf: Float, yf: Float) {
        val maxX = (safe.width - overlay.width).coerceAtLeast(0)
        val maxY = (safe.height - overlay.height).coerceAtLeast(0)
        val x = safe.left + (maxX * xf).toInt()
        val y = safe.top + (maxY * yf).toInt()
        overlay.x = x.toFloat()
        overlay.y = y.toFloat()
    }

    private fun attachOverlayDrag(overlay: View) {
        var dX = 0f
        var dY = 0f
        var dragging = false
        val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()
        val handler = Handler(Looper.getMainLooper())
        val longPressRunnable = Runnable {
            dragging = true
            overlay.alpha = 0.8f
            Toast.makeText(this, R.string.drag_move_hint, Toast.LENGTH_SHORT).show()
        }

        overlay.setOnTouchListener { v, event ->
            val safe = getSafeRect()
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    handler.postDelayed(longPressRunnable, longPressTimeout)
                    dX = v.x - event.rawX
                    dY = v.y - event.rawY
                    true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    if (!dragging) return@setOnTouchListener true
                    val desiredX = event.rawX + dX
                    val desiredY = event.rawY + dY
                    val minX = safe.left.toFloat()
                    val minY = safe.top.toFloat()
                    val maxX = (safe.right - v.width).toFloat()
                    val maxY = (safe.bottom - v.height).toFloat()
                    v.x = desiredX.coerceIn(minX, maxX)
                    v.y = desiredY.coerceIn(minY, maxY)
                    true
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(longPressRunnable)
                    if (dragging) {
                        val maxX = (safe.width - v.width).coerceAtLeast(1)
                        val maxY = (safe.height - v.height).coerceAtLeast(1)
                        val xf = ((v.x - safe.left) / maxX)
                        val yf = ((v.y - safe.top) / maxY)
                        saveOverlayFraction(xf, yf)
                        v.alpha = 1f
                    }
                    dragging = false
                    true
                }
                else -> false
            }
        }
    }

    private fun initCameraAndListeners() {
        currentScreen = Screen.LIVE

        val orientation = resources.configuration.orientation
        val orientationStr = if (orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) "LANDSCAPE" else "PORTRAIT"
        Log.d("MainActivity", "initCameraAndListeners called - Orientation: $orientationStr")

        // Only set content view if we're coming from intro video (not from orientation change)
        // Check if the current content view is the intro layout
        val introView = findViewById<VideoView>(R.id.introVideoView)
        if (introView != null) {
            // We're coming from intro, need to set the main layout
            Log.d("MainActivity", "Detected intro view, setting main layout for orientation: $orientationStr")
            setContentView(R.layout.activity_main)
        } else {
            Log.d("MainActivity", "No intro view detected, using existing layout")
        }
        // Otherwise, the layout is already set correctly by onCreate()

        hideSystemUI()

        Log.d("MainActivity", "Finding viewFinder...")
        previewView = findViewById(R.id.viewFinder)
        Log.d("MainActivity", "viewFinder found: ${previewView != null}")
        previewView.implementationMode = PreviewView.ImplementationMode.PERFORMANCE
        captureButton = findViewById(R.id.camera_capture_button)
        findViewById<ImageButton>(R.id.switch_camera_button).setOnClickListener {
            isBackCamera = !isBackCamera
            startCamera()
        }
        findViewById<ImageButton>(R.id.camera_capture_button).setOnClickListener { capturePhoto() }
        // Video recording button
        findViewById<ImageButton>(R.id.video_record_button)?.setOnClickListener {
            if (!isRecording) {
                startVideoRecording()
            } else {
                stopVideoRecording()
            }
        }
        // Gallery button
        findViewById<ImageButton>(R.id.gallery_button).setOnClickListener {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val folderDocId = "primary:Pictures/GPS Cam Visit Pro"
                    val folderUri = DocumentsContract.buildDocumentUri(
                        "com.android.externalstorage.documents",
                        folderDocId
                    )
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "image/*"
                        putExtra(DocumentsContract.EXTRA_INITIAL_URI, folderUri)
                    }
                    startActivity(intent)
                } else {
                    val generic = Intent(Intent.ACTION_VIEW).apply {
                        type = "image/*"
                        data = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    }
                    startActivity(generic)
                }
            } catch (e: Exception) {
                val generic = Intent(Intent.ACTION_VIEW).apply {
                    type = "image/*"
                    data = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }
                startActivity(generic)
            }
        }
        // Prepare Report button
        findViewById<ImageButton>(R.id.prepare_button)?.setOnClickListener {
            openGallery()
        }
        // Settings button
        findViewById<ImageButton>(R.id.settingsButton)?.setOnClickListener {
            val options = arrayOf("On", "Off")
            val checked = if (visitMode) 0 else 1
            AlertDialog.Builder(this)
                .setTitle("Visit Mode")
                .setSingleChoiceItems(options, checked) { dialog, which ->
                    visitMode = (which == 0)
                }
                .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                .show()
        }
        // Collage button
        findViewById<ImageButton>(R.id.collage_button)?.setOnClickListener {
            openCollagePhotoPicker()
        }

        // Zoom buttons setup
        setupZoomButtons()

        requestPermissions()
        startCamera()
        getLocation()
        handler.post(updateRunnable)
        // Adjust GPS overlay now that views are inflated
        displayRotation = getDisplayRotation()
        adjustGpsOverlayPosition(displayRotation)

        // Schedule a secondary recalculation after all views are fully laid out
        // This ensures correct positioning even if views weren't measured on first call
        val rootLayout = findViewById<ConstraintLayout>(R.id.rootContainer)
        rootLayout?.viewTreeObserver?.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                rootLayout.viewTreeObserver.removeOnGlobalLayoutListener(this)
                adjustGpsOverlayPosition(displayRotation)
            }
        })
    }

    /**
     * Setup 1x/2x zoom toggle buttons.
     * Active button gets yellow background, inactive gets dark background.
     */
    private fun setupZoomButtons() {
        val zoom1xBtn = findViewById<TextView>(R.id.zoomButton1x) ?: return
        val zoom2xBtn = findViewById<TextView>(R.id.zoomButton2x) ?: return

        fun updateZoomUI() {
            if (isZoom2x) {
                zoom1xBtn.setBackgroundResource(R.drawable.zoom_button_inactive_bg)
                zoom2xBtn.setBackgroundResource(R.drawable.zoom_button_active_bg)
            } else {
                zoom1xBtn.setBackgroundResource(R.drawable.zoom_button_active_bg)
                zoom2xBtn.setBackgroundResource(R.drawable.zoom_button_inactive_bg)
            }
        }

        zoom1xBtn.setOnClickListener {
            if (isZoom2x) {
                isZoom2x = false
                cameraControl?.setZoomRatio(1.0f)
                updateZoomUI()
            }
        }

        zoom2xBtn.setOnClickListener {
            if (!isZoom2x) {
                isZoom2x = true
                cameraControl?.setZoomRatio(2.0f)
                updateZoomUI()
            }
        }

        // Set initial state
        updateZoomUI()
    }

    private var visitMode = false
    private var isFromGallery = false

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty() && uris.size <= MAX_PHOTOS) {
            lifecycleScope.launch {
                val bitmaps = uris.map { uri ->
                    withContext(Dispatchers.IO) {
                        val inputStream = contentResolver.openInputStream(uri)
                        BitmapFactory.decodeStream(inputStream)
                    }
                }
                capturedPhotos.clear()
                bitmaps.forEachIndexed { index, bitmap ->
                    val fileName = "gallery_${System.currentTimeMillis()}_$index"
                    capturedPhotos.add(Pair(bitmap, fileName))
                }
                isFromGallery = true
                address = "GPS Details not fetched"
                launchStaffForm()
            }
        } else if (uris.size > MAX_PHOTOS) {
            Toast.makeText(this, "Please select up to 6 photos only", Toast.LENGTH_SHORT).show()
        }
    }

    private val collagePhotoPickerLauncher = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            showCollageSizeDialog(uris)
        }
    }

    private fun openGallery() {
        galleryLauncher.launch("image/*")
    }

    private fun openCollagePhotoPicker() {
        collagePhotoPickerLauncher.launch("image/*")
    }

    private fun showCollageSizeDialog(photoUris: List<Uri>) {
        launchCollageEditor(photoUris, "Default")
    }

    private fun launchCollageEditor(photoUris: List<Uri>, layoutType: String) {
        // Launch the new Collage Editor V2 with complete feature set
        val intent = Intent(this, com.amod.geotagcamera.collage.CollageEditorActivity::class.java).apply {
            putParcelableArrayListExtra("PHOTO_URIS", ArrayList(photoUris))
            putExtra("LAYOUT_TYPE", layoutType)
        }
        startActivity(intent)
    }

    private fun launchStaffForm() {
        currentScreen = Screen.FORM

        // Save bitmaps to temporary files to avoid parcel size limit
        val photoPaths = mutableListOf<String>()
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                capturedPhotos.forEach { (bitmap, fileName) ->
                    try {
                        val tempFile = File(cacheDir, "${fileName}_temp.jpg")
                        FileOutputStream(tempFile).use { out ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                        }
                        photoPaths.add(tempFile.absolutePath)
                    } catch (e: Exception) {
                        Log.e("LaunchStaffForm", "Error saving bitmap to temp file: ${e.message}")
                    }
                }
            }

            // Now launch the activity with file paths instead of bitmaps
            val intent = Intent(this@MainActivity, StaffInputActivity::class.java)
            intent.putStringArrayListExtra("PHOTO_PATHS", ArrayList(photoPaths))
            intent.putStringArrayListExtra("PHOTO_FILENAMES", ArrayList(capturedPhotos.map { it.second }))
            startActivity(intent)
        }
    }

    private fun isLandscapeRotation(rotation: Int): Boolean {
        return rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270
    }

    private fun isLandscapeLayout(): Boolean {
        val rotation = if (::previewView.isInitialized) {
            previewView.display?.rotation ?: displayRotation
        } else {
            displayRotation
        }
        val rotationLandscape = isLandscapeRotation(rotation)
        val configLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        return rotationLandscape || configLandscape
    }
}
