plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.jarvis.assistant"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.jarvis.assistant"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.3-phase3"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // Networking (Groq calls + Edge TTS websocket).
    // Note: org.json is NOT added here on purpose — Android's platform
    // already provides org.json.JSONObject/JSONArray at runtime. Adding
    // the standalone org.json:json library on top of that causes a
    // "duplicate class" build error, so this app relies on the
    // platform's built-in copy instead.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Coroutines for background work
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Porcupine wake word engine ("Jarvis" / "Friday")
    implementation("ai.picovoice:porcupine-android:3.0.5")
}
