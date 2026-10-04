package com.amod.geotagcamera

import android.app.Application
import androidx.camera.camera2.Camera2Config
import androidx.camera.core.CameraXConfig
import com.amod.geotagcamera.model.AppTheme

class GeoTagApp : Application(), CameraXConfig.Provider {
    override fun onCreate() {
        super.onCreate()
        AppTheme.applyTheme(this)
    }

    override fun getCameraXConfig(): CameraXConfig {
        return CameraXConfig.Builder.fromConfig(Camera2Config.defaultConfig())
            .setMinimumLoggingLevel(android.util.Log.WARN)
            .build()
    }
}
