plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Release signing comes from the environment, never from the repository: BALISET_RELEASE_STORE_FILE, _STORE_PASSWORD, _KEY_ALIAS, _KEY_PASSWORD.
// Without all four, the release build is simply left unsigned.
val signingEnv = listOf("STORE_FILE", "STORE_PASSWORD", "KEY_ALIAS", "KEY_PASSWORD").associateWith { System.getenv("BALISET_RELEASE_$it")?.takeIf(String::isNotBlank) }
val canSign = signingEnv.values.all { it != null }

android {
    namespace = "com.everettpenne.baliset"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.everettpenne.baliset"
        minSdk = 31
        targetSdk = 36
        versionCode = 3
        versionName = "0.3.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        // The debug keystore lives in the repo root so local builds work even when $HOME/.android is not writable.
        getByName("debug") {
            storeFile = rootProject.file("debug.keystore")
            storePassword = "android"; keyAlias = "androiddebugkey"; keyPassword = "android"
        }
        if (canSign) create("release") {
            storeFile = file(signingEnv.getValue("STORE_FILE")!!)
            storePassword = signingEnv.getValue("STORE_PASSWORD"); keyAlias = signingEnv.getValue("KEY_ALIAS"); keyPassword = signingEnv.getValue("KEY_PASSWORD")
        }
    }
    buildTypes {
        getByName("release") {
            if (canSign) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.06.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
    implementation("androidx.navigation:navigation-compose:2.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    // Liquid glass UI kit (components, theme, type), the shared player controls and mini player, and in-app updates.
    implementation("com.everettpenne:glass")
    implementation("com.everettpenne:media")
    implementation("com.everettpenne:updater")
    // Playback: ExoPlayer owns the sound, MediaSession gives it a notification, lock-screen controls and the launcher's island card.
    implementation("androidx.media3:media3-exoplayer:1.7.1")
    implementation("androidx.media3:media3-session:1.7.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("androidx.core:core-ktx:1.16.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    // Android stubs org.json in unit tests; the real one parses the MusicBrainz fixtures.
    testImplementation("org.json:json:20240303")
}
