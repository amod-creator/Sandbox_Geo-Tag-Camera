package com.amod.geotagcamera.collage

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.amod.geotagcamera.R

/**
 * Enhanced Border Intensity Adapter - Shows various border thickness/intensity levels
 * Focuses on border thickness variations with a single consistent color (White)
 */
class EnhancedBorderAdapter(
    private val onBorderSelected: (BorderStyleOption) -> Unit
) : RecyclerView.Adapter<EnhancedBorderAdapter.BorderViewHolder>() {

    private val borderStyles = listOf(
        // No Border
        BorderStyleOption(
            id = "none",
            name = "None",
            width = 0f,
            color = Color.WHITE,
            styleType = BorderStyleType.NONE
        ),

        // Solid White Borders - Different Intensities/Thickness
        BorderStyleOption(
            id = "intensity_1",
            name = "Thin",
            width = 2f,
            color = Color.WHITE,
            styleType = BorderStyleType.SOLID
        ),
        BorderStyleOption(
            id = "intensity_2",
            name = "Light",
            width = 4f,
            color = Color.WHITE,
            styleType = BorderStyleType.SOLID
        ),
        BorderStyleOption(
            id = "intensity_3",
            name = "Medium",
            width = 8f,
            color = Color.WHITE,
            styleType = BorderStyleType.SOLID
        ),
        BorderStyleOption(
            id = "intensity_4",
            name = "Bold",
            width = 12f,
            color = Color.WHITE,
            styleType = BorderStyleType.SOLID
        ),
        BorderStyleOption(
            id = "intensity_5",
            name = "Thick",
            width = 16f,
            color = Color.WHITE,
            styleType = BorderStyleType.SOLID
        ),
        BorderStyleOption(
            id = "intensity_6",
            name = "Extra Thick",
            width = 24f,
            color = Color.WHITE,
            styleType = BorderStyleType.SOLID
        ),
        BorderStyleOption(
            id = "intensity_7",
            name = "Heavy",
            width = 32f,
            color = Color.WHITE,
            styleType = BorderStyleType.SOLID
        )
    )

    private var selectedPosition = 2 // Default to Medium (8f width)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BorderViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_professional_border, parent, false)
        return BorderViewHolder(view)
    }

    override fun onBindViewHolder(holder: BorderViewHolder, position: Int) {
        val borderStyle = borderStyles[position]
        holder.bind(borderStyle, position == selectedPosition)
    }

    override fun getItemCount() = borderStyles.size

    inner class BorderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val borderPreview: View = itemView.findViewById(R.id.borderPreview)
        private val borderName: TextView = itemView.findViewById(R.id.borderName)
        private val selectionIndicator: View = itemView.findViewById(R.id.selectionIndicator)

        fun bind(borderStyle: BorderStyleOption, isSelected: Boolean) {
            borderName.text = borderStyle.name

            // Draw border preview based on intensity/thickness
            when (borderStyle.styleType) {
                BorderStyleType.NONE -> {
                    borderPreview.setBackgroundColor(Color.LTGRAY)
                    borderPreview.alpha = 0.5f
                }
                BorderStyleType.SOLID -> {
                    // Show increasing border thickness as visual intensity
                    borderPreview.setBackgroundColor(borderStyle.color)

                    // Vary the opacity/appearance based on thickness to show intensity
                    val intensityFactor = (borderStyle.width / 32f).coerceIn(0.4f, 1.0f)
                    borderPreview.alpha = intensityFactor
                }
                else -> {
                    borderPreview.setBackgroundColor(Color.WHITE)
                }
            }

            // Highlight selected border intensity
            selectionIndicator.visibility = if (isSelected) View.VISIBLE else View.GONE
            itemView.alpha = if (isSelected) 1.0f else 0.7f

            itemView.setOnClickListener {
                val oldPosition = selectedPosition
                selectedPosition = adapterPosition
                if (selectedPosition != RecyclerView.NO_POSITION) {
                    notifyItemChanged(oldPosition)
                    notifyItemChanged(selectedPosition)
                    onBorderSelected(borderStyle)
                }
            }
        }
    }
}

/**
 * Border Style Option data class
 */
data class BorderStyleOption(
    val id: String,
    val name: String,
    val width: Float,
    val color: Int,
    val secondaryColor: Int = Color.TRANSPARENT,
    val styleType: BorderStyleType = BorderStyleType.SOLID
)

/**
 * Border style types
 */
enum class BorderStyleType {
    NONE,           // No border
    SOLID,          // Solid white border with varying intensity/thickness
    DASHED,         // Dashed line border
    DOTTED,         // Dotted line border
    DUAL_TONE       // Two-color border (alternating or striped)
}





