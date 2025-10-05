plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")

}

android {
    namespace = "com.example.nasaclimatestation" // cambia si tu paquete es otro
    compileSdk = 34   // si quieres, puedes subirlo a 35 cuando todo funcione

    defaultConfig {
        applicationId = "com.example.nasaclimatestation"
        minSdk = 24
        targetSdk = 34
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

    buildFeatures { compose = true }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }


        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }

        kotlinOptions {
            jvmTarget = "17"
        }


// Opcional (pero ayuda a forzar toolchain):
    kotlin {
        jvmToolchain(17)
    }

}

dependencies {
    // Compose BOM: sin versiones explícitas para artefactos Compose
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("io.coil-kt:coil-compose:2.6.0")

    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Corrutinas
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Tests (por los ejemplos)
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
}


