package com.amod.geotagcamera.collage.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.amod.geotagcamera.R

class RatioAdapter(
    private val onRatioSelected: (Float) -> Unit
) : RecyclerView.Adapter<RatioAdapter.RatioViewHolder>() {
    
    private val ratios = listOf(
        RatioOption("1:1", 1.0f),
        RatioOption("4:3", 4.0f/3.0f),
        RatioOption("3:4", 3.0f/4.0f),
        RatioOption("16:9", 16.0f/9.0f),
        RatioOption("9:16", 9.0f/16.0f),
        RatioOption("3:2", 3.0f/2.0f),
        RatioOption("2:3", 2.0f/3.0f)
    )
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RatioViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ratio_option, parent, false)
        return RatioViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: RatioViewHolder, position: Int) {
        val ratio = ratios[position]
        holder.bind(ratio)
    }
    
    override fun getItemCount(): Int = ratios.size
    
    inner class RatioViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ratioText: TextView = itemView.findViewById(R.id.ratioText)
        
        fun bind(ratio: RatioOption) {
            ratioText.text = ratio.name
            
            itemView.setOnClickListener {
                onRatioSelected(ratio.value)
            }
        }
    }
    
    data class RatioOption(
        val name: String,
        val value: Float
    )
}
