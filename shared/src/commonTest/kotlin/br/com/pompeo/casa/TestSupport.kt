package br.com.pompeo.casa

import br.com.pompeo.casa.domain.AppError
import br.com.pompeo.casa.domain.DevicesState
import br.com.pompeo.casa.domain.ErrorMapper
import br.com.pompeo.casa.domain.TokenFormat
import br.com.pompeo.casa.domain.TokenStorage
import br.com.pompeo.casa.domain.model.Device
import br.com.pompeo.casa.domain.model.DeviceKind
import br.com.pompeo.casa.domain.model.DeviceOrigin
import br.com.pompeo.casa.domain.model.DevicePage
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
import br.com.pompeo.casa.domain.usecase.ChangeLockVolumeUseCase
import br.com.pompeo.casa.domain.usecase.SubmitTokenUseCase
import br.com.pompeo.casa.ui.HomeViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Substitui Keystore/Keychain nos testes; conta as escritas para provar "persiste só em sucesso". */
class InMemoryTokenStorage(initial: String? = null) : TokenStorage {
    var value: String? = initial
    val writes = mutableListOf<String?>()
    override fun read(): String? = value
    override fun write(token: String?) { writes += token; value = token }
}

/** Keystore/Keychain quebrado: lê nada (opcionalmente lança) e lança em toda gravação. */
class ThrowingTokenStorage(private val initial: String? = null, private val readFails: Boolean = false) : TokenStorage {
    var writeAttempts = 0
    override fun read(): String? = if (readFails) throw IllegalStateException("Keystore indisponível") else initial
    override fun write(token: String?) { writeAttempts++; throw IllegalStateException("Keystore indisponível") }
}

fun device(ns: String, kind: DeviceKind = DeviceKind.CAMERA, origin: DeviceOrigin = DeviceOrigin.UNKNOWN, providerId: String = "fake"): Device =
    Device(providerId = providerId, ns = ns, name = "Dispositivo $ns", kind = kind, model = null, productId = "P$ns", online = true, origin = origin)

/**
 * Parceiro falso: pagina uma lista fixa como a GDI (sem total). [failWhen] injeta erro por página/origem;
 * [delayFor] simula lentidão em tempo VIRTUAL do runTest; [queries]/[liveChannels] registram os pedidos.
 */
class FakeProvider(
    private val all: List<Device>,
    private val failWhen: (DeviceQuery) -> Throwable? = { null },
    override val id: String = "fake",
    private val delayFor: (DeviceQuery) -> Long = { 0 },
    override val locks: LockController? = null,
) : DeviceProvider {
    override val displayName: String = "Parceiro de teste"
    val queries = mutableListOf<DeviceQuery>()
    val liveChannels = mutableListOf<Int>()

    override suspend fun listDevices(query: DeviceQuery): DevicePage {
        queries += query
        delayFor(query).takeIf { it > 0 }?.let { delay(it) }
        failWhen(query)?.let { throw it }
        val from = (query.page - 1) * query.pageSize
        return DevicePage(all.drop(from).take(query.pageSize), query.page, query.pageSize)
    }
    override suspend fun startLive(camera: Device, channel: Int): StreamSession {
        liveChannels += channel
        return StreamSession(null, "rtsp://fake/${camera.ns}", null, null)
    }
    override suspend fun stopLive(session: StreamSession) = Unit
    override suspend fun firmware(device: Device): Firmware = Firmware("1.0", false)
    override suspend fun isOnline(device: Device): Boolean? = device.online
}

/**
 * Fechadura falsa: volume em memória; readError/writeError simulam o HTTP 500 real da leitura. [doorCommands]
 * registra os pedidos de porta; [commandDelayMs] (tempo VIRTUAL) deixa o comando em andamento; [commandError] o faz falhar.
 */
class FakeLockController : LockController {
    var volume: LockVolume? = LockVolume.MEDIUM
    var readError: Throwable? = null
    var writeError: Throwable? = null
    var commandError: Throwable? = null
    var commandDelayMs = 0L
    val doorCommands = mutableListOf<Boolean>()
    val historyRequests = mutableListOf<Int>()
    override suspend fun details(lock: Device): LockDetails =
        LockDetails(open = false, remoteEnabled = true, battery = 50, volume = volume, volumeError = null, history = emptyList())
    override suspend fun setOpen(lock: Device, open: Boolean) {
        doorCommands += open
        delay(commandDelayMs)
        commandError?.let { throw it }
    }
    override suspend fun volume(lock: Device): LockVolume? { readError?.let { throw it }; return volume }
    override suspend fun setVolume(lock: Device, volume: LockVolume) { writeError?.let { throw it }; this.volume = volume }
    override suspend fun history(lock: Device, count: Int): List<LockEvent> {
        historyRequests += count
        return List(count) { LockEvent(time = "02/10/2026 16:29:${(it % 60).toString().padStart(2, '0')}", description = "Remoto (APP)") }
    }
}

/** TokenRepository em memória. [canPersist] = false simula Keystore/Keychain quebrado. */
class FakeTokenRepository(initial: String? = null, private val canPersist: Boolean = true) : TokenRepository {
    private val mutableToken = MutableStateFlow(initial)
    override val token: StateFlow<String?> = mutableToken.asStateFlow()
    override val suggested: String? = null
    override var persisted: Boolean = initial != null
        private set
    /** Valores que chegaram ao "armazenamento seguro", na ordem. */
    val persistedValues = mutableListOf<String>()

    override fun setSession(token: String) {
        mutableToken.value = token
        persisted = false
    }

    override fun persist(token: String): Boolean {
        setSession(token)
        if (canPersist) persistedValues += token
        persisted = canPersist
        return persisted
    }

    override fun clear() {
        mutableToken.value = null
        persisted = false
    }

    override fun masked(): String? = mutableToken.value?.let(TokenFormat::mask)
}

/**
 * DeviceRepository de mentira: [firstPage] decide o resultado da validação e roda depois de [firstPageDelayMs]
 * (tempo VIRTUAL do runTest); fechadura delegada a [locks]; [startLiveDelayMs] permite cancelar o vídeo no meio.
 */
class FakeDeviceRepository(
    private val tokens: TokenRepository,
    private val firstPageDelayMs: Long = 0,
    private val locks: FakeLockController = FakeLockController(),
    private val firstPage: () -> DevicesState = { DevicesState.Empty(DeviceQuery()) },
) : DeviceRepository {
    private val mutableState = MutableStateFlow<DevicesState>(DevicesState.NoToken)
    override val state: StateFlow<DevicesState> = mutableState.asStateFlow()
    override val query: StateFlow<DeviceQuery> = MutableStateFlow(DeviceQuery())
    override val devices: StateFlow<List<Device>> = MutableStateFlow(emptyList())
    override val providerNames: List<String> = listOf(PROVIDER_NAME)
    var firstPageLoads = 0
        private set
    var logouts = 0
        private set
    var startLiveDelayMs = 0L

    override fun providerName(device: Device): String = PROVIDER_NAME

    override suspend fun loadFirstPage(): DevicesState {
        firstPageLoads++
        delay(firstPageDelayMs)
        return firstPage().also { mutableState.value = it }
    }

    override fun refresh() = Unit
    override fun loadMore() = Unit
    override fun setOrigin(filter: OriginFilter) = Unit
    override fun setPageSize(size: Int) = Unit

    override fun logout() {
        logouts++
        tokens.clear()
        mutableState.value = DevicesState.NoToken
    }

    override suspend fun startLive(camera: Device, channel: Int): StreamSession {
        delay(startLiveDelayMs)
        return StreamSession(null, "rtsp://fake/${camera.ns}", null, null)
    }

    override suspend fun stopLive(session: StreamSession) = Unit
    override suspend fun lockDetails(lock: Device): LockDetails = locks.details(lock)
    override suspend fun setLock(lock: Device, open: Boolean) = locks.setOpen(lock, open)
    override suspend fun setLockVolume(lock: Device, volume: LockVolume) = locks.setVolume(lock, volume)
    override suspend fun lockVolume(lock: Device): LockVolume? = locks.volume(lock)
    override suspend fun lockHistory(lock: Device, quantity: Int): List<LockEvent> = locks.history(lock, quantity)
    override suspend fun firmware(device: Device): Firmware = Firmware("1.0", false)
    override suspend fun isOnline(device: Device): Boolean? = device.online

    private companion object {
        const val PROVIDER_NAME = "Parceiro de teste"
    }
}

/** Mapeador previsível para testes de apresentação; o mapeamento real é coberto pelo GdiErrorMapperTest. */
object FakeErrorMapper : ErrorMapper {
    override fun toAppError(error: Throwable): AppError = AppError.Unexpected(error.message)
    override fun toShortMessage(error: Throwable): String = error.message.orEmpty()
}

/** HomeViewModel com casos de uso reais sobre fakes: o teste exercita só a apresentação. */
fun homeViewModel(devices: DeviceRepository, tokens: TokenRepository): HomeViewModel =
    HomeViewModel(devices, tokens, SubmitTokenUseCase(tokens, devices), ChangeLockVolumeUseCase(devices), FakeErrorMapper)
