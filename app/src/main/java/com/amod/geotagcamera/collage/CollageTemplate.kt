package com.amod.geotagcamera.collage

import android.graphics.RectF
import com.amod.geotagcamera.R

/**
 * Represents a collage template with predefined layout structure
 */
data class CollageTemplate(
    val id: String,
    val name: String,
    val thumbnailRes: Int,
    val slots: List<Slot>,
    val aspectRatio: Float = 1.0f
) {
    data class Slot(
        val id: Int,
        val rect: RectF,
        val cornerRadius: Float = 0f,
        val rotation: Float = 0f
    )
}

/**
 * Predefined collage templates similar to Collage Maker app
 */
object CollageTemplates {
    
    // 2 Photo Templates
    val SPLIT_HORIZONTAL = CollageTemplate(
        id = "split_h",
        name = "Split Horizontal",
        thumbnailRes = R.drawable.ic_layout_split_h,
        slots = listOf(
            CollageTemplate.Slot(0, RectF(0f, 0f, 1f, 0.5f)),
            CollageTemplate.Slot(1, RectF(0f, 0.5f, 1f, 1f))
        )
    )
    
    val SPLIT_VERTICAL = CollageTemplate(
        id = "split_v",
        name = "Split Vertical", 
        thumbnailRes = R.drawable.ic_layout_split_v,
        slots = listOf(
            CollageTemplate.Slot(0, RectF(0f, 0f, 0.5f, 1f)),
            CollageTemplate.Slot(1, RectF(0.5f, 0f, 1f, 1f))
        )
    )
    
    // 3 Photo Templates
    val THREE_GRID_1L_2S = CollageTemplate(
        id = "3_grid_1l_2s",
        name = "1 Large + 2 Small",
        thumbnailRes = R.drawable.ic_layout_grid_1l_2s,
        slots = listOf(
            CollageTemplate.Slot(0, RectF(0f, 0f, 0.6f, 1f)),
            CollageTemplate.Slot(1, RectF(0.6f, 0f, 1f, 0.5f)),
            CollageTemplate.Slot(2, RectF(0.6f, 0.5f, 1f, 1f))
        )
    )
    
    val THREE_ROW = CollageTemplate(
        id = "3_row",
        name = "Three Row",
        thumbnailRes = R.drawable.ic_layout_row_3,
        slots = listOf(
            CollageTemplate.Slot(0, RectF(0f, 0f, 1f, 0.33f)),
            CollageTemplate.Slot(1, RectF(0f, 0.33f, 1f, 0.66f)),
            CollageTemplate.Slot(2, RectF(0f, 0.66f, 1f, 1f))
        )
    )
    
    val THREE_COLUMN = CollageTemplate(
        id = "3_column",
        name = "Three Column",
        thumbnailRes = R.drawable.ic_layout_column_3,
        slots = listOf(
            CollageTemplate.Slot(0, RectF(0f, 0f, 0.33f, 1f)),
            CollageTemplate.Slot(1, RectF(0.33f, 0f, 0.66f, 1f)),
            CollageTemplate.Slot(2, RectF(0.66f, 0f, 1f, 1f))
        )
    )

    val THREE_GRID_2S_1L = CollageTemplate(
        id = "3_grid_2s_1l",
        name = "2 Small + 1 Large",
        thumbnailRes = R.drawable.ic_layout_grid_2s_1l,
        slots = listOf(
            CollageTemplate.Slot(0, RectF(0f, 0f, 0.4f, 0.5f)),
            CollageTemplate.Slot(1, RectF(0f, 0.5f, 0.4f, 1f)),
            CollageTemplate.Slot(2, RectF(0.4f, 0f, 1f, 1f))
        )
    )

    val THREE_GRID_TOP_BOTTOM = CollageTemplate(
        id = "3_grid_top_bottom",
        name = "Top + Bottom",
        thumbnailRes = R.drawable.ic_layout_grid_top_bottom_3,
        slots = listOf(
            CollageTemplate.Slot(0, RectF(0f, 0f, 1f, 0.6f)),
            CollageTemplate.Slot(1, RectF(0f, 0.6f, 0.5f, 1f)),
            CollageTemplate.Slot(2, RectF(0.5f, 0.6f, 1f, 1f))
        )
    )

    // 4 Photo Templates
    val GRID_2X2 = CollageTemplate(
        id = "grid_2x2",
        name = "2x2 Grid",
        thumbnailRes = R.drawable.ic_layout_grid_2x2,
        slots = listOf(
            CollageTemplate.Slot(0, RectF(0f, 0f, 0.5f, 0.5f)),
            CollageTemplate.Slot(1, RectF(0.5f, 0f, 1f, 0.5f)),
            CollageTemplate.Slot(2, RectF(0f, 0.5f, 0.5f, 1f)),
            CollageTemplate.Slot(3, RectF(0.5f, 0.5f, 1f, 1f))
        )
    )
    
    val FOUR_ROW = CollageTemplate(
        id = "4_row",
        name = "Four Row",
        thumbnailRes = R.drawable.ic_layout_row_4,
        slots = listOf(
            CollageTemplate.Slot(0, RectF(0f, 0f, 1f, 0.25f)),
            CollageTemplate.Slot(1, RectF(0f, 0.25f, 1f, 0.5f)),
            CollageTemplate.Slot(2, RectF(0f, 0.5f, 1f, 0.75f)),
            CollageTemplate.Slot(3, RectF(0f, 0.75f, 1f, 1f))
        )
    )
    
    val FOUR_COLUMN = CollageTemplate(
        id = "4_column",
        name = "Four Column",
        thumbnailRes = R.drawable.ic_layout_column_4,
        slots = listOf(
            CollageTemplate.Slot(0, RectF(0f, 0f, 0.25f, 1f)),
            CollageTemplate.Slot(1, RectF(0.25f, 0f, 0.5f, 1f)),
            CollageTemplate.Slot(2, RectF(0.5f, 0f, 0.75f, 1f)),
            CollageTemplate.Slot(3, RectF(0.75f, 0f, 1f, 1f))
        )
    )

    val FOUR_GRID_1L_3S = CollageTemplate(
        id = "4_grid_1l_3s",
        name = "1 Large + 3 Small",
        thumbnailRes = R.drawable.ic_layout_grid_1l_3s,
        slots = listOf(
            CollageTemplate.Slot(0, RectF(0f, 0f, 0.66f, 1f)),
            CollageTemplate.Slot(1, RectF(0.66f, 0f, 1f, 0.33f)),
            CollageTemplate.Slot(2, RectF(0.66f, 0.33f, 1f, 0.66f)),
            CollageTemplate.Slot(3, RectF(0.66f, 0.66f, 1f, 1f))
        )
    )

    val FOUR_GRID_TOP_BOTTOM = CollageTemplate(
        id = "4_grid_top_bottom",
        name = "Top + Bottom Split",
        thumbnailRes = R.drawable.ic_layout_grid_top_bottom_4,
        slots = listOf(
            CollageTemplate.Slot(0, RectF(0f, 0f, 0.5f, 0.6f)),
            CollageTemplate.Slot(1, RectF(0.5f, 0f, 1f, 0.6f)),
            CollageTemplate.Slot(2, RectF(0f, 0.6f, 0.5f, 1f)),
            CollageTemplate.Slot(3, RectF(0.5f, 0.6f, 1f, 1f))
        )
    )

    val FOUR_GRID_LEFT_RIGHT = CollageTemplate(
        id = "4_grid_left_right",
        name = "Left + Right Split",
        thumbnailRes = R.drawable.ic_layout_grid_left_right_4,
        slots = listOf(
            CollageTemplate.Slot(0, RectF(0f, 0f, 0.6f, 0.5f)),
            CollageTemplate.Slot(1, RectF(0f, 0.5f, 0.6f, 1f)),
            CollageTemplate.Slot(2, RectF(0.6f, 0f, 1f, 0.5f)),
            CollageTemplate.Slot(3, RectF(0.6f, 0.5f, 1f, 1f))
        )
    )

    // 5 Photo Templates
    val FIVE_GRID_3_OVER_2 = CollageTemplate(
        id = "5_grid_3_over_2",
        name = "3 Over 2",
        thumbnailRes = R.drawable.ic_layout_grid_3_over_2,
        slots = listOf(
            CollageTemplate.Slot(0, RectF(0f, 0f, 0.33f, 0.5f)),
            CollageTemplate.Slot(1, RectF(0.33f, 0f, 0.66f, 0.5f)),
            CollageTemplate.Slot(2, RectF(0.66f, 0f, 1f, 0.5f)),
            CollageTemplate.Slot(3, RectF(0f, 0.5f, 0.5f, 1f)),
            CollageTemplate.Slot(4, RectF(0.5f, 0.5f, 1f, 1f))
        )
    )
    
    val FIVE_GRID_2_OVER_3 = CollageTemplate(
        id = "5_grid_2_over_3",
        name = "2 Over 3",
        thumbnailRes = R.drawable.ic_layout_grid_2_over_3,
        slots = listOf(
            CollageTemplate.Slot(0, RectF(0f, 0f, 0.5f, 0.5f)),
            CollageTemplate.Slot(1, RectF(0.5f, 0f, 1f, 0.5f)),
            CollageTemplate.Slot(2, RectF(0f, 0.5f, 0.33f, 1f)),
            CollageTemplate.Slot(3, RectF(0.33f, 0.5f, 0.66f, 1f)),
            CollageTemplate.Slot(4, RectF(0.66f, 0.5f, 1f, 1f))
        )
    )
    
    // 6 Photo Templates
    val GRID_3X2 = CollageTemplate(
        id = "grid_3x2",
        name = "3x2 Grid",
        thumbnailRes = R.drawable.ic_layout_grid_3x2,
        slots = listOf(
            CollageTemplate.Slot(0, RectF(0f, 0f, 0.33f, 0.5f)),
            CollageTemplate.Slot(1, RectF(0.33f, 0f, 0.66f, 0.5f)),
            CollageTemplate.Slot(2, RectF(0.66f, 0f, 1f, 0.5f)),
            CollageTemplate.Slot(3, RectF(0f, 0.5f, 0.33f, 1f)),
            CollageTemplate.Slot(4, RectF(0.33f, 0.5f, 0.66f, 1f)),
            CollageTemplate.Slot(5, RectF(0.66f, 0.5f, 1f, 1f))
        )
    )
    
    val SIX_ROW = CollageTemplate(
        id = "6_row",
        name = "Six Row",
        thumbnailRes = R.drawable.ic_layout_row_5,
        slots = listOf(
            CollageTemplate.Slot(0, RectF(0f, 0f, 1f, 0.167f)),
            CollageTemplate.Slot(1, RectF(0f, 0.167f, 1f, 0.33f)),
            CollageTemplate.Slot(2, RectF(0f, 0.33f, 1f, 0.5f)),
            CollageTemplate.Slot(3, RectF(0f, 0.5f, 1f, 0.66f)),
            CollageTemplate.Slot(4, RectF(0f, 0.66f, 1f, 0.83f)),
            CollageTemplate.Slot(5, RectF(0f, 0.83f, 1f, 1f))
        )
    )
    
    // Get templates by photo count
    fun getTemplatesForPhotoCount(count: Int): List<CollageTemplate> {
        return when (count) {
            2 -> listOf(SPLIT_HORIZONTAL, SPLIT_VERTICAL)
            3 -> listOf(THREE_GRID_1L_2S, THREE_ROW, THREE_COLUMN, THREE_GRID_2S_1L, THREE_GRID_TOP_BOTTOM)
            4 -> listOf(GRID_2X2, FOUR_ROW, FOUR_COLUMN, FOUR_GRID_1L_3S, FOUR_GRID_TOP_BOTTOM, FOUR_GRID_LEFT_RIGHT)
            5 -> listOf(FIVE_GRID_3_OVER_2, FIVE_GRID_2_OVER_3)
            6 -> listOf(GRID_3X2, SIX_ROW)
            else -> listOf(GRID_2X2) // Default fallback
        }
    }
    
    // Get all templates
    fun getAllTemplates(): List<CollageTemplate> {
        return listOf(
            SPLIT_HORIZONTAL, SPLIT_VERTICAL,
            THREE_GRID_1L_2S, THREE_ROW, THREE_COLUMN, THREE_GRID_2S_1L, THREE_GRID_TOP_BOTTOM,
            GRID_2X2, FOUR_ROW, FOUR_COLUMN, FOUR_GRID_1L_3S, FOUR_GRID_TOP_BOTTOM, FOUR_GRID_LEFT_RIGHT,
            FIVE_GRID_3_OVER_2, FIVE_GRID_2_OVER_3,
            GRID_3X2, SIX_ROW
        )
    }
}
