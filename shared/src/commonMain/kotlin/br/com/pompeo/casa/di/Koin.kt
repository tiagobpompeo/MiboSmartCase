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
import br.com.pompeo.casa.ui.HomeViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

/** Armazenamento seguro do token é nativo: Keystore no Android, Keychain no iOS. */
expect val platformModule: Module

// Hilt só existe no Android; em código multiplataforma o Koin cumpre o papel de injeção.
// Raiz de composição: único arquivo que conhece domain, data e ui. Cada implementação é registrada pela
// INTERFACE do domain (single<TokenRepository> { …Impl }), então quem injeta nunca vê a classe concreta.
val appModule = module {
    // ---------- data ----------
    // GdiBuildConfig.TOKEN (local.properties) é só sugestão para a tela de token, nunca login automático.
    single<TokenRepository> { TokenRepositoryImpl(storage = get(), suggested = GdiBuildConfig.TOKEN) }
    single { GdiApi(tokens = get(), baseUrl = GdiBuildConfig.BASE_URL) }
    // Outro parceiro = outro single<DeviceProvider>(named("...")); o repositório recebe todos via getAll.
    single<DeviceProvider>(named("gdi")) { GdiDeviceProvider(get()) }
    single<ErrorMapper> { GdiErrorMapper }
    single<DeviceRepository> {
        DeviceRepositoryImpl(
            tokens = get(),
            providers = getAll<DeviceProvider>(),
            // Vive enquanto o app vive; SupervisorJob: a falha de um job não cancela os outros.
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )
    }
    // ---------- domain: casos de uso sem estado, uma instância por injeção ----------
    factoryOf(::SubmitTokenUseCase)
    factoryOf(::ChangeLockVolumeUseCase)
    // ---------- apresentação ----------
    viewModelOf(::HomeViewModel)
}

/** Chamado uma vez pela Application no Android. */
fun initKoin(config: KoinAppDeclaration): KoinApplication = startKoin {
    config()
    modules(appModule, platformModule)
}

/** Sem parâmetro para o Swift (`KoinKt.doInitKoin()`): valores padrão não são exportados ao Obj-C. */
fun initKoin(): KoinApplication = initKoin {}
