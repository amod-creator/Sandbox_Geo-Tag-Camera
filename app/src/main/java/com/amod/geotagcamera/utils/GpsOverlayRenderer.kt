package com.amod.geotagcamera.utils

import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.text.Layout
import android.text.StaticLayout
import android.text.TextUtils
import android.text.TextPaint
import androidx.core.content.ContextCompat
import com.amod.geotagcamera.R
import com.amod.geotagcamera.model.AppLanguage
import com.amod.geotagcamera.model.CustomNoteConfig
import com.amod.geotagcamera.model.NotePosition
import com.amod.geotagcamera.model.OverlayTemplate
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

class GpsOverlayRenderer(private val context: Context) {
    enum class LayoutMode { AUTO, HORIZONTAL, VERTICAL }
    var layoutMode: LayoutMode = LayoutMode.HORIZONTAL
    var customNote: String? = null
    var customNoteConfig: CustomNoteConfig? = null

    var currentAzimuth: Float = 207f
    var currentAltitude: Double = 15.0
    var currentMagneticField: Float = 71.25f
    var isBackCamera: Boolean = true
    var showCameraStamp: Boolean = true

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

            val isPortrait = videoHeight >= videoWidth
            val refDim = min(videoWidth, videoHeight).toFloat()
            val scaleFactor = refDim / 1080f

            val overlayWidth = (if (isPortrait) 0.94f else 0.70f) * videoWidth
            val overlayPadding = (if (isPortrait) 16f else 14f) * scaleFactor
            val cornerRadius = (if (isPortrait) 22f else 20f) * scaleFactor

            val headerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = (if (isPortrait) 46f else 54f) * scaleFactor
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val valuePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = (if (isPortrait) 26f else 32f) * scaleFactor
            }

            val contentInternalPadding = overlayPadding
            val thumbAreaRatio = if (isPortrait) 0.22f else 0.16f
            val totalContentWidth = overlayWidth - (contentInternalPadding * 3)
            val fixedThumbWidth = totalContentWidth * thumbAreaRatio
            val textAreaWidth = totalContentWidth * (1f - thumbAreaRatio)
            val thumbHeight = fixedThumbWidth // Square (1:1), matching reference images

            val cityHeader = extractCityStateCountry(address)
            val cleanAddr = formatCleanStreetAddress(address)
            val cleanLat = cleanCoordinate(latitude, "Lat")
            val cleanLon = cleanCoordinate(longitude, "Long")
            val combinedLatLon = "Lat $cleanLat°   Long $cleanLon°"

            adjustTextSizes(
                headerPaint, valuePaint, cityHeader, cleanAddr, combinedLatLon, datetime,
                textAreaWidth, isPortrait, scaleFactor
            )

            val lineSpacing = 4.5f * scaleFactor

            val headerLayout = StaticLayout.Builder.obtain(cityHeader, 0, cityHeader.length, headerPaint, textAreaWidth.toInt())
                .setMaxLines(1)
                .setEllipsize(TextUtils.TruncateAt.END)
                .build()

            val detailsText = if (!customNote.isNullOrBlank()) {
                "$customNote\n$cleanAddr\n$combinedLatLon\n$datetime"
            } else {
                "$cleanAddr\n$combinedLatLon\n$datetime"
            }
            val detailsLayout = StaticLayout.Builder.obtain(detailsText, 0, detailsText.length, valuePaint, textAreaWidth.toInt())
                .setMaxLines(if (isPortrait) 4 else 3)
                .setEllipsize(TextUtils.TruncateAt.END)
                .build()

            val textBlockHeight = (headerLayout.height + lineSpacing + detailsLayout.height).toFloat()
            val overlayHeight = max(thumbHeight, textBlockHeight) + (contentInternalPadding * 2)

            val topMargin = (if (isPortrait) 108f else 18f) * scaleFactor
            val overlayTop = topMargin
            val overlayBottom = overlayTop + overlayHeight
            val overlayLeft = (videoWidth - overlayWidth) / 2f
            val overlayRight = overlayLeft + overlayWidth
            val overlayRect = RectF(overlayLeft, overlayTop, overlayRight, overlayBottom)

            // 1. Draw Translucent Dark Background with rounded corners
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(195, 14, 18, 24)
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(overlayRect, cornerRadius, cornerRadius, bgPaint)

            // 2. Draw "Ad Free GPS Cam Visit Pro" in a separate overlay above top-right of GPS overlay card
            drawSeparateBadgeOverlay(canvas, overlayRect, scaleFactor, address)

            // 3. Draw Map Thumbnail (1:1 square) with rounded corners and Google watermark (Drawn on Left)
            mapThumbnail?.let {
                val thumbLeft = overlayLeft + contentInternalPadding
                val thumbTop = overlayTop + (overlayHeight - thumbHeight) / 2f
                val thumbRight = thumbLeft + fixedThumbWidth
                val thumbBottom = thumbTop + thumbHeight
                val thumbRect = RectF(thumbLeft, thumbTop, thumbRight, thumbBottom)

                val thumbRadius = cornerRadius * 0.5f
                val clipPath = Path().apply {
                    addRoundRect(thumbRect, thumbRadius, thumbRadius, Path.Direction.CW)
                }
                canvas.save()
                canvas.clipPath(clipPath)
                val srcRect = getResizedMapThumbnail(it, thumbRect.width(), thumbRect.height())
                val filterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
                canvas.drawBitmap(it, srcRect, thumbRect, filterPaint)
                canvas.restore()
                drawGoogleWatermark(canvas, thumbRect, scaleFactor)
            }

            // 4. Draw Text Block (Drawn on Right)
            val textStartX = if (mapThumbnail != null) {
                overlayLeft + contentInternalPadding + fixedThumbWidth + contentInternalPadding
            } else {
                overlayLeft + contentInternalPadding
            }
            val textStartY = overlayTop + (overlayHeight - textBlockHeight) / 2f
            canvas.save()
            canvas.translate(textStartX, textStartY)
            headerLayout.draw(canvas)
            canvas.translate(0f, headerLayout.height.toFloat() + lineSpacing)
            detailsLayout.draw(canvas)
            canvas.restore()

            val appLang = AppLanguage.getSelectedLanguage(context)
            val orientationStamp = if (isPortrait) appLang.portraitLabel else appLang.landscapeLabel
            val cameraStamp = if (isBackCamera) "Rear Camera" else "Front Camera"
            val watermark = if (showCameraStamp) "$orientationStamp • $cameraStamp" else orientationStamp
            drawModeWatermark(canvas, overlayRect, watermark, contentInternalPadding, scaleFactor)

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
        val selectedTemplate = OverlayTemplate.getSelectedTemplate(context)
        val isPortrait = originalBitmap.height >= originalBitmap.width
        val appLang = AppLanguage.getSelectedLanguage(context)
        val orientationStamp = if (isPortrait) appLang.portraitLabel else appLang.landscapeLabel
        val cameraStamp = if (isBackCamera) "Rear Camera" else "Front Camera"
        val watermark = if (showCameraStamp) "$orientationStamp • $cameraStamp" else orientationStamp

        val resultBitmap = when (selectedTemplate) {
            OverlayTemplate.DATETIME -> drawDateTimeTemplate(originalBitmap, latitude, longitude, address, datetime, watermark)
            OverlayTemplate.SCAN_LOCATION -> drawScanLocationTemplate(originalBitmap, mapThumbnail, latitude, longitude, address, datetime, watermark)
            OverlayTemplate.CLASSIC -> drawClassicTemplate(originalBitmap, mapThumbnail, latitude, longitude, address, datetime, watermark)
            OverlayTemplate.REPORTING -> drawReportingTemplate(originalBitmap, mapThumbnail, latitude, longitude, address, datetime, watermark)
            OverlayTemplate.NAVIGATION_COMPASS -> drawCompassTemplate(originalBitmap, mapThumbnail, latitude, longitude, address, datetime, watermark)
            OverlayTemplate.LOCATION_WATERMARK -> drawLocationWatermarkTemplate(originalBitmap, mapThumbnail, latitude, longitude, address, datetime, watermark)
        }

        // Draw Instagram-style custom note sticker if configured
        val config = customNoteConfig ?: customNote?.let { CustomNoteConfig(text = it) }
        if (config != null && config.text.isNotBlank() && config.position != NotePosition.INSIDE_OVERLAY.id) {
            val canvas = Canvas(resultBitmap)
            val globalScale = resultBitmap.width / 720f
            val cardTopEstimated = resultBitmap.height - (260f * globalScale)
            InstagramTextStyler.drawStickerOnCanvas(
                canvas = canvas,
                config = config,
                context = context,
                canvasWidth = resultBitmap.width,
                canvasHeight = resultBitmap.height,
                bottomPaddingAboveCard = cardTopEstimated,
                isCardAtTop = false
            )
        }

        return resultBitmap
    }

    private fun parseDateTimeParts(datetime: String): Triple<String, String, String> {
        return try {
            val now = Date()
            val timeFmt = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(now)
            val dateFmt = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(now)
            val dayFmt = SimpleDateFormat("EEEE", Locale.getDefault()).format(now)

            val timeRegex = Regex("(?i)(\\d{1,2}:\\d{2}\\s*(?:AM|PM))")
            val match = timeRegex.find(datetime)
            val parsedTime = match?.value ?: timeFmt
            Triple(parsedTime, dateFmt, dayFmt)
        } catch (e: Exception) {
            Triple("09:15 PM", "20 September 2026", "Sunday")
        }
    }

    private fun getResizedMapThumbnail(bitmap: Bitmap, targetWidth: Float, targetHeight: Float): Rect {
        val bWidth = bitmap.width
        val bHeight = bitmap.height
        val srcRatio = bWidth.toFloat() / bHeight.toFloat()
        val targetRatio = targetWidth / targetHeight
        return if (srcRatio > targetRatio) {
            val newWidth = (bHeight * targetRatio).toInt().coerceAtMost(bWidth)
            val left = (bWidth - newWidth) / 2
            Rect(left, 0, left + newWidth, bHeight)
        } else {
            val newHeight = (bWidth / targetRatio).toInt().coerceAtMost(bHeight)
            val top = (bHeight - newHeight) / 2
            Rect(0, top, bWidth, top + newHeight)
        }
    }

    private fun drawDateTimeTemplate(
        originalBitmap: Bitmap,
        latitude: String,
        longitude: String,
        address: String,
        datetime: String,
        watermark: String = ""
    ): Bitmap {
        val resultBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(resultBitmap)

        val isPortrait = resultBitmap.height >= resultBitmap.width
        val refDim = min(resultBitmap.width, resultBitmap.height).toFloat()
        val scaleFactor = refDim / 1080f

        val overlayWidth = (if (isPortrait) 0.92f else 0.70f) * resultBitmap.width
        val overlayPadding = (if (isPortrait) 16f else 14f) * scaleFactor
        val cornerRadius = (if (isPortrait) 26f else 22f) * scaleFactor
        val overlayLeft = (resultBitmap.width - overlayWidth) / 2f

        val headerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = (if (isPortrait) 44f else 54f) * scaleFactor
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val valuePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = (if (isPortrait) 26f else 32f) * scaleFactor
        }
        val bigTimePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = (if (isPortrait) 52f else 62f) * scaleFactor
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val datePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = (if (isPortrait) 26f else 30f) * scaleFactor
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val cityHeader = extractCityStateCountry(address)
        val cleanLat = cleanCoordinate(latitude, "Lat")
        val cleanLon = cleanCoordinate(longitude, "Long")
        val combinedLatLon = "Lat $cleanLat°   Long $cleanLon°"

        val contentInternalPadding = overlayPadding * 1.1f
        val innerWidth = overlayWidth - (contentInternalPadding * 2)

        adjustTextSizes(
            headerPaint, valuePaint, cityHeader, address, combinedLatLon, datetime,
            innerWidth, isPortrait, scaleFactor
        )

        val lineSpacing = 4.5f * scaleFactor
        val (timeStr, dateStr, dayStr) = parseDateTimeParts(datetime)

        val addressLayout = StaticLayout.Builder.obtain(
            address,
            0,
            address.length,
            valuePaint,
            innerWidth.toInt()
        ).setMaxLines(if (isPortrait) 3 else 2)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).build()

        val timeBannerHeight = max(bigTimePaint.textSize * 1.15f, datePaint.textSize * 2.3f)
        val dividerHeight = 6f * scaleFactor

        val textContentHeight = timeBannerHeight + dividerHeight +
                headerPaint.textSize + lineSpacing +
                addressLayout.height + lineSpacing +
                valuePaint.textSize

        val totalCardHeight = textContentHeight + (contentInternalPadding * 2)
        val bottomMargin = (if (isPortrait) 24f else 18f) * scaleFactor
        val overlayTop = resultBitmap.height - totalCardHeight - bottomMargin
        val overlayBottom = overlayTop + totalCardHeight
        val overlayRect = RectF(overlayLeft, overlayTop, overlayLeft + overlayWidth, overlayBottom)

        // Draw top right camera badge above overlay card
        drawTopRightBadge(canvas, overlayRect, scaleFactor, address)

        // Draw translucent dark card with rounded corners
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(190, 16, 20, 24)
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(overlayRect, cornerRadius, cornerRadius, bgPaint)

        var curY = overlayTop + contentInternalPadding

        // 1. Big DateTime Header
        val timeWidth = bigTimePaint.measureText(timeStr)
        canvas.drawText(timeStr, overlayLeft + contentInternalPadding, curY + bigTimePaint.textSize * 0.85f, bigTimePaint)

        val divX = overlayLeft + contentInternalPadding + timeWidth + (10f * scaleFactor)
        val divPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFC107")
            strokeWidth = 3f * scaleFactor
        }
        canvas.drawLine(divX, curY + 2f, divX, curY + timeBannerHeight - 2f, divPaint)

        val dateX = divX + (10f * scaleFactor)
        canvas.drawText(dateStr, dateX, curY + datePaint.textSize * 0.85f, datePaint)
        canvas.drawText(dayStr, dateX, curY + datePaint.textSize * 2.05f, datePaint)

        curY += timeBannerHeight + dividerHeight

        // 2. City Header
        canvas.drawText(cityHeader, overlayLeft + contentInternalPadding, curY + headerPaint.textSize * 0.8f, headerPaint)
        curY += headerPaint.textSize + lineSpacing

        // 3. Address
        canvas.save()
        canvas.translate(overlayLeft + contentInternalPadding, curY)
        addressLayout.draw(canvas)
        canvas.restore()
        curY += addressLayout.height + lineSpacing

        // 4. Coordinates
        canvas.drawText(combinedLatLon, overlayLeft + contentInternalPadding, curY + valuePaint.textSize * 0.8f, valuePaint)

        // Mode Watermark Stamp
        drawModeWatermark(canvas, overlayRect, watermark, contentInternalPadding, scaleFactor)

        return resultBitmap
    }

    private fun drawScanLocationTemplate(
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

        val isPortrait = resultBitmap.height >= resultBitmap.width
        val refDim = min(resultBitmap.width, resultBitmap.height).toFloat()
        val scaleFactor = refDim / 1080f

        val overlayWidth = (if (isPortrait) 0.92f else 0.70f) * resultBitmap.width
        val overlayPadding = (if (isPortrait) 14f else 14f) * scaleFactor
        val cornerRadius = (if (isPortrait) 26f else 22f) * scaleFactor
        val overlayLeft = (resultBitmap.width - overlayWidth) / 2f
        val contentInternalPadding = overlayPadding

        val cleanLat = cleanCoordinate(latitude, "Lat")
        val cleanLon = cleanCoordinate(longitude, "Long")
        val hasQr = cleanLat.isNotBlank() && cleanLon.isNotBlank() && cleanLat != "--" && cleanLon != "--"

        val squareThumbSize = overlayWidth * (if (isPortrait) 0.22f else 0.16f)
        val squareQrSize = if (hasQr) squareThumbSize * 0.95f else 0f
        val textAreaWidth = if (hasQr) {
            overlayWidth - squareThumbSize - squareQrSize - (contentInternalPadding * 4)
        } else {
            overlayWidth - squareThumbSize - (contentInternalPadding * 3)
        }

        val headerTextSize = (if (isPortrait) 44f else 52f) * scaleFactor
        val valueTextSize = (if (isPortrait) 25f else 30f) * scaleFactor

        val headerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = headerTextSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val valuePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(235, 255, 255, 255)
            textSize = valueTextSize
        }

        val cityHeader = extractCityStateCountry(address)
        val combinedLatLon = "Lat $cleanLat°   Long $cleanLon°"

        adjustTextSizes(
            headerPaint, valuePaint, cityHeader, address, combinedLatLon, datetime,
            textAreaWidth, isPortrait, scaleFactor
        )

        val lineSpacing = 4.0f * scaleFactor
        val cleanStreetAddress = address
            .replace("\n", ", ")
            .replace(Regex(",\\s*,"), ",")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trimEnd(',')

        val maxAddressLines = if (hasQr) 2 else 3
        val addressLayout = StaticLayout.Builder.obtain(
            cleanStreetAddress,
            0,
            cleanStreetAddress.length,
            valuePaint,
            textAreaWidth.toInt()
        ).setMaxLines(maxAddressLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.0f)
            .setIncludePad(false)
            .build()

        val textContentHeight = headerPaint.textSize + lineSpacing +
                addressLayout.height + lineSpacing +
                valuePaint.textSize + lineSpacing +
                valuePaint.textSize

        val innerHeight = max(squareThumbSize, max(squareQrSize, textContentHeight))
        val totalCardHeight = innerHeight + (contentInternalPadding * 2)
        val bottomMargin = (if (isPortrait) 24f else 18f) * scaleFactor
        val overlayTop = resultBitmap.height - totalCardHeight - bottomMargin
        val overlayBottom = overlayTop + totalCardHeight
        val overlayRect = RectF(overlayLeft, overlayTop, overlayLeft + overlayWidth, overlayBottom)

        // Draw top right camera badge above overlay card
        drawTopRightBadge(canvas, overlayRect, scaleFactor, address)

        // Draw translucent dark card with rounded corners
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(190, 16, 20, 24)
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(overlayRect, cornerRadius, cornerRadius, bgPaint)

        // 1. Draw Map on Left Corner
        val thumbLeft = overlayLeft + contentInternalPadding
        val thumbTop = overlayTop + (totalCardHeight - squareThumbSize) / 2f
        val thumbRight = thumbLeft + squareThumbSize
        val thumbBottom = thumbTop + squareThumbSize
        val thumbRect = RectF(thumbLeft, thumbTop, thumbRight, thumbBottom)

        mapThumbnail?.let {
            val thumbRadius = cornerRadius * 0.5f
            val thumbPath = Path().apply {
                addRoundRect(thumbRect, thumbRadius, thumbRadius, Path.Direction.CW)
            }
            canvas.save()
            canvas.clipPath(thumbPath)
            val srcRect = getResizedMapThumbnail(it, thumbRect.width(), thumbRect.height())
            val filterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
            canvas.drawBitmap(it, srcRect, thumbRect, filterPaint)
            canvas.restore()
            drawGoogleWatermark(canvas, thumbRect, scaleFactor)
        }

        // 2. Draw QR Code on Right Corner if available
        if (hasQr) {
            val qrLeft = overlayLeft + overlayWidth - contentInternalPadding - squareQrSize
            val qrTop = overlayTop + (totalCardHeight - squareQrSize) / 2f
            val qrRect = RectF(qrLeft, qrTop, qrLeft + squareQrSize, qrTop + squareQrSize)

            val qrWhiteBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.FILL
            }
            val qrRadius = cornerRadius * 0.5f
            canvas.drawRoundRect(qrRect, qrRadius, qrRadius, qrWhiteBg)

            try {
                val qrBmp = QrCodeGenerator.generateQrCode("https://maps.google.com/?q=$cleanLat,$cleanLon", squareQrSize.toInt())
                qrBmp?.let {
                    val pad = squareQrSize * 0.05f
                    val innerQrRect = RectF(qrLeft + pad, qrTop + pad, qrRect.right - pad, qrRect.bottom - pad)
                    canvas.drawBitmap(it, null, innerQrRect, Paint(Paint.FILTER_BITMAP_FLAG))
                    it.recycle()
                }
            } catch (e: Exception) {
                // ignore
            }
        }

        // 3. Draw Text in Middle
        val textLeft = if (mapThumbnail != null) {
            thumbLeft + squareThumbSize + contentInternalPadding
        } else {
            overlayLeft + contentInternalPadding
        }
        var curY = overlayTop + (totalCardHeight - textContentHeight) / 2f

        canvas.drawText(cityHeader, textLeft, curY + headerPaint.textSize * 0.85f, headerPaint)
        curY += headerPaint.textSize + lineSpacing

        canvas.save()
        canvas.translate(textLeft, curY)
        addressLayout.draw(canvas)
        canvas.restore()
        curY += addressLayout.height + lineSpacing

        canvas.drawText(combinedLatLon, textLeft, curY + valuePaint.textSize * 0.85f, valuePaint)
        curY += valuePaint.textSize + lineSpacing

        canvas.drawText(datetime, textLeft, curY + valuePaint.textSize * 0.85f, valuePaint)

        // Mode & Camera Watermark: In Bar Code Template, shifted right before the border starts of the bar code at the bottom
        if (watermark.isNotBlank()) {
            val wmPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = max(overlayWidth * 0.020f, 18f * scaleFactor)
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                alpha = 240
                setShadowLayer(2.5f * scaleFactor, 1f * scaleFactor, 1f * scaleFactor, Color.argb(180, 0, 0, 0))
            }
            val textW = wmPaint.measureText(watermark)
            val anchorRight = if (hasQr) {
                val qrLeft = overlayLeft + overlayWidth - contentInternalPadding - squareQrSize
                qrLeft - (contentInternalPadding * 0.6f)
            } else {
                overlayLeft + overlayWidth - contentInternalPadding
            }
            val textX = anchorRight - textW
            val textY = overlayBottom - (contentInternalPadding * 0.35f)
            canvas.drawText(watermark, textX, textY, wmPaint)
        }

        return resultBitmap
    }

    private fun drawClassicTemplate(
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

        val isPortrait = resultBitmap.height >= resultBitmap.width
        val refDim = min(resultBitmap.width, resultBitmap.height).toFloat()
        val scaleFactor = refDim / 1080f

        val overlayWidth = (if (isPortrait) 0.92f else 0.70f) * resultBitmap.width
        val overlayPadding = (if (isPortrait) 16f else 14f) * scaleFactor
        val cornerRadius = (if (isPortrait) 26f else 22f) * scaleFactor

        val headerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = (if (isPortrait) 46f else 54f) * scaleFactor
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val valuePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = (if (isPortrait) 26f else 32f) * scaleFactor
        }

        val overlayLeft = (resultBitmap.width - overlayWidth) / 2f
        val contentInternalPadding = overlayPadding
        val thumbAreaRatio = if (isPortrait) 0.22f else 0.16f
        val totalContentWidth = overlayWidth - (contentInternalPadding * 3)

        val fixedThumbWidth = totalContentWidth * thumbAreaRatio
        val textAreaWidth = totalContentWidth * (1f - thumbAreaRatio)
        val thumbHeight = fixedThumbWidth // Square 1:1 format matching other templates

        val cityHeader = extractCityStateCountry(address)
        val cleanLat = cleanCoordinate(latitude, "Lat")
        val cleanLon = cleanCoordinate(longitude, "Long")
        val combinedLatLon = "Lat $cleanLat°   Long $cleanLon°"

        adjustTextSizes(
            headerPaint, valuePaint, cityHeader, address, combinedLatLon, datetime,
            textAreaWidth, isPortrait, scaleFactor
        )

        val lineSpacing = 4.5f * scaleFactor

        val addressLayout = StaticLayout.Builder.obtain(
            address,
            0,
            address.length,
            valuePaint,
            textAreaWidth.toInt()
        ).setMaxLines(if (isPortrait) 3 else 2)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).build()

        val textContentHeight = headerPaint.textSize + lineSpacing +
            addressLayout.height + lineSpacing +
            valuePaint.textSize + lineSpacing +
            valuePaint.textSize

        val innerHeight = max(thumbHeight, textContentHeight)
        val totalCardHeight = innerHeight + (contentInternalPadding * 2)
        val bottomMargin = (if (isPortrait) 24f else 18f) * scaleFactor
        val overlayTop = resultBitmap.height - totalCardHeight - bottomMargin
        val overlayBottom = overlayTop + totalCardHeight
        val overlayRect = RectF(overlayLeft, overlayTop, overlayLeft + overlayWidth, overlayBottom)

        // Draw top right camera badge above overlay card
        drawTopRightBadge(canvas, overlayRect, scaleFactor, address)

        // Draw translucent dark card with rounded corners
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(190, 16, 20, 24)
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(overlayRect, cornerRadius, cornerRadius, bgPaint)

        // Draw Map on Left
        val thumbLeft = overlayLeft + contentInternalPadding
        val thumbTop = overlayTop + (totalCardHeight - thumbHeight) / 2f
        val thumbRight = thumbLeft + fixedThumbWidth
        val thumbBottom = thumbTop + thumbHeight
        val thumbRect = RectF(thumbLeft, thumbTop, thumbRight, thumbBottom)

        mapThumbnail?.let {
            val thumbRadius = cornerRadius * 0.5f
            val thumbPath = Path().apply {
                addRoundRect(thumbRect, thumbRadius, thumbRadius, Path.Direction.CW)
            }
            canvas.save()
            canvas.clipPath(thumbPath)
            val srcRect = getResizedMapThumbnail(it, thumbRect.width(), thumbRect.height())
            val filterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
            canvas.drawBitmap(it, srcRect, thumbRect, filterPaint)
            canvas.restore()
            drawGoogleWatermark(canvas, thumbRect, scaleFactor)
        }

        // Draw Text on Right
        val textLeft = if (mapThumbnail != null) {
            thumbLeft + fixedThumbWidth + contentInternalPadding
        } else {
            overlayLeft + contentInternalPadding
        }
        var curY = overlayTop + (totalCardHeight - textContentHeight) / 2f

        canvas.drawText(cityHeader, textLeft, curY + headerPaint.textSize * 0.8f, headerPaint)
        curY += headerPaint.textSize + lineSpacing

        canvas.save()
        canvas.translate(textLeft, curY)
        addressLayout.draw(canvas)
        canvas.restore()
        curY += addressLayout.height + lineSpacing

        canvas.drawText(combinedLatLon, textLeft, curY + valuePaint.textSize * 0.8f, valuePaint)
        curY += valuePaint.textSize + lineSpacing

        canvas.drawText(datetime, textLeft, curY + valuePaint.textSize * 0.8f, valuePaint)

        // Mode Watermark Stamp
        drawModeWatermark(canvas, overlayRect, watermark, contentInternalPadding, scaleFactor)

        return resultBitmap
    }

    private fun drawReportingTemplate(
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

        val isPortrait = resultBitmap.height >= resultBitmap.width
        val refDim = min(resultBitmap.width, resultBitmap.height).toFloat()
        val scaleFactor = refDim / 1080f

        val thumbAreaRatio = if (isPortrait) 0.22f else 0.16f
        val overlayWidth = (if (isPortrait) 0.92f else 0.70f) * resultBitmap.width
        val overlayPadding = (if (isPortrait) 16f else 14f) * scaleFactor
        val cornerRadius = (if (isPortrait) 26f else 22f) * scaleFactor

        val headerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = (if (isPortrait) 46f else 54f) * scaleFactor
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val valuePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = (if (isPortrait) 26f else 32f) * scaleFactor
        }

        val overlayLeft = (resultBitmap.width - overlayWidth) / 2f
        val contentInternalPadding = overlayPadding
        val totalContentWidth = overlayWidth - (contentInternalPadding * 3)

        val fixedThumbWidth = totalContentWidth * thumbAreaRatio
        val textAreaWidth = totalContentWidth * (1f - thumbAreaRatio)
        val thumbHeight = fixedThumbWidth * (if (isPortrait) 0.75f else 0.70f)
        val ribbonHeight = thumbHeight * 0.30f

        val cityHeader = extractCityStateCountry(address)
        val cleanLat = cleanCoordinate(latitude, "Lat")
        val cleanLon = cleanCoordinate(longitude, "Long")
        val combinedLatLon = "Lat $cleanLat°   Long $cleanLon°"

        adjustTextSizes(
            headerPaint, valuePaint, cityHeader, address, combinedLatLon, datetime,
            textAreaWidth, isPortrait, scaleFactor
        )

        val lineSpacing = 4.5f * scaleFactor

        val addressLayout = StaticLayout.Builder.obtain(
            address,
            0,
            address.length,
            valuePaint,
            textAreaWidth.toInt()
        ).setMaxLines(if (isPortrait) 3 else 2)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).build()

        val textContentHeight = headerPaint.textSize + lineSpacing +
                addressLayout.height + lineSpacing +
                valuePaint.textSize + lineSpacing +
                valuePaint.textSize

        val innerHeight = max(thumbHeight + ribbonHeight, textContentHeight)
        val totalCardHeight = innerHeight + (contentInternalPadding * 2)
        val bottomMargin = (if (isPortrait) 24f else 18f) * scaleFactor
        val overlayTop = resultBitmap.height - totalCardHeight - bottomMargin
        val overlayBottom = overlayTop + totalCardHeight
        val overlayRect = RectF(overlayLeft, overlayTop, overlayLeft + overlayWidth, overlayBottom)

        // Draw top right camera badge above overlay card
        drawTopRightBadge(canvas, overlayRect, scaleFactor, address)

        // Draw translucent dark card with rounded corners
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(190, 16, 20, 24)
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(overlayRect, cornerRadius, cornerRadius, bgPaint)

        // Draw Text on Left
        val textLeft = overlayLeft + contentInternalPadding
        var curY = overlayTop + (totalCardHeight - textContentHeight) / 2f

        canvas.drawText(cityHeader, textLeft, curY + headerPaint.textSize * 0.8f, headerPaint)
        curY += headerPaint.textSize + lineSpacing

        canvas.save()
        canvas.translate(textLeft, curY)
        addressLayout.draw(canvas)
        canvas.restore()
        curY += addressLayout.height + lineSpacing

        canvas.drawText(combinedLatLon, textLeft, curY + valuePaint.textSize * 0.8f, valuePaint)
        curY += valuePaint.textSize + lineSpacing

        canvas.drawText(datetime, textLeft, curY + valuePaint.textSize * 0.8f, valuePaint)

        // Draw Map with Green "Check In" Ribbon on Right
        val thumbLeft = overlayLeft + overlayWidth - contentInternalPadding - fixedThumbWidth
        val totalBlockTop = overlayTop + (totalCardHeight - (thumbHeight + ribbonHeight)) / 2f
        val thumbRight = thumbLeft + fixedThumbWidth
        val ribbonRect = RectF(thumbLeft, totalBlockTop, thumbRight, totalBlockTop + ribbonHeight)

        // Green ribbon
        val greenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#00C853")
            style = Paint.Style.FILL
        }
        val ribbonRadius = cornerRadius * 0.4f
        canvas.drawRoundRect(ribbonRect, ribbonRadius, ribbonRadius, greenPaint)

        val ribbonTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = ribbonHeight * 0.58f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val ribbonTextY = ribbonRect.centerY() - ((ribbonTextPaint.descent() + ribbonTextPaint.ascent()) / 2f)
        canvas.drawText("Check In", ribbonRect.centerX(), ribbonTextY, ribbonTextPaint)

        // Map under ribbon
        val thumbTop = ribbonRect.bottom + (2f * scaleFactor)
        val thumbBottom = thumbTop + thumbHeight
        val thumbRect = RectF(thumbLeft, thumbTop, thumbRight, thumbBottom)

        mapThumbnail?.let {
            val thumbRadius = cornerRadius * 0.5f
            val thumbPath = Path().apply {
                addRoundRect(thumbRect, thumbRadius, thumbRadius, Path.Direction.CW)
            }
            canvas.save()
            canvas.clipPath(thumbPath)
            val srcRect = getResizedMapThumbnail(it, thumbRect.width(), thumbRect.height())
            val filterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
            canvas.drawBitmap(it, srcRect, thumbRect, filterPaint)
            canvas.restore()
            drawGoogleWatermark(canvas, thumbRect, scaleFactor)
        }

        canvas.drawText(datetime, textLeft, curY + valuePaint.textSize * 0.8f, valuePaint)

        // Mode Watermark Stamp
        drawModeWatermark(canvas, overlayRect, watermark, contentInternalPadding, scaleFactor)

        return resultBitmap
    }

    private fun drawCompassTemplate(
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

        val isPortrait = resultBitmap.height >= resultBitmap.width
        val refDim = min(resultBitmap.width, resultBitmap.height).toFloat()
        val scaleFactor = refDim / 1080f

        val compassAreaRatio = if (isPortrait) 0.20f else 0.14f
        val miniMapAreaRatio = if (isPortrait) 0.20f else 0.14f
        val textAreaRatio = 1f - compassAreaRatio - miniMapAreaRatio

        val overlayPadding = (if (isPortrait) 14f else 14f) * scaleFactor
        val cornerRadius = (if (isPortrait) 26f else 22f) * scaleFactor

        val headerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = (if (isPortrait) 44f else 50f) * scaleFactor
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val valuePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = (if (isPortrait) 25f else 29f) * scaleFactor
        }

        val overlayWidth = (if (isPortrait) 0.94f else 0.70f) * resultBitmap.width
        val overlayLeft = (resultBitmap.width - overlayWidth) / 2f
        val contentInternalPadding = overlayPadding
        val totalContentWidth = overlayWidth - (contentInternalPadding * 4)

        val fixedCompassWidth = totalContentWidth * compassAreaRatio
        val fixedMiniMapWidth = totalContentWidth * miniMapAreaRatio
        val textAreaWidth = totalContentWidth * textAreaRatio
        val compassHeight = fixedCompassWidth * 1.05f
        val mapHeight = compassHeight

        val cityHeader = extractCityStateCountry(address)
        val cleanLat = cleanCoordinate(latitude, "Lat")
        val cleanLon = cleanCoordinate(longitude, "Long")
        val combinedLatLon = "Lat $cleanLat°   Long $cleanLon°"
        val azimuthText = "Azimuth/Bearing : %.2f°".format(Locale.US, currentAzimuth)
        val telemetryText = "⛰️ %.0f m       \uD83E\uDDF2 %.2f µT".format(Locale.US, currentAltitude, currentMagneticField)

        adjustTextSizes(
            headerPaint, valuePaint, cityHeader, address, combinedLatLon, datetime,
            textAreaWidth, isPortrait, scaleFactor
        )

        val lineSpacing = 4.0f * scaleFactor

        val addressLayout = StaticLayout.Builder.obtain(
            address,
            0,
            address.length,
            valuePaint,
            textAreaWidth.toInt()
        ).setMaxLines(if (isPortrait) 2 else 1)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).build()

        val textContentHeight = headerPaint.textSize + lineSpacing +
                addressLayout.height + lineSpacing +
                valuePaint.textSize + lineSpacing +
                valuePaint.textSize + lineSpacing +
                valuePaint.textSize + lineSpacing +
                valuePaint.textSize

        val innerHeight = max(compassHeight, textContentHeight)
        val totalCardHeight = innerHeight + (contentInternalPadding * 2)
        val bottomMargin = (if (isPortrait) 24f else 18f) * scaleFactor
        val overlayTop = resultBitmap.height - totalCardHeight - bottomMargin
        val overlayBottom = overlayTop + totalCardHeight
        val overlayRect = RectF(overlayLeft, overlayTop, overlayLeft + overlayWidth, overlayBottom)

        // Draw top right camera badge above overlay card
        drawTopRightBadge(canvas, overlayRect, scaleFactor, address)

        // Draw translucent dark card with rounded corners
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(190, 16, 20, 24)
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(overlayRect, cornerRadius, cornerRadius, bgPaint)

        // 1. Draw Text on Left
        val textLeft = overlayLeft + contentInternalPadding
        var curY = overlayTop + (totalCardHeight - textContentHeight) / 2f

        canvas.drawText(cityHeader, textLeft, curY + headerPaint.textSize * 0.8f, headerPaint)
        curY += headerPaint.textSize + lineSpacing

        canvas.save()
        canvas.translate(textLeft, curY)
        addressLayout.draw(canvas)
        canvas.restore()
        curY += addressLayout.height + lineSpacing

        canvas.drawText(combinedLatLon, textLeft, curY + valuePaint.textSize * 0.8f, valuePaint)
        curY += valuePaint.textSize + lineSpacing

        canvas.drawText(datetime, textLeft, curY + valuePaint.textSize * 0.8f, valuePaint)
        curY += valuePaint.textSize + lineSpacing

        canvas.drawText(azimuthText, textLeft, curY + valuePaint.textSize * 0.8f, valuePaint)
        curY += valuePaint.textSize + lineSpacing

        canvas.drawText(telemetryText, textLeft, curY + valuePaint.textSize * 0.8f, valuePaint)

        // 2. Draw Compass Dial in the Middle
        val compassLeft = textLeft + textAreaWidth + contentInternalPadding
        val compassTop = overlayTop + (totalCardHeight - compassHeight) / 2f
        val compassRect = RectF(compassLeft, compassTop, compassLeft + fixedCompassWidth, compassTop + compassHeight)
        CompassRenderer.drawCompassDial(canvas, compassRect, currentAzimuth)

        // 3. Draw Mini Map on Right
        val mapLeft = overlayLeft + overlayWidth - contentInternalPadding - fixedMiniMapWidth
        val mapTop = overlayTop + (totalCardHeight - mapHeight) / 2f
        val mapRect = RectF(mapLeft, mapTop, mapLeft + fixedMiniMapWidth, mapTop + mapHeight)

        mapThumbnail?.let {
            val thumbRadius = cornerRadius * 0.5f
            val thumbPath = Path().apply {
                addRoundRect(mapRect, thumbRadius, thumbRadius, Path.Direction.CW)
            }
            canvas.save()
            canvas.clipPath(thumbPath)
            val srcRect = getResizedMapThumbnail(it, mapRect.width(), mapRect.height())
            val filterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
            canvas.drawBitmap(it, srcRect, mapRect, filterPaint)
            canvas.restore()
            drawGoogleWatermark(canvas, mapRect, scaleFactor)
        }

        // Mode Watermark Stamp: kept at the right bottom corner
        drawModeWatermark(canvas, overlayRect, watermark, contentInternalPadding, scaleFactor)

        return resultBitmap
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

        val isPortrait = resultBitmap.height >= resultBitmap.width
        val refDim = min(resultBitmap.width, resultBitmap.height).toFloat()
        val scaleFactor = refDim / 1080f

        val overlayWidth = (if (isPortrait) 0.94f else 0.70f) * resultBitmap.width
        val overlayPadding = (if (isPortrait) 16f else 14f) * scaleFactor
        val cornerRadius = (if (isPortrait) 22f else 20f) * scaleFactor

        val headerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = (if (isPortrait) 46f else 54f) * scaleFactor
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val valuePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = (if (isPortrait) 26f else 32f) * scaleFactor
        }

        val contentInternalPadding = overlayPadding
        val thumbAreaRatio = if (isPortrait) 0.22f else 0.16f
        val totalContentWidth = overlayWidth - (contentInternalPadding * 3)
        val fixedThumbWidth = totalContentWidth * thumbAreaRatio
        val textAreaWidth = totalContentWidth * (1f - thumbAreaRatio)
        val thumbHeight = fixedThumbWidth // Square (1:1), matching reference images

        val cityHeader = extractCityStateCountry(address)
        val cleanAddr = formatCleanStreetAddress(address)
        val cleanLat = cleanCoordinate(latitude, "Lat")
        val cleanLon = cleanCoordinate(longitude, "Long")
        val combinedLatLon = "Lat $cleanLat°   Long $cleanLon°"
        val lineSpacing = (if (isPortrait) 7f else 6.5f) * scaleFactor

        val headerLayout = StaticLayout.Builder.obtain(cityHeader, 0, cityHeader.length, headerPaint, textAreaWidth.toInt())
            .setMaxLines(1)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()

        val detailsText = if (!customNote.isNullOrBlank()) {
            "$customNote\n$cleanAddr\n$combinedLatLon\n$datetime"
        } else {
            "$cleanAddr\n$combinedLatLon\n$datetime"
        }
        val detailsLayout = StaticLayout.Builder.obtain(detailsText, 0, detailsText.length, valuePaint, textAreaWidth.toInt())
            .setMaxLines(if (isPortrait) 4 else 3)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setLineSpacing((if (isPortrait) 4f else 3.5f) * scaleFactor, 1.15f)
            .build()

        val textBlockHeight = (headerLayout.height + lineSpacing + detailsLayout.height).toFloat()
        val overlayHeight = max(thumbHeight, textBlockHeight) + (contentInternalPadding * 2)

        val bottomMargin = (if (isPortrait) 24f else 18f) * scaleFactor
        val overlayBottom = resultBitmap.height - bottomMargin
        val overlayTop = overlayBottom - overlayHeight
        val overlayLeft = (resultBitmap.width - overlayWidth) / 2f
        val overlayRight = overlayLeft + overlayWidth
        val overlayRect = RectF(overlayLeft, overlayTop, overlayRight, overlayBottom)

        // 1. Draw Translucent Dark Background with rounded corners
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(195, 14, 18, 24)
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(overlayRect, cornerRadius, cornerRadius, bgPaint)

        // 2. Draw "Ad Free GPS Cam Visit Pro" in a separate overlay above top-right of GPS overlay card
        drawSeparateBadgeOverlay(canvas, overlayRect, scaleFactor, address)

        // 3. Draw Map Thumbnail (1:1 square) with rounded corners and Google watermark
        mapThumbnail?.let {
            val thumbLeft = overlayLeft + contentInternalPadding
            val thumbTop = overlayTop + (overlayHeight - thumbHeight) / 2f
            val thumbRight = thumbLeft + fixedThumbWidth
            val thumbBottom = thumbTop + thumbHeight
            val thumbRect = RectF(thumbLeft, thumbTop, thumbRight, thumbBottom)

            val thumbRadius = cornerRadius * 0.5f
            val clipPath = Path().apply {
                addRoundRect(thumbRect, thumbRadius, thumbRadius, Path.Direction.CW)
            }
            canvas.save()
            canvas.clipPath(clipPath)
            val srcRect = getResizedMapThumbnail(it, thumbRect.width(), thumbRect.height())
            val filterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
            canvas.drawBitmap(it, srcRect, thumbRect, filterPaint)
            canvas.restore()
            drawGoogleWatermark(canvas, thumbRect, scaleFactor)
        }

        // 4. Draw Text Block
        val textStartX = overlayLeft + contentInternalPadding + fixedThumbWidth + contentInternalPadding
        val textStartY = overlayTop + (overlayHeight - textBlockHeight) / 2f
        canvas.save()
        canvas.translate(textStartX, textStartY)
        headerLayout.draw(canvas)
        canvas.translate(0f, headerLayout.height.toFloat() + lineSpacing)
        detailsLayout.draw(canvas)
        canvas.restore()

        // 5. Mode Watermark Stamp (bottom-right of overlay card, in italics)
        drawModeWatermark(canvas, overlayRect, watermark, contentInternalPadding, scaleFactor)

        return resultBitmap
    }

    private fun drawLocationWatermarkTemplate(
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

        val isPortrait = resultBitmap.height >= resultBitmap.width
        val refDim = min(resultBitmap.width, resultBitmap.height).toFloat()
        val scaleFactor = refDim / 1080f

        val overlayWidth = (if (isPortrait) 0.94f else 0.70f) * resultBitmap.width
        val overlayPadding = (if (isPortrait) 16f else 14f) * scaleFactor
        val cornerRadius = (if (isPortrait) 24f else 20f) * scaleFactor

        val headerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = (if (isPortrait) 44f else 52f) * scaleFactor
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.03f
        }
        val valuePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E6FFFFFF")
            textSize = (if (isPortrait) 25f else 30f) * scaleFactor
        }
        val accentPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#80D8FF")
            textSize = (if (isPortrait) 24f else 28f) * scaleFactor
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val overlayLeft = (resultBitmap.width - overlayWidth) / 2f
        val contentInternalPadding = overlayPadding
        val totalContentWidth = overlayWidth - (contentInternalPadding * 3)

        val hasMap = mapThumbnail != null
        val thumbAreaRatio = if (hasMap) (if (isPortrait) 0.24f else 0.16f) else 0f
        val fixedThumbWidth = if (hasMap) totalContentWidth * thumbAreaRatio else 0f
        val textAreaWidth = if (hasMap) totalContentWidth * (1f - thumbAreaRatio) else totalContentWidth
        val thumbHeight = fixedThumbWidth // 1:1 square

        val cityHeader = extractCityStateCountry(address).uppercase(Locale.US)
        val cleanLat = cleanCoordinate(latitude, "Lat")
        val cleanLon = cleanCoordinate(longitude, "Long")
        val coordsText = "Lat $cleanLat°   Long $cleanLon°"

        adjustTextSizes(
            headerPaint, valuePaint, cityHeader, address, coordsText, datetime,
            textAreaWidth, isPortrait, scaleFactor
        )
        accentPaint.textSize = valuePaint.textSize * 0.95f

        val lineSpacing = 4.0f * scaleFactor

        val addressLayout = StaticLayout.Builder.obtain(
            address,
            0,
            address.length,
            valuePaint,
            textAreaWidth.toInt().coerceAtLeast(100)
        ).setMaxLines(if (isPortrait) 2 else 2)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).build()

        val textContentHeight = headerPaint.textSize + lineSpacing +
            addressLayout.height + lineSpacing +
            accentPaint.textSize + lineSpacing +
            valuePaint.textSize

        val innerHeight = max(thumbHeight, textContentHeight)
        val totalCardHeight = innerHeight + (contentInternalPadding * 2)
        val bottomMargin = (if (isPortrait) 24f else 18f) * scaleFactor
        val overlayTop = resultBitmap.height - totalCardHeight - bottomMargin
        val overlayBottom = overlayTop + totalCardHeight
        val overlayRect = RectF(overlayLeft, overlayTop, overlayLeft + overlayWidth, overlayBottom)

        // Draw top right camera badge above overlay card
        drawTopRightBadge(canvas, overlayRect, scaleFactor, address)

        // Draw sleek frosted dark pill/card
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(205, 18, 22, 28)
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(overlayRect, cornerRadius, cornerRadius, bgPaint)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            alpha = 45
            style = Paint.Style.STROKE
            strokeWidth = 2f * scaleFactor
        }
        canvas.drawRoundRect(overlayRect, cornerRadius, cornerRadius, borderPaint)

        // 1. Draw Text on Left
        val textLeft = overlayLeft + contentInternalPadding
        var curY = overlayTop + (totalCardHeight - textContentHeight) / 2f

        canvas.drawText("📍 " + cityHeader, textLeft, curY + headerPaint.textSize * 0.85f, headerPaint)
        curY += headerPaint.textSize + lineSpacing

        canvas.save()
        canvas.translate(textLeft, curY)
        addressLayout.draw(canvas)
        canvas.restore()
        curY += addressLayout.height + lineSpacing

        canvas.drawText(coordsText, textLeft, curY + accentPaint.textSize * 0.85f, accentPaint)
        curY += accentPaint.textSize + lineSpacing

        canvas.drawText(datetime, textLeft, curY + valuePaint.textSize * 0.85f, valuePaint)

        // 2. Draw Map Thumbnail on Right if available
        if (hasMap && mapThumbnail != null) {
            val thumbLeft = overlayLeft + overlayWidth - contentInternalPadding - fixedThumbWidth
            val thumbTop = overlayTop + (totalCardHeight - thumbHeight) / 2f
            val thumbRect = RectF(thumbLeft, thumbTop, thumbLeft + fixedThumbWidth, thumbTop + thumbHeight)
            val thumbRadius = cornerRadius * 0.5f

            val thumbPath = Path().apply {
                addRoundRect(thumbRect, thumbRadius, thumbRadius, Path.Direction.CW)
            }
            canvas.save()
            canvas.clipPath(thumbPath)
            val srcRect = getResizedMapThumbnail(mapThumbnail, thumbRect.width(), thumbRect.height())
            val filterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
            canvas.drawBitmap(mapThumbnail, srcRect, thumbRect, filterPaint)
            canvas.restore()
            drawGoogleWatermark(canvas, thumbRect, scaleFactor)
        }

        // 3. Draw Mode Watermark Stamp in bottom right corner
        drawModeWatermark(canvas, overlayRect, watermark, contentInternalPadding, scaleFactor)

        return resultBitmap
    }

    /**
     * Clean labels from coordinates (remove "Lat:", "Lat Lat:", etc.)
     */
    private fun cleanCoordinate(coord: String, prefix: String): String {
        return coord.replace(Regex("(?i)${prefix}:?"), "").replace(Regex("(?i)Lat:?"), "").replace(Regex("(?i)Lon:?"), "").replace(Regex("(?i)Long:?"), "").trim()
    }

    /**
     * Draw crisp "Google" watermark on bottom-left and map data attribution on bottom-right of map thumbnail
     */
    private fun drawGoogleWatermark(canvas: Canvas, rect: RectF, scale: Float) {
        val wmTextSize = max(rect.width() * 0.12f, 15f * scale)
        val wmPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = wmTextSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setShadowLayer(3f * scale, 1.5f * scale, 1.5f * scale, Color.argb(220, 0, 0, 0))
        }
        val x = rect.left + (5f * scale)
        val y = rect.bottom - (4f * scale)
        canvas.drawText("Google", x, y, wmPaint)

        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val copyText = "Map data ©$currentYear"
        val copyTextSize = max(rect.width() * 0.065f, 9f * scale)
        val copyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(220, 255, 255, 255)
            textSize = copyTextSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            setShadowLayer(2f * scale, 1f * scale, 1f * scale, Color.argb(200, 0, 0, 0))
        }
        val copyW = copyPaint.measureText(copyText)
        val copyX = rect.right - copyW - (4f * scale)
        canvas.drawText(copyText, copyX, y, copyPaint)
    }

    /**
     * Draw camera badge aligned to top right inside overlay card.
     * Rendered with an elegant cyan/blue circular camera icon followed by "Ad Free GPS Cam Visit Pro".
     */
    /**
     * Draw "Ads Free GPS Cam Visit Pro" in a separate overlay tab positioned above
     * the top-right of the GPS overlay card (not inside the GPS overlay part).
     */
    private fun drawSeparateBadgeOverlay(canvas: Canvas, overlayRect: RectF, scale: Float, address: String) {
        val badgeText = "Ads Free GPS Cam Visit Pro"

        val overlayW = overlayRect.width()
        val badgeTextSize = max(overlayW * 0.024f, 19f * scale)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = badgeTextSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setShadowLayer(2f * scale, 1f * scale, 1f * scale, Color.argb(160, 0, 0, 0))
        }

        val iconSize = badgeTextSize * 1.25f
        val iconMargin = badgeTextSize * 0.35f
        val textW = textPaint.measureText(badgeText)

        val padH = 10f * scale
        val padV = 5f * scale
        val badgeH = maxOf(iconSize, badgeTextSize) + (padV * 2)
        val badgeW = iconSize + iconMargin + textW + (padH * 2)

        val badgeRight = overlayRect.right - (14f * scale)
        val badgeBottom = overlayRect.top + 1f // Rests directly on top of the GPS overlay card
        val badgeTop = badgeBottom - badgeH
        val badgeLeft = badgeRight - badgeW
        val badgeRect = RectF(badgeLeft, badgeTop, badgeRight, badgeBottom)

        // Draw separate overlay tab background with rounded top corners (matching badge_background.xml)
        val topRadius = 6f * scale
        val radii = floatArrayOf(
            topRadius, topRadius, // top-left
            topRadius, topRadius, // top-right
            0f, 0f,               // bottom-right
            0f, 0f                // bottom-left
        )
        val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(204, 24, 28, 32)
            style = Paint.Style.FILL
        }
        val badgePath = Path().apply {
            addRoundRect(badgeRect, radii, Path.Direction.CW)
        }
        canvas.drawPath(badgePath, badgeBgPaint)

        // Draw app thumbnail icon beside text
        val circleRadius = iconSize / 2f
        val circleCenterX = badgeLeft + padH + circleRadius
        val circleCenterY = badgeTop + (badgeH / 2f)

        val appIconDrawable = ContextCompat.getDrawable(context, R.mipmap.ic_launcher_round)
            ?: ContextCompat.getDrawable(context, R.mipmap.ic_launcher)
            ?: try { context.packageManager.getApplicationIcon(context.applicationInfo) } catch (e: Exception) { null }

        var drawn = false
        if (appIconDrawable != null) {
            try {
                val bmpSize = (iconSize * 2f).toInt().coerceAtLeast(32)
                val iconBmp = Bitmap.createBitmap(bmpSize, bmpSize, Bitmap.Config.ARGB_8888)
                val iconCanvas = Canvas(iconBmp)
                appIconDrawable.setBounds(0, 0, bmpSize, bmpSize)
                appIconDrawable.draw(iconCanvas)

                val destRect = RectF(
                    circleCenterX - circleRadius,
                    circleCenterY - circleRadius,
                    circleCenterX + circleRadius,
                    circleCenterY + circleRadius
                )
                val clipCircle = Path().apply {
                    addCircle(circleCenterX, circleCenterY, circleRadius, Path.Direction.CW)
                }
                canvas.save()
                canvas.clipPath(clipCircle)
                val filterPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                canvas.drawBitmap(iconBmp, null, destRect, filterPaint)
                canvas.restore()
                iconBmp.recycle()
                drawn = true
            } catch (e: Exception) {
                drawn = false
            }
        }

        if (!drawn) {
            // Draw circular cyan/blue badge background fallback
            val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#0288D1")
                style = Paint.Style.FILL
            }
            canvas.drawCircle(circleCenterX, circleCenterY, circleRadius, circlePaint)

            // Draw camera icon centered inside circle
            val iconDrawable = ContextCompat.getDrawable(context, R.drawable.ic_camera_badge)
            iconDrawable?.let {
                val innerPad = (iconSize * 0.18f).toInt()
                val iSize = (iconSize - innerPad * 2).toInt()
                val iLeft = (circleCenterX - iSize / 2f).toInt()
                val iTop = (circleCenterY - iSize / 2f).toInt()
                it.setBounds(iLeft, iTop, iLeft + iSize, iTop + iSize)
                it.draw(canvas)
            }
        }

        val textX = badgeLeft + padH + iconSize + iconMargin
        val textY = badgeTop + (badgeH / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f)
        canvas.drawText(badgeText, textX, textY, textPaint)
    }

    private fun drawTopRightBadge(canvas: Canvas, overlayRect: RectF, scale: Float, address: String) {
        drawSeparateBadgeOverlay(canvas, overlayRect, scale, address)
    }

    /**
     * Draw "Portrait Mode" / "Landscape Mode" watermark stamping on the overlay card.
     * Rendered in clean, crisp italic typography at the bottom-right of the overlay card.
     */
    private fun drawModeWatermark(
        canvas: Canvas,
        overlayRect: RectF,
        watermark: String,
        contentPadding: Float,
        scale: Float
    ) {
        if (watermark.isBlank()) return
        val overlayW = overlayRect.width()
        val wmTextSize = max(overlayW * 0.020f, 18f * scale)
        val wmPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = wmTextSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            alpha = 240
            setShadowLayer(2.5f * scale, 1f * scale, 1f * scale, Color.argb(180, 0, 0, 0))
        }

        val textW = wmPaint.measureText(watermark)
        val textX = overlayRect.right - contentPadding - textW
        val textY = overlayRect.bottom - contentPadding * 0.8f
        canvas.drawText(watermark, textX, textY, wmPaint)
    }

    /**
     * Clean and format street address for compact, aesthetic display.
     */
    private fun formatCleanStreetAddress(fullAddress: String): String {
        if (fullAddress.isBlank() || fullAddress == "Fetching address..." || fullAddress == "GPS Details not fetched") {
            return "Fetching location..."
        }
        return fullAddress
            .replace("\n", ", ")
            .replace(Regex(",\\s*,"), ",")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trimEnd(',')
    }

    /**
     * Extract city, state, country from address for the bold header.
     */
    private fun extractCityStateCountry(fullAddress: String): String {
        if (fullAddress.isBlank()) return "Location Unavailable"
        val clean = fullAddress.replace("\n", ", ")
        val parts = clean.split(",").map { it.trim() }.filter { it.isNotBlank() }
        val header = when {
            parts.size >= 3 -> {
                val country = parts.last().replace(Regex("\\d+"), "").trim()
                val state = parts[parts.size - 2].replace(Regex("\\d+"), "").trim()
                val city = parts[parts.size - 3].replace(Regex("\\d+"), "").trim()
                listOf(city, state, country).filter { it.isNotBlank() }.joinToString(", ")
            }
            parts.size == 2 -> "${parts[0]}, ${parts[1]}"
            else -> clean
        }
        val isIndia = header.contains("India", ignoreCase = true) ||
                header.contains("भारत") ||
                header.contains("Gujarat", ignoreCase = true) ||
                header.contains("गुजरात") ||
                header.contains("Surat", ignoreCase = true) ||
                header.contains("सूरत")
        return if (isIndia && !header.contains("\uD83C\uDDEE\uD83C\uDDF3")) "$header \uD83C\uDDEE\uD83C\uDDF3" else header
    }

    private fun adjustTextSizes(
        headerPaint: TextPaint,
        valuePaint: TextPaint,
        headerText: String,
        address: String,
        combinedLatLon: String,
        datetime: String,
        textAreaWidth: Float,
        isPortrait: Boolean,
        scaleFactor: Float
    ) {
        val baseHeaderSize = (if (isPortrait) 44f else 52f) * scaleFactor
        val baseValueSize = (if (isPortrait) 25f else 30f) * scaleFactor

        headerPaint.textSize = baseHeaderSize
        valuePaint.textSize = baseValueSize

        // Compact letter spacing to tightly align words and fit text beautifully
        headerPaint.letterSpacing = -0.015f
        valuePaint.letterSpacing = -0.012f

        // 1. Fit Header
        val headerW = headerPaint.measureText(headerText)
        if (headerW > textAreaWidth) {
            val scale = (textAreaWidth / headerW).coerceIn(0.65f, 0.95f)
            headerPaint.textSize = baseHeaderSize * scale
        } else if (headerText.length < 15 && headerW < textAreaWidth * 0.45f) {
            headerPaint.textSize = baseHeaderSize * 1.15f
        }

        // 2. Fit Details
        val latLonW = valuePaint.measureText(combinedLatLon)
        val datetimeW = valuePaint.measureText(datetime)
        val maxSingleLineW = maxOf(latLonW, datetimeW)

        var scaleDetails = 1.0f
        if (maxSingleLineW > textAreaWidth) {
            scaleDetails = (textAreaWidth / maxSingleLineW).coerceIn(0.65f, 0.95f)
        }

        // 3. Address Length Factor
        val addressLen = address.length
        if (addressLen > 120) {
            scaleDetails = minOf(scaleDetails, 0.68f)
        } else if (addressLen > 80) {
            scaleDetails = minOf(scaleDetails, 0.78f)
        } else if (addressLen < 40 && maxSingleLineW < textAreaWidth * 0.55f) {
            scaleDetails = 1.15f
        }

        valuePaint.textSize = baseValueSize * scaleDetails
    }
}
