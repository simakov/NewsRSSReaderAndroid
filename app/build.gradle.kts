import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
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
        // Both are plain literals, bumped by the `release` skill in the commit that the release
        // tag then points at. They are deliberately *not* derived from `git describe` any more:
        // F-Droid determines an app's version by regex-scanning this file at each tag and cannot
        // execute Gradle code to find it, so anything computed at configuration time is invisible
        // to it (every tag would look like the same version and no update would ever be offered).
        // versionCode encodes the tag as MAJOR * 10000 + MINOR * 100 + PATCH, so v1.6.0 -> 10600.
        versionCode = 10700
        versionName = "1.7.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Where a build is distributed from, which is the only thing that differs between the two:
    // an app installed from GitHub Releases has to update itself, while an app installed from
    // F-Droid is updated by the F-Droid client and must not try. The updater's code lives in
    // `main` for both — only this flag and the fdroid manifest's permission removal differ, so
    // there is no second copy of anything to keep in sync.
    flavorDimensions += "distribution"
    productFlavors {
        create("github") {
            dimension = "distribution"
            buildConfigField("boolean", "UPDATE_CHECK_ENABLED", "true")
        }
        create("fdroid") {
            dimension = "distribution"
            // F-Droid signs its own builds with its own key, so an APK downloaded from GitHub
            // Releases cannot install over an F-Droid install anyway — offering the update would
            // lead the user into a dead end. With this off the app never calls the GitHub API,
            // and src/fdroid/AndroidManifest.xml drops REQUEST_INSTALL_PACKAGES so the F-Droid
            // listing doesn't advertise a permission this build has no use for.
            buildConfigField("boolean", "UPDATE_CHECK_ENABLED", "false")
        }
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
