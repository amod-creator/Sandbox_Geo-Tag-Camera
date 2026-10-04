package com.amod.geotagcamera

import android.graphics.Color
import android.os.Bundle
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.amod.geotagcamera.model.OverlayTemplate
import com.amod.geotagcamera.utils.CompassRenderer
import com.amod.geotagcamera.utils.QrCodeGenerator
import com.google.android.material.card.MaterialCardView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowCompat
import android.os.Build
import android.view.View

class TemplateSelectionActivity : AppCompatActivity() {

    private lateinit var cardDateTime: MaterialCardView
    private lateinit var cardScanLocation: MaterialCardView
    private lateinit var cardClassic: MaterialCardView
    private lateinit var cardReporting: MaterialCardView
    private lateinit var cardCompass: MaterialCardView
    private lateinit var cardLocationWatermark: MaterialCardView

    private val strokeColorSelected = Color.parseColor("#FF6D00") // Warm amber/orange
    private val strokeWidthSelected = 8 // in pixels (approx 3dp)

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.navigationBarColor = Color.TRANSPARENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
            val decor = window.peekDecorView()
            if (decor != null) {
                WindowCompat.getInsetsController(window, decor).show(WindowInsetsCompat.Type.navigationBars())
            }
        } catch (e: Exception) {
            // ignore
        }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_template_selection)

        val topBar = findViewById<View>(R.id.topBarLayout)
        val scrollView = findViewById<View>(R.id.templateScrollView)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { _, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            val navBarHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            if (topBar != null && statusBarHeight > 0) {
                topBar.setPadding(
                    topBar.paddingLeft,
                    statusBarHeight + (6 * resources.displayMetrics.density).toInt(),
                    topBar.paddingRight,
                    (8 * resources.displayMetrics.density).toInt()
                )
            }
            if (scrollView != null) {
                scrollView.setPadding(
                    scrollView.paddingLeft,
                    scrollView.paddingTop,
                    scrollView.paddingRight,
                    navBarHeight
                )
            }
            insets
        }

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }

        cardDateTime = findViewById(R.id.cardTemplateDateTime)
        cardScanLocation = findViewById(R.id.cardTemplateScanLocation)
        cardClassic = findViewById(R.id.cardTemplateClassic)
        cardReporting = findViewById(R.id.cardTemplateReporting)
        cardCompass = findViewById(R.id.cardTemplateCompass)
        cardLocationWatermark = findViewById(R.id.cardTemplateLocationWatermark)

        // Render QR Code for preview in Scan Location Template
        val previewQr = findViewById<ImageView>(R.id.previewQrCode)
        try {
            val qrBmp = QrCodeGenerator.generateQrCode("https://maps.google.com/?q=21.132845,72.804873", 180)
            previewQr.setImageBitmap(qrBmp)
        } catch (e: Exception) {
            // ignore
        }

        // Render Compass Dial for preview in Navigation Compass Template
        val previewCompass = findViewById<ImageView>(R.id.previewCompassDial)
        try {
            val compassBmp = CompassRenderer.createCompassBitmap(180, 180, 207f)
            previewCompass.setImageBitmap(compassBmp)
        } catch (e: Exception) {
            // ignore
        }

        // Set initial selected state
        val current = OverlayTemplate.getSelectedTemplate(this)
        updateCardHighlights(current)

        // Card click listeners
        cardDateTime.setOnClickListener { selectTemplate(OverlayTemplate.DATETIME) }
        cardScanLocation.setOnClickListener { selectTemplate(OverlayTemplate.SCAN_LOCATION) }
        cardClassic.setOnClickListener { selectTemplate(OverlayTemplate.CLASSIC) }
        cardReporting.setOnClickListener { selectTemplate(OverlayTemplate.REPORTING) }
        cardCompass.setOnClickListener { selectTemplate(OverlayTemplate.NAVIGATION_COMPASS) }
        cardLocationWatermark.setOnClickListener { selectTemplate(OverlayTemplate.LOCATION_WATERMARK) }
    }

    private fun selectTemplate(template: OverlayTemplate) {
        OverlayTemplate.setSelectedTemplate(this, template)
        updateCardHighlights(template)
        Toast.makeText(this, "Selected: ${template.displayName}", Toast.LENGTH_SHORT).show()
        setResult(RESULT_OK)
        finish()
    }

    private fun updateCardHighlights(selected: OverlayTemplate) {
        val cards = listOf(
            OverlayTemplate.DATETIME to cardDateTime,
            OverlayTemplate.SCAN_LOCATION to cardScanLocation,
            OverlayTemplate.CLASSIC to cardClassic,
            OverlayTemplate.REPORTING to cardReporting,
            OverlayTemplate.NAVIGATION_COMPASS to cardCompass,
            OverlayTemplate.LOCATION_WATERMARK to cardLocationWatermark
        )

        for ((template, card) in cards) {
            if (template == selected) {
                card.strokeColor = strokeColorSelected
                card.strokeWidth = strokeWidthSelected
            } else {
                card.strokeWidth = 0
            }
        }
    }
}
