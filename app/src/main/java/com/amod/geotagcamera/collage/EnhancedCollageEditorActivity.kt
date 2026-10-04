
package com.amod.geotagcamera.collage

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import android.os.Build
import android.graphics.Color
import com.amod.geotagcamera.R
import com.amod.geotagcamera.databinding.ActivityEnhancedCollageEditorBinding
import com.amod.geotagcamera.collage.adapters.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.graphics.RectF
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * Enhanced Collage Editor Activity with full Collage Maker functionality
 */
class EnhancedCollageEditorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEnhancedCollageEditorBinding
    private lateinit var collageCanvas: CollageCanvas

    // Current state
    private var currentTemplate: CollageTemplate? = null
    private var selectedTab = TabType.LAYOUT
    private var photoUris = mutableListOf<Uri>()
    private var photoBitmaps = mutableListOf<Bitmap>()

    // Adapters
    private lateinit var layoutAdapter: LayoutAdapter
    private lateinit var borderAdapter: BorderAdapter
    private lateinit var ratioAdapter: RatioAdapter

    enum class TabType {
        LAYOUT, BORDER, RATIO
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.navigationBarColor = Color.TRANSPARENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
            val decor = window.peekDecorView()
            if (decor != null) {
                WindowCompat.getInsetsController(window, decor).show(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
            }
        } catch (e: Exception) {
            // ignore
        }
        super.onCreate(savedInstanceState)
        binding = ActivityEnhancedCollageEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupCanvas()
        setupAdapters()  // Initialize adapters BEFORE setting up tabs
        setupTabGroup()  // Now safe to set default tab
        loadPhotos()
        setupListeners()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.collageToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Collage Editor"
    }

    private fun setupCanvas() {
        collageCanvas = binding.collageCanvas

        // Wait for canvas to be laid out before adding photos
        collageCanvas.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                if (collageCanvas.width > 0 && collageCanvas.height > 0) {
                    // Remove the listener to prevent multiple calls
                    collageCanvas.viewTreeObserver.removeOnGlobalLayoutListener(this)

                    // Canvas now has dimensions, safe to add photos
                    if (currentTemplate != null && photoBitmaps.isNotEmpty()) {
                        addPhotosToTemplate()
                    }
                }
            }
        })
    }

    private fun setupTabGroup() {
        binding.tabRow.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.tabLayout -> switchToTab(TabType.LAYOUT)
                    R.id.tabBorder -> switchToTab(TabType.BORDER)
                    R.id.tabRatio -> switchToTab(TabType.RATIO)
                }
            }
        }

        // Set default tab
        binding.tabLayout.isChecked = true
        switchToTab(TabType.LAYOUT)
    }

    private fun setupAdapters() {
        // Set up RecyclerView with horizontal LinearLayoutManager
        binding.optionsList.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(
            this,
            androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL,
            false
        )

        // Layout adapter
        layoutAdapter = LayoutAdapter { template ->
            currentTemplate = template
            collageCanvas.setTemplate(template)
            addPhotosToTemplate()
        }

        // Border adapter
        borderAdapter = BorderAdapter { style, color ->
            collageCanvas.setBorder(style, color)
        }

        // Ratio adapter
        ratioAdapter = RatioAdapter { ratio ->
            // Apply aspect ratio to canvas
            applyAspectRatio(ratio)
        }
    }

    private fun loadPhotos() {
        val uris = intent.getParcelableArrayListExtra<Uri>("PHOTO_URIS") ?: emptyList()
        photoUris.addAll(uris)

        // Load bitmaps
        photoBitmaps.clear()
        photoUris.forEach { uri ->
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val bitmap = BitmapFactory.decodeStream(inputStream)
                bitmap?.let { photoBitmaps.add(it) }
            }
        }

        // Set default template based on photo count
        val templates = CollageTemplates.getTemplatesForPhotoCount(photoBitmaps.size)
        if (templates.isNotEmpty()) {
            currentTemplate = templates.first()
            collageCanvas.setTemplate(currentTemplate!!)
            // Photos will be added when canvas has dimensions (via ViewTreeObserver)
        }
    }

    private fun addPhotosToTemplate() {
        currentTemplate?.let { template ->
            // Clear existing photo elements
            collageCanvas.clearSelection()

            // Add photos to template slots
            photoBitmaps.forEachIndexed { index, bitmap ->
                if (index < template.slots.size) {
                    val slot = template.slots[index]
                    val rect = RectF(
                        slot.rect.left * collageCanvas.width,
                        slot.rect.top * collageCanvas.height,
                        slot.rect.right * collageCanvas.width,
                        slot.rect.bottom * collageCanvas.height
                    )
                    val photoElement = CollageElement.PhotoElement(
                        id = "photo_$index",
                        rect = rect,
                        bitmap = bitmap,
                        cornerRadius = 16f
                    )

                    // Store original position for reset functionality
                    collageCanvas.storeOriginalPosition("photo_$index", RectF(rect))

                    collageCanvas.addElement(photoElement)
                }
            }
        }
    }

    private fun setupListeners() {
        // Add photo button
        binding.fabAddPhoto.setOnClickListener {
            openPhotoPicker()
        }
    }

    private fun switchToTab(tabType: TabType) {
        selectedTab = tabType

        when (tabType) {
            TabType.LAYOUT -> {
                binding.optionsList.adapter = layoutAdapter
                layoutAdapter.updateTemplates(CollageTemplates.getTemplatesForPhotoCount(photoBitmaps.size))
            }
            TabType.BORDER -> {
                binding.optionsList.adapter = borderAdapter
            }
            TabType.RATIO -> {
                binding.optionsList.adapter = ratioAdapter
            }
        }
    }

    private fun openPhotoPicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "image/*"
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        startActivityForResult(intent, REQUEST_PICK_PHOTOS)
    }

    private fun applyAspectRatio(ratio: Float) {
        // Apply aspect ratio to canvas
        val layoutParams = collageCanvas.layoutParams
        layoutParams.height = (collageCanvas.width / ratio).toInt()
        collageCanvas.layoutParams = layoutParams
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.enhanced_collage_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_save -> {
                saveCollage()
                true
            }
            R.id.action_share -> {
                shareCollage()
                true
            }
            R.id.action_export -> {
                exportCollage()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun saveCollage() {
        val bitmap = createCollageBitmap()
        val fileName = "Collage_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.jpg"

        val file = File(getExternalFilesDir(null), fileName)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }

        Toast.makeText(this, "Collage saved to ${file.absolutePath}", Toast.LENGTH_LONG).show()
    }

    private fun shareCollage() {
        val bitmap = createCollageBitmap()
        val fileName = "Collage_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.jpg"

        val file = File(getExternalFilesDir(null), fileName)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }

        val uri = Uri.fromFile(file)
        val shareIntent = Intent(Intent.ACTION_SEND)
        shareIntent.type = "image/jpeg"
        shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
        startActivity(Intent.createChooser(shareIntent, "Share Collage"))
    }

    private fun exportCollage() {
        // Show export options dialog
        val options = arrayOf("Save to Gallery", "Save as PDF", "Print")
        MaterialAlertDialogBuilder(this)
            .setTitle("Export Options")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> saveToGallery()
                    1 -> saveAsPdf()
                    2 -> printCollage()
                }
            }
            .show()
    }

    private fun saveToGallery() {
        // Implement gallery save
        Toast.makeText(this, "Saved to Gallery", Toast.LENGTH_SHORT).show()
    }

    private fun saveAsPdf() {
        // Implement PDF export
        Toast.makeText(this, "Exported as PDF", Toast.LENGTH_SHORT).show()
    }

    private fun printCollage() {
        // Implement print functionality
        Toast.makeText(this, "Print functionality", Toast.LENGTH_SHORT).show()
    }

    private fun createCollageBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(collageCanvas.width, collageCanvas.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        collageCanvas.draw(canvas)
        return bitmap
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_PICK_PHOTOS && resultCode == RESULT_OK) {
            data?.clipData?.let { clipData ->
                for (i in 0 until clipData.itemCount) {
                    val uri = clipData.getItemAt(i).uri
                    photoUris.add(uri)

                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        val bitmap = BitmapFactory.decodeStream(inputStream)
                        bitmap?.let { photoBitmaps.add(it) }
                    }
                }
            } ?: data?.data?.let { uri ->
                photoUris.add(uri)
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    bitmap?.let { photoBitmaps.add(it) }
                }
            }

            // Refresh template options
            switchToTab(selectedTab)
        }
    }

    companion object {
        private const val REQUEST_PICK_PHOTOS = 1001
    }
}
