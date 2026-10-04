package com.amod.geotagcamera.collage

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.*
import android.location.Geocoder
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.util.Log
import android.view.*
import android.view.animation.OvershootInterpolator
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.amod.geotagcamera.R
import com.amod.geotagcamera.StaffInputActivity
import com.amod.geotagcamera.model.CustomNoteConfig
import com.amod.geotagcamera.ui.InstagramTextEditorDialog
import com.amod.geotagcamera.utils.ExifGpsExtractor
import com.amod.geotagcamera.utils.InstagramTextStyler
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/**
 * Complete Collage Editor V2 with:
 * - 20+ layout templates
 * - Drag-and-drop photo swapping
 * - 12 border presets, 12 backgrounds
 * - Ratio selector (1:1, 4:3, 3:4, 16:9, 9:16)
 * - Save / Share / Report with progress
 * - 3-level undo stack
 */
class CollageEditorActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "CollageEditorV2"
    }

    // ── State ──
    private var currentState = CollageState(emptyList())
    private val undoStack = mutableListOf<CollageState>()
    private val maxUndo = 3

    // ── Text Sticker State ──
    private var isStickerSelected = false
    private var currentStickerConfig: CustomNoteConfig? = null
    private lateinit var scaleGestureDetector: ScaleGestureDetector

    // ── Views ──
    private lateinit var preview: ImageView
    private lateinit var dragOverlay: View
    private lateinit var progressBar: ProgressBar
    private lateinit var optionsContainer: LinearLayout
    private lateinit var cellZoomControlPill: View
    private lateinit var btnCellZoomOut: ImageButton
    private lateinit var btnCellZoomIn: ImageButton
    private lateinit var btnCellZoomReset: ImageButton
    private lateinit var txtCellZoomPercent: TextView

    // ── Rendering job for smooth live gesture updates ──
    private var liveRenderJob: Job? = null

    // ── Tab views ──
    private lateinit var tabRatio: TextView
    private lateinit var tabLayout: TextView
    private lateinit var tabRotate: TextView
    private lateinit var tabBorder: TextView
    private lateinit var tabBackground: TextView
    private var activeTab = 0 // 0=Ratio, 1=Layout, 2=Rotate, 3=Border, 4=Background
    private var selectedImageIndex = 0

    // ── Drag state ──
    private var isDragging = false
    private var dragSourceIndex = -1
    private var dragTargetIndex = -1
    private var longPressStartTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.navigationBarColor = Color.TRANSPARENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
            val decor = window.peekDecorView()
            if (decor != null) {
                WindowCompat.getInsetsController(window, decor).show(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
            }
        } catch (e: Exception) {
            // ignore
        }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_collage_editor_v2)

        initViews()
        loadImages()
        setupToolbar()
        setupTabs()
        
        // Initialize scale gesture detector for cell zoom and pan
        scaleGestureDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            private var prevFocusX = 0f
            private var prevFocusY = 0f
            private var scaleTargetIndex = -1

            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                prevFocusX = detector.focusX
                prevFocusY = detector.focusY
                val focalIdx = getCellIndex(detector.focusX, detector.focusY)
                scaleTargetIndex = if (focalIdx in currentState.images.indices) focalIdx else currentState.selectedIndex
                if (scaleTargetIndex in currentState.images.indices) {
                    currentState = currentState.copy(selectedIndex = scaleTargetIndex)
                    selectedImageIndex = scaleTargetIndex
                    updateZoomPillUi()
                }
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val selectedIdx = scaleTargetIndex
                if (selectedIdx in currentState.images.indices) {
                    val currentScales = currentState.scales.toMutableList()
                    val factor = detector.scaleFactor
                    // Smooth scaling between 0.25x and 6.0x
                    val newScale = (currentScales[selectedIdx] * factor).coerceIn(0.25f, 6.0f)
                    currentScales[selectedIdx] = newScale

                    // Update pan / translate on drag with two fingers
                    val oxs = currentState.offsetsX.toMutableList()
                    val oys = currentState.offsetsY.toMutableList()
                    val pW = preview.width.coerceAtLeast(1)
                    val pH = preview.height.coerceAtLeast(1)

                    val dx = detector.focusX - prevFocusX
                    val dy = detector.focusY - prevFocusY
                    prevFocusX = detector.focusX
                    prevFocusY = detector.focusY

                    oxs[selectedIdx] = (oxs[selectedIdx] + (dx / pW) * 1.2f).coerceIn(-2.0f, 2.0f)
                    oys[selectedIdx] = (oys[selectedIdx] + (dy / pH) * 1.2f).coerceIn(-2.0f, 2.0f)

                    currentState = currentState.copy(scales = currentScales, offsetsX = oxs, offsetsY = oys)
                    updateZoomPillUi()
                    scheduleQuickPreview()
                    return true
                }
                prevFocusX = detector.focusX
                prevFocusY = detector.focusY
                return false
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                scaleTargetIndex = -1
                refreshPreview()
            }
        })

        setupDragAndDrop()
        setupCustomTextSticker()

        // Initial render
        showRatioOptions()
        refreshPreview()
    }

    private fun initViews() {
        preview = findViewById(R.id.collagePreview)
        dragOverlay = findViewById(R.id.dragOverlay)
        progressBar = findViewById(R.id.progressBar)
        optionsContainer = findViewById(R.id.optionsContainer)

        tabRatio = findViewById(R.id.tabRatio)
        tabLayout = findViewById(R.id.tabLayout)
        tabRotate = findViewById(R.id.tabRotate)
        tabBorder = findViewById(R.id.tabBorder)
        tabBackground = findViewById(R.id.tabBackground)

        cellZoomControlPill = findViewById(R.id.cellZoomControlPill)
        btnCellZoomOut = findViewById(R.id.btnCellZoomOut)
        btnCellZoomIn = findViewById(R.id.btnCellZoomIn)
        btnCellZoomReset = findViewById(R.id.btnCellZoomReset)
        txtCellZoomPercent = findViewById(R.id.txtCellZoomPercent)

        btnCellZoomIn.setOnClickListener { zoomSelectedImage(1.15f) }
        btnCellZoomOut.setOnClickListener { zoomSelectedImage(0.85f) }
        btnCellZoomReset.setOnClickListener { resetZoomSelectedImage() }
        txtCellZoomPercent.setOnClickListener { resetZoomSelectedImage() }
    }

    private fun loadImages() {
        val uris = intent.getParcelableArrayListExtra<Uri>("PHOTO_URIS") ?: emptyList()
        if (uris.isEmpty()) {
            Toast.makeText(this, "No photos selected", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        lifecycleScope.launch {
            val bitmaps = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri ->
                    try {
                        val options = BitmapFactory.Options().apply {
                            inJustDecodeBounds = true
                        }
                        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }

                        // Subsample large images to avoid OOM
                        val maxDim = 1600
                        val sampleSize = Math.max(1,
                            Math.max(options.outWidth / maxDim, options.outHeight / maxDim))

                        val decodeOpts = BitmapFactory.Options().apply {
                            inSampleSize = sampleSize
                        }
                        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decodeOpts) }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to load image: ${e.message}")
                        null
                    }
                }
            }

            if (bitmaps.isEmpty()) {
                Toast.makeText(this@CollageEditorActivity, "Failed to load images", Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }

            currentState = CollageState(images = bitmaps)
            refreshPreview()
            selectTab(0)
        }
    }

    // ── Toolbar ──
    private fun setupToolbar() {
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        findViewById<View>(R.id.btnAddText).setOnClickListener {
            openStickerTextEditor()
        }

        findViewById<View>(R.id.btnUndo).setOnClickListener {
            if (undoStack.isNotEmpty()) {
                currentState = undoStack.removeAt(undoStack.lastIndex)
                refreshPreview()
                refreshCurrentTab()
                Toast.makeText(this, "Undone", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Nothing to undo", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<View>(R.id.btnSave).setOnClickListener {
            showProgress(true)
            lifecycleScope.launch(Dispatchers.IO) {
                CollageExporter.save(this@CollageEditorActivity, currentState) { uri ->
                    runOnUiThread {
                        showProgress(false)
                        if (uri != null) {
                            Toast.makeText(this@CollageEditorActivity, "✓ Saved to Gallery", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@CollageEditorActivity, "Save failed", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }

        findViewById<View>(R.id.btnShare).setOnClickListener {
            showProgress(true)
            lifecycleScope.launch(Dispatchers.IO) {
                CollageExporter.share(this@CollageEditorActivity, currentState) { success ->
                    runOnUiThread { showProgress(false) }
                }
            }
        }

        findViewById<View>(R.id.btnReport).setOnClickListener {
            showProgress(true)
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val bitmap = CollageExporter.render(this@CollageEditorActivity, currentState)
                    if (bitmap == null) {
                        withContext(Dispatchers.Main) {
                            showProgress(false)
                            Toast.makeText(this@CollageEditorActivity, "Failed to render collage", Toast.LENGTH_SHORT).show()
                        }
                        return@launch
                    }

                    // Save to temporary file in cache for StaffInputActivity
                    val tempFile = File(cacheDir, "collage_for_report_${System.currentTimeMillis()}.jpg")
                    FileOutputStream(tempFile).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                    }

                    // Extract GPS from the original photos if available
                    val uris = intent.getParcelableArrayListExtra<Uri>("PHOTO_URIS") ?: emptyList()
                    var gpsAddress = ""
                    var firstCoords: Pair<Double, Double>? = null
                    for (uri in uris) {
                        val coords = ExifGpsExtractor.extractGps(this@CollageEditorActivity, uri)
                        if (coords != null) {
                            firstCoords = coords
                            try {
                                val geocoder = Geocoder(this@CollageEditorActivity, Locale.getDefault())
                                @Suppress("DEPRECATION")
                                val addrs = geocoder.getFromLocation(coords.first, coords.second, 1)
                                if (!addrs.isNullOrEmpty()) {
                                    gpsAddress = addrs[0].getAddressLine(0) ?: "Lat ${coords.first}, Long ${coords.second}"
                                } else {
                                    gpsAddress = "Lat ${coords.first}, Long ${coords.second}"
                                }
                            } catch (_: Exception) {
                                gpsAddress = "Lat ${coords.first}, Long ${coords.second}"
                            }
                            if (gpsAddress.isNotBlank()) break
                        }
                    }

                    // Embed GPS info in the EXIF of the temp collage file
                    if (firstCoords != null) {
                        try {
                            val exif = androidx.exifinterface.media.ExifInterface(tempFile.absolutePath)
                            fun formatCoord(coord: Double): String {
                                val deg = Math.abs(coord).toInt()
                                val min = ((Math.abs(coord) - deg) * 60).toInt()
                                val sec = (Math.abs(coord) - deg - min / 60.0) * 3600.0
                                return "$deg/1,$min/1,${(sec * 1000).toInt()}/1000"
                            }
                            exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_GPS_LATITUDE, formatCoord(firstCoords.first))
                            exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_GPS_LATITUDE_REF, if (firstCoords.first >= 0) "N" else "S")
                            exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_GPS_LONGITUDE, formatCoord(firstCoords.second))
                            exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_GPS_LONGITUDE_REF, if (firstCoords.second >= 0) "E" else "W")
                            exif.saveAttributes()
                        } catch (_: Exception) { }
                    }

                    withContext(Dispatchers.Main) {
                        showProgress(false)
                        val staffIntent = Intent(this@CollageEditorActivity, StaffInputActivity::class.java).apply {
                            putExtra("COLLAGE_IMAGE_PATH", tempFile.absolutePath)
                            putExtra("IS_COLLAGE_REPORT", true)
                            if (gpsAddress.isNotBlank()) {
                                putExtra("COLLAGE_GPS_ADDRESS", gpsAddress)
                                putStringArrayListExtra("PHOTO_GPS", arrayListOf(gpsAddress))
                            }
                        }
                        startActivity(staffIntent)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error launching StaffInputActivity with collage: ${e.message}", e)
                    withContext(Dispatchers.Main) {
                        showProgress(false)
                        Toast.makeText(this@CollageEditorActivity, "Error preparing report: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // ── Tabs ──
    private fun setupTabs() {
        tabRatio.setOnClickListener { selectTab(0) }
        tabLayout.setOnClickListener { selectTab(1) }
        tabRotate.setOnClickListener { selectTab(2) }
        tabBorder.setOnClickListener { selectTab(3) }
        tabBackground.setOnClickListener { selectTab(4) }
    }

    private fun selectTab(index: Int) {
        activeTab = index
        val tabs = listOf(tabRatio, tabLayout, tabRotate, tabBorder, tabBackground)
        tabs.forEachIndexed { i, tv ->
            if (i == index) {
                tv.setTextColor(0xFFFFC107.toInt())
                tv.setTypeface(null, Typeface.BOLD)
            } else {
                tv.setTextColor(0xFF888888.toInt())
                tv.setTypeface(null, Typeface.NORMAL)
            }
        }
        refreshCurrentTab()
    }

    private fun refreshCurrentTab() {
        when (activeTab) {
            0 -> showRatioOptions()
            1 -> showLayoutOptions()
            2 -> showRotateOptions()
            3 -> showBorderOptions()
            4 -> showBackgroundOptions()
        }
    }

    // ── Rotate & Zoom Options ──
    private fun showRotateOptions() {
        optionsContainer.removeAllViews()

        if (currentState.images.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = "No images to rotate"
                setTextColor(0xFF888888.toInt())
                setPadding(16, 16, 16, 16)
            }
            optionsContainer.addView(emptyTv)
            return
        }

        if (selectedImageIndex !in currentState.images.indices) {
            selectedImageIndex = 0
        }

        // 1. Quick action button: "Rotate 90° ↻" for selected image
        val rotateRightBtn = createActionButton("Rotate 90° ↻", 0xFFFFC107.toInt()) {
            rotateImage(selectedImageIndex, 90f)
        }
        optionsContainer.addView(rotateRightBtn)

        // 2. Quick action button: "Rotate -90° ↺"
        val rotateLeftBtn = createActionButton("Rotate -90° ↺", 0xFFFFFFFF.toInt()) {
            rotateImage(selectedImageIndex, -90f)
        }
        optionsContainer.addView(rotateLeftBtn)

        // 3. Quick action button: "Rotate 180°"
        val rotate180Btn = createActionButton("Rotate 180°", 0xFFFFB74D.toInt()) {
            rotateImage(selectedImageIndex, 180f)
        }
        optionsContainer.addView(rotate180Btn)

        // 4. Quick action button: "Rotate All ↻"
        val rotateAllBtn = createActionButton("Rotate All ↻", 0xFF64B5F6.toInt()) {
            rotateAllImages(90f)
        }
        optionsContainer.addView(rotateAllBtn)

        // 5. Quick action button: "Zoom In ➕"
        val zoomInBtn = createActionButton("Zoom In ➕", 0xFF81C784.toInt()) {
            zoomSelectedImage(1.15f)
        }
        optionsContainer.addView(zoomInBtn)

        // 6. Quick action button: "Zoom Out ➖"
        val zoomOutBtn = createActionButton("Zoom Out ➖", 0xFFE57373.toInt()) {
            zoomSelectedImage(0.85f)
        }
        optionsContainer.addView(zoomOutBtn)

        // 7. Quick action button: "Reset Zoom ↺"
        val resetZoomBtn = createActionButton("Reset Zoom ↺", 0xFF64B5F6.toInt()) {
            resetZoomSelectedImage()
        }
        optionsContainer.addView(resetZoomBtn)
    }

    private fun zoomSelectedImage(factor: Float) {
        val selectedIdx = currentState.selectedIndex
        if (selectedIdx in currentState.images.indices) {
            pushUndo()
            val currentScales = currentState.scales.toMutableList()
            val newScale = (currentScales[selectedIdx] * factor).coerceIn(0.25f, 6.0f)
            currentScales[selectedIdx] = newScale
            currentState = currentState.copy(scales = currentScales)
            updateZoomPillUi()
            refreshPreview()
            Toast.makeText(this, "Zoom: ${(newScale * 100).toInt()}%", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Tap a photo in the collage to select and zoom it", Toast.LENGTH_SHORT).show()
        }
    }

    private fun resetZoomSelectedImage() {
        val selectedIdx = currentState.selectedIndex
        if (selectedIdx in currentState.images.indices) {
            pushUndo()
            val currentScales = currentState.scales.toMutableList()
            val oxs = currentState.offsetsX.toMutableList()
            val oys = currentState.offsetsY.toMutableList()
            currentScales[selectedIdx] = 1.0f
            oxs[selectedIdx] = 0f
            oys[selectedIdx] = 0f
            currentState = currentState.copy(scales = currentScales, offsetsX = oxs, offsetsY = oys)
            updateZoomPillUi()
            refreshPreview()
            Toast.makeText(this, "Zoom reset to 100%", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateZoomPillUi() {
        if (!::txtCellZoomPercent.isInitialized) return
        val selectedIdx = currentState.selectedIndex
        if (selectedIdx in currentState.scales.indices) {
            val scale = currentState.scales[selectedIdx]
            txtCellZoomPercent.text = "${(scale * 100).toInt()}%"
        } else {
            txtCellZoomPercent.text = "100%"
        }
    }

    private fun rotateImage(index: Int, degrees: Float) {
        if (index !in currentState.images.indices) return
        val original = currentState.images[index] ?: return
        pushUndo()
        val matrix = Matrix().apply { postRotate(degrees) }
        val rotated = Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)
        val newImages = currentState.images.toMutableList()
        newImages[index] = rotated
        currentState = currentState.copy(images = newImages)
        refreshPreview()
        showRotateOptions()
        Toast.makeText(this, "Photo ${index + 1} rotated ${degrees.toInt()}°", Toast.LENGTH_SHORT).show()
    }

    private fun rotateAllImages(degrees: Float) {
        if (currentState.images.isEmpty()) return
        pushUndo()
        val matrix = Matrix().apply { postRotate(degrees) }
        val newImages = currentState.images.map { bmp ->
            bmp?.let { Bitmap.createBitmap(it, 0, 0, it.width, it.height, matrix, true) }
        }
        currentState = currentState.copy(images = newImages)
        refreshPreview()
        showRotateOptions()
        Toast.makeText(this, "All photos rotated ${degrees.toInt()}°", Toast.LENGTH_SHORT).show()
    }

    private fun createActionButton(label: String, color: Int, onClick: () -> Unit): View {
        val density = resources.displayMetrics.density
        return TextView(this).apply {
            text = label
            setTextColor(color)
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding((14 * density).toInt(), (10 * density).toInt(), (14 * density).toInt(), (10 * density).toInt())
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0x33333333)
                cornerRadius = 16 * density
                setStroke((1.5f * density).toInt(), color)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = (8 * density).toInt()
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
    }

    // ── Ratio Options ──
    private fun showRatioOptions() {
        optionsContainer.removeAllViews()
        CollageRatio.values().forEach { ratio ->
            val card = createOptionCard(
                drawRatioThumbnail(ratio, 60),
                ratio.label,
                ratio == currentState.ratio
            ) {
                // Reset layout index when changing ratios so we don't go out of bounds on the filtered layouts
                currentState = currentState.copy(ratio = ratio, layoutIndex = 0)
                refreshPreview()
                showRatioOptions()
            }
            optionsContainer.addView(card)
        }
    }

    // ── Layout options: Layout template thumbnails ──
    private fun showLayoutOptions() {
        optionsContainer.removeAllViews()
        val imageCount = currentState.images.size.coerceIn(2, 6)
        val templates = CollageLayoutEngine.getTemplates(imageCount, currentState.ratio)

        templates.forEachIndexed { index, template ->
            val card = createOptionCard(
                drawLayoutThumbnail(template, 60),
                template.name,
                index == currentState.layoutIndex
            ) {
                pushUndo()
                currentState = currentState.copy(layoutIndex = index)
                refreshPreview()
                showLayoutOptions()
            }
            optionsContainer.addView(card)
        }
    }



    // ── Drag and Drop, Zoom, Pan ──
    @SuppressLint("ClickableViewAccessibility")
    private fun setupDragAndDrop() {
        var lastTouchX = 0f
        var lastTouchY = 0f
        var downTouchX = 0f
        var downTouchY = 0f
        var isPanning = false

        dragOverlay.setOnTouchListener { v, event ->
            scaleGestureDetector.onTouchEvent(event)

            if (event.pointerCount >= 2) {
                isDragging = false
                isPanning = false
                return@setOnTouchListener true
            }

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    longPressStartTime = System.currentTimeMillis()
                    dragSourceIndex = getCellIndex(event.x, event.y)
                    lastTouchX = event.x
                    lastTouchY = event.y
                    downTouchX = event.x
                    downTouchY = event.y
                    isDragging = false
                    isPanning = false
                    if (dragSourceIndex in currentState.images.indices) {
                        selectedImageIndex = dragSourceIndex
                        currentState = currentState.copy(selectedIndex = dragSourceIndex)
                        updateZoomPillUi()
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - lastTouchX
                    val dy = event.y - lastTouchY
                    lastTouchX = event.x
                    lastTouchY = event.y

                    val dist = Math.hypot((event.x - downTouchX).toDouble(), (event.y - downTouchY).toDouble()).toFloat()

                    // If user moved finger noticeably, cancel swap mode and immediately pan the photo
                    if (!isDragging && !isPanning && dist > 14f) {
                        isPanning = true
                    }

                    // Only trigger swap mode if user holds finger still without dragging for > 600ms
                    if (!isDragging && !isPanning && System.currentTimeMillis() - longPressStartTime > 600) {
                        isDragging = true
                        v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                        preview.animate().scaleX(1.02f).scaleY(1.02f).setDuration(100).start()
                    }

                    if (isDragging) {
                        dragTargetIndex = getCellIndex(event.x, event.y)
                        refreshPreviewWithHighlight(dragTargetIndex)
                    } else if (isPanning || dist > 8f) {
                        // Smoothly pan/drag photo within cell
                        val selectedIdx = currentState.selectedIndex
                        if (selectedIdx in currentState.images.indices) {
                            val oxs = currentState.offsetsX.toMutableList()
                            val oys = currentState.offsetsY.toMutableList()
                            val pW = preview.width.coerceAtLeast(1)
                            val pH = preview.height.coerceAtLeast(1)
                            oxs[selectedIdx] = (oxs[selectedIdx] + (dx / pW) * 1.3f).coerceIn(-2.0f, 2.0f)
                            oys[selectedIdx] = (oys[selectedIdx] + (dy / pH) * 1.3f).coerceIn(-2.0f, 2.0f)
                            currentState = currentState.copy(offsetsX = oxs, offsetsY = oys)
                            scheduleQuickPreview()
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isDragging && dragSourceIndex >= 0 && dragTargetIndex >= 0 && dragSourceIndex != dragTargetIndex) {
                        swapImages(dragSourceIndex, dragTargetIndex)
                    } else if (!isDragging && dragSourceIndex >= 0) {
                        selectedImageIndex = dragSourceIndex
                        currentState = currentState.copy(selectedIndex = dragSourceIndex)
                        updateZoomPillUi()
                        if (System.currentTimeMillis() - longPressStartTime < 400 && !isPanning) {
                            showRotateOptions()
                        }
                    }
                    isDragging = false
                    isPanning = false
                    dragSourceIndex = -1
                    dragTargetIndex = -1
                    preview.animate()
                        .scaleX(1f).scaleY(1f)
                        .setInterpolator(OvershootInterpolator(2f))
                        .setDuration(200).start()
                    refreshPreview()
                    true
                }
                else -> false
            }
        }
    }

    private fun getCellIndex(touchX: Float, touchY: Float): Int {
        val drawable = preview.drawable ?: return -1
        val imageW = drawable.intrinsicWidth
        val imageH = drawable.intrinsicHeight
        if (imageW <= 0 || imageH <= 0) return -1

        // Map touch coordinates to image coordinates (accounting for fitCenter)
        val viewW = preview.width.toFloat()
        val viewH = preview.height.toFloat()
        val scaleX = viewW / imageW
        val scaleY = viewH / imageH
        val scale = minOf(scaleX, scaleY)

        val renderedW = imageW * scale
        val renderedH = imageH * scale
        val offsetX = (viewW - renderedW) / 2
        val offsetY = (viewH - renderedH) / 2

        val imgX = ((touchX - offsetX) / renderedW).coerceIn(0f, 1f)
        val imgY = ((touchY - offsetY) / renderedH).coerceIn(0f, 1f)

        val imageCount = currentState.images.size.coerceIn(2, 6)
        val template = CollageLayoutEngine.getTemplate(imageCount, currentState.ratio, currentState.layoutIndex)

        template.cells.forEachIndexed { i, cell ->
            if (imgX in cell.left..cell.right && imgY in cell.top..cell.bottom) {
                return i
            }
        }
        return -1
    }

    private fun swapImages(from: Int, to: Int) {
        if (from < 0 || to < 0 || from >= currentState.images.size || to >= currentState.images.size) return
        pushUndo()
        val newImages = currentState.images.toMutableList()
        val temp = newImages[from]
        newImages[from] = newImages[to]
        newImages[to] = temp
        currentState = currentState.copy(images = newImages)
    }

    // ── Live fast preview rendering for 60 FPS gesture updates without queue lag ──
    private fun scheduleQuickPreview() {
        liveRenderJob?.cancel()
        liveRenderJob = lifecycleScope.launch {
            val bmp = withContext(Dispatchers.Default) {
                CollageExporter.renderPreview(this@CollageEditorActivity, currentState, previewSize = 650)
            }
            if (isActive && bmp != null) {
                preview.setImageBitmap(bmp)
            }
        }
    }

    // ── Full resolution preview rendering ──
    private fun refreshPreview() {
        liveRenderJob?.cancel()
        updateZoomPillUi()
        lifecycleScope.launch {
            val bmp = withContext(Dispatchers.Default) {
                CollageExporter.renderPreview(this@CollageEditorActivity, currentState, previewSize = 850)
            }
            bmp?.let { preview.setImageBitmap(it) }
        }
    }

    private fun refreshPreviewWithHighlight(targetIdx: Int) {
        lifecycleScope.launch {
            val bmp = withContext(Dispatchers.Default) {
                val rendered = CollageExporter.renderPreview(this@CollageEditorActivity, currentState) ?: return@withContext null
                if (targetIdx < 0) return@withContext rendered

                // Draw blue highlight on target cell
                val canvas = Canvas(rendered)
                val imageCount = currentState.images.size.coerceIn(2, 6)
                val template = CollageLayoutEngine.getTemplate(imageCount, currentState.ratio, currentState.layoutIndex)
                if (targetIdx < template.cells.size) {
                    val cell = template.cells[targetIdx]
                    val rect = cell.toRectF(rendered.width.toFloat(), rendered.height.toFloat())
                    // Blue translucent fill
                    val paint = Paint().apply {
                        color = Color.parseColor("#4400AAFF")
                        style = Paint.Style.FILL
                    }
                    canvas.drawRect(rect, paint)
                    // Blue border
                    val borderPaint = Paint().apply {
                        color = Color.parseColor("#00AAFF")
                        style = Paint.Style.STROKE
                        strokeWidth = 4f
                    }
                    canvas.drawRect(rect, borderPaint)
                }
                rendered
            }
            bmp?.let { preview.setImageBitmap(it) }
        }
    }

    // ── Undo ──
    private fun pushUndo() {
        undoStack.add(currentState)
        if (undoStack.size > maxUndo) {
            undoStack.removeAt(0)
        }
    }

    // ── Progress ──
    private fun showProgress(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }

    // ── Thumbnail generators for option cards ──

    private fun createOptionCard(thumbnail: Bitmap, label: String, isSelected: Boolean, onClick: () -> Unit): View {
        val density = resources.displayMetrics.density
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val size = (70 * density).toInt()
            layoutParams = LinearLayout.LayoutParams(size, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = (6 * density).toInt()
            }
            setPadding(0, (4 * density).toInt(), 0, (4 * density).toInt())
            setOnClickListener { onClick() }
            isClickable = true
            isFocusable = true
            if (isSelected) {
                setBackgroundColor(0x33FFC107)
            }
        }

        val imgView = ImageView(this).apply {
            val imgSize = (54 * density).toInt()
            layoutParams = LinearLayout.LayoutParams(imgSize, imgSize)
            setImageBitmap(thumbnail)
            scaleType = ImageView.ScaleType.FIT_CENTER
            if (isSelected) {
                setPadding(2, 2, 2, 2)
            }
        }
        container.addView(imgView)

        val textView = TextView(this).apply {
            text = label
            textSize = 10f
            setTextColor(if (isSelected) 0xFFFFC107.toInt() else 0xFFCCCCCC.toInt())
            gravity = Gravity.CENTER
            maxLines = 1
        }
        container.addView(textView)

        return container
    }

    // ── Border options ──
    private fun showBorderOptions() {
        optionsContainer.removeAllViews()
        CollageBorder.values().forEach { border ->
            val card = createOptionCard(
                drawBorderThumbnail(border, 60),
                border.label,
                border == currentState.border
            ) {
                pushUndo()
                currentState = currentState.copy(border = border)
                refreshPreview()
                showBorderOptions()
            }
            optionsContainer.addView(card)
        }
    }

    // ── Background options ──
    private fun showBackgroundOptions() {
        optionsContainer.removeAllViews()
        CollageBg.values().forEach { bg ->
            val card = createOptionCard(
                drawBgThumbnail(bg, 60),
                bg.label,
                bg == currentState.background
            ) {
                pushUndo()
                currentState = currentState.copy(background = bg)
                refreshPreview()
                showBackgroundOptions()
            }
            optionsContainer.addView(card)
        }
    }

    /** Draw intuitive ratio thumbnail */
    private fun drawRatioThumbnail(ratio: CollageRatio, sizeDp: Int): Bitmap {
        val density = resources.displayMetrics.density
        val size = (sizeDp * density).toInt()
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        
        // Dark gray background for item
        canvas.drawColor(0x00000000)

        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            strokeWidth = 4f
            style = Paint.Style.STROKE
        }

        // Calculate rect honoring aspect ratio inside square
        val margin = size * 0.15f
        val boxSize = size - margin * 2

        val rv = ratio.value()
        val rw: Float
        val rh: Float

        if (rv >= 1f) {
            rw = boxSize
            rh = boxSize / rv
        } else {
            rh = boxSize
            rw = boxSize * rv
        }

        val cx = size / 2f
        val cy = size / 2f

        val rect = RectF(cx - rw / 2, cy - rh / 2, cx + rw / 2, cy + rh / 2)
        canvas.drawRoundRect(rect, 8f, 8f, strokePaint)

        return bmp
    }

    /** Draw modern, attractive layouts thumbnails based on ratio */
    private fun drawLayoutThumbnail(template: CollageLayoutEngine.Template, sizeDp: Int): Bitmap {
        val density = resources.displayMetrics.density
        val canvasSize = (sizeDp * density).toInt()
        val bmp = Bitmap.createBitmap(canvasSize, canvasSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        
        // Transparent BG for a clean look
        canvas.drawColor(0x00000000)

        // Bounding box size honoring current ratio
        val rv = currentState.ratio.value()
        val margin = canvasSize * 0.1f
        val boxSize = canvasSize - margin * 2
        
        val rw: Float
        val rh: Float
        if (rv >= 1f) {
            rw = boxSize
            rh = boxSize / rv
        } else {
            rh = boxSize
            rw = boxSize * rv
        }

        // Draw outer canvas background
        val cx = canvasSize / 2f
        val cy = canvasSize / 2f
        val layoutRect = RectF(cx - rw / 2, cy - rh / 2, cx + rw / 2, cy + rh / 2)
        
        // Draw elegant gray placeholder background
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#333333")
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(layoutRect, 8f, 8f, bgPaint)

        // Draw layout cells inside this box
        val gap = 3f
        
        // Cell colors - beautiful, modern UI
        val colors = intArrayOf(
            0xFF8E8CD8.toInt(), // Modern vibrant pastel colors
            0xFF83C5BE.toInt(),
            0xFFFFD166.toInt(),
            0xFFEF476F.toInt(),
            0xFF118AB2.toInt(),
            0xFF06D6A0.toInt()
        )

        template.cells.forEachIndexed { i, cell ->
            // Map 0..1 coordinates to our layoutRect
            val cLeft = layoutRect.left + (cell.left * layoutRect.width())
            val cTop = layoutRect.top + (cell.top * layoutRect.height())
            val cRight = layoutRect.left + (cell.right * layoutRect.width())
            val cBottom = layoutRect.top + (cell.bottom * layoutRect.height())
            
            val insetRect = RectF(
                cLeft + gap/2, 
                cTop + gap/2, 
                cRight - gap/2, 
                cBottom - gap/2
            )
            
            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { 
                color = colors[i % colors.size] 
            }
            canvas.drawRoundRect(insetRect, 4f, 4f, fillPaint)
        }
        
        return bmp
    }

    /** Draw a border style preview thumbnail */
    private fun drawBorderThumbnail(border: CollageBorder, sizeDp: Int): Bitmap {
        val density = resources.displayMetrics.density
        val size = (sizeDp * density).toInt()
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(0xFF555555.toInt())

        if (border != CollageBorder.NONE) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = border.color
                strokeWidth = border.widthDp * density
                style = Paint.Style.STROKE
                if (border.isDashed) pathEffect = DashPathEffect(floatArrayOf(8f, 5f), 0f)
                if (border.isDotted) pathEffect = DashPathEffect(floatArrayOf(3f, 4f), 0f)
            }
            val inset = border.widthDp * density / 2 + 4
            canvas.drawRect(inset, inset, size - inset, size - inset, paint)

            // Draw a 2x2 grid inside
            val mid = size / 2f
            canvas.drawLine(mid, inset, mid, size - inset, paint)
            canvas.drawLine(inset, mid, size - inset, mid, paint)
        }
        return bmp
    }

    /** Draw a background color/gradient preview thumbnail */
    private fun drawBgThumbnail(bg: CollageBg, sizeDp: Int): Bitmap {
        val density = resources.displayMetrics.density
        val size = (sizeDp * density).toInt()
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        if (bg.colors.size == 1) {
            canvas.drawColor(bg.colors[0])
        } else {
            val gradient = LinearGradient(
                0f, 0f, size.toFloat(), size.toFloat(),
                bg.colors[0], bg.colors[1], Shader.TileMode.CLAMP
            )
            val paint = Paint().apply { shader = gradient }
            canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
        }

        // Draw a grid icon inside for visual reference
        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            alpha = 100
            strokeWidth = 2f
            style = Paint.Style.STROKE
        }
        val margin = size * 0.2f
        canvas.drawRect(margin, margin, size - margin, size - margin, iconPaint)
        canvas.drawLine(size / 2f, margin, size / 2f, size - margin, iconPaint)
        canvas.drawLine(margin, size / 2f, size - margin, size / 2f, iconPaint)

        return bmp
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

    private fun openStickerTextEditor() {
        val currentConfig = currentStickerConfig ?: CustomNoteConfig(text = "")
        val textEditorDialog = InstagramTextEditorDialog(this, currentConfig, false) { savedConfig ->
            updateLiveCustomNoteView(savedConfig)
        }
        textEditorDialog.show()
    }

    private fun updateLiveCustomNoteView(config: CustomNoteConfig) {
        currentStickerConfig = config
        val container = findViewById<View>(R.id.liveCustomNoteContainer) ?: return
        val contentBox = findViewById<View>(R.id.stickerContentBox) ?: return
        val textView = findViewById<TextView>(R.id.liveCustomNoteText) ?: return

        if (config.text.isBlank()) {
            container.visibility = View.GONE
            setStickerSelected(false)
            currentState = currentState.copy(stickerConfig = null)
            refreshPreview()
            return
        }

        container.visibility = View.VISIBLE
        textView.text = config.text
        InstagramTextStyler.applyStyleToView(textView, config)
        container.rotation = config.rotation
        contentBox.scaleX = if (config.isMirrored) -1f else 1f
        setStickerSelected(true)

        // Save sticker configuration to CollageState so it gets drawn on the exported bitmap
        currentState = currentState.copy(stickerConfig = config)
        refreshPreview()
    }

    private fun setupCustomTextSticker() {
        val container = findViewById<View>(R.id.liveCustomNoteContainer) ?: return
        val contentBox = findViewById<View>(R.id.stickerContentBox) ?: return
        val textView = findViewById<TextView>(R.id.liveCustomNoteText) ?: return
        val btnDelete = findViewById<View>(R.id.btnStickerDelete) ?: return
        val btnMirror = findViewById<View>(R.id.btnStickerMirror) ?: return
        val btnEdit = findViewById<View>(R.id.btnStickerEdit) ?: return
        val btnResize = findViewById<View>(R.id.btnStickerResize) ?: return

        setStickerSelected(false)

        // 1. Drag & Tap on Content Box
        var startRawX = 0f
        var startRawY = 0f
        var startTransX = 0f
        var startTransY = 0f
        var isDraggingSticker = false
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop

        contentBox.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startRawX = event.rawX
                    startRawY = event.rawY
                    startTransX = container.translationX
                    startTransY = container.translationY
                    isDraggingSticker = false
                    setStickerSelected(true)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
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
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!isDraggingSticker) {
                        setStickerSelected(true)
                    } else {
                        val parent = container.parent as? View
                        if (parent != null && parent.width > 0 && parent.height > 0) {
                            val stickerCenterX = container.x + container.width / 2f
                            val stickerCenterY = container.y + container.height / 2f
                            val normX = stickerCenterX / parent.width
                            val normY = stickerCenterY / parent.height

                            val cfg = currentStickerConfig ?: CustomNoteConfig(text = "")
                            cfg.isCustomPositioned = true
                            cfg.normPosX = normX
                            cfg.normPosY = normY
                            currentStickerConfig = cfg
                            currentState = currentState.copy(stickerConfig = cfg)
                            refreshPreview()
                        }
                    }
                    true
                }
                else -> false
            }
        }

        // 2. Top-Left: Delete button (Trash can)
        btnDelete.setOnClickListener {
            val cfg = currentStickerConfig ?: CustomNoteConfig(text = "")
            cfg.text = ""
            cfg.isMirrored = false
            currentStickerConfig = cfg
            setStickerSelected(false)
            container.visibility = View.GONE
            currentState = currentState.copy(stickerConfig = null)
            refreshPreview()
            Toast.makeText(this, "Text deleted", Toast.LENGTH_SHORT).show()
        }

        // 3. Top-Right: Mirror/Flip button
        btnMirror.setOnClickListener {
            val cfg = currentStickerConfig ?: CustomNoteConfig(text = "")
            cfg.isMirrored = !cfg.isMirrored
            contentBox.scaleX = if (cfg.isMirrored) -1f else 1f
            currentStickerConfig = cfg
            currentState = currentState.copy(stickerConfig = cfg)
            refreshPreview()
            val status = if (cfg.isMirrored) "Mirrored" else "Normal"
            Toast.makeText(this, "Text $status", Toast.LENGTH_SHORT).show()
        }

        // 4. Bottom-Left: Edit button (Pencil)
        btnEdit.setOnClickListener {
            openStickerTextEditor()
        }

        // 5. Bottom-Right: Resize & Rotate button
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
                    val cfg = currentStickerConfig ?: CustomNoteConfig(text = "")
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
                        val cfg = currentStickerConfig ?: CustomNoteConfig(text = "")
                        cfg.textSizeSp = newSize
                    }

                    val currentAngle = Math.toDegrees(Math.atan2(dy.toDouble(), dx.toDouble()))
                    val deltaAngle = (currentAngle - startAngle).toFloat()
                    var newRot = (startRot + deltaAngle) % 360f
                    if (newRot < 0f) newRot += 360f

                    if (newRot < 4f || newRot > 356f) newRot = 0f
                    else if (Math.abs(newRot - 90f) < 4f) newRot = 90f
                    else if (Math.abs(newRot - 180f) < 4f) newRot = 180f
                    else if (Math.abs(newRot - 270f) < 4f) newRot = 270f

                    container.rotation = newRot
                    val cfg = currentStickerConfig ?: CustomNoteConfig(text = "")
                    cfg.rotation = newRot
                    currentState = currentState.copy(stickerConfig = cfg)
                    refreshPreview()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    true
                }
                else -> false
            }
        }
    }
}
