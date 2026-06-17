plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
    // id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.unifiedcomms"
    compileSdk = 34

    // Only configure release signing if keystore path is provided
    val keystorePath = System.getenv("KEYSTORE_PATH") ?: ""
    if (keystorePath.isNotBlank()) {
        signingConfigs {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
                keyAlias = System.getenv("KEY_ALIAS") ?: ""
                keyPassword = System.getenv("KEY_PASSWORD") ?: ""
            }
        }
    }

    defaultConfig {
        applicationId = "com.unifiedcomms"
        minSdk = 31
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
        multiDexEnabled = true
        
        buildConfigField("String", "GOOGLE_CLIENT_ID", "\"${System.getenv("GOOGLE_CLIENT_ID") ?: ""}\"")
        buildConfigField("String", "MICROSOFT_CLIENT_ID", "\"${System.getenv("MICROSOFT_CLIENT_ID") ?: ""}\"")
        buildConfigField("String", "YAHOO_CLIENT_ID", "\"${System.getenv("YAHOO_CLIENT_ID") ?: ""}\"")
        buildConfigField("String", "APPLE_CLIENT_ID", "\"${System.getenv("APPLE_CLIENT_ID") ?: ""}\"")
        buildConfigField("String", "PUSH_API_KEY", "\"${System.getenv("PUSH_API_KEY") ?: ""}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (keystorePath.isNotBlank()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            isDebuggable = true
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=androidx.lifecycle.ExperimentalLifecycleApi"
        )
    }

    buildFeatures {
        viewBinding = true
        dataBinding = false
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += listOf("META-INF/*")
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }

    namespace = "com.unifiedcomms"
}

dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.fragment:fragment-ktx:1.8.3")

    // Material 3
    implementation("androidx.compose.material3:material3:1.3.0")
    implementation("androidx.compose.material3:material3-window-size-class:1.3.0")
    implementation("com.google.android.material:material:1.12.0")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui:1.6.7")
    implementation("androidx.compose.ui:ui-graphics:1.6.7")
    implementation("androidx.compose.ui:ui-tooling-preview:1.6.7")
    implementation("androidx.compose.material:material:1.6.7")
    implementation("androidx.compose.material:material-icons-extended:1.6.7")
    implementation("androidx.compose.runtime:runtime-livedata:1.6.7")
    implementation("androidx.compose.runtime:runtime-rxjava2:1.6.7")

    // Lifecycle & ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-process:2.8.4")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Hilt DI - disabled for build
    // implementation("com.google.dagger:hilt-android:2.50")
    // kapt("com.google.dagger:hilt-compiler:2.50")
    // implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Room Database
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    // DataStore (Preferences)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Coroutines & Flow
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.1")

    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // Networking - OkHttp & Retrofit
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
    implementation("com.squareup.retrofit2:converter-scalars:2.11.0")
    implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")

    // Email Protocols
    implementation("com.sun.mail:android-mail:1.6.7")
    implementation("com.sun.mail:android-activation:1.6.7")

    // CalDAV/CardDAV - commented out for build, add back with correct versions
    // implementation("at.bitfire.ical4android:ical4android:1.2.1")
    // implementation("at.bitfire.dav4jvm:dav4jvm:3.5.2")

    // Crypto/Security
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.biometric:biometric:1.2.0-alpha04")

    // WorkManager (Background sync)
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Notifications
    implementation("androidx.core:core-splashscreen:1.0.1")

    // Image Loading
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Permissions
    implementation("com.google.accompanist:accompanist-permissions:0.32.0")

    // Animations
    implementation("androidx.compose.animation:animation:1.6.7")
    implementation("androidx.compose.animation:animation-graphics:1.6.7")

    // Widgets (Glance)
    implementation("androidx.glance:glance:1.1.0")
    implementation("androidx.glance:glance-appwidget:1.1.0")
    implementation("androidx.glance:glance-appwidget-proto:1.1.0")
    // implementation("androidx.glance:glance-wear-tiles:1.1.0") // not available

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.11.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.1.0")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("com.google.truth:truth:1.1.5")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.6.7")
    androidTestImplementation("androidx.compose.ui:ui-test-manifest:1.6.7")
    debugImplementation("androidx.compose.ui:ui-tooling:1.6.7")
    debugImplementation("androidx.compose.ui:ui-tooling-data:1.6.7")
}

kapt {
    correctErrorTypes = true
    javacOptions {
        option("-Xlint:unchecked")
        option("-Xlint:deprecation")
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
        freeCompilerArgs += listOf(
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=androidx.lifecycle.ExperimentalLifecycleApi",
            "-opt-in=com.google.devtools.ksp.ExperimentalKspInterop"
        )
    }
}