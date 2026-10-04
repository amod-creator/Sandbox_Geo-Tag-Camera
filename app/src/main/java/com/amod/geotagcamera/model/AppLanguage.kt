package com.amod.geotagcamera.model

import android.content.Context
import java.util.Locale

enum class AppLanguage(
    val code: String,
    val displayName: String,
    val nativeName: String,
    val latLabel: String,
    val lonLabel: String,
    val altLabel: String,
    val azimuthLabel: String,
    val checkInLabel: String,
    val landscapeLabel: String,
    val portraitLabel: String,
    val badgeLabel: String
) {
    ENGLISH(
        "en", "English", "English",
        "Lat", "Lon", "Alt", "Azimuth/Bearing", "Check In",
        "Landscape Mode", "Portrait Mode", "Ads Free GPS Cam Visit Pro"
    ),
    SPANISH(
        "es", "Spanish", "Español",
        "Lat", "Lon", "Alt", "Azimut/Rumbo", "Registro",
        "Landscape Mode", "Portrait Mode", "Ads Free GPS Cam Visit Pro"
    ),
    FRENCH(
        "fr", "French", "Français",
        "Lat", "Long", "Alt", "Azimut/Relèvement", "Enregistrement",
        "Landscape Mode", "Portrait Mode", "Ads Free GPS Cam Visit Pro"
    ),
    GERMAN(
        "de", "German", "Deutsch",
        "Breite", "Länge", "Höhe", "Azimut/Peilung", "Einchecken",
        "Landscape Mode", "Portrait Mode", "Ads Free GPS Cam Visit Pro"
    ),
    HINDI(
        "hi", "Hindi", "हिन्दी",
        "अक्षांश", "देशांतर", "ऊंचाई", "दिगंश/दिशा", "चेक इन",
        "Landscape Mode", "Portrait Mode", "Ads Free GPS Cam Visit Pro"
    ),
    ARABIC(
        "ar", "Arabic", "العربية",
        "خط عرض", "خط طول", "ارتفاع", "السمت/الاتجاه", "تسجيل الوصول",
        "Landscape Mode", "Portrait Mode", "Ads Free GPS Cam Visit Pro"
    ),
    PORTUGUESE(
        "pt", "Portuguese", "Português",
        "Lat", "Long", "Alt", "Azimute/Rumo", "Check-in",
        "Landscape Mode", "Portrait Mode", "Ads Free GPS Cam Visit Pro"
    ),
    RUSSIAN(
        "ru", "Russian", "Русский",
        "Шир", "Долг", "Выс", "Азимут/Направление", "Отметка",
        "Landscape Mode", "Portrait Mode", "Ads Free GPS Cam Visit Pro"
    ),
    JAPANESE(
        "ja", "Japanese", "日本語",
        "緯度", "経度", "標高", "方位角/方位", "チェックイン",
        "Landscape Mode", "Portrait Mode", "Ads Free GPS Cam Visit Pro"
    ),
    CHINESE(
        "zh", "Chinese", "中文",
        "纬度", "经度", "海拔", "方位角/方位", "签到",
        "Landscape Mode", "Portrait Mode", "Ads Free GPS Cam Visit Pro"
    ),
    ITALIAN(
        "it", "Italian", "Italiano",
        "Lat", "Long", "Alt", "Azimut/Rilevamento", "Registrati",
        "Landscape Mode", "Portrait Mode", "Ads Free GPS Cam Visit Pro"
    );

    val locale: Locale
        get() = Locale(code)

    companion object {
        private const val PREFS_NAME = "app_prefs"
        private const val KEY_LANGUAGE = "selected_language_code"

        fun getSelectedLanguage(context: Context): AppLanguage {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val code = prefs.getString(KEY_LANGUAGE, ENGLISH.code) ?: ENGLISH.code
            return values().firstOrNull { it.code == code } ?: ENGLISH
        }

        fun setSelectedLanguage(context: Context, language: AppLanguage) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_LANGUAGE, language.code)
                .apply()
        }
    }
}
