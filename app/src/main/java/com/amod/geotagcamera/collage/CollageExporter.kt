package com.amod.geotagcamera.collage

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * Handles rendering, saving, sharing, and report generation for collages.
 */
object CollageExporter {

    private const val TAG = "CollageExporter"
    private const val EXPORT_SIZE = 2048 // px for high-quality export
    private const val JPEG_QUALITY = 95

    /**
     * Render the collage to a bitmap.
     */
    fun render(state: CollageState): Bitmap? {
        val ratio = state.ratio.value()
        val w: Int
        val h: Int
        if (ratio >= 1f) {
            w = EXPORT_SIZE
            h = (EXPORT_SIZE / ratio).toInt()
        } else {
            h = EXPORT_SIZE
            w = (EXPORT_SIZE * ratio).toInt()
        }

        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Draw background
        drawBackground(canvas, state.background, w, h)

        // Get layout cells
        val imageCount = state.images.size.coerceIn(2, 6)
        val template = CollageLayoutEngine.getTemplate(imageCount, state.ratio, state.layoutIndex)

        val borderPx = state.border.widthDp * 3f // density-independent approximation for export

        // Draw each cell
        for (i in template.cells.indices) {
            val cell = template.cells[i]
            val img = if (i < state.images.size) state.images[i] else null
            img ?: continue

            val rect = cell.toRectF(w.toFloat(), h.toFloat())
            // Inset by border
            val insetRect = RectF(
                rect.left + borderPx / 2,
                rect.top + borderPx / 2,
                rect.right - borderPx / 2,
                rect.bottom - borderPx / 2
            )

            // Draw image scaled to fit cell
            drawImageInCell(canvas, img, insetRect)

            // Draw border
            if (state.border != CollageBorder.NONE && borderPx > 0) {
                drawBorder(canvas, rect, state.border, borderPx)
            }
        }

        return bitmap
    }

    /**
     * Render a preview bitmap at lower resolution.
     */
    fun renderPreview(state: CollageState, previewSize: Int = 800): Bitmap? {
        val ratio = state.ratio.value()
        val w: Int
        val h: Int
        if (ratio >= 1f) {
            w = previewSize
            h = (previewSize / ratio).toInt()
        } else {
            h = previewSize
            w = (previewSize * ratio).toInt()
        }

        val bitmap = Bitmap.createBitmap(w.coerceAtLeast(1), h.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        drawBackground(canvas, state.background, w, h)

        val imageCount = state.images.size.coerceIn(2, 6)
        val template = CollageLayoutEngine.getTemplate(imageCount, state.ratio, state.layoutIndex)

        val borderPx = state.border.widthDp * 2f

        for (i in template.cells.indices) {
            val cell = template.cells[i]
            val img = if (i < state.images.size) state.images[i] else null
            img ?: continue

            val rect = cell.toRectF(w.toFloat(), h.toFloat())
            val insetRect = RectF(
                rect.left + borderPx / 2,
                rect.top + borderPx / 2,
                rect.right - borderPx / 2,
                rect.bottom - borderPx / 2
            )

            drawImageInCell(canvas, img, insetRect)

            if (state.border != CollageBorder.NONE && borderPx > 0) {
                drawBorder(canvas, rect, state.border, borderPx)
            }
        }

        return bitmap
    }

    private fun drawBackground(canvas: Canvas, bg: CollageBg, w: Int, h: Int) {
        if (bg.colors.size == 1) {
            canvas.drawColor(bg.colors[0])
        } else {
            val gradient = LinearGradient(
                0f, 0f, w.toFloat(), h.toFloat(),
                bg.colors[0], bg.colors[1],
                Shader.TileMode.CLAMP
            )
            val paint = Paint().apply { shader = gradient }
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        }
    }

    private fun drawImageInCell(canvas: Canvas, img: Bitmap, rect: RectF) {
        if (rect.width() <= 0 || rect.height() <= 0) return

        val srcRatio = img.width.toFloat() / img.height.toFloat()
        val dstRatio = rect.width() / rect.height()

        val srcRect: Rect = if (srcRatio > dstRatio) {
            // Image is wider: crop sides
            val newW = (img.height * dstRatio).toInt()
            val offset = (img.width - newW) / 2
            Rect(offset, 0, offset + newW, img.height)
        } else {
            // Image is taller: crop top/bottom
            val newH = (img.width / dstRatio).toInt()
            val offset = (img.height - newH) / 2
            Rect(0, offset, img.width, offset + newH)
        }

        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        canvas.drawBitmap(img, srcRect, rect, paint)
    }

    private fun drawBorder(canvas: Canvas, rect: RectF, border: CollageBorder, borderPx: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = border.color
            strokeWidth = borderPx
            style = Paint.Style.STROKE
            if (border.isDashed) {
                pathEffect = DashPathEffect(floatArrayOf(borderPx * 3, borderPx * 2), 0f)
            } else if (border.isDotted) {
                pathEffect = DashPathEffect(floatArrayOf(borderPx, borderPx * 2), 0f)
            }
        }
        canvas.drawRect(rect, paint)
    }

    /**
     * Save collage to gallery.
     */
    fun save(context: Context, state: CollageState, onComplete: (Uri?) -> Unit) {
        val bitmap = render(state) ?: run { onComplete(null); return }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "Collage_$timestamp.jpg"

        try {
            val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/GPS Cam Visit Pro/Collages")
                }
                val insertUri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                insertUri?.let { uri ->
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                    }
                }
                insertUri
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "GPS Cam Visit Pro/Collages")
                dir.mkdirs()
                val file = File(dir, fileName)
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                }
                Uri.fromFile(file)
            }
            onComplete(uri)
        } catch (e: Exception) {
            Log.e(TAG, "Save failed: ${e.message}")
            onComplete(null)
        }
    }

    /**
     * Share collage via intent.
     */
    fun share(context: Context, state: CollageState, onComplete: (Boolean) -> Unit) {
        val bitmap = render(state) ?: run { onComplete(false); return }
        try {
            val cacheDir = File(context.cacheDir, "collage_share")
            cacheDir.mkdirs()
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val file = File(cacheDir, "Collage_$timestamp.jpg")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share Collage"))
            onComplete(true)
        } catch (e: Exception) {
            Log.e(TAG, "Share failed: ${e.message}")
            onComplete(false)
        }
    }

    /**
     * Generate a JSON report with collage metadata.
     */
    fun generateReport(context: Context, state: CollageState, onComplete: (Uri?) -> Unit) {
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val report = JSONObject().apply {
                put("type", "Collage Report")
                put("timestamp", SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault()).format(Date()))
                put("imageCount", state.images.size)
                put("layout", CollageLayoutEngine.getTemplate(state.images.size.coerceIn(2, 6), state.ratio, state.layoutIndex).name)
                put("ratio", state.ratio.label)
                put("border", state.border.label)
                put("background", state.background.label)
                put("images", JSONArray().apply {
                    state.images.forEachIndexed { i, img ->
                        put(JSONObject().apply {
                            put("index", i)
                            put("width", img?.width ?: 0)
                            put("height", img?.height ?: 0)
                        })
                    }
                })
            }

            val cacheDir = File(context.cacheDir, "collage_reports")
            cacheDir.mkdirs()
            val file = File(cacheDir, "CollageReport_$timestamp.json")
            file.writeText(report.toString(2))

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share Report"))
            onComplete(uri)
        } catch (e: Exception) {
            Log.e(TAG, "Report failed: ${e.message}")
            onComplete(null)
        }
    }
}
