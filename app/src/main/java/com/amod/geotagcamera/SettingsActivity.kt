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

        binding.visitModeSwitch.isChecked = currentVisitMode
        binding.visitModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            sharedPrefs.edit().putBoolean("visit_mode", isChecked).apply()
        }

        // Initialize dynamic signature sizing seekbars
        val widthVal = sharedPrefs.getFloat("signature_pdf_width", 120f).toInt()
        val heightVal = sharedPrefs.getFloat("signature_pdf_height", 40f).toInt()

        binding.sigWidthSeekBar.progress = widthVal
        binding.sigWidthLabel.text = "Display Width: ${widthVal}pt"

        binding.sigHeightSeekBar.progress = heightVal
        binding.sigHeightLabel.text = "Display Height: ${heightVal}pt"

        binding.sigWidthSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val progressVal = maxOf(30, progress) // minimum width 30pt
                binding.sigWidthLabel.text = "Display Width: ${progressVal}pt"
                sharedPrefs.edit().putFloat("signature_pdf_width", progressVal.toFloat()).apply()
                val curHeight = maxOf(10, binding.sigHeightSeekBar.progress)
                updateLivePreviewBounds(progressVal, curHeight)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.sigHeightSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val progressVal = maxOf(10, progress) // minimum height 10pt
                binding.sigHeightLabel.text = "Display Height: ${progressVal}pt"
                sharedPrefs.edit().putFloat("signature_pdf_height", progressVal.toFloat()).apply()
                val curWidth = maxOf(30, binding.sigWidthSeekBar.progress)
                updateLivePreviewBounds(curWidth, progressVal)
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
                    updateLivePreviewBounds(widthVal, heightVal)
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

    private fun updateLivePreviewBounds(widthVal: Int, heightVal: Int) {
        val density = resources.displayMetrics.density
        val params = binding.signaturePreviewBorderCard.layoutParams as FrameLayout.LayoutParams
        params.width = (widthVal * density).toInt()
        params.height = (heightVal * density).toInt()
        binding.signaturePreviewBorderCard.layoutParams = params
        binding.signaturePreviewBorderCard.requestLayout()
    }
}
