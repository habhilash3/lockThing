plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.focuslock"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.focuslock"
        minSdk = 26
        targetSdk = 30   // device is Android 11; avoids Android 12+ behaviour changes we don't need
        versionCode = 1
        versionName = "1.0"
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    lint { checkReleaseBuilds = false; abortOnError = false }
}
dependencies {
    testImplementation("junit:junit:4.13.2")   // only dependency (tests); the app itself has none
}
