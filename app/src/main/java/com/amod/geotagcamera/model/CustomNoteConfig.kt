package com.amod.geotagcamera.model

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.amod.geotagcamera.R
import org.json.JSONObject

enum class NoteFont(
    val id: String,
    val displayName: String,
    val fontResId: Int?,
    val systemFont: String,
    val isBold: Boolean = true
) {
    CLASSIC("classic", "Classic", null, "sans-serif", true),
    MODERN("modern", "Modern", R.font.montserrat, "sans-serif-medium", true),
    NEON("neon", "Neon", R.font.caveat, "cursive", true),
    TYPEWRITER("typewriter", "Typewriter", R.font.roboto_mono, "monospace", false),
    STRONG("strong", "Strong", null, "sans-serif-black", true),
    SERIF("serif", "Serif", R.font.playfair_display, "serif", true),
    HANDWRITING("handwriting", "Signature", R.font.caveat, "casual", true),
    COMIC("comic", "Playful", null, "casual", true);

    fun getTypeface(context: Context): Typeface {
        if (fontResId != null) {
            try {
                val tf = ResourcesCompat.getFont(context, fontResId)
                if (tf != null) return tf
            } catch (_: Exception) {
            }
        }
        val style = if (isBold) Typeface.BOLD else Typeface.NORMAL
        return Typeface.create(systemFont, style)
    }

    companion object {
        fun fromId(id: String?): NoteFont {
            return values().firstOrNull { it.id.equals(id, ignoreCase = true) } ?: CLASSIC
        }
    }
}

enum class NoteHighlightMode(
    val id: Int,
    val displayName: String
) {
    NONE(0, "None"),
    SOLID_BOX(1, "Solid Box"),
    FROSTED(2, "Frosted"),
    NEON_GLOW(3, "Neon Glow"),
    INVERTED(4, "Inverted");

    companion object {
        fun fromId(id: Int): NoteHighlightMode {
            return values().firstOrNull { it.id == id } ?: SOLID_BOX
        }
    }
}

enum class NotePosition(
    val id: String,
    val displayName: String
) {
    ABOVE_CARD("above_card", "Above Card"),
    TOP("top", "Top Banner"),
    CENTER("center", "Center Floating"),
    INSIDE_OVERLAY("inside_overlay", "Inside Stamp");

    companion object {
        fun fromId(id: String?): NotePosition {
            return values().firstOrNull { it.id.equals(id, ignoreCase = true) } ?: ABOVE_CARD
        }
    }
}

data class CustomNoteConfig(
    var text: String = "",
    var fontId: String = NoteFont.CLASSIC.id,
    var textColor: Int = Color.WHITE,
    var highlightMode: Int = NoteHighlightMode.SOLID_BOX.id,
    var highlightColor: Int = Color.BLACK,
    var alignment: Int = 1, // 0 = Left, 1 = Center, 2 = Right
    var textSizeSp: Float = 20f,
    var position: String = NotePosition.ABOVE_CARD.id,
    var normPosX: Float = 0.5f,
    var normPosY: Float = 0.65f,
    var rotation: Float = 0f,
    var isCustomPositioned: Boolean = false,
    var isMirrored: Boolean = false
) {
    val font: NoteFont
        get() = NoteFont.fromId(fontId)

    val highlight: NoteHighlightMode
        get() = NoteHighlightMode.fromId(highlightMode)

    val notePosition: NotePosition
        get() = NotePosition.fromId(position)

    fun toJson(): String {
        val json = JSONObject()
        json.put("text", text)
        json.put("fontId", fontId)
        json.put("textColor", textColor)
        json.put("highlightMode", highlightMode)
        json.put("highlightColor", highlightColor)
        json.put("alignment", alignment)
        json.put("textSizeSp", textSizeSp.toDouble())
        json.put("position", position)
        json.put("normPosX", normPosX.toDouble())
        json.put("normPosY", normPosY.toDouble())
        json.put("rotation", rotation.toDouble())
        json.put("isCustomPositioned", isCustomPositioned)
        json.put("isMirrored", isMirrored)
        return json.toString()
    }

    companion object {
        private const val PREFS_NAME = "com.amod.geotagcamera.PREFERENCES"
        private const val KEY_CONFIG = "key_custom_note_config"
        private const val LEGACY_KEY_TEXT = "camera_text"

        fun load(context: Context): CustomNoteConfig {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val jsonStr = prefs.getString(KEY_CONFIG, null)
            if (!jsonStr.isNullOrBlank()) {
                try {
                    val json = JSONObject(jsonStr)
                    return CustomNoteConfig(
                        text = json.optString("text", ""),
                        fontId = json.optString("fontId", NoteFont.CLASSIC.id),
                        textColor = json.optInt("textColor", Color.WHITE),
                        highlightMode = json.optInt("highlightMode", NoteHighlightMode.SOLID_BOX.id),
                        highlightColor = json.optInt("highlightColor", Color.BLACK),
                        alignment = json.optInt("alignment", 1),
                        textSizeSp = json.optDouble("textSizeSp", 20.0).toFloat(),
                        position = json.optString("position", NotePosition.ABOVE_CARD.id),
                        normPosX = json.optDouble("normPosX", 0.5).toFloat(),
                        normPosY = json.optDouble("normPosY", 0.65).toFloat(),
                        rotation = json.optDouble("rotation", 0.0).toFloat(),
                        isCustomPositioned = json.optBoolean("isCustomPositioned", false),
                        isMirrored = json.optBoolean("isMirrored", false)
                    )
                } catch (_: Exception) {
                }
            }

            // Fallback to legacy single text string if present
            val legacyText = prefs.getString(LEGACY_KEY_TEXT, "") ?: ""
            return CustomNoteConfig(text = legacyText)
        }

        fun save(context: Context, config: CustomNoteConfig) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString(KEY_CONFIG, config.toJson())
                .putString(LEGACY_KEY_TEXT, config.text)
                .apply()
        }
    }
}
