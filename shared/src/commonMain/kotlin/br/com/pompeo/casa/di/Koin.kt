package br.com.pompeo.casa.di

import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

/** Armazenamento seguro do token é nativo: Keystore no Android, Keychain no iOS. */
expect val platformModule: Module

val appModule = module { }

/** Chamado uma vez pela Application no Android. */
fun initKoin(config: KoinAppDeclaration): KoinApplication = startKoin {
    config()
    modules(appModule, platformModule)
}

/** Sem parâmetro para o Swift (`KoinKt.doInitKoin()`): valores padrão não são exportados ao Obj-C. */
fun initKoin(): KoinApplication = initKoin {}
