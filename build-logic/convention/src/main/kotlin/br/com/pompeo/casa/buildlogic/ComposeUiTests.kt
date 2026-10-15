package br.com.pompeo.casa.buildlogic

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Property
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.the
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import java.io.File

/** Nome que identifica uma classe de teste de UI (Compose) dentro de commonTest, por exemplo TokenScreenUiTest. */
private const val UI_TEST_CLASS_PATTERN = "**/*UiTest*"

/**
 * Testes de UI em Compose Multiplatform, escritos uma vez em commonTest (runComposeUiTest) e executados:
 *  - no simulador iOS, pelo mesmo binário de teste dos unitários (`:feature:x:iosSimulatorArm64Test`);
 *  - no Android, como teste instrumentado no aparelho (`:feature:x:connectedAndroidTest`), porque precisam de
 *    uma Activity. `sourceSetTreeName = "test"` põe o source set androidDeviceTest na árvore de commonTest.
 * Na JVM do host não há Activity, então as classes *UiTest ficam fora de testAndroidHostTest; os unitários
 * continuam lá.
 */
fun Project.configureComposeUiTests() {
    val libs = the<LibrariesForLibs>()
    extensions.configure<KotlinMultiplatformExtension> {
        (this as ExtensionAware).extensions.configure<KotlinMultiplatformAndroidLibraryTarget>("android") {
            withDeviceTestBuilder { sourceSetTreeName = "test" }.configure {
                instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }
        }
        sourceSets.getByName("commonTest").dependencies {
            implementation(libs.compose.uiTest)
        }
        sourceSets.getByName("androidDeviceTest").dependencies {
            implementation(libs.androidx.compose.uiTestJunit4)
            // Declara a ComponentActivity que runComposeUiTest usa no APK de teste (sem AndroidManifest à mão).
            implementation(libs.androidx.compose.uiTestManifest)
            implementation(libs.androidx.test.runner)
        }
    }
    tasks.withType<Test>().configureEach {
        if (name == "testAndroidHostTest") exclude(UI_TEST_CLASS_PATTERN)
    }
    // Compose Multiplatform 1.12.1 registra a cópia de composeResources também para a variante de teste em
    // aparelho, mas sem configurar as propriedades, e o Gradle recusa a task. As features não têm
    // composeResources, então a cópia é vazia: só damos valores padrão para a task validar.
    // (A classe da task, CopyResourcesToAndroidAssetsTask, é internal no plugin; as propriedades são
    // alcançadas pelo nome.)
    tasks.matching { it.name.contains("AndroidDeviceTestComposeResourcesToAndroidAssets") }.configureEach {
        @Suppress("UNCHECKED_CAST")
        (property("from") as Property<FileCollection>).convention(project.files())
        @Suppress("UNCHECKED_CAST")
        (property("relativeResourcePlacement") as Property<File>).convention(File("composeResources"))
        (property("outputDirectory") as DirectoryProperty)
            .convention(layout.buildDirectory.dir("generated/composeResources/$name"))
    }
}
