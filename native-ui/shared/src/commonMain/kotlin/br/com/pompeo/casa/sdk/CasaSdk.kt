package br.com.pompeo.casa.sdk

import br.com.pompeo.casa.domain.AppError
import br.com.pompeo.casa.domain.DevicesState
import br.com.pompeo.casa.domain.ErrorMapper
import br.com.pompeo.casa.domain.model.Device
import br.com.pompeo.casa.domain.model.DeviceQuery
import br.com.pompeo.casa.domain.model.Firmware
import br.com.pompeo.casa.domain.model.LockDetails
import br.com.pompeo.casa.domain.model.LockEvent
import br.com.pompeo.casa.domain.model.LockVolume
import br.com.pompeo.casa.domain.model.OriginFilter
import br.com.pompeo.casa.domain.model.StreamSession
import br.com.pompeo.casa.domain.repository.DeviceRepository
import br.com.pompeo.casa.domain.repository.TokenRepository
import br.com.pompeo.casa.domain.resultOf
import br.com.pompeo.casa.domain.usecase.ChangeLockVolumeUseCase
import br.com.pompeo.casa.domain.usecase.SubmitTokenUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform

/*
 * A ponte Kotlin → Swift. O que o Kotlin/Native NÃO exporta bem para o Swift e como esta fachada contorna:
 *  - Flow/StateFlow não atravessam: StateWatcher expõe value + watch(callback) com cancelamento.
 *  - Exceções de suspend derrubam o app sem @Throws: toda operação devolve Outcome (nunca lança).
 *  - Genéricos viram "lightweight generics" do Obj-C: Outcome<T> chega como Outcome<T: AnyObject>, por isso
 *    os valores são classes (LockDetails, StreamSession…) e nunca primitivos.
 *  - Int vira Int32, valores padrão não existem: as assinaturas aqui não têm parâmetro opcional.
 * O Android não usa esta classe: injeta repositórios e casos de uso direto no seu ViewModel.
 */

/** Resultado de uma operação para o Swift: sucesso com valor ou falha já traduzida em AppError. */
sealed class Outcome<out T : Any> {
    class Success<T : Any>(val value: T) : Outcome<T>()
    class Failure(val error: AppError) : Outcome<Nothing>()

    /** Atalhos para o Swift não precisar fazer cast de OutcomeSuccess<T>. */
    val valueOrNull: T? get() = when (this) { is Success -> value; is Failure -> null }
    val errorOrNull: AppError? get() = when (this) { is Success -> null; is Failure -> error }
    val isSuccess: Boolean get() = this is Success
}

/** Devolvido por [StateWatcher.watch]; o Swift cancela no deinit / onDisappear. */
class WatchHandle internal constructor(private val job: Job) {
    fun cancel() { job.cancel() }
}

/**
 * Expõe um fluxo de estado ao Swift: leitura síncrona do valor atual e observação por callback.
 * [read] é calculado na hora (nunca fica velho esperando o dispatcher), [flow] alimenta o watch.
 */
class StateWatcher<T> internal constructor(
    private val flow: Flow<T>,
    private val read: () -> T,
    private val scope: CoroutineScope,
) {
    internal constructor(state: StateFlow<T>, scope: CoroutineScope) : this(state, { state.value }, scope)

    val value: T get() = read()

    /** Emite o valor atual e cada mudança. [onEach] roda no dispatcher da fachada (Main no iOS): pode atualizar @Published direto. */
    fun watch(onEach: (T) -> Unit): WatchHandle = WatchHandle(scope.launch { flow.distinctUntilChanged().collect { onEach(it) } })
}

/** Resumo da sessão para a tela de token e as definições: nunca expõe o token inteiro. */
data class SessionInfo(val hasToken: Boolean, val maskedToken: String?, val persisted: Boolean)

/**
 * Fachada única que o SwiftUI consome. Cada ObservableObject em Swift observa os StateWatcher e chama os
 * suspend (vistos no Swift como `async throws`, que só lançam em cancelamento).
 */
class CasaSdk(
    private val tokens: TokenRepository,
    private val devices: DeviceRepository,
    private val submitToken: SubmitTokenUseCase,
    private val changeLockVolume: ChangeLockVolumeUseCase,
    private val errors: ErrorMapper,
    // Main: os callbacks atualizam a UI. Nos testes entra um dispatcher de teste.
    dispatcher: CoroutineDispatcher = Dispatchers.Main,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    // ---------- sessão (RF01) ----------
    private fun sessionInfo() = SessionInfo(hasToken = tokens.token.value != null, maskedToken = tokens.masked(), persisted = tokens.persisted)
    val session: StateWatcher<SessionInfo> = StateWatcher(flow = tokens.token.map { sessionInfo() }, read = ::sessionInfo, scope = scope)

    /** Token de local.properties (desenvolvimento): só pré-preenche o campo, nunca login automático. */
    val suggestedToken: String? get() = tokens.suggested

    /** null = aceito (e já carregou a 1.ª página); senão o erro que a tela de token mostra. */
    suspend fun submitToken(raw: String): AppError? =
        try {
            submitToken.invoke(raw)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            errors.toAppError(e)
        }

    fun logout() = devices.logout()

    // ---------- lista (RF02, RF07, RF08) ----------
    val devicesState: StateWatcher<DevicesState> = StateWatcher(devices.state, scope)
    val query: StateWatcher<DeviceQuery> = StateWatcher(devices.query, scope)
    val providerNames: List<String> get() = devices.providerNames
    fun providerName(device: Device): String? = devices.providerName(device)

    /** As telas de detalhe recebem só o ns pela navegação e buscam o Device aqui. */
    fun device(ns: String): Device? = devices.devices.value.firstOrNull { it.ns == ns }
    fun children(hubNs: String): List<Device> = devices.devices.value.filter { it.parentNs == hubNs }

    fun refresh() = devices.refresh()
    fun loadMore() = devices.loadMore()
    fun setOrigin(filter: OriginFilter) = devices.setOrigin(filter)
    fun setPageSize(size: Int) = devices.setPageSize(size)

    // ---------- câmera (RF03) ----------
    suspend fun startLive(camera: Device, channel: Int): Outcome<StreamSession> = outcome { devices.startLive(camera, channel) }

    /** Encerrar não bloqueia a UI nem falha: roda no escopo da fachada. */
    fun stopLive(session: StreamSession) { scope.launch { resultOf { devices.stopLive(session) } } }

    // ---------- fechadura e hub (RF05, RF06, RF09) ----------
    suspend fun lockDetails(lock: Device): Outcome<LockDetails> = outcome { devices.lockDetails(lock) }
    suspend fun lockHistory(lock: Device, quantity: Int): Outcome<List<LockEvent>> = outcome { devices.lockHistory(lock, quantity) }

    /** Comando físico: a UI só chama depois da confirmação do usuário. null = enviado. */
    suspend fun setLock(lock: Device, open: Boolean): AppError? = outcome<Unit> { devices.setLock(lock, open) }.errorOrNull

    /** Grava, relê e, se a releitura falhar, assume o pedido (regra do caso de uso). */
    suspend fun changeLockVolume(lock: Device, volume: LockVolume): Outcome<LockVolume> = outcome { changeLockVolume.invoke(lock, volume) }

    suspend fun firmware(device: Device): Outcome<Firmware> = outcome { devices.firmware(device) }

    /** null = a API não informou ou falhou: a tela mostra "—" em vez de inventar estado. */
    suspend fun isOnline(device: Device): Boolean? = resultOf { devices.isOnline(device) }.getOrNull()

    private suspend fun <T : Any> outcome(block: suspend () -> T): Outcome<T> =
        resultOf { block() }.fold( // resultOf é inline: a lambda suspensa precisa ser chamada dentro dela
            onSuccess = { Outcome.Success(it) },
            onFailure = { Outcome.Failure(errors.toAppError(it)) },
        )
}

/** Para o Swift: `CasaSdkKt.casaSdk()` depois de `KoinKt.doInitKoin()`. */
fun casaSdk(): CasaSdk = KoinPlatform.getKoin().get()
