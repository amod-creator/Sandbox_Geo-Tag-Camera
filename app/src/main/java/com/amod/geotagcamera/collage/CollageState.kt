package com.amod.geotagcamera.collage

import android.graphics.Bitmap
import android.graphics.RectF
import com.amod.geotagcamera.model.CustomNoteConfig

/**
 * Immutable state model for the Collage Editor V2.
 * Every change creates a new CollageState, enabling undo.
 */
data class CollageState(
    val images: List<Bitmap?>,
    val layoutIndex: Int = 0,
    val ratio: CollageRatio = CollageRatio.RATIO_1_1,
    val border: CollageBorder = CollageBorder.THIN_WHITE,
    val background: CollageBg = CollageBg.WHITE,
    val scales: List<Float> = List(images.size) { 1f },
    val offsetsX: List<Float> = List(images.size) { 0f },
    val offsetsY: List<Float> = List(images.size) { 0f },
    val selectedIndex: Int = -1,
    val stickerConfig: CustomNoteConfig? = null
)

enum class CollageRatio(val label: String, val widthRatio: Float, val heightRatio: Float) {
    RATIO_1_1("1:1", 1f, 1f),
    RATIO_3_4("3:4", 3f, 4f),
    RATIO_4_3("4:3", 4f, 3f),
    RATIO_4_5("4:5", 4f, 5f),
    RATIO_5_4("5:4", 5f, 4f),
    RATIO_9_16("9:16", 9f, 16f),
    RATIO_16_9("16:9", 16f, 9f),
    RATIO_2_3("2:3", 2f, 3f),
    RATIO_3_2("3:2", 3f, 2f),
    RATIO_5_7("5:7", 5f, 7f),
    RATIO_7_5("7:5", 7f, 5f);

    fun value(): Float = widthRatio / heightRatio
}

enum class CollageBorder(val label: String, val widthDp: Float, val color: Int, val isDashed: Boolean = false, val isDotted: Boolean = false) {
    NONE("None", 0f, 0x00000000),
    THIN_WHITE("Thin", 2f, 0xFFFFFFFF.toInt()),
    MEDIUM_WHITE("Medium", 4f, 0xFFFFFFFF.toInt()),
    THICK_WHITE("Thick", 8f, 0xFFFFFFFF.toInt()),
    THIN_BLACK("Blk Thin", 2f, 0xFF000000.toInt()),
    MEDIUM_BLACK("Blk Med", 4f, 0xFF000000.toInt()),
    THICK_BLACK("Blk Thick", 8f, 0xFF000000.toInt()),
    THIN_GRAY("Gray", 3f, 0xFF9E9E9E.toInt()),
    DASHED_WHITE("Dashed", 3f, 0xFFFFFFFF.toInt(), isDashed = true),
    DASHED_BLACK("Dash Blk", 3f, 0xFF000000.toInt(), isDashed = true),
    DOTTED_WHITE("Dotted", 3f, 0xFFFFFFFF.toInt(), isDotted = true),
    PADDED("Padded", 12f, 0xFFFFFFFF.toInt())
}

enum class CollageBg(val label: String, val colors: IntArray) {
    WHITE("White", intArrayOf(0xFFFFFFFF.toInt())),
    BLACK("Black", intArrayOf(0xFF000000.toInt())),
    DARK_GRAY("Dark Gray", intArrayOf(0xFF333333.toInt())),
    LIGHT_GRAY("Light Gray", intArrayOf(0xFFE0E0E0.toInt())),
    CREAM("Cream", intArrayOf(0xFFFFF8E1.toInt())),
    NAVY("Navy", intArrayOf(0xFF1A237E.toInt())),
    FOREST("Forest", intArrayOf(0xFF1B5E20.toInt())),
    GRADIENT_SUNSET("Sunset", intArrayOf(0xFFFF6F00.toInt(), 0xFFFF1744.toInt())),
    GRADIENT_OCEAN("Ocean", intArrayOf(0xFF0288D1.toInt(), 0xFF00BCD4.toInt())),
    GRADIENT_PURPLE("Purple", intArrayOf(0xFF7B1FA2.toInt(), 0xFF303F9F.toInt())),
    GRADIENT_MINT("Mint", intArrayOf(0xFF00C853.toInt(), 0xFF00BCD4.toInt())),
    GRADIENT_WARM("Warm", intArrayOf(0xFFFF9800.toInt(), 0xFFFFC107.toInt()))
}

/**
 * Represents a single cell in a collage layout.
 * Coordinates are normalized (0..1 range).
 */
data class LayoutCell(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    fun toRectF(canvasW: Float, canvasH: Float): RectF =
        RectF(left * canvasW, top * canvasH, right * canvasW, bottom * canvasH)
}
