package com.amod.geotagcamera.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import com.amod.geotagcamera.R
import com.amod.geotagcamera.utils.GpsHistoryDatabaseHelper
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class TimelineActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
        } catch (e: Exception) {
            // ignore
        }
        setContent {
            MaterialTheme {
                TimelineScreen(onBackPressed = { finish() })
            }
        }
    }
}

data class PhotoMarkerItem(
    val path: String,
    val latitude: Double,
    val longitude: Double,
    val dateTaken: Long,
    val title: String = "",
    val thumbBase64: String = ""
)

class MapWebInterface(
    private val onMarkerSelected: (String) -> Unit,
    private val onMapTapped: () -> Unit,
    private val onReady: () -> Unit
) {
    @JavascriptInterface
    fun onMarkerClicked(path: String) {
        onMarkerSelected(path)
    }

    @JavascriptInterface
    fun onMapClicked() {
        onMapTapped()
    }

    @JavascriptInterface
    fun onMapReady() {
        onReady()
    }
}

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(onBackPressed: () -> Unit) {
    val context = LocalContext.current
    var photoMarkers by remember { mutableStateOf<List<PhotoMarkerItem>>(emptyList()) }
    var selectedItem by remember { mutableStateOf<PhotoMarkerItem?>(null) }
    var currentMapTypeName by remember { mutableStateOf("Default") }
    var showMapTypeDialog by remember { mutableStateOf(false) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var isMapEngineReady by remember { mutableStateOf(false) }

    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    // Launcher for Location permissions
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fineGranted || coarseGranted) {
            try {
                fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                    if (loc != null) {
                        webViewInstance?.evaluateJavascript("setCurrentLocation(${loc.latitude}, ${loc.longitude}, true);", null)
                    }
                }
            } catch (e: SecurityException) {
                Log.e("TimelineActivity", "Location error: ${e.message}")
            }
        } else {
            Toast.makeText(context, "Location permission required to center map", Toast.LENGTH_SHORT).show()
        }
    }

    // Load and scan photos in background coroutine
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val scanned = scanPhotosForMap(context)
            withContext(Dispatchers.Main) {
                photoMarkers = scanned
                Log.d("TimelineActivity", "Scanned ${photoMarkers.size} geotagged photos.")
            }
        }
    }

    // Sync markers to WebView whenever map engine is ready or photos finish scanning
    LaunchedEffect(isMapEngineReady, photoMarkers) {
        if (!isMapEngineReady || webViewInstance == null) return@LaunchedEffect
        val wv = webViewInstance ?: return@LaunchedEffect

        val jsonArray = JSONArray()
        photoMarkers.forEach { item ->
            val obj = JSONObject().apply {
                put("path", item.path)
                put("latitude", item.latitude)
                put("longitude", item.longitude)
                put("dateTaken", item.dateTaken)
                put("thumbBase64", item.thumbBase64)
            }
            jsonArray.put(obj)
        }

        wv.post {
            wv.evaluateJavascript("setMarkers($jsonArray);", null)
        }

        // If no photo markers, center on user location if available
        if (photoMarkers.isEmpty()) {
            val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (hasFine || hasCoarse) {
                try {
                    fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                        if (loc != null) {
                            wv.evaluateJavascript("setCurrentLocation(${loc.latitude}, ${loc.longitude}, false);", null)
                        }
                    }
                } catch (e: SecurityException) {
                    // ignore
                }
            }
        }
    }

    // Cleanup WebView on disposal to prevent EGL/Mesa resource leaks
    DisposableEffect(Unit) {
        onDispose {
            webViewInstance?.apply {
                stopLoading()
                removeJavascriptInterface("AndroidBridge")
                clearHistory()
                destroy()
            }
            webViewInstance = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF12161A))
    ) {
        // 1. High-Performance Interactive Map View (Zero Google Maps API Key dependency)
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(android.graphics.Color.parseColor("#12161A"))
                    // Use software layer in container/emulator environments to eliminate Mesa DRI rendernode missing errors
                    try {
                        setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)
                    } catch (e: Exception) {
                        // ignore
                    }
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        allowFileAccess = true
                        allowContentAccess = true
                        cacheMode = WebSettings.LOAD_DEFAULT
                        useWideViewPort = true
                        loadWithOverviewMode = true
                    }
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            isMapEngineReady = true
                        }
                    }
                    addJavascriptInterface(
                        MapWebInterface(
                            onMarkerSelected = { clickedPath ->
                                post {
                                    selectedItem = photoMarkers.find { it.path == clickedPath }
                                }
                            },
                            onMapTapped = {
                                post {
                                    selectedItem = null
                                }
                            },
                            onReady = {
                                post {
                                    isMapEngineReady = true
                                }
                            }
                        ),
                        "AndroidBridge"
                    )
                    loadUrl("file:///android_asset/map/map.html")
                    webViewInstance = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // 2. Transparent Back Button Panel at the Top
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xCC000000),
                            Color(0x66000000),
                            Color.Transparent
                        )
                    )
                )
                .statusBarsPadding()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackPressed,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_chevron_left),
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }
            Text(
                text = "Image on Map",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 2.dp)
            )
        }

        // Empty state banner (subtle overlay if no photos found yet)
        if (photoMarkers.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xDD1A1F24),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 64.dp)
                    .padding(horizontal = 24.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_map_timeline),
                        contentDescription = null,
                        tint = Color(0xFFFFC107),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "No geotagged photos found in gallery yet",
                        color = Color.White,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // 3. Floating Action Buttons on Bottom Right (Map Type & Current Location)
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 16.dp, bottom = if (selectedItem != null) 210.dp else 76.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Button: Select Map Type (Layers)
            Surface(
                onClick = { showMapTypeDialog = true },
                shape = CircleShape,
                color = Color.White,
                shadowElevation = 6.dp,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_map_layers),
                        contentDescription = "Select Map Type",
                        tint = Color(0xFF333333),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Button: Current Location (Crosshair)
            Surface(
                onClick = {
                    val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    if (hasFine || hasCoarse) {
                        try {
                            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                                if (loc != null) {
                                    webViewInstance?.evaluateJavascript("setCurrentLocation(${loc.latitude}, ${loc.longitude}, true);", null)
                                } else {
                                    Toast.makeText(context, "Acquiring current GPS location...", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } catch (e: SecurityException) {
                            locationPermissionLauncher.launch(
                                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                            )
                        }
                    } else {
                        locationPermissionLauncher.launch(
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                        )
                    }
                },
                shape = CircleShape,
                color = Color.White,
                shadowElevation = 6.dp,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_my_location_crosshair),
                        contentDescription = "Current Location",
                        tint = Color(0xFF333333),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // 4. Selected Image Detail Bottom Card (Shifted upwards)
        selectedItem?.let { item ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, bottom = 44.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xEE101418)
                    ),
                    elevation = CardDefaults.cardElevation(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFFFFC107).copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .padding(12.dp)
                                .fillMaxWidth()
                                .height(IntrinsicSize.Max),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Rounded Image thumbnail
                            val thumbnailBitmap = remember(item.path) {
                                try {
                                    val options = BitmapFactory.Options().apply { inSampleSize = 4 }
                                    BitmapFactory.decodeFile(item.path, options)
                                } catch (e: Exception) {
                                    null
                                }
                            }

                            if (thumbnailBitmap != null) {
                                Image(
                                    bitmap = thumbnailBitmap.asImageBitmap(),
                                    contentDescription = "Thumbnail",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(1.dp, Color(0xFFFFC107), RoundedCornerShape(8.dp))
                                        .clickable {
                                            viewFullImage(context, item.path)
                                        }
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.DarkGray)
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 20.dp)
                            ) {
                                val dateStr = SimpleDateFormat("dd MMMM yyyy, hh:mm a", Locale.getDefault()).format(Date(item.dateTaken))
                                Text(
                                    text = dateStr,
                                    color = Color(0xFFFFC107),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Lat: ${String.format(Locale.US, "%.5f", item.latitude)}°",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Long: ${String.format(Locale.US, "%.5f", item.longitude)}°",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.clickable { viewFullImage(context, item.path) },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "View Full Screen Image ↗",
                                        color = Color(0xFF81D4FA),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Close (X) button on top right of the card
                        IconButton(
                            onClick = { selectedItem = null },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .size(28.dp)
                        ) {
                            Text(
                                text = "✕",
                                color = Color.LightGray,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // 5. Map Type Selection Dialog
        if (showMapTypeDialog) {
            AlertDialog(
                onDismissRequest = { showMapTypeDialog = false },
                title = {
                    Text(
                        text = "Select Map Type",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color.White
                    )
                },
                containerColor = Color(0xFF1E242B),
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val types = listOf(
                            "Default" to "default",
                            "Satellite" to "satellite",
                            "Terrain" to "terrain",
                            "Hybrid" to "hybrid"
                        )
                        types.forEach { (displayName, typeKey) ->
                            val isSelected = currentMapTypeName == displayName
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) Color(0x33FFC107) else Color.Transparent)
                                    .clickable {
                                        currentMapTypeName = displayName
                                        webViewInstance?.evaluateJavascript("setMapType('$typeKey');", null)
                                        showMapTypeDialog = false
                                    }
                                    .padding(horizontal = 8.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        currentMapTypeName = displayName
                                        webViewInstance?.evaluateJavascript("setMapType('$typeKey');", null)
                                        showMapTypeDialog = false
                                    },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = Color(0xFFFFC107),
                                        unselectedColor = Color.Gray
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = displayName,
                                    color = if (isSelected) Color(0xFFFFC107) else Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showMapTypeDialog = false }) {
                        Text("Close", color = Color(0xFFFFC107), fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    }
}

/**
 * Scan photo directories and databases to place geotagged thumbnail pins on the map
 */
private fun scanPhotosForMap(context: Context): List<PhotoMarkerItem> {
    val list = mutableListOf<PhotoMarkerItem>()

    // 1. Scan public Pictures/GPS Cam Visit Pro directory
    val picsDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "GPS Cam Visit Pro")
    if (picsDir.exists() && picsDir.isDirectory) {
        val files = picsDir.listFiles { file -> file.extension.lowercase() == "jpg" || file.extension.lowercase() == "jpeg" }
        files?.forEach { file ->
            try {
                val exif = androidx.exifinterface.media.ExifInterface(file.absolutePath)
                val latLong = exif.latLong
                if (latLong != null && latLong[0] != 0.0 && latLong[1] != 0.0) {
                    val base64Thumb = createCompactBase64Thumb(file.absolutePath)
                    list.add(
                        PhotoMarkerItem(
                            path = file.absolutePath,
                            latitude = latLong[0],
                            longitude = latLong[1],
                            dateTaken = file.lastModified(),
                            thumbBase64 = base64Thumb
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e("TimelineActivity", "Error loading EXIF: ${e.message}")
            }
        }
    }

    // 2. Query MediaStore
    try {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DATE_TAKEN
        )
        val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf("%Pictures/GPS Cam Visit Pro%")

        val cursor = context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${MediaStore.Images.Media.DATE_TAKEN} DESC"
        )
        cursor?.use { c ->
            val dataIndex = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            val dateIndex = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            while (c.moveToNext()) {
                val path = c.getString(dataIndex)
                val date = c.getLong(dateIndex)
                if (path != null && list.none { it.path == path }) {
                    val file = File(path)
                    if (file.exists()) {
                        try {
                            val exif = androidx.exifinterface.media.ExifInterface(path)
                            val latLong = exif.latLong
                            if (latLong != null && latLong[0] != 0.0 && latLong[1] != 0.0) {
                                val base64Thumb = createCompactBase64Thumb(path)
                                list.add(
                                    PhotoMarkerItem(
                                        path = path,
                                        latitude = latLong[0],
                                        longitude = latLong[1],
                                        dateTaken = date,
                                        thumbBase64 = base64Thumb
                                    )
                                )
                            }
                        } catch (e: Exception) {
                            Log.e("TimelineActivity", "Error reading EXIF on path: ${e.message}")
                        }
                    }
                }
            }
        }
    } catch (e: Exception) {
        Log.e("TimelineActivity", "Error querying MediaStore: ${e.message}")
    }

    // 3. Scan History Database
    try {
        val historyItems = GpsHistoryDatabaseHelper.historyFlow.value
        historyItems.forEach { history ->
            if (history.latitude != 0.0 && history.longitude != 0.0) {
                val uri = Uri.parse(history.imageUri)
                val path = getFilePathFromUri(context, uri)
                if (path != null && File(path).exists() && list.none { it.path == path }) {
                    val base64Thumb = createCompactBase64Thumb(path)
                    list.add(
                        PhotoMarkerItem(
                            path = path,
                            latitude = history.latitude,
                            longitude = history.longitude,
                            dateTaken = history.timestamp,
                            thumbBase64 = base64Thumb
                        )
                    )
                }
            }
        }
    } catch (e: Exception) {
        Log.e("TimelineActivity", "Error loading history items: ${e.message}")
    }

    return list.distinctBy { it.path }.sortedByDescending { it.dateTaken }
}

private fun createCompactBase64Thumb(path: String): String {
    return try {
        val options = BitmapFactory.Options().apply { inSampleSize = 8 }
        val bmp = BitmapFactory.decodeFile(path, options) ?: return ""
        val scaled = Bitmap.createScaledBitmap(bmp, 88, 88, true)
        val stream = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 70, stream)
        Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    } catch (e: Exception) {
        ""
    }
}

private fun getFilePathFromUri(context: Context, uri: Uri): String? {
    if (uri.scheme == "file") return uri.path
    if (uri.scheme != "content") return null
    val projection = arrayOf(MediaStore.Images.Media.DATA)
    return try {
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                cursor.getString(index)
            } else {
                null
            }
        }
    } catch (e: Exception) {
        null
    }
}

/**
 * Launch an intent to open the full picture viewer for the user
 */
private fun viewFullImage(context: Context, path: String) {
    try {
        val file = File(path)
        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, file)

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "image/jpeg")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Cannot open photo viewer: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
