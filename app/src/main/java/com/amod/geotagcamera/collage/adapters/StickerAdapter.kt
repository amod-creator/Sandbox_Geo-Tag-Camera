package com.amod.geotagcamera.collage.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.amod.geotagcamera.R

class StickerAdapter(
    private val onStickerSelected: (Int) -> Unit
) : RecyclerView.Adapter<StickerAdapter.StickerViewHolder>() {
    
    private val stickers = listOf(
        R.drawable.sticker_heart,
        R.drawable.sticker_star,
        R.drawable.sticker_crown,
        R.drawable.sticker_flower,
        R.drawable.sticker_butterfly,
        R.drawable.sticker_balloon,
        R.drawable.sticker_gift,
        R.drawable.sticker_cake,
        R.drawable.sticker_music,
        R.drawable.sticker_sport,
        R.drawable.sticker_travel,
        R.drawable.sticker_food,
        R.drawable.sticker_animal,
        R.drawable.sticker_emoji
    )
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StickerViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_sticker_option, parent, false)
        return StickerViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: StickerViewHolder, position: Int) {
        val stickerRes = stickers[position]
        holder.bind(stickerRes)
    }
    
    override fun getItemCount(): Int = stickers.size
    
    inner class StickerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val stickerImage: ImageView = itemView.findViewById(R.id.stickerImage)
        
        fun bind(stickerRes: Int) {
            stickerImage.setImageResource(stickerRes)
            
            itemView.setOnClickListener {
                onStickerSelected(stickerRes)
            }
        }
    }
}
