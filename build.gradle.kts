// Top-level build file. Plugins are declared here without applying them, so the
// versions live in one place (gradle/libs.versions.toml) and each module opts in.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
