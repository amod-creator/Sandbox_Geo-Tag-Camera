package com.amod.geotagcamera.utils

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.amod.geotagcamera.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.Locale
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object GeminiGpsScanner {
    private const val MODEL_NAME = "gemini-3.5-flash"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL_NAME:generateContent"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(45, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(45, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(45, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    /**
     * Master scan function: Tries free local offline OCR first.
     * Falls back to Gemini Vision API only if coordinates aren't stamped visually on the image.
     */
    suspend fun scanImage(bitmap: Bitmap): GpsScanResult? {
        Log.d("GeminiGpsScanner", "Running free, offline ML Kit OCR scanner...")
        val localResult = scanImageLocally(bitmap)
        if (localResult != null) {
            Log.i("GeminiGpsScanner", "Success: Coordinate text extracted offline using Google ML Kit!")
            return localResult
        }

        Log.i("GeminiGpsScanner", "Offline OCR found no visible coordinate stamps. Falling back to Gemini Vision API...")
        return scanImageWithGemini(bitmap)
    }

    /**
     * Runs 100% Free, Offline Google ML Kit OCR to read printed coordinate text stamped on the image.
     */
    private suspend fun scanImageLocally(bitmap: Bitmap): GpsScanResult? = suspendCancellableCoroutine { continuation ->
        try {
            val recognizer = com.google.mlkit.vision.text.TextRecognition.getClient(
                com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS
            )
            val image = com.google.mlkit.vision.common.InputImage.fromBitmap(bitmap, 0)
            
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val text = visionText.text
                    val result = parseCoordinatesFromText(text)
                    if (continuation.isActive) {
                        continuation.resume(result)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("GeminiGpsScanner", "Local ML Kit OCR failed: ${e.message}")
                    if (continuation.isActive) {
                        continuation.resume(null)
                    }
                }
        } catch (e: Exception) {
            Log.e("GeminiGpsScanner", "Error executing local OCR: ${e.message}")
            if (continuation.isActive) {
                continuation.resume(null)
            }
        }
    }

    /**
     * Parser that scans extracted text for latitude and longitude patterns.
     * Supports both Decimal Coordinates and Degrees/Minutes/Seconds (DMS) stamps.
     */
    fun parseCoordinatesFromText(text: String): GpsScanResult? {
        if (text.isBlank()) return null
        Log.d("GeminiGpsScanner", "Local OCR raw text:\n$text")

        val lines = text.lines()
        var lat: Double? = null
        var lon: Double? = null

        // 1. Matches "Lat/Latitude: 21.170241" and "Long/Longitude: 72.831061"
        val latRegex = Regex("(?i)\\b(?:lat|latitude|lati)[:\\s]*([+-]?\\d{1,3}\\.\\d+)")
        val lonRegex = Regex("(?i)\\b(?:long|lon|longitude|longi)[:\\s]*([+-]?\\d{1,3}\\.\\d+)")

        for (line in lines) {
            val latMatch = latRegex.find(line)
            if (latMatch != null && lat == null) {
                lat = latMatch.groupValues[1].toDoubleOrNull()
            }
            val lonMatch = lonRegex.find(line)
            if (lonMatch != null && lon == null) {
                lon = lonMatch.groupValues[1].toDoubleOrNull()
            }
        }

        // 2. Matches Coordinate pairs without labels, e.g. "21.170241, 72.831061"
        if (lat == null || lon == null) {
            val pairRegex = Regex("([+-]?\\d{1,3}\\.\\d+)\\s*[,\\s]\\s*([+-]?\\d{1,3}\\.\\d+)")
            for (line in lines) {
                val match = pairRegex.find(line)
                if (match != null) {
                    val potentialLat = match.groupValues[1].toDoubleOrNull()
                    val potentialLon = match.groupValues[2].toDoubleOrNull()
                    if (potentialLat != null && potentialLon != null &&
                        potentialLat >= -90.0 && potentialLat <= 90.0 &&
                        potentialLon >= -180.0 && potentialLon <= 180.0) {
                        lat = potentialLat
                        lon = potentialLon
                        break
                    }
                }
            }
        }

        // 3. Matches DMS format e.g., "21° 10' 12.3\" N, 72° 49' 51\" E"
        if (lat == null || lon == null) {
            val dmsRegex = Regex("(\\d{1,3})[°\\sdeg]*(\\d{1,2})['’\\s]*(\\d{1,2}(?:\\.\\d+)?)[\\\"”\\s]*([NnSsEeWw])")
            val dmsMatches = dmsRegex.findAll(text).toList()
            if (dmsMatches.size >= 2) {
                val firstDms = dmsMatches[0]
                val secondDms = dmsMatches[1]
                val firstVal = parseDms(
                    firstDms.groupValues[1].toDoubleOrNull() ?: 0.0,
                    firstDms.groupValues[2].toDoubleOrNull() ?: 0.0,
                    firstDms.groupValues[3].toDoubleOrNull() ?: 0.0,
                    firstDms.groupValues[4]
                )
                val secondVal = parseDms(
                    secondDms.groupValues[1].toDoubleOrNull() ?: 0.0,
                    secondDms.groupValues[2].toDoubleOrNull() ?: 0.0,
                    secondDms.groupValues[3].toDoubleOrNull() ?: 0.0,
                    secondDms.groupValues[4]
                )
                val dir1 = firstDms.groupValues[4].uppercase()
                val dir2 = secondDms.groupValues[4].uppercase()
                if ((dir1 == "N" || dir1 == "S") && (dir2 == "E" || dir2 == "W")) {
                    lat = firstVal
                    lon = secondVal
                } else if ((dir2 == "N" || dir2 == "S") && (dir1 == "E" || dir1 == "W")) {
                    lat = secondVal
                    lon = firstVal
                }
            }
        }

        if (lat != null && lon != null) {
            if (lat >= -90.0 && lat <= 90.0 && lon >= -180.0 && lon <= 180.0) {
                return GpsScanResult(
                    locationName = "Extracted via Free Local OCR",
                    latitude = lat,
                    longitude = lon,
                    confidence = "high",
                    reasoning = "GPS coordinates successfully extracted locally and offline using on-device Google ML Kit OCR."
                )
            }
        }
        return null
    }

    private fun parseDms(degrees: Double, minutes: Double, seconds: Double, direction: String): Double {
        var decimal = degrees + (minutes / 60.0) + (seconds / 3600.0)
        if (direction.equals("S", ignoreCase = true) || direction.equals("W", ignoreCase = true)) {
            decimal = -decimal
        }
        return decimal
    }

    /**
     * Sends the bitmap to Gemini-3.5-Flash to extract coordinates via landmark analysis or remote OCR.
     */
    private suspend fun scanImageWithGemini(bitmap: Bitmap): GpsScanResult? = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank()) {
            Log.e("GeminiGpsScanner", "API key is missing!")
            return@withContext null
        }

        // Compress and encode bitmap to a smaller size to speed up network transmission
        val base64Image = try {
            val outputStream = ByteArrayOutputStream()
            val maxDim = 1024
            val srcWidth = bitmap.width
            val srcHeight = bitmap.height
            val scaledBitmap = if (srcWidth > maxDim || srcHeight > maxDim) {
                val ratio = srcWidth.toFloat() / srcHeight.toFloat()
                val (w, h) = if (ratio > 1) {
                    Pair(maxDim, (maxDim / ratio).toInt())
                } else {
                    Pair((maxDim * ratio).toInt(), maxDim)
                }
                Bitmap.createScaledBitmap(bitmap, w, h, true)
            } else {
                bitmap
            }
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 75, outputStream)
            Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e("GeminiGpsScanner", "Bitmap compression failed: ${e.message}")
            return@withContext null
        }

        val prompt = """
            You are a highly precise Geographic GPS Scanner. Analyze this image.
            
            DIRECTIONS:
            1. Check if there is any printed GPS watermark or text overlay containing latitude and longitude coordinates. If so, perform OCR to extract those coordinates exactly.
            2. If there are no printed watermarks or coordinates, inspect the landmarks, buildings, geography, terrain, street signs, vegetation, or unique architectural style in the image to estimate the location.
            3. Return a valid, minified JSON object matching the following schema exactly (do not output any markdown blocks other than raw JSON):
            
            {
               "location_name": "Name of the place, city, state, country",
               "latitude": 0.0,
               "longitude": 0.0,
               "confidence": "high" | "medium" | "low",
               "reasoning": "Brief explanation of how the location was identified or coordinates extracted"
            }
            
            If you cannot identify the location or any coordinates, make your best guess.
        """.trimIndent()

        try {
            val requestJson = JSONObject()
            val contentsArray = JSONArray()
            val contentObj = JSONObject()
            val partsArray = JSONArray()
            
            val textPart = JSONObject().put("text", prompt)
            val imagePart = JSONObject().put("inlineData", JSONObject()
                .put("mimeType", "image/jpeg")
                .put("data", base64Image)
            )
            
            partsArray.put(textPart)
            partsArray.put(imagePart)
            contentObj.put("parts", partsArray)
            contentsArray.put(contentObj)
            requestJson.put("contents", contentsArray)
            
            val genConfig = JSONObject().put("responseMimeType", "application/json")
            requestJson.put("generationConfig", genConfig)

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = requestJson.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url("$BASE_URL?key=$apiKey")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("GeminiGpsScanner", "API Request failed with code: ${response.code}")
                    return@withContext null
                }

                val responseBodyStr = response.body?.string() ?: return@withContext null
                Log.d("GeminiGpsScanner", "Raw response: $responseBodyStr")
                
                val responseJson = JSONObject(responseBodyStr)
                val candidates = responseJson.getJSONArray("candidates")
                if (candidates.length() > 0) {
                    val firstCandidate = candidates.getJSONObject(0)
                    val content = firstCandidate.getJSONObject("content")
                    val parts = content.getJSONArray("parts")
                    if (parts.length() > 0) {
                        val textResponse = parts.getJSONObject(0).getString("text")
                        Log.d("GeminiGpsScanner", "Parsed JSON text from Gemini: $textResponse")
                        
                        val resultJson = JSONObject(textResponse.trim())
                        val locName = resultJson.optString("location_name", "Unknown Location")
                        val lat = resultJson.optDouble("latitude", 0.0)
                        val lon = resultJson.optDouble("longitude", 0.0)
                        val conf = resultJson.optString("confidence", "low")
                        val reason = resultJson.optString("reasoning", "")
                        
                        return@withContext GpsScanResult(locName, lat, lon, conf, reason)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("GeminiGpsScanner", "Error scanning with Gemini: ${e.message}", e)
        }
        return@withContext null
    }
}

data class GpsScanResult(
    val locationName: String,
    val latitude: Double,
    val longitude: Double,
    val confidence: String,
    val reasoning: String
)
