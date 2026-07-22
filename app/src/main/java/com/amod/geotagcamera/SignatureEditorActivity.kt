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

        // Make root clickable to hide navigation bar when clicked
        binding.root.isClickable = true
        binding.root.setOnClickListener {
            hideNavigationBar()
        }

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

        // Manual Crop Mode activation
        binding.btnManualCrop.setOnClickListener {
            // Disable other buttons during crop mode
            binding.btnRotateClockwise.isEnabled = false
            binding.btnAutoCrop.isEnabled = false
            binding.btnReset.isEnabled = false
            binding.btnManualCrop.isEnabled = false
            binding.filterRadioGroup.isEnabled = false
            binding.sensitivitySeekBar.isEnabled = false

            // Calculate actual image rect inside ImageView
            val imgRect = getImageRect(binding.sigPreviewImageView)

            // Initialize CropOverlayView
            binding.cropOverlayView.initCropBounds(imgRect)
            binding.cropOverlayView.visibility = View.VISIBLE
            binding.cropControlsContainer.visibility = View.VISIBLE
        }

        // Confirm manual crop
        binding.btnConfirmManualCrop.setOnClickListener {
            val width = currentBitmap.width
            val height = currentBitmap.height

            val imgRect = getImageRect(binding.sigPreviewImageView)
            val overlay = binding.cropOverlayView

            val w = overlay.width.toFloat()
            val h = overlay.height.toFloat()

            // Calculate crop bounds relative to the scaled image bounds
            val leftViewX = overlay.cropLeft * w
            val topViewY = overlay.cropTop * h
            val rightViewX = overlay.cropRight * w
            val bottomViewY = overlay.cropBottom * h

            // Map these view coordinates relative to imgRect back to bitmap coordinates
            val imgWidth = imgRect.width()
            val imgHeight = imgRect.height()

            if (imgWidth > 0 && imgHeight > 0) {
                val leftBmpPct = ((leftViewX - imgRect.left) / imgWidth).coerceIn(0f, 1f)
                val topBmpPct = ((topViewY - imgRect.top) / imgHeight).coerceIn(0f, 1f)
                val rightBmpPct = ((rightViewX - imgRect.left) / imgWidth).coerceIn(0f, 1f)
                val bottomBmpPct = ((bottomViewY - imgRect.top) / imgHeight).coerceIn(0f, 1f)

                val leftClip = (width * leftBmpPct).toInt()
                val topClip = (height * topBmpPct).toInt()
                val rightClip = (width * rightBmpPct).toInt()
                val bottomClip = (height * bottomBmpPct).toInt()

                val newWidth = rightClip - leftClip
                val newHeight = bottomClip - topClip

                if (newWidth > 5 && newHeight > 5) {
                    val cropped = Bitmap.createBitmap(currentBitmap, leftClip, topClip, newWidth, newHeight)
                    currentBitmap.recycle()
                    currentBitmap = cropped

                    // Hide crop controls and restore state
                    binding.cropOverlayView.visibility = View.GONE
                    binding.cropControlsContainer.visibility = View.GONE
                    binding.btnRotateClockwise.isEnabled = true
                    binding.btnAutoCrop.isEnabled = true
                    binding.btnReset.isEnabled = true
                    binding.btnManualCrop.isEnabled = true
                    binding.filterRadioGroup.isEnabled = true
                    binding.sensitivitySeekBar.isEnabled = true

                    updatePreview()
                    Toast.makeText(this, "Signature cropped successfully", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Crop area too small", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Failed to calculate image crop bounds", Toast.LENGTH_SHORT).show()
            }
        }

        // Cancel manual crop
        binding.btnCancelManualCrop.setOnClickListener {
            binding.cropOverlayView.visibility = View.GONE
            binding.cropControlsContainer.visibility = View.GONE
            binding.btnRotateClockwise.isEnabled = true
            binding.btnAutoCrop.isEnabled = true
            binding.btnReset.isEnabled = true
            binding.btnManualCrop.isEnabled = true
            binding.filterRadioGroup.isEnabled = true
            binding.sensitivitySeekBar.isEnabled = true
            updatePreview()
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

    private fun getActiveFilter(): FilterMode {
        return when (binding.filterRadioGroup.checkedRadioButtonId) {
            R.id.radioFilterTransparent -> FilterMode.TRANSPARENT_INK
            R.id.radioFilterClean -> FilterMode.CLEAN_PAPER
            else -> FilterMode.ORIGINAL
        }
    }

    private fun getImageRect(imageView: android.widget.ImageView): android.graphics.RectF {
        val drawable = imageView.drawable ?: return android.graphics.RectF(0f, 0f, imageView.width.toFloat(), imageView.height.toFloat())
        val imageWidth = drawable.intrinsicWidth
        val imageHeight = drawable.intrinsicHeight

        val viewWidth = imageView.width - imageView.paddingLeft - imageView.paddingRight
        val viewHeight = imageView.height - imageView.paddingTop - imageView.paddingBottom

        val scale = Math.min(
            viewWidth.toFloat() / imageWidth,
            viewHeight.toFloat() / imageHeight
        )

        val scaledWidth = imageWidth * scale
        val scaledHeight = imageHeight * scale

        val left = imageView.paddingLeft + (viewWidth - scaledWidth) / 2f
        val top = imageView.paddingTop + (viewHeight - scaledHeight) / 2f

        return android.graphics.RectF(left, top, left + scaledWidth, top + scaledHeight)
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

    private fun scaleBitmapIfNeeded(bitmap: Bitmap, maxDim: Int = 1024): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDim && height <= maxDim) return bitmap
        
        val ratio = width.toFloat() / height.toFloat()
        val newWidth: Int
        val newHeight: Int
        if (width > height) {
            newWidth = maxDim
            newHeight = (maxDim / ratio).toInt()
        } else {
            newHeight = maxDim
            newWidth = (maxDim * ratio).toInt()
        }
        val scaled = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        bitmap.recycle()
        return scaled
    }

    private fun loadAndCorrectImage(path: String): Bitmap? {
        try {
            val file = File(path)
            if (!file.exists()) return null

            // First, decode bounds only to determine raw dimensions and calculate optimal inSampleSize
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(path, options)
            
            val reqWidth = 1024
            val reqHeight = 1024
            var inSampleSize = 1
            if (options.outHeight > reqHeight || options.outWidth > reqWidth) {
                val halfHeight = options.outHeight / 2
                val halfWidth = options.outWidth / 2
                while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                    inSampleSize *= 2
                }
            }
            
            // Decode downscaled bitmap using optimal sample size to save heap memory
            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
            }
            val rawBitmap = BitmapFactory.decodeFile(path, decodeOptions) ?: return null

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

            val corrected = if (rotationAngle != 0) {
                rotateBitmap(rawBitmap, rotationAngle)
            } else {
                rawBitmap
            }

            // Ensure the final bitmap is exactly within 1024px maximum bounds
            return scaleBitmapIfNeeded(corrected, 1024)
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

        val originalPixels = IntArray(width * height)
        source.getPixels(originalPixels, 0, width, 0, 0, width, height)

        val grayPixels = IntArray(width * height)
        for (i in originalPixels.indices) {
            val p = originalPixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            grayPixels[i] = (0.299f * r + 0.587f * g + 0.114f * b).toInt()
        }

        // 1D integral images (flat arrays for memory locality and performance)
        val intImg = LongArray(width * height)
        val intSqImg = LongArray(width * height)

        // Compute 2D integral images (Summed Area Tables) in a single fast row-major pass
        for (y in 0 until height) {
            var rowSum = 0L
            var rowSqSum = 0L
            for (x in 0 until width) {
                val idx = y * width + x
                val valGray = grayPixels[idx].toLong()
                rowSum += valGray
                rowSqSum += valGray * valGray
                
                if (y == 0) {
                    intImg[idx] = rowSum
                    intSqImg[idx] = rowSqSum
                } else {
                    val prevRowIdx = (y - 1) * width + x
                    intImg[idx] = intImg[prevRowIdx] + rowSum
                    intSqImg[idx] = intSqImg[prevRowIdx] + rowSqSum
                }
            }
        }

        // O(1) rectangular lookup function
        fun getRectSum(intA: LongArray, x1: Int, y1: Int, x2: Int, y2: Int): Long {
            val idxBottomRight = y2 * width + x2
            val idxBottomLeft = y2 * width + (x1 - 1)
            val idxTopRight = (y1 - 1) * width + x2
            val idxTopLeft = (y1 - 1) * width + (x1 - 1)
            
            var sum = intA[idxBottomRight]
            if (x1 > 0) sum -= intA[idxBottomLeft]
            if (y1 > 0) sum -= intA[idxTopRight]
            if (x1 > 0 && y1 > 0) sum += intA[idxTopLeft]
            return sum
        }

        // Map threshold (0..255) to Sauvola k-factor
        val k = (255 - threshold) * 0.0015f + 0.05f
        
        // Dynamic local window size (approx 5% of width, odd number)
        val windowSize = ((width / 20) or 1).coerceAtLeast(15)
        val halfW = windowSize / 2
        val R = 128f

        for (y in 0 until height) {
            val y1 = (y - halfW).coerceAtLeast(0)
            val y2 = (y + halfW).coerceAtMost(height - 1)
            
            for (x in 0 until width) {
                val x1 = (x - halfW).coerceAtLeast(0)
                val x2 = (x + halfW).coerceAtMost(width - 1)
                
                val count = (x2 - x1 + 1) * (y2 - y1 + 1)
                val sum = getRectSum(intImg, x1, y1, x2, y2)
                val sumSq = getRectSum(intSqImg, x1, y1, x2, y2)
                
                val m = sum.toFloat() / count
                val variance = (sumSq.toFloat() - (sum.toFloat() * sum.toFloat() / count)) / count
                val s = Math.sqrt(variance.coerceAtLeast(0f).toDouble()).toFloat()
                
                // Sauvola Adaptive Threshold formula: T = m * (1 + k * (s / 128 - 1))
                val T = m * (1f + k * (s / R - 1f))
                
                val idx = y * width + x
                val valLuma = grayPixels[idx]
                val a = (originalPixels[idx] shr 24) and 0xFF

                if (filterMode == FilterMode.TRANSPARENT_INK) {
                    if (valLuma < T) {
                        val diff = T - valLuma
                        // Smooth edge anti-aliasing
                        val finalAlpha = ((diff / 8f).coerceIn(0f, 1f) * 255).toInt()
                        val mergedAlpha = (finalAlpha * a / 255).coerceIn(0, 255)
                        originalPixels[idx] = (mergedAlpha shl 24) or 0x000000 // Pure black ink with alpha
                    } else {
                        originalPixels[idx] = Color.TRANSPARENT
                    }
                } else if (filterMode == FilterMode.CLEAN_PAPER) {
                    if (valLuma < T) {
                        val diff = T - valLuma
                        val finalAlpha = ((diff / 8f).coerceIn(0f, 1f) * 255).toInt()
                        val gray = (255 - finalAlpha).coerceIn(0, 255)
                        originalPixels[idx] = (0xFF shl 24) or (gray shl 16) or (gray shl 8) or gray // Smooth black ink
                    } else {
                        originalPixels[idx] = -0x1 // Pure solid white paper background
                    }
                }
            }
        }

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        result.setPixels(originalPixels, 0, width, 0, 0, width, height)
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

    override fun onResume() {
        super.onResume()
        hideNavigationBar()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideNavigationBar()
        }
    }

    private fun hideNavigationBar() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.hide(android.view.WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    )
        }
    }
}
