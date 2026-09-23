// Since AGP 9.0 the Android plugin brings Kotlin support with it, so there is
// no separate org.jetbrains.kotlin.android plugin here. Applying it is an error.
import java.util.Properties
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.testing.Test

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    // Required by the Samsung Health Data SDK: its request and permission
    // classes are Parcelable.
    id("kotlin-parcelize")
}

// Whether the Samsung Health Data SDK has been downloaded into app/libs.
//
// Its source set imports SDK classes directly, so without the .aar it cannot
// compile. Excluding it keeps `git clone && ./gradlew test` working on a fresh
// checkout, which matters more for a public repository than an optional
// feature does. With the .aar present nothing is excluded and Energy Score
// works as normal.
val samsungSdkPresent = file("libs").listFiles().orEmpty().any { it.extension == "aar" }

android {
    namespace = "com.cutcoach"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.cutcoach"
        minSdk = 28
        targetSdk = 37
        versionCode = 1
        versionName = "0.1"

        // The Claude proxy's address and the secret the app presents to it.
        //
        // Both are read from local.properties, which is gitignored, so neither
        // reaches the repository. Missing values become empty strings rather
        // than failing the build: every phase before this one works without
        // them, and the app says plainly when the layer is not configured.
        //
        // The Anthropic API key is deliberately absent. It lives only as a
        // Cloudflare secret, so it is not here to be leaked.
        val local = Properties().apply {
            rootProject.file("local.properties")
                .takeIf { it.exists() }
                ?.inputStream()
                ?.use { load(it) }
        }
        buildConfigField(
            "String",
            "CLAUDE_PROXY_URL",
            "\"${local.getProperty("cutcoach.proxyUrl", "")}\"",
        )
        buildConfigField(
            "String",
            "CLAUDE_PROXY_SECRET",
            "\"${local.getProperty("cutcoach.proxySecret", "")}\"",
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // The Samsung code lives in its own source root and is only compiled when
    // the SDK is there. A separate folder rather than an exclude filter: the
    // layout then says which code is optional, instead of a build file saying
    // it somewhere else.
    sourceSets {
        getByName("main") {
            if (samsungSdkPresent) {
                // kotlin.srcDir, not java.srcDir. The folder holds .kt files,
                // and adding it to the java source set compiled exactly nothing
                // -- silently, because there is nothing wrong with a java source
                // root that contains no java. The build still succeeded, the
                // Samsung classes simply were not there, and the reflective
                // lookup in MainActivity did what it does when the SDK is
                // absent: nothing at all. On a phone that had the SDK.
                kotlin.srcDir("src/samsung/java")
            } else {
                logger.lifecycle(
                    "Samsung Health SDK not in app/libs -- building without Energy " +
                        "Score. See app/libs/README.md.",
                )
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// Room writes the database schema to this folder on every build. It is checked
// in, so a change to a table shows up as a diff in review rather than as a
// crash on the phone.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// The unit tests read protocol.json, exercises.json and poses.json straight
// off disk, so that a typo in one of them fails on the desktop in seconds.
// Gradle cannot see that on its own and would report the tests up to date after
// an asset was edited, which quietly hides a real failure.
tasks.withType<Test>().configureEach {
    inputs.dir(layout.projectDirectory.dir("src/main/assets"))
        .withPropertyName("protocolAssets")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    // HealthPermissionsTest reads the manifest the same way, so an edit there
    // has to invalidate the tests too.
    inputs.file(layout.projectDirectory.file("src/main/AndroidManifest.xml"))
        .withPropertyName("manifest")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    implementation(libs.kotlinx.serialization.json)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.health.connect)
    implementation(libs.androidx.work.runtime)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)

    // Samsung Health Data SDK, Phase 5. A manual .aar download rather than a
    // Maven dependency: Samsung does not publish it to a repository, and it is
    // gitignored because it is not ours to redistribute.
    //
    // Without the file, com/cutcoach/samsung does NOT compile -- it imports SDK
    // classes directly. app/libs/README.md says so rather than leaving someone
    // to discover it. The runtime guards in EnergyScoreProbe are for developer
    // mode being off, which is a different problem.
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))
    implementation(libs.gson)
    ksp(libs.androidx.room.compiler)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
