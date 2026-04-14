pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()

        // ⚠️  SUPPLY-CHAIN WARNING: The repository below is an unofficial
        // GitHub-hosted Maven mirror for tor-android (Guardian Project).
        // It is only needed when tor-android dependency is uncommented in
        // app/build.gradle.kts. Evaluate trust before enabling Tor support.
        // maven { url = uri("https://raw.githubusercontent.com/nickcoolshen/nickcoolshen.github.io/master/") }

        // JitPack — kept as fallback for any future community libraries.
        // Prefer mavenCentral/google for all first-party dependencies.
        // maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "Pionen"
include(":app")
