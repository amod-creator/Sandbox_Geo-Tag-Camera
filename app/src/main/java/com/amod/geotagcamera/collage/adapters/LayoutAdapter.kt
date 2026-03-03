package com.amod.geotagcamera.collage.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.amod.geotagcamera.R
import com.amod.geotagcamera.collage.CollageTemplate

class LayoutAdapter(
    private val onTemplateSelected: (CollageTemplate) -> Unit
) : RecyclerView.Adapter<LayoutAdapter.LayoutViewHolder>() {
    
    private var templates = listOf<CollageTemplate>()
    
    fun updateTemplates(newTemplates: List<CollageTemplate>) {
        templates = newTemplates
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LayoutViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_collage_template, parent, false)
        return LayoutViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: LayoutViewHolder, position: Int) {
        val template = templates[position]
        holder.bind(template)
    }
    
    override fun getItemCount(): Int = templates.size
    
    inner class LayoutViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val templateImage: ImageView = itemView.findViewById(R.id.templateImage)
        private val templateName: TextView = itemView.findViewById(R.id.templateName)
        
        fun bind(template: CollageTemplate) {
            templateImage.setImageResource(template.thumbnailRes)
            templateName.text = template.name
            
            itemView.setOnClickListener {
                onTemplateSelected(template)
            }
        }
    }
}
