// Build incluído (includeBuild) com os plugins de convenção: um lugar só para "o que é um módulo KMP deste app".
// Padrão de mercado (Now in Android, documentação do Gradle): a configuração repetida sai dos build.gradle.kts
// dos módulos e vira um plugin com nome, versionado junto com o código.
rootProject.name = "build-logic"

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
        gradlePluginPortal()
    }
    // O mesmo catálogo de versões do projeto: os plugins de convenção leem versões e bibliotecas de lá.
    versionCatalogs {
        create("libs") { from(files("../gradle/libs.versions.toml")) }
    }
}

include(":convention")
