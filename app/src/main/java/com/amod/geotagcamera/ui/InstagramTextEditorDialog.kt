package com.amod.geotagcamera.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.amod.geotagcamera.R
import com.amod.geotagcamera.model.CustomNoteConfig
import com.amod.geotagcamera.model.NoteFont
import com.amod.geotagcamera.model.NoteHighlightMode
import com.amod.geotagcamera.model.NotePosition
import com.amod.geotagcamera.utils.InstagramTextStyler

class InstagramTextEditorDialog(
    context: Context,
    initialConfig: CustomNoteConfig,
    private val shouldSaveGlobally: Boolean = true,
    private val onSave: (CustomNoteConfig) -> Unit
) : Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen) {

    private val workingConfig = initialConfig.copy()

    private lateinit var etNoteContent: EditText
    private lateinit var btnAlignment: ImageButton
    private lateinit var btnHighlight: ImageButton
    private lateinit var btnPosition: TextView
    private lateinit var btnDone: TextView
    private lateinit var btnClose: ImageButton
    private lateinit var containerFonts: LinearLayout
    private lateinit var containerColors: LinearLayout
    private lateinit var containerPresets: LinearLayout
    private lateinit var sliderTouchTrack: FrameLayout
    private lateinit var sliderThumb: View
    private lateinit var sliderFill: View
    private lateinit var tabTextColor: TextView
    private lateinit var tabBgColor: TextView

    private var isEditingTextColor = true
    private val minSp = 14f
    private val maxSp = 48f

    private val paletteColors = listOf(
        Color.parseColor("#FFFFFF"), // Crisp White
        Color.parseColor("#000000"), // Pure Black
        Color.parseColor("#1C1C1E"), // Dark Slate
        Color.parseColor("#FF2D55"), // Instagram Neon Pink
        Color.parseColor("#FF3B30"), // Vivid Coral Red
        Color.parseColor("#FF9500"), // Sunset Orange
        Color.parseColor("#FFCC00"), // Sunshine Gold
        Color.parseColor("#34C759"), // Vibrant Green
        Color.parseColor("#00C7BE"), // Mint Aqua
        Color.parseColor("#32ADE6"), // Sky Blue
        Color.parseColor("#007AFF"), // Electric Blue
        Color.parseColor("#5856D6"), // Deep Indigo
        Color.parseColor("#AF52DE"), // Royal Purple
        Color.parseColor("#E040FB"), // Berry Magenta
        Color.parseColor("#8E8E93"), // Neutral Slate
        Color.parseColor("#F2F2F7")  // Light Gray
    )

    private val quickPresets = listOf(
        "📍 Location Check-in",
        "📋 Site Verification",
        "🔍 Field Inspection",
        "⭐ Important Note",
        "🏗️ Work Progress",
        "🌾 Crop Assessment",
        "❌ Clear"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_instagram_text_editor)

        window?.let { win ->
            win.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            win.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            win.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            win.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                win.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                win.attributes = win.attributes?.apply {
                    blurBehindRadius = 35
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                win.setDecorFitsSystemWindows(false)
            }
        }

        hideSystemUI()
        setupKeyboardAndInsetsHandling()

        initViews()
        setupListeners()
        setupColorModeTabs()
        populatePresets()
        populateFontSelector()
        populateColorPalette()
        setupVerticalSlider()

        applyLivePreview()

        // Focus & show keyboard smoothly
        etNoteContent.postDelayed({
            etNoteContent.requestFocus()
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(etNoteContent, InputMethodManager.SHOW_IMPLICIT)
        }, 150)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUI()
        }
    }

    private fun hideSystemUI() {
        window?.let { win ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                win.insetsController?.let { controller ->
                    controller.hide(WindowInsets.Type.navigationBars())
                    controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                @Suppress("DEPRECATION")
                win.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                )
            }
        }
    }

    private fun setupKeyboardAndInsetsHandling() {
        val root = findViewById<View>(R.id.editorRootLayout) ?: return
        val topBar = findViewById<View>(R.id.topBar) ?: return
        val bottomControlArea = findViewById<View>(R.id.bottomControlArea) ?: return

        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        val defaultStatusBar = if (resourceId > 0) context.resources.getDimensionPixelSize(resourceId) else dpToPx(28f).toInt()

        (topBar.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
            lp.topMargin = defaultStatusBar
            topBar.layoutParams = lp
        }

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val statusInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val navInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())

            // Shift topBar below status bar
            val topMargin = if (statusInsets.top > 0) statusInsets.top else defaultStatusBar
            (topBar.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
                if (lp.topMargin != topMargin) {
                    lp.topMargin = topMargin
                    topBar.requestLayout()
                }
            }

            // Shift functions of color and other controls upwards above the keyboard, and down when keyboard hides
            val targetBottom = if (imeInsets.bottom > 0) {
                imeInsets.bottom
            } else {
                navInsets.bottom
            }

            (bottomControlArea.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
                if (lp.bottomMargin != targetBottom) {
                    lp.bottomMargin = targetBottom
                    bottomControlArea.requestLayout()
                }
            }

            insets
        }

        // Additional listener for dynamic keyboard height tracking across all device form factors
        root.viewTreeObserver.addOnGlobalLayoutListener {
            val r = Rect()
            root.getWindowVisibleDisplayFrame(r)
            val screenHeight = root.rootView.height
            val keypadHeight = screenHeight - r.bottom
            val isKeyboardVisible = keypadHeight > dpToPx(100f)
            val targetBottom = if (isKeyboardVisible) keypadHeight else 0

            (bottomControlArea.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
                if (Math.abs(lp.bottomMargin - targetBottom) > 12) {
                    lp.bottomMargin = targetBottom
                    bottomControlArea.requestLayout()
                }
            }
        }
    }

    private fun initViews() {
        etNoteContent = findViewById(R.id.etNoteContent)
        btnAlignment = findViewById(R.id.btnAlignment)
        btnHighlight = findViewById(R.id.btnHighlight)
        btnPosition = findViewById(R.id.btnPosition)
        btnDone = findViewById(R.id.btnDone)
        btnClose = findViewById(R.id.btnClose)
        containerFonts = findViewById(R.id.containerFonts)
        containerColors = findViewById(R.id.containerColors)
        containerPresets = findViewById(R.id.containerPresets)
        sliderTouchTrack = findViewById(R.id.sliderTouchTrack)
        sliderThumb = findViewById(R.id.sliderThumb)
        sliderFill = findViewById(R.id.sliderFill)
        tabTextColor = findViewById(R.id.tabTextColor)
        tabBgColor = findViewById(R.id.tabBgColor)

        etNoteContent.setText(workingConfig.text)
        etNoteContent.setSelection(workingConfig.text.length)
    }

    private fun setupListeners() {
        // Text changes
        etNoteContent.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                workingConfig.text = s?.toString() ?: ""
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Close / Cancel
        btnClose.setOnClickListener {
            dismiss()
        }

        // Done / Save
        btnDone.setOnClickListener {
            workingConfig.text = etNoteContent.text.toString().trim()
            if (shouldSaveGlobally) {
                CustomNoteConfig.save(context, workingConfig)
            }
            onSave(workingConfig)
            dismiss()
        }

        // Alignment toggle: Left -> Center -> Right
        btnAlignment.setOnClickListener {
            workingConfig.alignment = (workingConfig.alignment + 1) % 3
            updateAlignmentIcon()
            applyLivePreview()
        }
        updateAlignmentIcon()

        // Highlight mode toggle: Solid -> Frosted -> Neon -> Inverted -> None
        btnHighlight.setOnClickListener {
            val modes = NoteHighlightMode.values()
            val currentIdx = modes.indexOf(workingConfig.highlight)
            val nextIdx = (currentIdx + 1) % modes.size
            workingConfig.highlightMode = modes[nextIdx].id
            applyLivePreview()
            refreshColorPaletteSelection()
        }

        // Position toggle: Above Card -> Top -> Center -> Inside Stamp
        btnPosition.setOnClickListener {
            val positions = NotePosition.values()
            val currentIdx = positions.indexOf(workingConfig.notePosition)
            val nextIdx = (currentIdx + 1) % positions.size
            workingConfig.position = positions[nextIdx].id
            btnPosition.text = positions[nextIdx].displayName
        }
        btnPosition.text = workingConfig.notePosition.displayName
    }

    private fun setupColorModeTabs() {
        updateColorModeTabsVisual()

        tabTextColor.setOnClickListener {
            isEditingTextColor = true
            updateColorModeTabsVisual()
            populateColorPalette()
        }

        tabBgColor.setOnClickListener {
            isEditingTextColor = false
            // If background mode was NONE, switch to SOLID_BOX so user can immediately see background color
            if (workingConfig.highlight == NoteHighlightMode.NONE) {
                workingConfig.highlightMode = NoteHighlightMode.SOLID_BOX.id
                applyLivePreview()
            }
            updateColorModeTabsVisual()
            populateColorPalette()
        }
    }

    private fun updateColorModeTabsVisual() {
        if (isEditingTextColor) {
            tabTextColor.setBackgroundResource(R.drawable.badge_template_pill)
            tabTextColor.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            tabTextColor.setTextColor(Color.BLACK)

            tabBgColor.setBackgroundResource(R.drawable.badge_instagram_pill)
            tabBgColor.backgroundTintList = null
            tabBgColor.setTextColor(Color.WHITE)
        } else {
            tabTextColor.setBackgroundResource(R.drawable.badge_instagram_pill)
            tabTextColor.backgroundTintList = null
            tabTextColor.setTextColor(Color.WHITE)

            tabBgColor.setBackgroundResource(R.drawable.badge_template_pill)
            tabBgColor.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            tabBgColor.setTextColor(Color.BLACK)
        }
    }

    private fun updateAlignmentIcon() {
        val iconRes = when (workingConfig.alignment) {
            0 -> R.drawable.ic_format_align_left
            2 -> R.drawable.ic_format_align_right
            else -> R.drawable.ic_format_align_center
        }
        btnAlignment.setImageResource(iconRes)
    }

    private fun setupVerticalSlider() {
        sliderTouchTrack.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    val trackHeight = view.height.toFloat()
                    if (trackHeight > 0) {
                        updateSliderFromTouch(event.y, trackHeight)
                    }
                    true
                }
                else -> false
            }
        }

        sliderTouchTrack.post {
            val trackHeight = sliderTouchTrack.height.toFloat()
            if (trackHeight > 0) {
                updateSliderFromSize(workingConfig.textSizeSp, trackHeight)
            }
        }
    }

    private fun updateSliderFromTouch(touchY: Float, trackHeight: Float) {
        val thumbHeight = if (sliderThumb.height > 0) sliderThumb.height.toFloat() else dpToPx(22f)
        val usableTrack = (trackHeight - thumbHeight).coerceAtLeast(1f)

        // Clamped thumb position between 0 (top) and usableTrack (bottom)
        val clampedThumbY = (touchY - thumbHeight / 2f).coerceIn(0f, usableTrack)
        sliderThumb.translationY = clampedThumbY

        // Ratio: 1.0 at top (highest size), 0.0 at bottom (lowest size)
        val ratio = 1f - (clampedThumbY / usableTrack)
        val newSize = minSp + (ratio * (maxSp - minSp))
        workingConfig.textSizeSp = newSize

        // Fill bar extends from bottom up to thumb position
        val fillHeight = (trackHeight - clampedThumbY).toInt()
        val fillLp = sliderFill.layoutParams
        fillLp.height = fillHeight
        sliderFill.layoutParams = fillLp

        findViewById<TextView>(R.id.tvSizeIndicator)?.text = "${newSize.toInt()}sp"
        applyLivePreview()
    }

    private fun updateSliderFromSize(sizeSp: Float, trackHeight: Float) {
        val thumbHeight = if (sliderThumb.height > 0) sliderThumb.height.toFloat() else dpToPx(22f)
        val usableTrack = (trackHeight - thumbHeight).coerceAtLeast(1f)
        val ratio = ((sizeSp - minSp) / (maxSp - minSp)).coerceIn(0f, 1f)

        // At ratio 1.0 (top), thumbY is 0f
        val thumbY = (1f - ratio) * usableTrack
        sliderThumb.translationY = thumbY

        val fillHeight = (trackHeight - thumbY).toInt()
        val fillLp = sliderFill.layoutParams
        fillLp.height = fillHeight
        sliderFill.layoutParams = fillLp

        findViewById<TextView>(R.id.tvSizeIndicator)?.text = "${sizeSp.toInt()}sp"
    }

    private fun populatePresets() {
        containerPresets.removeAllViews()
        val inflater = LayoutInflater.from(context)

        for (preset in quickPresets) {
            val chip = inflater.inflate(R.layout.item_font_chip, containerPresets, false) as TextView
            chip.text = preset
            chip.textSize = 12f

            chip.setOnClickListener {
                if (preset == "❌ Clear") {
                    etNoteContent.setText("")
                    workingConfig.text = ""
                } else {
                    val current = etNoteContent.text.toString().trim()
                    if (current.isEmpty()) {
                        etNoteContent.setText(preset)
                    } else {
                        etNoteContent.setText("$preset - $current")
                    }
                    etNoteContent.setSelection(etNoteContent.text.length)
                }
                applyLivePreview()
            }

            containerPresets.addView(chip)
        }
    }

    private fun populateFontSelector() {
        containerFonts.removeAllViews()
        val inflater = LayoutInflater.from(context)

        for (font in NoteFont.values()) {
            val chip = inflater.inflate(R.layout.item_font_chip, containerFonts, false) as TextView
            chip.text = font.displayName
            chip.typeface = font.getTypeface(context)

            updateFontChipVisual(chip, font == workingConfig.font)

            chip.setOnClickListener {
                workingConfig.fontId = font.id
                // Refresh all chips
                for (i in 0 until containerFonts.childCount) {
                    val child = containerFonts.getChildAt(i) as? TextView
                    val childFont = NoteFont.values().getOrNull(i)
                    if (child != null && childFont != null) {
                        updateFontChipVisual(child, childFont == workingConfig.font)
                    }
                }
                applyLivePreview()
            }

            containerFonts.addView(chip)
        }
    }

    private fun updateFontChipVisual(chip: TextView, isSelected: Boolean) {
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(14f)
            if (isSelected) {
                setColor(Color.parseColor("#FFFFFF"))
                setStroke(dpToPx(1.5f).toInt(), Color.parseColor("#FFFFFF"))
            } else {
                setColor(Color.parseColor("#4D000000"))
                setStroke(dpToPx(1f).toInt(), Color.parseColor("#59FFFFFF"))
            }
        }
        chip.background = bg
        chip.setTextColor(if (isSelected) Color.BLACK else Color.WHITE)
    }

    private fun populateColorPalette() {
        containerColors.removeAllViews()
        val inflater = LayoutInflater.from(context)

        // If editing background color, first option is "🚫 No Background"
        if (!isEditingTextColor) {
            val noneCircle = inflater.inflate(R.layout.item_color_circle, containerColors, false)
            val isSelected = workingConfig.highlight == NoteHighlightMode.NONE
            updateNoneCircleVisual(noneCircle, isSelected)

            noneCircle.setOnClickListener {
                workingConfig.highlightMode = NoteHighlightMode.NONE.id
                refreshColorPaletteSelection()
                applyLivePreview()
            }

            containerColors.addView(noneCircle)
        }

        for (color in paletteColors) {
            val circleView = inflater.inflate(R.layout.item_color_circle, containerColors, false)
            val isSelected = if (isEditingTextColor) {
                color == workingConfig.textColor
            } else {
                workingConfig.highlight != NoteHighlightMode.NONE && color == workingConfig.highlightColor
            }
            updateColorCircleVisual(circleView, color, isSelected)

            circleView.setOnClickListener {
                if (isEditingTextColor) {
                    workingConfig.textColor = color
                } else {
                    workingConfig.highlightColor = color
                    if (workingConfig.highlight == NoteHighlightMode.NONE) {
                        workingConfig.highlightMode = NoteHighlightMode.SOLID_BOX.id
                    }
                }
                refreshColorPaletteSelection()
                applyLivePreview()
            }

            containerColors.addView(circleView)
        }
    }

    private fun refreshColorPaletteSelection() {
        val totalCount = containerColors.childCount
        val startIndex = if (!isEditingTextColor) 1 else 0

        if (!isEditingTextColor && totalCount > 0) {
            val noneCircle = containerColors.getChildAt(0)
            val isNoneSelected = workingConfig.highlight == NoteHighlightMode.NONE
            updateNoneCircleVisual(noneCircle, isNoneSelected)
        }

        for (i in startIndex until totalCount) {
            val child = containerColors.getChildAt(i)
            val colorIndex = if (!isEditingTextColor) i - 1 else i
            val childColor = paletteColors.getOrNull(colorIndex) ?: Color.WHITE

            val isSelected = if (isEditingTextColor) {
                childColor == workingConfig.textColor
            } else {
                workingConfig.highlight != NoteHighlightMode.NONE && childColor == workingConfig.highlightColor
            }
            updateColorCircleVisual(child, childColor, isSelected)
        }
    }

    private fun updateColorCircleVisual(circleView: View, color: Int, isSelected: Boolean) {
        val dot = circleView.findViewById<View>(R.id.viewColorDot)
        val ring = circleView.findViewById<View>(R.id.viewSelectionRing)

        // Color Dot with white border around each color dot shown on screen
        val dotDrawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            if (isSelected) {
                setStroke(dpToPx(2.5f).toInt(), Color.WHITE)
            } else {
                setStroke(dpToPx(1.5f).toInt(), Color.WHITE)
            }
        }
        dot?.background = dotDrawable

        // Prominent Outer White Border Ring when selected
        if (isSelected) {
            ring?.visibility = View.VISIBLE
            val ringDrawable = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.TRANSPARENT)
                setStroke(dpToPx(3f).toInt(), Color.WHITE)
            }
            ring?.background = ringDrawable
        } else {
            ring?.visibility = View.GONE
        }
    }

    private fun updateNoneCircleVisual(noneCircle: View, isSelected: Boolean) {
        val dot = noneCircle.findViewById<View>(R.id.viewColorDot)
        val ring = noneCircle.findViewById<View>(R.id.viewSelectionRing)

        val noneDrawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#33FFFFFF"))
            if (isSelected) {
                setStroke(dpToPx(2.5f).toInt(), Color.WHITE)
            } else {
                setStroke(dpToPx(1.5f).toInt(), Color.WHITE)
            }
        }
        dot?.background = noneDrawable

        if (isSelected) {
            ring?.visibility = View.VISIBLE
            val ringDrawable = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.TRANSPARENT)
                setStroke(dpToPx(3f).toInt(), Color.WHITE)
            }
            ring?.background = ringDrawable
        } else {
            ring?.visibility = View.GONE
        }
    }

    private fun applyLivePreview() {
        InstagramTextStyler.applyStyleToView(etNoteContent, workingConfig)
    }

    private fun dpToPx(dp: Float): Float {
        return dp * context.resources.displayMetrics.density
    }
}
