import java.io.InputStream
import java.net.URI
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.navigation.safeargs)
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
}

if (file("google-services.json").exists()) {
    pluginManager.apply(libs.plugins.google.services.get().pluginId)
    pluginManager.apply(libs.plugins.firebase.crashlytics.get().pluginId)
}

val localProperties = Properties().apply {
    val localFile = rootProject.file("local.properties")
    if (localFile.exists()) {
        localFile.inputStream().use { load(it) }
    }
}
val storeFileProvider: String? = localProperties.getProperty("storeFile")
val storePasswordProvider: String? = localProperties.getProperty("storePassword")
val keyAliasProvider: String? = localProperties.getProperty("keyAlias")
val keyPasswordProvider: String? = localProperties.getProperty("keyPassword")
val hasReleaseSigning = listOf(
    storeFileProvider,
    storePasswordProvider,
    keyAliasProvider,
    keyPasswordProvider
).all { !it.isNullOrBlank() }

android {
    namespace = "com.arindam.camerax"
    compileSdk = rootProject.extra["compileSdk"] as Int

    signingConfigs {
        create("debugConfig") {
            storeFile = file("${rootDir}/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(storeFileProvider!!)
                storePassword = storePasswordProvider
                keyAlias = keyAliasProvider
                keyPassword = keyPasswordProvider
            }
        }
    }

    defaultConfig {
        applicationId = "com.arindam.camerax"
        minSdk = rootProject.extra["minSdkVersion"] as Int
        targetSdk = rootProject.extra["targetSdkVersion"] as Int
        versionCode = rootProject.extra["versionCodeBase"] as Int
        versionName = rootProject.extra["versionNameBase"] as String
        vectorDrawables.useSupportLibrary = true
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk.abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64") )
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("debugConfig")
            applicationIdSuffix = ".debug"
            resValue("string", "app_version", "${defaultConfig.versionName} (build ${defaultConfig.versionCode}$applicationIdSuffix)")
            resValue("string", "app_name", "كاميرا برو")
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        getByName("release") {
            resValue("string", "app_version", "${defaultConfig.versionName} (build ${defaultConfig.versionCode}.release)")
            resValue("string", "app_name", "كاميرا برو")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            ndk.debugSymbolLevel = "SYMBOL_TABLE"
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
        compose = true
        resValues = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = true
        warningsAsErrors = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/LICENSE*"
            excludes += "/META-INF/NOTICE*"
            excludes += "/META-INF/*.kotlin_module"
        }
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

dependencies {
    // AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.preference.ktx)

    // Lifecycle and LiveData
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Navigation library
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    // CameraX
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.video)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.extensions)
    implementation(libs.androidx.camera.featurecombinationquery)
    implementation(libs.androidx.camera.featurecombinationquery.play.services)

    // Coil
    implementation(libs.coil.compose)

    // Material Design
    implementation(libs.material)

    // Firebase SDK
    implementation(libs.firebase.analytics.ktx)
    implementation(libs.firebase.crashlytics)

    // [CONFLICT FIX] Guava ListenableFuture vs CameraX
    implementation(libs.guava)

    // MediaPipe Tasks Vision (Face Landmarker)
    implementation(libs.mediapipe.tasks.vision)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.text.google.fonts)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
    androidTestImplementation(libs.compose.ui.test.junit4)

    // Unit testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)

    // Instrumented testing
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.rules)
    androidTestImplementation(libs.androidx.runner)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.uiautomator)
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

// GrantPermissionRule fails on Android 16+ (UiAutomation lacks GRANT_RUNTIME_PERMISSIONS).
val debugAppId = "${android.defaultConfig.applicationId}.debug"
val debugTestPermissions = listOf(
    "android.permission.CAMERA",
    "android.permission.RECORD_AUDIO"
)

afterEvaluate {
    tasks.matching { it.name == "connectedDebugAndroidTest" }.configureEach {
        dependsOn("installDebug")
        doFirst {
            val adb = System.getenv("ANDROID_SERIAL")?.let { listOf("adb", "-s", it) } ?: listOf("adb")
            debugTestPermissions.forEach { permission ->
                runCatching {
                    ProcessBuilder(adb + listOf("shell", "pm", "grant", debugAppId, permission))
                        .redirectErrorStream(true)
                        .start()
                        .waitFor()
                }
            }
        }
    }
}

val downloadFaceLandmarker = tasks.register("downloadFaceLandmarker") {
    val modelFile = file("src/main/assets/face_landmarker.task")
    outputs.file(modelFile)
    doLast {
        if (!modelFile.exists() || modelFile.length() < 1000) {
            println("Downloading MediaPipe face_landmarker.task model...")
            modelFile.parentFile.mkdirs()
            URI.create("https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/face_landmarker.task").toURL().openStream().use { input ->
                modelFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            println("Successfully downloaded face_landmarker.task: ${modelFile.length()} bytes")
        }
    }
}

tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }.configureEach {
    dependsOn(downloadFaceLandmarker)
}

