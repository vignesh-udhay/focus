import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// The upload key, read from keystore.properties at the repository root. That
// file and the key it names are never committed; without them a release
// build is left unsigned, as it was before.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "app.tellcat"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "app.tellcat"
        minSdk = 29
        targetSdk = 37
        versionCode = 3
        versionName = "1.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystoreProperties.isNotEmpty()) {
            create("upload") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        // **The debug build installs beside the real app**, as app.tellcat.debug
        // labelled "Tellcat dev". The owner's phone holds the Play copy of
        // app.tellcat with their own tasks in it, and a debug build of the same
        // package cannot install over a Play-signed one without uninstalling
        // it, which would delete those tasks. Nothing in the app is tied to the
        // package name: there are no content providers, every PendingIntent is
        // explicit, and the backup file name does not use it.
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            signingConfig = signingConfigs.findByName("upload")
            // R8 on: shrinking, optimisation and resource shrinking. The release
            // otherwise shipped every unused Compose, Material, Glance, Room and
            // WorkManager class, a larger download and slower screens. Those
            // libraries bring their own keep rules, and nothing in the app is
            // reached by reflection. The one exception is in proguard-rules.pro:
            // an old WorkManager rule that full-mode R8 reads too narrowly,
            // which left the widget loading forever.
            optimization {
                enable = true
            }
            proguardFiles("proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }

    // The exported schemas ship into the instrumented test APK, which is what
    // MigrationTestHelper reads to build a version-1 database and to validate
    // the result of a migration against version 2.
    sourceSets.getByName("androidTest") {
        assets.directories.add("$projectDir/schemas")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// room-testing reads the exported schema JSON with kotlinx-serialization, and
// its bundle serializers are generated for serialization 1.8. AGP's consistent
// resolution otherwise pins the test classpath to the 1.7.3 core the app
// resolves through lifecycle-viewmodel-savedstate, and the mismatch throws
// AbstractMethodError inside MigrationTestHelper before a single assertion
// runs. Only the instrumented test classpath is aligned; the application's own
// dependencies are untouched.
configurations.matching { it.name.contains("AndroidTest") }.configureEach {
    resolutionStrategy {
        force("org.jetbrains.kotlinx:kotlinx-serialization-core:1.8.1")
        force("org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.8.1")
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    // Glance is AndroidX's RemoteViews-safe Compose API. A home-screen widget
    // cannot use the app's ordinary Compose UI directly.
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    // Supplies Dispatchers.setMain, so a view model observing through
    // viewModelScope can be tested on the JVM instead of on a device.
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.room.testing)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
