// Maven Central answers 429 Too Many Requests for some large files from
// shared networks (GitHub runners, cloud containers), Robolectric's jars
// above all. This reads Central through Google's mirror of it instead.
// Used by CI (.github/workflows/android.yml) and Claude's containers only;
// the repo's own build files, and so F-Droid's build, are unchanged.
val mirror = "https://maven-central.storage-download.googleapis.com/maven2/"
fun RepositoryHandler.useMirror() = all {
    if (this is MavenArtifactRepository && url.host in setOf("repo.maven.apache.org", "repo1.maven.org"))
        setUrl(mirror)
}
settingsEvaluated {
    pluginManagement.repositories.useMirror()
    dependencyResolutionManagement.repositories.useMirror()
}
// Robolectric fetches its android-all jars itself, outside Gradle.
allprojects {
    tasks.withType<Test>().configureEach {
        systemProperty("robolectric.dependency.repo.url", mirror)
    }
}
