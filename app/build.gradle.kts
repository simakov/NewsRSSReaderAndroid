import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Computed once at configuration time: the most recent reachable git tag, used to populate
// BuildConfig.GIT_TAG so a running app can compare itself against GitHub Releases' tag_name.
// Falls back to "v0.0.0" in a checkout with no tags yet (e.g. a fresh clone before the first
// release), so the build never fails just because no release has happened.
val gitTag: String = run {
    val process = ProcessBuilder("git", "describe", "--tags", "--abbrev=0")
        .directory(rootDir)
        .start()
    process.waitFor()
    val output = if (process.exitValue() == 0) {
        process.inputStream.bufferedReader().readText().trim()
    } else {
        ""
    }
    output.ifBlank { "v0.0.0" }
}

// Release signing is configured from Gradle properties kept outside the repository (normally
// ~/.gradle/gradle.properties), so no key material or password is ever committed. When they are
// absent — a fresh clone, or a CI runner — the release build still assembles, just unsigned,
// rather than failing outright; `signingConfigs.findByName("release")` below then resolves to
// null, which is exactly what the release build type had before signing existed at all.
val releaseStoreFile = (project.findProperty("NEWSRSSREADER_RELEASE_STORE_FILE") as String?)
    ?.let { File(it) }
    ?.takeIf { it.exists() }

android {
    namespace = "com.newsrssreader"
    compileSdk = 35

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = project.findProperty("NEWSRSSREADER_RELEASE_STORE_PASSWORD") as String?
                keyAlias = project.findProperty("NEWSRSSREADER_RELEASE_KEY_ALIAS") as String?
                keyPassword = project.findProperty("NEWSRSSREADER_RELEASE_KEY_PASSWORD") as String?
            }
        }
    }

    defaultConfig {
        applicationId = "com.newsrssreader"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "GIT_TAG", "\"$gitTag\"")
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = false
        }
    }
    sourceSets {
        getByName("test") {
            resources.srcDirs("src/test/resources")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
}
