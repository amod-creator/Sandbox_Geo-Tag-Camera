package com.amod.geotagcamera

import android.content.ContentValues
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.*
import android.view.ViewGroup.MarginLayoutParams
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar

class CollageEditorActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var canvasRoot: FrameLayout
    private lateinit var optionsList: RecyclerView

    private val photoUris = arrayListOf<Uri>()
    private val bitmaps = arrayListOf<Bitmap>()

    private var layoutType: LayoutType = LayoutType.GRID_2x2
    private var borderPx = 8f
    private var cornerPx = 32f
    private var borderColor = Color.CYAN

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_collage_editor)

        toolbar = findViewById(R.id.collageToolbar)
        canvasRoot = findViewById(R.id.collageCanvas)
        optionsList = findViewById(R.id.optionsList)

        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        toolbar.setNavigationOnClickListener { finish() }
        toolbar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.action_save_collage -> {
                    saveToGallery()
                    true
                }
                R.id.action_back -> {
                    onBackPressedDispatcher.onBackPressed()
                    true
                }
                R.id.action_reset -> {
                    // Reset to Live Camera Screen
                    val intent = android.content.Intent(this, MainActivity::class.java)
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                    finish()
                    true
                }
                else -> false
            }
        }

        // Accept URIs from either key: "images" (picker) or legacy "PHOTO_URIS"
        val fromPicker = intent.getParcelableArrayListExtra<Uri>("images")
        val fromAltKey = intent.getParcelableArrayListExtra<Uri>("PHOTO_URIS")
        when {
            !fromPicker.isNullOrEmpty() -> photoUris.addAll(fromPicker)
            !fromAltKey.isNullOrEmpty() -> photoUris.addAll(fromAltKey)
        }
        loadBitmaps()

        // If nothing loaded, close gracefully
        if (bitmaps.isEmpty()) {
            finish()
            return
        }

        // Choose a sensible default layout based on count
        layoutType = when (bitmaps.size) {
            2 -> LayoutType.SPLIT_H
            3 -> LayoutType.GRID_1L_2S
            4 -> LayoutType.GRID_2x2
            5 -> LayoutType.GRID_3_OVER_2
            6 -> LayoutType.GRID_3x2
            else -> LayoutType.GRID_2x2
        }

        setupLayoutOptions()
        renderCurrentLayout()

        // Bottom “tabs” are present for the UI look; Layout is functional now.
        findViewById<View>(R.id.tabLayout).setOnClickListener { setupLayoutOptions() }
        findViewById<View>(R.id.tabBorder).setOnClickListener {
            borderPx = when (borderPx.toInt()) { 0 -> 8f; 8 -> 16f; else -> 0f }
            renderCurrentLayout()
        }
    }

    private fun loadBitmaps() {
        bitmaps.clear()
        for (u in photoUris) {
            contentResolver.openInputStream(u)?.use { ins ->
                BitmapFactory.decodeStream(ins)?.let { bitmaps.add(it) }
            }
        }
    }

    // --- layout chooser like the screenshot ---
    private fun setupLayoutOptions() {
        optionsList.layoutManager = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        val options = when (bitmaps.size) {
            2 -> listOf(LayoutType.SPLIT_H, LayoutType.SPLIT_V)
            3 -> listOf(LayoutType.GRID_1L_2S)
            4 -> listOf(LayoutType.GRID_2x2, LayoutType.ROW_4)
            5 -> listOf(LayoutType.GRID_3_OVER_2, LayoutType.GRID_2_OVER_3, LayoutType.ROW_5)
            6 -> listOf(LayoutType.GRID_3x2)
            else -> listOf(LayoutType.GRID_2x2)
        }
        optionsList.adapter = LayoutAdapter(options) {
            layoutType = it
            renderCurrentLayout()
        }
    }

    private fun renderCurrentLayout() {
        canvasRoot.removeAllViews()
        when (layoutType) {
            LayoutType.SPLIT_H -> arrangeSplit(horizontal = true)
            LayoutType.SPLIT_V -> arrangeSplit(horizontal = false)
            LayoutType.GRID_1L_2S -> arrange3Grid()
            LayoutType.GRID_2x2 -> arrange2x2()
            LayoutType.ROW_4 -> arrangeRow4()
            LayoutType.GRID_3x2 -> arrange3x2()
            LayoutType.GRID_3_OVER_2 -> arrange3Over2()
            LayoutType.GRID_2_OVER_3 -> arrange2Over3()
            LayoutType.ROW_5 -> arrangeRow5()
        }
    }

    private fun newTile(): ImageView = TouchImageView(this).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, cornerPx)
            }
        }
        clipToOutline = true
        setPadding(borderPx.toInt(), borderPx.toInt(), borderPx.toInt(), borderPx.toInt())
        setBackgroundColor(borderColor)
    }

    private fun addRoot(root: FrameLayout) {
        canvasRoot.addView(
            root,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    private fun arrangeSplit(horizontal: Boolean) {
        if (bitmaps.size < 2) return
        val root = FrameLayout(this)
        val a = newTile().apply { setImageBitmap(bitmaps[0]) }
        val b = newTile().apply { setImageBitmap(bitmaps[1]) }
        root.addView(a); root.addView(b)
        root.post {
            val w = root.width; val h = root.height
            if (horizontal) {
                a.layout(0, 0, w, h / 2)
                b.layout(0, h / 2, w, h)
            } else {
                a.layout(0, 0, w / 2, h)
                b.layout(w / 2, 0, w, h)
            }
        }
        addRoot(root)
    }

    private fun arrange2x2() {
        val root = FrameLayout(this)
        val tiles = (0 until minOf(4, bitmaps.size)).map { i -> newTile().apply { setImageBitmap(bitmaps[i]) } }
        tiles.forEach { root.addView(it) }
        root.post {
            val w = root.width; val h = root.height
            val hw = w / 2; val hh = h / 2
            tiles.getOrNull(0)?.layout(0, 0, hw, hh)
            tiles.getOrNull(1)?.layout(hw, 0, w, hh)
            tiles.getOrNull(2)?.layout(0, hh, hw, h)
            tiles.getOrNull(3)?.layout(hw, hh, w, h)
        }
        addRoot(root)
    }

    private fun arrangeRow4() {
        val root = FrameLayout(this)
        val count = minOf(4, bitmaps.size)
        val tiles = (0 until count).map { i -> newTile().apply { setImageBitmap(bitmaps[i]) } }
        tiles.forEach { root.addView(it) }
        root.post {
            val w = root.width; val h = root.height
            val cellW = w / count
            tiles.forEachIndexed { i, v ->
                val l = i * cellW
                v.layout(l, 0, l + cellW, h)
            }
        }
        addRoot(root)
    }

    private fun arrange3Grid() {
        if (bitmaps.size < 3) return
        val root = FrameLayout(this)
        val a = newTile().apply { setImageBitmap(bitmaps[0]) }
        val b = newTile().apply { setImageBitmap(bitmaps[1]) }
        val c = newTile().apply { setImageBitmap(bitmaps[2]) }
        listOf(a, b, c).forEach { root.addView(it) }
        root.post {
            val w = root.width; val h = root.height
            val leftW = (w * 0.6f).toInt()
            a.layout(0, 0, leftW, h)
            b.layout(leftW, 0, w, h / 2)
            c.layout(leftW, h / 2, w, h)
        }
        addRoot(root)
    }

    private fun arrange3x2() {
        val root = FrameLayout(this)
        val count = minOf(6, bitmaps.size)
        val tiles = (0 until count).map { i -> newTile().apply { setImageBitmap(bitmaps[i]) } }
        tiles.forEach { root.addView(it) }
        root.post {
            val w = root.width; val h = root.height
            val cellW = w / 3; val cellH = h / 2
            tiles.forEachIndexed { i, v ->
                val row = i / 3; val col = i % 3
                val l = col * cellW; val t = row * cellH
                v.layout(l, t, l + cellW, t + cellH)
            }
        }
        addRoot(root)
    }

    private fun arrange3Over2() {
        val root = FrameLayout(this)
        val count = minOf(5, bitmaps.size)
        val tiles = (0 until count).map { i -> newTile().apply { setImageBitmap(bitmaps[i]) } }
        tiles.forEach { root.addView(it) }
        root.post {
            val w = root.width; val h = root.height
            val halfH = h / 2
            val topW = w / 3
            // Top row: 3
            tiles.getOrNull(0)?.layout(0 * topW, 0, 1 * topW, halfH)
            tiles.getOrNull(1)?.layout(1 * topW, 0, 2 * topW, halfH)
            tiles.getOrNull(2)?.layout(2 * topW, 0, w, halfH)
            // Bottom row: 2
            val bottomW = w / 2
            tiles.getOrNull(3)?.layout(0, halfH, bottomW, h)
            tiles.getOrNull(4)?.layout(bottomW, halfH, w, h)
        }
        addRoot(root)
    }

    private fun arrange2Over3() {
        val root = FrameLayout(this)
        val count = minOf(5, bitmaps.size)
        val tiles = (0 until count).map { i -> newTile().apply { setImageBitmap(bitmaps[i]) } }
        tiles.forEach { root.addView(it) }
        root.post {
            val w = root.width; val h = root.height
            val halfH = h / 2
            // Top row: 2
            val topW = w / 2
            tiles.getOrNull(0)?.layout(0, 0, topW, halfH)
            tiles.getOrNull(1)?.layout(topW, 0, w, halfH)
            // Bottom row: 3
            val bottomW = w / 3
            tiles.getOrNull(2)?.layout(0 * bottomW, halfH, 1 * bottomW, h)
            tiles.getOrNull(3)?.layout(1 * bottomW, halfH, 2 * bottomW, h)
            tiles.getOrNull(4)?.layout(2 * bottomW, halfH, w, h)
        }
        addRoot(root)
    }

    private fun arrangeRow5() {
        val root = FrameLayout(this)
        val count = minOf(5, bitmaps.size)
        val tiles = (0 until count).map { i -> newTile().apply { setImageBitmap(bitmaps[i]) } }
        tiles.forEach { root.addView(it) }
        root.post {
            val w = root.width; val h = root.height
            val cellW = w / count
            tiles.forEachIndexed { i, v ->
                val l = i * cellW
                v.layout(l, 0, l + cellW, h)
            }
        }
        addRoot(root)
    }

    private fun saveToGallery() {
        val bmp = Bitmap.createBitmap(canvasRoot.width, canvasRoot.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp); canvasRoot.draw(c)

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "Collage_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/GeoTagCamera")
        }
        contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)?.let { uri ->
            contentResolver.openOutputStream(uri)?.use { out -> bmp.compress(Bitmap.CompressFormat.JPEG, 90, out) }
        }
        finish()
    }

    enum class LayoutType { SPLIT_H, SPLIT_V, GRID_1L_2S, GRID_2x2, ROW_4, GRID_3x2, GRID_3_OVER_2, GRID_2_OVER_3, ROW_5 }

    private class LayoutAdapter(
        private val items: List<LayoutType>,
        private val onClick: (LayoutType) -> Unit
    ) : RecyclerView.Adapter<LayoutVH>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LayoutVH {
            val iv = ImageView(parent.context).apply {
                layoutParams = MarginLayoutParams(160, 120).apply { setMargins(12, 8, 12, 8) }
                setBackgroundColor(Color.DKGRAY)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                // Do not set image resource here; will be set in onBindViewHolder
            }
            return LayoutVH(iv, onClick)
        }
        override fun getItemCount() = items.size
        override fun onBindViewHolder(holder: LayoutVH, position: Int) {
            val type = items[position]
            val resId = when (type) {
                LayoutType.SPLIT_H -> R.drawable.ic_layout_split_h
                LayoutType.SPLIT_V -> R.drawable.ic_layout_split_v
                LayoutType.GRID_1L_2S -> R.drawable.ic_layout_grid_1l_2s
                LayoutType.GRID_2x2 -> R.drawable.ic_layout_grid_2x2
                LayoutType.ROW_4 -> R.drawable.ic_layout_row_4
                LayoutType.GRID_3x2 -> R.drawable.ic_layout_grid_3x2
                LayoutType.GRID_3_OVER_2 -> R.drawable.ic_layout_grid_3_over_2
                LayoutType.GRID_2_OVER_3 -> R.drawable.ic_layout_grid_2_over_3
                LayoutType.ROW_5 -> R.drawable.ic_layout_row_5
            }
            holder.iv.setImageResource(resId)
            holder.bind(type)
        }
    }
    private class LayoutVH(val iv: ImageView, val onClick: (LayoutType) -> Unit) :
        RecyclerView.ViewHolder(iv) {
        fun bind(t: LayoutType) {
            iv.setOnClickListener { onClick(t) }
        }
    }
}
    // Custom ImageView with touch gestures for zoom and drag
    class TouchImageView(context: android.content.Context) : androidx.appcompat.widget.AppCompatImageView(context) {
        private val doubleTapDetector = android.view.GestureDetector(context,
            object : android.view.GestureDetector.SimpleOnGestureListener() {
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    animateReset()
                    return true
                }
            })
        private var scaleDetector = android.view.ScaleGestureDetector(context, ScaleListener())
        private var lastX = 0f; private var lastY = 0f
        private var posX = 0f; private var posY = 0f
        private var scaleFactor = 1.0f

        override fun onTouchEvent(ev: MotionEvent): Boolean {
            doubleTapDetector.onTouchEvent(ev)
            scaleDetector.onTouchEvent(ev)
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> { lastX = ev.x; lastY = ev.y }
                MotionEvent.ACTION_MOVE -> {
                    if (!scaleDetector.isInProgress) {
                        val dx = ev.x - lastX; val dy = ev.y - lastY
                        posX += dx; posY += dy
                        invalidate()
                        lastX = ev.x; lastY = ev.y
                    }
                }
            }
            return true
        }

        override fun onDraw(canvas: Canvas) {
            canvas.save()
            canvas.translate(posX, posY)
            canvas.scale(scaleFactor, scaleFactor, (width / 2).toFloat(), (height / 2).toFloat())
            super.onDraw(canvas)
            canvas.restore()
        }

        private fun animateReset() {
            val startScale = scaleFactor
            val startX = posX
            val startY = posY
            val animator = android.animation.ValueAnimator.ofFloat(0f, 1f)
            animator.duration = 200L
            animator.interpolator = android.view.animation.DecelerateInterpolator()
            animator.addUpdateListener { va ->
                val t = va.animatedValue as Float
                scaleFactor = lerp(startScale, 1f, t)
                posX = lerp(startX, 0f, t)
                posY = lerp(startY, 0f, t)
                invalidate()
            }
            animator.start()
        }

        private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

        private inner class ScaleListener : android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: android.view.ScaleGestureDetector): Boolean {
                scaleFactor *= detector.scaleFactor
                scaleFactor = scaleFactor.coerceIn(0.5f, 3.0f)
                invalidate()
                return true
            }
        }
    }