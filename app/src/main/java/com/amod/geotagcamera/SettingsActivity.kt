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
                    binding.noSignatureText.visibility = View.GONE
                    binding.btnEditSignature.visibility = View.VISIBLE
                    binding.btnClearSignature.visibility = View.VISIBLE
                    binding.btnEditSpace.visibility = View.VISIBLE
                    binding.btnClearSpace.visibility = View.VISIBLE
                    binding.btnUploadSignature.text = "Replace"
                    return
                }
            } catch (e: Exception) {
                Log.e("SettingsActivity", "Error decoding signature file: ${e.message}", e)
            }
        }
        binding.signaturePreviewImage.visibility = View.GONE
        binding.noSignatureText.visibility = View.VISIBLE
        binding.btnEditSignature.visibility = View.GONE
        binding.btnClearSignature.visibility = View.GONE
        binding.btnEditSpace.visibility = View.GONE
        binding.btnClearSpace.visibility = View.GONE
        binding.btnUploadSignature.text = getString(R.string.upload_signature)
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
}
