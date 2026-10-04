package com.amod.geotagcamera.collage

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.*

/**
 * Professional CollageView - Based on industry-standard collage maker apps
 * Features: Drag & drop, pinch zoom, rotation, swap photos, borders, backgrounds
 */
class ProfessionalCollageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Photo slots
    private val photoSlots = mutableListOf<PhotoSlot>()
    private var currentTemplate: CollageTemplate? = null

    // Selected slot for editing
    private var selectedSlot: PhotoSlot? = null
    private var draggedSlot: PhotoSlot? = null
    private var isDragging = false
    private var dragShadowX = 0f
    private var dragShadowY = 0f

    // Touch handling
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private var lastTapTime = 0L
    private var tapCount = 0

    // Gestures
    private val scaleDetector = ScaleGestureDetector(context, ScaleListener())
    private var rotationGestureDetector: RotationGestureDetector? = null

    // Border and background
    var borderWidth = 8f
    var borderColor = Color.WHITE
    var borderStyleType = BorderStyleType.SOLID
    var borderSecondaryColor = Color.TRANSPARENT
    var collageBackground = Color.WHITE
    var backgroundPattern: Bitmap? = null

    // Paints
    private val photoPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00BCD4")
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }

    init {
        rotationGestureDetector = RotationGestureDetector(RotationListener())
    }

    /**
     * Photo slot class - holds bitmap and transformation data
     */
    data class PhotoSlot(
        var bitmap: Bitmap? = null,
        val bounds: RectF,
        var matrix: Matrix = Matrix(),
        var scale: Float = 1f,
        var rotation: Float = 0f,
        var offsetX: Float = 0f,
        var offsetY: Float = 0f,
        var cornerRadius: Float = 0f
    ) {
        fun containsPoint(x: Float, y: Float): Boolean {
            return bounds.contains(x, y)
        }

        fun resetTransform() {
            matrix.reset()
            scale = 1f
            rotation = 0f
            offsetX = 0f
            offsetY = 0f
        }
    }

    /**
     * Set template and initialize photo slots
     */
    fun setTemplate(template: CollageTemplate) {
        currentTemplate = template
        photoSlots.clear()

        template.slots.forEach { slot ->
            val bounds = RectF(
                slot.rect.left * width,
                slot.rect.top * height,
                slot.rect.right * width,
                slot.rect.bottom * height
            )
            photoSlots.add(PhotoSlot(
                bounds = bounds,
                cornerRadius = slot.cornerRadius
            ))
        }
        invalidate()
    }

    /**
     * Add photo to next available slot
     */
    fun addPhoto(bitmap: Bitmap): Boolean {
        val emptySlot = photoSlots.firstOrNull { it.bitmap == null }
        if (emptySlot != null) {
            emptySlot.bitmap = bitmap
            emptySlot.resetTransform()
            // Auto-scale to fit
            autoScalePhoto(emptySlot)
            invalidate()
            return true
        }
        return false
    }

    /**
     * Auto-scale photo to fit slot
     */
    private fun autoScalePhoto(slot: PhotoSlot) {
        val bitmap = slot.bitmap ?: return

        val bitmapRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
        val slotRatio = slot.bounds.width() / slot.bounds.height()

        slot.scale = if (bitmapRatio > slotRatio) {
            slot.bounds.height() / bitmap.height
        } else {
            slot.bounds.width() / bitmap.width
        }
    }

    /**
     * Swap photos
     */
    fun swapPhotos(slot1: PhotoSlot, slot2: PhotoSlot) {
        val tempBitmap = slot1.bitmap
        slot1.bitmap = slot2.bitmap
        slot2.bitmap = tempBitmap

        // Reset transforms after swap
        slot1.resetTransform()
        slot2.resetTransform()
        autoScalePhoto(slot1)
        autoScalePhoto(slot2)
        invalidate()
    }

    /**
     * Swap with nearest slot (for double-tap)
     */
    private fun swapWithNearestSlot(currentSlot: PhotoSlot) {
        // Find nearest slot with a photo
        val nearest = photoSlots
            .filter { it != currentSlot && it.bitmap != null }
            .minByOrNull { slot ->
                val dx = slot.bounds.centerX() - currentSlot.bounds.centerX()
                val dy = slot.bounds.centerY() - currentSlot.bounds.centerY()
                sqrt(dx * dx + dy * dy)
            }

        if (nearest != null) {
            swapPhotos(currentSlot, nearest)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw background
        drawBackground(canvas)

        // Draw photo slots with borders
        photoSlots.forEach { slot ->
            drawPhotoSlot(canvas, slot)
        }

        // Draw drag shadow/preview if currently dragging
        if (isDragging && selectedSlot != null) {
            drawDragShadow(canvas, selectedSlot!!)
        }
    }

    private fun drawBackground(canvas: Canvas) {
        if (backgroundPattern != null) {
            val shader = BitmapShader(backgroundPattern!!, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            val paint = Paint().apply { this.shader = shader }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        } else {
            canvas.drawColor(collageBackground)
        }
    }

    /**
     * Draw drag shadow - semi-transparent preview of photo being dragged
     */
    private fun drawDragShadow(canvas: Canvas, slot: PhotoSlot) {
        slot.bitmap?.let { bitmap ->
            canvas.save()

            // Draw semi-transparent background for shadow
            val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#000000")
                alpha = 50
                style = Paint.Style.FILL
            }

            // Draw rounded rectangle shadow at drag position
            val shadowSize = 100f
            val shadowRect = RectF(
                dragShadowX - shadowSize / 2,
                dragShadowY - shadowSize / 2,
                dragShadowX + shadowSize / 2,
                dragShadowY + shadowSize / 2
            )
            canvas.drawRoundRect(shadowRect, 8f, 8f, shadowPaint)

            // Draw the photo preview at drag position
            canvas.translate(dragShadowX, dragShadowY)
            canvas.scale(0.6f, 0.6f) // Scale down for preview
            canvas.rotate(slot.rotation)

            val left = -bitmap.width / 2f
            val top = -bitmap.height / 2f

            // Draw with transparency
            val dragPhotoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                alpha = 220
            }
            canvas.drawBitmap(bitmap, left, top, dragPhotoPaint)

            canvas.restore()
        }
    }

    private fun drawPhotoSlot(canvas: Canvas, slot: PhotoSlot) {
        canvas.save()

        // Draw border first based on style type
        if (borderWidth > 0) {
            drawBorder(canvas, slot)
        }

        // Clip to slot bounds with corner radius
        val path = Path()
        path.addRoundRect(slot.bounds, slot.cornerRadius, slot.cornerRadius, Path.Direction.CW)
        canvas.clipPath(path)

        // Draw photo if available
        slot.bitmap?.let { bitmap ->
            canvas.save()

            // Apply transformations
            val centerX = slot.bounds.centerX()
            val centerY = slot.bounds.centerY()

            canvas.translate(centerX + slot.offsetX, centerY + slot.offsetY)
            canvas.rotate(slot.rotation)
            canvas.scale(slot.scale, slot.scale)

            val left = -bitmap.width / 2f
            val top = -bitmap.height / 2f

            // If this slot is being dragged, fade it out
            val paintAlpha = if (isDragging && slot == selectedSlot) 100 else 255
            photoPaint.alpha = paintAlpha

            canvas.drawBitmap(bitmap, left, top, photoPaint)

            // Reset alpha for next draw
            photoPaint.alpha = 255
            canvas.restore()
        }

        canvas.restore()

        // Draw selection highlight
        if (slot == selectedSlot) {
            val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#2196F3")
                style = Paint.Style.STROKE
                strokeWidth = 8f
            }
            canvas.drawRoundRect(
                slot.bounds,
                slot.cornerRadius,
                slot.cornerRadius,
                highlightPaint
            )
        }

        // Draw dragging indicator
        if (slot == draggedSlot && slot != selectedSlot) {
            val dragPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#FF9800")
                style = Paint.Style.STROKE
                strokeWidth = 6f
            }
            canvas.drawRoundRect(
                slot.bounds,
                slot.cornerRadius,
                slot.cornerRadius,
                dragPaint
            )
        }
    }

    /**
     * Draw border with different styles
     */
    private fun drawBorder(canvas: Canvas, slot: PhotoSlot) {
        val borderBounds = RectF(
            slot.bounds.left - borderWidth / 2,
            slot.bounds.top - borderWidth / 2,
            slot.bounds.right + borderWidth / 2,
            slot.bounds.bottom + borderWidth / 2
        )

        when (borderStyleType) {
            BorderStyleType.SOLID -> {
                // Solid color border
                borderPaint.color = borderColor
                canvas.drawRoundRect(borderBounds, slot.cornerRadius, slot.cornerRadius, borderPaint)
            }
            BorderStyleType.DASHED -> {
                // Dashed border
                val dashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = borderWidth
                    color = borderColor
                    pathEffect = android.graphics.DashPathEffect(floatArrayOf(10f, 10f), 0f)
                }
                canvas.drawRoundRect(borderBounds, slot.cornerRadius, slot.cornerRadius, dashPaint)
            }
            BorderStyleType.DOTTED -> {
                // Dotted border
                val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = borderWidth
                    color = borderColor
                    pathEffect = android.graphics.DashPathEffect(floatArrayOf(5f, 5f), 0f)
                }
                canvas.drawRoundRect(borderBounds, slot.cornerRadius, slot.cornerRadius, dotPaint)
            }
            BorderStyleType.DUAL_TONE -> {
                // Dual-tone border (outer and inner)
                val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = borderWidth / 2
                    color = borderColor
                }
                val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = borderWidth / 2
                    color = borderSecondaryColor
                }

                // Draw outer border
                canvas.drawRoundRect(borderBounds, slot.cornerRadius, slot.cornerRadius, outerPaint)

                // Draw inner border (slightly inset)
                val innerBounds = RectF(
                    slot.bounds.left + borderWidth / 4,
                    slot.bounds.top + borderWidth / 4,
                    slot.bounds.right - borderWidth / 4,
                    slot.bounds.bottom - borderWidth / 4
                )
                canvas.drawRoundRect(innerBounds, slot.cornerRadius, slot.cornerRadius, innerPaint)
            }
            BorderStyleType.NONE -> {
                // No border - do nothing
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        rotationGestureDetector?.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                activePointerId = event.getPointerId(0)

                // Find touched slot
                val touchedSlot = photoSlots.firstOrNull { it.containsPoint(event.x, event.y) }

                // Handle double-tap for swapping
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastTapTime < 300) {
                    tapCount++
                    if (tapCount == 2 && touchedSlot != null) {
                        // Double tap - swap with nearest slot
                        swapWithNearestSlot(touchedSlot)
                        tapCount = 0
                        isDragging = false
                    }
                } else {
                    tapCount = 1
                    lastTapTime = currentTime
                }

                selectedSlot = touchedSlot
                draggedSlot = touchedSlot
                isDragging = false
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val pointerIndex = event.findPointerIndex(activePointerId)
                if (pointerIndex == -1) return false

                val x = event.getX(pointerIndex)
                val y = event.getY(pointerIndex)

                // Only move if single touch (not scaling or rotating)
                if (event.pointerCount == 1 && selectedSlot != null) {
                    // Enable dragging when movement detected
                    isDragging = true
                    dragShadowX = x
                    dragShadowY = y

                    selectedSlot?.let { slot ->
                        slot.offsetX += x - lastTouchX
                        slot.offsetY += y - lastTouchY

                        // Check if dragging over another slot for swap
                        val targetSlot = photoSlots.firstOrNull {
                            it != slot && it.containsPoint(x, y) && it.bitmap != null
                        }
                        if (targetSlot != null) {
                            draggedSlot = targetSlot
                        }
                        invalidate()
                    }
                }

                lastTouchX = x
                lastTouchY = y
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // Perform swap if dragged over another slot
                if (draggedSlot != null && selectedSlot != null && draggedSlot != selectedSlot) {
                    swapPhotos(selectedSlot!!, draggedSlot!!)
                }

                activePointerId = MotionEvent.INVALID_POINTER_ID
                draggedSlot = null
                isDragging = false
                invalidate()
                return true
            }
        }

        return super.onTouchEvent(event)
    }

    /**
     * Scale gesture listener
     */
    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        private var prevFocusX = 0f
        private var prevFocusY = 0f

        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            prevFocusX = detector.focusX
            prevFocusY = detector.focusY
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val slot = selectedSlot ?: photoSlots.firstOrNull { it.containsPoint(detector.focusX, detector.focusY) }
            slot?.let { s ->
                // Update selectedSlot to the slotted focus
                if (selectedSlot != s) {
                    selectedSlot = s
                }

                // Update scale - can reduce the size down to 0.1f!
                s.scale *= detector.scaleFactor
                s.scale = s.scale.coerceIn(0.1f, 8.0f)

                // Update pan / translate on drag with two fingers
                val dx = detector.focusX - prevFocusX
                val dy = detector.focusY - prevFocusY
                prevFocusX = detector.focusX
                prevFocusY = detector.focusY

                s.offsetX += dx
                s.offsetY += dy
                invalidate()
            }
            return true
        }
    }

    /**
     * Rotation gesture detector
     */
    private inner class RotationGestureDetector(private val listener: RotationListener) {
        private var prevAngle = 0f

        fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.pointerCount < 2) return false

            val angle = calculateAngle(event)

            when (event.actionMasked) {
                MotionEvent.ACTION_POINTER_DOWN -> {
                    prevAngle = angle
                }
                MotionEvent.ACTION_MOVE -> {
                    val delta = angle - prevAngle
                    listener.onRotation(delta)
                    prevAngle = angle
                }
            }
            return true
        }

        private fun calculateAngle(event: MotionEvent): Float {
            val deltaX = (event.getX(0) - event.getX(1)).toDouble()
            val deltaY = (event.getY(0) - event.getY(1)).toDouble()
            return Math.toDegrees(atan2(deltaY, deltaX)).toFloat()
        }
    }

    /**
     * Rotation listener
     */
    private inner class RotationListener {
        fun onRotation(delta: Float) {
            selectedSlot?.let { slot ->
                slot.rotation += delta
                invalidate()
            }
        }
    }

    /**
     * Generate final collage bitmap
     */
    fun generateCollageBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        draw(canvas)
        return bitmap
    }

    /**
     * Clear all photos
     */
    fun clearAll() {
        photoSlots.forEach { it.bitmap = null }
        selectedSlot = null
        invalidate()
    }

    /**
     * Get photo slots
     */
    fun getPhotoSlots() = photoSlots.toList()
}

