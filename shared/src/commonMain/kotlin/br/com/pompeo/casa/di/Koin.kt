package br.com.pompeo.casa.di

import br.com.pompeo.casa.feature.camera.cameraFeatureModule
import br.com.pompeo.casa.feature.device.deviceFeatureModule
import br.com.pompeo.casa.feature.home.homeFeatureModule
import br.com.pompeo.casa.feature.token.tokenFeatureModule
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration

// Hilt só existe no Android; em código multiplataforma o Koin cumpre o papel de injeção.
// Raiz de composição: o umbrella é o único módulo que conhece todos os outros. Cada módulo registra o que é
// seu (dataModule e platformModule em :core:data; cada feature o próprio ViewModel) e aqui só se junta tudo.
// Feature nova = mais um módulo Koin nesta lista e mais uma entrada no NavHost de App.kt; nada mais muda.
val appModules = listOf(
    dataModule,
    platformModule,
    tokenFeatureModule,
    homeFeatureModule,
    cameraFeatureModule,
    deviceFeatureModule,
)

/** Chamado uma vez pela Application no Android. */
fun initKoin(config: KoinAppDeclaration): KoinApplication = startKoin {
    config()
    modules(appModules)
}

/** Sem parâmetro para o Swift (`KoinKt.doInitKoin()`): valores padrão não são exportados ao Obj-C. */
fun initKoin(): KoinApplication = initKoin {}
