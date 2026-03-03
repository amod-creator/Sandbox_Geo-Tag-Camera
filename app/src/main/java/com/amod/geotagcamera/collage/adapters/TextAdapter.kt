package com.amod.geotagcamera.collage.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.RecyclerView
import com.amod.geotagcamera.R
import com.amod.geotagcamera.collage.CollageElement
import com.amod.geotagcamera.collage.TextAlignment
import com.amod.geotagcamera.collage.TextStyle

class TextAdapter(
    private val onTextElementCreated: (CollageElement.TextElement) -> Unit
) : RecyclerView.Adapter<TextAdapter.TextViewHolder>() {
    
    private val textStyles = listOf(
        TextStyle.NORMAL,
        TextStyle.BOLD,
        TextStyle.ITALIC,
        TextStyle.BOLD_ITALIC
    )
    
    private val textColors = listOf(
        Color.WHITE, Color.BLACK, Color.RED, Color.BLUE,
        Color.GREEN, Color.YELLOW, Color.MAGENTA, Color.CYAN
    )
    
    private val fontSizes = listOf(24f, 32f, 48f, 64f, 80f, 96f)
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TextViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_text_editor, parent, false)
        return TextViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: TextViewHolder, position: Int) {
        holder.bind()
    }
    
    override fun getItemCount(): Int = 1 // Single text editor item
    
    inner class TextViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textInput: EditText = itemView.findViewById(R.id.textInput)
        private val styleSpinner: Spinner = itemView.findViewById(R.id.styleSpinner)
        private val colorSpinner: Spinner = itemView.findViewById(R.id.colorSpinner)
        private val sizeSpinner: Spinner = itemView.findViewById(R.id.sizeSpinner)
        private val alignmentSpinner: Spinner = itemView.findViewById(R.id.alignmentSpinner)
        private val addTextButton: Button = itemView.findViewById(R.id.addTextButton)
        
        fun bind() {
            setupSpinners()
            setupListeners()
        }
        
        private fun setupSpinners() {
            // Style spinner
            val styleAdapter = ArrayAdapter(itemView.context, android.R.layout.simple_spinner_item, textStyles)
            styleAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            styleSpinner.adapter = styleAdapter
            
            // Color spinner
            val colorNames = textColors.map { "Color" }
            val colorAdapter = ArrayAdapter(itemView.context, android.R.layout.simple_spinner_item, colorNames)
            colorAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            colorSpinner.adapter = colorAdapter
            
            // Size spinner
            val sizeNames = fontSizes.map { "${it.toInt()}px" }
            val sizeAdapter = ArrayAdapter(itemView.context, android.R.layout.simple_spinner_item, sizeNames)
            sizeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            sizeSpinner.adapter = sizeAdapter
            
            // Alignment spinner
            val alignmentAdapter = ArrayAdapter(itemView.context, android.R.layout.simple_spinner_item, TextAlignment.values())
            alignmentAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            alignmentSpinner.adapter = alignmentAdapter
        }
        
        private fun setupListeners() {
            addTextButton.setOnClickListener {
                val text = textInput.text.toString()
                if (text.isNotEmpty()) {
                    val textElement = CollageElement.TextElement(
                        id = "text_${System.currentTimeMillis()}",
                        rect = android.graphics.RectF(100f, 100f, 400f, 200f),
                        text = text,
                        textSize = fontSizes[sizeSpinner.selectedItemPosition],
                        textColor = textColors[colorSpinner.selectedItemPosition],
                        textStyle = textStyles[styleSpinner.selectedItemPosition],
                        alignment = TextAlignment.values()[alignmentSpinner.selectedItemPosition]
                    )
                    onTextElementCreated(textElement)
                    textInput.text.clear()
                }
            }
        }
    }
}
