package com.amod.geotagcamera.model

import android.content.Context

enum class MapType(
    val id: String,
    val displayName: String,
    val description: String,
    val googleMapType: String,
    val defaultZoom: Int
) {
    TERRAIN("terrain", "Terrain", "Topographic relief map with physical contours and terrain", "terrain", 16),
    NORMAL("normal", "Normal", "Standard roadmap with street layouts", "roadmap", 17),
    HYBRID("hybrid", "Satellite / Hybrid", "High-resolution satellite imagery with street overlays", "hybrid", 17);

    companion object {
        private const val PREFS_NAME = "com.amod.geotagcamera.PREFERENCES"
        private const val KEY_MAP_TYPE = "key_selected_map_type"

        fun fromId(id: String?): MapType {
            return values().firstOrNull { it.id.equals(id, ignoreCase = true) } ?: TERRAIN
        }

        fun getSelectedMapType(context: Context): MapType {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val id = prefs.getString(KEY_MAP_TYPE, TERRAIN.id)
            return fromId(id)
        }

        fun setSelectedMapType(context: Context, mapType: MapType) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_MAP_TYPE, mapType.id).apply()
        }
    }
}
