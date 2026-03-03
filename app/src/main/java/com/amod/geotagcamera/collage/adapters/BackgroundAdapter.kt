package com.amod.geotagcamera.collage.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.amod.geotagcamera.R
import com.amod.geotagcamera.collage.BackgroundType

class BackgroundAdapter(
    private val onBackgroundSelected: (BackgroundType) -> Unit
) : RecyclerView.Adapter<BackgroundAdapter.BackgroundViewHolder>() {
    
    private val backgrounds = listOf(
        BackgroundOption("White", BackgroundType.ColorBackground(Color.WHITE)),
        BackgroundOption("Black", BackgroundType.ColorBackground(Color.BLACK)),
        BackgroundOption("Gray", BackgroundType.ColorBackground(Color.GRAY)),
        BackgroundOption("Red", BackgroundType.ColorBackground(Color.RED)),
        BackgroundOption("Blue", BackgroundType.ColorBackground(Color.BLUE)),
        BackgroundOption("Green", BackgroundType.ColorBackground(Color.GREEN)),
        BackgroundOption("Yellow", BackgroundType.ColorBackground(Color.YELLOW)),
        BackgroundOption("Purple", BackgroundType.ColorBackground(Color.MAGENTA)),
        BackgroundOption("Gradient 1", BackgroundType.GradientBackground(Color.BLUE, Color.MAGENTA, com.amod.geotagcamera.collage.GradientDirection.VERTICAL)),
        BackgroundOption("Gradient 2", BackgroundType.GradientBackground(Color.RED, Color.YELLOW, com.amod.geotagcamera.collage.GradientDirection.HORIZONTAL)),
        BackgroundOption("Gradient 3", BackgroundType.GradientBackground(Color.GREEN, Color.BLUE, com.amod.geotagcamera.collage.GradientDirection.DIAGONAL_TOP_LEFT)),
        BackgroundOption("Pattern 1", BackgroundType.PatternBackground(R.drawable.pattern_dots)),
        BackgroundOption("Pattern 2", BackgroundType.PatternBackground(R.drawable.pattern_lines)),
        BackgroundOption("Pattern 3", BackgroundType.PatternBackground(R.drawable.pattern_grid))
    )
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BackgroundViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_background_option, parent, false)
        return BackgroundViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: BackgroundViewHolder, position: Int) {
        val background = backgrounds[position]
        holder.bind(background)
    }
    
    override fun getItemCount(): Int = backgrounds.size
    
    inner class BackgroundViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val backgroundPreview: ImageView = itemView.findViewById(R.id.backgroundPreview)
        
        fun bind(background: BackgroundOption) {
            // Set background preview based on type
            when (background.type) {
                is BackgroundType.ColorBackground -> {
                    backgroundPreview.setBackgroundColor(background.type.color)
                }
                is BackgroundType.GradientBackground -> {
                    // Set gradient preview
                    backgroundPreview.setBackgroundColor(background.type.startColor)
                }
                is BackgroundType.PatternBackground -> {
                    backgroundPreview.setImageResource(background.type.patternRes)
                }
                is BackgroundType.ImageBackground -> {
                    backgroundPreview.setImageBitmap(background.type.bitmap)
                }
            }
            
            itemView.setOnClickListener {
                onBackgroundSelected(background.type)
            }
        }
    }
    
    data class BackgroundOption(
        val name: String,
        val type: BackgroundType
    )
}
