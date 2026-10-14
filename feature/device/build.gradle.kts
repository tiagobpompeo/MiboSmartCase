// RF05, RF06, RF09: fechadura, hub e "outros dispositivos", com o ViewModel dos comandos físicos.
plugins {
    // O plugin Kotlin Multiplatform vem declarado aqui também: é dele que o Gradle gera os acessores tipados
    // (kotlin { }, sourceSets.commonMain) deste script. A configuração continua toda em casa.kmp.compose.
    alias(libs.plugins.kotlinMultiplatform)
    id("casa.kmp.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.domain)
            implementation(projects.core.ui)
        }
        commonTest.dependencies {
            implementation(projects.core.testing)
        }
    }
}
