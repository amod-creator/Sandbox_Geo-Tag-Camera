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

import kotlin.math.max


class GpsOverlayRenderer(private val context: Context) {
    enum class LayoutMode { AUTO, HORIZONTAL, VERTICAL }
    var layoutMode: LayoutMode = LayoutMode.HORIZONTAL

    private val titleTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 96f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val valueTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 84f
    }

    private val backgroundPaint = Paint().apply {
        color = Color.argb(81, 0, 0, 0) // 20% opacity black
        style = Paint.Style.FILL
    }

    fun drawGpsOverlay(
        originalBitmap: Bitmap,
        mapThumbnail: Bitmap?,
        latitude: String,
        longitude: String,
        address: String,
        datetime: String
    ): Bitmap {
        // Always use the bottom overlay style
        return drawGpsOverlayBottom(originalBitmap, mapThumbnail, latitude, longitude, address, datetime)
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
        datetime: String
    ): Bitmap {
        val resultBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(resultBitmap)

        val overlayPadding = 24f
        val cornerRadius = 28f  // Rounded corners matching live card

        // Bold header paint for city/state
        val headerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 96f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isSubpixelText = true
            isLinearText = true
        }
        val valuePaint = valueTextPaint.apply {
            isSubpixelText = true
            isLinearText = true
        }

        val isLandscape = resultBitmap.width > resultBitmap.height
        
        // Calculate Overlay Width
        val overlayWidth = if (isLandscape) {
            resultBitmap.width * 0.65f
        } else {
            resultBitmap.width.toFloat() - (overlayPadding * 2)
        }

        val thumbWidthRatio = 0.3f
        val textWidthRatio = 0.7f
        
        val contentInternalPadding = overlayPadding
        val totalContentWidth = overlayWidth - (contentInternalPadding * 2)
        
        val fixedThumbWidth = totalContentWidth * thumbWidthRatio
        val textAreaWidth = totalContentWidth * textWidthRatio
        val thumbHeight = mapThumbnail?.let { fixedThumbWidth / (it.width.toFloat() / it.height.toFloat()) } ?: 0f

        // New text layout: Header, Address, combined LatLon, Date
        val cityHeader = extractCityStateCountry(address)
        val combinedLatLon = "Lat $latitude°   Long $longitude°"
        val lineSpacing = 8f

        val headerLayout = StaticLayout.Builder.obtain(cityHeader, 0, cityHeader.length, headerPaint, textAreaWidth.toInt()).build()
        val addrLayout = StaticLayout.Builder.obtain(address, 0, address.length, valuePaint, textAreaWidth.toInt()).build()
        val latLonLayout = StaticLayout.Builder.obtain(combinedLatLon, 0, combinedLatLon.length, valuePaint, textAreaWidth.toInt()).build()
        val dateLayout = StaticLayout.Builder.obtain(datetime, 0, datetime.length, valuePaint, textAreaWidth.toInt()).build()

        val textBlockHeight = (headerLayout.height + addrLayout.height + latLonLayout.height + dateLayout.height + lineSpacing * 3).toFloat()
        val overlayHeight = max(thumbHeight, textBlockHeight) + (overlayPadding * 2)

        val overlayBottom = resultBitmap.height - overlayPadding
        val overlayTop = overlayBottom - overlayHeight
        
        val overlayLeft = if (isLandscape) {
            (resultBitmap.width - overlayWidth) / 2f
        } else {
            overlayPadding
        }
        val overlayRight = overlayLeft + overlayWidth

        val bgPaint = backgroundPaint
        val rect = RectF(overlayLeft, overlayTop, overlayRight, overlayBottom)
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, bgPaint)

        // Draw map thumbnail with rounded corners
        mapThumbnail?.let {
            val scaledThumb = Bitmap.createScaledBitmap(it, fixedThumbWidth.toInt(), thumbHeight.toInt(), true)
            val thumbX = overlayLeft + contentInternalPadding
            val thumbY = overlayTop + (overlayHeight - thumbHeight) / 2
            
            // Clip to rounded rect for thumbnail
            val thumbRect = RectF(thumbX, thumbY, thumbX + fixedThumbWidth, thumbY + thumbHeight)
            canvas.save()
            val clipPath = android.graphics.Path()
            clipPath.addRoundRect(thumbRect, 12f, 12f, android.graphics.Path.Direction.CW)
            canvas.clipPath(clipPath)
            canvas.drawBitmap(scaledThumb, thumbX, thumbY, null)
            canvas.restore()
        }

        // Draw text: Header → Address → LatLon → Date
        val textStartX = overlayLeft + contentInternalPadding + fixedThumbWidth + contentInternalPadding
        val textStartY = overlayTop + (overlayHeight - textBlockHeight) / 2
        canvas.save()
        canvas.translate(textStartX, textStartY)
        headerLayout.draw(canvas)
        canvas.translate(0f, headerLayout.height.toFloat() + lineSpacing)
        addrLayout.draw(canvas)
        canvas.translate(0f, addrLayout.height.toFloat() + lineSpacing)
        latLonLayout.draw(canvas)
        canvas.translate(0f, latLonLayout.height.toFloat() + lineSpacing)
        dateLayout.draw(canvas)
        canvas.restore()

        return resultBitmap
    }

    private fun drawText(canvas: Canvas, text: String, x: Int, y: Int, paint: TextPaint) {
        val maxWidth = (canvas.width - x - 16).toFloat()
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, maxWidth.toInt())
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1f)
            .setIncludePad(false)
            .build()

        canvas.save()
        canvas.translate(x.toFloat(), y.toFloat())
        layout.draw(canvas)
        canvas.restore()
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
        // Create high-quality paints for PDF
        val pdfTitleTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = context.resources.getDimensionPixelSize(R.dimen.gps_overlay_title_size).toFloat() * 2  // Double size for PDF
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val pdfValueTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = context.resources.getDimensionPixelSize(R.dimen.gps_overlay_value_size).toFloat() * 2  // Double size for PDF
        }

        // Create a higher resolution bitmap for PDF
        val scale = 2.0f  // Scale factor for higher resolution
        val scaledWidth = (imageBitmap.width * scale).toInt()
        val scaledHeight = (imageBitmap.height * scale).toInt()

        val highResBitmap = Bitmap.createBitmap(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(highResBitmap)

        // Scale the canvas for high resolution
        canvas.scale(scale, scale)

        // Draw the original image
        val matrix = Matrix()
        matrix.setScale(scale, scale)
        canvas.drawBitmap(imageBitmap, matrix, Paint(Paint.FILTER_BITMAP_FLAG))

        // Draw overlay with high-quality settings
        val overlayPadding = 24f * scale
        val cornerRadius = 16f * scale
        val rect = RectF(
            overlayPadding,
            overlayPadding,
            highResBitmap.width - overlayPadding,
            overlayPadding + (highResBitmap.height * 0.25f)
        )

        // Draw background with anti-aliasing
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(153, 0, 0, 0)
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, bgPaint)

        // Draw high-resolution thumbnail
        mapThumbnail?.let {
            val desiredThumbHeight = rect.height() - overlayPadding * 2
            val thumbWidth = desiredThumbHeight * (it.width.toFloat() / it.height)
            val scaledThumb = Bitmap.createScaledBitmap(
                it,
                (thumbWidth * 2).toInt(),
                (desiredThumbHeight * 2).toInt(),
                true
            )
            canvas.drawBitmap(scaledThumb, overlayPadding * 2, overlayPadding * 2, Paint(Paint.FILTER_BITMAP_FLAG))
        }

        // Draw text with higher quality
        val textStartX = overlayPadding * 2 + (mapThumbnail?.let {
            ((rect.height() - overlayPadding * 2) * (it.width.toFloat() / it.height))
        } ?: 0f) + overlayPadding

        canvas.save()
        canvas.translate(textStartX, overlayPadding * 2)

        // Draw text with increased size and quality
        val maxTextWidth = (highResBitmap.width * 0.6f).toInt()
        val latLayout = StaticLayout.Builder.obtain("Lat: $latitude", 0, "Lat: $latitude".length, pdfTitleTextPaint, maxTextWidth).build()
        val lonLayout = StaticLayout.Builder.obtain("Lon: $longitude", 0, "Lon: $longitude".length, pdfTitleTextPaint, maxTextWidth).build()
        val addrLayout = StaticLayout.Builder.obtain(address, 0, address.length, pdfValueTextPaint, maxTextWidth).build()
        val dateLayout = StaticLayout.Builder.obtain(datetime, 0, datetime.length, pdfValueTextPaint, maxTextWidth).build()

        latLayout.draw(canvas)
        canvas.translate(0f, latLayout.height.toFloat())
        lonLayout.draw(canvas)
        canvas.translate(0f, lonLayout.height.toFloat() + overlayPadding)
        addrLayout.draw(canvas)
        canvas.translate(0f, addrLayout.height.toFloat() + overlayPadding)
        dateLayout.draw(canvas)
        canvas.restore()

        // Create PDF with high-quality bitmap
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(scaledWidth, scaledHeight, 1).create()
        val page = document.startPage(pageInfo)

        // Draw the high-resolution bitmap to PDF
        page.canvas.drawBitmap(highResBitmap, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))

        document.finishPage(page)
        document.writeTo(outputFile.outputStream())
        document.close()

        // Clean up
        highResBitmap.recycle()
    }
}
