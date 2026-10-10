import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.darkstore.darkroot"
        minSdk = 26
        targetSdk = 34
        versionCode = 603
        versionName = "6.0.3"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        // A committed, permanent keystore for the release build — deliberately NOT
        // the machine-default debug keystore. That one is auto-generated fresh by
        // AGP on whatever machine builds it, which meant every GitHub Actions run
        // could sign with a DIFFERENT, ephemeral certificate — silently breaking
        // Google Sign-In (which is locked to a specific SHA-1 fingerprint
        // registered in Firebase) any time CI happened to generate a new one.
        // Committing this keystore makes every build (CI or local) produce the
        // exact same, stable SHA-1 fingerprint every time.
        // This is a sideload/testing keystore, not a production Play Store key —
        // replace it with a real one before any real distribution.
        create("darkstoreRelease") {
            storeFile = file("darkstore-release.keystore")
            storePassword = "darkstore123"
            keyAlias = "darkstore"
            keyPassword = "darkstore123"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("darkstoreRelease")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("darkstoreRelease")
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
    kotlinOptions {
        jvmTarget = "17"
        // Strong Skipping Mode makes the Compose compiler far more willing to skip
        // recomposing a composable even when it has "unstable" parameters (raw
        // List/Map/Set, lambdas capturing mutable state, etc.) by comparing values
        // instead of assuming the worst. This project has many composables with
        // exactly that shape; this is a broad, low-risk win on top of the specific
        // @Immutable fixes already made by hand.
        freeCompilerArgs += listOf(
            "-P",
            "plugin:androidx.compose.compiler.plugins.kotlin:experimentalStrongSkipping=true"
        )
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Pin stack to compileSdk 34 / AGP 8.4.2–compatible versions.
    // Without force rules, transitive deps can float to androidx.core 1.19 /
    // Compose 1.9 / Activity 1.14 / OkHttp 5 which require compileSdk 35–37
    // and break :app:checkDebugAarMetadata.
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.play.services.auth)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.coil.compose)
    implementation(libs.okhttp)
    implementation(libs.moshi.kotlin)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    debugImplementation(libs.androidx.compose.ui.tooling)
}

// Force versions that work with compileSdk 34 + AGP 8.4.2 even if a
// transitive dependency tries to pull a newer line.
configurations.configureEach {
    resolutionStrategy {
        force(
            "androidx.core:core:1.13.1",
            "androidx.core:core-ktx:1.13.1",
            "androidx.activity:activity:1.9.2",
            "androidx.activity:activity-ktx:1.9.2",
            "androidx.activity:activity-compose:1.9.2",
            "com.squareup.okhttp3:okhttp:4.12.0",
            "com.squareup.okhttp3:logging-interceptor:4.12.0",
            "androidx.lifecycle:lifecycle-runtime-ktx:2.8.4",
            "androidx.lifecycle:lifecycle-runtime-compose:2.8.4",
            "androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4",
            "androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4"
        )
        eachDependency {
            if (requested.group == "com.squareup.okhttp3" && requested.name.startsWith("okhttp")) {
                useVersion("4.12.0")
                because("OkHttp 5.x requires compileSdk 37")
            }
            if (requested.group == "androidx.core" && (requested.name == "core" || requested.name == "core-ktx")) {
                useVersion("1.13.1")
                because("core 1.19 requires compileSdk 37 / AGP 9.1")
            }
            if (requested.group == "androidx.activity") {
                useVersion("1.9.2")
                because("activity 1.14-alpha requires compileSdk 36 / AGP 8.9")
            }
            // Block navigationevent (pulled by new activity) — needs SDK 36
            if (requested.group == "androidx.navigationevent") {
                useVersion("1.0.0-alpha01") // may still fail; prefer exclude
            }
        }
    }
}

val downloadAssetsTask = tasks.register("downloadAssets") {
    doLast {
        val adBadgeDest = file("src/main/res/drawable/img_ad_badge.png")
        try {
            val adBadgeUri = URI("https://i.ibb.co/YFHRgL0r/Picsart-26-06-15-09-31-05-304.png")
            adBadgeDest.parentFile.mkdirs()
            adBadgeDest.writeBytes(adBadgeUri.toURL().readBytes())
        } catch (e: Exception) {
            logger.warn("Failed to download ad badge: ${e.message}")
        }

        val appLogoDest = file("src/main/res/drawable/img_app_logo_new.png")
        val appIconDest = file("src/main/res/drawable/img_app_icon.png")
        try {
            val appLogoUri = URI("https://i.ibb.co/jnf0Wj8/2f7e92384edf.png")
            val logoBytes = appLogoUri.toURL().readBytes()
            appLogoDest.parentFile.mkdirs()
            appLogoDest.writeBytes(logoBytes)
            appIconDest.writeBytes(logoBytes)
        } catch (e: Exception) {
            logger.warn("Failed to download app logo: ${e.message}")
        }
    }
}

// Make preBuild depend on downloadAssets
tasks.named("preBuild") {
    dependsOn(downloadAssetsTask)
}



