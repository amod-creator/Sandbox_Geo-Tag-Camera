package com.amod.geotagcamera.collage.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.amod.geotagcamera.R
import com.amod.geotagcamera.collage.BorderStyle

class BorderAdapter(
    private val onBorderSelected: (BorderStyle, Int) -> Unit
) : RecyclerView.Adapter<BorderAdapter.BorderViewHolder>() {
    
    private val borderOptions = listOf(
        BorderOption(BorderStyle.NONE, "None", Color.TRANSPARENT),
        BorderOption(BorderStyle.THIN, "Thin", Color.WHITE),
        BorderOption(BorderStyle.MEDIUM, "Medium", Color.WHITE),
        BorderOption(BorderStyle.THICK, "Thick", Color.WHITE),
        BorderOption(BorderStyle.DOTTED, "Dotted", Color.WHITE),
        BorderOption(BorderStyle.DASHED, "Dashed", Color.WHITE)
    )
    
    private val borderColors = listOf(
        Color.WHITE, Color.BLACK, Color.RED, Color.BLUE, 
        Color.GREEN, Color.YELLOW, Color.MAGENTA, Color.CYAN
    )
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BorderViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_border_option, parent, false)
        return BorderViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: BorderViewHolder, position: Int) {
        val option = borderOptions[position]
        holder.bind(option)
    }
    
    override fun getItemCount(): Int = borderOptions.size
    
    inner class BorderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val borderPreview: ImageView = itemView.findViewById(R.id.borderPreview)
        private val borderName: TextView = itemView.findViewById(R.id.borderName)
        
        fun bind(option: BorderOption) {
            borderName.text = option.name
            // Set border preview based on style
            borderPreview.setImageResource(
                when (option.style) {
                    BorderStyle.NONE -> R.drawable.ic_border_none
                    BorderStyle.THIN -> R.drawable.ic_border_thin
                    BorderStyle.MEDIUM -> R.drawable.ic_border_medium
                    BorderStyle.THICK -> R.drawable.ic_border_thick
                    BorderStyle.DOTTED -> R.drawable.ic_border_dotted
                    BorderStyle.DASHED -> R.drawable.ic_border_dashed
                }
            )
            
            itemView.setOnClickListener {
                onBorderSelected(option.style, option.color)
            }
        }
    }
    
    data class BorderOption(
        val style: BorderStyle,
        val name: String,
        val color: Int
    )
}
