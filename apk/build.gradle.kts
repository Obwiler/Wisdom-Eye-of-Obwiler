plugins {
    id("com.android.application") version "8.1.4"
    id("org.jetbrains.kotlin.android") version "1.9.22"
}

android {
    namespace = "com.obwiler.weo"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.obwiler.weo"
        minSdk = 31
        targetSdk = 34
        versionCode = 2
        versionName = "0.2.0"
    }

    signingConfigs {
        create("release") {
            val keystoreFile = file("keystore.properties")
            if (keystoreFile.exists()) {
                val props = keystoreFile.readLines()
                    .filter { it.contains("=") }
                    .associate {
                        val (k, v) = it.split("=", limit = 2)
                        k.trim() to v.trim()
                    }
                storeFile = file(props["storeFile"] ?: "weo-temp.keystore")
                storePassword = props["storePassword"] ?: "temporary"
                keyAlias = props["keyAlias"] ?: "weo"
                keyPassword = props["keyPassword"] ?: "temporary"
            } else {
                storeFile = file("weo-temp.keystore")
                storePassword = "temporary"
                keyAlias = "weo"
                keyPassword = "temporary"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
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
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.01.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("com.google.android.material:material:1.11.0")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Rokid CXR-S bridge — 阶段1启用时取消注释
    // implementation("com.rokid.cxr:cxr-service-bridge:1.0-SNAPSHOT")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
