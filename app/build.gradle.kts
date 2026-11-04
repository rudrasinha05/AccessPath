import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Release signing comes from environment variables (CI secrets or a local shell), never from the repo.
val releaseKeystore: String? = System.getenv("ACCESSPATH_KEYSTORE")

// Machine-specific values: local.properties first, then environment (CI secrets).
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun config(name: String, default: String = ""): String =
    (localProps.getProperty(name) ?: System.getenv(name) ?: default).trim()

android {
    namespace = "com.accesspath"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "com.accesspath"
        minSdk = 29
        targetSdk = 35
        versionCode = (System.getenv("ACCESSPATH_VERSION_CODE") ?: "2").toInt()
        versionName = "2.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Map tiles key (secret-ish: kept out of git). Empty = fall back to OpenStreetMap tiles.
        buildConfigField("String", "MAPTILER_KEY", "\"${config("MAPTILER_KEY")}\"")
        // Public client values: the publishable key and OAuth client ID are meant to ship inside apps.
        buildConfigField("String", "SUPABASE_URL", "\"${config("SUPABASE_URL", "https://jjpussjaccbgcufsgqpu.supabase.co")}\"")
        buildConfigField("String", "SUPABASE_KEY", "\"${config("SUPABASE_KEY", "sb_publishable_ZJvQgC1S0XPYmMpB-JT_YQ_aKGSiqqQ")}\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${config("GOOGLE_WEB_CLIENT_ID", "1097491457415-goun56h1vod6p7mqbpils4mc0hts9dek.apps.googleusercontent.com")}\"")
        // Lets device tests save screenshots that Gradle pulls into build/outputs.
        testInstrumentationRunnerArguments["useTestStorageService"] = "true"
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("ACCESSPATH_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ACCESSPATH_KEY_ALIAS")
                keyPassword = System.getenv("ACCESSPATH_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures { compose = true; buildConfig = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isReturnDefaultValues = true }
    androidResources { generateLocaleConfig = false }
    lint {
        abortOnError = true
        warningsAsErrors = false
        checkReleaseBuilds = true
        // Pinned toolchain for reproducible builds; upgrades are tracked in docs, not failed in CI.
        disable += setOf("GradleDependency", "AndroidGradlePluginVersion", "NewerVersionAvailable", "OldTargetApi")
    }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

kapt { arguments { arg("room.schemaLocation", "$projectDir/schemas") } }

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.osmdroid:osmdroid-android:6.1.20")
    implementation("com.google.ar:core:1.44.0")
    implementation("com.google.zxing:core:3.5.3")
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")

    androidTestImplementation(platform("androidx.compose:compose-bom:2024.10.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.room:room-testing:2.6.1")
    androidTestImplementation("androidx.test.services:storage:1.5.0")
    androidTestUtil("androidx.test.services:test-services:1.5.0")
}
