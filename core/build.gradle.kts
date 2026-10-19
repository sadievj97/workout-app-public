// The protocol, the session plan and the rules engine: everything that decides
// what to do, with no Android in it.
//
// This was always true and was not visible. The rules engine was written
// Android-free so the twelve rule tests the brief specifies could run on a
// laptop in seconds instead of needing a device, and the session plan and the
// protocol parser followed. Of the files that moved here, exactly one imported
// anything from Android, and only to read a file by name.
//
// Making it a module turns that from a convention into a compiler-enforced
// rule: nothing here can reach for a Context, because the dependency is not on
// the path. It is also what lets a watch app build the same session from the
// same protocol without a line of this being rewritten.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

// The tests here read the shipped protocol.json and exercises.json straight off
// disk, so a typo in either fails on the desktop in seconds. The files live in
// the app module's assets, which is where Android needs them, so the tests
// reach across rather than keeping a second copy that could drift.
tasks.withType<Test>().configureEach {
    inputs.dir(rootProject.file("app/src/main/assets"))
        .withPropertyName("protocolAssets")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}
