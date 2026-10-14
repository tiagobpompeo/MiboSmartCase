package br.com.pompeo.casa.di

import br.com.pompeo.casa.GdiBuildConfig
import br.com.pompeo.casa.data.DeviceRepositoryImpl
import br.com.pompeo.casa.data.TokenRepositoryImpl
import br.com.pompeo.casa.data.gdi.GdiApi
import br.com.pompeo.casa.data.gdi.GdiDeviceProvider
import br.com.pompeo.casa.data.gdi.GdiErrorMapper
import br.com.pompeo.casa.domain.ErrorMapper
import br.com.pompeo.casa.domain.provider.DeviceProvider
import br.com.pompeo.casa.domain.repository.DeviceRepository
import br.com.pompeo.casa.domain.repository.TokenRepository
import br.com.pompeo.casa.domain.usecase.ChangeLockVolumeUseCase
import br.com.pompeo.casa.domain.usecase.SubmitTokenUseCase
import br.com.pompeo.casa.sdk.CasaSdk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.core.qualifier.named
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

/** Armazenamento seguro do token é nativo: Keystore no Android, Keychain no iOS. */
expect val platformModule: Module

// Raiz de composição do shared: só domain, data e a ponte para o Swift. Nenhum ViewModel aqui: cada app
// nativo tem o seu (Android: AndroidX ViewModel em Kotlin; iOS: ObservableObject em Swift).
val appModule = module {
    // ---------- data ----------
    single<TokenRepository> { TokenRepositoryImpl(storage = get(), suggested = GdiBuildConfig.TOKEN) }
    single { GdiApi(tokens = get(), baseUrl = GdiBuildConfig.BASE_URL) }
    single<DeviceProvider>(named("gdi")) { GdiDeviceProvider(get()) }
    single<ErrorMapper> { GdiErrorMapper }
    single<DeviceRepository> {
        DeviceRepositoryImpl(
            tokens = get(),
            providers = getAll<DeviceProvider>(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )
    }
    // ---------- domain ----------
    factoryOf(::SubmitTokenUseCase)
    factoryOf(::ChangeLockVolumeUseCase)
    // ---------- ponte para o Swift ----------
    // O Android injeta repositórios e casos de uso direto no seu ViewModel; o iOS não fala Koin e recebe
    // tudo por esta fachada (CasaSdkKt.casaSdk() no Swift).
    single { CasaSdk(tokens = get(), devices = get(), submitToken = get(), changeLockVolume = get(), errors = get()) }
}

/** Chamado uma vez pela Application no Android (com androidContext e o módulo do ViewModel). */
fun initKoin(config: KoinAppDeclaration): KoinApplication = startKoin {
    config()
    modules(appModule, platformModule)
}

/** Sem parâmetro para o Swift (`KoinKt.doInitKoin()`): valores padrão não são exportados ao Obj-C. */
fun initKoin(): KoinApplication = initKoin {}
