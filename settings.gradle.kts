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
        // Sumsub's identity-verification SDK is only published here, and only it is taken from here.
        maven("https://maven.sumsub.com/repository/maven-public/") {
            content { includeGroupByRegex("com\\.sumsub.*") }
        }
        // Sumsub's device-intelligence module depends on Fingerprint, published only here.
        maven("https://maven.fpregistry.io/releases") {
            content { includeGroup("com.fingerprint.android") }
        }
    }
}

rootProject.name = "fundaza-android"
include(":app")
