package com.amod.geotagcamera.utils

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.OverlaySettings
import androidx.media3.common.Effect
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.google.common.collect.ImmutableList
import androidx.media3.common.util.UnstableApi
import java.io.File
import java.util.concurrent.Executors

@androidx.annotation.OptIn(UnstableApi::class)
class VideoPostProcessor(private val context: Context) {

    interface Callback {
        fun onProcessingStarted()
        fun onProcessingFinished(outputFile: File)
        fun onProcessingFailed(error: String)
    }

    fun processVideoWithGpsOverlay(
        inputUri: Uri,
        mapThumbnail: Bitmap?,
        latitude: String,
        longitude: String,
        address: String,
        datetime: String,
        callback: Callback
    ) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, inputUri)
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toInt() ?: 1280
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toInt() ?: 720
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toInt() ?: 0
            
            // 1. Generate the upright overlay bitmap matching the final visible portrait/landscape orientation
            val finalWidth = if (rotation == 90 || rotation == 270) height else width
            val finalHeight = if (rotation == 90 || rotation == 270) width else height

            val renderer = GpsOverlayRenderer(context)
            val overlayBitmap = renderer.drawOnlyOverlay(
                finalWidth, finalHeight, mapThumbnail, latitude, longitude, address, datetime
            )

            // 2. Setup Media3 Transformer
            val outputFileName = "WATERMARKED_" + System.currentTimeMillis() + ".mp4"
            val outputFile = File(context.cacheDir, outputFileName)
            
            val transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .build()

            val bitmapOverlay = BitmapOverlay.createStaticBitmapOverlay(overlayBitmap)
            val overlayEffect = OverlayEffect(ImmutableList.of(bitmapOverlay))
            
            val editedMediaItem = EditedMediaItem.Builder(MediaItem.fromUri(inputUri))
                .setEffects(Effects(ImmutableList.of(), ImmutableList.of(overlayEffect as Effect)))
                .build()

            callback.onProcessingStarted()

            transformer.addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    overlayBitmap.recycle()
                    callback.onProcessingFinished(outputFile)
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    overlayBitmap.recycle()
                    callback.onProcessingFailed(exportException.message ?: "Unknown error")
                }
            })

            transformer.start(editedMediaItem, outputFile.absolutePath)

        } catch (e: Exception) {
            callback.onProcessingFailed(e.message ?: "Initialization error")
        } finally {
            retriever.release()
        }
    }
}
