package com.amod.geotagcamera.collage

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.amod.geotagcamera.R

/**
 * Professional Layout Adapter - Shows grid templates with visual previews
 */
class ProfessionalLayoutAdapter(
    private val photoCount: Int,
    private val onLayoutSelected: (CollageTemplate, Int) -> Unit
) : RecyclerView.Adapter<ProfessionalLayoutAdapter.LayoutViewHolder>() {

    private val templates = CollageTemplates.getTemplatesForPhotoCount(photoCount)
    private var selectedPosition = 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LayoutViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_professional_layout, parent, false)
        return LayoutViewHolder(view)
    }

    override fun onBindViewHolder(holder: LayoutViewHolder, position: Int) {
        val template = templates[position]
        holder.bind(template, position == selectedPosition)
    }

    override fun getItemCount() = templates.size

    inner class LayoutViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val layoutIcon: ImageView = itemView.findViewById(R.id.layoutIcon)
        private val layoutName: TextView = itemView.findViewById(R.id.layoutName)
        private val selectionBorder: View = itemView.findViewById(R.id.selectionBorder)

        fun bind(template: CollageTemplate, isSelected: Boolean) {
            layoutIcon.setImageResource(template.thumbnailRes)
            layoutName.text = template.name

            // Highlight selected layout
            selectionBorder.visibility = if (isSelected) View.VISIBLE else View.GONE
            itemView.alpha = if (isSelected) 1.0f else 0.7f

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
}

/**
 * Professional Border Adapter - Shows border styles with previews
 */
class ProfessionalBorderAdapter(
    private val onBorderSelected: (Float, Int) -> Unit
) : RecyclerView.Adapter<ProfessionalBorderAdapter.BorderViewHolder>() {

    private val borders = listOf(
        BorderOption(0f, Color.TRANSPARENT, "None"),
        BorderOption(4f, Color.WHITE, "Thin White"),
        BorderOption(8f, Color.WHITE, "Medium White"),
        BorderOption(16f, Color.WHITE, "Thick White"),
        BorderOption(8f, Color.BLACK, "Black"),
        BorderOption(8f, Color.parseColor("#FFD700"), "Gold"),
        BorderOption(8f, Color.parseColor("#FF69B4"), "Pink"),
        BorderOption(8f, Color.parseColor("#00BCD4"), "Cyan")
    )

    private var selectedPosition = 1 // Default to thin white

    data class BorderOption(val width: Float, val color: Int, val name: String)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BorderViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_professional_border, parent, false)
        return BorderViewHolder(view)
    }

    override fun onBindViewHolder(holder: BorderViewHolder, position: Int) {
        val border = borders[position]
        holder.bind(border, position == selectedPosition)
    }

    override fun getItemCount() = borders.size

    inner class BorderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val borderPreview: View = itemView.findViewById(R.id.borderPreview)
        private val borderName: TextView = itemView.findViewById(R.id.borderName)
        private val selectionIndicator: View = itemView.findViewById(R.id.selectionIndicator)

        fun bind(border: BorderOption, isSelected: Boolean) {
            borderPreview.setBackgroundColor(border.color)
            borderName.text = border.name

            // Update preview size based on border width
            val layoutParams = borderPreview.layoutParams
            layoutParams.height = (40 + border.width * 2).toInt()
            borderPreview.layoutParams = layoutParams

            selectionIndicator.visibility = if (isSelected) View.VISIBLE else View.GONE
            itemView.alpha = if (isSelected) 1.0f else 0.7f

            itemView.setOnClickListener {
                val oldPosition = selectedPosition
                selectedPosition = adapterPosition
                if (selectedPosition != RecyclerView.NO_POSITION) {
                    notifyItemChanged(oldPosition)
                    notifyItemChanged(selectedPosition)
                    onBorderSelected(border.width, border.color)
                }
            }
        }
    }
}

/**
 * Professional Background Adapter - Shows solid colors and patterns
 */
class ProfessionalBackgroundAdapter(
    private val onBackgroundSelected: (Int, android.graphics.Bitmap?) -> Unit
) : RecyclerView.Adapter<ProfessionalBackgroundAdapter.BackgroundViewHolder>() {

    private val backgrounds = listOf(
        BackgroundOption(Color.WHITE, null, "White"),
        BackgroundOption(Color.BLACK, null, "Black"),
        BackgroundOption(Color.parseColor("#F5F5F5"), null, "Light Gray"),
        BackgroundOption(Color.parseColor("#E3F2FD"), null, "Light Blue"),
        BackgroundOption(Color.parseColor("#FFF3E0"), null, "Cream"),
        BackgroundOption(Color.parseColor("#FCE4EC"), null, "Light Pink"),
        BackgroundOption(Color.parseColor("#E8F5E9"), null, "Light Green"),
        BackgroundOption(Color.parseColor("#FFF9C4"), null, "Light Yellow")
    )

    private var selectedPosition = 0

    data class BackgroundOption(val color: Int, val pattern: android.graphics.Bitmap?, val name: String)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BackgroundViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_professional_background, parent, false)
        return BackgroundViewHolder(view)
    }

    override fun onBindViewHolder(holder: BackgroundViewHolder, position: Int) {
        val background = backgrounds[position]
        holder.bind(background, position == selectedPosition)
    }

    override fun getItemCount() = backgrounds.size

    inner class BackgroundViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val backgroundPreview: View = itemView.findViewById(R.id.backgroundPreview)
        private val backgroundName: TextView = itemView.findViewById(R.id.backgroundName)
        private val selectionIndicator: View = itemView.findViewById(R.id.selectionIndicator)

        fun bind(background: BackgroundOption, isSelected: Boolean) {
            backgroundPreview.setBackgroundColor(background.color)
            backgroundName.text = background.name

            selectionIndicator.visibility = if (isSelected) View.VISIBLE else View.GONE
            itemView.alpha = if (isSelected) 1.0f else 0.7f

            itemView.setOnClickListener {
                val oldPosition = selectedPosition
                selectedPosition = adapterPosition
                if (selectedPosition != RecyclerView.NO_POSITION) {
                    notifyItemChanged(oldPosition)
                    notifyItemChanged(selectedPosition)
                    onBackgroundSelected(background.color, background.pattern)
                }
            }
        }
    }
}

