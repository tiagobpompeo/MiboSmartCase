rootProject.name = "MobiSmartCase"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS") // permite implementation(projects.shared)

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral() // org.jetbrains.* (Compose MP, lifecycle e navigation JetBrains) vêm daqui
    }
}

include(":shared")
include(":androidApp")
