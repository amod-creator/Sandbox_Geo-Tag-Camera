package com.amod.geotagcamera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.OutputStream

class VisitReportPdfGenerator(private val context: Context) {

    data class Input(
        val staffName: String,
        val pfNumber: String,
        val designation: String,
        val branchName: String,
        val branchCode: String,
        val loanAccountNumber: String,
        val borrowerName: String,
        val loanAmount: String,
        val borrowerAddress: String,
        val mobileNumber: String,
        val activity: String,
        val observations: String,
        val visitDate: String,
        val gpsLocation: String
    )

    private val pageW = 595
    private val pageH = 842
    private val margin = 40f
    
    fun generate(input: Input, photos: List<Bitmap>, collageBitmap: Bitmap?, outputFile: File) {
        outputFile.outputStream().use { out ->
            generateToStream(input, photos, collageBitmap, out)
        }
    }

    fun generateToStream(input: Input, photos: List<Bitmap>, collageBitmap: Bitmap?, out: OutputStream) {
        val document = PdfDocument()

        // Paints
        val borderPaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
            color = Color.BLACK
        }
        val headerFillPaint = Paint().apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#E6EDF5")
        }
        val textBlack = Paint().apply {
            color = Color.BLACK
            textSize = 10f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            isAntiAlias = true
        }
        val textBold = Paint().apply {
            color = Color.BLACK
            textSize = 10f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
        }
        val titleTextBold = Paint().apply {
            color = Color.BLACK
            textSize = 14f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        val sbiBlue = Color.parseColor("#00338D")
        val footerPaint = Paint().apply {
            color = Color.BLACK
            textSize = 7f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            isAntiAlias = true
        }
        val footerBoldPaint = Paint().apply {
            color = Color.BLACK
            textSize = 7f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
        }

        var pageNum = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create()
        var page = document.startPage(pageInfo)
        var canvas = page.canvas
        var currentY = 0f

        val centerAlignFooter = Paint(footerPaint).apply { textAlign = Paint.Align.CENTER }
        val collagePageCount = if (collageBitmap != null) 1 else 0
        // photosPageCount is only calculated if there is NO collage
        val photosPageCount = if (collageBitmap == null && photos.isNotEmpty()) (photos.size + 1) / 2 else 0
        val estimatedTotalPages = 1 + collagePageCount + photosPageCount

        fun drawFooter() {
            val footerY = pageH - 45f
            canvas.drawLine(margin, footerY - 8f, pageW - margin, footerY - 8f, borderPaint)
            val email = "sbi.${input.branchCode}@sbi.co.in"
            canvas.drawText("bank.sbi", margin, footerY, footerPaint)
            canvas.drawText(email, margin, footerY + 12f, footerPaint)
            
            // Center: Page X of Y
            canvas.drawText("Page $pageNum of $estimatedTotalPages", pageW / 2f, footerY + 12f, centerAlignFooter)
            
            val branchLine = "${input.branchName} (${input.branchCode})"
            val rightAlignF = Paint(footerPaint).apply { textAlign = Paint.Align.RIGHT }
            val rightAlignFB = Paint(footerBoldPaint).apply { textAlign = Paint.Align.RIGHT }
            
            canvas.drawText("STATE BANK OF INDIA", pageW - margin, footerY, rightAlignFB)
            canvas.drawText(branchLine, pageW - margin, footerY + 12f, rightAlignF)
        }

        fun drawHeader() {
            val curveP = Paint().apply { color = sbiBlue; style = Paint.Style.FILL }
            val curvePath = android.graphics.Path().apply {
                moveTo(pageW - 80f, 0f); lineTo(pageW.toFloat(), 0f); lineTo(pageW.toFloat(), 80f)
                quadTo(pageW - 20f, 20f, pageW - 80f, 0f); close()
            }
            canvas.drawPath(curvePath, curveP)

            var taglineY = 110f 

            val sbiLogo = BitmapFactory.decodeResource(context.resources, R.drawable.sbi_logo)
            if (sbiLogo != null) {
                val lh = 64f; val lw = sbiLogo.width * (lh / sbiLogo.height)
                canvas.drawBitmap(sbiLogo, null, RectF(margin, 20f, margin + lw, 20f + lh), null)

                val taglinePaint = Paint().apply {
                    color = Color.parseColor("#444444")
                    textSize = 11f
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                    isAntiAlias = true
                }
                taglineY = 20f + lh + 8f
                val text1 = "The banker to every "
                canvas.drawText(text1, margin, taglineY, taglinePaint)
                val w1 = taglinePaint.measureText(text1)
                val startXTag = margin + w1
                val brushTypeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
                val indPaint = Paint(taglinePaint).apply { 
                    color = Color.parseColor("#FF9431"); typeface = brushTypeface; textSize = 14f; isAntiAlias = true 
                }
                val ianPaint = Paint(taglinePaint).apply { 
                    color = Color.parseColor("#008238"); typeface = brushTypeface; textSize = 14f; isAntiAlias = true 
                }
                val textIn = "\u0131n"; val textD = "d"; val textIanArr = "\u0131an"
                val inW = indPaint.measureText(textIn); val dW = indPaint.measureText(textD); val ianW = ianPaint.measureText(textIanArr)
                val lineY = taglineY - 10.5f 
                val barSaffron = Paint().apply { color = Color.parseColor("#FF9431"); strokeWidth = 1.6f; style = Paint.Style.STROKE; isAntiAlias = true }
                val barGreen = Paint().apply { color = Color.parseColor("#008238"); strokeWidth = 1.6f; style = Paint.Style.STROKE; isAntiAlias = true }
                val splitX = startXTag + inW + dW - 0.5f
                canvas.drawLine(startXTag, lineY, splitX, lineY, barSaffron)
                canvas.drawLine(splitX, lineY, startXTag + inW + dW + ianW, lineY, barGreen)
                canvas.drawText(textIn, startXTag, taglineY, indPaint)
                canvas.drawText(textD, startXTag + inW, taglineY, indPaint)
                canvas.drawText(textIanArr, startXTag + inW + dW, taglineY, ianPaint)
                val bindiSaffron = Paint().apply { color = Color.parseColor("#FF9431"); style = Paint.Style.FILL; isAntiAlias = true }
                canvas.drawCircle(startXTag + 3.0f, lineY - 6f, 2.0f, bindiSaffron)
            }
            
            val rightX = pageW - margin - 20f 
            val hP = Paint().apply {
                color = sbiBlue; textSize = 18f; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD); textAlign = Paint.Align.RIGHT; isAntiAlias = true
            }
            canvas.drawText("भारतीय स्टेट बैंक", rightX, 50f, hP)
            canvas.drawText("STATE BANK OF INDIA", rightX, 75f, hP)
            
            currentY = taglineY + 14f 
            canvas.drawLine(margin, currentY, pageW - margin, currentY, borderPaint)
            
            if (pageNum > 1) {
                val headerTextPaint = Paint().apply {
                    color = Color.BLACK; textSize = 9f; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD); textAlign = Paint.Align.RIGHT; isAntiAlias = true
                }
                currentY += 15f
                canvas.drawText("Name of Borrower : ${input.borrowerName}", pageW - margin, currentY, headerTextPaint)
                currentY += 12f
                canvas.drawText("Loan Account Number : ${input.loanAccountNumber}", pageW - margin, currentY, headerTextPaint)
            }
            currentY += 15f
            drawFooter()
        }

        fun newPage() {
            document.finishPage(page)
            pageNum++
            pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create()
            page = document.startPage(pageInfo)
            canvas = page.canvas
            drawHeader()
        }

        drawHeader()
        
        // --- DYNAMIC PAGE 1 LAYOUT ENGINE ---
        val col1Width = 180f
        val startX = margin
        val midX = margin + col1Width
        val endX = pageW - margin
        val contentWidth = endX - midX - 10f

        data class Row(val label: String, val value: String, val isHeader: Boolean = false)
        val p1Rows = mutableListOf<Row>()
        p1Rows.add(Row("BORROWER INFORMATION", "", true))
        p1Rows.add(Row("Name of Borrower", input.borrowerName))
        p1Rows.add(Row("Loan Account Number", input.loanAccountNumber))
        p1Rows.add(Row("Loan Amount", "₹ ${input.loanAmount}"))
        p1Rows.add(Row("Mobile Number", input.mobileNumber))
        p1Rows.add(Row("GPS Location", input.gpsLocation))
        p1Rows.add(Row("Address of Borrower", input.borrowerAddress))
        p1Rows.add(Row("Activity", input.activity))
        p1Rows.add(Row("VISIT DETAILS - Date of Visit: ${input.visitDate}", "", true))
        p1Rows.add(Row("Observations of Inspecting Officer", input.observations))
        p1Rows.add(Row("STAFF INFORMATION", "", true))
        p1Rows.add(Row("Name of Staff", input.staffName))
        p1Rows.add(Row("PF Number", input.pfNumber))
        p1Rows.add(Row("Designation", input.designation))

        // Measure row heights
        fun getMultilineLines(text: String, width: Float, paint: Paint): List<List<String>> {
            val res = mutableListOf<List<String>>()
            val paragraphs = text.split("\n")
            val spaceW = paint.measureText(" ")
            for (p in paragraphs) {
                val words = p.trim().split(" ").filter { it.isNotBlank() }
                if (words.isEmpty()) { res.add(emptyList()); continue }
                var curLine = mutableListOf<String>(); var curWidth = 0f
                for (word in words) {
                    val wordW = paint.measureText(word)
                    if (curWidth + wordW + (if (curLine.isEmpty()) 0f else spaceW) > width) {
                        if (curLine.isNotEmpty()) res.add(curLine)
                        curLine = mutableListOf(word); curWidth = wordW
                    } else {
                        curLine.add(word); curWidth += wordW + (if (curLine.isEmpty()) 0f else spaceW)
                    }
                }
                if (curLine.isNotEmpty()) res.add(curLine)
            }
            return res
        }

        val rowMeasuredHeights = p1Rows.map { row ->
            if (row.isHeader) 22f
            else {
                val lines = getMultilineLines(row.value, contentWidth, textBlack)
                maxOf(22f, 10f + lines.size * 14f)
            }
        }

        val baseSpacings = 10f * 3 + 25f + 10f + 120f // title, title gap, 3 section gaps, signature section (increased for signing space)
        val totalIdealHeight = rowMeasuredHeights.sum() + baseSpacings
        val footerAreaY = pageH - 45f - 8f
        val availableHeight = footerAreaY - currentY
        
        var shrinkFactor = 1.0f
        if (totalIdealHeight > availableHeight && availableHeight > 0) {
            shrinkFactor = availableHeight / totalIdealHeight
        }
        
        // Final adjusted constants
        val rowHScale = if (shrinkFactor < 1.0f) shrinkFactor else 1.0f
        val gapScale = if (shrinkFactor < 1.0f) shrinkFactor else 1.0f
        val lineSpacing = 14f * rowHScale
        val padding = 10f * rowHScale

        // Draw Title
        canvas.drawText("VISIT REPORT", pageW / 2f, currentY + 10f * gapScale, titleTextBold)
        currentY += 25f * gapScale

        // Draw Rows
        for (i in p1Rows.indices) {
            val row = p1Rows[i]
            val h = rowMeasuredHeights[i] * rowHScale
            
            if (row.isHeader) {
                canvas.drawRect(startX, currentY, endX, currentY + h, headerFillPaint)
                canvas.drawRect(startX, currentY, endX, currentY + h, borderPaint)
                canvas.drawText(row.label, startX + 5f, currentY + h * 0.7f, textBold)
                currentY += h
            } else {
                canvas.drawRect(startX, currentY, midX, currentY + h, borderPaint)
                canvas.drawRect(midX, currentY, endX, currentY + h, borderPaint)
                canvas.drawText(row.label, startX + 5f, currentY + h * 0.35f + 10f * rowHScale * 0.5f, textBold)

                val lines = getMultilineLines(row.value, contentWidth, textBlack)
                var textY = currentY + padding + 8f * rowHScale
                for (lIdx in lines.indices) {
                    val lineWords = lines[lIdx]
                    if (lineWords.isEmpty()) { textY += lineSpacing; continue }
                    val isLastLine = (lIdx == lines.size - 1)
                    
                    var x = midX + 5f
                    if (isLastLine || lineWords.size == 1) {
                        canvas.drawText(lineWords.joinToString(" "), x, textY, textBlack)
                    } else {
                        val totalWordWidth = lineWords.sumOf { textBlack.measureText(it).toDouble() }.toFloat()
                        val totalGapSpace = contentWidth - totalWordWidth
                        val gap = totalGapSpace / (lineWords.size - 1)
                        for (w in lineWords) {
                            canvas.drawText(w, x, textY, textBlack)
                            x += textBlack.measureText(w) + gap
                        }
                    }
                    textY += lineSpacing
                }
                currentY += h
            }
            if (i < p1Rows.size - 1 && p1Rows[i+1].isHeader) currentY += 10f * gapScale
        }
        
        currentY += 10f * gapScale

        // Signature section
        val signatureFile = File(context.filesDir, "signature.png")
        var sigBitmap: Bitmap? = null
        var drawW = 0f
        var drawH = 0f
        val sharedPrefs = context.getSharedPreferences("com.amod.geotagcamera.PREFERENCES", android.content.Context.MODE_PRIVATE)
        val signatureRequired = sharedPrefs.getBoolean("signature_required", false)
        if (signatureRequired && signatureFile.exists()) {
            try {
                val tempBitmap = BitmapFactory.decodeFile(signatureFile.absolutePath)
                if (tempBitmap != null) {
                    sigBitmap = tempBitmap
                    val signatureWidth = sharedPrefs.getFloat("signature_pdf_width", 120f)
                    val signatureHeight = sharedPrefs.getFloat("signature_pdf_height", 40f)
                    val signatureOffsetX = sharedPrefs.getFloat("signature_pdf_offset_x", -40f)
                    val signatureOffsetY = sharedPrefs.getFloat("signature_pdf_offset_y", 0f)
                    val signatureRotation = sharedPrefs.getFloat("signature_pdf_rotation", 0f)

                    val maxWidth = signatureWidth * gapScale
                    val maxHeight = signatureHeight * gapScale
                    drawW = maxWidth
                    drawH = maxHeight
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val sigY = currentY + 50f * gapScale // Increased gap from 10f to 50f for signing space
        
        if (sigBitmap != null) {
            val sharedPrefs = context.getSharedPreferences("com.amod.geotagcamera.PREFERENCES", android.content.Context.MODE_PRIVATE)
            val signatureOffsetX = sharedPrefs.getFloat("signature_pdf_offset_x", -40f)
            val signatureOffsetY = sharedPrefs.getFloat("signature_pdf_offset_y", 0f)
            val signatureRotation = sharedPrefs.getFloat("signature_pdf_rotation", 0f)

            val sigLeft = startX + (signatureOffsetX * gapScale) - 35f * gapScale
            val sigTop = sigY - drawH - (2f * gapScale) + (signatureOffsetY * gapScale)

            canvas.save()
            val sigCenterX = sigLeft + drawW / 2f
            val sigCenterY = sigTop + drawH / 2f
            canvas.rotate(signatureRotation, sigCenterX, sigCenterY)
            canvas.drawBitmap(sigBitmap, null, RectF(sigLeft, sigTop, sigLeft + drawW, sigTop + drawH), null)
            canvas.restore()
        }

        val linePaint = Paint(textBlack).apply { strokeWidth = 1f }
        canvas.drawLine(startX, sigY, startX + 160f * gapScale, sigY, linePaint)
        canvas.drawText("Signature of Visiting Official", startX, sigY + 15f * rowHScale, textBold)
        canvas.drawText("(${input.staffName})", startX, sigY + 30f * rowHScale, textBlack)
        canvas.drawText("Designation: ${input.designation}", startX, sigY + 45f * rowHScale, textBlack)
        val todayDateStr = java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.getDefault()).format(java.util.Date())
        canvas.drawText("Date: $todayDateStr", startX, sigY + 60f * rowHScale, textBlack)

        // --- END DYNAMIC LAYOUT ---

        // Photos: 2 images per page
        if (photos.isNotEmpty() || collageBitmap != null) {
            val paint = Paint().apply { isAntiAlias = true; isFilterBitmap = true }
            collageBitmap?.let { collage ->
                newPage()
                canvas.drawText("COLLAGE REPORT - SITE PHOTOGRAPHS", pageW / 2f, currentY + 15f, titleTextBold)
                currentY += 30f
                val maxImgW = pageW - margin * 2
                val maxImgH = pageH - 90f - currentY
                val scale = minOf(maxImgW / collage.width.toFloat(), maxImgH / collage.height.toFloat())
                val drawW = collage.width * scale
                val drawH = collage.height * scale
                val drawX = margin + (maxImgW - drawW) / 2f
                val drawY = currentY + (maxImgH - drawH) / 2f
                val rect = RectF(drawX, drawY, drawX + drawW, drawY + drawH)
                canvas.drawBitmap(collage, null, rect, paint)
                canvas.drawRect(rect, borderPaint)
                currentY = pageH.toFloat() 
            }

            var photoIter = photos.iterator(); var pCount = 1
            // Only draw individual photos if NO collage is present
            if (collageBitmap == null) {
                while (photoIter.hasNext()) {
                    newPage()
                    canvas.drawText("SITE PHOTOGRAPHS", pageW / 2f, currentY + 15f, titleTextBold)
                    currentY += 30f
                    val photoAreaH = pageH - 120f - currentY
                    val singlePhotoAreaH = (photoAreaH - 30f) / 2f
                    for (i in 0..1) {
                        if (photoIter.hasNext()) {
                            val bmp = photoIter.next()
                            val scaledBmp = if (bmp.width > 1200 || bmp.height > 1200) {
                                val ratio = minOf(1200f / bmp.width, 1200f / bmp.height)
                                Bitmap.createScaledBitmap(bmp, (bmp.width * ratio).toInt(), (bmp.height * ratio).toInt(), true)
                            } else bmp
                            val maxImgW = pageW - margin * 2
                            val scale = minOf(maxImgW / scaledBmp.width.toFloat(), singlePhotoAreaH / scaledBmp.height.toFloat())
                            val drawW = scaledBmp.width * scale; val drawH = scaledBmp.height * scale
                            val drawX = margin + (maxImgW - drawW) / 2f
                            canvas.drawText("Photograph ${pCount++}", margin, currentY - 5f, textBold)
                            val rect = RectF(drawX, currentY, drawX + drawW, currentY + drawH)
                            canvas.drawBitmap(scaledBmp, null, rect, paint); canvas.drawRect(rect, borderPaint)
                            currentY += drawH + 35f
                        }
                    }
                }
            }
        }

        document.finishPage(page)
        document.writeTo(out)
        document.close()
    }
}
