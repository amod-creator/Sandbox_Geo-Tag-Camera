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
            color = Color.parseColor("#E6EDF5") // Light blue for table headers
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
        val rightAlignText = Paint().apply {
            color = Color.BLACK
            textSize = 8f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            textAlign = Paint.Align.RIGHT
        }
        val rightAlignBoldText = Paint().apply {
            color = Color.BLACK
            textSize = 8f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }

        var pageNum = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create()
        var page = document.startPage(pageInfo)
        var canvas = page.canvas
        var currentY = margin

        fun drawHeader() {
            // Draw SBI Logo
            val sbiLogo = BitmapFactory.decodeResource(context.resources, R.drawable.sbi_logo)
            if (sbiLogo != null) {
                val logoHeight = 40f
                val logoWidth = sbiLogo.width * (logoHeight / sbiLogo.height)
                canvas.drawBitmap(sbiLogo, null, RectF(margin, currentY, margin + logoWidth, currentY + logoHeight), null)
            }
            
            // Draw right texts
            canvas.drawText("Page $pageNum", pageW - margin, currentY + 10f, rightAlignText)
            canvas.drawText("STATE BANK OF INDIA", pageW - margin, currentY + 22f, rightAlignBoldText)
            canvas.drawText("Branch: ${input.branchName}", pageW - margin, currentY + 34f, rightAlignText)
            canvas.drawText("Branch Code: ${input.branchCode}", pageW - margin, currentY + 46f, rightAlignText)
            
            currentY += 60f
            // Horizontal line
            canvas.drawLine(margin, currentY, pageW - margin, currentY, borderPaint)
            currentY += 15f
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

        // Let's implement drawing tables exactly.
        val col1Width = 200f
        val startX = margin
        val midX = margin + col1Width
        val endX = pageW - margin

        fun drawTableHeader(title: String) {
            val h = 20f
            if (currentY + h > pageH - margin) { newPage(); drawHeader() }
            canvas.drawRect(startX, currentY, endX, currentY + h, headerFillPaint)
            canvas.drawRect(startX, currentY, endX, currentY + h, borderPaint)
            canvas.drawText(title, startX + 5f, currentY + 14f, textBold)
            currentY += h
        }

        fun drawTableRow(label: String, value: String) {
            val h = 24f
            if (currentY + h > pageH - margin) { newPage(); drawHeader() }
            canvas.drawRect(startX, currentY, midX, currentY + h, borderPaint)
            canvas.drawRect(midX, currentY, endX, currentY + h, borderPaint)
            canvas.drawText(label, startX + 5f, currentY + 16f, textBold)
            val v = value.ifBlank { "" }
            canvas.drawText(v, midX + 5f, currentY + 16f, textBlack)
            currentY += h
        }

        fun drawMultiLineRow(label: String, value: String) {
            val maxChars = 75
            val lines = mutableListOf<String>()
            val words = value.split(" ")
            var currentLine = ""
            for (word in words) {
                if ((currentLine + word).length > maxChars) {
                    lines.add(currentLine)
                    currentLine = word + " "
                } else {
                    currentLine += word + " "
                }
            }
            if (currentLine.isNotBlank()) lines.add(currentLine.trim())

            val reqLines = maxOf(1, lines.size)
            val padding = 12f
            val h = padding + (reqLines * 12f) + padding
            
            if (currentY + h > pageH - margin) { newPage(); drawHeader() }
            
            canvas.drawRect(startX, currentY, midX, currentY + h, borderPaint)
            canvas.drawRect(midX, currentY, endX, currentY + h, borderPaint)
            
            canvas.drawText(label, startX + 5f, currentY + 20f, textBold)
            
            var textY = currentY + 20f
            for (line in lines) {
                canvas.drawText(line, midX + 5f, textY, textBlack)
                textY += 12f
            }
            currentY += h
        }

        // STAFF INFORMATION
        drawTableHeader("STAFF INFORMATION")
        drawTableRow("Name of Visiting Staff", input.staffName)
        drawTableRow("PF Number", input.pfNumber)
        drawTableRow("Designation", input.designation)
        currentY += 10f

        // BORROWER INFORMATION
        drawTableHeader("BORROWER INFORMATION")
        drawTableRow("Loan Account Number", input.loanAccountNumber)
        drawTableRow("Name of Borrower", input.borrowerName)
        drawTableRow("Loan Amount", input.loanAmount)
        drawTableRow("Address of Borrower", input.borrowerAddress)
        drawTableRow("Mobile Number", input.mobileNumber)
        drawTableRow("Activity", input.activity)
        currentY += 10f

        // VISIT DETAILS
        drawTableHeader("VISIT DETAILS - Date: ${input.visitDate}")
        drawMultiLineRow("Observations of Inspecting Officer", input.observations)
        currentY += 30f

        // Signature
        if (currentY + 60f > pageH - margin) { newPage(); drawHeader() }
        canvas.drawText("Signature of Visiting Staff: ___________________", startX, currentY + 10f, textBlack)
        canvas.drawText("Name: ${input.staffName}", startX, currentY + 25f, textBlack)
        canvas.drawText("Designation: ${input.designation}", startX, currentY + 40f, textBlack)
        canvas.drawText("Date: ${input.visitDate}", startX, currentY + 55f, textBlack)

        // Photos on new page(s)
        for ((index, bmp) in photos.withIndex()) {
            newPage()
            drawHeader()
            canvas.drawText("Photo ${index + 1}", startX, currentY + 15f, textBold)
            currentY += 25f

            val maxImgW = pageW - margin * 2
            val maxImgH = pageH - margin - currentY - 20f
            
            val scaleW = maxImgW / bmp.width.toFloat()
            val scaleH = maxImgH / bmp.height.toFloat()
            val scale = minOf(scaleW, scaleH)
            
            val drawW = bmp.width * scale
            val drawH = bmp.height * scale
            val drawX = startX + (maxImgW - drawW) / 2f
            
            val rect = RectF(drawX, currentY, drawX + drawW, currentY + drawH)
            
            val paint = Paint()
            paint.isAntiAlias = true
            paint.isFilterBitmap = true

            canvas.drawBitmap(bmp, null, rect, paint)
            canvas.drawRect(rect, borderPaint)
            currentY += drawH + 20f
        }

        document.finishPage(page)
        document.writeTo(out)
        document.close()
    }
}

