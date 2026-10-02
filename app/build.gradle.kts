plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "uz.sergak"
    compileSdk = 35

    defaultConfig {
        applicationId = "uz.sergak"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"
        vectorDrawables { useSupportLibrary = true }
    }

    // offline — internetsiz versiya (INTERNET ruxsati yo'q, davlat/air-gapped muhit uchun).
    // online  — Sergak serveri orqali VirusTotal, abuse.ch, Google Web Risk tekshiruvi (foydalanuvchi roziligi bilan).
    // Server manzili:  ./gradlew assembleOnlineRelease -PsergakApiUrl=https://api.example.uz -PsergakAppToken=...
    flavorDimensions += "network"
    productFlavors {
        create("online") {
            dimension = "network"
            buildConfigField("boolean", "CLOUD", "true")
            buildConfigField("String", "API_URL", "\"${project.findProperty("sergakApiUrl") ?: ""}\"")
            buildConfigField("String", "APP_TOKEN", "\"${project.findProperty("sergakAppToken") ?: ""}\"")
        }
        create("offline") {
            dimension = "network"
            applicationIdSuffix = ".offline"
            versionNameSuffix = "-offline"
            buildConfigField("boolean", "CLOUD", "false")
            buildConfigField("String", "API_URL", "\"\"")
            buildConfigField("String", "APP_TOKEN", "\"\"")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Imzo kaliti CI orqali beriladi (README'ga qarang). Kalit bo'lmasa debug kaliti ishlatiladi.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }

    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.04.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.9")
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    testImplementation("junit:junit:4.13.2")
}
