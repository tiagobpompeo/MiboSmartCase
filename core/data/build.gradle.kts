import java.util.Properties

// Implementações: Ktor contra a GDI, parser, mapeador de erros, repositórios e os cofres de token nativos
// (Keystore / Keychain). Expõe dataModule e platformModule ao Koin. Depende só de :core:domain.
plugins {
    id("casa.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.domain)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.koin.core)
        }
        commonTest.dependencies {
            implementation(projects.core.testing)
            implementation(libs.ktor.client.mock)
        }
        androidMain.dependencies {
            // Engine do Ktor escolhida pelo classpath: HttpClient {} sem engine explícita usa OkHttp aqui.
            implementation(libs.ktor.client.okhttp)
            // androidContext() para o KeystoreTokenStorage no platformModule.
            implementation(libs.koin.android)
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
