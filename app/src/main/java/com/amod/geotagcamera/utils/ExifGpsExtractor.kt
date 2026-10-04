package com.amod.geotagcamera.utils

import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import android.util.Log

object ExifGpsExtractor {
    /**
     * Extracts coordinates from an image Uri using ExifInterface.
     * Returns a Pair of (Latitude, Longitude) if successful, null otherwise.
     */
    fun extractGps(context: Context, uri: Uri): Pair<Double, Double>? {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val exif = ExifInterface(inputStream)
                val latLong = FloatArray(2)
                if (exif.getLatLong(latLong)) {
                    val lat = latLong[0].toDouble()
                    val lon = latLong[1].toDouble()
                    if (lat != 0.0 || lon != 0.0) {
                        return Pair(lat, lon)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ExifGpsExtractor", "Error extracting EXIF GPS: ${e.message}", e)
        }
        return null
    }
}
