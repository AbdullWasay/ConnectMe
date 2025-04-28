plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.google.gms.google.services)
    id("org.jlleitschuh.gradle.ktlint")
}

android {
    namespace = "com.example.connectme"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.connectme"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

ktlint {
    android.set(true)
    verbose.set(true)
    filter {
        exclude { element -> element.file.path.contains("generated/") }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    implementation ("com.google.firebase:firebase-messaging:23.3.1")

    // For handling notification images
    implementation ("com.squareup.picasso:picasso:2.8")
    implementation ("io.agora.rtc:full-sdk:4.0.1")
    // 🔥 Firebase Dependencies
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore.ktx)
    implementation(libs.firebase.database.ktx)
    implementation("com.google.firebase:firebase-storage:20.3.0") // Firebase Storage

    // ✅ CameraX Dependencies
    implementation("androidx.camera:camera-core:1.3.0")
    implementation("androidx.camera:camera-camera2:1.3.0")
    implementation("androidx.camera:camera-lifecycle:1.3.0")
    implementation("androidx.camera:camera-view:1.3.0")

    // ✅ Google Guava (For ListenableFuture)
    implementation("com.google.guava:guava:31.0.1-android")

    // ✅ Lifecycle & ViewModel
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")

    // ✅ Glide for Image Loading
    implementation("com.github.bumptech.glide:glide:4.15.1")
    implementation(libs.firebase.messaging.ktx)
    implementation(libs.firebase.storage.ktx)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.firebase.crashlytics.buildtools)
    implementation(libs.androidx.ui.text.android)
    implementation(libs.volley)
    implementation(libs.androidx.tools.core)
    annotationProcessor("com.github.bumptech.glide:compiler:4.15.1")


    // ✅ Coroutine Support for Firebase
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.6.4")

    // ✅ Testing dependencies
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)


}
