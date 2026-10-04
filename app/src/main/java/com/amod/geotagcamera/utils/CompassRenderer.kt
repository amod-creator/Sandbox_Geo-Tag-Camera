package com.amod.geotagcamera.utils

import android.graphics.*
import kotlin.math.cos
import kotlin.math.sin

object CompassRenderer {

    fun getCardinalDirection(degrees: Float): String {
        val normalized = (degrees % 360 + 360) % 360
        return when {
            normalized >= 337.5 || normalized < 22.5 -> "N"
            normalized >= 22.5 && normalized < 67.5 -> "NE"
            normalized >= 67.5 && normalized < 112.5 -> "E"
            normalized >= 112.5 && normalized < 157.5 -> "SE"
            normalized >= 157.5 && normalized < 202.5 -> "S"
            normalized >= 202.5 && normalized < 247.5 -> "SW"
            normalized >= 247.5 && normalized < 292.5 -> "W"
            else -> "NW"
        }
    }

    fun getFacingDescription(degrees: Float): String {
        val cardinal = getCardinalDirection(degrees)
        return when (cardinal) {
            "N" -> "Facing North"
            "NE" -> "Facing North-East"
            "E" -> "Facing East"
            "SE" -> "Facing South-East"
            "S" -> "Facing South"
            "SW" -> "Facing South-West"
            "W" -> "Facing West"
            "NW" -> "Facing North-West"
            else -> "Facing South"
        }
    }

    fun drawCompassDial(
        canvas: Canvas,
        bounds: RectF,
        azimuth: Float = 207f
    ) {
        val width = bounds.width()
        val height = bounds.height()
        val cx = bounds.centerX()
        val badgeHeight = height * 0.20f
        val cy = bounds.top + (height - badgeHeight) / 2f
        val radius = minOf(width / 2f, (height - badgeHeight) / 2f) * 0.92f

        // Draw dark dial background
        val dialBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1C1C1E")
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, cy, radius, dialBgPaint)

        // Draw dial outer border
        val dialBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#444446")
            style = Paint.Style.STROKE
            strokeWidth = maxOf(1.5f, radius * 0.03f)
        }
        canvas.drawCircle(cx, cy, radius, dialBorderPaint)

        // Draw ticks and degree numbers around the ring
        val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            strokeWidth = maxOf(1f, radius * 0.02f)
        }
        val cardinalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = radius * 0.18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#B0B0B5")
            textSize = radius * 0.12f
            typeface = Typeface.DEFAULT
            textAlign = Paint.Align.CENTER
        }

        // Draw degree ticks
        for (deg in 0 until 360 step 30) {
            val rad = Math.toRadians((deg - 90).toDouble())
            val cosVal = cos(rad).toFloat()
            val sinVal = sin(rad).toFloat()

            val tickLen = if (deg % 90 == 0) radius * 0.16f else radius * 0.10f
            val startR = radius - tickLen
            val x1 = cx + startR * cosVal
            val y1 = cy + startR * sinVal
            val x2 = cx + (radius - 2f) * cosVal
            val y2 = cy + (radius - 2f) * sinVal

            canvas.drawLine(x1, y1, x2, y2, tickPaint)

            // Number or cardinal letter
            val labelR = radius * 0.68f
            val lx = cx + labelR * cosVal
            val ly = cy + labelR * sinVal - ((cardinalPaint.descent() + cardinalPaint.ascent()) / 2f)

            when (deg) {
                0 -> canvas.drawText("N", lx, ly, cardinalPaint)
                90 -> canvas.drawText("E", lx, ly, cardinalPaint)
                180 -> canvas.drawText("S", lx, ly, cardinalPaint)
                270 -> canvas.drawText("W", lx, ly, cardinalPaint)
                else -> canvas.drawText("$deg", lx, ly, numberPaint)
            }
        }

        // Center readout (e.g. "207° SW")
        val cardinal = getCardinalDirection(azimuth)
        val headingText = "${azimuth.toInt()}° $cardinal"
        val centerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = radius * 0.22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val textY = cy - ((centerTextPaint.descent() + centerTextPaint.ascent()) / 2f)
        canvas.drawText(headingText, cx, textY, centerTextPaint)

        // Bottom pill badge: "Facing South"
        val badgeTop = bounds.bottom - badgeHeight
        val badgeBottom = bounds.bottom
        val badgeW = width * 0.95f
        val badgeLeft = cx - badgeW / 2f
        val badgeRight = cx + badgeW / 2f
        val badgeRadius = badgeHeight * 0.35f

        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#4A4A4C")
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(RectF(badgeLeft, badgeTop, badgeRight, badgeBottom), badgeRadius, badgeRadius, badgePaint)

        val badgeText = getFacingDescription(azimuth)
        val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = badgeHeight * 0.52f
            typeface = Typeface.DEFAULT
            textAlign = Paint.Align.CENTER
        }
        val badgeTextY = (badgeTop + badgeBottom) / 2f - ((badgeTextPaint.descent() + badgeTextPaint.ascent()) / 2f)
        canvas.drawText(badgeText, cx, badgeTextY, badgeTextPaint)
    }

    fun createCompassBitmap(width: Int, height: Int, azimuth: Float = 207f): Bitmap {
        val bitmap = Bitmap.createBitmap(maxOf(1, width), maxOf(1, height), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val bounds = RectF(0f, 0f, width.toFloat(), height.toFloat())
        drawCompassDial(canvas, bounds, azimuth)
        return bitmap
    }
}
