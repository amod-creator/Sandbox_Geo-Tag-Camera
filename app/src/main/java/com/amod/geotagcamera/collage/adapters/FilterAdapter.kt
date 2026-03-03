package com.amod.geotagcamera.collage.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.amod.geotagcamera.R
import com.amod.geotagcamera.collage.PhotoFilter

class FilterAdapter(
    private val onFilterSelected: (PhotoFilter) -> Unit
) : RecyclerView.Adapter<FilterAdapter.FilterViewHolder>() {
    
    private val filters = PhotoFilter.values().toList()
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FilterViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_filter_option, parent, false)
        return FilterViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: FilterViewHolder, position: Int) {
        val filter = filters[position]
        holder.bind(filter)
    }
    
    override fun getItemCount(): Int = filters.size
    
    inner class FilterViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val filterPreview: ImageView = itemView.findViewById(R.id.filterPreview)
        private val filterName: TextView = itemView.findViewById(R.id.filterName)
        
        fun bind(filter: PhotoFilter) {
            filterName.text = filter.displayName
            
            // Set filter preview icon
            filterPreview.setImageResource(
                when (filter) {
                    PhotoFilter.NONE -> R.drawable.ic_filter_none
                    PhotoFilter.VINTAGE -> R.drawable.ic_filter_vintage
                    PhotoFilter.SEPIA -> R.drawable.ic_filter_sepia
                    PhotoFilter.BLACK_WHITE -> R.drawable.ic_filter_bw
                    PhotoFilter.BLUR -> R.drawable.ic_filter_blur
                    PhotoFilter.SHARPEN -> R.drawable.ic_filter_sharpen
                }
            )
            
            itemView.setOnClickListener {
                onFilterSelected(filter)
            }
        }
    }
}
