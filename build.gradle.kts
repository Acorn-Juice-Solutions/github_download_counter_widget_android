plugins {
    // The Kotlin plugin is auto-applied by AGP 9+; declaring it explicitly here
    // conflicts with AGP's registration of the `kotlin` extension.
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.spotless) apply false
    // alias(libs.plugins.kover) apply false  // See app/build.gradle.kts for status.
}
