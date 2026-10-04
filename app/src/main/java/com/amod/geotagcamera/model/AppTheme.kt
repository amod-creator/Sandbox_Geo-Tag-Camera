package com.amod.geotagcamera.model

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

enum class AppTheme(val id: String, val displayName: String, val mode: Int) {
    SYSTEM("system", "System Default", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
    LIGHT("light", "Light Mode", AppCompatDelegate.MODE_NIGHT_NO),
    DARK("dark", "Dark Mode", AppCompatDelegate.MODE_NIGHT_YES);

    companion object {
        private const val PREFS_NAME = "app_prefs"
        private const val KEY_THEME = "selected_app_theme"

        fun getSelectedTheme(context: Context): AppTheme {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val id = prefs.getString(KEY_THEME, SYSTEM.id) ?: SYSTEM.id
            return values().firstOrNull { it.id == id } ?: SYSTEM
        }

        fun setSelectedTheme(context: Context, theme: AppTheme) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_THEME, theme.id)
                .apply()
            AppCompatDelegate.setDefaultNightMode(theme.mode)
        }

        fun applyTheme(context: Context) {
            val theme = getSelectedTheme(context)
            AppCompatDelegate.setDefaultNightMode(theme.mode)
        }
    }
}
