package com.amod.geotagcamera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.ceil

class AgriPssPdfGenerator(private val context: Context) {

    data class Input(
        val applicantName: String,
        val cifNo: String,
        val constitution: String,
        val fatherName: String,
        val natureOfApplicant: String,
        val keyPersonName: String,
        val keyPersonContact: String,
        val residenceAddress: String,
        val residenceVerified: String,
        val residencePersonMet: String,
        val residenceRelation: String,
        val workplaceAddress: String,
        val workplaceVerified: String,
        val workplacePersonMet: String,
        val workplaceRelation: String,
        val collateralObtained: String,
        val collateralNature: String,
        val guarantorName: String,
        val collateralAddress: String,
        val collateralDemarcated: String,
        val collateralAccessible: String,
        val collateralVerified: String,
        val remarks: String,
        val otherLoan: String,
        val dispute: String,
        val annexureAttached: String,
        val officialName: String,
        val designation: String,
        val place: String,
        val date: String
    )

    // ==== PDF constants (A4 points: 595 x 842) ====
    private val pageW = 595
    private val pageH = 842
    private val margin = 28f
    private val topStart = 72f // leave space for header/title
    private val bottomLimit = pageH - margin

    // Table columns (label | value)
    private val col1X = margin
    private val col2X = 285f
    private val colRight = pageW - margin

    private val borderPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f
        isAntiAlias = true
    }
    private val textPaint = Paint().apply {
        textSize = 11.5f
        isAntiAlias = true
    }
    private val boldPaint = Paint(textPaint).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val titlePaint = Paint(textPaint).apply {
        textSize = 14.5f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val headerFillPaint = Paint().apply {
        style = Paint.Style.FILL
        // Opaque light yellow (Material Yellow 200): ARGB 0xFFFFF59D
        color = 0xFFFFF59D.toInt()
        isAntiAlias = true
    }

    fun generate(input: Input, outputFile: File, collageBitmap: Bitmap? = null, photos: List<Bitmap> = emptyList()) {
        val pdf = PdfDocument()
        var pageNum = 1
        var page = pdf.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create())
        var canvas = page.canvas
        var cursorY = drawHeader(canvas, pageNum)

        fun newPage(): Pair<Canvas, Float> {
            pdf.finishPage(page)
            pageNum += 1
            page = pdf.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create())
            canvas = page.canvas
            val y = drawHeader(canvas, pageNum)
            return canvas to y
        }

        // Helper to measure wrapped text height
        fun wrappedHeight(text: String, paint: Paint, maxWidth: Float, lineSpacing: Float = 4f): Float {
            if (text.isBlank()) return paint.textSize + 6f
            val lines = wrapText(text, paint, maxWidth)
            return lines.size * (paint.textSize + lineSpacing) + 6f
        }

        // Helper to draw a table row (with border + wrapped text)
        fun drawRow(label: String, value: String): Float {
            val labelH = wrappedHeight(label, boldPaint, col2X - col1X - 8f)
            val valueH = wrappedHeight(value, textPaint, colRight - col2X - 8f)
            val rowH = ceil(maxOf(labelH, valueH)) + 6f

            // page break if needed
            if (cursorY + rowH > bottomLimit) {
                val pair = newPage()
                canvas = pair.first
                cursorY = pair.second
            }

            // Row rect
            val top = cursorY
            val bottom = cursorY + rowH
            // draw borders
            canvas.drawRect(RectF(col1X, top, colRight, bottom), borderPaint)
            // vertical middle divider
            canvas.drawLine(col2X, top, col2X, bottom, borderPaint)

            // draw label (bold)
            drawWrappedText(canvas, label, boldPaint, col1X + 6f, top + 14f, col2X - col1X - 12f)
            // draw value (normal)
            drawWrappedText(canvas, value, textPaint, col2X + 6f, top + 14f, colRight - col2X - 12f)

            cursorY = bottom
            return rowH
        }

        fun drawSelectOptionTable() {
            cursorY += drawSectionTitle(canvas, cursorY, "Select Appropriate Option")

            val optRowH = 28f
            fun drawOptionRow(label: String, withCheckbox: Boolean = true) {
                if (cursorY + optRowH > bottomLimit) {
                    val pair = newPage(); canvas = pair.first; cursorY = pair.second
                }
                val top = cursorY
                val bottom = cursorY + optRowH
                // full-width bordered row
                canvas.drawRect(RectF(col1X, top, colRight, bottom), borderPaint)
                if (withCheckbox) {
                    val bx = col1X + 10f
                    val by = top + (optRowH - 12f) / 2f
                    canvas.drawRect(RectF(bx, by, bx + 12f, by + 12f), borderPaint)
                    canvas.drawText(label, bx + 18f, by + 10f, textPaint)
                } else {
                    canvas.drawText(label, col1X + 10f, top + 18f, textPaint)
                }
                cursorY = bottom
            }

            drawOptionRow("Annexure – Non KCC Loan")
            drawOptionRow("Annexure – KCC Loan")
            drawOptionRow("Annexure – KCC AHF Loan")
            drawOptionRow("Multiple (Please tick appropriate columns above)", withCheckbox = false)
        }

        // ======= Page-1: tabular sections (A–D) =======
        cursorY += drawSectionTitle(canvas, cursorY, "A. Basic Information")
        drawRow("Name of the Applicant", input.applicantName)
        drawRow("CIF No.", input.cifNo)
        drawRow("Constitution of the Unit", input.constitution)
        drawRow("Father/Husband Name", input.fatherName)
        drawRow("Nature of Applicant", input.natureOfApplicant)
        drawRow("Name & Contact No. of Key Person in the Unit", listOf(input.keyPersonName, input.keyPersonContact).filter { it.isNotBlank() }.joinToString(" | "))

        cursorY += drawSectionTitle(canvas, cursorY, "B. Verification Report of Residence", "Date of Visit: ${input.date}")
        drawRow("Residence Address", input.residenceAddress)
        drawRow("Residence Address verified with Address Proof", input.residenceVerified)
        drawRow("Name of person/s met at Residence and Relationship with the Borrower", listOf(input.residencePersonMet, input.residenceRelation).filter { it.isNotBlank() }.joinToString(" | "))

        cursorY += drawSectionTitle(canvas, cursorY, "C. Verification Report of Workplace", "Date of Visit: ${input.date}")
        drawRow("Workplace Address", input.workplaceAddress)
        drawRow("Workplace Address verified with Address Proof", input.workplaceVerified)
        drawRow("Name of Key person/s Met at Workplace and Relationship with the Borrower", listOf(input.workplacePersonMet, input.workplaceRelation).filter { it.isNotBlank() }.joinToString(" | "))

        cursorY += drawSectionTitle(canvas, cursorY, "D. Verification Report of Collateral Security/Guarantor", "Date of Visit: ${input.date}")
        drawRow("Collateral Obtained", input.collateralObtained)
        drawRow("Nature of Property", input.collateralNature)
        drawRow("Name of Guarantor/s (if any)", input.guarantorName)
        drawRow("Address of Collateral Security", input.collateralAddress)
        drawRow("Collateral Security is clearly demarcated and identifiable", input.collateralDemarcated)
        drawRow("Collateral Security is not land locked and easily accessible", input.collateralAccessible)
        drawRow("Collateral Property Address verified", input.collateralVerified)

        // ======= Page-2: Section E + Declaration (and spillover for long remarks) =======
        // Force new page if we are close to bottom
        if (cursorY + 80f > bottomLimit) {
            val pair = newPage(); canvas = pair.first; cursorY = pair.second
        }

        cursorY += drawSectionTitle(canvas, cursorY, "E. Other Inputs")
        drawRow("Remarks about Applicant not covered above", input.remarks)
        drawRow("Any Loan taken from Other than Bank", input.otherLoan)
        drawRow("Any Dispute / litigation against the applicant", input.dispute)
        drawRow("Attached Annexure", input.annexureAttached)

        // Select Appropriate Option (tabular block)
        if (cursorY + 140f > bottomLimit) { val pair = newPage(); canvas = pair.first; cursorY = pair.second }
        drawSelectOptionTable()

        // Force some spacing before declaration block
        if (cursorY + 120f > bottomLimit) {
            val pair = newPage(); canvas = pair.first; cursorY = pair.second
        }

        cursorY += drawSectionTitle(canvas, cursorY, "Declaration")
        val decl = """
Declaration:
I, _______________________________________________ [Name of the Official], hereby declare that I have
thoroughly conducted the pre-sanction verification process for the
_________________________________________________[Borrower/Borrower entity name] as per the guidelines
of the State Bank of India. In the course of this verification, I have diligently reviewed all the details and relevant documentation provided by the applicant in his loan application form.
I affirm that to the best of my knowledge,
o The information provided by the applicant is accurate and complete
o Any discrepancies or inconsistencies identified during the verification process have been duly noted.
""".trimIndent()
        val paraH = drawParagraphKeepNewlines(canvas, decl, col1X, cursorY + 14f, colRight - col1X, textPaint)
        cursorY += 14f + paraH + 10f

        // Signature block as plain lines (no table)
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

                    val maxWidth = signatureWidth
                    val maxHeight = signatureHeight
                    drawW = maxWidth
                    drawH = maxHeight
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val lineGap = textPaint.textSize + 8f
        val sigNeededSpace = 5 * lineGap + 8f + (if (sigBitmap != null) drawH + 5f else 0f)

        if (cursorY + sigNeededSpace > bottomLimit) {
            val pair = newPage()
            canvas = pair.first
            cursorY = pair.second
        }

        // Draw signature above "Signature of Inspecting Official" if uploaded
        if (sigBitmap != null) {
            val sharedPrefs = context.getSharedPreferences("com.amod.geotagcamera.PREFERENCES", android.content.Context.MODE_PRIVATE)
            val signatureOffsetX = sharedPrefs.getFloat("signature_pdf_offset_x", -40f)
            val signatureOffsetY = sharedPrefs.getFloat("signature_pdf_offset_y", 0f)
            val signatureRotation = sharedPrefs.getFloat("signature_pdf_rotation", 0f)

            val sigLeft = col1X + signatureOffsetX - 35f
            val sigTop = cursorY + lineGap - drawH - 2f + signatureOffsetY

            canvas.save()
            val sigCenterX = sigLeft + drawW / 2f
            val sigCenterY = sigTop + drawH / 2f
            canvas.rotate(signatureRotation, sigCenterX, sigCenterY)
            canvas.drawBitmap(sigBitmap, null, RectF(sigLeft, sigTop, sigLeft + drawW, sigTop + drawH), null)
            canvas.restore()
        }

        canvas.drawText("Signature of Inspecting Official", col1X, cursorY + lineGap, textPaint)
        canvas.drawText("Name of Official: ${input.officialName}", col1X, cursorY + 2 * lineGap, textPaint)
        canvas.drawText("Designation: ${input.designation}", col1X, cursorY + 3 * lineGap, textPaint)
        canvas.drawText("Place: ${input.place}", col1X, cursorY + 4 * lineGap, textPaint)
        canvas.drawText("Date: ${input.date}", col1X, cursorY + 5 * lineGap, textPaint)
        cursorY += 5 * lineGap + 8f

        // Finish last page
        pdf.finishPage(page)

        if (collageBitmap != null) {
            pageNum += 1
            val collagePage = pdf.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create())
            val collageCanvas = collagePage.canvas
            val headerBottom = drawHeader(collageCanvas, pageNum)

            val titlePaintCenter = Paint(boldPaint).apply {
                textSize = 13f
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
            }
            collageCanvas.drawText("COLLAGE REPORT - SITE PHOTOGRAPHS", pageW / 2f, headerBottom + 18f, titlePaintCenter)

            val maxImgW = pageW - margin * 2
            val maxImgH = bottomLimit - (headerBottom + 36f)
            val scale = minOf(maxImgW / collageBitmap.width.toFloat(), maxImgH / collageBitmap.height.toFloat())
            val drawW = collageBitmap.width * scale
            val drawH = collageBitmap.height * scale
            val drawX = margin + (maxImgW - drawW) / 2f
            val drawY = headerBottom + 36f + (maxImgH - drawH) / 2f

            val rect = RectF(drawX, drawY, drawX + drawW, drawY + drawH)
            val imgPaint = Paint().apply { isAntiAlias = true; isFilterBitmap = true }
            collageCanvas.drawBitmap(collageBitmap, null, rect, imgPaint)
            collageCanvas.drawRect(rect, borderPaint)

            pdf.finishPage(collagePage)
        } else if (photos.isNotEmpty()) {
            val imgPaint = Paint().apply { isAntiAlias = true; isFilterBitmap = true }
            var photoIter = photos.iterator()
            var pCount = 1
            while (photoIter.hasNext()) {
                pageNum += 1
                val photoPage = pdf.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create())
                val pCanvas = photoPage.canvas
                var curY = drawHeader(pCanvas, pageNum)

                val titlePaintCenter = Paint(boldPaint).apply {
                    textSize = 13f
                    textAlign = Paint.Align.CENTER
                    isAntiAlias = true
                }
                pCanvas.drawText("SITE PHOTOGRAPHS", pageW / 2f, curY + 18f, titlePaintCenter)
                curY += 32f
                val photoAreaH = bottomLimit - curY
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
                        val drawW = scaledBmp.width * scale
                        val drawH = scaledBmp.height * scale
                        val drawX = margin + (maxImgW - drawW) / 2f
                        pCanvas.drawText("Photograph ${pCount++}", margin, curY - 5f, boldPaint)
                        val rect = RectF(drawX, curY, drawX + drawW, curY + drawH)
                        pCanvas.drawBitmap(scaledBmp, null, rect, imgPaint)
                        pCanvas.drawRect(rect, borderPaint)
                        curY += drawH + 35f
                    }
                }
                pdf.finishPage(photoPage)
            }
        }

        try {
            FileOutputStream(outputFile).use { out -> pdf.writeTo(out) }
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            pdf.close()
        }
    }

    private fun drawHeader(canvas: Canvas, pageNum: Int): Float {
        // Top header similar across pages; you can add SBI logo draw if needed
        val title = "Pre-sanction Survey format for Applicant/Borrower – Annexure I"
        canvas.drawText(title, margin, 40f, titlePaint)
        canvas.drawText("AGRI PSS REPORT", margin, 58f, boldPaint)
        // page number (right aligned)
        val pageStr = "Page $pageNum"
        val w = titlePaint.measureText(pageStr)
        canvas.drawText(pageStr, pageW - margin - w, 40f, textPaint)
        return topStart
    }

    private fun drawSectionTitle(
        canvas: Canvas,
        y: Float,
        title: String,
        rightText: String? = null
    ): Float {
        // Yellow bar background
        val barTop = y + 4f
        val barBottom = barTop + 22f
        canvas.drawRect(RectF(col1X, barTop, colRight, barBottom), headerFillPaint)

        // Text baselines
        val baseline = barTop + 16f
        canvas.drawText(title, col1X + 6f, baseline, boldPaint)

        // Optional right-aligned text on the same bar (e.g., Date of Visit)
        rightText?.let {
            val w = textPaint.measureText(it)
            canvas.drawText(it, colRight - 6f - w, baseline, textPaint)
        }
        // Return vertical increment (bar height + small gap)
        return (barBottom - y) + 4f
    }

    private fun drawParagraphKeepNewlines(
        canvas: Canvas,
        text: String,
        startX: Float,
        startY: Float,
        maxWidth: Float,
        paint: Paint = textPaint,
        lineSpacing: Float = 4f
    ): Float {
        var y = startY
        val paragraphs = text.split("\n")
        for (p in paragraphs) {
            val lines = wrapText(p, paint, maxWidth)
            for (line in lines) {
                canvas.drawText(line, startX, y, paint)
                y += paint.textSize + lineSpacing
            }
        }
        return y - startY
    }

    private fun drawParagraph(canvas: Canvas, text: String, startX: Float, startY: Float, maxWidth: Float, paint: Paint = textPaint, lineSpacing: Float = 4f): Float {
        var y = startY
        val lines = wrapText(text, paint, maxWidth)
        for (line in lines) {
            canvas.drawText(line, startX, y, paint)
            y += paint.textSize + lineSpacing
        }
        return y - startY
    }

    private fun drawWrappedText(canvas: Canvas, text: String, paint: Paint, x: Float, yStart: Float, maxWidth: Float, lineSpacing: Float = 4f) {
        var y = yStart
        val lines = wrapText(text, paint, maxWidth)
        for (line in lines) {
            canvas.drawText(line, x, y, paint)
            y += paint.textSize + lineSpacing
        }
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        if (text.isBlank()) return listOf("")
        val words = text.replace('\n', ' ').split(' ').filter { it.isNotEmpty() }
        val lines = mutableListOf<String>()
        var curr = StringBuilder()
        for (word in words) {
            val test = if (curr.isEmpty()) word else curr.toString() + " " + word
            if (paint.measureText(test) <= maxWidth) {
                if (curr.isEmpty()) curr.append(word) else curr.append(" ").append(word)
            } else {
                lines.add(curr.toString())
                curr = StringBuilder(word)
            }
        }
        if (curr.isNotEmpty()) lines.add(curr.toString())
        return lines
    }
}