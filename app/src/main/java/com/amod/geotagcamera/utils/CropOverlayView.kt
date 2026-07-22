package com.amod.geotagcamera.utils

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot

class CropOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Crop fractions relative to view width/height
    var cropLeft = 0.05f
    var cropTop = 0.05f
    var cropRight = 0.95f
    var cropBottom = 0.95f

    var imageBounds: RectF? = null

    private val maskPaint = Paint().apply {
        color = Color.parseColor("#99000000") // Balanced semi-transparent black mask
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint().apply {
        color = Color.parseColor("#1976D2") // Material Blue
        strokeWidth = 3f * resources.displayMetrics.density
        style = Paint.Style.STROKE
    }

    private val handlePaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val handleBorderPaint = Paint().apply {
        color = Color.parseColor("#1976D2")
        strokeWidth = 2f * resources.displayMetrics.density
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val handleRadius = 12f * resources.displayMetrics.density
    private val touchThreshold = 28f * resources.displayMetrics.density

    private enum class Handle {
        NONE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, LEFT, TOP, RIGHT, BOTTOM
    }

    private var activeHandle = Handle.NONE
    private var lastX = 0f
    private var lastY = 0f

    fun resetCrop() {
        val bounds = imageBounds
        val w = width.toFloat()
        val h = height.toFloat()
        if (bounds != null && w > 0 && h > 0) {
            val padding = 8f * resources.displayMetrics.density
            cropLeft = (bounds.left + padding) / w
            cropTop = (bounds.top + padding) / h
            cropRight = (bounds.right - padding) / w
            cropBottom = (bounds.bottom - padding) / h
        } else {
            cropLeft = 0.05f
            cropTop = 0.05f
            cropRight = 0.95f
            cropBottom = 0.95f
        }
        invalidate()
    }

    fun initCropBounds(bounds: RectF) {
        imageBounds = bounds
        resetCrop()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val left = cropLeft * w
        val top = cropTop * h
        val right = cropRight * w
        val bottom = cropBottom * h

        val bounds = imageBounds ?: RectF(0f, 0f, w, h)

        // Draw mask outside the crop window, constrained by the image bounds
        canvas.save()
        canvas.clipRect(bounds)
        
        // Top mask
        canvas.drawRect(bounds.left, bounds.top, bounds.right, top, maskPaint)
        // Bottom mask
        canvas.drawRect(bounds.left, bottom, bounds.right, bounds.bottom, maskPaint)
        // Left mask
        canvas.drawRect(bounds.left, top, left, bottom, maskPaint)
        // Right mask
        canvas.drawRect(right, top, bounds.right, bottom, maskPaint)
        
        canvas.restore()

        // Draw crop area border frame
        canvas.drawRect(left, top, right, bottom, borderPaint)

        // Draw corner handles
        canvas.drawCircle(left, top, handleRadius, handlePaint)
        canvas.drawCircle(left, top, handleRadius, handleBorderPaint)

        canvas.drawCircle(right, top, handleRadius, handlePaint)
        canvas.drawCircle(right, top, handleRadius, handleBorderPaint)

        canvas.drawCircle(left, bottom, handleRadius, handlePaint)
        canvas.drawCircle(left, bottom, handleRadius, handleBorderPaint)

        canvas.drawCircle(right, bottom, handleRadius, handlePaint)
        canvas.drawCircle(right, bottom, handleRadius, handleBorderPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return false

        val left = cropLeft * w
        val top = cropTop * h
        val right = cropRight * w
        val bottom = cropBottom * h

        val bounds = imageBounds ?: RectF(0f, 0f, w, h)
        val leftBound = bounds.left
        val rightBound = bounds.right
        val topBound = bounds.top
        val bottomBound = bounds.bottom

        val minSize = 40f * resources.displayMetrics.density

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // Check corners first (highest priority)
                activeHandle = when {
                    hypot(x - left, y - top) < touchThreshold -> Handle.TOP_LEFT
                    hypot(x - right, y - top) < touchThreshold -> Handle.TOP_RIGHT
                    hypot(x - left, y - bottom) < touchThreshold -> Handle.BOTTOM_LEFT
                    hypot(x - right, y - bottom) < touchThreshold -> Handle.BOTTOM_RIGHT
                    // Check edges
                    Math.abs(x - left) < touchThreshold && y in (top - touchThreshold)..(bottom + touchThreshold) -> Handle.LEFT
                    Math.abs(x - right) < touchThreshold && y in (top - touchThreshold)..(bottom + touchThreshold) -> Handle.RIGHT
                    Math.abs(y - top) < touchThreshold && x in (left - touchThreshold)..(right + touchThreshold) -> Handle.TOP
                    Math.abs(y - bottom) < touchThreshold && x in (left - touchThreshold)..(right + touchThreshold) -> Handle.BOTTOM
                    else -> Handle.NONE
                }
                if (activeHandle != Handle.NONE) {
                    lastX = x
                    lastY = y
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (activeHandle != Handle.NONE) {
                    val dx = x - lastX
                    val dy = y - lastY
                    lastX = x
                    lastY = y

                    when (activeHandle) {
                        Handle.TOP_LEFT -> {
                            val newLeft = (left + dx).coerceIn(leftBound, right - minSize)
                            val newTop = (top + dy).coerceIn(topBound, bottom - minSize)
                            cropLeft = newLeft / w
                            cropTop = newTop / h
                        }
                        Handle.TOP_RIGHT -> {
                            val newRight = (right + dx).coerceIn(left + minSize, rightBound)
                            val newTop = (top + dy).coerceIn(topBound, bottom - minSize)
                            cropRight = newRight / w
                            cropTop = newTop / h
                        }
                        Handle.BOTTOM_LEFT -> {
                            val newLeft = (left + dx).coerceIn(leftBound, right - minSize)
                            val newBottom = (bottom + dy).coerceIn(top + minSize, bottomBound)
                            cropLeft = newLeft / w
                            cropBottom = newBottom / h
                        }
                        Handle.BOTTOM_RIGHT -> {
                            val newRight = (right + dx).coerceIn(left + minSize, rightBound)
                            val newBottom = (bottom + dy).coerceIn(top + minSize, bottomBound)
                            cropRight = newRight / w
                            cropBottom = newBottom / h
                        }
                        Handle.LEFT -> {
                            val newLeft = (left + dx).coerceIn(leftBound, right - minSize)
                            cropLeft = newLeft / w
                        }
                        Handle.RIGHT -> {
                            val newRight = (right + dx).coerceIn(left + minSize, rightBound)
                            cropRight = newRight / w
                        }
                        Handle.TOP -> {
                            val newTop = (top + dy).coerceIn(topBound, bottom - minSize)
                            cropTop = newTop / h
                        }
                        Handle.BOTTOM -> {
                            val newBottom = (bottom + dy).coerceIn(top + minSize, bottomBound)
                            cropBottom = newBottom / h
                        }
                        Handle.NONE -> {}
                    }
                    invalidate()
                    onCropRectChangedListener?.invoke()
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activeHandle = Handle.NONE
            }
        }
        return super.onTouchEvent(event)
    }

    var onCropRectChangedListener: (() -> Unit)? = null
}
