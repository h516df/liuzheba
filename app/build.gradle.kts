import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}
val signingProperties=Properties().apply {
    val source=rootProject.file(System.getenv("SHICANG_SIGNING_PROPERTIES") ?: "keystore.properties")
    if(source.exists()) source.inputStream().use {load(it)}
}
android {
    namespace = "dev.h516.shicang"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.h516.shicang"
        minSdk = 24
        targetSdk = 36
        versionCode = 6
        versionName = "0.5"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true; buildConfig = true }
    signingConfigs {
        if(signingProperties.isNotEmpty()) create("release") {
            storeFile=rootProject.file(System.getenv("SHICANG_KEYSTORE") ?: "keystore/shicang.jks")
            storePassword=signingProperties.getProperty("storePassword")
            keyAlias="shicang"
            keyPassword=signingProperties.getProperty("keyPassword")
        }
    }
    buildTypes { release { isMinifyEnabled = true; isShrinkResources=true;proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"));signingConfig=signingConfigs.findByName("release") } }
    testOptions { unitTests.isReturnDefaultValues = true }
}
dependencies {
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-ui:1.11.1")
    implementation(platform("androidx.compose:compose-bom:2026.03.01"))
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("io.coil-kt.coil3:coil-compose:3.4.0")
    implementation("me.saket.telephoto:zoomable-image-coil3:0.19.0")
    implementation("androidx.exifinterface:exifinterface:1.4.2")
    implementation("androidx.fragment:fragment-ktx:1.8.9")
    implementation("androidx.biometric:biometric:1.1.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
