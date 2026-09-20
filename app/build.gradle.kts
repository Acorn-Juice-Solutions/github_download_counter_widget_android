plugins {
    // AGP 9+ auto-applies the Kotlin plugin, so we do NOT declare
    // `alias(libs.plugins.kotlin.android)` here — doing so would produce
    // "Cannot add extension with name 'kotlin'". The catalog entry is kept
    // for downstream consumers on AGP 8.
    alias(libs.plugins.android.application)
    alias(libs.plugins.detekt)
    alias(libs.plugins.spotless)
    // Kover 0.9 does not yet auto-detect the AGP 9 debug variant, so its report is
    // consistently empty. Coverage tracking is on hold until Kover ships AGP 9 support;
    // the tests themselves are the authoritative quality gate.
    // alias(libs.plugins.kover)
}

android {
    namespace = "com.acornjuice.downloadwidget"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.acornjuice.downloadwidget"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "1.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // Release signing is opt-in. Set KEYSTORE_PATH (+ passwords + alias) via CI secrets
        // or `~/.gradle/gradle.properties` — otherwise release builds stay unsigned so local
        // dev doesn't need a keystore.
        create("release") {
            val storeFilePath = System.getenv("KEYSTORE_PATH") ?: findProperty("KEYSTORE_PATH") as String?
            if (!storeFilePath.isNullOrBlank() && file(storeFilePath).exists()) {
                storeFile = file(storeFilePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: findProperty("KEYSTORE_PASSWORD") as String?
                keyAlias = System.getenv("KEY_ALIAS") ?: findProperty("KEY_ALIAS") as String?
                keyPassword = System.getenv("KEY_PASSWORD") ?: findProperty("KEY_PASSWORD") as String?

                // AGP 9 defaults leave v3/v4 off. Explicitly opt in so portfolio releases carry
                // a modern signature set: v2 (baseline for Android 7+), v3 (key rotation) and
                // v4 (incremental install idsig). v1 stays off — minSdk 26 never runs on
                // devices that require the JAR-signing scheme.
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            val releaseSigning = signingConfigs.getByName("release")
            if (releaseSigning.storeFile?.exists() == true) {
                signingConfig = releaseSigning
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

// AGP 9 removed the `kotlinOptions {}` block inside `android {}`. Configure the JVM
// target via the Kotlin extension the auto-applied plugin exposes.
kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.preference)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.security.crypto)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.robolectric)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.json)

    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.truth)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.work.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}

detekt {
    toolVersion = libs.versions.detekt.get()
    config.setFrom("$rootDir/config/detekt/detekt.yml")
    buildUponDefaultConfig = true
    autoCorrect = false
    parallel = true
    ignoreFailures = false
    source.setFrom(
        files(
            "src/main/java",
            "src/main/kotlin",
            "src/test/java",
            "src/test/kotlin",
            "src/androidTest/java",
            "src/androidTest/kotlin",
        ),
    )
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    reports {
        html.required.set(true)
        xml.required.set(true)
        txt.required.set(false)
        sarif.required.set(false)
    }
}

spotless {
    kotlin {
        target("src/**/*.kt")
        targetExclude("**/build/**", "**/generated/**")
        ktlint(libs.versions.ktlint.get())
            .editorConfigOverride(
                mapOf(
                    "ktlint_standard_no-wildcard-imports" to "enabled",
                    "ktlint_standard_max-line-length" to "enabled",
                    "max_line_length" to "140",
                ),
            )
    }
    kotlinGradle {
        target("*.kts", "**/*.kts")
        targetExclude("**/build/**")
        ktlint(libs.versions.ktlint.get())
    }
}

// Coverage reporting deliberately disabled here — see the plugins block for the reason.
