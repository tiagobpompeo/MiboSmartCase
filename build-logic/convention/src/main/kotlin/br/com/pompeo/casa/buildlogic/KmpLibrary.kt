package br.com.pompeo.casa.buildlogic

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.the
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Configuração comum a todo módulo KMP do app, chamada pelos plugins de convenção casa.kmp.library e
 * casa.kmp.compose. Fica numa função Kotlin (e não num plugin aplicado por dentro do outro) porque o Gradle só
 * gera os acessores tipados dos módulos consumidores para os plugins declarados diretamente no bloco plugins {}
 * de cada convenção.
 */
fun Project.configureKmpLibrary() {
    val libs = the<LibrariesForLibs>()
    extensions.configure<KotlinMultiplatformExtension> {
        // iosArm64 = iPhone físico; iosSimulatorArm64 = simulador em Mac Apple Silicon. Sem iosX64.
        iosArm64()
        iosSimulatorArm64()

        // O alvo Android do plugin com.android.kotlin.multiplatform.library é a extensão aninhada "android"
        // (a mesma que o script escreve como kotlin { android { … } }).
        (this as ExtensionAware).extensions.configure<KotlinMultiplatformAndroidLibraryTarget>("android") {
            // :core:domain → br.com.pompeo.casa.core.domain; :shared → br.com.pompeo.casa.shared
            namespace = "br.com.pompeo.casa" + project.path.replace(':', '.')
            compileSdk = libs.versions.android.compileSdk.get().toInt()
            minSdk = libs.versions.android.minSdk.get().toInt()
            compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
            // Roda commonTest na JVM do host, sem emulador: ./gradlew :core:domain:testAndroidHostTest
            withHostTest {}
        }

        sourceSets.getByName("commonMain").dependencies {
            implementation(libs.kotlinx.coroutines.core)
        }
        sourceSets.getByName("commonTest").dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
