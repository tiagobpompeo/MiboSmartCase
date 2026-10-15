import br.com.pompeo.casa.buildlogic.configureComposeMultiplatform
import br.com.pompeo.casa.buildlogic.configureKmpLibrary

/*
 * Convenção "módulo com Compose, sem tela própria": biblioteca KMP + Compose Multiplatform + navegação tipada
 * + ViewModel e Koin para Compose. Aplicada em core/ui, core/player e no umbrella :shared. As features usam
 * casa.kmp.feature, que acrescenta os testes de UI e as dependências do core.
 * Atenção nos comentários deste arquivo: Kotlin aninha comentários de bloco, então uma barra seguida de
 * asterisco dentro de um comentário engole o resto do script (aconteceu uma vez, com "feature/" + asterisco).
 */
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

configureKmpLibrary()
configureComposeMultiplatform()
