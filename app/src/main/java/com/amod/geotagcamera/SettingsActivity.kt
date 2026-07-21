package com.amod.geotagcamera

import android.content.Context
import android.graphics.BitmapFactory
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.amod.geotagcamera.databinding.ActivitySettingsBinding
import android.widget.SeekBar
import android.widget.FrameLayout
import java.io.File
import java.io.FileOutputStream

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private var ptToPx: Float = 1.0f
    
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { handleSelectedSignature(it) }
    }

    private val signatureEditorLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            updateSignaturePreview()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set up toolbar back button
        binding.settingsToolbar.setNavigationOnClickListener {
            finish()
        }

        // Shared preferences setup for Visit Mode
        val sharedPrefs = getSharedPreferences("com.amod.geotagcamera.PREFERENCES", Context.MODE_PRIVATE)
        val currentVisitMode = sharedPrefs.getBoolean("visit_mode", false)
        val currentSignatureRequired = sharedPrefs.getBoolean("signature_required", false)

        binding.visitModeSwitch.isChecked = currentVisitMode
        binding.signatureRequiredSwitch.isChecked = currentSignatureRequired

        // Initialize visibility
        updateSwitchesVisibility()

        binding.visitModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            sharedPrefs.edit().putBoolean("visit_mode", isChecked).apply()
            updateSwitchesVisibility()
        }

        binding.signatureRequiredSwitch.setOnCheckedChangeListener { _, isChecked ->
            sharedPrefs.edit().putBoolean("signature_required", isChecked).apply()
            updateSwitchesVisibility()
        }

        // Initialize dynamic signature sizing, offsets, and rotation seekbars
        val widthVal = sharedPrefs.getFloat("signature_pdf_width", 120f).toInt()
        val heightVal = sharedPrefs.getFloat("signature_pdf_height", 40f).toInt()
        val offsetXVal = sharedPrefs.getFloat("signature_pdf_offset_x", 0f).toInt()
        val offsetYVal = sharedPrefs.getFloat("signature_pdf_offset_y", 0f).toInt()
        val rotationVal = sharedPrefs.getFloat("signature_pdf_rotation", 0f).toInt()

        binding.sigWidthSeekBar.progress = widthVal
        binding.sigWidthLabel.text = "Display Width: ${widthVal}pt"

        binding.sigHeightSeekBar.progress = heightVal
        binding.sigHeightLabel.text = "Display Height: ${heightVal}pt"

        binding.sigOffsetXSeekBar.progress = offsetXVal + 100
        binding.sigOffsetXLabel.text = "Horizontal Shift: ${offsetXVal}pt"

        binding.sigOffsetYSeekBar.progress = offsetYVal + 60
        binding.sigOffsetYLabel.text = "Vertical Shift: ${offsetYVal}pt"

        binding.sigRotationSeekBar.progress = rotationVal + 45
        binding.sigRotationLabel.text = "Curve/Slant Rotation: ${rotationVal}°"

        binding.sigWidthSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val progressVal = maxOf(30, progress) // minimum width 30pt
                binding.sigWidthLabel.text = "Display Width: ${progressVal}pt"
                sharedPrefs.edit().putFloat("signature_pdf_width", progressVal.toFloat()).apply()
                triggerBoundsAndTransformsUpdate()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.sigHeightSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val progressVal = maxOf(10, progress) // minimum height 10pt
                binding.sigHeightLabel.text = "Display Height: ${progressVal}pt"
                sharedPrefs.edit().putFloat("signature_pdf_height", progressVal.toFloat()).apply()
                triggerBoundsAndTransformsUpdate()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.sigOffsetXSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val offset = progress - 100
                binding.sigOffsetXLabel.text = "Horizontal Shift: ${offset}pt"
                sharedPrefs.edit().putFloat("signature_pdf_offset_x", offset.toFloat()).apply()
                triggerBoundsAndTransformsUpdate()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.sigOffsetYSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val offset = progress - 60
                binding.sigOffsetYLabel.text = "Vertical Shift: ${offset}pt"
                sharedPrefs.edit().putFloat("signature_pdf_offset_y", offset.toFloat()).apply()
                triggerBoundsAndTransformsUpdate()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.sigRotationSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val angle = progress - 45
                binding.sigRotationLabel.text = "Curve/Slant Rotation: ${angle}°"
                sharedPrefs.edit().putFloat("signature_pdf_rotation", angle.toFloat()).apply()
                triggerBoundsAndTransformsUpdate()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Setup upload, edit, and clear buttons
        binding.btnUploadSignature.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        binding.btnEditSignature.setOnClickListener {
            val targetFile = File(filesDir, "signature.png")
            if (targetFile.exists()) {
                try {
                    val tempFile = File(cacheDir, "temp_sig_input.jpg")
                    targetFile.inputStream().use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    val intent = Intent(this, SignatureEditorActivity::class.java).apply {
                        putExtra("TEMP_SIG_PATH", tempFile.absolutePath)
                    }
                    signatureEditorLauncher.launch(intent)
                } catch (e: Exception) {
                    Log.e("SettingsActivity", "Error setting up edit signature: ${e.message}", e)
                    Toast.makeText(this, "Could not open signature editor", Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.btnClearSignature.setOnClickListener {
            clearSignature()
        }

        // Setup post layout listener to measure correct content width and calculate ptToPx factor
        binding.signaturePreviewContentArea.post {
            val width = binding.signaturePreviewContentArea.width
            if (width > 0) {
                ptToPx = width / 515f
                
                // Adjust height of the float area dynamically to match 50pt PDF spacing
                val floatAreaParams = binding.sigPreviewFloatArea.layoutParams
                floatAreaParams.height = (50f * ptToPx).toInt()
                binding.sigPreviewFloatArea.layoutParams = floatAreaParams
                
                // Trigger initial bounds and transforms update
                triggerBoundsAndTransformsUpdate()
            }
        }

        // Display current signature if any
        updateSignaturePreview()
    }

    private fun handleSelectedSignature(uri: Uri) {
        try {
            contentResolver.openInputStream(uri).use { inputStream ->
                if (inputStream != null) {
                    val tempFile = File(cacheDir, "temp_sig_input.jpg")
                    FileOutputStream(tempFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                    val intent = Intent(this, SignatureEditorActivity::class.java).apply {
                        putExtra("TEMP_SIG_PATH", tempFile.absolutePath)
                    }
                    signatureEditorLauncher.launch(intent)
                } else {
                    Toast.makeText(this, "Could not read selected image", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e("SettingsActivity", "Error saving signature: ${e.message}", e)
            Toast.makeText(this, "Failed to upload signature", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateSignaturePreview() {
        val targetFile = File(filesDir, "signature.png")
        if (targetFile.exists()) {
            try {
                val bitmap = BitmapFactory.decodeFile(targetFile.absolutePath)
                if (bitmap != null) {
                    binding.signaturePreviewImage.setImageBitmap(bitmap)
                    binding.signaturePreviewImage.visibility = View.VISIBLE
                    binding.signaturePreviewBorderCard.visibility = View.VISIBLE
                    binding.noSignatureText.visibility = View.GONE
                    binding.btnEditSignature.visibility = View.VISIBLE
                    binding.btnClearSignature.visibility = View.VISIBLE
                    binding.btnEditSpace.visibility = View.VISIBLE
                    binding.btnClearSpace.visibility = View.VISIBLE
                    binding.btnUploadSignature.text = "Replace"
                    binding.signatureSizeContainer.visibility = View.VISIBLE
                    
                    val sharedPrefs = getSharedPreferences("com.amod.geotagcamera.PREFERENCES", Context.MODE_PRIVATE)
                    val widthVal = sharedPrefs.getFloat("signature_pdf_width", 120f).toInt()
                    val heightVal = sharedPrefs.getFloat("signature_pdf_height", 40f).toInt()
                    val offsetXVal = sharedPrefs.getFloat("signature_pdf_offset_x", 0f).toInt()
                    val offsetYVal = sharedPrefs.getFloat("signature_pdf_offset_y", 0f).toInt()
                    val rotationVal = sharedPrefs.getFloat("signature_pdf_rotation", 0f).toInt()
                    
                    updateLivePreviewBounds(widthVal, heightVal, offsetXVal, offsetYVal, rotationVal)
                    return
                }
            } catch (e: Exception) {
                Log.e("SettingsActivity", "Error decoding signature file: ${e.message}", e)
            }
        }
        binding.signaturePreviewImage.visibility = View.GONE
        binding.signaturePreviewBorderCard.visibility = View.GONE
        binding.noSignatureText.visibility = View.VISIBLE
        binding.btnEditSignature.visibility = View.GONE
        binding.btnClearSignature.visibility = View.GONE
        binding.btnEditSpace.visibility = View.GONE
        binding.btnClearSpace.visibility = View.GONE
        binding.btnUploadSignature.text = getString(R.string.upload_signature)
        binding.signatureSizeContainer.visibility = View.GONE
    }

    private fun clearSignature() {
        try {
            val targetFile = File(filesDir, "signature.png")
            if (targetFile.exists()) {
                targetFile.delete()
            }
            updateSignaturePreview()
            Toast.makeText(this, "Signature cleared successfully", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("SettingsActivity", "Error clearing signature: ${e.message}", e)
            Toast.makeText(this, "Failed to clear signature", Toast.LENGTH_SHORT).show()
        }
    }

    private fun triggerBoundsAndTransformsUpdate() {
        val width = maxOf(30, binding.sigWidthSeekBar.progress)
        val height = maxOf(10, binding.sigHeightSeekBar.progress)
        val offsetX = binding.sigOffsetXSeekBar.progress - 100
        val offsetY = binding.sigOffsetYSeekBar.progress - 60
        val angle = binding.sigRotationSeekBar.progress - 45
        
        updateLivePreviewBounds(width, height, offsetX, offsetY, angle)
    }

    private fun updateLivePreviewBounds(widthVal: Int, heightVal: Int, offsetX: Int = 0, offsetY: Int = 0, angle: Int = 0) {
        val params = binding.signaturePreviewBorderCard.layoutParams as FrameLayout.LayoutParams
        params.width = (widthVal * ptToPx).toInt()
        params.height = (heightVal * ptToPx).toInt()
        params.bottomMargin = (2f * ptToPx).toInt() // Spacing above the line matches PDF scaling!
        binding.signaturePreviewBorderCard.layoutParams = params
        
        binding.signaturePreviewBorderCard.translationX = offsetX * ptToPx
        binding.signaturePreviewBorderCard.translationY = offsetY * ptToPx
        binding.signaturePreviewBorderCard.rotation = angle.toFloat()
        
        binding.signaturePreviewBorderCard.requestLayout()
    }

    private fun updateSwitchesVisibility() {
        val sharedPrefs = getSharedPreferences("com.amod.geotagcamera.PREFERENCES", Context.MODE_PRIVATE)
        val visitModeEnabled = binding.visitModeSwitch.isChecked
        val signatureRequiredEnabled = binding.signatureRequiredSwitch.isChecked
        
        // "Signature Required" divider and switch are only shown when Visit Mode is enabled
        if (visitModeEnabled) {
            binding.visitModeDivider.visibility = View.VISIBLE
            binding.signatureRequiredSwitch.visibility = View.VISIBLE
        } else {
            binding.visitModeDivider.visibility = View.GONE
            binding.signatureRequiredSwitch.visibility = View.GONE
        }
        
        // Card 2: Signature Settings is only shown when Visit Mode is enabled AND Signature Required is enabled
        if (visitModeEnabled && signatureRequiredEnabled) {
            binding.signatureSettingsCard.visibility = View.VISIBLE
        } else {
            binding.signatureSettingsCard.visibility = View.GONE
        }
    }
}
