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
import android.view.MotionEvent
import android.view.OrientationEventListener
import android.view.Surface
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.camera2.interop.Camera2CameraInfo
import android.hardware.camera2.CameraCharacteristics
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import android.util.Size
import android.content.ContentUris
import androidx.media3.common.util.UnstableApi
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.lifecycleScope
import com.amod.geotagcamera.model.AppLanguage
import com.amod.geotagcamera.model.CustomNoteConfig
import com.amod.geotagcamera.model.MapType
import com.amod.geotagcamera.model.NotePosition
import com.amod.geotagcamera.model.OverlayTemplate
import com.amod.geotagcamera.ui.InstagramTextEditorDialog
import com.amod.geotagcamera.utils.CompassRenderer
import com.amod.geotagcamera.utils.GpsOverlayRenderer
import com.amod.geotagcamera.utils.InstagramTextStyler
import com.amod.geotagcamera.utils.QrCodeGenerator
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
    private var isVideoSupported = false
    private fun playShutterSound() {
        if (!cameraSoundSetting) return
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
            audioManager?.playSoundEffect(android.media.AudioManager.FX_KEY_CLICK, 1.0f)
                ?: window?.decorView?.playSoundEffect(android.view.SoundEffectConstants.CLICK)
        } catch (_: Throwable) {
            try {
                window?.decorView?.playSoundEffect(android.view.SoundEffectConstants.CLICK)
            } catch (_: Throwable) {}
        }
    }

    private fun playTimerBeep() {
        if (!cameraSoundSetting) return
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
            audioManager?.playSoundEffect(android.media.AudioManager.FX_KEY_CLICK, 0.7f)
                ?: window?.decorView?.playSoundEffect(android.view.SoundEffectConstants.CLICK)
        } catch (_: Throwable) {
            try {
                window?.decorView?.playSoundEffect(android.view.SoundEffectConstants.CLICK)
            } catch (_: Throwable) {}
        }
    }
    
    companion object {
        private var isIntroShown = false
        private var currentLocation: Location? = Location("GPS").apply {
            latitude = 37.421998
            longitude = -122.084000
        }
        private var address: String = "Googleplex, Mountain View, CA 94043, United States"
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
    private var isUsingFallbackThumbnail: Boolean = false
    private var mapFetchJob: kotlinx.coroutines.Job? = null
    private var lastOverlayLatText: String = ""
    private var lastOverlayLonText: String = ""
    private var lastOverlayAddrText: String = ""
    private var lastOverlayDateTime: String = ""
    private var lastMapLat: Double = 0.0
    private var lastMapLon: Double = 0.0
    private var lastFetchedMapType: MapType? = null
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

    // Dynamic Camera Options
    private var cameraRatioSetting = "4:3"
    private var cameraGridSetting = "OFF"
    private var cameraTimerSetting = 0
    private var cameraFocusSetting = "AUTO"
    private var cameraMirrorSetting = false
    private var cameraSoundSetting = true
    private var cameraWbSetting = "AUTO"
    private var cameraLevelSetting = false
    private var cameraTextSetting = ""
    private var cameraVoiceSetting = true
    private var cameraFlashSetting = "Auto" // "Auto", "On", "Off"
    private var cameraStampSetting = true  // Front or Rear camera stamp on overlay
    private var cameraTelemetrySetting = true
    private var cameraTimeFormatSetting = "12H"

    // Sensor Manager for leveling bubble and compass
    private var sensorManager: android.hardware.SensorManager? = null
    private var accelerometer: android.hardware.Sensor? = null
    private var magnetometer: android.hardware.Sensor? = null
    private val lastAccelerometerValues = FloatArray(3)
    private val lastMagnetometerValues = FloatArray(3)
    private var isAccelerometerSet = false
    private var isMagnetometerSet = false
    private var currentDeviceAzimuth = 207.0f
    private var currentDeviceMagneticField = 71.25f

    private val sensorListener = object : android.hardware.SensorEventListener {
        override fun onSensorChanged(event: android.hardware.SensorEvent) {
            if (event.sensor.type == android.hardware.Sensor.TYPE_ACCELEROMETER) {
                val ax = event.values[0]
                val ay = event.values[1]
                val az = event.values[2]
                System.arraycopy(event.values, 0, lastAccelerometerValues, 0, 3)
                isAccelerometerSet = true
                val roll = Math.toDegrees(Math.atan2(-ax.toDouble(), ay.toDouble())).toFloat()
                findViewById<com.amod.geotagcamera.ui.CameraGridView>(R.id.cameraGridView)?.updateRoll(roll)
            } else if (event.sensor.type == android.hardware.Sensor.TYPE_MAGNETIC_FIELD) {
                System.arraycopy(event.values, 0, lastMagnetometerValues, 0, 3)
                isMagnetometerSet = true
                val mx = event.values[0]
                val my = event.values[1]
                val mz = event.values[2]
                currentDeviceMagneticField = Math.sqrt((mx * mx + my * my + mz * mz).toDouble()).toFloat()
            }

            if (isAccelerometerSet && isMagnetometerSet) {
                val rotationMatrix = FloatArray(9)
                val inclinationMatrix = FloatArray(9)
                if (android.hardware.SensorManager.getRotationMatrix(rotationMatrix, inclinationMatrix, lastAccelerometerValues, lastMagnetometerValues)) {
                    val orientation = FloatArray(3)
                    android.hardware.SensorManager.getOrientation(rotationMatrix, orientation)
                    val azimuthInRadians = orientation[0]
                    var azimuthInDegrees = Math.toDegrees(azimuthInRadians.toDouble()).toFloat()
                    if (azimuthInDegrees < 0) azimuthInDegrees += 360f
                    currentDeviceAzimuth = azimuthInDegrees
                    gpsOverlayRenderer.currentAzimuth = currentDeviceAzimuth
                    gpsOverlayRenderer.currentMagneticField = currentDeviceMagneticField
                }
            }
        }
        override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) {}
    }

    // Countdown / Timer tracker
    private var countDownTimer: android.os.CountDownTimer? = null
    private var isTimerCountingDown = false

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {}
        override fun onDisplayRemoved(displayId: Int) {}
        override fun onDisplayChanged(displayId: Int) {
            val newRotation = getDisplayRotation()
            if (newRotation != displayRotation) {
                displayRotation = newRotation
                Log.d("DisplayListener", "Rotation changed to $displayRotation")
                adjustGpsOverlayPosition(displayRotation)
                updateWatermarkModeText()
            }
        }
    }

    /**
     * Keep system navigation buttons visible at all times with a 100% transparent background (Edge-to-Edge).
     */
    fun setupTransparentSystemUI() {
        try {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.navigationBarColor = Color.TRANSPARENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
            val decor = window.peekDecorView()
            if (decor != null) {
                WindowCompat.getInsetsController(window, decor).show(WindowInsetsCompat.Type.navigationBars())
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in setupTransparentSystemUI: ${e.message}")
        }
    }

    fun hideSystemUI() {
        setupTransparentSystemUI()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        // Load initial visitMode from SharedPreferences
        val sharedPrefs = getSharedPreferences("com.amod.geotagcamera.PREFERENCES", Context.MODE_PRIVATE)
        visitMode = sharedPrefs.getBoolean("visit_mode", false)

        // Load camera settings preferences
        cameraRatioSetting = sharedPrefs.getString("camera_ratio", "4:3") ?: "4:3"
        cameraGridSetting = sharedPrefs.getString("camera_grid", "OFF") ?: "OFF"
        cameraTimerSetting = sharedPrefs.getInt("camera_timer", 0)
        cameraFocusSetting = sharedPrefs.getString("camera_focus", "AUTO") ?: "AUTO"
        cameraMirrorSetting = sharedPrefs.getBoolean("camera_mirror", false)
        cameraSoundSetting = sharedPrefs.getBoolean("camera_sound", true)
        cameraWbSetting = sharedPrefs.getString("camera_wb", "AUTO") ?: "AUTO"
        cameraLevelSetting = sharedPrefs.getBoolean("camera_level", false)
        cameraTextSetting = sharedPrefs.getString("camera_text", "") ?: ""
        cameraVoiceSetting = sharedPrefs.getBoolean("camera_voice", true)
        cameraFlashSetting = sharedPrefs.getString("camera_flash", "Auto") ?: "Auto"
        cameraStampSetting = sharedPrefs.getBoolean("camera_stamp", true)
        cameraTelemetrySetting = sharedPrefs.getBoolean("camera_telemetry", true)
        cameraTimeFormatSetting = sharedPrefs.getString("camera_time_format", "12H") ?: "12H"

        val orientation = resources.configuration.orientation
        val orientationStr = if (orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) "LANDSCAPE" else "PORTRAIT"
        Log.d("MainActivity", "onCreate called - Orientation: $orientationStr, isIntroShown: $isIntroShown")

        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        permissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
                val cameraGranted = perms[Manifest.permission.CAMERA] ?: (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
                val fineGranted = perms[Manifest.permission.ACCESS_FINE_LOCATION] ?: (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
                val coarseGranted = perms[Manifest.permission.ACCESS_COARSE_LOCATION] ?: (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)
                
                if (cameraGranted && (fineGranted || coarseGranted)) {
                    Log.d("MainActivity", "Required permissions granted via launcher")
                    startCamera()
                    getLocation()
                    handler.removeCallbacks(updateRunnable)
                    handler.post(updateRunnable)
                } else {
                    Toast.makeText(this, "Camera and Location permissions are required for GPS overlay features.", Toast.LENGTH_LONG).show()
                }
            }

        gpsOverlayRenderer = GpsOverlayRenderer(this)
        displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        setupOrientationListener()

        isIntroShown = true
        setContentView(R.layout.activity_main)
        setupTransparentSystemUI()
        checkInternetAndInitialize()

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

    private fun endIntro() {
        isIntroShown = true
        setContentView(R.layout.activity_main)
        checkInternetAndInitialize()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUI()
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d("MainActivity", "onResume called")
        hideSystemUI()
        
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
        
        // Initialize and register sensors (accelerometer & magnetometer for compass & leveling)
        if (sensorManager == null) {
            sensorManager = getSystemService(Context.SENSOR_SERVICE) as android.hardware.SensorManager
            accelerometer = sensorManager?.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)
            magnetometer = sensorManager?.getDefaultSensor(android.hardware.Sensor.TYPE_MAGNETIC_FIELD)
        }
        accelerometer?.let {
            sensorManager?.registerListener(sensorListener, it, android.hardware.SensorManager.SENSOR_DELAY_UI)
        }
        magnetometer?.let {
            sensorManager?.registerListener(sensorListener, it, android.hardware.SensorManager.SENSOR_DELAY_UI)
        }
        
        // Immediate UI refresh using cached address and location
        val currentMapType = MapType.getSelectedMapType(this)
        if (lastFetchedMapType != null && lastFetchedMapType != currentMapType) {
            lastOverlayMapThumbnail = null
        }
        val noteConfig = CustomNoteConfig.load(this)
        cameraTextSetting = noteConfig.text
        gpsOverlayRenderer.customNote = noteConfig.text.ifBlank { null }
        gpsOverlayRenderer.customNoteConfig = noteConfig
        updateLiveCustomNoteView(noteConfig)
        updateLiveOverlay()
        
        // Fetch location instantly on resume/return
        if (hasCriticalPermissions()) {
            getLocation()
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
        sensorManager?.unregisterListener(sensorListener)
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
        try {
            if (::previewView.isInitialized && previewView.parent != null) {
                displayRotation = getDisplayRotation()
                adjustGpsOverlayPosition(displayRotation)
                updateLiveOverlay()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error handling configuration change: ${e.message}")
        }
    }

    private fun setupOrientationListener() {
        orientationEventListener = object : OrientationEventListener(this) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return

                val correctedRotation = when (orientation) {
                    in 45..134 -> Surface.ROTATION_270
                    in 135..224 -> Surface.ROTATION_180
                    in 225..314 -> Surface.ROTATION_90
                    else -> Surface.ROTATION_0
                }

                if (correctedRotation != displayRotation) {
                    displayRotation = correctedRotation
                    Log.d("OrientationListener", "Rotation changed to $displayRotation")
                    adjustGpsOverlayPosition(displayRotation)
                    updateWatermarkModeText()
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

    private fun isLandscapeRotation(rotation: Int): Boolean {
        return rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270
    }

    private fun isCurrentLandscape(): Boolean {
        return isLandscapeRotation(displayRotation) ||
                resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    }

    private fun isLandscapeLayout(): Boolean {
        return isCurrentLandscape()
    }

    private fun updateWatermarkModeText() {
        val liveModeWatermarkBeforeBarcode = findViewById<TextView>(R.id.liveModeWatermarkBeforeBarcode)
        val liveModeWatermarkCorner = findViewById<TextView>(R.id.liveModeWatermarkCorner)
        val isLandscape = isCurrentLandscape()
        val orientationStr = if (isLandscape) "Landscape Mode" else "Portrait Mode"
        val cameraLabel = if (isBackCamera) "Rear Camera" else "Front Camera"
        val watermarkStamp = if (cameraStampSetting) "$orientationStr • $cameraLabel" else orientationStr

        val lat = currentLocation?.latitude
        val lon = currentLocation?.longitude
        val selectedTemplate = OverlayTemplate.getSelectedTemplate(this)
        val isBarcodeTemplate = (selectedTemplate == OverlayTemplate.SCAN_LOCATION && lat != null && lon != null)
        if (isBarcodeTemplate) {
            liveModeWatermarkBeforeBarcode?.text = watermarkStamp
            liveModeWatermarkBeforeBarcode?.visibility = View.VISIBLE
            liveModeWatermarkCorner?.visibility = View.GONE
        } else {
            liveModeWatermarkCorner?.text = watermarkStamp
            liveModeWatermarkCorner?.visibility = View.VISIBLE
            liveModeWatermarkBeforeBarcode?.visibility = View.GONE
        }
    }

    private enum class OverlayAnchor { BOTTOM_RIGHT, BOTTOM_LEFT, TOP_RIGHT, TOP_LEFT, CENTER }
    private var currentAnchor = OverlayAnchor.BOTTOM_RIGHT

    private fun adjustGpsOverlayPosition(rotation: Int) {
        // Update ImageCapture rotation to match physical device rotation
        if (::imageCapture.isInitialized) {
            imageCapture.targetRotation = rotation
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
            val overlayWrapper = findViewById<LinearLayout>(R.id.gpsOverlayWrapper) ?: return@runOnUiThread
            val overlayCard = findViewById<MaterialCardView>(R.id.gpsOverlayCard) ?: return@runOnUiThread
            val buttonLayout = findViewById<View>(R.id.buttonLayout) ?: return@runOnUiThread
            val contentLayout = findViewById<LinearLayout>(R.id.gpsOverlayContent) ?: return@runOnUiThread
            val textContainer = findViewById<LinearLayout>(R.id.gpsOverlayTextContainer) ?: return@runOnUiThread

            // Ensure Horizontal Layout
            contentLayout.orientation = LinearLayout.HORIZONTAL
            
            // Update content layout params to match parent
            val contentParams = (contentLayout.layoutParams as? LinearLayout.LayoutParams)
                ?: LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            contentParams.width = LinearLayout.LayoutParams.MATCH_PARENT
            contentParams.height = LinearLayout.LayoutParams.WRAP_CONTENT
            contentLayout.layoutParams = contentParams

            val isLandscape = isCurrentLandscape()
            val isPortrait = !isLandscape
            val density = resources.displayMetrics.density

            // Reset transforms
            overlayWrapper.scaleX = 1.0f
            overlayWrapper.scaleY = 1.0f
            overlayWrapper.rotation = 0f
            overlayWrapper.translationX = 0f
            overlayWrapper.translationY = 0f

            // 1. Maintain Pristine Fixed Square Map & QR Code Size
            val mapCard = findViewById<View>(R.id.liveMapThumbnailCard)
            val qrCard = findViewById<View>(R.id.liveQrCodeCard)
            val mapSizePx = (if (isLandscape) 82 * density else 80 * density).toInt()
            val qrSizePx = (if (isLandscape) 78 * density else 78 * density).toInt()
            val compassSizePx = (if (isLandscape) 76 * density else 72 * density).toInt()

            mapCard?.layoutParams?.let { params ->
                params.width = mapSizePx
                params.height = mapSizePx
                mapCard.layoutParams = params
            }
            qrCard?.layoutParams?.let { params ->
                params.width = qrSizePx
                params.height = qrSizePx
                qrCard.layoutParams = params
            }
            findViewById<View>(R.id.liveCompassDial)?.layoutParams?.let { params ->
                params.width = compassSizePx
                params.height = compassSizePx
            }
            findViewById<View>(R.id.liveMiniMapThumbnailCard)?.layoutParams?.let { params ->
                params.width = compassSizePx
                params.height = compassSizePx
            }

            // 2. Adjust Text Sizes to look filled and clearly legible in Landscape
            val cityHeaderView = findViewById<TextView>(R.id.geo_city_header)
            val addrView = findViewById<TextView>(R.id.geo_address)
            val latLonView = findViewById<TextView>(R.id.geo_latlon)
            val dateTimeView = findViewById<TextView>(R.id.geo_datetime)
            val azimuthView = findViewById<TextView>(R.id.liveAzimuthText)
            val telemetryView = findViewById<TextView>(R.id.liveTelemetryText)
            val watermarkView = findViewById<TextView>(R.id.liveModeWatermarkBeforeBarcode)
            val watermarkCorner = findViewById<TextView>(R.id.liveModeWatermarkCorner)
            val bigTimeView = findViewById<TextView>(R.id.liveBigTime)
            val dateLineView = findViewById<TextView>(R.id.liveDateLine)
            val dayLineView = findViewById<TextView>(R.id.liveDayLine)

            if (isLandscape) {
                cityHeaderView?.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16.5f)
                addrView?.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f)
                latLonView?.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11.5f)
                dateTimeView?.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11.5f)
                azimuthView?.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 10.5f)
                telemetryView?.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 10.5f)
                watermarkView?.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 10f)
                watermarkCorner?.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 9.5f)
                bigTimeView?.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 20f)
                dateLineView?.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f)
                dayLineView?.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11f)
            } else {
                cityHeaderView?.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15.5f)
                addrView?.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11f)
                latLonView?.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 10.5f)
                dateTimeView?.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 10.5f)
                azimuthView?.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 10f)
                telemetryView?.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 10f)
                watermarkView?.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 9.5f)
                watermarkCorner?.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 8f)
                bigTimeView?.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 18f)
                dateLineView?.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11f)
                dayLineView?.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 10f)
            }

            // 3. Update text container params
            val updatedTextParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f).apply {
                marginStart = (10 * density).toInt()
                marginEnd = (6 * density).toInt()
                gravity = Gravity.CENTER_VERTICAL
            }
            textContainer.layoutParams = updatedTextParams

            val rootW = rootLayout.width
            val rootH = rootLayout.height
            val isWindowLandscape = rootW > rootH

            if (isWindowLandscape) {
                // System window is already in landscape mode. Reduced width (~0.68f) centered at the bottom
                overlayWrapper.rotation = 0f
                overlayWrapper.translationX = 0f
                overlayWrapper.translationY = 0f

                val targetWidth = (rootW * 0.68f).toInt()
                val set = ConstraintSet()
                set.clone(rootLayout)
                set.clear(R.id.gpsOverlayWrapper)
                
                // Position at the bottom of the landscape screen, centered
                set.connect(R.id.gpsOverlayWrapper, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM, (10 * density).toInt())
                set.connect(R.id.gpsOverlayWrapper, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, 0)
                set.connect(R.id.gpsOverlayWrapper, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END, 0)
                set.centerHorizontally(R.id.gpsOverlayWrapper, ConstraintSet.PARENT_ID)
                
                set.constrainWidth(R.id.gpsOverlayWrapper, targetWidth)
                set.constrainHeight(R.id.gpsOverlayWrapper, ConstraintSet.WRAP_CONTENT)
                set.applyTo(rootLayout)

                overlayWrapper.layoutParams.width = targetWidth
                overlayCard.layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
                overlayCard.requestLayout()
                overlayWrapper.requestLayout()
            } else if (rotation == Surface.ROTATION_0) {
                // PORTRAIT: Full width, positioned at the bottom above the zoom buttons
                overlayWrapper.rotation = 0f
                overlayWrapper.translationX = 0f
                overlayWrapper.translationY = 0f

                val set = ConstraintSet()
                set.clone(rootLayout)
                set.clear(R.id.gpsOverlayWrapper)
                val zoomContainer = findViewById<View>(R.id.zoomButtonContainer)
                if (zoomContainer != null && zoomContainer.visibility == View.VISIBLE) {
                    set.connect(R.id.gpsOverlayWrapper, ConstraintSet.BOTTOM, R.id.zoomButtonContainer, ConstraintSet.TOP, (12 * density).toInt())
                } else if (buttonLayout != null && buttonLayout.visibility == View.VISIBLE) {
                    set.connect(R.id.gpsOverlayWrapper, ConstraintSet.BOTTOM, R.id.buttonLayout, ConstraintSet.TOP, (12 * density).toInt())
                } else {
                    set.connect(R.id.gpsOverlayWrapper, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM, (12 * density).toInt())
                }
                set.connect(R.id.gpsOverlayWrapper, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, 0)
                set.connect(R.id.gpsOverlayWrapper, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END, 0)
                set.constrainWidth(R.id.gpsOverlayWrapper, ConstraintSet.MATCH_CONSTRAINT)
                set.constrainHeight(R.id.gpsOverlayWrapper, ConstraintSet.WRAP_CONTENT)
                set.applyTo(rootLayout)
                
                val lp = overlayWrapper.layoutParams as? ConstraintLayout.LayoutParams
                if (lp != null) {
                    lp.width = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
                    lp.height = ConstraintLayout.LayoutParams.WRAP_CONTENT
                    overlayWrapper.layoutParams = lp
                }
                overlayCard.layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
                overlayCard.requestLayout()
                overlayWrapper.requestLayout()
            } else {
                // LANDSCAPE IN PORTRAIT WINDOW:
                // Device physically rotated, but window is portrait.
                // Rotate overlay so text is right-side up, and shift to landscape bottom edge.
                val (screenEdgeLength, rotAngle) = when (rotation) {
                    Surface.ROTATION_90 -> Pair(rootH, 90f)      // 90 deg clockwise to be right-side up
                    Surface.ROTATION_270 -> Pair(rootH, -90f)   // 90 deg counter-clockwise to be right-side up
                    Surface.ROTATION_180 -> Pair(rootW, 180f)
                    else -> Pair(rootW, 0f)
                }

                val targetWidth = (screenEdgeLength * 0.68f).toInt() 
                overlayWrapper.layoutParams.width = targetWidth
                overlayWrapper.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                overlayCard.layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
                
                overlayWrapper.measure(
                    View.MeasureSpec.makeMeasureSpec(targetWidth, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                )
                val wrapperHeight = overlayWrapper.measuredHeight

                val set = ConstraintSet()
                set.clone(rootLayout)
                set.clear(R.id.gpsOverlayWrapper)
                set.centerHorizontally(R.id.gpsOverlayWrapper, ConstraintSet.PARENT_ID)
                set.centerVertically(R.id.gpsOverlayWrapper, ConstraintSet.PARENT_ID)
                set.constrainWidth(R.id.gpsOverlayWrapper, targetWidth)
                set.constrainHeight(R.id.gpsOverlayWrapper, wrapperHeight)
                set.applyTo(rootLayout)

                overlayWrapper.rotation = rotAngle
                
                val bottomMargin = 12 * density // Distance from physical bottom edge of screen in landscape
                val tx = when (rotation) {
                    Surface.ROTATION_90 -> -((rootW - wrapperHeight) / 2f - bottomMargin)
                    Surface.ROTATION_270 -> ((rootW - wrapperHeight) / 2f - bottomMargin)
                    else -> 0f
                }
                val ty = when (rotation) {
                    Surface.ROTATION_180 -> -((rootH - wrapperHeight) / 2f - (80 * density))
                    else -> -24 * density // slightly offset away from bottom shutter button
                }
                overlayWrapper.translationX = tx
                overlayWrapper.translationY = ty
                
                overlayCard.requestLayout()
                overlayWrapper.requestLayout()
            }

            updateWatermarkModeText()
        }
    }


    private fun hasCriticalPermissions(): Boolean {
        val hasCamera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val hasFineLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarseLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return hasCamera && (hasFineLocation || hasCoarseLocation)
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

    private fun getUltraWideCameraSelector(cameraProvider: ProcessCameraProvider): CameraSelector? {
        var bestCameraInfo: androidx.camera.core.CameraInfo? = null
        var minFocalLength = Float.MAX_VALUE

        for (cameraInfo in cameraProvider.availableCameraInfos) {
            val camera2Info = try {
                Camera2CameraInfo.from(cameraInfo)
            } catch (e: Exception) {
                continue
            }
            val lensFacing = camera2Info.getCameraCharacteristic(CameraCharacteristics.LENS_FACING)
            if (lensFacing == CameraCharacteristics.LENS_FACING_BACK) {
                val focalLengths = camera2Info.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                if (focalLengths != null && focalLengths.isNotEmpty()) {
                    val shortest = focalLengths.minOrNull() ?: Float.MAX_VALUE
                    if (shortest < minFocalLength) {
                        minFocalLength = shortest
                        bestCameraInfo = cameraInfo
                    }
                }
            }
        }

        // Standard wide-angle lenses have focal length of ~4.0mm to 4.5mm.
        // Ultra-wide angle lenses typically have a focal length < 3.2mm (commonly 1.5mm - 2.5mm).
        if (bestCameraInfo != null && minFocalLength < 3.2f) {
            return CameraSelector.Builder()
                .addCameraFilter { cameraInfos ->
                    cameraInfos.filter { it == bestCameraInfo }
                }
                .build()
        }

        // Secondary fallback checking minZoomRatio < 1.0f on any back camera
        for (cameraInfo in cameraProvider.availableCameraInfos) {
            val state = cameraInfo.zoomState.value
            if (state != null && state.minZoomRatio < 1.0f) {
                return CameraSelector.Builder()
                    .addCameraFilter { cameraInfos ->
                        cameraInfos.filter { it == cameraInfo }
                    }
                    .build()
            }
        }
        return null
    }

    private fun startCamera() {
        // Use COMPATIBLE (TextureView) mode to prevent SurfaceView buffer queue abandonment
        previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val ratioVal = if (cameraRatioSetting == "16:9") AspectRatio.RATIO_16_9 else AspectRatio.RATIO_4_3
            val preview = Preview.Builder()
                .setTargetAspectRatio(ratioVal)
                .build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
            val flashModeVal = when (cameraFlashSetting) {
                "On" -> ImageCapture.FLASH_MODE_ON
                "Auto" -> ImageCapture.FLASH_MODE_AUTO
                else -> ImageCapture.FLASH_MODE_OFF
            }
            val initialRot = if (displayRotation != Surface.ROTATION_0) displayRotation else getDisplayRotation()
            imageCapture = ImageCapture.Builder()
                .setTargetAspectRatio(ratioVal)
                .setFlashMode(flashModeVal)
                .setTargetRotation(initialRot)
                .build()

            val wideSelector = if (currentZoomRatio == 0.6f) getUltraWideCameraSelector(cameraProvider) else null
            if (currentZoomRatio == 0.6f && wideSelector == null) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Physical ultra-wide lens (0.6x) is not supported on this device. Using widest 1.0x view instead.", Toast.LENGTH_LONG).show()
                }
            }
            val cameraSelector = if (isBackCamera) {
                wideSelector ?: CameraSelector.DEFAULT_BACK_CAMERA
            } else {
                CameraSelector.DEFAULT_FRONT_CAMERA
            }

            isVideoSupported = false

            try {
                cameraProvider.unbindAll()
                
                // Bind use cases to camera (photo capture and viewfinder preview)
                val camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
                
                // Save camera reference for zoom control
                cameraControl = camera.cameraControl
                cameraInfo = camera.cameraInfo
                
                // Apply zoom ratio safely. On physical ultra-wide camera, base 1.0f zoom represents the widest (0.6x) field of view.
                val targetZoom = if (currentZoomRatio == 0.6f) 1.0f else currentZoomRatio
                try {
                    cameraControl?.setZoomRatio(targetZoom)
                } catch (e: Exception) {
                    Log.e("Camera", "Failed setting initial zoom: ${e.message}")
                }

                // Update UI based on video recording support
                runOnUiThread {
                    findViewById<ImageButton>(R.id.video_record_button)?.apply {
                        alpha = 0.6f
                    }
                }

                // Show .6x button to allow wide-angle zoom selection
                camera.cameraInfo.zoomState.observe(this@MainActivity) { state ->
                    runOnUiThread {
                        findViewById<TextView>(R.id.zoomButton06x)?.visibility = View.VISIBLE
                    }
                }

                updateLiveOverlay()
            } catch (e: Exception) {
                Log.e("Camera", "Use case binding failed", e)
                Toast.makeText(this, "Camera initialization failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun applyFlashSetting() {
        if (::imageCapture.isInitialized) {
            val mode = when (cameraFlashSetting) {
                "On" -> ImageCapture.FLASH_MODE_ON
                "Auto" -> ImageCapture.FLASH_MODE_AUTO
                else -> ImageCapture.FLASH_MODE_OFF
            }
            try {
                imageCapture.flashMode = mode
            } catch (e: Exception) {
                Log.w("Camera", "Failed to set flash mode: ${e.message}")
            }
        }
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

        val hasFine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (hasFine || hasCoarse) {
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
            Log.d("Location", "Location permission not granted inside getLocation() - waiting for user permission")
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
        val clean = fullAddress.replace("\n", ", ")
        val parts = clean.split(",").map { it.trim() }.filter { it.isNotBlank() }
        val header = when {
            parts.size >= 3 -> {
                val country = parts.last().replace(Regex("\\d+"), "").trim()
                val state = parts[parts.size - 2].replace(Regex("\\d+"), "").trim()
                val city = parts[parts.size - 3].replace(Regex("\\d+"), "").trim()
                listOf(city, state, country).filter { it.isNotBlank() }.joinToString(", ")
            }
            parts.size == 2 -> "${parts[0]}, ${parts[1]}"
            else -> clean
        }
        val isIndia = header.contains("India", ignoreCase = true) ||
                header.contains("भारत") ||
                header.contains("Gujarat", ignoreCase = true) ||
                header.contains("गुजरात") ||
                header.contains("Surat", ignoreCase = true) ||
                header.contains("सूरत")
        return if (isIndia && !header.contains("\uD83C\uDDEE\uD83C\uDDF3")) "$header \uD83C\uDDEE\uD83C\uDDF3" else header
    }

    /**
     * Clean and format street address for compact 1-2 line display
     */
    private fun formatCleanStreetAddress(fullAddress: String): String {
        if (fullAddress.isBlank() || fullAddress == "Fetching address..." || fullAddress == "GPS Details not fetched") {
            return "Fetching location..."
        }
        return fullAddress
            .replace("\n", ", ")
            .replace(Regex(",\\s*,"), ",")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trimEnd(',')
    }

    private fun updateLiveOverlay() {
        // Bring the overlay card to front
        val card = findViewById<MaterialCardView>(R.id.gpsOverlayCard) ?: return
        card.bringToFront()

        val selectedTemplate = OverlayTemplate.getSelectedTemplate(this)
        card.setCardBackgroundColor(Color.parseColor("#CC101418")) // deep charcoal dark translucent
        val textContainer = findViewById<View>(R.id.gpsOverlayTextContainer)
        textContainer?.setPadding(4, 0, 4, 0)

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

        val appLang = AppLanguage.getSelectedLanguage(this)

        // Bold city/state header with flag
        cityHeaderView.text = extractCityStateCountry(address)

        // Combined Lat/Long in one single line
        val lonLabel = if (appLang == AppLanguage.ENGLISH) "Long" else appLang.lonLabel
        val latLonText = if (lat != null && lon != null) {
            "${appLang.latLabel} %.6f°   $lonLabel %.6f°".format(lat, lon)
        } else {
            "${appLang.latLabel} --°   $lonLabel --°"
        }

        // Date/time in selected language locale on one single line
        val timePattern = if (cameraTimeFormatSetting == "24H") "EEEE, dd/MM/yyyy HH:mm:ss" else "EEEE, dd/MM/yyyy hh:mm a"
        val dateTimeText = SimpleDateFormat(timePattern, appLang.locale).format(Date())

        // Separate and format views cleanly (matching Image 1)
        val cleanAddr = formatCleanStreetAddress(address)
        addrView.text = cleanAddr
        latLonView.visibility = View.VISIBLE
        latLonView.text = latLonText
        dateTimeView.visibility = View.VISIBLE
        dateTimeView.text = dateTimeText

        // Apply highly dynamic live text autoscaling based on length & character count
        adjustLiveTextSizes(
            cityHeaderView, addrView, latLonView, dateTimeView,
            cityHeaderView.text.toString(), cleanAddr, latLonText, dateTimeText,
            selectedTemplate
        )

        // For backward compatibility with PDF/gallery overlay
        lastOverlayLatText = lat?.let { "%.6f".format(it) } ?: "--"
        lastOverlayLonText = lon?.let { "%.6f".format(it) } ?: "--"

        // Update state and refresh UI immediately for text
        lastOverlayAddrText = cleanAddr
        lastOverlayDateTime = dateTimeView.text.toString()

        // Configure template-specific views
        val badgeContainer = findViewById<View>(R.id.badgeContainer)
        val badgeText = findViewById<TextView>(R.id.badgeText)
        val liveDateTimeHeader = findViewById<View>(R.id.liveDateTimeHeader)
        val liveBigTime = findViewById<TextView>(R.id.liveBigTime)
        val liveDateLine = findViewById<TextView>(R.id.liveDateLine)
        val liveDayLine = findViewById<TextView>(R.id.liveDayLine)

        val liveMapThumbnailCard = findViewById<View>(R.id.liveMapThumbnailCard)
        val liveCheckInRibbon = findViewById<TextView>(R.id.liveCheckInRibbon)
        val liveCompassDial = findViewById<ImageView>(R.id.liveCompassDial)
        val liveQrCodeCard = findViewById<View>(R.id.liveQrCodeCard)
        val liveQrCode = findViewById<ImageView>(R.id.liveQrCode)
        val liveMiniMapThumbnailCard = findViewById<View>(R.id.liveMiniMapThumbnailCard)
        val liveMiniMapThumbnail = findViewById<ImageView>(R.id.liveMiniMapThumbnail)
        val liveAzimuthText = findViewById<TextView>(R.id.liveAzimuthText)
        val liveTelemetryText = findViewById<TextView>(R.id.liveTelemetryText)

        gpsOverlayRenderer.currentAzimuth = currentDeviceAzimuth
        gpsOverlayRenderer.currentAltitude = currentLocation?.altitude ?: 15.0
        gpsOverlayRenderer.currentMagneticField = currentDeviceMagneticField
        gpsOverlayRenderer.isBackCamera = isBackCamera
        gpsOverlayRenderer.showCameraStamp = cameraStampSetting

        val badgeTitle = "Ads Free GPS Cam Visit Pro"

        // Ensure "Ads Free" badge is visible across all templates as requested
        badgeContainer?.visibility = View.VISIBLE
        badgeText?.text = badgeTitle
        findViewById<ImageView>(R.id.badgeAppThumbnail)?.apply {
            setImageResource(R.mipmap.ic_launcher_round)
            visibility = View.VISIBLE
        }

        updateWatermarkModeText()

        when (selectedTemplate) {
            OverlayTemplate.DATETIME -> {
                liveDateTimeHeader?.visibility = View.VISIBLE
                liveMapThumbnailCard?.visibility = View.GONE
                liveCheckInRibbon?.visibility = View.GONE
                liveCompassDial?.visibility = View.GONE
                liveQrCodeCard?.visibility = View.GONE
                liveMiniMapThumbnailCard?.visibility = View.GONE
                liveAzimuthText?.visibility = View.GONE
                liveTelemetryText?.visibility = View.GONE

                val now = Date()
                liveBigTime?.text = SimpleDateFormat("hh:mm a", appLang.locale).format(now)
                liveDateLine?.text = SimpleDateFormat("dd MMMM yyyy", appLang.locale).format(now)
                liveDayLine?.text = SimpleDateFormat("EEEE", appLang.locale).format(now)

                // Fill card space generously
                addrView.maxLines = 3
                dateTimeView.visibility = View.GONE
            }
            OverlayTemplate.SCAN_LOCATION -> {
                liveDateTimeHeader?.visibility = View.GONE
                liveMapThumbnailCard?.visibility = View.VISIBLE
                liveCheckInRibbon?.visibility = View.GONE
                liveCompassDial?.visibility = View.GONE
                liveMiniMapThumbnailCard?.visibility = View.GONE
                liveAzimuthText?.visibility = View.GONE
                liveTelemetryText?.visibility = View.GONE

                if (lat != null && lon != null) {
                    liveQrCodeCard?.visibility = View.VISIBLE
                    addrView.maxLines = 2
                    val qrBmp = QrCodeGenerator.generateQrCode("https://maps.google.com/?q=$lat,$lon", 240)
                    qrBmp?.let { liveQrCode?.setImageBitmap(it) }
                } else {
                    // If QR not available, expand text font and lines to occupy vacant space
                    liveQrCodeCard?.visibility = View.GONE
                    addrView.maxLines = 3
                }
            }
            OverlayTemplate.CLASSIC -> {
                liveDateTimeHeader?.visibility = View.GONE
                liveMapThumbnailCard?.visibility = View.VISIBLE
                liveCheckInRibbon?.visibility = View.GONE
                liveCompassDial?.visibility = View.GONE
                liveQrCodeCard?.visibility = View.GONE
                liveMiniMapThumbnailCard?.visibility = View.GONE
                liveAzimuthText?.visibility = View.GONE
                liveTelemetryText?.visibility = View.GONE

                // Use vacant barcode space
                addrView.maxLines = 3
            }
            OverlayTemplate.REPORTING -> {
                liveDateTimeHeader?.visibility = View.GONE
                liveMapThumbnailCard?.visibility = View.VISIBLE
                liveCheckInRibbon?.visibility = View.VISIBLE
                liveCheckInRibbon?.text = appLang.checkInLabel
                liveCompassDial?.visibility = View.GONE
                liveQrCodeCard?.visibility = View.GONE
                liveMiniMapThumbnailCard?.visibility = View.GONE
                liveAzimuthText?.visibility = View.GONE
                liveTelemetryText?.visibility = View.GONE

                // Use vacant barcode space
                addrView.maxLines = 3
            }
            OverlayTemplate.NAVIGATION_COMPASS -> {
                liveDateTimeHeader?.visibility = View.GONE
                liveMapThumbnailCard?.visibility = View.GONE
                liveCheckInRibbon?.visibility = View.GONE
                liveCompassDial?.visibility = View.VISIBLE
                liveQrCodeCard?.visibility = View.GONE
                liveMiniMapThumbnailCard?.visibility = View.VISIBLE
                liveAzimuthText?.visibility = View.VISIBLE
                liveTelemetryText?.visibility = View.VISIBLE

                addrView.maxLines = 2

                liveAzimuthText?.text = "${appLang.azimuthLabel} : %.2f°".format(Locale.US, currentDeviceAzimuth)
                val altitude = currentLocation?.altitude ?: 15.0
                liveTelemetryText?.text = "⛰️ %.0f m (${appLang.altLabel})       \uD83E\uDDF2 %.2f µT".format(Locale.US, altitude, currentDeviceMagneticField)

                try {
                    val dialBmp = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888)
                    val dialCanvas = Canvas(dialBmp)
                    CompassRenderer.drawCompassDial(dialCanvas, RectF(0f, 0f, 160f, 160f), currentDeviceAzimuth)
                    liveCompassDial?.setImageBitmap(dialBmp)
                } catch (e: Exception) {
                    // ignore
                }

                lastOverlayMapThumbnail?.let {
                    liveMiniMapThumbnail?.setImageBitmap(it)
                }
            }
            OverlayTemplate.LOCATION_WATERMARK -> {
                liveDateTimeHeader?.visibility = View.GONE
                liveMapThumbnailCard?.visibility = View.VISIBLE
                liveCheckInRibbon?.visibility = View.GONE
                liveCompassDial?.visibility = View.GONE
                liveQrCodeCard?.visibility = View.GONE
                liveMiniMapThumbnailCard?.visibility = View.GONE
                liveAzimuthText?.visibility = View.GONE
                liveTelemetryText?.visibility = View.GONE

                cityHeaderView.text = "📍 " + extractCityStateCountry(address).uppercase(Locale.US)
                addrView.maxLines = 2
                addrView.text = cleanAddr
                latLonView.visibility = View.VISIBLE
                latLonView.setTextColor(Color.parseColor("#80D8FF"))
                latLonView.text = latLonText
                dateTimeView.visibility = View.VISIBLE
                dateTimeView.text = dateTimeText

                lastOverlayMapThumbnail?.let {
                    mapImageView.setImageBitmap(it)
                }
            }
        }

        // Fetch and display static map thumbnail with distance threshold
        if (lat != null && lon != null) {
            val dist = FloatArray(1)
            Location.distanceBetween(lastMapLat, lastMapLon, lat, lon, dist)
            val currentMapType = MapType.getSelectedMapType(this)
            
            if (lastOverlayMapThumbnail == null || isUsingFallbackThumbnail || dist[0] > 5.0 || lastFetchedMapType != currentMapType) {
                lastFetchedMapType = currentMapType
                
                // Immediately update coordinates to prevent parallel triggers
                lastMapLat = lat
                lastMapLon = lon

                val zoom = currentMapType.defaultZoom
                val mapTypeParam = currentMapType.googleMapType

                val candidateKeys = listOf(
                    "AIzaSyCfJ2d9XnWCbbi2hMBoQLma19Pr8fVNeaU",
                    BuildConfig.MAPS_API_KEY,
                    "AIzaSyDBr0XfggiNMjgaqZXwJg4lDP-X9fHBtXY"
                ).distinct().filter { it.isNotBlank() }

                // Cancel previous job to prevent the thumbnail from changing multiple times on startup
                mapFetchJob?.cancel()
                mapFetchJob = lifecycleScope.launch {
                    // Debounce rapid successive updates at startup
                    kotlinx.coroutines.delay(450)

                    val bmp = withContext(Dispatchers.IO) {
                        var fetchedBmp: Bitmap? = null
                        val sha1 = getSigningCertificateSha1()

                        // 1. Try Google Static Maps with candidate keys
                        for (key in candidateKeys) {
                            val url = "https://maps.googleapis.com/maps/api/staticmap" +
                                    "?center=$lat,$lon" +
                                    "&zoom=$zoom&size=400x400&scale=2" +
                                    "&maptype=$mapTypeParam" +
                                    "&markers=color:red%7C$lat,$lon" +
                                    "&key=$key"

                            try {
                                // Try plain request first (standard working key works without headers)
                                val reqPlain = Request.Builder().url(url).build()
                                val respPlain = httpClient.newCall(reqPlain).execute()
                                if (respPlain.isSuccessful) {
                                    val b = respPlain.body?.byteStream()?.let { BitmapFactory.decodeStream(it) }
                                    if (b != null) {
                                        fetchedBmp = b
                                        break
                                    }
                                } else {
                                    // Try with Android Package and SHA-1 cert headers
                                    val reqAuth = Request.Builder()
                                        .url(url)
                                        .header("X-Android-Package", packageName)
                                        .header("X-Android-Cert", sha1)
                                        .build()
                                    val respAuth = httpClient.newCall(reqAuth).execute()
                                    if (respAuth.isSuccessful) {
                                        val b = respAuth.body?.byteStream()?.let { BitmapFactory.decodeStream(it) }
                                        if (b != null) {
                                            fetchedBmp = b
                                            break
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("MapFetch", "Error querying key $key: ${e.message}")
                            }
                        }

                        // 2. If Google Maps static API fails, fetch real live OSM tile for the exact coordinates
                        if (fetchedBmp == null) {
                            fetchedBmp = fetchOsmTileBitmap(lat, lon, zoom)
                        }

                        fetchedBmp
                    }

                    if (bmp != null) {
                        isUsingFallbackThumbnail = false
                        lastOverlayMapThumbnail = bmp
                        runOnUiThread {
                            mapImageView.setImageBitmap(bmp)
                            findViewById<ImageView>(R.id.liveMiniMapThumbnail)?.setImageBitmap(bmp)
                            mapImageView.invalidate()
                            mapImageView.requestLayout()
                        }
                    } else if (lastOverlayMapThumbnail == null) {
                        val fallback = getFallbackMapThumbnail(currentMapType)
                        isUsingFallbackThumbnail = true
                        lastOverlayMapThumbnail = fallback
                        runOnUiThread {
                            mapImageView.setImageBitmap(fallback)
                            findViewById<ImageView>(R.id.liveMiniMapThumbnail)?.setImageBitmap(fallback)
                            mapImageView.invalidate()
                        }
                    }
                }
            }
        }
        adjustGpsOverlayPosition(displayRotation)
    }

    private fun adjustLiveTextSizes(
        cityHeaderView: TextView,
        addrView: TextView,
        latLonView: TextView,
        dateTimeView: TextView,
        cityHeader: String,
        address: String,
        combinedLatLon: String,
        datetime: String,
        selectedTemplate: OverlayTemplate
    ) {
        val isLandscape = isLandscapeLayout()
        
        // Base sizes in SP depending on template visual real estate
        var baseHeaderSize = if (isLandscape) 16.5f else 15.5f
        var baseValueSize = if (isLandscape) 11.5f else 11.0f

        when (selectedTemplate) {
            OverlayTemplate.DATETIME -> {
                baseHeaderSize = if (isLandscape) 15.5f else 14.5f
                baseValueSize = if (isLandscape) 12.5f else 12.0f
            }
            OverlayTemplate.SCAN_LOCATION -> {
                baseHeaderSize = if (isLandscape) 13.5f else 12.5f
                baseValueSize = if (isLandscape) 10.5f else 10.0f
            }
            OverlayTemplate.NAVIGATION_COMPASS -> {
                baseHeaderSize = if (isLandscape) 12.5f else 11.5f
                baseValueSize = if (isLandscape) 10.0f else 9.5f
            }
            OverlayTemplate.LOCATION_WATERMARK -> {
                baseHeaderSize = if (isLandscape) 15.5f else 14.5f
                baseValueSize = if (isLandscape) 11.5f else 11.0f
            }
            else -> {
                // Classic and Reporting
                baseHeaderSize = if (isLandscape) 15.0f else 14.0f
                baseValueSize = if (isLandscape) 12.0f else 11.5f
            }
        }

        // 1. Fit Header based on length and width
        var headerSize = baseHeaderSize
        val headerLen = cityHeader.length
        if (headerLen > 35) {
            headerSize = (baseHeaderSize * 0.78f).coerceAtLeast(11.0f)
        } else if (headerLen > 24) {
            headerSize = (baseHeaderSize * 0.88f).coerceAtLeast(12.0f)
        } else if (headerLen < 15) {
            headerSize = baseHeaderSize * 1.12f
        }
        cityHeaderView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, headerSize)

        // 2. Fit Details based on length and width
        val latLonLen = combinedLatLon.length
        val datetimeLen = datetime.length
        val maxLen = maxOf(latLonLen, datetimeLen)

        var detailsSize = baseValueSize
        if (maxLen > 38) {
            detailsSize = (baseValueSize * 0.82f).coerceAtLeast(9.0f)
        } else if (maxLen > 28) {
            detailsSize = (baseValueSize * 0.90f).coerceAtLeast(9.5f)
        }

        // 3. Address Length Factor
        val addressLen = address.length
        if (addressLen > 110) {
            detailsSize = minOf(detailsSize, 8.8f)
        } else if (addressLen > 80) {
            detailsSize = minOf(detailsSize, 9.8f)
        } else if (addressLen < 40 && maxLen < 25) {
            detailsSize = baseValueSize * 1.12f
        }

        addrView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, detailsSize)
        latLonView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, detailsSize)
        dateTimeView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, detailsSize)

        // 4. Reduce Line Gap dynamically
        addrView.setLineSpacing(0f, 0.92f) // Tighten line height for address to prevent empty gaps
    }

    private fun getFallbackMapThumbnail(mapType: MapType): Bitmap {
        val width = 400
        val height = 400
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val drawable = ContextCompat.getDrawable(this, R.drawable.sample_map_thumb)
        drawable?.let {
            it.setBounds(0, 0, width, height)
            it.draw(canvas)
        }

        // Draw red pin marker
        val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.RED
            style = Paint.Style.FILL
            setShadowLayer(4f, 0f, 2f, Color.parseColor("#80000000"))
        }
        val centerPinX = width / 2f
        val centerPinY = height / 2f - 10f
        canvas.drawCircle(centerPinX, centerPinY, 14f, pinPaint)

        val innerPinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawCircle(centerPinX, centerPinY, 6f, innerPinPaint)

        val tipPath = Path().apply {
            moveTo(centerPinX - 10f, centerPinY + 8f)
            lineTo(centerPinX + 10f, centerPinY + 8f)
            lineTo(centerPinX, centerPinY + 28f)
            close()
        }
        canvas.drawPath(tipPath, pinPaint)

        // Draw "Google" or map tag
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setShadowLayer(3f, 1f, 1f, Color.BLACK)
        }
        canvas.drawText("Google", 16f, height - 16f, labelPaint)

        return bitmap
    }

    private fun getSigningCertificateSha1(): String {
        try {
            val packageInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                packageManager.getPackageInfo(packageName, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, android.content.pm.PackageManager.GET_SIGNATURES)
            }
            val signatures = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                packageInfo.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                packageInfo.signatures
            }
            if (signatures != null && signatures.isNotEmpty()) {
                val md = java.security.MessageDigest.getInstance("SHA-1")
                val publicKey = md.digest(signatures[0].toByteArray())
                val hexString = StringBuilder()
                for (i in publicKey.indices) {
                    val appendString = Integer.toHexString(0xFF and publicKey[i].toInt()).uppercase(java.util.Locale.US)
                    if (appendString.length == 1) hexString.append("0")
                    hexString.append(appendString)
                    if (i < publicKey.size - 1) {
                        hexString.append(":")
                    }
                }
                return hexString.toString()
            }
        } catch (e: Exception) {
            Log.e("Signature", "Failed to get SHA-1: ${e.message}")
        }
        return "2E:6F:5E:85:CE:DC:39:C2:BA:3C:26:F5:5E:46:72:37:61:BC:1C:34"
    }

    private fun fetchOsmTileBitmap(lat: Double, lon: Double, zoom: Int): Bitmap? {
        try {
            val z = zoom.coerceIn(1, 18)
            val n = 1 shl z
            val x = ((lon + 180.0) / 360.0 * n).toInt().coerceIn(0, n - 1)
            val latRad = Math.toRadians(lat)
            val y = ((1.0 - kotlin.math.ln(kotlin.math.tan(latRad) + 1.0 / kotlin.math.cos(latRad)) / Math.PI) / 2.0 * n).toInt().coerceIn(0, n - 1)
            val tileUrl = "https://tile.openstreetmap.org/$z/$x/$y.png"
            val request = Request.Builder()
                .url(tileUrl)
                .header("User-Agent", "GPSMapCamera/1.0 (Android; Location Overlay)")
                .build()
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val rawTile = response.body?.byteStream()?.let { BitmapFactory.decodeStream(it) }
                if (rawTile != null) {
                    val result = Bitmap.createScaledBitmap(rawTile, 400, 400, true)
                    val canvas = Canvas(result)
                    drawPinOnMap(canvas, 400, 400)
                    return result
                }
            }
        } catch (e: Exception) {
            Log.e("MapFetch", "OSM tile fetch failed: ${e.message}")
        }
        return null
    }

    private fun drawPinOnMap(canvas: Canvas, width: Int, height: Int) {
        val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.RED
            style = Paint.Style.FILL
            setShadowLayer(4f, 0f, 2f, Color.parseColor("#80000000"))
        }
        val centerPinX = width / 2f
        val centerPinY = height / 2f - 10f
        canvas.drawCircle(centerPinX, centerPinY, 14f, pinPaint)

        val innerPinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawCircle(centerPinX, centerPinY, 6f, innerPinPaint)

        val tipPath = Path().apply {
            moveTo(centerPinX - 10f, centerPinY + 8f)
            lineTo(centerPinX + 10f, centerPinY + 8f)
            lineTo(centerPinX, centerPinY + 28f)
            close()
        }
        canvas.drawPath(tipPath, pinPaint)
    }

    private fun capturePhoto() {
        if (cameraTimerSetting > 0 && !isTimerCountingDown) {
            startCountdownAndCapture()
            return
        }
        if (isTimerCountingDown) {
            cancelCountdown()
            return
        }
        capturePhotoActual()
    }

    private fun capturePhotoActual() {
        playShutterSound()

        val fileName = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
        val shutterLat = currentLocation?.latitude
        val shutterLon = currentLocation?.longitude
        val shutterAddr = address.ifBlank { "Address unavailable" }
        val shutterGpsText = if (shutterLat != null && shutterLon != null) {
            val addrLine = if (shutterAddr.isNotBlank() && shutterAddr != "Address unavailable" && shutterAddr != "Address unavailable (Offline)") "$shutterAddr\n" else ""
            "${addrLine}Lat %.5f, Long %.5f".format(shutterLat, shutterLon)
        } else {
            shutterAddr
        }

        val file = File(cacheDir, "$fileName.jpg")
        val output = ImageCapture.OutputFileOptions.Builder(file).build()

        val captureRotation = if (displayRotation != Surface.ROTATION_0) displayRotation else getDisplayRotation()
        imageCapture.targetRotation = captureRotation

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

                    val isLandscape = isCurrentLandscape()

                    // Ensure bitmap orientation matches physical capture mode:
                    // If device was held in landscape, the captured photo MUST be landscape (width >= height).
                    // If camera/driver produced a portrait bitmap or EXIF was missing/stripped, rotate it to landscape!
                    if (isLandscape && rotatedBitmap.width < rotatedBitmap.height) {
                        val fixAngle = if (displayRotation == Surface.ROTATION_270) 90f else 270f
                        val fixMatrix = Matrix().apply { postRotate(fixAngle) }
                        rotatedBitmap = Bitmap.createBitmap(
                            rotatedBitmap,
                            0,
                            0,
                            rotatedBitmap.width,
                            rotatedBitmap.height,
                            fixMatrix,
                            true
                        )
                    } else if (!isLandscape && rotatedBitmap.width > rotatedBitmap.height) {
                        // If device was held in portrait, ensure portrait orientation
                        val fixMatrix = Matrix().apply { postRotate(90f) }
                        rotatedBitmap = Bitmap.createBitmap(
                            rotatedBitmap,
                            0,
                            0,
                            rotatedBitmap.width,
                            rotatedBitmap.height,
                            fixMatrix,
                            true
                        )
                    }

                    // Aspect ratio 1:1 square crop handling
                    if (cameraRatioSetting == "1:1") {
                        val minSide = Math.min(rotatedBitmap.width, rotatedBitmap.height)
                        val xOffset = (rotatedBitmap.width - minSide) / 2
                        val yOffset = (rotatedBitmap.height - minSide) / 2
                        rotatedBitmap = Bitmap.createBitmap(
                            rotatedBitmap,
                            xOffset,
                            yOffset,
                            minSide,
                            minSide
                        )
                    }

                    // Mirror handling: selfie horizontal flip
                    val shouldMirror = !isBackCamera && cameraMirrorSetting
                    if (shouldMirror) {
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
                    val dateTimeText = findViewById<TextView>(R.id.geo_datetime)?.text?.toString() ?: SimpleDateFormat("EEEE, dd/MM/yyyy hh:mm a", Locale.getDefault()).format(Date())

                    // Apply GPS overlay using on-screen values
                    gpsOverlayRenderer.layoutMode = if (isLandscape) GpsOverlayRenderer.LayoutMode.VERTICAL else GpsOverlayRenderer.LayoutMode.HORIZONTAL
                    gpsOverlayRenderer.currentAzimuth = currentDeviceAzimuth
                    gpsOverlayRenderer.currentAltitude = shutterLat?.let { currentLocation?.altitude } ?: 15.0
                    gpsOverlayRenderer.currentMagneticField = currentDeviceMagneticField
                    gpsOverlayRenderer.isBackCamera = isBackCamera
                    gpsOverlayRenderer.showCameraStamp = cameraStampSetting
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
    }

    private fun fetchAddress(location: Location) {
        lifecycleScope.launch {
            try {
                val addressList = withContext(Dispatchers.IO) {
                    val langLocale = AppLanguage.getSelectedLanguage(this@MainActivity).locale
                    Geocoder(this@MainActivity, langLocale)
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
                    address = lines.joinToString(", ").replace(Regex(",\\s*,"), ",").replace(Regex("\\s+"), " ").trim()
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
                        
                        val bitmap = try {
                            contentResolver.openInputStream(uri)?.use { stream ->
                                val options = BitmapFactory.Options().apply {
                                    inSampleSize = 8
                                }
                                BitmapFactory.decodeStream(stream, null, options)
                            }
                        } catch (e: Exception) {
                            Log.w("Gallery", "Could not decode thumbnail: ${e.message}")
                            null
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
        if (!isVideoSupported || !::videoCapture.isInitialized) {
            Toast.makeText(this, "Video recording is disabled on virtual emulator devices. Photo capture with GPS overlay is fully supported.", Toast.LENGTH_LONG).show()
            return
        }

        val fileName = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
        val tempFile = File(cacheDir, "raw_video_$fileName.mp4")
        val fileOutputOptions = androidx.camera.video.FileOutputOptions.Builder(tempFile).build()

        try {
            recording = videoCapture.output
                .prepareRecording(this, fileOutputOptions)
                .apply {
                    if (cameraVoiceSetting && ContextCompat.checkSelfPermission(
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

        // Layout is already set to activity_main
        hideSystemUI()

        @Suppress("DEPRECATION")
        window.decorView.setOnSystemUiVisibilityChangeListener { visibility ->
            if ((visibility and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) == 0) {
                window.decorView.postDelayed({
                    hideSystemUI()
                }, 2500)
            }
        }

        Log.d("MainActivity", "Finding viewFinder...")
        previewView = findViewById(R.id.viewFinder)
        Log.d("MainActivity", "viewFinder found: ${previewView != null}")
        previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        captureButton = findViewById(R.id.camera_capture_button)

        // Apply loaded settings to overlay views
        findViewById<com.amod.geotagcamera.ui.CameraGridView>(R.id.cameraGridView)?.let { gridView ->
            gridView.setGridMode(cameraGridSetting)
            gridView.setLevelEnabled(cameraLevelSetting)
            gridView.ratioSetting = cameraRatioSetting
        }
        val initialNoteConfig = CustomNoteConfig.load(this)
        cameraTextSetting = initialNoteConfig.text
        gpsOverlayRenderer.customNote = initialNoteConfig.text.ifBlank { null }
        gpsOverlayRenderer.customNoteConfig = initialNoteConfig
        updateLiveCustomNoteView(initialNoteConfig)

        findViewById<ImageButton>(R.id.switch_camera_button).setOnClickListener {
            isBackCamera = !isBackCamera
            startCamera()
        }
        findViewById<ImageButton>(R.id.camera_capture_button).setOnClickListener { capturePhoto() }
        // Video recording button
        findViewById<ImageButton>(R.id.video_record_button)?.setOnClickListener {
            if (!isVideoSupported || !::videoCapture.isInitialized) {
                Toast.makeText(this, "Video recording is disabled on virtual emulator devices. Photo capture with GPS overlay is fully supported.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
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
        // GPS Lookup / Scanner button
        findViewById<ImageButton>(R.id.gpsLookupButton)?.setOnClickListener {
            val intent = Intent(this, com.amod.geotagcamera.ui.GpsLookupActivity::class.java)
            startActivity(intent)
        }
        // Timeline Button
        findViewById<ImageButton>(R.id.timelineButton)?.setOnClickListener {
            val intent = Intent(this, com.amod.geotagcamera.ui.TimelineActivity::class.java)
            startActivity(intent)
        }
        // Settings button
        findViewById<ImageButton>(R.id.settingsButton)?.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }
        // Template Selection button
        findViewById<ImageButton>(R.id.templateButton)?.setOnClickListener {
            val intent = Intent(this, TemplateSelectionActivity::class.java)
            startActivity(intent)
        }
        // Quick Options button
        findViewById<ImageButton>(R.id.quickOptionsButton)?.setOnClickListener {
            showCameraOptionsDialog()
        }

        // Dynamically adjust top button margins so they never coincide with notification panel
        val mainRoot = findViewById<View>(R.id.rootContainer)
        if (mainRoot != null) {
            ViewCompat.setOnApplyWindowInsetsListener(mainRoot) { _, insets ->
                val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
                val safeTop = if (statusBarHeight > 0) statusBarHeight + dp(12) else dp(52)

                listOf(
                    findViewById<View>(R.id.quickOptionsButton),
                    findViewById<View>(R.id.gpsLookupButton),
                    findViewById<View>(R.id.timelineButton),
                    findViewById<View>(R.id.templateButton),
                    findViewById<View>(R.id.settingsButton)
                ).forEach { btn ->
                    val lp = btn?.layoutParams as? androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
                    if (lp != null && lp.topMargin != safeTop) {
                        lp.topMargin = safeTop
                        btn.layoutParams = lp
                    }
                }
                insets
            }
        }
        // Instagram-style Custom Note interactive sticker setup
        setupLiveCustomNoteSticker()

        // Collage button
        findViewById<ImageButton>(R.id.collage_button)?.setOnClickListener {
            openCollagePhotoPicker()
        }

        // Zoom buttons setup
        setupZoomButtons()

        if (hasCriticalPermissions()) {
            startCamera()
            getLocation()
        } else {
            requestPermissions()
        }
        
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
            val previousRatio = currentZoomRatio
            currentZoomRatio = 0.6f
            if (previousRatio != 0.6f) {
                // Switch physical camera to ultra-wide
                startCamera()
            } else {
                try {
                    cameraControl?.setZoomRatio(1.0f)
                } catch (e: Exception) {
                    Log.e("Zoom", "Failed setting zoom: ${e.message}")
                }
            }
            updateZoomUI()
        }
        zoom1xBtn.setOnClickListener {
            val previousRatio = currentZoomRatio
            currentZoomRatio = 1.0f
            if (previousRatio == 0.6f) {
                // Switch physical camera back to standard
                startCamera()
            } else {
                try {
                    cameraControl?.setZoomRatio(1.0f)
                } catch (e: Exception) {
                    Log.e("Zoom", "Failed setting zoom: ${e.message}")
                }
            }
            updateZoomUI()
        }
        zoom2xBtn.setOnClickListener {
            val previousRatio = currentZoomRatio
            currentZoomRatio = 2.0f
            if (previousRatio == 0.6f) {
                // Switch physical camera back to standard
                startCamera()
            } else {
                try {
                    cameraControl?.setZoomRatio(2.0f)
                } catch (e: Exception) {
                    Log.e("Zoom", "Failed setting zoom: ${e.message}")
                }
            }
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
                                    val lat = latLong[0].toDouble()
                                    val lon = latLong[1].toDouble()
                                    var addressText = ""
                                    try {
                                        val langLocale = AppLanguage.getSelectedLanguage(this@MainActivity).locale
                                        val addressList = Geocoder(this@MainActivity, langLocale)
                                            .getFromLocation(lat, lon, 1)
                                        if (!addressList.isNullOrEmpty()) {
                                            val addr = addressList[0]
                                            val lines = (0..addr.maxAddressLineIndex).map { addr.getAddressLine(it) }.distinct()
                                            addressText = lines.joinToString("\n")
                                        }
                                    } catch (e: Exception) {
                                        Log.e("GPS_DEBUG", "Gallery reverse geocoding failed: ${e.message}")
                                    }

                                    @Suppress("DefaultLocale")
                                    gpsInfo = if (addressText.isNotBlank()) {
                                        "$addressText\nLat %.5f, Long %.5f".format(lat, lon)
                                    } else {
                                        "Lat %.5f, Long %.5f".format(lat, lon)
                                    }
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

    private fun isEmulator(): Boolean {
        if (Build.SUPPORTED_ABIS.any { it.contains("x86") }) {
            return true
        }

        val brand = Build.BRAND.lowercase(Locale.US)
        val device = Build.DEVICE.lowercase(Locale.US)
        val fingerprint = Build.FINGERPRINT.lowercase(Locale.US)
        val hardware = Build.HARDWARE.lowercase(Locale.US)
        val model = Build.MODEL.lowercase(Locale.US)
        val manufacturer = Build.MANUFACTURER.lowercase(Locale.US)
        val product = Build.PRODUCT.lowercase(Locale.US)
        val board = Build.BOARD.lowercase(Locale.US)
        val bootloader = Build.BOOTLOADER.lowercase(Locale.US)
        val host = Build.HOST.lowercase(Locale.US)
        val tags = Build.TAGS.lowercase(Locale.US)

        return (brand.startsWith("generic") && device.startsWith("generic"))
                || brand.startsWith("generic")
                || (brand.contains("google") && (model.contains("sdk") || product.contains("sdk")))
                || device.startsWith("generic")
                || device.contains("vsoc")
                || device.contains("cutf")
                || device.contains("cuttlefish")
                || device.contains("emulator")
                || device.contains("x86")
                || fingerprint.startsWith("generic")
                || fingerprint.startsWith("unknown")
                || fingerprint.contains("test-keys")
                || fingerprint.contains("cuttlefish")
                || fingerprint.contains("cutf")
                || fingerprint.contains("vbox")
                || fingerprint.contains("sdk")
                || hardware.contains("goldfish")
                || hardware.contains("ranchu")
                || hardware.contains("cutf")
                || hardware.contains("cuttlefish")
                || hardware.contains("qemu")
                || hardware.contains("virtual")
                || hardware.contains("x86")
                || model.contains("google_sdk")
                || model.contains("emulator")
                || model.contains("android sdk built for")
                || model.contains("cuttlefish")
                || model.contains("cvd")
                || model.contains("virtual")
                || model.contains("sdk")
                || model.contains("gphone")
                || manufacturer.contains("genymotion")
                || (manufacturer.contains("google") && (model.contains("sdk") || device.contains("vsoc") || product.contains("cf_") || product.contains("sdk")))
                || product.contains("sdk_google")
                || product.contains("google_sdk")
                || product.contains("sdk")
                || product.contains("sdk_x86")
                || product.contains("vbox86p")
                || product.contains("emulator")
                || product.contains("simulator")
                || product.contains("cuttlefish")
                || product.contains("cutf")
                || product.contains("cf_")
                || product.contains("aosp")
                || board.contains("cutf")
                || board.contains("vsoc")
                || board.contains("goldfish")
                || board.contains("ranchu")
                || bootloader.contains("qemu")
                || host.contains("android-build")
                || tags.contains("test-keys")
                || try { File("/dev/qemu_pipe").exists() } catch (_: Exception) { false }
                || try { File("/dev/goldfish_pipe").exists() } catch (_: Exception) { false }
                || try { File("/dev/socket/qemud").exists() } catch (_: Exception) { false }
    }

    private fun startCountdownAndCapture() {
        isTimerCountingDown = true
        val countdownText = findViewById<TextView>(R.id.countdownTextView)
        countdownText?.text = cameraTimerSetting.toString()
        countdownText?.visibility = View.VISIBLE
        
        countDownTimer = object : android.os.CountDownTimer((cameraTimerSetting * 1000).toLong(), 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsRemaining = (millisUntilFinished / 1000 + 1).toInt()
                countdownText?.text = secondsRemaining.toString()
                if (cameraSoundSetting) {
                    playTimerBeep()
                }
            }

            override fun onFinish() {
                isTimerCountingDown = false
                countdownText?.visibility = View.GONE
                capturePhotoActual()
            }
        }.start()
    }

    private fun cancelCountdown() {
        countDownTimer?.cancel()
        countDownTimer = null
        isTimerCountingDown = false
        val countdownText = findViewById<TextView>(R.id.countdownTextView)
        countdownText?.visibility = View.GONE
        Toast.makeText(this, "Timer cancelled", Toast.LENGTH_SHORT).show()
    }

    private fun showCameraOptionsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_camera_options, null)
        val builder = AlertDialog.Builder(this)
            .setView(dialogView)
        val dialog = builder.create()
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        
        // Initial refresh
        refreshCameraOptionsUI(dialogView)

        val sharedPrefs = getSharedPreferences("com.amod.geotagcamera.PREFERENCES", Context.MODE_PRIVATE)
        val editor = sharedPrefs.edit()

        // 1. Ratio Click Listener
        dialogView.findViewById<View>(R.id.card_ratio)?.setOnClickListener {
            cameraRatioSetting = when (cameraRatioSetting) {
                "4:3" -> "16:9"
                "16:9" -> "1:1"
                else -> "4:3"
            }
            editor.putString("camera_ratio", cameraRatioSetting).apply()
            
            // Apply immediately to viewfinder and startCamera
            findViewById<com.amod.geotagcamera.ui.CameraGridView>(R.id.cameraGridView)?.let { gridView ->
                gridView.ratioSetting = cameraRatioSetting
                gridView.invalidate()
            }
            startCamera()
            refreshCameraOptionsUI(dialogView)
        }

        // 2. Grid Click Listener
        dialogView.findViewById<View>(R.id.card_grid)?.setOnClickListener {
            cameraGridSetting = when (cameraGridSetting) {
                "OFF" -> "3X3"
                "3X3" -> "PHI"
                else -> "OFF"
            }
            editor.putString("camera_grid", cameraGridSetting).apply()
            
            findViewById<com.amod.geotagcamera.ui.CameraGridView>(R.id.cameraGridView)?.setGridMode(cameraGridSetting)
            refreshCameraOptionsUI(dialogView)
        }

        // 3. Timer Click Listener
        dialogView.findViewById<View>(R.id.card_timer)?.setOnClickListener {
            cameraTimerSetting = when (cameraTimerSetting) {
                0 -> 3
                3 -> 5
                5 -> 10
                else -> 0
            }
            editor.putInt("camera_timer", cameraTimerSetting).apply()
            refreshCameraOptionsUI(dialogView)
        }

        // 4. Focus Click Listener
        dialogView.findViewById<View>(R.id.card_focus)?.setOnClickListener {
            cameraFocusSetting = when (cameraFocusSetting) {
                "AUTO" -> "MANUAL"
                else -> "AUTO"
            }
            editor.putString("camera_focus", cameraFocusSetting).apply()
            
            // Camera focus adjustment
            if (cameraFocusSetting == "AUTO") {
                cameraControl?.cancelFocusAndMetering()
            } else {
                // Focus manually locked on center point
                try {
                    val factory = previewView.meteringPointFactory
                    val point = factory.createPoint(0.5f, 0.5f)
                    val action = androidx.camera.core.FocusMeteringAction.Builder(point).build()
                    cameraControl?.startFocusAndMetering(action)
                } catch (e: Exception) {
                    Log.e("Camera", "Failed to lock focus: ${e.message}")
                }
            }
            refreshCameraOptionsUI(dialogView)
        }

        // 5. Mirror Click Listener
        dialogView.findViewById<View>(R.id.card_mirror)?.setOnClickListener {
            cameraMirrorSetting = !cameraMirrorSetting
            editor.putBoolean("camera_mirror", cameraMirrorSetting).apply()
            refreshCameraOptionsUI(dialogView)
        }

        // 6. Sound Click Listener
        dialogView.findViewById<View>(R.id.card_sound)?.setOnClickListener {
            cameraSoundSetting = !cameraSoundSetting
            editor.putBoolean("camera_sound", cameraSoundSetting).apply()
            refreshCameraOptionsUI(dialogView)
        }

        // 7. White Balance Click Listener
        dialogView.findViewById<View>(R.id.card_wb)?.setOnClickListener {
            cameraWbSetting = when (cameraWbSetting) {
                "AUTO" -> "SUNNY"
                "SUNNY" -> "CLOUDY"
                "CLOUDY" -> "INCANDESCENT"
                "INCANDESCENT" -> "FLUORESCENT"
                else -> "AUTO"
            }
            editor.putString("camera_wb", cameraWbSetting).apply()
            
            // Apply control options to camera
            try {
                cameraControl?.let { ctrl ->
                    val c2ctrl = androidx.camera.camera2.interop.Camera2CameraControl.from(ctrl)
                    val reqOpts = androidx.camera.camera2.interop.CaptureRequestOptions.Builder()
                        .setCaptureRequestOption(android.hardware.camera2.CaptureRequest.CONTROL_AWB_MODE, when (cameraWbSetting) {
                            "SUNNY" -> android.hardware.camera2.CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT
                            "CLOUDY" -> android.hardware.camera2.CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT
                            "INCANDESCENT" -> android.hardware.camera2.CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT
                            "FLUORESCENT" -> android.hardware.camera2.CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT
                            else -> android.hardware.camera2.CaptureRequest.CONTROL_AWB_MODE_AUTO
                        })
                        .build()
                    c2ctrl.setCaptureRequestOptions(reqOpts)
                }
            } catch (e: Exception) {
                Log.e("Camera", "Failed setting AWB mode: ${e.message}")
            }
            refreshCameraOptionsUI(dialogView)
        }

        // 8. Level Click Listener
        dialogView.findViewById<View>(R.id.card_level)?.setOnClickListener {
            cameraLevelSetting = !cameraLevelSetting
            editor.putBoolean("camera_level", cameraLevelSetting).apply()
            
            // Register or unregister sensor event listener
            findViewById<com.amod.geotagcamera.ui.CameraGridView>(R.id.cameraGridView)?.setLevelEnabled(cameraLevelSetting)
            if (cameraLevelSetting) {
                if (sensorManager == null) {
                    sensorManager = getSystemService(Context.SENSOR_SERVICE) as android.hardware.SensorManager
                    accelerometer = sensorManager?.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)
                }
                sensorManager?.registerListener(sensorListener, accelerometer, android.hardware.SensorManager.SENSOR_DELAY_UI)
            } else {
                sensorManager?.unregisterListener(sensorListener)
            }
            refreshCameraOptionsUI(dialogView)
        }

        // 9. Add Text (Instagram Style Custom Note) Listener
        dialogView.findViewById<View>(R.id.card_addtext)?.setOnClickListener {
            dialog.dismiss()
            openInstagramTextEditor()
        }

        // 10. Voice Record Click Listener (Microphone setup)
        dialogView.findViewById<View>(R.id.card_voice)?.setOnClickListener {
            cameraVoiceSetting = !cameraVoiceSetting
            editor.putBoolean("camera_voice", cameraVoiceSetting).apply()
            refreshCameraOptionsUI(dialogView)
        }

        // 11. Flash Light Click Listener (Auto -> On -> Off -> Auto)
        dialogView.findViewById<View>(R.id.card_flash)?.setOnClickListener {
            cameraFlashSetting = when (cameraFlashSetting) {
                "Auto" -> "On"
                "On" -> "Off"
                else -> "Auto"
            }
            editor.putString("camera_flash", cameraFlashSetting).apply()
            applyFlashSetting()
            refreshCameraOptionsUI(dialogView)
            Toast.makeText(this, "Flash: $cameraFlashSetting", Toast.LENGTH_SHORT).show()
        }

        // 12. Camera Lens Click Listener (Rear <-> Front)
        dialogView.findViewById<View>(R.id.card_lens)?.setOnClickListener {
            isBackCamera = !isBackCamera
            startCamera()
            updateLiveOverlay()
            refreshCameraOptionsUI(dialogView)
            val lensName = if (isBackCamera) "Rear Camera" else "Front Camera"
            Toast.makeText(this, "Switched to $lensName", Toast.LENGTH_SHORT).show()
        }

        // 13. Camera Stamp Click Listener (Front or Rear Camera Stamp on Overlay)
        dialogView.findViewById<View>(R.id.card_stamp)?.setOnClickListener {
            cameraStampSetting = !cameraStampSetting
            editor.putBoolean("camera_stamp", cameraStampSetting).apply()
            gpsOverlayRenderer.showCameraStamp = cameraStampSetting
            updateLiveOverlay()
            refreshCameraOptionsUI(dialogView)
            val state = if (cameraStampSetting) "Stamp Enabled" else "Stamp Disabled"
            Toast.makeText(this, "Camera Stamp: $state", Toast.LENGTH_SHORT).show()
        }

        // 14. Telemetry Click Listener (Altitude & Azimuth bearing on Overlay)
        dialogView.findViewById<View>(R.id.card_telemetry)?.setOnClickListener {
            cameraTelemetrySetting = !cameraTelemetrySetting
            editor.putBoolean("camera_telemetry", cameraTelemetrySetting).apply()
            updateLiveOverlay()
            refreshCameraOptionsUI(dialogView)
            val state = if (cameraTelemetrySetting) "Telemetry Enabled" else "Telemetry Disabled"
            Toast.makeText(this, "Telemetry: $state", Toast.LENGTH_SHORT).show()
        }

        // 15. Time Format Click Listener (12H <-> 24H)
        dialogView.findViewById<View>(R.id.card_time_format)?.setOnClickListener {
            cameraTimeFormatSetting = if (cameraTimeFormatSetting == "12H") "24H" else "12H"
            editor.putString("camera_time_format", cameraTimeFormatSetting).apply()
            updateLiveOverlay()
            refreshCameraOptionsUI(dialogView)
            Toast.makeText(this, "Time Format: $cameraTimeFormatSetting", Toast.LENGTH_SHORT).show()
        }

        // Close button listener
        dialogView.findViewById<View>(R.id.btn_close_options)?.setOnClickListener {
            dialog.dismiss()
        }

        dialog.setOnDismissListener {
            hideSystemUI()
        }

        dialog.show()

        // Position horizontally across the top of the screen in rectangular form
        dialog.window?.let { window ->
            window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            window.setGravity(android.view.Gravity.TOP)
            window.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
            val lp = window.attributes
            lp.width = android.view.WindowManager.LayoutParams.MATCH_PARENT
            lp.height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            lp.gravity = android.view.Gravity.TOP
            lp.x = 0
            lp.y = 0
            
            // Modern background blur support for API 31+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                lp.blurBehindRadius = 40
            }
            
            window.attributes = lp
            window.setWindowAnimations(R.style.TopDialogAnimation)
            window.setDimAmount(0.35f)
        }
    }

    private fun setOptionState(view: View, iconId: Int, valId: Int, text: String, isActive: Boolean) {
        val iconView = view.findViewById<ImageView>(iconId)
        val textView = view.findViewById<TextView>(valId)
        
        textView?.text = text
        
        val color = if (isActive) Color.parseColor("#FFC107") else Color.parseColor("#FFFFFF")
        textView?.setTextColor(color)
        iconView?.imageTintList = android.content.res.ColorStateList.valueOf(color)
    }

    private fun refreshCameraOptionsUI(view: View) {
        // 1. Ratio
        setOptionState(
            view, 
            R.id.icon_ratio, 
            R.id.val_ratio, 
            "Ratio $cameraRatioSetting", 
            cameraRatioSetting != "4:3"
        )

        // 2. Grid
        setOptionState(
            view, 
            R.id.icon_grid, 
            R.id.val_grid, 
            "Grid ${if (cameraGridSetting == "OFF") "Off" else cameraGridSetting}", 
            cameraGridSetting != "OFF"
        )

        // 3. Timer
        setOptionState(
            view, 
            R.id.icon_timer, 
            R.id.val_timer, 
            "Timer ${if (cameraTimerSetting == 0) "Off" else "${cameraTimerSetting}s"}", 
            cameraTimerSetting != 0
        )

        // 4. Focus
        setOptionState(
            view, 
            R.id.icon_focus, 
            R.id.val_focus, 
            "Focus ${if (cameraFocusSetting == "AUTO") "Auto" else "Manual"}", 
            cameraFocusSetting != "AUTO"
        )

        // 5. Mirror
        setOptionState(
            view, 
            R.id.icon_mirror, 
            R.id.val_mirror, 
            "Mirror ${if (cameraMirrorSetting) "On" else "Off"}", 
            cameraMirrorSetting
        )

        // 6. Sound
        setOptionState(
            view, 
            R.id.icon_sound, 
            R.id.val_sound, 
            "Sound ${if (cameraSoundSetting) "On" else "Off"}", 
            cameraSoundSetting
        )

        // 7. White Balance
        setOptionState(
            view, 
            R.id.icon_wb, 
            R.id.val_wb, 
            if (cameraWbSetting == "AUTO") "White Balance" else "WB ${cameraWbSetting.lowercase(Locale.US).replaceFirstChar { it.uppercase(Locale.US) }}", 
            cameraWbSetting != "AUTO"
        )

        // 8. Camera Level
        setOptionState(
            view, 
            R.id.icon_level, 
            R.id.val_level, 
            "Camera Level", 
            cameraLevelSetting
        )

        // 9. Add Text
        val currentNote = CustomNoteConfig.load(this)
        setOptionState(
            view, 
            R.id.icon_addtext, 
            R.id.val_addtext, 
            "Add Text", 
            currentNote.text.isNotBlank()
        )

        // 10. Record Video with Voice
        setOptionState(
            view, 
            R.id.icon_voice, 
            R.id.val_voice, 
            "Record Video with Voice", 
            cameraVoiceSetting
        )

        // 11. Flash Light
        setOptionState(
            view, 
            R.id.icon_flash, 
            R.id.val_flash, 
            "Flash $cameraFlashSetting", 
            cameraFlashSetting != "Off"
        )

        // 12. Camera Lens
        setOptionState(
            view, 
            R.id.icon_lens, 
            R.id.val_lens, 
            if (isBackCamera) "Rear Camera" else "Front Camera", 
            !isBackCamera
        )

        // 13. Camera Stamp
        setOptionState(
            view, 
            R.id.icon_stamp, 
            R.id.val_stamp, 
            if (cameraStampSetting) "Stamp On" else "Stamp Off", 
            cameraStampSetting
        )

        // 14. GPS Telemetry
        setOptionState(
            view, 
            R.id.icon_telemetry, 
            R.id.val_telemetry, 
            if (cameraTelemetrySetting) "Telemetry On" else "Telemetry Off", 
            cameraTelemetrySetting
        )

        // 15. Time Format
        setOptionState(
            view, 
            R.id.icon_time_format, 
            R.id.val_time_format, 
            "Time $cameraTimeFormatSetting", 
            cameraTimeFormatSetting == "24H"
        )
    }

    private var isStickerSelected: Boolean = false
    private var currentStickerConfig: CustomNoteConfig? = null

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN && isStickerSelected) {
            val container = findViewById<View>(R.id.liveCustomNoteContainer)
            if (container != null && container.visibility == View.VISIBLE) {
                val rect = Rect()
                container.getGlobalVisibleRect(rect)
                val padding = (24 * resources.displayMetrics.density).toInt()
                rect.inset(-padding, -padding)
                if (!rect.contains(ev.rawX.toInt(), ev.rawY.toInt())) {
                    setStickerSelected(false)
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun setStickerSelected(selected: Boolean) {
        isStickerSelected = selected
        val contentBox = findViewById<View>(R.id.stickerContentBox) ?: return
        val btnDelete = findViewById<View>(R.id.btnStickerDelete) ?: return
        val btnMirror = findViewById<View>(R.id.btnStickerMirror) ?: return
        val btnEdit = findViewById<View>(R.id.btnStickerEdit) ?: return
        val btnResize = findViewById<View>(R.id.btnStickerResize) ?: return

        if (selected) {
            contentBox.setBackgroundResource(R.drawable.sticker_border_selected)
            btnDelete.visibility = View.VISIBLE
            btnMirror.visibility = View.VISIBLE
            btnEdit.visibility = View.VISIBLE
            btnResize.visibility = View.VISIBLE
        } else {
            contentBox.background = null
            btnDelete.visibility = View.GONE
            btnMirror.visibility = View.GONE
            btnEdit.visibility = View.GONE
            btnResize.visibility = View.GONE
        }
    }

    private fun spacing(event: MotionEvent): Float {
        if (event.pointerCount < 2) return 0f
        val x = event.getX(0) - event.getX(1)
        val y = event.getY(0) - event.getY(1)
        return Math.hypot(x.toDouble(), y.toDouble()).toFloat()
    }

    private fun angle(event: MotionEvent): Double {
        if (event.pointerCount < 2) return 0.0
        val x = (event.getX(0) - event.getX(1)).toDouble()
        val y = (event.getY(0) - event.getY(1)).toDouble()
        return Math.toDegrees(Math.atan2(y, x))
    }

    private fun setupLiveCustomNoteSticker() {
        val container = findViewById<View>(R.id.liveCustomNoteContainer) ?: return
        val contentBox = findViewById<View>(R.id.stickerContentBox) ?: return
        val textView = findViewById<TextView>(R.id.liveCustomNoteText) ?: return
        val btnDelete = findViewById<View>(R.id.btnStickerDelete) ?: return
        val btnMirror = findViewById<View>(R.id.btnStickerMirror) ?: return
        val btnEdit = findViewById<View>(R.id.btnStickerEdit) ?: return
        val btnResize = findViewById<View>(R.id.btnStickerResize) ?: return

        setStickerSelected(false)

        // 1. Drag & Tap & Multi-touch on Content Box
        var startRawX = 0f
        var startRawY = 0f
        var startTransX = 0f
        var startTransY = 0f
        var isDraggingSticker = false
        var isPinching = false
        var startPinchDist = 0f
        var startPinchAngle = 0.0
        var startPinchSize = 20f
        var startPinchRot = 0f
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop

        contentBox.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startRawX = event.rawX
                    startRawY = event.rawY
                    startTransX = container.translationX
                    startTransY = container.translationY
                    isDraggingSticker = false
                    isPinching = false
                    setStickerSelected(true)
                    true
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    if (event.pointerCount >= 2) {
                        isPinching = true
                        startPinchDist = spacing(event)
                        startPinchAngle = angle(event)
                        val cfg = currentStickerConfig ?: CustomNoteConfig.load(this)
                        startPinchSize = cfg.textSizeSp
                        startPinchRot = container.rotation
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isPinching && event.pointerCount >= 2) {
                        val currentDist = spacing(event)
                        if (startPinchDist > 10f) {
                            val scale = currentDist / startPinchDist
                            val newSize = (startPinchSize * scale).coerceIn(12f, 72f)
                            textView.textSize = newSize
                            val cfg = currentStickerConfig ?: CustomNoteConfig.load(this)
                            cfg.textSizeSp = newSize
                        }

                        val currentAngle = angle(event)
                        val deltaAngle = (currentAngle - startPinchAngle).toFloat()
                        var newRot = (startPinchRot + deltaAngle) % 360f
                        if (newRot < 0f) newRot += 360f
                        if (newRot < 4f || newRot > 356f) newRot = 0f
                        else if (Math.abs(newRot - 90f) < 4f) newRot = 90f
                        else if (Math.abs(newRot - 180f) < 4f) newRot = 180f
                        else if (Math.abs(newRot - 270f) < 4f) newRot = 270f
                        container.rotation = newRot
                        val cfg = currentStickerConfig ?: CustomNoteConfig.load(this)
                        cfg.rotation = newRot
                    } else if (!isPinching) {
                        val dx = event.rawX - startRawX
                        val dy = event.rawY - startRawY
                        val dist = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
                        if (dist > touchSlop) {
                            isDraggingSticker = true
                        }
                        if (isDraggingSticker) {
                            container.translationX = startTransX + dx
                            container.translationY = startTransY + dy
                        }
                    }
                    true
                }
                MotionEvent.ACTION_POINTER_UP -> {
                    if (event.pointerCount <= 2) {
                        isPinching = false
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!isDraggingSticker && !isPinching) {
                        setStickerSelected(true)
                    } else {
                        val parentView = container.parent as? View
                        if (parentView != null && parentView.width > 0 && parentView.height > 0) {
                            val centerX = container.left + container.translationX + container.width / 2f
                            val centerY = container.top + container.translationY + container.height / 2f
                            val normX = (centerX / parentView.width).coerceIn(0.05f, 0.95f)
                            val normY = (centerY / parentView.height).coerceIn(0.05f, 0.95f)
                            val cfg = currentStickerConfig ?: CustomNoteConfig.load(this)
                            cfg.isCustomPositioned = true
                            cfg.normPosX = normX
                            cfg.normPosY = normY
                            CustomNoteConfig.save(this, cfg)
                            currentStickerConfig = cfg
                            gpsOverlayRenderer.customNoteConfig = cfg
                        }
                    }
                    isPinching = false
                    true
                }
                else -> false
            }
        }

        // 2. Top-Left: Delete button (Trash can)
        btnDelete.setOnClickListener {
            val cfg = currentStickerConfig ?: CustomNoteConfig.load(this)
            cfg.text = ""
            cfg.isMirrored = false
            CustomNoteConfig.save(this, cfg)
            currentStickerConfig = cfg
            gpsOverlayRenderer.customNote = null
            gpsOverlayRenderer.customNoteConfig = cfg
            cameraTextSetting = ""
            setStickerSelected(false)
            container.visibility = View.GONE
            Toast.makeText(this, "Text deleted", Toast.LENGTH_SHORT).show()
        }

        // 3. Top-Right: Mirror/Flip button
        btnMirror.setOnClickListener {
            val cfg = currentStickerConfig ?: CustomNoteConfig.load(this)
            cfg.isMirrored = !cfg.isMirrored
            contentBox.scaleX = if (cfg.isMirrored) -1f else 1f
            CustomNoteConfig.save(this, cfg)
            currentStickerConfig = cfg
            gpsOverlayRenderer.customNoteConfig = cfg
            val status = if (cfg.isMirrored) "Mirrored" else "Normal"
            Toast.makeText(this, "Text $status", Toast.LENGTH_SHORT).show()
        }

        // 4. Bottom-Left: Edit button (Pencil)
        btnEdit.setOnClickListener {
            openInstagramTextEditor()
        }

        // 5. Bottom-Right: Resize & Rotate handle
        var startDist = 0f
        var startAngle = 0.0
        var startSize = 20f
        var startRot = 0f

        btnResize.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val parentView = container.parent as? View
                    val parentLoc = IntArray(2)
                    parentView?.getLocationOnScreen(parentLoc)
                    val stickerCenterX = parentLoc[0] + container.x + container.pivotX
                    val stickerCenterY = parentLoc[1] + container.y + container.pivotY

                    val dx = event.rawX - stickerCenterX
                    val dy = event.rawY - stickerCenterY
                    startDist = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
                    startAngle = Math.toDegrees(Math.atan2(dy.toDouble(), dx.toDouble()))
                    val cfg = currentStickerConfig ?: CustomNoteConfig.load(this)
                    startSize = cfg.textSizeSp
                    startRot = container.rotation
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val parentView = container.parent as? View
                    val parentLoc = IntArray(2)
                    parentView?.getLocationOnScreen(parentLoc)
                    val stickerCenterX = parentLoc[0] + container.x + container.pivotX
                    val stickerCenterY = parentLoc[1] + container.y + container.pivotY

                    val dx = event.rawX - stickerCenterX
                    val dy = event.rawY - stickerCenterY
                    val currentDist = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
                    if (startDist > 10f) {
                        val scale = currentDist / startDist
                        val newSize = (startSize * scale).coerceIn(12f, 72f)
                        textView.textSize = newSize
                        val cfg = currentStickerConfig ?: CustomNoteConfig.load(this)
                        cfg.textSizeSp = newSize
                    }

                    val currentAngle = Math.toDegrees(Math.atan2(dy.toDouble(), dx.toDouble()))
                    val deltaAngle = (currentAngle - startAngle).toFloat()
                    var newRot = (startRot + deltaAngle) % 360f
                    if (newRot < 0f) newRot += 360f

                    // Snapping to cardinal angles within 4 degrees
                    if (newRot < 4f || newRot > 356f) newRot = 0f
                    else if (Math.abs(newRot - 90f) < 4f) newRot = 90f
                    else if (Math.abs(newRot - 180f) < 4f) newRot = 180f
                    else if (Math.abs(newRot - 270f) < 4f) newRot = 270f

                    container.rotation = newRot
                    val cfg = currentStickerConfig ?: CustomNoteConfig.load(this)
                    cfg.rotation = newRot
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val cfg = currentStickerConfig ?: CustomNoteConfig.load(this)
                    CustomNoteConfig.save(this, cfg)
                    currentStickerConfig = cfg
                    gpsOverlayRenderer.customNoteConfig = cfg
                    true
                }
                else -> false
            }
        }
    }

    private fun openInstagramTextEditor() {
        val currentConfig = CustomNoteConfig.load(this)

        // Blur live camera preview while editing text
        val previewView = findViewById<View>(R.id.viewFinder)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                previewView?.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(30f, 30f, android.graphics.Shader.TileMode.CLAMP))
            } catch (e: Exception) {}
        }

        val textEditorDialog = InstagramTextEditorDialog(this, currentConfig) { savedConfig ->
            cameraTextSetting = savedConfig.text
            gpsOverlayRenderer.customNote = savedConfig.text.ifBlank { null }
            gpsOverlayRenderer.customNoteConfig = savedConfig
            updateLiveCustomNoteView(savedConfig)
            updateLiveOverlay()
            if (savedConfig.text.isNotBlank()) {
                setStickerSelected(true)
            } else {
                setStickerSelected(false)
            }
            val msg = if (savedConfig.text.isBlank()) {
                "Text removed"
            } else {
                "Text added"
            }
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
        textEditorDialog.setOnDismissListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    previewView?.setRenderEffect(null)
                } catch (e: Exception) {}
            }
        }
        textEditorDialog.show()
    }

    private fun updateLiveCustomNoteView(config: CustomNoteConfig) {
        currentStickerConfig = config
        val container = findViewById<View>(R.id.liveCustomNoteContainer) ?: return
        val contentBox = findViewById<View>(R.id.stickerContentBox) ?: return
        val textView = findViewById<TextView>(R.id.liveCustomNoteText) ?: return

        if (config.text.isBlank() || config.position == NotePosition.INSIDE_OVERLAY.id) {
            container.visibility = View.GONE
            setStickerSelected(false)
            return
        }

        container.visibility = View.VISIBLE
        textView.text = config.text
        InstagramTextStyler.applyStyleToView(textView, config)
        container.rotation = config.rotation
        contentBox.scaleX = if (config.isMirrored) -1f else 1f
        setStickerSelected(true)

        val params = container.layoutParams as? androidx.constraintlayout.widget.ConstraintLayout.LayoutParams ?: return
        when (config.position) {
            NotePosition.TOP.id -> {
                params.topToTop = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
                params.bottomToBottom = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET
                params.bottomToTop = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET
                params.topMargin = (76 * resources.displayMetrics.density).toInt()
                params.bottomMargin = 0
            }
            NotePosition.CENTER.id -> {
                params.topToTop = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
                params.bottomToBottom = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
                params.bottomToTop = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET
                params.topMargin = 0
                params.bottomMargin = 0
            }
            else -> { // ABOVE_CARD
                params.topToTop = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET
                params.bottomToBottom = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET
                params.topToBottom = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET
                params.bottomToTop = R.id.gpsOverlayWrapper
                params.bottomMargin = (12 * resources.displayMetrics.density).toInt()
                params.topMargin = 0
            }
        }
        container.layoutParams = params

        if (config.isCustomPositioned) {
            container.post {
                val parentView = container.parent as? View ?: return@post
                if (parentView.width > 0 && parentView.height > 0) {
                    val targetCenterX = config.normPosX * parentView.width
                    val targetCenterY = config.normPosY * parentView.height
                    val currentCenterX = container.left + container.width / 2f
                    val currentCenterY = container.top + container.height / 2f
                    container.translationX = targetCenterX - currentCenterX
                    container.translationY = targetCenterY - currentCenterY
                }
            }
        } else {
            container.translationX = 0f
            container.translationY = 0f
        }
    }
}
