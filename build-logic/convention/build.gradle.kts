import org.gradle.plugin.use.PluginDependency

// Os plugins de convenção são scripts .gradle.kts pré-compilados em src/main/kotlin (kotlin-dsl).
// Cada arquivo casa.*.gradle.kts vira um plugin com esse id, aplicável nos módulos com plugins { id("casa.…") }.
plugins {
    `kotlin-dsl`
}

group = "br.com.pompeo.casa.buildlogic"

// Para aplicar um plugin por id dentro de um script pré-compilado, o artefato dele precisa estar no classpath
// deste módulo. O "marker" <id>:<id>.gradle.plugin:<versão> resolve para o artefato real; as versões vêm do catálogo.
fun Provider<PluginDependency>.asDependency(): Provider<String> =
    map { "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version}" }

dependencies {
    // Deixa o catálogo tipado (libs.ktor.client.core…) disponível dentro dos scripts de convenção.
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
    compileOnly(libs.plugins.kotlinMultiplatform.asDependency())
    compileOnly(libs.plugins.androidMultiplatformLibrary.asDependency())
    compileOnly(libs.plugins.composeMultiplatform.asDependency())
    compileOnly(libs.plugins.composeCompiler.asDependency())
    compileOnly(libs.plugins.kotlinxSerialization.asDependency())
}
