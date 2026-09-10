pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("com\\.google\\.devtools.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
        maven {
            url = uri("https://storage.googleapis.com/download.tensorflow.org/maven")
        }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // El repositorio de TensorFlow ya está declarado en pluginManagement,
        // no es necesario duplicarlo aquí para dependencias del proyecto.

        // Añadido el repositorio de JitPack para LibSU
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "Speccy OS E5 Ultra V 0.2.1b"
include(":app")
