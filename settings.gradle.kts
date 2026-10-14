rootProject.name = "MobiSmartCase"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS") // permite implementation(projects.shared)

pluginManagement {
    // Plugins de convenção (casa.kmp.library, casa.kmp.compose) vêm do build incluído build-logic/.
    includeBuild("build-logic")
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

// Módulos: core (sem tela) e feature (uma tela ou fluxo por módulo); :shared é o umbrella que monta o app
// e gera o framework do iOS. Regra: feature → core; feature nunca depende de feature; só :shared vê todas.
include(":core:domain")
include(":core:data")
include(":core:ui")
include(":core:player")
include(":core:testing")
include(":feature:token")
include(":feature:home")
include(":feature:camera")
include(":feature:device")
include(":shared")
include(":androidApp")
