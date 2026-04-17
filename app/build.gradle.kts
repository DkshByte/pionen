import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    kotlin("kapt")
}

// Load signing credentials from keystore.properties (git-ignored).
// Falls back to environment variables for CI/CD.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) load(keystorePropertiesFile.inputStream())
}

android {
    namespace = "com.pionen.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pionen.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }
    
    // Release signing — reads from keystore.properties (local, git-ignored).
    // For CI, set KEYSTORE_FILE / KEYSTORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD
    // environment variables as GitHub Actions secrets. See .github/workflows/ci.yml.
    signingConfigs {
        create("release") {
            // Only configure when a keystore is actually available.
            // storeFile MUST be set before any other property — setting it to null
            // is what triggers the "missing storeFile" build error.
            val localStoreFile = keystoreProperties.getProperty("storeFile")
                ?.let { file(it) }
                ?: System.getenv("KEYSTORE_FILE")?.let { file(it) }

            if (localStoreFile != null) {
                storeFile     = localStoreFile
                storePassword = keystoreProperties.getProperty("storePassword")
                    ?: System.getenv("KEYSTORE_PASSWORD") ?: ""
                keyAlias      = keystoreProperties.getProperty("keyAlias")
                    ?: System.getenv("KEY_ALIAS") ?: ""
                keyPassword   = keystoreProperties.getProperty("keyPassword")
                    ?: System.getenv("KEY_PASSWORD") ?: ""
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled    = true
            isShrinkResources  = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("Boolean", "ENABLE_LOGGING", "false")

            // Only use the release signing config when a keystore is present.
            // Without this guard, assigning the config unconditionally triggers
            // "SigningConfig 'release' is missing required property storeFile".
            val releaseSigning = signingConfigs.getByName("release")
            if (releaseSigning.storeFile != null) {
                signingConfig = releaseSigning
            }
            // When no keystore is available (e.g. local dev / assembleDebug),
            // Android Gradle falls back to the debug keystore automatically.
        }
        debug {
            buildConfigField("Boolean", "ENABLE_LOGGING", "true")
            // Debug builds are automatically signed with debug keystore
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    kotlinOptions {
        jvmTarget = "17"
    }
    
    buildFeatures {
        compose = true
        buildConfig = true
    }
    
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
    
    // Compose compiler performance flags
    kotlinOptions {
        freeCompilerArgs = freeCompilerArgs + listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.animation.ExperimentalAnimationApi",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi"
        )
    }
    
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-process:2.6.2")
    implementation("androidx.activity:activity-compose:1.8.2")
    
    // Compose
    implementation(platform("androidx.compose:compose-bom:2023.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.6")
    
    // Security - Crypto
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    
    // Biometric
    implementation("androidx.biometric:biometric:1.1.0")
    
    // Camera
    implementation("androidx.camera:camera-core:1.3.1")
    implementation("androidx.camera:camera-camera2:1.3.1")
    implementation("androidx.camera:camera-lifecycle:1.3.1")
    implementation("androidx.camera:camera-view:1.3.1")
    
    // Hilt DI
    implementation("com.google.dagger:hilt-android:2.50")
    kapt("com.google.dagger:hilt-compiler:2.50")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")
    
    // Room Database (for encrypted metadata)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    
    // SQLCipher for encrypted database
    implementation("net.zetetic:android-database-sqlcipher:4.5.4")
    implementation("androidx.sqlite:sqlite-ktx:2.4.0")
    
    // OkHttp for secure downloads
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // Coil for image loading (with memory-only cache)
    implementation("io.coil-kt:coil-compose:2.5.0")
    
    // DataStore for preferences
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    
    // ExoPlayer for video/audio playback
    implementation("androidx.media3:media3-exoplayer:1.2.1")
    implementation("androidx.media3:media3-ui:1.2.1")
    implementation("androidx.media3:media3-datasource:1.2.1")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // Embedded HTTPS Server
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    
    // BouncyCastle for SSL certificate generation
    implementation("org.bouncycastle:bcpkix-jdk15on:1.70")
    
    // QR Code generation
    implementation("com.google.zxing:core:3.5.2")
    
    // Tor Integration (Guardian Project) - Requires special repository setup
    // To enable, add Guardian Project's Maven repository and uncomment:
    implementation("info.guardianproject:tor-android:0.4.8.12")
    implementation("info.guardianproject:jtorctl:0.4.5.7")
    // See: https://github.com/nickcoolshen/nickcoolshen.github.io for mirror repo
    
    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2023.10.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

kapt {
    correctErrorTypes = true
}
