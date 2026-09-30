plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.aaharin"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.aaharin"
        // Android 8.0, the floor the adaptive launcher icons need. Was 35 (Android 15),
        // which meant the APK would only install on phones running Android 15 or newer -
        // everything else refused it with "problem parsing the package".
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            // Keeps debug installs fast; R8 never runs on this variant anyway.
            isDebuggable = true
        }
        release {
            // R8 shrink + obfuscate. Keep rules live in src/main/keepRules/rules.keep
            // (AGP merges every file in that folder automatically).
            optimization {
                enable = true
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        // Required so NetworkModule can branch on BuildConfig.DEBUG.
        buildConfig = true
    }
    packaging {
        resources {
            // Drops duplicate/unused metadata from the packaged APK.
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/*.kotlin_module",
                "DebugProbesKt.bin",
                "kotlin-tooling-metadata.json"
            )
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    // Was only arriving transitively; viewModel() is used directly in the UI layer.
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    
    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp.logging)

    // Item photos. The okhttp artifact is what gives Coil a network fetcher; without it
    // every http(s) URL fails to load.
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}