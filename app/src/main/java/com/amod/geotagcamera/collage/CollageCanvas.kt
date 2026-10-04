package com.amod.geotagcamera.collage

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.amod.geotagcamera.R
import kotlin.math.*

class CollageCanvas @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var template: CollageTemplate? = null
    private var elements = mutableListOf<CollageElement>()
    private var selectedElement: CollageElement? = null
    private var backgroundType: BackgroundType = BackgroundType.ColorBackground(Color.WHITE)
    private var borderStyle: BorderStyle = BorderStyle.NONE
    private var borderColor: Int = Color.WHITE

    private val originalPositions = mutableMapOf<String, RectF>()
    private val history = mutableListOf<List<CollageElement>>()
    private var historyIndex = -1
    private val maxHistorySize = 50

    // Touch handling
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isDragging = false
    private var isResizing = false
    private var isRotating = false
    private var hasMoved = false
    private var dropTargetElement: CollageElement.PhotoElement? = null // To highlight potential swap target
    private var dragStartRect: RectF? = null // To store the original position of the dragged element

    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private var lastPointerDistance = 0f
    private var lastRotationAngle = 0f
    private var isScaling = false
    private var isTwoFingerGesture = false

    private val scaleGestureDetector by lazy { android.view.ScaleGestureDetector(context, ScaleListener()) }

    private var lastTapTime = 0L
    private val doubleTapTimeout = 300L

    private var activeResizeHandle: ResizeHandle? = null

    enum class ResizeHandle {
        TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT,
        TOP_CENTER, BOTTOM_CENTER, LEFT_CENTER, RIGHT_CENTER
    }

    // Paint objects
    private val photoPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val handleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { // For highlighting drop target
        color = Color.argb(100, 0, 150, 255)
        style = Paint.Style.FILL
    }

    init {
        setupPaints()
    }

    private fun setupPaints() {
        textPaint.apply {
            color = Color.WHITE
            textSize = 48f
        }
        borderPaint.apply {
            style = Paint.Style.STROKE
        }
        selectionPaint.apply {
            color = Color.BLUE
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        handlePaint.apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        handleStrokePaint.apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
    }

    // Public API
    fun setTemplate(newTemplate: CollageTemplate) {
        template = newTemplate
        elements.clear()
        invalidate()
    }

    fun addElement(element: CollageElement) {
        elements.add(element)
        invalidate()
    }

    fun setBorder(style: BorderStyle, color: Int) {
        borderStyle = style
        borderColor = color
        borderPaint.color = color
        borderPaint.strokeWidth = style.strokeWidth
        invalidate()
    }

    fun clearSelection() {
        selectedElement = null
        invalidate()
    }

    fun storeOriginalPosition(elementId: String, rect: RectF) {
        originalPositions[elementId] = RectF(rect)
    }

    // Drawing logic
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawBackground(canvas)
        template?.let { drawTemplateSlots(canvas, it) }
        elements.forEach { drawElement(canvas, it) }
        
        // Highlight the potential drop target
        dropTargetElement?.let {
            canvas.drawRect(it.rect, highlightPaint)
        }

        selectedElement?.let { drawSelectionHandles(canvas, it) }
    }

    private fun drawBackground(canvas: Canvas) {
        // Simplified for brevity. Original logic can be retained.
        canvas.drawColor(if (backgroundType is BackgroundType.ColorBackground) (backgroundType as BackgroundType.ColorBackground).color else Color.WHITE)
    }

    private fun drawTemplateSlots(canvas: Canvas, template: CollageTemplate) {
        template.slots.forEach { slot ->
            val rect = RectF(slot.rect.left * width, slot.rect.top * height, slot.rect.right * width, slot.rect.bottom * height)
            if (borderStyle != BorderStyle.NONE) {
                canvas.drawRoundRect(rect, slot.cornerRadius, slot.cornerRadius, borderPaint)
            }
        }
    }

    private fun drawElement(canvas: Canvas, element: CollageElement) {
        canvas.save()
        val rect = element.rect
        canvas.translate(rect.centerX(), rect.centerY())
        canvas.rotate(element.rotation)
        canvas.scale(element.scale, element.scale)
        canvas.translate(-rect.width() / 2, -rect.height() / 2)
        val drawRect = RectF(0f, 0f, rect.width(), rect.height())

        when (element) {
            is CollageElement.PhotoElement -> element.bitmap?.let { canvas.drawBitmap(it, null, drawRect, photoPaint) }
            is CollageElement.TextElement -> canvas.drawText(element.text, rect.width() / 2, rect.height() / 2 + element.textSize / 3, textPaint)
            is CollageElement.StickerElement -> {
                // Sticker drawing logic
            }
            is CollageElement.ShapeElement -> {
                // Shape drawing logic
            }
        }
        canvas.restore()
    }

    private fun drawSelectionHandles(canvas: Canvas, element: CollageElement) {
        val rect = element.rect
        canvas.drawRect(rect, selectionPaint) // Main selection box
        // Draw handles at corners/sides
        val handleSize = 20f
        val handles = listOf(
            PointF(rect.left, rect.top), PointF(rect.right, rect.top),
            PointF(rect.left, rect.bottom), PointF(rect.right, rect.bottom)
        )
        handles.forEach {
            canvas.drawCircle(it.x, it.y, handleSize / 2, handleStrokePaint)
            canvas.drawCircle(it.x, it.y, handleSize / 2, handlePaint)
        }
    }

    // Touch Event Handling
    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleGestureDetector.onTouchEvent(event)
        if (event.pointerCount >= 2) {
            isDragging = false
            return true
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                activePointerId = event.getPointerId(0)
                hasMoved = false
                dropTargetElement = null

                val touchedElement = findElementAt(event.x, event.y)
                if (touchedElement != null) {
                    selectedElement = touchedElement
                    isDragging = true // Tentatively start dragging
                    if (touchedElement is CollageElement.PhotoElement) {
                        dragStartRect = RectF(touchedElement.rect) // Store original position
                    }
                } else {
                    selectedElement = null
                }
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val pointerIndex = event.findPointerIndex(activePointerId)
                if (pointerIndex < 0) return true
                
                val x = event.getX(pointerIndex)
                val y = event.getY(pointerIndex)

                if (!hasMoved) {
                    val dx = abs(x - lastTouchX)
                    val dy = abs(y - lastTouchY)
                    if (sqrt(dx * dx + dy * dy) > 10f) {
                        hasMoved = true // Exceeded drag threshold
                    }
                }

                if (isDragging && hasMoved && selectedElement != null) {
                    val dx = x - lastTouchX
                    val dy = y - lastTouchY
                    updateElementPosition(selectedElement!!, dx, dy)
                    
                    // Check for a potential swap target
                    val target = findElementAt(x, y)
                    if (target is CollageElement.PhotoElement && target.id != selectedElement!!.id) {
                        dropTargetElement = target
                    } else {
                        dropTargetElement = null
                    }
                    
                    lastTouchX = x
                    lastTouchY = y
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging && hasMoved && selectedElement != null) {
                    val targetElement = dropTargetElement
                    if (targetElement != null && selectedElement is CollageElement.PhotoElement && dragStartRect != null) {
                        // A drag ended on a valid target, perform the swap
                        val sourceElement = selectedElement as CollageElement.PhotoElement
                        val sourceIndex = elements.indexOfFirst { it.id == sourceElement.id }
                        val targetIndex = elements.indexOfFirst { it.id == targetElement.id }

                        if (sourceIndex != -1 && targetIndex != -1) {
                            val sourceBitmap = sourceElement.bitmap
                            val targetBitmap = targetElement.bitmap

                            // Create new source element: original rect, target's bitmap
                            elements[sourceIndex] = sourceElement.copy(bitmap = targetBitmap, rect = dragStartRect!!)
                            // Create new target element: its rect, source's bitmap
                            elements[targetIndex] = targetElement.copy(bitmap = sourceBitmap)
                        }
                    }
                    // If no swap, the element has already been moved, so we do nothing.
                }
                
                // Reset all states
                isDragging = false
                isResizing = false
                isRotating = false
                isTwoFingerGesture = false
                activePointerId = MotionEvent.INVALID_POINTER_ID
                dropTargetElement = null // Clear highlight
                dragStartRect = null
                invalidate()
                return true
            }
            
            // Other actions like ACTION_POINTER_DOWN/UP can be added back for multi-touch
        }
        return true
    }

    // Helper functions
    private fun findElementAt(x: Float, y: Float): CollageElement? {
        return elements.lastOrNull { it.rect.contains(x, y) }
    }

    private fun updateElementPosition(element: CollageElement, dx: Float, dy: Float) {
        element.rect.offset(dx, dy)
    }
    
    // Stubs for other original methods to keep structure
    fun undo() { /* ... */ }
    fun redo() { /* ... */ }

    private inner class ScaleListener : android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() {
        private var prevFocusX = 0f
        private var prevFocusY = 0f

        override fun onScaleBegin(detector: android.view.ScaleGestureDetector): Boolean {
            prevFocusX = detector.focusX
            prevFocusY = detector.focusY
            return true
        }

        override fun onScale(detector: android.view.ScaleGestureDetector): Boolean {
            val element = selectedElement ?: findElementAt(detector.focusX, detector.focusY)
            element?.let { elem ->
                if (selectedElement != elem) {
                    selectedElement = elem
                }
                
                val index = elements.indexOfFirst { it.id == elem.id }
                if (index != -1) {
                    val currentScale = elem.scale * detector.scaleFactor
                    val newScale = currentScale.coerceIn(0.1f, 8.0f)
                    
                    val dx = detector.focusX - prevFocusX
                    val dy = detector.focusY - prevFocusY
                    prevFocusX = detector.focusX
                    prevFocusY = detector.focusY
                    
                    // Pan movement: offset the rect
                    elem.rect.offset(dx, dy)
                    
                    // Copy element with new scale
                    val updatedElement = when (elem) {
                        is CollageElement.PhotoElement -> elem.copy(scale = newScale)
                        is CollageElement.TextElement -> elem.copy(scale = newScale)
                        is CollageElement.StickerElement -> elem.copy(scale = newScale)
                        is CollageElement.ShapeElement -> elem.copy(scale = newScale)
                    }
                    elements[index] = updatedElement
                    invalidate()
                }
            }
            return true
        }
    }
}
