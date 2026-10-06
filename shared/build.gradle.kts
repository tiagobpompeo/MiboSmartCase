import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinxSerialization) // rotas tipadas da navegação são @Serializable
}

kotlin {
    // iosArm64 = iPhone físico; iosSimulatorArm64 = simulador no Mac Apple Silicon. Sem iosX64.
    listOf(iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"  // nome do módulo Swift: import Shared
            isStatic = true      // linkado dentro do executável: não precisa embarcar framework dinâmico
        }
    }

    android {
        namespace = "br.com.pompeo.casa.shared" // diferente do namespace do app
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions { jvmTarget = JvmTarget.JVM_11 }
        // Roda commonTest na JVM do host, sem emulador: ./gradlew :shared:testAndroidHostTest
        withHostTest {}
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.compose.material.icons.core)
            implementation(libs.compose.material.icons.extended)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.navigation.compose)
            implementation(libs.koin.core)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
        androidMain.dependencies {
            // Engine do Ktor escolhida pelo classpath: HttpClient {} sem engine explícita usa OkHttp aqui.
            implementation(libs.ktor.client.okhttp)
            // androidContext() para o KeystoreTokenStorage no platformModule.
            implementation(libs.koin.android)
            // RTSP da nuvem Intelbras: H.265 sem "fmtp" no SDP, que o Media3 não aceita; o libVLC aceita.
            implementation(libs.libvlc)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin) // ... e Darwin (NSURLSession) aqui
        }
    }
}

// Lê o token GDI de local.properties (ignorado pelo Git) e gera GdiBuildConfig em commonMain.
// O valor só pré-preenche a tela de token (desenvolvimento), nunca é login automático, e só entra em
// build não release; -PgdiDevToken=true|false força. Em produção o token ficaria num backend.
val gdiConfig = tasks.register("generateGdiConfig") {
    val props = Properties()
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { props.load(it) }
    // Release = task pedida (assembleRelease, bundleRelease, linkReleaseFramework…) ou Xcode em Release
    // (CONFIGURATION chega ao embedAndSignAppleFrameworkForXcode). Os dois são entradas do configuration cache.
    val releaseBuild = gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) } ||
        providers.environmentVariable("CONFIGURATION").orNull == "Release"
    val devToken = project.findProperty("gdiDevToken")?.toString()?.let { it != "false" } ?: !releaseBuild
    val token = if (devToken) props.getProperty("gdi.token", "") else ""
    val baseUrl = props.getProperty("gdi.baseUrl", "https://api-casainteligente.intelbras.com.br")
    val outDir = layout.buildDirectory.dir("generated/gdi/commonMain/kotlin")
    inputs.property("token", token)     // muda o valor => a task roda de novo
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
// Passar o TaskProvider (e não um caminho) cria a dependência de build sozinho, para Android e iOS.
kotlin.sourceSets.commonMain { kotlin.srcDir(gdiConfig) }

dependencies {
    // Nome da configuração no plugin KMP-library que põe o ui-tooling no runtime Android (Previews na IDE).
    androidRuntimeClasspath(libs.compose.uiTooling)
}
