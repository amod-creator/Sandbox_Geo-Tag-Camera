import java.util.Properties

plugins {
    id("com.android.application")
    kotlin("android")
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.amod.geotagcamera"
    compileSdk = 36

    signingConfigs {
        create("debugConfig") {
            storeFile = file("${rootDir}/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    defaultConfig {
        applicationId = "com.aistudio.geotagcamera.fkslae"
        minSdk = 24
        targetSdk = 36
        versionCode = 13
        versionName = "12.1"
        vectorDrawables.useSupportLibrary = true
        ndk {
            abiFilters.add("arm64-v8a")
        }

        // Read API key from environment, local.properties, or working default
        val localPropertiesFile = rootProject.file("local.properties")
        val localProperties = Properties()
        if (localPropertiesFile.exists()) {
            localProperties.load(localPropertiesFile.inputStream())
        }
                val mapsApiKey = System.getenv("MAPS_API_KEY") 
            ?: localProperties.getProperty("MAPS_API_KEY") 
            ?: "AIzaSyDBr0XfggiNMjgaqZXwJg4lDP-X9fHBtXY"
        buildConfigField("String", "MAPS_API_KEY", "\"$mapsApiKey\"")
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
        
        val geminiApiKey = System.getenv("GEMINI_API_KEY") ?: localProperties.getProperty("GEMINI_API_KEY") ?: "AIzaSyAhAK6CwMuzhbyBjeLdAHR9Chf9NKR0yS8"
        buildConfigField("String", "GEMINI_API_KEY", "\"$geminiApiKey\"")
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debugConfig")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // Generate native debug symbol tables for release builds
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Configure App Bundle for native symbol packaging
    bundle {
        language {
            enableSplit = true
        }
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
        compose = true
        buildConfig = true  // Enable BuildConfig generation
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

dependencies {
    // Core and AppCompat
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation("com.squareup.okio:okio-jvm:3.13.0")
    implementation("androidx.activity:activity-ktx:1.8.2") // Added for OnBackPressedDispatcher
    // ConstraintLayout for positioning overlay
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // CameraX – ensure camera-view is present for ImplementationMode
    implementation("androidx.camera:camera-core:1.4.2")
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-view:1.4.2")
    implementation("androidx.camera:camera-video:1.4.2") // Video recording
    implementation("androidx.camera:camera-effects:1.4.2") // Overlays for video

    // Media3 Transformer for video post-processing
    val media3Version = "1.3.1"
    implementation("androidx.media3:media3-transformer:$media3Version")
    implementation("androidx.media3:media3-effect:$media3Version")
    implementation("androidx.media3:media3-common:$media3Version")

    // Google Location Services
    implementation("com.google.android.gms:play-services-location:21.0.1")
    implementation("com.google.android.gms:play-services-maps:19.0.0")

    // Google Play Services ML Kit Text Recognition for free local offline OCR (Bundled version to prevent dynamic download failures)
    implementation("com.google.mlkit:text-recognition:16.0.0")

    // QR Code generation for Scan Location Template
    implementation("com.google.zxing:core:3.5.3")

    // Jetpack Compose
    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Coil for image loading
    implementation("io.coil-kt:coil-compose:2.5.0")

    // Accompanist Permissions
    implementation("com.google.accompanist:accompanist-permissions:0.30.1")
    implementation("androidx.exifinterface:exifinterface:1.3.6")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}

configurations.all {
    resolutionStrategy.force(
        "com.squareup.okio:okio-jvm:3.6.0"  // Keep this if it helps with itext/okhttp conflicts
    )
}
