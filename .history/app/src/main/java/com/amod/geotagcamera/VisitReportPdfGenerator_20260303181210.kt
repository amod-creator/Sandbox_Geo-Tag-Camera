package com.amod.geotagcamera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
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
        val visitDate: String
    )

    private val pageW = 595
    private val pageH = 842
    private val margin = 40f
    
    fun generate(input: Input, photos: List<Bitmap>, outputFile: File) {
        outputFile.outputStream().use { out ->
            generateToStream(input, photos, out)
        }
    }

    fun generateToStream(input: Input, photos: List<Bitmap>, out: OutputStream) {
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
        }
        val textBold = Paint().apply {
            color = Color.BLACK
            textSize = 10f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val titleTextBold = Paint().apply {
            color = Color.BLACK
            textSize = 14f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val sbiBlue = Color.parseColor("#00338D")
        val footerPaint = Paint().apply {
            color = Color.BLACK
            textSize = 7f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val footerBoldPaint = Paint().apply {
            color = Color.BLACK
            textSize = 7f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }

        var pageNum = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create()
        var page = document.startPage(pageInfo)
        var canvas = page.canvas
        var currentY = 0f

        fun drawFooter() {
            val footerY = pageH - 45f
            canvas.drawLine(margin, footerY - 8f, pageW - margin, footerY - 8f, borderPaint)
            val email = "sbi.${input.branchCode}@sbi.co.in"
            canvas.drawText("bank.sbi", margin, footerY, footerPaint)
            canvas.drawText(email, margin, footerY + 12f, footerPaint)
            
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

            val sbiLogo = BitmapFactory.decodeResource(context.resources, R.drawable.sbi_logo)
            if (sbiLogo != null) {
                val lh = 32f; val lw = sbiLogo.width * (lh / sbiLogo.height)
                canvas.drawBitmap(sbiLogo, null, RectF(margin, 20f, margin + lw, 20f + lh), null)
            }
            
            val rightX = pageW - margin - 20f 
            val hP = Paint().apply {
                color = sbiBlue; textSize = 9f; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD); textAlign = Paint.Align.RIGHT
            }
            canvas.drawText("भारतीय स्टेट बैंक", rightX, 35f, hP)
            canvas.drawText("STATE BANK OF INDIA", rightX, 48f, hP)
            
            currentY = 82f
            canvas.drawLine(margin, currentY, pageW - margin, currentY, borderPaint)
            currentY += 15f
            drawFooter()
        }

        fun newPage() {
            document.finishPage(page)
            pageNum++
            pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create()
            page = document.startPage(pageInfo)
            canvas = page.canvas
            currentY = margin
        }

        drawHeader()
        
        // VISIT REPORT
        canvas.drawText("VISIT REPORT", pageW / 2f, currentY + 10f, titleTextBold)
        currentY += 25f
        canvas.drawLine(margin, currentY, pageW - margin, currentY, borderPaint)
        currentY += 20f

        // Table Constants
        val col1Width = 180f
        val startX = margin
        val midX = margin + col1Width
        val endX = pageW - margin

        fun drawTableHeader(title: String) {
            val h = 22f
            if (currentY + h > pageH - margin) { newPage(); drawHeader() }
            canvas.drawRect(startX, currentY, endX, currentY + h, headerFillPaint)
            canvas.drawRect(startX, currentY, endX, currentY + h, borderPaint)
            canvas.drawText(title, startX + 5f, currentY + 15f, textBold)
            currentY += h
        }

        fun drawTableRow(label: String, value: String) {
            val h = 22f
            if (currentY + h > pageH - margin) { newPage(); drawHeader() }
            canvas.drawRect(startX, currentY, midX, currentY + h, borderPaint)
            canvas.drawRect(midX, currentY, endX, currentY + h, borderPaint)
            canvas.drawText(label, startX + 5f, currentY + 15f, textBold)
            canvas.drawText(value.ifBlank { "NA" }, midX + 5f, currentY + 15f, textBlack)
            currentY += h
        }

        fun drawMultiLineRow(label: String, value: String) {
            val maxChars = 70
            val lines = mutableListOf<String>()
            val processedValue = value.ifBlank { "NA" }
            val words = processedValue.split(" ")
            var currentLine = ""
            for (word in words) {
                if ((currentLine + word).length > maxChars) {
                    lines.add(currentLine.trim())
                    currentLine = word + " "
                } else {
                    currentLine += word + " "
                }
            }
            if (currentLine.isNotBlank()) lines.add(currentLine.trim())

            val reqLines = maxOf(1, lines.size)
            val h = 20f + (reqLines * 14f)
            
            if (currentY + h > pageH - margin) { newPage(); drawHeader() }
            
            canvas.drawRect(startX, currentY, midX, currentY + h, borderPaint)
            canvas.drawRect(midX, currentY, endX, currentY + h, borderPaint)
            canvas.drawText(label, startX + 5f, currentY + 18f, textBold)
            
            var textY = currentY + 18f
            for (line in lines) {
                canvas.drawText(line, midX + 5f, textY, textBlack)
                textY += 14f
            }
            currentY += h
        }

        // 1. BORROWER INFORMATION
        drawTableHeader("BORROWER INFORMATION")
        drawTableRow("Name of Borrower", input.borrowerName)
        drawTableRow("Loan Account Number", input.loanAccountNumber)
        drawTableRow("Loan Amount", "₹ ${input.loanAmount}")
        drawTableRow("Mobile Number", input.mobileNumber)
        drawMultiLineRow("Address of Borrower", input.borrowerAddress)
        drawTableRow("Activity", input.activity)
        currentY += 10f

        // 2. VISIT DETAILS
        drawTableHeader("VISIT DETAILS - Date: ${input.visitDate}")
        drawMultiLineRow("Observations of Inspecting Officer", input.observations)
        currentY += 10f

        // 3. STAFF INFORMATION (Moved here as requested)
        drawTableHeader("STAFF INFORMATION")
        drawTableRow("Name of Staff", input.staffName)
        drawTableRow("PF Number", input.pfNumber)
        drawTableRow("Designation", input.designation)
        currentY += 30f

        // Signature section
        if (currentY + 60f > pageH - margin) { newPage(); drawHeader() }
        canvas.drawText("Signature of Visiting Official: ___________________", startX, currentY + 10f, textBlack)
        canvas.drawText("(${input.staffName})", startX, currentY + 25f, textBlack)
        canvas.drawText("Designation: ${input.designation}", startX, currentY + 40f, textBlack)
        canvas.drawText("Date: ${input.visitDate}", startX, currentY + 55f, textBlack)

        // Photos: 2 images per page
        if (photos.isNotEmpty()) {
            val paint = Paint().apply {
                isAntiAlias = true
                isFilterBitmap = true
            }

            var photoIter = photos.iterator()
            while (photoIter.hasNext()) {
                newPage()
                drawHeader()
                canvas.drawText("SITE PHOTOGRAPHS", pageW / 2f, currentY + 10f, titleTextBold)
                currentY += 25f

                val photoAreaH = pageH - margin - currentY - 20f
                val singlePhotoAreaH = (photoAreaH - 30f) / 2f // Divide height by 2, minus spacing

                // Draw up to 2 photos
                for (i in 0..1) {
                    if (photoIter.hasNext()) {
                        val bmp = photoIter.next()
                        
                        // Downsample bitmap for size optimization (max width/height 1000px)
                        val scaledBmp = if (bmp.width > 1000 || bmp.height > 1000) {
                            val ratio = minOf(1000f / bmp.width, 1000f / bmp.height)
                            Bitmap.createScaledBitmap(bmp, (bmp.width * ratio).toInt(), (bmp.height * ratio).toInt(), true)
                        } else {
                            bmp
                        }

                        val maxImgW = pageW - margin * 2
                        val scaleW = maxImgW / scaledBmp.width.toFloat()
                        val scaleH = singlePhotoAreaH / scaledBmp.height.toFloat()
                        val scale = minOf(scaleW, scaleH)

                        val drawW = scaledBmp.width * scale
                        val drawH = scaledBmp.height * scale
                        val drawX = margin + (maxImgW - drawW) / 2f
                        
                        val rect = RectF(drawX, currentY, drawX + drawW, currentY + drawH)
                        canvas.drawBitmap(scaledBmp, null, rect, paint)
                        canvas.drawRect(rect, borderPaint)
                        
                        currentY += drawH + 25f // Next photo position or end of page
                        
                        // If it's a new scaled bitmap, recycle it after drawing (original is managed by activity)
                        if (scaledBmp != bmp) {
                            // scaledBmp.recycle() // Note: Be careful with recycling if used elsewhere, but here it's temporary
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

