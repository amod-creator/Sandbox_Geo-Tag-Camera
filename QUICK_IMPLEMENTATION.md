# Quick Implementation Checklist for MainActivity.kt

## ✅ COMPLETED
1. API Key moved to local.properties
2. BuildConfig.MAPS_API_KEY implemented
3. Null safety checks added to updateLiveOverlay()
4. Network error handling with timeouts
5. CameraX Video dependencies added to build.gradle.kts

---

## 📝 REMAINING CODE TO ADD TO MainActivity.kt

### Step 1: Add Missing Imports (at the top of file)
Add these after the existing imports:

```kotlin
import android.location.LocationManager
import android.content.Context
import androidx.camera.video.*
import androidx.core.util.Consumer
```

### Step 2: Add Video Recording Variables (around line 120, after existing variables)

```kotlin
private var videoCapture: VideoCapture<Recorder>? = null
private var recording: Recording? = null
private var isRecordingVideo = false
```

### Step 3: Add GPS Check Functions (before getLocation() function)

```kotlin
private fun isGpsEnabled(): Boolean {
    val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
}

private fun promptEnableGps() {
    AlertDialog.Builder(this)
        .setTitle("GPS Required")
        .setMessage("GPS is disabled. Please enable GPS for accurate location tagging.")
        .setPositiveButton("Settings") { _, _ ->
            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        }
        .setNegativeButton("Cancel", null)
        .show()
}
```

### Step 4: Update getLocation() - Add GPS Check (first lines of function)

Replace the start of getLocation() with:

```kotlin
private fun getLocation() {
    // Check if GPS is enabled
    if (!isGpsEnabled()) {
        promptEnableGps()
        return
    }
    
    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
    // ... rest of existing code stays the same
```

### Step 5: Fix saveImageToGallery() - Remove Double Overlay

Replace the entire saveImageToGallery() function with:

```kotlin
private fun saveImageToGallery(bitmap: Bitmap, fileName: String) {
    // Check storage permission for Android 10+
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, "Storage permission required", Toast.LENGTH_SHORT).show()
            requestPermissions()
            return
        }
    }

    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "$fileName.jpg")
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/GPS Cam Visit Pro")
    }

    // FIX: Don't apply GPS overlay again - it's already applied
    val finalBitmap = bitmap

    try {
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        uri?.let {
            contentResolver.openOutputStream(it)?.use { out ->
                finalBitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
                Toast.makeText(this, "Image saved to gallery", Toast.LENGTH_SHORT).show()
            }
        } ?: run {
            Toast.makeText(this, "Failed to save image", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        Log.e("SaveImage", "Error saving image: ${e.message}")
        Toast.makeText(this, "Error saving image: ${e.message}", Toast.LENGTH_SHORT).show()
    }

    // Generate PDF
    try {
        val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val pdfDir = File(docsDir, "GPS Cam Visit Pro")
        if (!pdfDir.exists()) pdfDir.mkdirs()
        val pdfFile = File(pdfDir, "$fileName.pdf")
        
        val latText = findViewById<TextView>(R.id.geo_lat)?.text?.toString() ?: "Lat: --"
        val lonText = findViewById<TextView>(R.id.geo_lon)?.text?.toString() ?: "Lon: --"
        val addrText = findViewById<TextView>(R.id.geo_address)?.text?.toString() ?: ""
        val dateTimeText = findViewById<TextView>(R.id.geo_datetime)?.text?.toString() ?: ""
        val mapThumbView = findViewById<ImageView>(R.id.liveMapThumbnail)
        val mapThumbnail = (mapThumbView?.drawable as? BitmapDrawable)?.bitmap
        
        gpsOverlayRenderer.createPdfWithOverlay(
            finalBitmap, pdfFile, mapThumbnail, latText, lonText, addrText, dateTimeText
        )
    } catch (e: Exception) {
        Log.e("SavePDF", "Error generating PDF: ${e.message}")
    }
}
```

### Step 6: Update requestPermissions() - Add Audio Permission

Replace requestPermissions() with:

```kotlin
private fun requestPermissions() {
    val permissions = mutableListOf(
        Manifest.permission.CAMERA,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )
    
    // Add audio permission for video recording
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        permissions.add(Manifest.permission.RECORD_AUDIO)
    }
    
    permissionLauncher.launch(permissions.toTypedArray())
}
```

### Step 7: Add Video Recording Functions (add at end of class)

```kotlin
private fun startVideoCapture() {
    val videoCapture = this.videoCapture ?: return
    
    val name = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        .format(System.currentTimeMillis())
    val contentValues = ContentValues().apply {
        put(MediaStore.Video.Media.DISPLAY_NAME, "$name.mp4")
        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
        put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/GPS Cam Visit Pro")
    }
    
    val mediaStoreOutput = MediaStoreOutputOptions.Builder(
        contentResolver,
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    ).setContentValues(contentValues).build()
    
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
        .start(ContextCompat.getMainExecutor(this)) { recordEvent ->
            when (recordEvent) {
                is VideoRecordEvent.Start -> {
                    isRecordingVideo = true
                    runOnUiThread {
                        Toast.makeText(this, "Recording started", Toast.LENGTH_SHORT).show()
                    }
                }
                is VideoRecordEvent.Finalize -> {
                    isRecordingVideo = false
                    if (!recordEvent.hasError()) {
                        runOnUiThread {
                            Toast.makeText(
                                this,
                                "Video saved",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        addGpsMetadataToVideo(recordEvent.outputResults.outputUri, name)
                    } else {
                        recording?.close()
                        recording = null
                        Log.e("VideoCapture", "Error: ${recordEvent.error}")
                    }
                }
            }
        }
}

private fun stopVideoCapture() {
    recording?.stop()
    recording = null
}

private fun addGpsMetadataToVideo(videoUri: Uri, fileName: String) {
    try {
        val location = currentLocation ?: return
        val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val gpsDir = File(docsDir, "GPS Cam Visit Pro/Video Metadata")
        if (!gpsDir.exists()) gpsDir.mkdirs()
        
        val metadataFile = File(gpsDir, "$fileName.txt")
        val metadata = buildString {
            appendLine("Video GPS Metadata")
            appendLine("==================")
            appendLine("Filename: $fileName.mp4")
            appendLine("Latitude: ${location.latitude}")
            appendLine("Longitude: ${location.longitude}")
            appendLine("Address: $address")
            appendLine("Timestamp: ${SimpleDateFormat("dd/MM/yyyy hh:mm:ss a", Locale.getDefault()).format(Date())}")
        }
        metadataFile.writeText(metadata)
        MediaScannerConnection.scanFile(this, arrayOf(metadataFile.absolutePath), arrayOf("text/plain"), null)
    } catch (e: Exception) {
        Log.e("VideoMetadata", "Error: ${e.message}")
    }
}
```

### Step 8: Update startCamera() - Add Video Capability

Find the startCamera() function and update it to:

```kotlin
private fun startCamera() {
    previewView.implementationMode = PreviewView.ImplementationMode.PERFORMANCE
    val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
    cameraProviderFuture.addListener({
        val cameraProvider = cameraProviderFuture.get()
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }
        imageCapture = ImageCapture.Builder().build()
        
        // Add video capture
        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.HD))
            .build()
        videoCapture = VideoCapture.withOutput(recorder)
        
        val cameraSelector =
            if (isBackCamera) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
        
        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture, videoCapture)
            updateLiveOverlay()
        } catch (e: Exception) {
            Log.e("CameraX", "Use case binding failed", e)
        }
    }, ContextCompat.getMainExecutor(this))
}
```

---

## 🎯 Final Step: Add Video Button to Layout

Edit `app/src/main/res/layout/activity_main.xml` and add:

```xml
<ImageButton
    android:id="@+id/video_capture_button"
    android:layout_width="60dp"
    android:layout_height="60dp"
    android:src="@android:drawable/ic_media_play"
    android:contentDescription="Record Video"
    android:background="@android:color/transparent" />
```

Then in initCameraAndListeners(), add:

```kotlin
findViewById<ImageButton>(R.id.video_capture_button)?.setOnClickListener {
    if (isRecordingVideo) {
        stopVideoCapture()
        it.setImageResource(android.R.drawable.ic_media_play)
    } else {
        startVideoCapture()
        it.setImageResource(android.R.drawable.ic_media_pause)
    }
}
```

---

## ✅ All Done!

After applying these changes:
1. Sync Gradle
2. Build project
3. Test all features

Your app will have:
- ✅ Secure API key handling
- ✅ Null-safe code
- ✅ Network error handling
- ✅ GPS disabled detection
- ✅ Storage permission checks
- ✅ No double overlay bug
- ✅ Video recording with GPS metadata

