package com.amod.geotagcamera

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.amod.geotagcamera.databinding.ActivitySignatureEditorBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class SignatureEditorActivity : AppCompatActivity() {

    enum class FilterMode {
        ORIGINAL,
        TRANSPARENT_INK,
        CLEAN_PAPER
    }

    private lateinit var binding: ActivitySignatureEditorBinding
    
    private lateinit var originalBitmap: Bitmap
    private lateinit var currentBitmap: Bitmap
    private var processedBitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignatureEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set up toolbar back button
        binding.sigEditorToolbar.setNavigationOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }

        // Get path from intent
        val tempPath = intent.getStringExtra("TEMP_SIG_PATH")
        if (tempPath.isNullOrBlank()) {
            Toast.makeText(this, "No image received", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Load image and correct EXIF rotation
        val corrected = loadAndCorrectImage(tempPath)
        if (corrected == null) {
            Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        originalBitmap = corrected
        currentBitmap = originalBitmap.copy(originalBitmap.config ?: Bitmap.Config.ARGB_8888, true)

        // Initialize UI listeners
        setupListeners()
        updatePreview()
    }

    private fun setupListeners() {
        // Rotate 90° Clockwise
        binding.btnRotateClockwise.setOnClickListener {
            val matrix = Matrix().apply { postRotate(90f) }
            val rotated = Bitmap.createBitmap(
                currentBitmap, 0, 0, currentBitmap.width, currentBitmap.height, matrix, true
            )
            currentBitmap.recycle()
            currentBitmap = rotated
            updatePreview()
        }

        // Auto-Crop whitespace edges
        binding.btnAutoCrop.setOnClickListener {
            binding.btnAutoCrop.isEnabled = false
            lifecycleScope.launch(Dispatchers.Default) {
                // Use a threshold of 210 for detection of signature lines
                val cropped = autoCropSignature(currentBitmap, 210)
                withContext(Dispatchers.Main) {
                    currentBitmap.recycle()
                    currentBitmap = cropped
                    binding.btnAutoCrop.isEnabled = true
                    updatePreview()
                    Toast.makeText(this@SignatureEditorActivity, "Signature cropped successfully", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Reset changes to original state
        binding.btnReset.setOnClickListener {
            currentBitmap.recycle()
            currentBitmap = originalBitmap.copy(originalBitmap.config ?: Bitmap.Config.ARGB_8888, true)
            binding.filterRadioGroup.check(R.id.radioFilterOriginal)
            binding.sensitivitySeekBar.progress = 200
            updatePreview()
        }

        // Filter group changed
        binding.filterRadioGroup.setOnCheckedChangeListener { _, _ ->
            updatePreview()
        }

        // Seek bar sensitivity adjustments
        binding.sensitivitySeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                binding.sliderLabel.text = "Background Cleaning Sensitivity: $progress"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                updatePreview()
            }
        })

        // Cancel button
        binding.btnCancelSig.setOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }

        // Save and Apply button
        binding.btnSaveApplySig.setOnClickListener {
            saveSignatureAndFinish()
        }
    }

    private fun updatePreview() {
        val filter = when (binding.filterRadioGroup.checkedRadioButtonId) {
            R.id.radioFilterTransparent -> FilterMode.TRANSPARENT_INK
            R.id.radioFilterClean -> FilterMode.CLEAN_PAPER
            else -> FilterMode.ORIGINAL
        }

        val threshold = binding.sensitivitySeekBar.progress
        binding.sliderContainer.visibility = if (filter == FilterMode.ORIGINAL) View.GONE else View.VISIBLE
        binding.sliderLabel.text = "Background Cleaning Sensitivity: $threshold"

        lifecycleScope.launch(Dispatchers.Default) {
            val bmp = applySigFilter(currentBitmap, filter, threshold)
            withContext(Dispatchers.Main) {
                processedBitmap?.recycle()
                processedBitmap = bmp
                binding.sigPreviewImageView.setImageBitmap(bmp)
            }
        }
    }

    private fun loadAndCorrectImage(path: String): Bitmap? {
        try {
            val file = File(path)
            if (!file.exists()) return null
            val rawBitmap = BitmapFactory.decodeFile(path) ?: return null

            // Read EXIF orientation to correct rotated pictures
            val exif = androidx.exifinterface.media.ExifInterface(path)
            val orientation = exif.getAttributeInt(
                androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
            )

            val rotationAngle = when (orientation) {
                androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90
                androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180
                androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }

            return if (rotationAngle != 0) {
                rotateBitmap(rawBitmap, rotationAngle)
            } else {
                rawBitmap
            }
        } catch (e: Exception) {
            Log.e("SigEditor", "Error reading/correcting image: ${e.message}", e)
            return null
        }
    }

    private fun rotateBitmap(source: Bitmap, angle: Int): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(angle.toFloat())
        val rotated = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        source.recycle()
        return rotated
    }

    private fun autoCropSignature(source: Bitmap, threshold: Int): Bitmap {
        val width = source.width
        val height = source.height

        var minX = width
        var minY = height
        var maxX = 0
        var maxY = 0

        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val p = pixels[y * width + x]
                val a = Color.alpha(p)
                val r = Color.red(p)
                val g = Color.green(p)
                val b = Color.blue(p)
                val luminance = (0.299 * r + 0.587 * g + 0.114 * b).toInt()

                // Consider pixel as part of signature if it's somewhat dark and opaque
                if (a > 20 && luminance < threshold) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }

        if (maxX <= minX || maxY <= minY) {
            return source.copy(source.config ?: Bitmap.Config.ARGB_8888, true)
        }

        // Add padding margins to avoid clipping letters
        val padding = 8
        minX = (minX - padding).coerceAtLeast(0)
        minY = (minY - padding).coerceAtLeast(0)
        maxX = (maxX + padding).coerceAtMost(width)
        maxY = (maxY + padding).coerceAtMost(height)

        return Bitmap.createBitmap(source, minX, minY, maxX - minX, maxY - minY)
    }

    private fun applySigFilter(source: Bitmap, filterMode: FilterMode, threshold: Int): Bitmap {
        if (filterMode == FilterMode.ORIGINAL) {
            return source.copy(source.config ?: Bitmap.Config.ARGB_8888, true)
        }

        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in pixels.indices) {
            val p = pixels[i]
            val r = Color.red(p)
            val g = Color.green(p)
            val b = Color.blue(p)
            val a = Color.alpha(p)

            val luminance = (0.299 * r + 0.587 * g + 0.114 * b).toInt()

            if (filterMode == FilterMode.TRANSPARENT_INK) {
                // If luminance is above threshold, make it transparent
                if (luminance > threshold) {
                    pixels[i] = Color.TRANSPARENT
                } else {
                    // Turn everything else into clean solid black ink (absorbing borders)
                    pixels[i] = Color.argb(a, 0, 0, 0)
                }
            } else if (filterMode == FilterMode.CLEAN_PAPER) {
                // Clean Paper: remove white background, make ink darker
                if (luminance > threshold) {
                    pixels[i] = Color.TRANSPARENT
                } else {
                    // Make the original ink darker/higher contrast
                    val factor = 0.5f
                    val nr = (r * factor).toInt().coerceIn(0, 255)
                    val ng = (g * factor).toInt().coerceIn(0, 255)
                    val nb = (b * factor).toInt().coerceIn(0, 255)
                    pixels[i] = Color.argb(a, nr, ng, nb)
                }
            }
        }

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, width, 0, 0, width, height)
        return result
    }

    private fun saveSignatureAndFinish() {
        val bitmapToSave = processedBitmap ?: currentBitmap
        try {
            val targetFile = File(filesDir, "signature.png")
            if (targetFile.exists()) {
                targetFile.delete()
            }
            
            FileOutputStream(targetFile).use { out ->
                bitmapToSave.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            
            setResult(Activity.RESULT_OK)
            finish()
        } catch (e: Exception) {
            Log.e("SigEditor", "Error saving final signature: ${e.message}", e)
            Toast.makeText(this, "Failed to save edited signature", Toast.LENGTH_SHORT).show()
        }
    }
}
