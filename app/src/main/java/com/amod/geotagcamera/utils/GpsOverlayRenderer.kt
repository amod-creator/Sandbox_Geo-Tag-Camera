package com.amod.geotagcamera.utils

import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.pdf.PdfDocument
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.ContextCompat
import com.amod.geotagcamera.R
import java.io.File
import android.graphics.RectF
import android.text.Layout
import android.graphics.Color
import android.graphics.Typeface
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

class GpsOverlayRenderer(private val context: Context) {
    enum class LayoutMode { AUTO, HORIZONTAL, VERTICAL }
    var layoutMode: LayoutMode = LayoutMode.HORIZONTAL

    private val watermarkPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 64f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)
        alpha = 220 // More opaque
    }

    fun drawOnlyOverlay(
        videoWidth: Int,
        videoHeight: Int,
        mapThumbnail: Bitmap?,
        latitude: String,
        longitude: String,
        address: String,
        datetime: String
    ): Bitmap {
        return drawOnlyOverlay(videoWidth, videoHeight, 0, mapThumbnail, latitude, longitude, address, datetime)
    }

    fun drawOnlyOverlay(
        videoWidth: Int,
        videoHeight: Int,
        rotation: Int,
        mapThumbnail: Bitmap?,
        latitude: String,
        longitude: String,
        address: String,
        datetime: String
    ): Bitmap {
        if (rotation == 0) {
            val resultBitmap = Bitmap.createBitmap(videoWidth, videoHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(resultBitmap)

            val isPortrait = videoHeight > videoWidth
            val globalScale = if (isPortrait) 0.9f else 0.8f

            val thumbAreaRatio = if (isPortrait) 0.22f else 0.17f 
            val textAreaRatio = 1f - thumbAreaRatio

            val overlayPadding = videoWidth * 0.02f * globalScale
            val cornerRadius = 36f * globalScale 

            val headerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = videoWidth * 0.04f * globalScale
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val valuePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = videoWidth * 0.032f * globalScale
            }
            val wmPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = videoWidth * 0.025f * globalScale
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                alpha = 200
            }

            val overlayWidth = (if (isPortrait) 0.94f else 0.8f) * videoWidth
            val contentInternalPadding = overlayPadding
            val totalContentWidth = overlayWidth - (contentInternalPadding * 3)
            val fixedThumbWidth = totalContentWidth * thumbAreaRatio
            val textAreaWidth = totalContentWidth * textAreaRatio
            val thumbHeight = mapThumbnail?.let { fixedThumbWidth / (it.width.toFloat() / it.height) } ?: (fixedThumbWidth * 0.8f)

            val cityHeader = extractCityStateCountry(address)
            val cleanLat = cleanCoordinate(latitude, "Lat")
            val cleanLon = cleanCoordinate(longitude, "Long")
            val combinedLatLon = "Lat $cleanLat°   Long $cleanLon°"
            val lineSpacing = videoWidth * 0.008f

            val badgeText = "Ad Free GPS Cam Visit Pro"
            val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = videoWidth * 0.016f * globalScale
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val padX = 4f * globalScale
            val padY = 2f * globalScale

            // Load and draw App Icon in badge
            val appIconDrawable = ContextCompat.getDrawable(context, R.mipmap.ic_launcher_round)
            val appIconBitmap = if (appIconDrawable is BitmapDrawable) {
                appIconDrawable.bitmap
            } else {
                val bitmap = Bitmap.createBitmap(
                    appIconDrawable?.intrinsicWidth ?: 1,
                    appIconDrawable?.intrinsicHeight ?: 1,
                    Bitmap.Config.ARGB_8888
                )
                val appCanvas = Canvas(bitmap)
                appIconDrawable?.setBounds(0, 0, appCanvas.width, appCanvas.height)
                appIconDrawable?.draw(appCanvas)
                bitmap
            }

            val iconSize = badgeTextPaint.textSize * 1.1f
            val iconMarginRight = 2f * globalScale
            val badgeW = iconSize + iconMarginRight + badgeTextPaint.measureText(badgeText) + padX * 2
            val badgeH = maxOf(iconSize, badgeTextPaint.textSize) + padY * 2

            val headerLayout = StaticLayout.Builder.obtain(cityHeader, 0, cityHeader.length, headerPaint, textAreaWidth.toInt()).build()
            
            // Merge all details into a single StaticLayout for uniform line spacing
            val detailsText = "$address\n$combinedLatLon\n$datetime"
            val detailsLayout = StaticLayout.Builder.obtain(detailsText, 0, detailsText.length, valuePaint, textAreaWidth.toInt()).build()

            val textBlockHeight = (headerLayout.height + detailsLayout.height + lineSpacing).toFloat()
            val overlayHeight = max(thumbHeight, textBlockHeight) + (overlayPadding * 2)

            val overlayBottom = videoHeight - overlayPadding
            val overlayTop = overlayBottom - overlayHeight
            val overlayLeft = (videoWidth - overlayWidth) / 2f
            val overlayRight = overlayLeft + overlayWidth

            val bgPaint = Paint().apply {
                color = Color.argb(160, 0, 0, 0)
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(RectF(overlayLeft, overlayTop, overlayRight, overlayBottom), cornerRadius, cornerRadius, bgPaint)

            val watermark = if (isPortrait) "Portrait Mode" else "Landscape Mode"
            val wmWidth = wmPaint.measureText(watermark)
            canvas.drawText(watermark, overlayRight - contentInternalPadding - wmWidth, overlayBottom - contentInternalPadding, wmPaint)

            mapThumbnail?.let {
                val targetThumbWidth = fixedThumbWidth
                val targetThumbHeight = overlayHeight - (overlayPadding * 2)
                
                val scaleX = targetThumbWidth / it.width
                val scaleY = targetThumbHeight / it.height
                val scale = max(scaleX, scaleY)
                
                val dx = (targetThumbWidth - it.width * scale) / 2
                val dy = (targetThumbHeight - it.height * scale) / 2
                
                val thumbX = overlayLeft + contentInternalPadding
                val thumbY = overlayTop + overlayPadding
                
                val thumbMatrix = Matrix()
                thumbMatrix.postScale(scale, scale)
                thumbMatrix.postTranslate(thumbX + dx, thumbY + dy)

                canvas.save()
                val thumbRect = RectF(thumbX, thumbY, thumbX + targetThumbWidth, thumbY + targetThumbHeight)
                val clipPath = android.graphics.Path()
                clipPath.addRoundRect(thumbRect, cornerRadius, cornerRadius, android.graphics.Path.Direction.CW)
                canvas.clipPath(clipPath)
                canvas.drawBitmap(it, thumbMatrix, Paint(Paint.FILTER_BITMAP_FLAG))
                canvas.restore()
            }

            // Draw "Ad Free GPS Cam Visit Pro" badge above the overlay card (aligned to the right side of overlay)
            val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(204, 0, 0, 0) // Semi-transparent black
                style = Paint.Style.FILL
            }

            val badgeRight = overlayRight - contentInternalPadding
            val badgeLeft = badgeRight - badgeW
            val badgeBottom = overlayTop
            val badgeTop = badgeBottom - badgeH

            val badgeRect = RectF(badgeLeft, badgeTop, badgeRight, badgeBottom)
            val badgeRadius = 2f * globalScale
            canvas.drawRoundRect(badgeRect, badgeRadius, badgeRadius, badgePaint)

            // Draw app icon inside the badge
            val iconLeft = badgeLeft + padX
            val iconTop = badgeTop + (badgeH - iconSize) / 2
            val srcRect = Rect(0, 0, appIconBitmap.width, appIconBitmap.height)
            val dstRect = RectF(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize)
            canvas.drawBitmap(appIconBitmap, srcRect, dstRect, Paint(Paint.FILTER_BITMAP_FLAG))

            // Draw badge text
            canvas.drawText(badgeText, iconLeft + iconSize + iconMarginRight, badgeBottom - padY - 0.5f * globalScale, badgeTextPaint)

            val textStartX = overlayLeft + contentInternalPadding + fixedThumbWidth + contentInternalPadding
            // Shift text block slightly up
            val textStartY = overlayTop + (overlayHeight - textBlockHeight) / 2 - 8f * globalScale
            canvas.save()
            canvas.translate(textStartX, textStartY)
            headerLayout.draw(canvas)
            canvas.translate(0f, headerLayout.height.toFloat() + lineSpacing)
            detailsLayout.draw(canvas)
            canvas.restore()

            return resultBitmap
        } else {
            val finalWidth = if (rotation == 90 || rotation == 270) videoHeight else videoWidth
            val finalHeight = if (rotation == 90 || rotation == 270) videoWidth else videoHeight

            val tempBitmap = drawOnlyOverlay(finalWidth, finalHeight, 0, mapThumbnail, latitude, longitude, address, datetime)

            val resultBitmap = Bitmap.createBitmap(videoWidth, videoHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(resultBitmap)

            val matrix = Matrix()
            matrix.postTranslate(-finalWidth / 2f, -finalHeight / 2f)
            matrix.postRotate(-rotation.toFloat())
            matrix.postTranslate(videoWidth / 2f, videoHeight / 2f)

            canvas.drawBitmap(tempBitmap, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
            tempBitmap.recycle()

            return resultBitmap
        }
    }

    fun drawGpsOverlay(
        originalBitmap: Bitmap,
        mapThumbnail: Bitmap?,
        latitude: String,
        longitude: String,
        address: String,
        datetime: String
    ): Bitmap {
        // Determine orientation for watermark
        val watermark = if (originalBitmap.width > originalBitmap.height) "Landscape Mode" else "Portrait Mode"
        return drawGpsOverlayBottom(originalBitmap, mapThumbnail, latitude, longitude, address, datetime, watermark)
    }

    /**
     * Clean labels from coordinates (remove "Lat:", "Lat Lat:", etc.)
     */
    private fun cleanCoordinate(coord: String, prefix: String): String {
        return coord.replace(Regex("(?i)${prefix}:?"), "").replace(Regex("(?i)Lat:?"), "").replace(Regex("(?i)Lon:?"), "").replace(Regex("(?i)Long:?"), "").trim()
    }

    /**
     * Extract city, state, country from address for the bold header.
     */
    private fun extractCityStateCountry(fullAddress: String): String {
        if (fullAddress.isBlank()) return "Location Unavailable"
        val parts = fullAddress.split(",").map { it.trim() }
        return when {
            parts.size >= 3 -> {
                val country = parts.last().replace(Regex("\\d+"), "").trim()
                val state = parts[parts.size - 2].replace(Regex("\\d+"), "").trim()
                val city = parts[parts.size - 3].replace(Regex("\\d+"), "").trim()
                val header = listOf(city, state, country).filter { it.isNotBlank() }.joinToString(", ")
                if (country.equals("India", ignoreCase = true)) "$header \uD83C\uDDEE\uD83C\uDDF3" else header
            }
            parts.size == 2 -> "${parts[0]}, ${parts[1]}"
            else -> fullAddress
        }
    }

    private fun drawGpsOverlayBottom(
        originalBitmap: Bitmap,
        mapThumbnail: Bitmap?,
        latitude: String,
        longitude: String,
        address: String,
        datetime: String,
        watermark: String = ""
    ): Bitmap {
        val resultBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(resultBitmap)

        val isPortrait = resultBitmap.height > resultBitmap.width
        val globalScale = if (isPortrait) 0.9f else 0.8f

        // Further reduced area ratio
        val thumbAreaRatio = if (isPortrait) 0.22f else 0.17f 
        val textAreaRatio = 1f - thumbAreaRatio

        val overlayPadding = resultBitmap.width * 0.02f * globalScale
        val cornerRadius = 36f * globalScale 

        val headerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = resultBitmap.width * 0.04f * globalScale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val valuePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = resultBitmap.width * 0.032f * globalScale
        }
        val wmPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = resultBitmap.width * 0.025f * globalScale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            alpha = 200
        }

        val overlayWidth = (if (isPortrait) 0.94f else 0.8f) * resultBitmap.width
        
        val contentInternalPadding = overlayPadding
        val totalContentWidth = overlayWidth - (contentInternalPadding * 3)
        
        val fixedThumbWidth = totalContentWidth * thumbAreaRatio
        val textAreaWidth = totalContentWidth * textAreaRatio
        val thumbHeight = mapThumbnail?.let { fixedThumbWidth / (it.width.toFloat() / it.height) } ?: (fixedThumbWidth * 0.8f)

        // Balanced and Clean Formatting (Image 1 style)
        val cityHeader = extractCityStateCountry(address)
        val cleanLat = cleanCoordinate(latitude, "Lat")
        val cleanLon = cleanCoordinate(longitude, "Long")
        val combinedLatLon = "Lat $cleanLat°   Long $cleanLon°"
        val lineSpacing = resultBitmap.width * 0.008f

        val badgeText = "Ad Free GPS Cam Visit Pro"
        val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = resultBitmap.width * 0.016f * globalScale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val padX = 4f * globalScale
        val padY = 2f * globalScale

        // Load and draw App Icon in badge
        val appIconDrawable = ContextCompat.getDrawable(context, R.mipmap.ic_launcher_round)
        val appIconBitmap = if (appIconDrawable is BitmapDrawable) {
            appIconDrawable.bitmap
        } else {
            val bitmap = Bitmap.createBitmap(
                appIconDrawable?.intrinsicWidth ?: 1,
                appIconDrawable?.intrinsicHeight ?: 1,
                Bitmap.Config.ARGB_8888
            )
            val appCanvas = Canvas(bitmap)
            appIconDrawable?.setBounds(0, 0, appCanvas.width, appCanvas.height)
            appIconDrawable?.draw(appCanvas)
            bitmap
        }

        val iconSize = badgeTextPaint.textSize * 1.1f
        val iconMarginRight = 2f * globalScale
        val badgeW = iconSize + iconMarginRight + badgeTextPaint.measureText(badgeText) + padX * 2
        val badgeH = maxOf(iconSize, badgeTextPaint.textSize) + padY * 2

        val headerLayout = StaticLayout.Builder.obtain(cityHeader, 0, cityHeader.length, headerPaint, textAreaWidth.toInt()).build()
        
        // Merge all details into a single StaticLayout for uniform line spacing
        val detailsText = "$address\n$combinedLatLon\n$datetime"
        val detailsLayout = StaticLayout.Builder.obtain(detailsText, 0, detailsText.length, valuePaint, textAreaWidth.toInt()).build()

        val textBlockHeight = (headerLayout.height + detailsLayout.height + lineSpacing).toFloat()
        val overlayHeight = max(thumbHeight, textBlockHeight) + (overlayPadding * 2)

        val overlayBottom = resultBitmap.height - overlayPadding
        val overlayTop = overlayBottom - overlayHeight
        val overlayLeft = (resultBitmap.width - overlayWidth) / 2f
        val overlayRight = overlayLeft + overlayWidth

        // Draw Translucent Dark Background (Image 1 style)
        val bgPaint = Paint().apply {
            color = Color.argb(160, 0, 0, 0) // Balanced dark gray, semi-transparent
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(RectF(overlayLeft, overlayTop, overlayRight, overlayBottom), cornerRadius, cornerRadius, bgPaint)

        // Draw Watermark inside overlay for better balance - Move to Bottom Right
        if (watermark.isNotEmpty()) {
            val wmWidth = wmPaint.measureText(watermark)
            canvas.drawText(watermark, overlayRight - contentInternalPadding - wmWidth, overlayBottom - contentInternalPadding, wmPaint)
        }

        // Thumbnail - Dynamically matches background height
        mapThumbnail?.let {
            val targetThumbWidth = fixedThumbWidth
            val targetThumbHeight = overlayHeight - (overlayPadding * 2)
            
            // Center-Crop Scaling Logic: fill the available space without stretching
            val scaleX = targetThumbWidth / it.width
            val scaleY = targetThumbHeight / it.height
            val scale = max(scaleX, scaleY)
            
            val dx = (targetThumbWidth - it.width * scale) / 2
            val dy = (targetThumbHeight - it.height * scale) / 2
            
            val thumbX = overlayLeft + contentInternalPadding
            val thumbY = overlayTop + overlayPadding
            
            val thumbMatrix = Matrix()
            thumbMatrix.postScale(scale, scale)
            thumbMatrix.postTranslate(thumbX + dx, thumbY + dy)

            canvas.save()
            val thumbRect = RectF(thumbX, thumbY, thumbX + targetThumbWidth, thumbY + targetThumbHeight)
            val clipPath = android.graphics.Path()
            clipPath.addRoundRect(thumbRect, cornerRadius, cornerRadius, android.graphics.Path.Direction.CW)
            canvas.clipPath(clipPath)
            canvas.drawBitmap(it, thumbMatrix, Paint(Paint.FILTER_BITMAP_FLAG))
            canvas.restore()
        }

        // Draw "Ad Free GPS Cam Visit Pro" badge above the text details block (aligned to the right side of overlay)
        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(204, 0, 0, 0) // Semi-transparent black
            style = Paint.Style.FILL
        }

        val badgeRight = overlayRight - contentInternalPadding
        val badgeLeft = badgeRight - badgeW
        val badgeBottom = overlayTop
        val badgeTop = badgeBottom - badgeH

        val badgeRect = RectF(badgeLeft, badgeTop, badgeRight, badgeBottom)
        val badgeRadius = 2f * globalScale
        canvas.drawRoundRect(badgeRect, badgeRadius, badgeRadius, badgePaint)

        // Draw app icon inside the badge
        val iconLeft = badgeLeft + padX
        val iconTop = badgeTop + (badgeH - iconSize) / 2
        val srcRect = Rect(0, 0, appIconBitmap.width, appIconBitmap.height)
        val dstRect = RectF(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize)
        canvas.drawBitmap(appIconBitmap, srcRect, dstRect, Paint(Paint.FILTER_BITMAP_FLAG))

        // Draw badge text
        canvas.drawText(badgeText, iconLeft + iconSize + iconMarginRight, badgeBottom - padY - 0.5f * globalScale, badgeTextPaint)

        // Draw text block
        val textStartX = overlayLeft + contentInternalPadding + fixedThumbWidth + contentInternalPadding
        // Shift text block slightly up
        val textStartY = overlayTop + (overlayHeight - textBlockHeight) / 2 - 8f * globalScale
        canvas.save()
        canvas.translate(textStartX, textStartY)
        headerLayout.draw(canvas)
        canvas.translate(0f, headerLayout.height.toFloat() + lineSpacing)
        detailsLayout.draw(canvas)
        canvas.restore()

        return resultBitmap
    }

    fun createPdfWithOverlay(
        imageBitmap: Bitmap,
        outputFile: File,
        mapThumbnail: Bitmap?,
        latitude: String,
        longitude: String,
        address: String,
        datetime: String
    ) {
        // High-res PDF overlay should match the look of the new balanced bitmap overlay
        val highResBitmap = drawGpsOverlay(imageBitmap, mapThumbnail, latitude, longitude, address, datetime)
        
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(highResBitmap.width, highResBitmap.height, 1).create()
        val page = document.startPage(pageInfo)
        page.canvas.drawBitmap(highResBitmap, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        document.finishPage(page)
        document.writeTo(outputFile.outputStream())
        document.close()
        
        highResBitmap.recycle()
    }
}
