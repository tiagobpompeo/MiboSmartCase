// Kotlin puro: modelos, interfaces de repositório e de parceiro, casos de uso, erros. Nenhuma dependência de
// Android, iOS, Ktor, Koin ou Compose, de propósito: é a camada que o resto importa e que nada do resto suja.
plugins {
    id("casa.kmp.library")
}

kotlin {
    sourceSets {
        commonTest.dependencies {
            implementation(projects.core.testing)
        }
    }
}
