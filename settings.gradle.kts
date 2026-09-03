pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google(); mavenCentral()
        maven { url = uri("https://jitpack.io") }   // para 7-Zip-JBinding
    }
}
rootProject.name = "LectorComics"
include(":app")
include(":shared")
