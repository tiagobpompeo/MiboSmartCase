// RF03: vídeo ao vivo. É a única feature que usa core:player (o expect/actual do VLC).
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
            implementation(projects.core.player)
        }
        commonTest.dependencies {
            implementation(projects.core.testing)
        }
    }
}
