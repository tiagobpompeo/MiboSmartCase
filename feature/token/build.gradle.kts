// RF01: tela do token e seu ViewModel.
// A convenção casa.kmp.feature dá: alvos Android + iOS, Compose Multiplatform, navegação, Koin, testes unitários
// (JVM do host e simulador iOS), testes de UI (simulador iOS e aparelho Android) e as dependências de toda
// feature: api(core:domain), implementation(core:ui) e core:testing só nos testes.
plugins {
    // O plugin Kotlin Multiplatform vem declarado aqui também: é dele que o Gradle gera os acessores tipados
    // (kotlin { }, sourceSets.commonMain) deste script. A configuração continua toda em casa.kmp.feature.
    alias(libs.plugins.kotlinMultiplatform)
    id("casa.kmp.feature")
}
