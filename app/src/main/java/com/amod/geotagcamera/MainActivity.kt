package com.amod.geotagcamera
import android.Manifest
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.hardware.display.DisplayManager
import android.location.Geocoder
import android.location.Location
import android.media.MediaScannerConnection
import android.net.Uri
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
import android.util.Size
import android.content.ContentUris
import androidx.media3.common.util.UnstableApi
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
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private lateinit var previewView: PreviewView
    private lateinit var captureButton: ImageButton
    private lateinit var imageCapture: ImageCapture
    private lateinit var videoCapture: androidx.camera.video.VideoCapture<androidx.camera.video.Recorder>
    private var recording: androidx.camera.video.Recording? = null
    private var isRecording = false
    
    companion object {
        private var isIntroShown = false
        private var currentLocation: Location? = null
        private var address: String = ""
        private var lastSavedImageUri: Uri? = null
        private var offlinePromptShown = false
    }

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

    private val capturedPhotos = mutableListOf<Triple<Bitmap, String, String?>>() // Stores bitmap, filename, and gpsInfo string
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
    private var lastMapLat: Double = 0.0
    private var lastMapLon: Double = 0.0
    private lateinit var displayManager: DisplayManager
    private var displayRotation = Surface.ROTATION_0
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    // Zoom control
    private var cameraControl: androidx.camera.core.CameraControl? = null
    private var cameraInfo: androidx.camera.core.CameraInfo? = null
    private var currentZoomRatio = 1.0f

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



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Load initial visitMode from SharedPreferences
        val sharedPrefs = getSharedPreferences("com.amod.geotagcamera.PREFERENCES", Context.MODE_PRIVATE)
        visitMode = sharedPrefs.getBoolean("visit_mode", false)

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

        gpsOverlayRenderer = GpsOverlayRenderer(this)
        displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        setupOrientationListener()

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
                Log.d("MainActivity", "Intro completed, calling checkInternetAndInitialize")
                checkInternetAndInitialize()
            }
            // Handle Skip intro
            val skipButton = findViewById<Button>(R.id.skipButton)
            skipButton.setOnClickListener {
                videoView.stopPlayback()
                isIntroShown = true
                Log.d("MainActivity", "Intro skipped, calling checkInternetAndInitialize")
                checkInternetAndInitialize()
            }
            videoView.start()
        } else {
            // After intro, let Android load the correct layout based on orientation
            Log.d("MainActivity", "Intro already shown, setting main layout for orientation: $orientationStr")
            setContentView(R.layout.activity_main)
            Log.d("MainActivity", "Content view set, calling checkInternetAndInitialize")
            checkInternetAndInitialize()
        }

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

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Ensure system UI (back panel) stays hidden during intro video
        if (hasFocus && !isIntroShown) {
            hideSystemUI()
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d("MainActivity", "onResume called")
        
        // Load latest visitMode from SharedPreferences
        val sharedPrefs = getSharedPreferences("com.amod.geotagcamera.PREFERENCES", Context.MODE_PRIVATE)
        visitMode = sharedPrefs.getBoolean("visit_mode", false)

        displayManager.registerDisplayListener(displayListener, handler)
        if (orientationEventListener.canDetectOrientation()) {
            orientationEventListener.enable()
        }
        
        // Restart the live UI update loop (in case it was stopped when launching the form)
        handler.removeCallbacks(updateRunnable)
        handler.post(updateRunnable)
        
        // Immediate UI refresh using cached address and location
        updateLiveOverlay()
        
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
        val buttonLayout = findViewById<View>(R.id.buttonLayout)
        val zoomContainer = findViewById<View>(R.id.zoomButtonContainer)

        if (rootLayout.width == 0 || rootLayout.height == 0 || 
            (buttonLayout != null && buttonLayout.height == 0) ||
            (zoomContainer != null && zoomContainer.height == 0)) {
            rootLayout.post { adjustGpsOverlayPosition(rotation) }
            return
        }

        runOnUiThread {
            val overlayWrapper = findViewById<FrameLayout>(R.id.gpsOverlayWrapper) ?: return@runOnUiThread
            val overlayCard = findViewById<MaterialCardView>(R.id.gpsOverlayCard) ?: return@runOnUiThread
            val buttonLayout = findViewById<View>(R.id.buttonLayout) ?: return@runOnUiThread
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
            val isPortrait = rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180
            val globalScale = if (isPortrait) 0.9f else 0.8f
            val density = resources.displayMetrics.density

            // Reset transforms
            overlayWrapper.scaleX = 1.0f
            overlayWrapper.scaleY = 1.0f
            overlayWrapper.rotation = 0f
            overlayWrapper.translationX = 0f
            overlayWrapper.translationY = 0f // Reset Y translation as well

            // 1. Adjust Map Size (Further reduction for Landscape)
            findViewById<ImageView>(R.id.liveMapThumbnail)?.let { mv ->
                val mapMinH = if (isLandscape) (50 * density * globalScale).toInt() else (80 * density * globalScale).toInt()
                mv.minimumHeight = mapMinH
                val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT).apply {
                    weight = if (isLandscape) 0.35f else 0.55f // Further reduced map weight
                    gravity = Gravity.CENTER_VERTICAL
                }
                mv.layoutParams = params
            }
            
            // 2. Update text container params
            val updatedTextParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                weight = if (isLandscape) 1.65f else 1.45f // Increased text weight accordingly
                marginStart = (14 * density * globalScale).toInt()
                gravity = Gravity.CENTER_VERTICAL
            }
            textContainer.layoutParams = updatedTextParams
            textContainer.setPadding(
                (8 * density * globalScale).toInt(),
                (4 * density * globalScale).toInt(),
                (8 * density * globalScale).toInt(),
                (4 * density * globalScale).toInt()
            )

            if (rotation == Surface.ROTATION_0) {
                // PORTRAIT: Full width, centered bottom
                val set = ConstraintSet()
                set.clone(rootLayout)
                set.clear(R.id.gpsOverlayWrapper)
                set.connect(R.id.gpsOverlayWrapper, ConstraintSet.BOTTOM, R.id.zoomButtonContainer, ConstraintSet.TOP, (4 * density).toInt())
                set.connect(R.id.gpsOverlayWrapper, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, 0)
                set.connect(R.id.gpsOverlayWrapper, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END, 0)
                set.constrainWidth(R.id.gpsOverlayWrapper, ConstraintSet.MATCH_CONSTRAINT)
                set.constrainHeight(R.id.gpsOverlayWrapper, ConstraintSet.WRAP_CONTENT)
                set.applyTo(rootLayout)
                
                overlayWrapper.layoutParams.width = FrameLayout.LayoutParams.MATCH_PARENT
                overlayCard.layoutParams.width = FrameLayout.LayoutParams.MATCH_PARENT
                overlayCard.requestLayout()
                overlayWrapper.requestLayout()
            } else {
                // LANDSCAPE: Centered horizontally on the long edge with equal margins
                val rootW = rootLayout.width
                val rootH = rootLayout.height

                val (screenEdgeLength, rotAngle) = when (rotation) {
                    Surface.ROTATION_90 -> Pair(rootH, -90f)
                    Surface.ROTATION_270 -> Pair(rootH, 90f)
                    Surface.ROTATION_180 -> Pair(rootW, 180f)
                    else -> Pair(rootW, 0f)
                }

                val targetWidth = (screenEdgeLength * 0.7f).toInt() 
                
                // CRUCIAL: Set WRAPPER width to targetWidth too, otherwise it rotates a full-screen bar
                overlayWrapper.layoutParams.width = targetWidth
                overlayWrapper.layoutParams.height = FrameLayout.LayoutParams.WRAP_CONTENT
                overlayCard.layoutParams.width = FrameLayout.LayoutParams.MATCH_PARENT
                
                // Measure card height
                overlayCard.measure(
                    View.MeasureSpec.makeMeasureSpec(targetWidth, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                )
                val barHeight = overlayCard.measuredHeight

                // Dead center the wrapper first
                val set = ConstraintSet()
                set.clone(rootLayout)
                set.clear(R.id.gpsOverlayWrapper)
                set.centerHorizontally(R.id.gpsOverlayWrapper, ConstraintSet.PARENT_ID)
                set.centerVertically(R.id.gpsOverlayWrapper, ConstraintSet.PARENT_ID)
                set.constrainWidth(R.id.gpsOverlayWrapper, targetWidth)
                set.constrainHeight(R.id.gpsOverlayWrapper, barHeight)
                set.applyTo(rootLayout)

                overlayWrapper.rotation = rotAngle
                
                // Perfect Centering Logic:
                // translationY=0 ensures it is perfectly centered along the software WIDTH (physical long axis)
                // translationX moves it to the software BOTTOM (physical short axis edge)
                val tx = when (rotation) {
                    Surface.ROTATION_90 -> (rootW - barHeight) / 2f - (12 * density)
                    Surface.ROTATION_270 -> -(rootW - barHeight) / 2f + (12 * density)
                    Surface.ROTATION_180 -> -(rootH - barHeight) / 2f + (12 * density)
                    else -> 0f
                }
                overlayWrapper.translationX = tx
                overlayWrapper.translationY = 0f 
                
                overlayCard.requestLayout()
                overlayWrapper.requestLayout()
            }
        }
    }


    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.RECORD_AUDIO
        )
        
        // Android 10+ (API 29) requires this to read EXIF GPS from gallery/media files
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.ACCESS_MEDIA_LOCATION)
        }

        permissionLauncher.launch(permissions.toTypedArray())
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

            // Setup video capture (Optimized to 720p HD with fallback for extremely fast post-processing)
            val recorder = androidx.camera.video.Recorder.Builder()
                .setQualitySelector(
                    androidx.camera.video.QualitySelector.from(
                        androidx.camera.video.Quality.HD,
                        androidx.camera.video.FallbackStrategy.higherQualityOrLowerThan(androidx.camera.video.Quality.HD)
                    )
                )
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
                cameraControl?.setZoomRatio(currentZoomRatio)

                // Show .6x button only if supported (ultra-wide lens available)
                camera.cameraInfo.zoomState.observe(this@MainActivity) { state ->
                    val minZoom = state.minZoomRatio
                    runOnUiThread {
                        findViewById<TextView>(R.id.zoomButton06x)?.visibility = if (minZoom <= 0.61f) View.VISIBLE else View.GONE
                    }
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
        lastOverlayLatText = lat?.let { "%.5f".format(it) } ?: "--"
        lastOverlayLonText = lon?.let { "%.5f".format(it) } ?: "--"

        // Update state and refresh UI immediately for text
        lastOverlayAddrText = address
        lastOverlayDateTime = dateTimeView.text.toString()

        // Fetch and display static map thumbnail with distance threshold
        if (lat != null && lon != null) {
            val dist = FloatArray(1)
            Location.distanceBetween(lastMapLat, lastMapLon, lat, lon, dist)
            
            if (lastOverlayMapThumbnail == null || dist[0] > 5.0) {
                val apiKey = try {
                    BuildConfig.MAPS_API_KEY
                } catch (e: Exception) {
                    "AIzaSyCfJ2d9XnWCbbi2hMBoQLma19Pr8fVNeaU"
                }

                val url = "https://maps.googleapis.com/maps/api/staticmap" +
                        "?center=$lat,$lon" +
                        "&zoom=17&size=400x400&scale=2" +
                        "&markers=color:red%7C$lat,$lon" +
                        "&key=$apiKey"

                lifecycleScope.launch {
                    val bmp = withContext(Dispatchers.IO) {
                        try {
                            val request = Request.Builder().url(url).build()
                            val response = httpClient.newCall(request).execute()
                            if (response.isSuccessful) {
                                response.body?.byteStream()?.let { BitmapFactory.decodeStream(it) }
                            } else {
                                Log.e("MapFetch", "Failed: ${response.code}")
                                null
                            }
                        } catch (e: Exception) {
                            Log.e("MapFetch", "Error: ${e.message}")
                            null
                        }
                    }
                    bmp?.let {
                        lastOverlayMapThumbnail = it
                        lastMapLat = lat
                        lastMapLon = lon
                        runOnUiThread {
                            mapImageView.setImageBitmap(it)
                            mapImageView.invalidate()
                            mapImageView.requestLayout()
                        }
                    }
                }
            }
        }
    }

    private fun capturePhoto() {
        val fileName = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
        val shutterLat = currentLocation?.latitude
        val shutterLon = currentLocation?.longitude
        val shutterAddr = findViewById<TextView>(R.id.geo_address)?.text?.toString() ?: "Address unavailable"
        val shutterGpsText = if (shutterLat != null && shutterLon != null) "Lat %.5f, Long %.5f".format(shutterLat, shutterLon) else null

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
                    // Mirror handling: User requested "captured as Mirror image" for front camera.
                    // To match the preview look (Selfie), we HORIZONTALLY flip the image.
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
                    // Use SHUTTER-TIME values instead of live ones (which might have drifted)
                    val mapThumbnail = lastOverlayMapThumbnail
                    val latText = if (shutterLat != null) "Lat: %.5f".format(shutterLat) else "Lat: --"
                    val lonText = if (shutterLon != null) "Lon: %.5f".format(shutterLon) else "Lon: --"
                    val addrText = shutterAddr
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
                            showImagePurview(overlaidBitmap, fileName, shutterGpsText)
                        }
                        saveImageToGallery(overlaidBitmap, fileName, shutterGpsText)
                    } else {
                        // Only save to gallery and return to live camera
                        saveImageToGallery(overlaidBitmap, fileName, shutterGpsText)
                        runOnUiThread {
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


    private fun showImagePurview(bitmap: Bitmap, fileName: String, shutterGps: String? = null) {
        currentScreen = Screen.PURVIEW
        lastCapturedBitmap = bitmap
        lastCapturedFileName = fileName
        
        Log.d("GPS_DEBUG", "Showing preview for $fileName. Shutter GPS: $shutterGps")

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
                            val (newBmp, newName, _) = capturedPhotos.last()
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

        // Add current and previous thumbnails (temporary photo from this capture cycle is added at the end of the dialog)
        // Note: For preview, we treat the current capture as the first one
        addFramedThumbnail(bitmap, fileName)
        capturedPhotos.asReversed().forEach { (prevBmp, prevName, _) ->
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
                // STRICT: Use shutter source only. Live fallback is prohibited by requirements.
                val gpsToStore = shutterGps
                
                Log.d("GPS_DEBUG", "Adding photo to list. Storing GPS: $gpsToStore")
                capturedPhotos.add(Triple(bitmap, fileName, gpsToStore))
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
                
                val gpsToStore = shutterGps

                Log.d("GPS_DEBUG", "Finalizing report. Storing last photo GPS: $gpsToStore")
                capturedPhotos.add(Triple(bitmap, fileName, gpsToStore))
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

    private fun saveImageToGallery(bitmap: Bitmap, fileName: String, gpsText: String? = null) {
        // The bitmap passed here already has the overlay from capturePhoto().
        // We will save it directly and then separately create the PDF.

        // 1. Save the bitmap to the gallery
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$fileName.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/GPS Cam Visit Pro")
        }

        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        uri?.let { targetUri ->
            contentResolver.openOutputStream(targetUri)?.use { out ->
                // First save to a temp file to add EXIF, then copy
                val tempFile = File(cacheDir, "${fileName}_temp.jpg")
                // Use the passed shutter-time GPS info
                saveBitmapWithExif(bitmap, tempFile, currentLocation, gpsText)
                tempFile.inputStream().use { input ->
                    input.copyTo(out)
                }
                tempFile.delete()
                Toast.makeText(this, "Image saved to gallery", Toast.LENGTH_SHORT).show()
                Log.d("GPS_DEBUG", "Gallery save complete for $fileName with EXIF: $gpsText")
                
                // Update persistent URI and refresh the UI thumbnail
                lastSavedImageUri = targetUri
                runOnUiThread {
                    findViewById<ImageView>(R.id.gallery_button).apply {
                        setImageBitmap(bitmap)
                    }
                }
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
            try {
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
                } else {
                    address = "Address unavailable"
                    updateLiveOverlay()
                }
            } catch (e: Exception) {
                Log.e("Location", "Geocoder failed: ${e.message}")
                address = "Address unavailable (Offline)"
                updateLiveOverlay()
            }
        }
    }

    private fun loadLastTakenPhotoThumbnail() {
        lifecycleScope.launch(Dispatchers.IO) {
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATE_TAKEN
            )
            
            // RELATIVE_PATH is Q (29) or higher. For older versions, we check DATA or just query all and filter.
            val selection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
            } else {
                "${MediaStore.Images.Media.DATA} LIKE ?"
            }
            val selectionArgs = arrayOf("%Pictures/GPS Cam Visit Pro%")
            val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC"

            try {
                contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    sortOrder
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                        val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                        
                        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            contentResolver.loadThumbnail(uri, Size(128, 128), null)
                        } else {
                            @Suppress("DEPRECATION")
                            MediaStore.Images.Thumbnails.getThumbnail(contentResolver, id, MediaStore.Images.Thumbnails.MINI_KIND, null)
                        }
                        
                        lastSavedImageUri = uri
                        withContext(Dispatchers.Main) {
                            findViewById<ImageView>(R.id.gallery_button).apply {
                                setImageBitmap(bitmap)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("Gallery", "Failed to load thumbnail: ${e.message}")
            }
        }
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    private fun startVideoRecording() {
        if (!::videoCapture.isInitialized) {
            Toast.makeText(this, "Video capture not initialized", Toast.LENGTH_SHORT).show()
            return
        }

        val fileName = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
        val tempFile = File(cacheDir, "raw_video_$fileName.mp4")
        val fileOutputOptions = androidx.camera.video.FileOutputOptions.Builder(tempFile).build()

        try {
            recording = videoCapture.output
                .prepareRecording(this, fileOutputOptions)
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
                                    // Trigger Automatic Post-Processing (Watermarking)
                                    applyWatermarkToVideo(uri)
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

    @androidx.annotation.OptIn(UnstableApi::class)
    private fun applyWatermarkToVideo(inputUri: Uri) {
        val processor = com.amod.geotagcamera.utils.VideoPostProcessor(this)
        
        // Use the same snapshot data as the current live view
        val lat = lastOverlayLatText
        val lon = lastOverlayLonText
        val addr = address
        val dt = lastOverlayDateTime
        val thumb = lastOverlayMapThumbnail

        // Show a "Processing" message
        val statusToast = Toast.makeText(this, "Applying GPS Overlay... Please wait.", Toast.LENGTH_SHORT)
        statusToast.show()

        processor.processVideoWithGpsOverlay(
            inputUri, thumb, lat, lon, addr, dt,
            object : com.amod.geotagcamera.utils.VideoPostProcessor.Callback {
                override fun onProcessingStarted() {
                    Log.d("VideoPost", "Processing started for $inputUri")
                }

                override fun onProcessingFinished(outputFile: File) {
                    runOnUiThread {
                        statusToast.cancel()
                        saveProcessedVideoToGallery(outputFile)
                        // Cleanup temp file
                        try { File(inputUri.path!!).delete() } catch(e: Exception) {}
                    }
                }

                override fun onProcessingFailed(error: String) {
                    runOnUiThread {
                        statusToast.cancel()
                        Toast.makeText(this@MainActivity, "Watermark failed: $error", Toast.LENGTH_SHORT).show()
                        // Fallback: Save the original raw video anyway
                        saveRawVideoToGallery(inputUri)
                    }
                }
            }
        )
    }

    private fun saveProcessedVideoToGallery(outputFile: File) {
        val fileName = "GPS_Video_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis()) + ".mp4"
        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/GPS Cam Visit Pro")
            put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
        }

        val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
        uri?.let { destUri ->
            try {
                contentResolver.openOutputStream(destUri)?.use { outputStream ->
                    outputFile.inputStream().use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                Toast.makeText(this, "Video saved with GPS overlay!", Toast.LENGTH_LONG).show()
                refreshGallery(destUri)
                
                // Add companionship text file
                lastSavedImageUri = destUri
                addGpsMetadataToVideo(destUri, fileName) 
            } catch (e: Exception) {
                Log.e("SaveVideo", "Error: ${e.message}")
            }
        }
    }

    private fun saveRawVideoToGallery(inputUri: Uri) {
         // Fallback logic to save raw video if processing fails
         val file = File(inputUri.path ?: return)
         saveProcessedVideoToGallery(file)
    }

    private fun refreshGallery(uri: Uri) {
        MediaScannerConnection.scanFile(this, arrayOf(uri.path), null, null)
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
        // Setup the Gallery Thumbnail UI
        loadLastTakenPhotoThumbnail()

        // Gallery button - Launches the phone's default gallery app
        findViewById<ImageView>(R.id.gallery_button).setOnClickListener {
            val uri = lastSavedImageUri
            if (uri != null) {
                // Open the specific last photo in the phone's Gallery App
                try {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "image/*")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "No gallery app found", Toast.LENGTH_SHORT).show()
                }
            } else {
                // Fallback: Show the folder in a document picker if no image has been taken yet
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
                    Log.e("Gallery", "Error opening gallery fallback: ${e.message}")
                }
            }
        }
        // Prepare Report button
        findViewById<ImageButton>(R.id.prepare_button)?.setOnClickListener {
            openGallery()
        }
        // Settings button
        findViewById<ImageButton>(R.id.settingsButton)?.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
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
        
        // Start live UI update loop (safely)
        handler.removeCallbacks(updateRunnable)
        handler.post(updateRunnable)
        
        // Immediate UI refresh with existing geo-data
        updateLiveOverlay()
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
        val zoom06Btn = findViewById<TextView>(R.id.zoomButton06x) ?: return
        val zoom1xBtn = findViewById<TextView>(R.id.zoomButton1x) ?: return
        val zoom2xBtn = findViewById<TextView>(R.id.zoomButton2x) ?: return
    
        fun updateZoomUI() {
            zoom06Btn.setBackgroundResource(if (currentZoomRatio == 0.6f) R.drawable.zoom_button_active_bg else R.drawable.zoom_button_inactive_bg)
            zoom1xBtn.setBackgroundResource(if (currentZoomRatio == 1.0f) R.drawable.zoom_button_active_bg else R.drawable.zoom_button_inactive_bg)
            zoom2xBtn.setBackgroundResource(if (currentZoomRatio >= 2.0f) R.drawable.zoom_button_active_bg else R.drawable.zoom_button_inactive_bg)
        }
    
        zoom06Btn.setOnClickListener {
            currentZoomRatio = 0.6f
            cameraControl?.setZoomRatio(0.6f)
            updateZoomUI()
        }
        zoom1xBtn.setOnClickListener {
            currentZoomRatio = 1.0f
            cameraControl?.setZoomRatio(1.0f)
            updateZoomUI()
        }
        zoom2xBtn.setOnClickListener {
            currentZoomRatio = 2.0f
            cameraControl?.setZoomRatio(2.0f)
            updateZoomUI()
        }
    
        // Set initial state
        updateZoomUI()
    }

    private var visitMode = false
    private var isFromGallery = false

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty() && uris.size <= MAX_PHOTOS) {
            lifecycleScope.launch {
                val results = uris.map { uri ->
                    withContext(Dispatchers.IO) {
                        val inputStream = contentResolver.openInputStream(uri)
                        val bitmap = BitmapFactory.decodeStream(inputStream)
                        
                        // Try to extract GPS and Date from the gallery image itself
                        var gpsInfo: String? = null
                        var dateInfo: String? = null
                        try {
                            // First, stabilize the URI to handle sensitive data
                            val stableUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                MediaStore.setRequireOriginal(uri)
                            } else uri

                            contentResolver.openInputStream(stableUri)?.use { input ->
                                val exif = ExifInterface(input)
                                val latLong = FloatArray(2)
                                if (exif.getLatLong(latLong)) {
                                    @Suppress("DefaultLocale")
                                    gpsInfo = "Lat %.5f, Long %.5f".format(latLong[0], latLong[1])
                                    Log.d("GPS_DEBUG", "Gallery EXIF hit for URI $stableUri: $gpsInfo")
                                } else {
                                    Log.w("GPS_DEBUG", "Gallery EXIF miss for URI $stableUri (No coordinates tag)")
                                }
                                
                                val dt = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
                                if (dt != null) {
                                    try {
                                        val inFmt = java.text.SimpleDateFormat("yyyy:MM:dd HH:mm:ss", java.util.Locale.US)
                                        val pt = inFmt.parse(dt)
                                        if (pt != null) {
                                            val outFmt = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
                                            dateInfo = outFmt.format(pt)
                                        }
                                    } catch (e: Exception) {}

                                    if (dateInfo == null) {
                                        try {
                                            val sdfIn = if (dt.lowercase().contains("m")) {
                                                java.text.SimpleDateFormat("dd/MM/yyyy hh:mm a", java.util.Locale.US)
                                            } else {
                                                java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.US)
                                            }
                                            val pt = sdfIn.parse(dt.trim())
                                            if (pt != null) {
                                                val outFmt = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
                                                dateInfo = outFmt.format(pt)
                                            }
                                        } catch (e: Exception) {}
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("GPS_DEBUG", "Gallery EXIF error for URI $uri: ${e.message}")
                        }
                        
                        Triple(bitmap, gpsInfo, dateInfo)
                    }
                }
                capturedPhotos.clear()
                results.forEachIndexed { index, (bitmap, gpsInfo, dateInfo) ->
                    val fileName = dateInfo ?: "gallery_${System.currentTimeMillis()}_$index"
                    capturedPhotos.add(Triple(bitmap, fileName, gpsInfo))
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
        Log.d("GPS_DEBUG", "Launching Staff Form with ${capturedPhotos.size} photos.")
        capturedPhotos.forEachIndexed { i, triple -> Log.d("GPS_DEBUG", "Photo $i GPS: ${triple.third}") }
        
        val photoPaths = mutableListOf<String>()
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                capturedPhotos.forEach { (bitmap, fileName, gpsInfo) ->
                    try {
                        val tempFile = File(cacheDir, "${fileName}_temp.jpg")
                        saveBitmapWithExif(bitmap, tempFile, currentLocation, gpsInfo)
                        photoPaths.add(tempFile.absolutePath)
                        Log.d("GPS_DEBUG", "Prepared temp file: ${tempFile.name} with info: $gpsInfo")
                    } catch (e: Exception) {
                        Log.e("LaunchStaffForm", "Error saving bitmap to temp file: ${e.message}")
                    }
                }
            }

            // Now launch the activity with file paths instead of bitmaps
            val intent = Intent(this@MainActivity, StaffInputActivity::class.java)
            intent.putStringArrayListExtra("PHOTO_PATHS", ArrayList(photoPaths))
            intent.putStringArrayListExtra("PHOTO_FILENAMES", ArrayList(capturedPhotos.map { it.second }))
            intent.putStringArrayListExtra("PHOTO_GPS", ArrayList(capturedPhotos.map { it.third ?: "" }))
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

    private fun saveBitmapWithExif(bitmap: Bitmap, file: File, location: Location?, gpsInfo: String? = null) {
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }
        
        // Use ONLY provided gpsInfo string if available. DO NOT fallback to live location.
        val finalGpsInfo = gpsInfo 

        if (finalGpsInfo != null) {
            val info = finalGpsInfo
            try {
                // Regex to extract Lat and Long from strings like "Lat 22.947239, Long 72.82264"
                val match = Regex("Lat\\s*(-?\\d+\\.?\\d*),\\s*Long\\s*(-?\\d+\\.?\\d*)").find(info)
                if (match != null) {
                    val lat = match.groupValues[1].toDouble()
                    val lon = match.groupValues[2].toDouble()
                    
                    val exif = ExifInterface(file.absolutePath)
                    
                    fun formatCoord(coord: Double): String {
                        val deg = Math.abs(coord).toInt()
                        val min = ((Math.abs(coord) - deg) * 60).toInt()
                        val sec = (Math.abs(coord) - deg - min / 60.0) * 3600.0
                        return "$deg/1,$min/1,${(sec * 1000).toInt()}/1000"
                    }

                    exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, formatCoord(lat))
                    exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, if (lat >= 0) "N" else "S")
                    exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, formatCoord(lon))
                    exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, if (lon >= 0) "E" else "W")
                    
                    // Crucial: Embed the Date Time to perfectly match the original photo stamp if one exists.
                    var timestamp: String? = null
                    try {
                        val baseName = file.name
                        
                        // Try pattern 1
                        val match1 = Regex("(\\d{8}_\\d{6})").find(baseName)
                        if (match1 != null) {
                            val timestampStr = match1.groupValues[1]
                            val inFmt = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
                            val pt = inFmt.parse(timestampStr)
                            if (pt != null) {
                                timestamp = java.text.SimpleDateFormat("yyyy:MM:dd HH:mm:ss", java.util.Locale.US).format(pt)
                            }
                        } else {
                            // Try pattern 2
                            val match2 = Regex("(\\d{2}/\\d{2}/\\d{4}\\s+\\d{1,2}:\\d{2}\\s*(?:am|pm|AM|PM)?)").find(baseName)
                            if (match2 != null) {
                                val timestampStr = match2.groupValues[1]
                                val sdfIn = if (timestampStr.lowercase().contains("m")) {
                                    java.text.SimpleDateFormat("dd/MM/yyyy hh:mm a", java.util.Locale.US)
                                } else {
                                    java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.US)
                                }
                                val pt = sdfIn.parse(timestampStr.trim())
                                if (pt != null) {
                                    timestamp = java.text.SimpleDateFormat("yyyy:MM:dd HH:mm:ss", java.util.Locale.US).format(pt)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("GPS_DEBUG", "Could not parse original date from temp filename: ${e.message}")
                    }
                    
                    if (timestamp != null) {
                        exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, timestamp)
                    }
                    
                    exif.saveAttributes()
                    Log.d("GPS_DEBUG", "Exif Attributes saved for ${file.name}: $lat, $lon")
                } else {
                    Log.w("GPS_DEBUG", "Regex match failed for GPS string: '$info'")
                }
            } catch (e: Exception) {
                Log.e("GPS_DEBUG", "Exif Save Error: ${e.message}")
            }
        } else {
            Log.w("GPS_DEBUG", "No GPS info available to save for ${file.name}")
        }
    }

    private fun isInternetAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
            return when {
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
                else -> false
            }
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo ?: return false
            @Suppress("DEPRECATION")
            return networkInfo.isConnected
        }
    }

    private fun showOfflineDialog() {
        AlertDialog.Builder(this)
            .setTitle("Internet Connection Required")
            .setMessage("This app requires an active internet connection to fetch real-time address details and load map overlays.\n\nWithout internet, some functions will not be available, but you can still capture photos and record videos.\n\nWould you like to proceed offline or exit the app?")
            .setCancelable(false)
            .setPositiveButton("Proceed") { dialog, _ ->
                dialog.dismiss()
                initCameraAndListeners()
            }
            .setNegativeButton("Exit") { _, _ ->
                finishAffinity()
            }
            .show()
    }

    private fun checkInternetAndInitialize() {
        if (!isInternetAvailable() && !offlinePromptShown) {
            offlinePromptShown = true
            showOfflineDialog()
        } else {
            initCameraAndListeners()
        }
    }
}
