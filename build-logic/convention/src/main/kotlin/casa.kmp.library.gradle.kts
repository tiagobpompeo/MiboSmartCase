import br.com.pompeo.casa.buildlogic.configureKmpLibrary

/*
 * Convenção "biblioteca KMP deste app": todo módulo de core/ aplica este plugin e ganha, sem repetir nada,
 * os mesmos alvos (Android, iPhone físico e simulador), o mesmo JVM target, o namespace Android derivado do
 * caminho do módulo (:core:domain → br.com.pompeo.casa.core.domain), os testes na JVM do host e as
 * dependências comuns de testes. Só o módulo :shared (umbrella) produz o framework para o Xcode.
 */
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
}

configureKmpLibrary()
