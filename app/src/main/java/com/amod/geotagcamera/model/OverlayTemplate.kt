package com.amod.geotagcamera.model

import android.content.Context

enum class OverlayTemplate(val id: String, val displayName: String) {
    DATETIME("datetime", "DateTime Template"),
    SCAN_LOCATION("scan_location", "Scan Location Template"),
    CLASSIC("classic", "Classic Template"),
    REPORTING("reporting", "Reporting Template"),
    NAVIGATION_COMPASS("navigation_compass", "Navigation Compass Template"),
    LOCATION_WATERMARK("location_watermark", "Location Watermark");

    companion object {
        private const val PREFS_NAME = "com.amod.geotagcamera.PREFERENCES"
        private const val KEY_SELECTED_TEMPLATE = "selected_overlay_template"

        fun fromId(id: String?): OverlayTemplate {
            if (id.equals("advance", ignoreCase = true)) return CLASSIC
            return values().firstOrNull { it.id.equals(id, ignoreCase = true) } ?: SCAN_LOCATION
        }

        fun getSelectedTemplate(context: Context): OverlayTemplate {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val savedId = prefs.getString(KEY_SELECTED_TEMPLATE, SCAN_LOCATION.id)
            return fromId(savedId)
        }

        fun setSelectedTemplate(context: Context, template: OverlayTemplate) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_SELECTED_TEMPLATE, template.id).apply()
        }
    }
}
