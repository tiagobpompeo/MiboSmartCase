package br.com.pompeo.casa.di

import br.com.pompeo.casa.GdiBuildConfig
import br.com.pompeo.casa.data.DeviceRepositoryImpl
import br.com.pompeo.casa.data.TokenRepositoryImpl
import br.com.pompeo.casa.data.gdi.GdiApi
import br.com.pompeo.casa.data.gdi.GdiDeviceProvider
import br.com.pompeo.casa.data.gdi.GdiErrorMapper
import br.com.pompeo.casa.domain.APP_SCOPE_NAME
import br.com.pompeo.casa.domain.ErrorMapper
import br.com.pompeo.casa.domain.provider.DeviceProvider
import br.com.pompeo.casa.domain.repository.DeviceRepository
import br.com.pompeo.casa.domain.repository.TokenRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** Armazenamento seguro do token é nativo: Keystore no Android, Keychain no iOS. */
expect val platformModule: Module

/** Qualificador do escopo que vive enquanto o app vive (comandos físicos e encerramento de sessão de vídeo). */
val APP_SCOPE = named(APP_SCOPE_NAME)

// Módulo Koin da camada de dados. Cada implementação é registrada pela INTERFACE do domain
// (single<TokenRepository> { …Impl }), então quem injeta nunca vê a classe concreta. As features registram
// os próprios ViewModels; o umbrella :shared junta tudo em initKoin.
val dataModule = module {
    // Escopo da aplicação: SupervisorJob para a falha de um job não cancelar os outros. Usado pelo repositório
    // e por trabalhos que não podem morrer com uma tela (comando na fechadura, stopLive ao sair da câmera).
    single(APP_SCOPE) { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    // GdiBuildConfig.TOKEN (local.properties) é só sugestão para a tela de token, nunca login automático.
    single<TokenRepository> { TokenRepositoryImpl(storage = get(), suggested = GdiBuildConfig.TOKEN) }
    single { GdiApi(tokens = get(), baseUrl = GdiBuildConfig.BASE_URL) }
    // Outro parceiro = outro single<DeviceProvider>(named("...")); o repositório recebe todos via getAll.
    single<DeviceProvider>(named("gdi")) { GdiDeviceProvider(get()) }
    single<ErrorMapper> { GdiErrorMapper }
    single<DeviceRepository> {
        DeviceRepositoryImpl(tokens = get(), providers = getAll<DeviceProvider>(), scope = get(APP_SCOPE))
    }
}
