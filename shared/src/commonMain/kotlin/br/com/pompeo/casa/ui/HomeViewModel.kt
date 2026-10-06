package br.com.pompeo.casa.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Andamento da validação do token (RF01). Vive no ViewModel para sobreviver à rotação da tela de token. */
sealed interface TokenValidation {
    data object Idle : TokenValidation
    data object Validating : TokenValidation
    data class Failed(val error: AppError) : TokenValidation
    /** GDI aceitou: a tela navega e chama [HomeViewModel.tokenValidationHandled]. */
    data object Done : TokenValidation
}

/**
 * Único ViewModel do app: uma fonte de verdade para todas as telas. Só conhece o domain (interfaces de
 * repositório, casos de uso, ErrorMapper), injetado pelo Koin; nunca uma classe de data.
 */
class HomeViewModel(
    private val repository: DeviceRepository,
    private val tokens: TokenRepository,
    private val submitTokenUseCase: SubmitTokenUseCase,
    private val changeLockVolumeUseCase: ChangeLockVolumeUseCase,
    private val errors: ErrorMapper,
) : ViewModel() {
    val state: StateFlow<DevicesState> = repository.state
    val query: StateFlow<DeviceQuery> = repository.query
    val devices: StateFlow<List<Device>> = repository.devices
    val token: StateFlow<String?> = tokens.token

    /** Token de local.properties (desenvolvimento): só pré-preenche a tela de token. */
    val suggestedToken: String? get() = tokens.suggested
    val providerNames: List<String> get() = repository.providerNames
    fun maskedToken(): String? = tokens.masked()
    fun providerName(device: Device): String? = repository.providerName(device)

    private val mutableValidation = MutableStateFlow<TokenValidation>(TokenValidation.Idle)
    val tokenValidation: StateFlow<TokenValidation> = mutableValidation.asStateFlow()

    /** Aviso único para a Home (Snackbar), consumido por [noticeShown]. */
    private val mutableNotice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = mutableNotice.asStateFlow()

    /**
     * Valida com a 1.ª página e só então persiste. Não-suspend, no viewModelScope: a rotação recria a tela
     * (e cancelaria um rememberCoroutineScope no meio da chamada), mas não o ViewModel.
     */
    fun submitToken(raw: String) {
        if (mutableValidation.value is TokenValidation.Validating) return // segundo toque ignorado
        mutableValidation.value = TokenValidation.Validating
        viewModelScope.launch {
            // O caso de uso já devolve AppError; o catch é a rede de segurança para falha inesperada (nunca crash).
            val error = try { submitTokenUseCase(raw) } catch (e: CancellationException) { throw e } catch (e: Exception) { errors.toAppError(e) }
            // Aceito mas não gravado (Keystore/Keychain falhou): segue para a Home e avisa lá, em vez de fingir erro.
            if (error == null && !tokens.persisted) mutableNotice.value = TOKEN_NOT_PERSISTED
            mutableValidation.value = error?.let(TokenValidation::Failed) ?: TokenValidation.Done
        }
    }

    /** A tela consumiu o resultado: sem isso, um Done antigo navegaria sozinho na próxima visita. */
    fun tokenValidationHandled() { mutableValidation.value = TokenValidation.Idle }
    fun noticeShown() { mutableNotice.value = null }
    fun logout() { repository.logout(); tokenValidationHandled() }

    /** Texto amigável de uma falha (RF04): a tela nunca importa o mapeador de data/gdi. */
    fun messageOf(error: Throwable): String = errors.toAppError(error).userMessage
    /** Versão curta, para caber entre parênteses ("a API respondeu HTTP 500"). */
    fun shortMessageOf(error: Throwable): String = errors.toShortMessage(error)

    fun refresh() = repository.refresh()
    fun loadMore() = repository.loadMore()
    fun setOrigin(filter: OriginFilter) = repository.setOrigin(filter)
    fun setPageSize(size: Int) = repository.setPageSize(size)

    // resultOf (domain) e não runCatching: cancelamento é relançado; senão a tentativa antiga escreveria
    // "Job was cancelled" como erro por cima da nova (armadilha 28).
    suspend fun startLive(camera: Device, channel: Int = 0): Result<StreamSession> = resultOf { repository.startLive(camera, channel) }

    /** No viewModelScope: no onDispose da tela, o escopo dela já foi cancelado. */
    fun stopLive(session: StreamSession) { viewModelScope.launch { repository.stopLive(session) } }

    suspend fun lockDetails(lock: Device): Result<LockDetails> = resultOf { repository.lockDetails(lock) }
    /** Comando real na fechadura; a UI só chama depois de confirmação do usuário. */
    suspend fun setLock(lock: Device, open: Boolean): Result<Unit> = resultOf { repository.setLock(lock, open) }
    suspend fun setLockVolume(lock: Device, volume: LockVolume): Result<LockVolume> = resultOf { changeLockVolumeUseCase(lock, volume) }
    suspend fun lockVolume(lock: Device): Result<LockVolume?> = resultOf { repository.lockVolume(lock) }
    suspend fun lockHistory(lock: Device, more: Boolean): Result<List<LockEvent>> =
        resultOf { repository.lockHistory(lock, if (more) HISTORY_MORE else HISTORY_FIRST) }
    suspend fun firmware(device: Device): Result<Firmware> = resultOf { repository.firmware(device) }
    suspend fun isOnline(device: Device): Result<Boolean?> = resultOf { repository.isOnline(device) }

    companion object {
        const val HISTORY_FIRST = 10
        const val HISTORY_MORE = 30
        const val TOKEN_NOT_PERSISTED = "Token válido, mas não foi possível guardá-lo neste aparelho."
    }
}
