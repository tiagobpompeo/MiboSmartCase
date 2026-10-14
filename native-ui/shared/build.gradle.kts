import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// shared = só regra de negócio e dados (domain, data, di) e a ponte para o Swift (sdk). Sem Compose:
// as telas são nativas em cada app. Por isso não há plugin composeMultiplatform nem composeCompiler aqui.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.kotlinxSerialization)
}

kotlin {
    // iosArm64 = iPhone físico; iosSimulatorArm64 = simulador no Mac Apple Silicon.
    listOf(iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"  // nome do módulo Swift: import Shared
            isStatic = true
            // Sem Compose no framework, o binário é bem menor que o do projeto principal.
        }
    }

    android {
        namespace = "br.com.pompeo.casa.nativeui.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions { jvmTarget = JvmTarget.JVM_11 }
        // Roda commonTest na JVM do host, sem emulador: ./gradlew :shared:testAndroidHostTest
        withHostTest {}
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.koin.core)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.koin.android) // androidContext() para o KeystoreTokenStorage
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
    }
}

// Lê o token GDI de local.properties (ignorado pelo Git) e gera GdiBuildConfig em commonMain.
// Procura primeiro em native-ui/local.properties e depois no local.properties da raiz do repositório,
// para o token ficar num lugar só. Só entra em build não release; -PgdiDevToken=true|false força.
val gdiConfig = tasks.register("generateGdiConfig") {
    val props = Properties()
    // Fora do repositório do case, o token continua vindo do local.properties dele (pasta irmã "MiboSmart Case").
    listOf(rootProject.file("../MiboSmart Case/local.properties"), rootProject.file("../local.properties"), rootProject.file("local.properties"))
        .filter { it.exists() }
        .forEach { file -> file.inputStream().use { props.load(it) } } // o mais específico sobrescreve
    val releaseBuild = gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) } ||
        providers.environmentVariable("CONFIGURATION").orNull == "Release"
    val devToken = project.findProperty("gdiDevToken")?.toString()?.let { it != "false" } ?: !releaseBuild
    val token = if (devToken) props.getProperty("gdi.token", "") else ""
    val baseUrl = props.getProperty("gdi.baseUrl", "https://api-casainteligente.intelbras.com.br")
    val outDir = layout.buildDirectory.dir("generated/gdi/commonMain/kotlin")
    inputs.property("token", token)
    inputs.property("baseUrl", baseUrl)
    outputs.dir(outDir)
    doLast {
        val file = outDir.get().file("br/com/pompeo/casa/GdiBuildConfig.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            package br.com.pompeo.casa

            /** Gerado pelo Gradle a partir de local.properties. Não editar. */
            object GdiBuildConfig {
                const val TOKEN: String = "${token.replace("\\", "\\\\").replace("\"", "\\\"")}"
                const val BASE_URL: String = "$baseUrl"
            }
            """.trimIndent()
        )
    }
}
kotlin.sourceSets.commonMain { kotlin.srcDir(gdiConfig) }
