import br.com.pompeo.casa.buildlogic.configureKmpLibrary
import org.gradle.accessors.dm.LibrariesForLibs

/*
 * Convenção "módulo com tela": biblioteca KMP + Compose Multiplatform + navegação tipada (rotas @Serializable)
 * + ViewModel e Koin para Compose. Aplicado em core/ui, core/player, nas features e no umbrella :shared.
 * Atenção nos comentários deste arquivo: Kotlin aninha comentários de bloco, então uma barra seguida de
 * asterisco dentro de um comentário engole o resto do script (aconteceu uma vez, com "feature/" + asterisco).
 */
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

configureKmpLibrary()

val libs = the<LibrariesForLibs>()

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.compose.material.icons.core)
            implementation(libs.compose.material.icons.extended)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.navigation.compose)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.koin.core)
            implementation(libs.koin.compose.viewmodel)
        }
    }
}
