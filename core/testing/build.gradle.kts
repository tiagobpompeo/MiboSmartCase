// Fakes compartilhados pelos testes de todos os módulos (repositórios, parceiro, fechadura, cofre do token).
// É um módulo normal, publicado só para commonTest dos outros: padrão "core:testing" do Now in Android.
plugins {
    id("casa.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.domain)
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
