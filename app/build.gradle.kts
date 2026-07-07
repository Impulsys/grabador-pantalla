plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.impulsys.grabador"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.impulsys.grabador"
        minSdk = 26
        targetSdk = 33
        versionCode = 4
        versionName = "4.0"
    }

    signingConfigs {
        create("impulsys") {
            storeFile = file("impulsys.keystore")
            storePassword = "impulsys2026"
            keyAlias = "impulsys"
            keyPassword = "impulsys2026"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("impulsys")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("impulsys")
        }
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
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("com.google.android.material:material:1.11.0")
}
