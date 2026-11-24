pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven(url = "https://jitpack.io")
    }
}

include(":google-gsa")
project(":google-gsa").projectDir = File(rootDir, "google-gsa")

rootProject.name = "Whisper"
include(":app")
// Records the startup profile on a phone (board 66). Not part of the app.
include(":baselineprofile")
 