// Design system e componentes compartilhados pelas features (cores Mibo, FitText, cartões, diálogos),
// mais o que é de plataforma e serve a várias telas: flag de debug e calendário (expect/actual).
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
        }
        commonTest.dependencies {
            implementation(projects.core.testing)
        }
        androidMain.dependencies {
            // isDebugBuild lê a flag do app hospedeiro pelo Context registrado no Koin.
            implementation(libs.koin.android)
        }
    }
}
