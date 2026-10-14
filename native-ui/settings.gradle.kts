// Projeto separado do case: UI nativa (Jetpack Compose no Android, SwiftUI no iOS) + shared só com regra e dados.
// Build Gradle independente do projeto da raiz: nada do projeto existente é alterado.
rootProject.name = "CasaNativeUi"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
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
        mavenCentral()
    }
}
include(":shared")
include(":androidApp")
