package com.amod.geotagcamera.collage

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import android.os.Build
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.amod.geotagcamera.R
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * Professional Collage Editor - Matches industry-standard collage maker apps
 * Features: Layout selection, borders, backgrounds, filters, stickers, text
 */
class ProfessionalCollageActivity : AppCompatActivity() {

    private lateinit var collageView: ProfessionalCollageView

    private var photoUris = mutableListOf<Uri>()
    private var photoBitmaps = mutableListOf<Bitmap>()

    // Adapters
    private lateinit var layoutAdapter: GridLayoutPreviewAdapter
    private lateinit var borderAdapter: EnhancedBorderAdapter
    private lateinit var backgroundAdapter: ProfessionalBackgroundAdapter

    // Current selections
    private var currentLayoutIndex = 0
    private var borderWidth = 8f
    private var borderColor = Color.WHITE

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
        setContentView(R.layout.activity_professional_collage)

        setupViews()
        loadPhotos()
        setupListeners()
    }

    private fun setupViews() {
        // Setup toolbar
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Create Collage"

        // Setup collage view
        collageView = findViewById(R.id.collageView)

        // Setup border and background options (don't need photo count)
        setupBorderOptions()
        setupBackgroundOptions()

        // Default: show layout tab
        showLayoutOptions()
    }

    private fun setupLayoutOptions() {
        val layoutRecyclerView = findViewById<RecyclerView>(R.id.layoutRecyclerView)
        val layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        layoutRecyclerView.layoutManager = layoutManager

        layoutAdapter = GridLayoutPreviewAdapter(photoBitmaps.size) { template, position ->
            currentLayoutIndex = position
            collageView.setTemplate(template)

            // Clear all photos and re-add only the ones that fit the template
            collageView.clearAll()
            photoBitmaps.forEachIndexed { index, bitmap ->
                if (index < template.slots.size) {
                    collageView.addPhoto(bitmap)
                }
            }
        }
        layoutRecyclerView.adapter = layoutAdapter
    }

    private fun setupBorderOptions() {
        val borderRecyclerView = findViewById<RecyclerView>(R.id.borderRecyclerView)
        val layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        borderRecyclerView.layoutManager = layoutManager

        borderAdapter = EnhancedBorderAdapter { borderStyle ->
            // Apply the selected border style to the collage
            collageView.borderWidth = borderStyle.width
            collageView.borderColor = borderStyle.color
            collageView.borderStyleType = borderStyle.styleType
            collageView.borderSecondaryColor = borderStyle.secondaryColor
            collageView.invalidate()
        }
        borderRecyclerView.adapter = borderAdapter
    }

    private fun setupBackgroundOptions() {
        val backgroundRecyclerView = findViewById<RecyclerView>(R.id.backgroundRecyclerView)
        val layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        backgroundRecyclerView.layoutManager = layoutManager

        backgroundAdapter = ProfessionalBackgroundAdapter { color, pattern ->
            if (pattern != null) {
                collageView.backgroundPattern = pattern
            } else {
                collageView.collageBackground = color
                collageView.backgroundPattern = null
            }
            collageView.invalidate()
        }
        backgroundRecyclerView.adapter = backgroundAdapter
    }

    private fun loadPhotos() {
        val uris = intent.getParcelableArrayListExtra<Uri>("PHOTO_URIS") ?: emptyList()
        photoUris.addAll(uris)

        // Load bitmaps
        photoBitmaps.clear()
        photoUris.forEach { uri ->
            try {
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    bitmap?.let { photoBitmaps.add(it) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Set default template based on photo count
        val templates = CollageTemplates.getTemplatesForPhotoCount(photoBitmaps.size)
        if (templates.isNotEmpty()) {
            collageView.clearAll()
            collageView.setTemplate(templates.first())

            // Add photos to template (only add photos that fit the template slots)
            photoBitmaps.forEachIndexed { index, bitmap ->
                if (index < templates.first().slots.size) {
                    collageView.addPhoto(bitmap)
                }
            }
        }

        // NOW setup layout options with correct photo count
        setupLayoutOptions()
    }

    private fun setupListeners() {
        // Tab buttons
        findViewById<LinearLayout>(R.id.btnLayout).setOnClickListener {
            showLayoutOptions()
            highlightTab(it)
        }

        findViewById<LinearLayout>(R.id.btnBorder).setOnClickListener {
            showBorderOptions()
            highlightTab(it)
        }

        findViewById<LinearLayout>(R.id.btnBackground).setOnClickListener {
            showBackgroundOptions()
            highlightTab(it)
        }

        findViewById<LinearLayout>(R.id.btnRatio).setOnClickListener {
            showRatioOptions()
            highlightTab(it)
        }

        // Action buttons
        findViewById<ImageButton>(R.id.btnSave).setOnClickListener {
            saveCollage()
        }

        findViewById<ImageButton>(R.id.btnShare).setOnClickListener {
            shareCollage()
        }

        findViewById<ImageButton>(R.id.btnAddPhoto).setOnClickListener {
            addMorePhotos()
        }

        // Toolbar navigation
        findViewById<Toolbar>(R.id.toolbar).setNavigationOnClickListener {
            finish()
        }
    }

    private fun highlightTab(selectedView: View) {
        // Reset all tabs
        findViewById<LinearLayout>(R.id.btnLayout).alpha = 0.5f
        findViewById<LinearLayout>(R.id.btnBorder).alpha = 0.5f
        findViewById<LinearLayout>(R.id.btnBackground).alpha = 0.5f
        findViewById<LinearLayout>(R.id.btnRatio).alpha = 0.5f

        // Highlight selected
        selectedView.alpha = 1.0f
    }

    private fun showLayoutOptions() {
        findViewById<RecyclerView>(R.id.layoutRecyclerView).visibility = View.VISIBLE
        findViewById<RecyclerView>(R.id.borderRecyclerView).visibility = View.GONE
        findViewById<RecyclerView>(R.id.backgroundRecyclerView).visibility = View.GONE
    }

    private fun showBorderOptions() {
        findViewById<RecyclerView>(R.id.layoutRecyclerView).visibility = View.GONE
        findViewById<RecyclerView>(R.id.borderRecyclerView).visibility = View.VISIBLE
        findViewById<RecyclerView>(R.id.backgroundRecyclerView).visibility = View.GONE
    }

    private fun showBackgroundOptions() {
        findViewById<RecyclerView>(R.id.layoutRecyclerView).visibility = View.GONE
        findViewById<RecyclerView>(R.id.borderRecyclerView).visibility = View.GONE
        findViewById<RecyclerView>(R.id.backgroundRecyclerView).visibility = View.VISIBLE
    }

    private fun showRatioOptions() {
        // Show ratio picker dialog
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_ratio_picker, null)

        val ratios = listOf(
            "1:1" to 1.0f,
            "4:3" to 4f/3f,
            "3:4" to 3f/4f,
            "16:9" to 16f/9f,
            "9:16" to 9f/16f
        )

        // Setup ratio buttons (you would create these in the layout)
        dialog.setContentView(view)
        dialog.show()
    }

    private fun saveCollage() {
        val bitmap = collageView.generateCollageBitmap()
        val fileName = "Collage_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg"

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/GPS Cam Visit Pro/Collages")
        }

        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        uri?.let {
            contentResolver.openOutputStream(it)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                Toast.makeText(this, "Collage saved to gallery!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun shareCollage() {
        val bitmap = collageView.generateCollageBitmap()
        val fileName = "Collage_${System.currentTimeMillis()}.jpg"
        val file = File(cacheDir, fileName)

        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }

        val uri = androidx.core.content.FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            file
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        startActivity(Intent.createChooser(shareIntent, "Share Collage"))
    }

    private fun addMorePhotos() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        startActivityForResult(intent, REQUEST_ADD_PHOTOS)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_ADD_PHOTOS && resultCode == RESULT_OK) {
            data?.clipData?.let { clipData ->
                for (i in 0 until clipData.itemCount) {
                    val uri = clipData.getItemAt(i).uri
                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        val bitmap = BitmapFactory.decodeStream(inputStream)
                        bitmap?.let {
                            if (collageView.addPhoto(it)) {
                                photoBitmaps.add(it)
                            }
                        }
                    }
                }
            } ?: data?.data?.let { uri ->
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    bitmap?.let {
                        if (collageView.addPhoto(it)) {
                            photoBitmaps.add(it)
                        }
                    }
                }
            }
        }
    }

    companion object {
        private const val REQUEST_ADD_PHOTOS = 1001
    }
}

