package com.amod.geotagcamera.utils

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.Gravity
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.amod.geotagcamera.model.CustomNoteConfig
import com.amod.geotagcamera.model.NoteHighlightMode

object InstagramTextStyler {

    /**
     * Applies styling to a TextView or EditText to preview Instagram-style text.
     */
    fun applyStyleToView(textView: TextView, config: CustomNoteConfig) {
        val context = textView.context

        // 1. Typeface
        val tf = config.font.getTypeface(context)
        textView.typeface = tf

        // 2. Text Size
        textView.textSize = config.textSizeSp

        // 3. Alignment / Gravity
        textView.gravity = when (config.alignment) {
            0 -> Gravity.START or Gravity.CENTER_VERTICAL
            2 -> Gravity.END or Gravity.CENTER_VERTICAL
            else -> Gravity.CENTER
        }

        // 4. Color & Background Highlight Mode
        val textColor = config.textColor
        val bgColor = config.highlightColor

        textView.setTextColor(textColor)

        when (config.highlight) {
            NoteHighlightMode.NONE -> {
                textView.background = null
                // Subtle text shadow for legibility over any photo background
                textView.setShadowLayer(6f, 0f, 2f, Color.parseColor("#99000000"))
            }

            NoteHighlightMode.SOLID_BOX -> {
                val shape = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dpToPx(context, 12f)
                    setColor(bgColor)
                }
                textView.background = shape
                textView.setShadowLayer(0f, 0f, 0f, 0)
            }

            NoteHighlightMode.FROSTED -> {
                val frostedBg = ColorUtils.setAlphaComponent(bgColor, 180)
                val shape = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dpToPx(context, 14f)
                    setColor(frostedBg)
                    setStroke(dpToPx(context, 1.2f).toInt(), Color.parseColor("#66FFFFFF"))
                }
                textView.background = shape
                textView.setShadowLayer(0f, 0f, 0f, 0)
            }

            NoteHighlightMode.NEON_GLOW -> {
                textView.background = null
                val glowColor = if (bgColor != Color.BLACK && bgColor != Color.WHITE) bgColor else textColor
                textView.setShadowLayer(16f, 0f, 0f, glowColor)
            }

            NoteHighlightMode.INVERTED -> {
                val boxBgColor = Color.parseColor("#E6121212")
                val shape = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dpToPx(context, 10f)
                    setColor(boxBgColor)
                    setStroke(dpToPx(context, 2f).toInt(), bgColor)
                }
                textView.background = shape
                textView.setShadowLayer(0f, 0f, 0f, 0)
            }
        }
    }

    /**
     * Draws the Instagram-style text sticker onto an arbitrary Canvas (e.g. for captured image overlay or PDF).
     */
    fun drawStickerOnCanvas(
        canvas: Canvas,
        config: CustomNoteConfig,
        context: Context,
        canvasWidth: Int,
        canvasHeight: Int,
        bottomPaddingAboveCard: Float,
        isCardAtTop: Boolean = false
    ) {
        val text = config.text.trim()
        if (text.isEmpty()) return

        val tf = config.font.getTypeface(context)
        val densityScale = canvasWidth / 1080f // normalize to standard 1080p base width
        val baseTextSize = config.textSizeSp * densityScale * 1.8f

        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = tf
            textSize = baseTextSize
        }

        val textColor = config.textColor
        val bgColor = config.highlightColor

        // Alignment
        val alignment = when (config.alignment) {
            0 -> Layout.Alignment.ALIGN_NORMAL
            2 -> Layout.Alignment.ALIGN_OPPOSITE
            else -> Layout.Alignment.ALIGN_CENTER
        }

        val maxTextWidth = (canvasWidth * 0.85f).toInt()
        val staticLayout = StaticLayout.Builder.obtain(text, 0, text.length, textPaint, maxTextWidth)
            .setAlignment(alignment)
            .setLineSpacing(4f * densityScale, 1f)
            .setIncludePad(true)
            .build()

        val textWidth = staticLayout.width.toFloat()
        val textHeight = staticLayout.height.toFloat()

        val padX = 24f * densityScale
        val padY = 14f * densityScale
        val boxWidth = textWidth + (padX * 2)
        val boxHeight = textHeight + (padY * 2)
        val cornerRadius = 16f * densityScale

        // Determine position based on config
        val centerX = if (config.isCustomPositioned) {
            config.normPosX * canvasWidth
        } else {
            canvasWidth / 2f
        }

        val centerY = if (config.isCustomPositioned) {
            config.normPosY * canvasHeight
        } else {
            when (config.position) {
                "top" -> 160f * densityScale
                "center" -> canvasHeight / 2f
                else -> {
                    if (isCardAtTop) {
                        (108f + 260f + 60f) * densityScale + (boxHeight / 2f)
                    } else {
                        bottomPaddingAboveCard - (boxHeight / 2f) - (20f * densityScale)
                    }
                }
            }
        }

        val boxLeft = centerX - (boxWidth / 2f)
        val boxTop = centerY - (boxHeight / 2f)
        val boxRight = boxLeft + boxWidth
        val boxBottom = boxTop + boxHeight
        val boxRect = RectF(boxLeft, boxTop, boxRight, boxBottom)

        val hasRotation = config.rotation != 0f
        val hasTransform = hasRotation || config.isMirrored
        if (hasTransform) {
            canvas.save()
            if (hasRotation) {
                canvas.rotate(config.rotation, centerX, centerY)
            }
            if (config.isMirrored) {
                canvas.scale(-1f, 1f, centerX, centerY)
            }
        }

        // Draw highlight background
        when (config.highlight) {
            NoteHighlightMode.NONE -> {
                textPaint.color = textColor
                textPaint.setShadowLayer(8f * densityScale, 0f, 3f * densityScale, Color.parseColor("#99000000"))
            }

            NoteHighlightMode.SOLID_BOX -> {
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = bgColor
                    style = Paint.Style.FILL
                    setShadowLayer(10f * densityScale, 0f, 4f * densityScale, Color.parseColor("#66000000"))
                }
                canvas.drawRoundRect(boxRect, cornerRadius, cornerRadius, bgPaint)

                textPaint.color = textColor
                textPaint.clearShadowLayer()
            }

            NoteHighlightMode.FROSTED -> {
                val frostedBg = ColorUtils.setAlphaComponent(bgColor, 180)
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = frostedBg
                    style = Paint.Style.FILL
                }
                canvas.drawRoundRect(boxRect, cornerRadius, cornerRadius, bgPaint)

                val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = Color.parseColor("#66FFFFFF")
                    style = Paint.Style.STROKE
                    strokeWidth = 2.5f * densityScale
                }
                canvas.drawRoundRect(boxRect, cornerRadius, cornerRadius, borderPaint)

                textPaint.color = textColor
                textPaint.clearShadowLayer()
            }

            NoteHighlightMode.NEON_GLOW -> {
                val glowColor = if (bgColor != Color.BLACK && bgColor != Color.WHITE) bgColor else textColor
                val glowPaint = TextPaint(textPaint).apply {
                    this.color = glowColor
                    style = Paint.Style.STROKE
                    strokeWidth = 6f * densityScale
                    setShadowLayer(20f * densityScale, 0f, 0f, glowColor)
                }
                val glowLayout = StaticLayout.Builder.obtain(text, 0, text.length, glowPaint, maxTextWidth)
                    .setAlignment(alignment)
                    .setLineSpacing(4f * densityScale, 1f)
                    .build()

                canvas.save()
                canvas.translate(boxLeft + padX, boxTop + padY)
                glowLayout.draw(canvas)
                canvas.restore()

                textPaint.color = textColor
                textPaint.setShadowLayer(10f * densityScale, 0f, 0f, glowColor)
            }

            NoteHighlightMode.INVERTED -> {
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = Color.parseColor("#E6121212")
                    style = Paint.Style.FILL
                }
                canvas.drawRoundRect(boxRect, cornerRadius, cornerRadius, bgPaint)

                val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = bgColor
                    style = Paint.Style.STROKE
                    strokeWidth = 3f * densityScale
                }
                canvas.drawRoundRect(boxRect, cornerRadius, cornerRadius, borderPaint)

                textPaint.color = textColor
                textPaint.clearShadowLayer()
            }
        }

        // Draw Text inside box
        canvas.save()
        canvas.translate(boxLeft + padX, boxTop + padY)
        staticLayout.draw(canvas)
        canvas.restore()

        if (hasTransform) {
            canvas.restore()
        }
    }

    private fun dpToPx(context: Context, dp: Float): Float {
        return dp * context.resources.displayMetrics.density
    }
}
