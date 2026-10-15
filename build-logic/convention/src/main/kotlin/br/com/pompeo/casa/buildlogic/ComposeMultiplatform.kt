package br.com.pompeo.casa.buildlogic

import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.the
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Dependências de todo módulo com Compose Multiplatform (design system, player, features, umbrella):
 * Compose, navegação tipada (rotas @Serializable), ViewModel e Koin para Compose. Chamada pelas convenções
 * casa.kmp.compose e casa.kmp.feature; os plugins em si continuam declarados no bloco plugins de cada script.
 */
fun Project.configureComposeMultiplatform() {
    val libs = the<LibrariesForLibs>()
    extensions.configure<KotlinMultiplatformExtension> {
        sourceSets.getByName("commonMain").dependencies {
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
