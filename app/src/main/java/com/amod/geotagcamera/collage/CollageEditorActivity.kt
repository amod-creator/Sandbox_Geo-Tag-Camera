package com.amod.geotagcamera.collage

import android.annotation.SuppressLint
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.util.Log
import android.view.*
import android.view.animation.OvershootInterpolator
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.amod.geotagcamera.R
import kotlinx.coroutines.*

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

    // ── Views ──
    private lateinit var preview: ImageView
    private lateinit var dragOverlay: View
    private lateinit var progressBar: ProgressBar
    private lateinit var optionsContainer: LinearLayout

    // ── Tab views ──
    private lateinit var tabRatio: TextView
    private lateinit var tabLayout: TextView
    private lateinit var tabBorder: TextView
    private lateinit var tabBackground: TextView
    private var activeTab = 0 // 0=Ratio, 1=Layout, 2=Border, 3=Background

    // ── Drag state ──
    private var isDragging = false
    private var dragSourceIndex = -1
    private var dragTargetIndex = -1
    private var longPressStartTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_collage_editor_v2)

        initViews()
        loadImages()
        setupToolbar()
        setupTabs()
        setupDragAndDrop()

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
        tabBorder = findViewById(R.id.tabBorder)
        tabBackground = findViewById(R.id.tabBackground)
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
            showLayoutOptions()
        }
    }

    // ── Toolbar ──
    private fun setupToolbar() {
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        findViewById<ImageButton>(R.id.btnUndo).setOnClickListener {
            if (undoStack.isNotEmpty()) {
                currentState = undoStack.removeAt(undoStack.lastIndex)
                refreshPreview()
                refreshCurrentTab()
                Toast.makeText(this, "Undone", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Nothing to undo", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<ImageButton>(R.id.btnSave).setOnClickListener {
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

        findViewById<ImageButton>(R.id.btnShare).setOnClickListener {
            showProgress(true)
            lifecycleScope.launch(Dispatchers.IO) {
                CollageExporter.share(this@CollageEditorActivity, currentState) { success ->
                    runOnUiThread { showProgress(false) }
                }
            }
        }

        findViewById<ImageButton>(R.id.btnReport).setOnClickListener {
            showProgress(true)
            lifecycleScope.launch(Dispatchers.IO) {
                CollageExporter.generateReport(this@CollageEditorActivity, currentState) { uri ->
                    runOnUiThread {
                        showProgress(false)
                        if (uri != null) {
                            Toast.makeText(this@CollageEditorActivity, "Report generated", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    // ── Tabs ──
    private fun setupTabs() {
        tabRatio.setOnClickListener { selectTab(0) }
        tabLayout.setOnClickListener { selectTab(1) }
        tabBorder.setOnClickListener { selectTab(2) }
        tabBackground.setOnClickListener { selectTab(3) }
    }

    private fun selectTab(index: Int) {
        activeTab = index
        val tabs = listOf(tabRatio, tabLayout, tabBorder, tabBackground)
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
            2 -> showBorderOptions()
            3 -> showBackgroundOptions()
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



    // ── Drag and Drop ──
    @SuppressLint("ClickableViewAccessibility")
    private fun setupDragAndDrop() {
        dragOverlay.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    longPressStartTime = System.currentTimeMillis()
                    dragSourceIndex = getCellIndex(event.x, event.y)
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!isDragging && System.currentTimeMillis() - longPressStartTime > 400) {
                        // Long press triggered — start drag
                        isDragging = true
                        v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                        // Scale up preview slightly
                        preview.animate().scaleX(1.02f).scaleY(1.02f).setDuration(100).start()
                    }
                    if (isDragging) {
                        dragTargetIndex = getCellIndex(event.x, event.y)
                        // Highlight target cell with blue overlay
                        refreshPreviewWithHighlight(dragTargetIndex)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isDragging && dragSourceIndex >= 0 && dragTargetIndex >= 0 && dragSourceIndex != dragTargetIndex) {
                        // Swap images
                        swapImages(dragSourceIndex, dragTargetIndex)
                    }
                    isDragging = false
                    dragSourceIndex = -1
                    dragTargetIndex = -1
                    // Reset scale with bounce animation
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

    // ── Preview rendering ──
    private fun refreshPreview() {
        lifecycleScope.launch {
            val bmp = withContext(Dispatchers.Default) {
                CollageExporter.renderPreview(currentState)
            }
            bmp?.let { preview.setImageBitmap(it) }
        }
    }

    private fun refreshPreviewWithHighlight(targetIdx: Int) {
        lifecycleScope.launch {
            val bmp = withContext(Dispatchers.Default) {
                val rendered = CollageExporter.renderPreview(currentState) ?: return@withContext null
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
}
