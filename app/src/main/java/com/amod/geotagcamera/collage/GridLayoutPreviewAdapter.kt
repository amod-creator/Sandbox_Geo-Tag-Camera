package com.amod.geotagcamera.collage

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView
import com.amod.geotagcamera.R

/**
 * Grid Layout Preview Adapter - Dynamically renders grid previews based on templates
 * Shows visual grid layouts that update based on selected photo count
 */
class GridLayoutPreviewAdapter(
    photoCount: Int,
    private val onLayoutSelected: (CollageTemplate, Int) -> Unit
) : RecyclerView.Adapter<GridLayoutPreviewAdapter.GridPreviewViewHolder>() {

    private val templates = CollageTemplates.getTemplatesForPhotoCount(photoCount)
    private var selectedPosition = 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GridPreviewViewHolder {
        val container = FrameLayout(parent.context).apply {
            layoutParams = RecyclerView.LayoutParams(
                100,
                100
            ).apply {
                marginStart = 12
                marginEnd = 12
                topMargin = 12
                bottomMargin = 12
            }
            setBackgroundColor(Color.BLACK)
        }
        return GridPreviewViewHolder(container)
    }

    override fun onBindViewHolder(holder: GridPreviewViewHolder, position: Int) {
        val template = templates[position]
        holder.bind(template, position == selectedPosition)
    }

    override fun getItemCount() = templates.size

    inner class GridPreviewViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val gridPreviewView: GridPreviewView

        init {
            gridPreviewView = GridPreviewView(itemView.context)
            gridPreviewView.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            (itemView as FrameLayout).addView(gridPreviewView)
        }

        fun bind(template: CollageTemplate, isSelected: Boolean) {
            // Draw grid preview
            gridPreviewView.drawTemplate(template, isSelected)

            // Highlight selected layout
            itemView.alpha = if (isSelected) 1.0f else 0.6f

            itemView.setOnClickListener {
                val oldPosition = selectedPosition
                selectedPosition = adapterPosition
                if (selectedPosition != RecyclerView.NO_POSITION) {
                    notifyItemChanged(oldPosition)
                    notifyItemChanged(selectedPosition)
                    onLayoutSelected(template, selectedPosition)
                }
            }
        }
    }

    /**
     * Custom View that draws grid layout previews
     */
    private inner class GridPreviewView(context: android.content.Context) : View(context) {
        private var template: CollageTemplate? = null
        private var isSelected = false
        private val gridBounds = RectF()

        private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
            color = Color.WHITE
        }

        private val selectedGridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3.5f
            color = Color.parseColor("#2196F3")
        }

        fun drawTemplate(template: CollageTemplate, isSelected: Boolean) {
            this.template = template
            this.isSelected = isSelected
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            template?.let { tmpl ->
                val padding = 6f
                val availableWidth = width - (padding * 2)
                val availableHeight = height - (padding * 2)

                // Draw each slot as a rectangle in the grid
                tmpl.slots.forEach { slot ->
                    gridBounds.set(
                        padding + slot.rect.left * availableWidth,
                        padding + slot.rect.top * availableHeight,
                        padding + slot.rect.right * availableWidth,
                        padding + slot.rect.bottom * availableHeight
                    )

                    // Draw border (white lines for grid)
                    val paint = if (isSelected) selectedGridPaint else gridPaint
                    canvas.drawRect(gridBounds, paint)
                }
            }
        }
    }
}

