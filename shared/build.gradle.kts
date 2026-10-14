import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Umbrella: o único módulo que conhece todas as features. Monta a navegação (App.kt), junta os módulos Koin
// (initKoin) e gera o framework "Shared" que o Xcode importa. Nenhuma regra de negócio mora aqui.
plugins {
    // O plugin Kotlin Multiplatform vem declarado aqui também: é dele que o Gradle gera os acessores tipados
    // (kotlin { }, sourceSets.commonMain) deste script. A configuração continua toda em casa.kmp.compose.
    alias(libs.plugins.kotlinMultiplatform)
    id("casa.kmp.compose")
}

kotlin {
    listOf(iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"  // nome do módulo Swift: import Shared
            isStatic = true      // linkado dentro do executável: não precisa embarcar framework dinâmico
            // O Swift implementa NativeVideoPlayer e registra a fábrica em NativePlayers: essas declarações
            // precisam aparecer no cabeçalho do framework, por isso o módulo é exportado.
            export(projects.core.player)
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.core.domain)
            implementation(projects.core.data)
            implementation(projects.core.ui)
            api(projects.core.player)
            implementation(projects.feature.token)
            implementation(projects.feature.home)
            implementation(projects.feature.camera)
            implementation(projects.feature.device)
        }
        androidMain.dependencies {
            implementation(libs.koin.android)
        }
    }
}

dependencies {
    // Nome da configuração no plugin KMP-library que põe o ui-tooling no runtime Android (Previews na IDE).
    "androidRuntimeClasspath"(libs.compose.uiTooling)
}
