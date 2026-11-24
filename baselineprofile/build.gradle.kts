/**
 * Records Whisper's startup profile on a phone, and measures start-up
 * (board 66, Szept 177). Not part of the app; F-Droid's build never runs it.
 *
 * Record (phone on USB, USB debugging on):
 *     ./gradlew :app:generateReleaseBaselineProfile
 * Measure, with and without the profile:
 *     ./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest
 *
 * Both install "Whisper Profile" (com.nyancatlabs.whisper.profile), a
 * separate app, and never touch the preview or the Play version.
 */
plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.nyancatlabs.whisper.baselineprofile"
    compileSdk = 37

    defaultConfig {
        // Baseline profiles are recorded on Android 9 or later.
        minSdk = 28
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"
}

kotlin {
    jvmToolchain(libs.versions.jvmVersion.get().toInt())
}

baselineProfile {
    // The phone on the desk, not an emulator: this machine may have none.
    useConnectedDevices = true
}

dependencies {
    implementation(libs.test.ext)
    implementation(libs.uiautomator)
    implementation(libs.benchmark.macro.junit4)
}
