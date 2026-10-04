package com.amod.geotagcamera.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import com.amod.geotagcamera.utils.ExifGpsExtractor
import com.amod.geotagcamera.utils.GeminiGpsScanner
import com.amod.geotagcamera.utils.GpsScanResult
import com.amod.geotagcamera.utils.GpsHistoryDatabaseHelper
import com.amod.geotagcamera.utils.GpsHistoryItem
import kotlinx.coroutines.launch
import java.io.File

class GpsLookupActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF4CAF50),
                    secondary = Color(0xFF81C784),
                    background = Color(0xFF121212),
                    surface = Color(0xFF1E1E1E),
                    onPrimary = Color.White,
                    onSecondary = Color.Black,
                    onBackground = Color(0xFFE0E0E0),
                    onSurface = Color(0xFFE0E0E0)
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    GpsLookupScreen(onBack = { finish() })
                }
            }
        }
    }
}

sealed class ScanState {
    object Idle : ScanState()
    data class Loading(val message: String) : ScanState()
    data class Success(
        val imageUri: Uri,
        val latitude: Double,
        val longitude: Double,
        val locationName: String,
        val confidence: String,
        val reasoning: String,
        val source: String // "EXIF Header" or "Gemini Vision AI"
    ) : ScanState()
    data class Error(val message: String) : ScanState()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GpsLookupScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var scanState by remember { mutableStateOf<ScanState>(ScanState.Idle) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }

    // Read saved log history reactively from SQLite
    val database = remember { GpsHistoryDatabaseHelper.getInstance(context) }
    val historyItems by GpsHistoryDatabaseHelper.historyFlow.collectAsState(initial = emptyList())

    // Setup Gallery Picker (Modern Photo Picker)
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedImageUri = uri
            processImage(context, uri, coroutineScope) { state -> scanState = state }
        }
    }

    // Setup Camera Capture Launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            val uri = tempCameraUri
            if (uri != null) {
                selectedImageUri = uri
                processImage(context, uri, coroutineScope) { state -> scanState = state }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("GPS Photo Scanner", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Helper Info Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Info",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Scan or upload any photo. The app will extract coordinates using EXIF metadata or utilize Gemini AI to recognize landmarks and watermarks.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }
            }

            // Image Preview Container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF262626))
                    .border(1.dp, Color(0xFF3C3C3C), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (selectedImageUri != null) {
                    AsyncImage(
                        model = selectedImageUri,
                        contentDescription = "Preview Image",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "Place holder",
                            tint = Color.Gray,
                            modifier = Modifier.size(56.dp)
                        )
                        Text(
                            text = "No image selected",
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // Action Selection Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        try {
                            val tempFile = File.createTempFile("scan_capture_", ".jpg", context.cacheDir).apply {
                                createNewFile()
                                deleteOnExit()
                            }
                            val providerUri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                tempFile
                            )
                            tempCameraUri = providerUri
                            cameraLauncher.launch(providerUri)
                        } catch (e: Exception) {
                            Log.e("GpsLookup", "Failed to setup camera intent: ${e.message}")
                            Toast.makeText(context, "Failed to initialize camera", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("scan_camera_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Camera", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "Scan with\nCamera",
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.5.sp,
                            lineHeight = 11.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            softWrap = true
                        )
                    }
                }

                Button(
                    onClick = {
                        galleryLauncher.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("upload_gallery_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E2E2E)),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = "Gallery", tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "Upload\nPhoto",
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.5.sp,
                            lineHeight = 11.sp,
                            textAlign = TextAlign.Center,
                            color = Color.White,
                            maxLines = 2,
                            softWrap = true
                        )
                    }
                }
            }

            // Dynamic State Displays
            AnimatedVisibility(
                visible = scanState is ScanState.Loading,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                if (scanState is ScanState.Loading) {
                    val loadingState = scanState as ScanState.Loading
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Text(
                            text = loadingState.message,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = scanState is ScanState.Error,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                if (scanState is ScanState.Error) {
                    val errorState = scanState as ScanState.Error
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF5A1A1A)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Processing Error", fontWeight = FontWeight.Bold, color = Color.White)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(errorState.message, fontSize = 13.sp, color = Color.White.copy(alpha = 0.9f))
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = scanState is ScanState.Success,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                if (scanState is ScanState.Success) {
                    val success = scanState as ScanState.Success
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFF333333), RoundedCornerShape(16.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "SCAN SUCCESSFUL",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    letterSpacing = 1.sp
                                )
                                
                                // Source & Confidence Tag
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                color = Color(0xFF232F34),
                                                shape = CircleShape
                                            )
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(success.source, fontSize = 10.sp, color = Color.LightGray)
                                    }
                                    
                                    val confColor = when(success.confidence.lowercase()) {
                                        "high" -> Color(0xFF4CAF50)
                                        "medium" -> Color(0xFFFFEB3B)
                                        else -> Color(0xFFFF5722)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                color = confColor.copy(alpha = 0.15f),
                                                shape = CircleShape
                                            )
                                            .border(1.dp, confColor, CircleShape)
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "Confidence: ${success.confidence.uppercase()}",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = confColor
                                        )
                                    }
                                }
                            }

                            Divider(color = Color(0xFF2B2B2B))

                            // Resolved Name
                            Text(
                                text = success.locationName,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )

                            // Coordinate Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("LATITUDE", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = "%.6f".format(success.latitude),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.White
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("LONGITUDE", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = "%.6f".format(success.longitude),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.White
                                    )
                                }
                            }

                            // Reasoning Field
                            if (success.reasoning.isNotBlank()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF222222), RoundedCornerShape(8.dp))
                                        .padding(10.dp)
                                ) {
                                    Text(
                                        text = "DETECTION REASONING",
                                        fontSize = 9.sp,
                                        color = Color.Gray,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = success.reasoning,
                                        fontSize = 12.sp,
                                        color = Color.LightGray
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Map Trigger Button
                            Button(
                                onClick = {
                                    launchGoogleMaps(context, success.latitude, success.longitude, success.locationName)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("open_maps_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(imageVector = Icons.Default.LocationOn, contentDescription = "Navigate")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Navigate on Google Maps", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Saved Scan History Log Section (Material 3 List)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Saved History (${historyItems.size})",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                if (historyItems.isNotEmpty()) {
                    TextButton(
                        onClick = { database.clearAllHistory() },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFFF5722))
                    ) {
                        Text("Clear All", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (historyItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No saved locations yet.",
                        fontSize = 13.sp,
                        color = Color.Gray
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    for (item in historyItems) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0xFF2C2C2C), RoundedCornerShape(12.dp))
                                .clickable {
                                    // Instantly load this record to the active preview card
                                    selectedImageUri = Uri.parse(item.imageUri)
                                    scanState = ScanState.Success(
                                        imageUri = Uri.parse(item.imageUri),
                                        latitude = item.latitude,
                                        longitude = item.longitude,
                                        locationName = item.locationName,
                                        confidence = item.confidence,
                                        reasoning = item.reasoning,
                                        source = item.source
                                    )
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Thumbnail
                                AsyncImage(
                                    model = item.imageUri,
                                    contentDescription = "Saved Image",
                                    modifier = Modifier
                                        .size(50.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF2B2B2B)),
                                    contentScale = ContentScale.Crop
                                )

                                // Details Column
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = item.locationName,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        // Simple tiny source badge
                                        Box(
                                            modifier = Modifier
                                                .background(
                                                    color = Color(0xFF2B2B2B),
                                                    shape = RoundedCornerShape(4.dp)
                                                )
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = if (item.source.contains("EXIF")) "EXIF" else "AI",
                                                fontSize = 8.sp,
                                                color = Color.LightGray,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Lat: %.5f, Lon: %.5f".format(item.latitude, item.longitude),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                // Delete Button
                                IconButton(
                                    onClick = { database.deleteHistoryItem(item.id) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Item",
                                        tint = Color(0xFFE57373),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Handles the async scanning execution sequentially:
 * Engine A (Local EXIF) -> Engine B (Gemini AI Vision)
 */
private fun processImage(
    context: Context,
    uri: Uri,
    scope: kotlinx.coroutines.CoroutineScope,
    onStateUpdate: (ScanState) -> Unit
) {
    onStateUpdate(ScanState.Loading("Reading EXIF Geotag headers locally..."))
    
    scope.launch {
        // Copy the image permanently to local app sandbox to prevent temporary Uri permission loss
        val savedLocalUri = saveImageToLocalAppDir(context, uri) ?: uri

        // Try local EXIF extraction
        val exifResult = ExifGpsExtractor.extractGps(context, uri)
        if (exifResult != null) {
            onStateUpdate(
                ScanState.Success(
                    imageUri = savedLocalUri,
                    latitude = exifResult.first,
                    longitude = exifResult.second,
                    locationName = "Extracted Geotag Location",
                    confidence = "high",
                    reasoning = "GPS coordinates extracted directly from native digital EXIF headers.",
                    source = "EXIF Header"
                )
            )
            
            // Automatically insert into History Database
            try {
                val db = GpsHistoryDatabaseHelper.getInstance(context)
                db.addHistoryItem(
                    GpsHistoryItem(
                        imageUri = savedLocalUri.toString(),
                        latitude = exifResult.first,
                        longitude = exifResult.second,
                        locationName = "Extracted Geotag Location",
                        confidence = "high",
                        reasoning = "GPS coordinates extracted directly from native digital EXIF headers.",
                        source = "EXIF Header"
                    )
                )
            } catch (e: Exception) {
                Log.e("GpsLookup", "Failed saving EXIF scan to history: ${e.message}")
            }
            return@launch
        }

        // Fallback to Free On-Device OCR Scanner with Gemini Fallback
        onStateUpdate(ScanState.Loading("No local EXIF tag found.\nRunning Free Offline OCR Scanner..."))
        
        try {
            val bitmap = loadUriToBitmap(context, uri)
            if (bitmap == null) {
                onStateUpdate(ScanState.Error("Failed to decode image from storage."))
                return@launch
            }

            val geminiResult = GeminiGpsScanner.scanImage(bitmap)
            if (geminiResult != null && geminiResult.latitude != 0.0 && geminiResult.longitude != 0.0) {
                val sourceName = if (geminiResult.locationName.contains("Free Local OCR")) "Free Local OCR" else "Gemini Vision AI"
                onStateUpdate(
                    ScanState.Success(
                        imageUri = savedLocalUri,
                        latitude = geminiResult.latitude,
                        longitude = geminiResult.longitude,
                        locationName = geminiResult.locationName,
                        confidence = geminiResult.confidence,
                        reasoning = geminiResult.reasoning,
                        source = sourceName
                    )
                )
                
                // Automatically insert into History Database
                try {
                    val db = GpsHistoryDatabaseHelper.getInstance(context)
                    db.addHistoryItem(
                        GpsHistoryItem(
                            imageUri = savedLocalUri.toString(),
                            latitude = geminiResult.latitude,
                            longitude = geminiResult.longitude,
                            locationName = geminiResult.locationName,
                            confidence = geminiResult.confidence,
                            reasoning = geminiResult.reasoning,
                            source = sourceName
                        )
                    )
                } catch (e: Exception) {
                    Log.e("GpsLookup", "Failed saving scan to history: ${e.message}")
                }
            } else {
                onStateUpdate(ScanState.Error("On-Device OCR and Gemini AI were unable to recognize any landmark, coordinates, or watermarks in this image. Please try a different photo."))
            }
        } catch (e: Exception) {
            Log.e("GpsLookup", "Failed processing image with Gemini: ${e.message}", e)
            onStateUpdate(ScanState.Error("An unexpected API error occurred: ${e.message}"))
        }
    }
}

private fun loadUriToBitmap(context: Context, uri: Uri): Bitmap? {
    return try {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BitmapFactory.decodeStream(inputStream)
        }
    } catch (e: Exception) {
        Log.e("GpsLookup", "Error decoding uri to bitmap: ${e.message}")
        null
    }
}

private fun launchGoogleMaps(context: Context, latitude: Double, longitude: Double, label: String) {
    try {
        val query = Uri.encode(label)
        val gmmIntentUri = Uri.parse("geo:0,0?q=$latitude,$longitude($query)")
        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
            setPackage("com.google.android.apps.maps")
        }
        
        // Use implicit intent fallback if Google Maps is not installed
        if (mapIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(mapIntent)
        } else {
            val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=$latitude,$longitude"))
            context.startActivity(fallbackIntent)
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Could not open Google Maps: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

private fun saveImageToLocalAppDir(context: Context, uri: Uri): Uri? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val historyDir = File(context.filesDir, "scan_history_images").apply { mkdirs() }
        val targetFile = File(historyDir, "scan_${System.currentTimeMillis()}.jpg")
        targetFile.outputStream().use { outputStream ->
            inputStream.copyTo(outputStream)
        }
        Uri.fromFile(targetFile)
    } catch (e: Exception) {
        Log.e("GpsLookup", "Failed copying image to local storage: ${e.message}")
        null
    }
}
