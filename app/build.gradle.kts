import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.chaquo.python")
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
        versionCode = 18
        versionName = "0.17"
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
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
chaquopy {
    defaultConfig {
        version = "3.12"
        System.getenv("SHICANG_BUILD_PYTHON")?.let { buildPython(it) }
        pip { install("yt-dlp==2026.8.19"); install("certifi==2026.10.18") }
    }
}
dependencies {
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.25.0")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-ui:1.11.1")
    implementation("androidx.media3:media3-transformer:1.11.1")
    implementation("androidx.media3:media3-effect:1.11.1")
    implementation(platform("androidx.compose:compose-bom:2026.03.01"))
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("io.coil-kt.coil3:coil-compose:3.4.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.4.0")
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

// Isolated host rendering experiment; never part of the shipping application.
android.testOptions.unitTests.apply {
    isIncludeAndroidResources = true
    all {
        // Robolectric native SQLite finalizers can outlive a sandbox. Do not reuse
        // one JVM across classes which select different SQLite/graphics modes.
        it.forkEvery = 1
        it.maxHeapSize = "2g"
        it.systemProperty("java.io.tmpdir", rootProject.file(".gradle/test-tmp").also { it.mkdirs() }.absolutePath)
        it.systemProperty("user.home", rootProject.file(".gradle/task-home").also { it.mkdirs() }.absolutePath)
        it.systemProperty("roborazzi.test.record", "true")
        it.systemProperty("robolectric.pixelCopyRenderMode", "hardware")
        it.systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
        listOf("http.proxyHost", "http.proxyPort", "https.proxyHost", "https.proxyPort", "javax.net.ssl.trustStore").forEach { key ->
            System.getProperty(key)?.let { value -> it.systemProperty(key, value) }
        }
        it.jvmArgs(
            "--add-opens=java.base/java.lang=ALL-UNNAMED",
            "--add-opens=java.base/java.util=ALL-UNNAMED",
            "--add-opens=java.base/java.io=ALL-UNNAMED",
            "--add-opens=java.base/java.net=ALL-UNNAMED",
            "--add-opens=java.base/java.security=ALL-UNNAMED",
            "--add-opens=java.base/java.text=ALL-UNNAMED",
            "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
            "--add-opens=java.desktop/java.awt.font=ALL-UNNAMED",
            "--add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
        )
    }
}
dependencies {
    testImplementation(platform("androidx.compose:compose-bom:2026.03.01"))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.76.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.76.0")
}
