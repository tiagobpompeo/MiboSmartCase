package br.com.pompeo.casa.data

import br.com.pompeo.casa.data.gdi.toAppError
import br.com.pompeo.casa.domain.DevicesState
import br.com.pompeo.casa.domain.model.Device
import br.com.pompeo.casa.domain.model.DeviceQuery
import br.com.pompeo.casa.domain.model.Firmware
import br.com.pompeo.casa.domain.model.LockDetails
import br.com.pompeo.casa.domain.model.LockEvent
import br.com.pompeo.casa.domain.model.LockVolume
import br.com.pompeo.casa.domain.model.OriginFilter
import br.com.pompeo.casa.domain.model.StreamSession
import br.com.pompeo.casa.domain.provider.DeviceProvider
import br.com.pompeo.casa.domain.provider.LockController
import br.com.pompeo.casa.domain.repository.DeviceRepository
import br.com.pompeo.casa.domain.repository.TokenRepository
import br.com.pompeo.casa.domain.resultOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Lista paginada e filtrada dos dispositivos de todos os parceiros registrados na DI. Três mecanismos de
 * concorrência, cada um para um problema: Mutex (escritas serializadas), geração (resposta velha não publica),
 * Jobs separados (trocar filtro cancela em vez de esperar).
 */
class DeviceRepositoryImpl(
    private val tokens: TokenRepository,
    private val providers: List<DeviceProvider>,
    private val scope: CoroutineScope,
) : DeviceRepository {
    private val mutableQuery = MutableStateFlow(DeviceQuery())
    override val query: StateFlow<DeviceQuery> = mutableQuery.asStateFlow()

    private val mutableState = MutableStateFlow<DevicesState>(if (tokens.token.value == null) DevicesState.NoToken else DevicesState.Loading)
    override val state: StateFlow<DevicesState> = mutableState.asStateFlow()

    private val mutableDevices = MutableStateFlow<List<Device>>(emptyList())
    override val devices: StateFlow<List<Device>> = mutableDevices.asStateFlow()

    // Carregamentos serializados: refresh e "carregar mais" nunca escrevem o estado ao mesmo tempo.
    private val loading = Mutex()

    // Cada refresh/filtro/tamanho abre uma geração; resposta de geração antiga nunca é publicada.
    // MutableStateFlow faz papel de contador atômico (update/updateAndGet são CAS): commonMain não tem AtomicInteger.
    private val generation = MutableStateFlow(0)

    // Trocar filtro/tamanho cancela em vez de esperar o lock (uma chamada lenta levaria até 20 s).
    // Referências SEPARADAS: um "carregar mais" disparado logo depois de um refresh não pode sobrescrever a do
    // refresh — ele ficaria órfão, impossível de cancelar, segurando o lock durante toda a chamada lenta.
    private var refreshJob: Job? = null
    private var loadMoreJob: Job? = null

    init {
        // Token restaurado do armazenamento seguro: carrega sem o usuário tocar em nada.
        if (tokens.token.value != null) refresh()
    }

    override val providerNames: List<String> get() = providers.map { it.displayName }
    override fun providerName(device: Device): String? = providers.firstOrNull { it.id == device.providerId }?.displayName

    override fun logout() {
        cancelLoads()                 // consulta em voo não pode republicar a lista depois de a conta sair
        generation.update { it + 1 }
        tokens.clear()
        publish(DevicesState.NoToken)
    }

    override suspend fun loadFirstPage(): DevicesState {
        // Geração marcada ANTES de esperar o lock: a consulta anterior, se ainda estiver rodando, já fica velha.
        val gen = generation.updateAndGet { it + 1 }
        return loading.withLock {
            if (tokens.token.value == null) return@withLock publish(DevicesState.NoToken)
            val query = mutableQuery.updateAndGet { it.copy(page = 1) }
            val current = mutableState.value
            // Com conteúdo na tela, só a barra fina (refreshing) para a lista não piscar; e um "carregar mais"
            // cancelado por este refresh não pode deixar loadingMore preso em true.
            mutableState.value =
                if (current is DevicesState.Content) current.copy(refreshing = true, loadingMore = false, loadMoreError = null)
                else DevicesState.Loading
            val next =
                try {
                    val fetched = fetchPage(query)
                    if (fetched.devices.isEmpty()) DevicesState.Empty(query)
                    else DevicesState.Content(fetched.devices, query, hasMore = fetched.hasMore, pagesLoaded = 1, note = fetched.note)
                } catch (e: CancellationException) {
                    throw e // nunca transformar cancelamento em erro de tela
                } catch (e: Throwable) {
                    DevicesState.Error(e.toAppError(), query)
                }
            if (gen == generation.value) publish(next) else next
        }
    }

    override fun refresh() {
        // Um refresh substitui qualquer consulta em voo, inclusive um "carregar mais".
        cancelLoads()
        refreshJob = scope.launch { loadFirstPage() }
    }

    private fun cancelLoads() {
        refreshJob?.cancel()
        loadMoreJob?.cancel()
    }

    override fun loadMore() {
        val current = mutableState.value as? DevicesState.Content ?: return
        // refreshJob ativo cobre a janela entre o launch do refresh e ele marcar refreshing = true no estado.
        if (!current.hasMore || current.loadingMore || current.refreshing || refreshJob?.isActive == true) return
        val gen = generation.value
        // Marcado FORA do lock e de forma síncrona: um segundo toque não pede a mesma página duas vezes.
        mutableState.value = current.copy(loadingMore = true, loadMoreError = null)
        loadMoreJob = scope.launch { loadNextPage(gen) }
    }

    internal suspend fun loadNextPage(gen: Int = generation.value): DevicesState = loading.withLock {
        val current = mutableState.value as? DevicesState.Content ?: return@withLock mutableState.value
        // Filtro/tamanho trocado enquanto esperava o lock: a página pedida pertence à consulta antiga.
        if (gen != generation.value) return@withLock current
        val query = current.query.copy(page = current.pagesLoaded + 1)
        val next =
            try {
                val fetched = fetchPage(query)
                current.copy(
                    devices = (current.devices + fetched.devices).distinctBy { it.providerId + it.ns },
                    hasMore = fetched.hasMore,
                    pagesLoaded = current.pagesLoaded + 1,
                    loadingMore = false,
                    loadMoreError = null,
                    note = current.note ?: fetched.note,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                current.copy(loadingMore = false, loadMoreError = e.toAppError())
            }
        if (gen == generation.value) publish(next) else current
    }

    override fun setOrigin(filter: OriginFilter) {
        mutableQuery.update { it.copy(origin = filter, page = 1) }
        refresh()
    }

    override fun setPageSize(size: Int) {
        require(size > 0) { "Itens por página deve ser positivo" }
        mutableQuery.update { it.copy(pageSize = size, page = 1) }
        refresh()
    }

    private class Fetched(val devices: List<Device>, val hasMore: Boolean, val note: String?)

    /** Chama cada parceiro com a mesma página; junta e aplica o filtro defensivo de origem. */
    private suspend fun fetchPage(query: DeviceQuery): Fetched {
        var note: String? = null
        val pages = providers.map { provider ->
            try {
                provider.listDevices(query)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // Defesa: se a API recusar o valor de "origem", repete com "todos", filtra no aparelho e avisa.
                // Se o fallback também falhar, prevalece o erro ORIGINAL.
                if (query.origin == OriginFilter.ALL) throw e
                val fallback = try {
                    provider.listDevices(query.copy(origin = OriginFilter.ALL))
                } catch (ignored: CancellationException) { throw ignored } catch (ignored: Throwable) { throw e }
                note = FILTER_ON_DEVICE_NOTE
                fallback
            }
        }
        // Se a API já filtrou, nada muda; origem desconhecida nunca é descartada.
        val devices = pages.flatMap { it.devices }.filter { query.origin.accepts(it.origin) }
        // hasMore vem das páginas CRUAS: filtrar no aparelho não encerra a paginação.
        return Fetched(devices, hasMore = pages.any { it.hasMore }, note = note)
    }

    private fun publish(state: DevicesState): DevicesState {
        mutableState.value = state
        when (state) {
            is DevicesState.Content -> mutableDevices.value = state.devices
            is DevicesState.Empty, DevicesState.NoToken -> mutableDevices.value = emptyList()
            // Erro de refresh mantém a última lista: a tela de detalhe aberta não fecha sozinha.
            is DevicesState.Error, DevicesState.Loading -> Unit
        }
        return state
    }

    // ---------- Operações por dispositivo: delegam ao parceiro dono dele ----------

    private fun providerOf(device: Device): DeviceProvider =
        providers.firstOrNull { it.id == device.providerId } ?: throw IllegalStateException("Parceiro ${device.providerId} não registrado")

    private fun locksOf(lock: Device): LockController =
        providerOf(lock).locks ?: throw IllegalStateException("${providerOf(lock).displayName} não controla fechaduras")

    // Override não repete o valor padrão (channel = 0): ele vem da interface.
    override suspend fun startLive(camera: Device, channel: Int): StreamSession = providerOf(camera).startLive(camera, channel)

    /** A sessão não carrega o providerId: pede o encerramento a todos, cada um isolado. */
    override suspend fun stopLive(session: StreamSession) = providers.forEach { resultOf { it.stopLive(session) } }

    override suspend fun lockDetails(lock: Device): LockDetails = locksOf(lock).details(lock)
    override suspend fun setLock(lock: Device, open: Boolean) = locksOf(lock).setOpen(lock, open)
    override suspend fun setLockVolume(lock: Device, volume: LockVolume) = locksOf(lock).setVolume(lock, volume)
    override suspend fun lockVolume(lock: Device): LockVolume? = locksOf(lock).volume(lock)
    override suspend fun lockHistory(lock: Device, quantity: Int): List<LockEvent> = locksOf(lock).history(lock, quantity)
    override suspend fun firmware(device: Device): Firmware = providerOf(device).firmware(device)
    override suspend fun isOnline(device: Device): Boolean? = providerOf(device).isOnline(device)

    companion object {
        const val FILTER_ON_DEVICE_NOTE = "Filtro aplicado no aparelho"
    }
}
