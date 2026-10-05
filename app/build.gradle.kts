import java.util.Properties

/**
 * The release signing key, if this machine has one.
 *
 * `keystore.properties` is gitignored and names a keystore that lives outside
 * the repository. Absent — on a fresh clone, or in CI — this is null and the
 * build still works: `assembleRelease` produces an unsigned APK as it always
 * has, and `assemblePreview` falls back to the debug key.
 *
 * The file holds four entries:
 *
 *     storeFile=/absolute/path/to/whisper-release.jks
 *     storePassword=…
 *     keyAlias=…
 *     keyPassword=…
 */
val releaseKeystore: Properties? = rootProject.file("keystore.properties")
    .takeIf { it.exists() }
    ?.let { file -> Properties().apply { file.inputStream().use(::load) } }

/**
 * The commit this build was made from, or null outside a git checkout.
 *
 * Every preview APK carries the same `1.0.0 (1)`, so a tester holding two of
 * them cannot tell which is which and neither can a diagnostics report — and
 * the reports are the whole of how this app is debugged on a device. A report
 * has already been read against the wrong build once, and the cost of that is
 * an afternoon spent explaining a fix by numbers that predate it.
 *
 * Resolved at configuration time and deliberately forgiving: a source archive
 * with no `.git`, a machine without git on its path, or a clone in some state
 * git dislikes all produce null and an unstamped build rather than a failure.
 * A version string is not worth breaking a build over.
 */
val gitCommit: String? = runCatching {
    providers.exec {
        commandLine("git", "rev-parse", "--short=7", "HEAD")
    }.standardOutput.asText.get().trim().takeIf { it.isNotEmpty() }
}.getOrNull()

/** Whether the tree had uncommitted changes when this was built. */
val gitDirty: Boolean = runCatching {
    providers.exec {
        commandLine("git", "status", "--porcelain")
    }.standardOutput.asText.get().isNotBlank()
}.getOrDefault(false)

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

allprojects {
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        compilerOptions {
            freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")
            freeCompilerArgs.add("-opt-in=kotlin.time.ExperimentalTime")
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
    arg("room.generateKotlin", "true")
}

android {
    namespace = "com.saulhdev.feeder"
    compileSdk = 37

    defaultConfig {
        // Nyancat Labs' domain, backwards, as every one of its apps will be.
        // Changed from io.zero76.whisper on 4 October 2026, before anything
        // was published: a store never lets an app change it afterwards.
        applicationId = "com.nyancatlabs.whisper"
        minSdk = 26
        targetSdk = 37
        // Whisper's own first release, not Neo Feed's ninth. The package
        // com.nyancatlabs.whisper has never been published, so there is no version
        // history to preserve and nothing to keep monotonic against — 1.9.0
        // would have claimed eight earlier releases that do not exist.
        versionCode = 2
        versionName = "1.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
        multiDexEnabled = true
    }

    signingConfigs {
        /**
         * The test-signing key, held in the repository on purpose.
         *
         * Without this block AGP falls back to `~/.android/debug.keystore`,
         * which is generated per machine — so a debug or preview APK built
         * here and one built on a laptop carry different signatures, and
         * Android refuses to install one over the other. A tester handed a
         * second build would have to uninstall the first and lose their feeds.
         * `app/debug.keystore` was already committed, inherited from upstream,
         * and simply never wired up; that is what it is for.
         *
         * Committing a key is normally indefensible. This one is the standard
         * Android debug key — `android`/`androiddebugkey`, the same credentials
         * every SDK install uses — so it is already public knowledge and grants
         * nobody anything they did not have. Every Android project's debug key
         * is effectively shared.
         *
         * It must never sign a release. It is public, so anyone could then
         * publish an update to a real app. The release build type deliberately
         * declares no signingConfig for exactly that reason: `assembleRelease`
         * produces an unsigned APK, and signing it is a separate, local step
         * with a key that never comes near this repository.
         */
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }

        /**
         * The real key, declared only when this machine has one.
         *
         * Registered conditionally rather than with empty strings, so a clone
         * without the keystore fails by having no config to reference — which
         * the build types handle — instead of by trying to sign with nothing
         * and failing somewhere less obvious.
         */
        releaseKeystore?.let { props ->
            create("release") {
                storeFile = file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
                // Both, so the APK verifies on everything from API 26 up.
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".dev"
            signingConfig = signingConfigs.getByName("debug")
            gitCommit?.let {
                versionNameSuffix = "-$it" + if (gitDirty) "-dirty" else ""
            }
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // Unsigned when this machine has no keystore, exactly as before.
            releaseKeystore?.let { signingConfig = signingConfigs.getByName("release") }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        /**
         * The shippable build, made installable for testing.
         *
         * Minified and resource-shrunk exactly as release is — so it exercises
         * R8, which is the only thing that can break the reflective Moshi and
         * Google Reader models, and which a debug build never runs — while
         * carrying the `.dev` application id, so it installs alongside a real
         * Whisper rather than replacing it.
         *
         * A debug APK is 31 MB of unminified dex; this is about 9. That is the
         * difference between a build that can be handed over and one that
         * cannot.
         *
         * **Signed with the real key when there is one.** It used to take the
         * debug key on the reasoning that a preview is not a release, which
         * was true and beside the point: Google Play Protect blocks the
         * install of anything signed `CN=Android Debug` outright, so a build
         * nobody can install is not a build that can be handed over either.
         * Verified on a Pixel 10 Pro — identical code and permissions, blocked
         * under the debug key and installed under a real one.
         *
         * The fallback is deliberate: without a keystore this is debug-signed
         * as before, so a clone still produces something that runs on the
         * machine that built it.
         */
        create("preview") {
            initWith(getByName("release"))
            applicationIdSuffix = ".dev"
            // The commit, so a report names the code it came from.
            //
            // Only here and on debug. A release is identified by its version
            // and its tag, and stamping a hash into a store listing's version
            // name would be noise to everybody who reads it.
            //
            // The `-dirty` mark matters more than it looks: a preview built
            // from a modified tree is not any commit, and a report from one
            // that claimed to be a commit would send somebody reading a diff
            // that never ran.
            gitCommit?.let {
                versionNameSuffix = "-$it" + if (gitDirty) "-dirty" else ""
            }
            // One line, on purpose: F-Droid deletes every line that sets a
            // signing key before it builds, and this used to span three, so
            // the deletion left half a call behind and the build file broke.
            signingConfig = signingConfigs.getByName(if (releaseKeystore != null) "release" else "debug")
            // The google-gsa module has only debug and release; without this
            // Gradle cannot decide which of them a "preview" app should use.
            matchingFallbacks += listOf("release")
        }
        all {
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    sourceSets {
        getByName("main") {
            java.directories.add("src/main/java")
            aidl.directories.add("src/main/aidl")
            assets.directories.add("src/main/assets")
            res.directories.add("src/main/res")
        }
    }

    compileOptions {
        sourceCompatibility(libs.versions.jvmVersion.get())
        targetCompatibility(libs.versions.jvmVersion.get())
    }

    buildFeatures {
        compose = true
        // dataBinding and viewBinding were both on and neither was used: no
        // layout file has a <layout> root, and nothing in the source names a
        // generated *Binding class. Each one adds a code-generation pass to
        // every build for classes nobody references.
        buildConfig = true
        aidl = true
    }

    packaging {
        resources.pickFirsts.add("rome-utils-2.1.0.jar")
    }

    // The dependency list Android Studio writes into the APK is encrypted
    // with Google's key, and F-Droid will not build an app whose APK carries
    // a block nobody else can read. Play reads it from the bundle, so the
    // bundle keeps it.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = true
    }

    lint {
        // The report is clean, so it can be a gate rather than a document.
        // With this off, a lint error changed nothing about the build and was
        // only ever found by somebody reading the report on purpose — which is
        // how an API-28 call in a minSdk-26 app, a crash on every Android 8
        // phone, sat in it unnoticed.
        abortOnError = true
        checkReleaseBuilds = true
        disable += listOf(
            "MissingTranslation",
            "ExtraTranslation",
            // Every site is a context.getString inside a coroutine or a
            // callback — a snackbar being shown, a subscription being written
            // — not a value read during composition. The check cannot tell
            // those apart and flags all of them, and seven standing errors
            // hide the ones that matter: an API-28 call on a minSdk-26 app sat
            // in this report behind them for a day.
            "LocalContextGetResourceValueCall",
            // Verified guarded: the receiver is registered with
            // RECEIVER_EXPORTED above TIRAMISU and without the flag below it,
            // which is correct. Lint reads only the else branch.
            "UnspecifiedRegisterReceiverFlag",
        )
    }
}

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            val versionName = output.versionName.get()
            output.outputFileName.set("Whisper_${versionName}_${variant.name}.apk")
        }
    }
}

kotlin {
    jvmToolchain(libs.versions.jvmVersion.get().toInt())
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    implementation(project(":google-gsa"))

    implementation(libs.symbol.processing.api)
    implementation(libs.stdlib)
    implementation(libs.serialization.json)

    //Core
    implementation(libs.appcompat)
    implementation(libs.core.ktx)
    implementation(libs.core.splashscreen)
    implementation(libs.multidex)
    implementation(libs.swiperefreshlayout)
    implementation(libs.work.runtime.ktx)
    implementation(libs.datetime)
    implementation(libs.material)
    implementation(libs.browser)
    implementation(libs.materialkolor)
    implementation(libs.collections.immutable)

    implementation(libs.datastore.preferences)
    implementation(libs.lifecycle.common.java8)
    implementation(libs.lifecycle.livedata.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.service)
    implementation(libs.lifecycle.viewmodel.ktx)

    //Compose
    api(platform(libs.compose.bom))
    implementation(libs.compose.animation)
    implementation(libs.compose.foundation)
    implementation(libs.compose.foundation.layout)
    implementation(libs.compose.material3)
    implementation(libs.compose.material3.navigationsuite)
    implementation(libs.compose.adaptive)
    implementation(libs.compose.adaptive.layout)
    implementation(libs.compose.adaptive.navigation)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.navigation.compose)

    //Room
    implementation(libs.room.ktx)
    implementation(libs.room.paging)
    implementation(libs.room.runtime)
    ksp(libs.room.compiler)

    //Security
    implementation(libs.security.crypto)
    implementation(libs.documentfile)

    //Squareup
    implementation(libs.moshi)
    implementation(libs.moshi.kotlin)
    implementation(libs.okhttp)
    implementation(libs.retrofit) { exclude(module = "okhttp") }
    implementation(libs.retrofit.converter.gson)

    //Coil
    implementation(libs.coil)
    implementation(libs.coil.compose)

    //Koin
    api(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.koin.compose)
    implementation(libs.koin.startup)
    implementation(libs.koin.annotations)
    ksp(libs.koin.compiler)
    testImplementation(libs.koin.test)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.work.testing)

    //Libs
    implementation(libs.threetenabp)
    implementation(libs.rome) { exclude(module = "rome-utils") }
    implementation(libs.rome.modules)
    implementation(libs.simple.storage)
    implementation(libs.readability4j)
    implementation(libs.tagsoup)
    implementation(libs.jsoup)
    implementation(libs.slf4j)

    // Test
    testImplementation(libs.test.runner)
    testImplementation(libs.test.rules)
    testImplementation(libs.test.ext)
}