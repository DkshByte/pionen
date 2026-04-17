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

        // Official Guardian Project Maven Repository for Tor Android binaries
        maven { url = uri("https://raw.githubusercontent.com/guardianproject/gpmaven/master") }

        // JitPack — kept as fallback for any future community libraries.
        // Prefer mavenCentral/google for all first-party dependencies.
        // maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "Pionen"
include(":app")
