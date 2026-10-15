import br.com.pompeo.casa.buildlogic.configureComposeMultiplatform
import br.com.pompeo.casa.buildlogic.configureComposeUiTests
import br.com.pompeo.casa.buildlogic.configureKmpLibrary

/*
 * Convenção "feature": uma tela (ou fluxo) com ViewModel, rota e módulo Koin. É casa.kmp.compose mais:
 *  - testes de UI em commonTest (simulador iOS e aparelho Android), ver configureComposeUiTests;
 *  - as dependências que toda feature tem: api de core:domain (modelos e interfaces aparecem na API pública
 *    da feature), core:ui (design system) e, só nos testes, core:testing (fakes).
 * Uma feature nunca declara outra feature nem core:data; o que ela precisa dos dados vem pelas interfaces
 * do domínio, injetadas pelo Koin no umbrella.
 */
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

configureKmpLibrary()
configureComposeMultiplatform()
configureComposeUiTests()

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:domain"))
            implementation(project(":core:ui"))
        }
        commonTest.dependencies {
            implementation(project(":core:testing"))
        }
    }
}
