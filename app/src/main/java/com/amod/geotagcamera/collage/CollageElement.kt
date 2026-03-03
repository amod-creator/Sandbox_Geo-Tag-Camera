package com.amod.geotagcamera.collage

import android.graphics.*
import android.graphics.drawable.Drawable
import android.net.Uri

/**
 * Represents different types of elements that can be added to a collage
 */
sealed class CollageElement {
    abstract val id: String
    abstract val rect: RectF
    abstract val rotation: Float
    abstract val scale: Float
    
    data class PhotoElement(
        override val id: String,
        override val rect: RectF,
        override val rotation: Float = 0f,
        override val scale: Float = 1f,
        val bitmap: Bitmap? = null,
        val uri: Uri? = null,
        val cornerRadius: Float = 0f,
        val filter: PhotoFilter? = null,
        val brightness: Float = 0f,
        val contrast: Float = 1f,
        val saturation: Float = 1f
    ) : CollageElement()
    
    data class TextElement(
        override val id: String,
        override val rect: RectF,
        override val rotation: Float = 0f,
        override val scale: Float = 1f,
        val text: String,
        val textSize: Float = 48f,
        val textColor: Int = Color.WHITE,
        val fontFamily: String = "default",
        val textStyle: TextStyle = TextStyle.NORMAL,
        val alignment: TextAlignment = TextAlignment.CENTER,
        val backgroundColor: Int? = null,
        val backgroundOpacity: Float = 0f,
        val padding: Float = 8f,
        val cornerRadius: Float = 0f
    ) : CollageElement()
    
    data class StickerElement(
        override val id: String,
        override val rect: RectF,
        override val rotation: Float = 0f,
        override val scale: Float = 1f,
        val stickerRes: Int,
        val tintColor: Int? = null,
        val opacity: Float = 1f,
        val cornerRadius: Float = 0f
    ) : CollageElement()
    
    data class ShapeElement(
        override val id: String,
        override val rect: RectF,
        override val rotation: Float = 0f,
        override val scale: Float = 1f,
        val shapeType: ShapeType,
        val fillColor: Int = Color.TRANSPARENT,
        val strokeColor: Int = Color.WHITE,
        val strokeWidth: Float = 2f,
        val cornerRadius: Float = 0f
    ) : CollageElement()
}

/**
 * Photo filter types
 */
enum class PhotoFilter(val displayName: String, val matrix: FloatArray) {
    NONE("None", floatArrayOf(
        1f, 0f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, 1f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )),
    VINTAGE("Vintage", floatArrayOf(
        0.9f, 0.5f, 0.1f, 0f, 0f,
        0.3f, 0.8f, 0.1f, 0f, 0f,
        0.2f, 0.3f, 0.5f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )),
    SEPIA("Sepia", floatArrayOf(
        0.393f, 0.769f, 0.189f, 0f, 0f,
        0.349f, 0.686f, 0.168f, 0f, 0f,
        0.272f, 0.534f, 0.131f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )),
    BLACK_WHITE("B&W", floatArrayOf(
        0.33f, 0.33f, 0.33f, 0f, 0f,
        0.33f, 0.33f, 0.33f, 0f, 0f,
        0.33f, 0.33f, 0.33f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )),
    BLUR("Blur", floatArrayOf(
        0.25f, 0.25f, 0.25f, 0f, 0f,
        0.25f, 0.25f, 0.25f, 0f, 0f,
        0.25f, 0.25f, 0.25f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )),
    SHARPEN("Sharpen", floatArrayOf(
        0f, -1f, 0f, 0f, 0f,
        -1f, 5f, -1f, 0f, 0f,
        0f, -1f, 0f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    ))
}

/**
 * Text style options
 */
enum class TextStyle {
    NORMAL, BOLD, ITALIC, BOLD_ITALIC
}

/**
 * Text alignment options
 */
enum class TextAlignment {
    LEFT, CENTER, RIGHT
}

/**
 * Shape types for decorative elements
 */
enum class ShapeType {
    RECTANGLE, CIRCLE, TRIANGLE, HEART, STAR, ARROW
}

/**
 * Border styles
 */
enum class BorderStyle(val displayName: String, val strokeWidth: Float) {
    NONE("None", 0f),
    THIN("Thin", 2f),
    MEDIUM("Medium", 4f),
    THICK("Thick", 8f),
    DOTTED("Dotted", 4f),
    DASHED("Dashed", 4f)
}

/**
 * Background types
 */
sealed class BackgroundType {
    data class ColorBackground(val color: Int) : BackgroundType()
    data class GradientBackground(
        val startColor: Int,
        val endColor: Int,
        val direction: GradientDirection
    ) : BackgroundType()
    data class PatternBackground(val patternRes: Int) : BackgroundType()
    data class ImageBackground(val bitmap: Bitmap) : BackgroundType()
}

enum class GradientDirection {
    HORIZONTAL, VERTICAL, DIAGONAL_TOP_LEFT, DIAGONAL_TOP_RIGHT
}
