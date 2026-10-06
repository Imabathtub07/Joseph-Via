import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.josephvia.bezelcalm"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.josephvia.bezelcalm"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        // A key kept in the repo so every build (local or CI) can install over the last one.
        // This app is sideloaded, not published to the Play Store.
        create("sideload") {
            storeFile = rootProject.file("keystore/bezelcalm.jks")
            storePassword = "bezelcalm"
            keyAlias = "bezelcalm"
            keyPassword = "bezelcalm"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("sideload")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("sideload")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
