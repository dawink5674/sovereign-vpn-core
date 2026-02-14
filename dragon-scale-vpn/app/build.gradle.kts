@file:Suppress("UnstableApiUsage")

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.sovereign.dragonscale"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.sovereign.dragonscale"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Control Plane API base URL
        buildConfigField("String", "API_BASE_URL",
            "\"https://vpn-control-plane-vqkyeuhxnq-uc.a.run.app\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // ---- WireGuard Tunnel (official Go backend) ----
    implementation("com.wireguard.android:tunnel:1.0.20231018")

    // ---- Jetpack Compose BOM ----
    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.runtime:runtime")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // ---- Material3 Adaptive (foldable support) ----
    implementation("androidx.compose.material3.adaptive:adaptive:1.0.0-alpha06")
    implementation("androidx.compose.material3.adaptive:adaptive-layout:1.0.0-alpha06")
    implementation("androidx.window:window:1.2.0")

    // ---- Retrofit (API client) ----
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // ---- Encrypted Storage ----
    implementation("androidx.security:security-crypto-ktx:1.1.0-alpha06")

    // ---- Core Android ----
    implementation("androidx.core:core-ktx:1.12.0")

    // ---- Testing ----
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("com.google.truth:truth:1.1.5")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
}
