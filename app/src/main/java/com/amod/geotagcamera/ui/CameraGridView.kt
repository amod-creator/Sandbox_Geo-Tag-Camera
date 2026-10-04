package com.amod.geotagcamera.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs

class CameraGridView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val linePaint = Paint().apply {
        color = Color.parseColor("#80FFFFFF") // 50% translucent white
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }

    private val shadowPaint = Paint().apply {
        color = Color.parseColor("#40000000") // Subtle drop shadow
        strokeWidth = 4f
        style = Paint.Style.STROKE
    }

    private val levelCenterPaint = Paint().apply {
        color = Color.parseColor("#A0FFFFFF")
        strokeWidth = 3f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val levelLinePaint = Paint().apply {
        color = Color.parseColor("#D0FFFFFF")
        strokeWidth = 4f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private var gridMode: String = "OFF" // "OFF", "3X3", "PHI"
    private var levelEnabled: Boolean = false
    private var currentRoll: Float = 0f // Degrees
    var ratioSetting: String = "4:3"

    fun setGridMode(mode: String) {
        gridMode = mode.uppercase()
        invalidate()
    }

    fun setLevelEnabled(enabled: Boolean) {
        levelEnabled = enabled
        invalidate()
    }

    fun updateRoll(roll: Float) {
        if (levelEnabled) {
            currentRoll = roll
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        // Draw 1:1 aspect ratio square crop masks if enabled
        if (ratioSetting == "1:1") {
            val size = Math.min(w, h)
            val top = (h - size) / 2f
            val bottom = top + size
            val maskPaint = Paint().apply {
                color = Color.parseColor("#A0000000") // 60% translucent black mask
                style = Paint.Style.FILL
            }
            // Draw top mask
            canvas.drawRect(0f, 0f, w, top, maskPaint)
            // Draw bottom mask
            canvas.drawRect(0f, bottom, w, h, maskPaint)
            // Draw square border
            val borderPaint = Paint().apply {
                color = Color.parseColor("#80FFFFFF")
                strokeWidth = 2f
                style = Paint.Style.STROKE
            }
            canvas.drawRect(0f, top, w, bottom, borderPaint)
        }

        // 1. Draw compositional grid lines if enabled
        if (gridMode != "OFF") {
            val r1: Float
            val r2: Float

            if (gridMode == "PHI") {
                r1 = 0.382f
                r2 = 0.618f
            } else {
                r1 = 1f / 3f
                r2 = 2f / 3f
            }

            val x1 = w * r1
            val x2 = w * r2
            val y1 = h * r1
            val y2 = h * r2

            canvas.drawLine(0f, y1, w, y1, shadowPaint)
            canvas.drawLine(0f, y1, w, y1, linePaint)

            canvas.drawLine(0f, y2, w, y2, shadowPaint)
            canvas.drawLine(0f, y2, w, y2, linePaint)

            canvas.drawLine(x1, 0f, x1, h, shadowPaint)
            canvas.drawLine(x1, 0f, x1, h, linePaint)

            canvas.drawLine(x2, 0f, x2, h, shadowPaint)
            canvas.drawLine(x2, 0f, x2, h, linePaint)
        }

        // 2. Draw real-time alignment level if enabled
        if (levelEnabled) {
            val centerX = w / 2f
            val centerY = h / 2f

            // Determine line color based on alignment perfection (within 1 degree)
            val isAligned = abs(currentRoll) <= 1.0f
            if (isAligned) {
                levelLinePaint.color = Color.parseColor("#00E676") // Vibrant Neon Green
                levelCenterPaint.color = Color.parseColor("#00E676")
            } else {
                levelLinePaint.color = Color.parseColor("#D0FFFFFF") // Clean White
                levelCenterPaint.color = Color.parseColor("#80FFFFFF")
            }

            // Draw center static guidelines (reticle target)
            canvas.drawCircle(centerX, centerY, 30f, levelCenterPaint)
            // Left and right static wings
            canvas.drawLine(centerX - 80f, centerY, centerX - 40f, centerY, levelCenterPaint)
            canvas.drawLine(centerX + 40f, centerY, centerX + 80f, centerY, levelCenterPaint)

            // Draw dynamic leveling indicator line (rotated)
            canvas.save()
            canvas.translate(centerX, centerY)
            canvas.rotate(-currentRoll)
            // Draw horizontal rotating level line
            canvas.drawLine(-110f, 0f, 110f, 0f, levelLinePaint)
            // Left/right mini tick tips
            canvas.drawLine(-110f, -10f, -110f, 10f, levelLinePaint)
            canvas.drawLine(110f, -10f, 110f, 10f, levelLinePaint)
            canvas.restore()
        }
    }
}
