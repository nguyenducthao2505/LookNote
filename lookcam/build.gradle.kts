plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.looknote.cam"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.looknote.cam"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    // Khoa ky co dinh: moi ban build co cung chu ky, cai de len ban cu duoc
    signingConfigs {
        create("fixed") {
            storeFile = rootProject.file("keystore/looknote-release.jks")
            storePassword = "looknote2026"
            keyAlias = "looknote"
            keyPassword = "looknote2026"
        }
    }

    buildTypes {
        getByName("debug") { signingConfig = signingConfigs.getByName("fixed") }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("fixed")
        }
    }

    // Dung chung bo cong thuc mau voi app LookNote
    sourceSets["main"].java.srcDir("../app/src/main/java/com/looknote/app/model")
    sourceSets["main"].java.srcDir("../lookai/src/main/java/com/looknote/ai/engine")
    sourceSets["main"].java.srcDir("../lookai/src/main/java/com/looknote/ai/util")

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    testImplementation("junit:junit:4.13.2")
}
