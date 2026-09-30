pluginManagement {
    includeBuild("build-logic")
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
    }
}

@Suppress("UnstableApiUsage")
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "agenda"

include(":androidApp")
include(":shared")
include(":server")

include(":core:common")
include(":core:designsystem")
include(":core:network")
include(":core:database")
include(":core:domain")
include(":core:data")

include(":feature:auth:domain")
include(":feature:auth:data")
include(":feature:auth:presentation")
include(":feature:calendar")
include(":feature:tasks")
include(":feature:streaks:domain")
include(":feature:streaks:data")
include(":feature:streaks:presentation")
