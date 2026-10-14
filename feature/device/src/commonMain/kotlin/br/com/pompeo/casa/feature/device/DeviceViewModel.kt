package br.com.pompeo.casa.feature.device

import androidx.lifecycle.ViewModel
import br.com.pompeo.casa.domain.ErrorMapper
import br.com.pompeo.casa.domain.model.Device
import br.com.pompeo.casa.domain.model.Firmware
import br.com.pompeo.casa.domain.model.LockDetails
import br.com.pompeo.casa.domain.model.LockEvent
import br.com.pompeo.casa.domain.model.LockVolume
import br.com.pompeo.casa.domain.repository.DeviceRepository
import br.com.pompeo.casa.domain.resultOf
import br.com.pompeo.casa.domain.usecase.ChangeLockVolumeUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Ação física pedida à fechadura: porta (RF05) ou volume (RF06). */
sealed interface LockAction {
    data class Door(val open: Boolean) : LockAction
    data class Volume(val volume: LockVolume) : LockAction
}

/**
 * Comando físico em andamento ou concluído. Vive no ViewModel: a rotação recria a tela, mas não o ViewModel,
 * então a tela recriada ainda recebe o resultado; o envio em si roda fora dele (ver [DeviceViewModel.runLockCommand]).
 */
sealed interface LockCommand {
    val lockNs: String
    val action: LockAction
    data class Sending(override val lockNs: String, override val action: LockAction) : LockCommand
    /** [error] = texto amigável da falha; [appliedVolume] = volume a exibir depois de mudar o volume. */
    data class Done(
        override val lockNs: String,
        override val action: LockAction,
        val error: String?,
        val appliedVolume: LockVolume? = null,
    ) : LockCommand
}

/**
 * ViewModel da tela de detalhe (fechadura, hub e outros dispositivos). É um por entrada da pilha de navegação:
 * nasce ao abrir a tela e é limpo ao sair dela. Só conhece o domain (interfaces de repositório, caso de uso,
 * ErrorMapper), injetado pelo Koin; nunca uma classe de data.
 *
 * [appScope] é o escopo que vive enquanto o app vive (registrado por :core:data sob
 * [br.com.pompeo.casa.domain.APP_SCOPE_NAME]): é nele que os comandos físicos rodam, não no viewModelScope.
 * [commandState] é compartilhado entre as instâncias (single do Koin): "um comando físico por vez" vale para o
 * app inteiro, não só para a tela aberta. Sair da fechadura no meio do envio e voltar cria um ViewModel novo,
 * que vê o Sending em andamento e recebe o Done; sem isso, o novo aceitaria um 2.º comando enquanto o 1.º corre.
 */
class DeviceViewModel(
    private val repository: DeviceRepository,
    private val changeLockVolumeUseCase: ChangeLockVolumeUseCase,
    private val errors: ErrorMapper,
    private val appScope: CoroutineScope,
    commandState: MutableStateFlow<LockCommand?> = MutableStateFlow(null),
) : ViewModel() {
    /** Acumulado das páginas carregadas pela Home: a tela de detalhe acha o próprio dispositivo pelo ns da rota. */
    val devices: StateFlow<List<Device>> = repository.devices

    fun providerName(device: Device): String? = repository.providerName(device)

    /** Texto amigável de uma falha (RF04): a tela nunca importa o mapeador de data/gdi. */
    fun messageOf(error: Throwable): String = errors.toAppError(error).userMessage
    /** Versão curta, para caber entre parênteses ("a API respondeu HTTP 500"). */
    fun shortMessageOf(error: Throwable): String = errors.toShortMessage(error)

    // resultOf (domain) e não runCatching: cancelamento é relançado; senão a tentativa antiga escreveria
    // "Job was cancelled" como erro por cima da nova (armadilha 28).
    suspend fun lockDetails(lock: Device): Result<LockDetails> = resultOf { repository.lockDetails(lock) }

    private val mutableLockCommand = commandState
    val lockCommand: StateFlow<LockCommand?> = mutableLockCommand.asStateFlow()

    /** Comando real na fechadura; a UI só chama depois de confirmação do usuário. */
    fun sendDoorCommand(lock: Device, open: Boolean) = runLockCommand(lock, LockAction.Door(open)) {
        repository.setLock(lock, open)
        null // a porta não devolve estado: a tela relê o estado real ao receber o Done
    }

    /** Também só depois de confirmação. O caso de uso grava, relê e, se a releitura falhar, assume o pedido. */
    fun changeLockVolume(lock: Device, volume: LockVolume) =
        runLockCommand(lock, LockAction.Volume(volume)) { changeLockVolumeUseCase(lock, volume) }

    /** A tela consumiu o Done: sem isso, ele seria reaplicado quando a tela fosse recriada (rotação). */
    fun lockCommandHandled() { mutableLockCommand.value = null }

    /**
     * Roda no [appScope], e não no viewModelScope, de propósito. Este ViewModel é por tela e morre quando o
     * usuário sai dela; o viewModelScope seria cancelado junto. Mas um pedido que já saiu para a nuvem não tem
     * volta: a fechadura pode já ter recebido a ordem, e cancelar a corrotina só esconderia o resultado (nem o
     * HTTP em voo é interrompido de forma confiável). No appScope o envio termina de qualquer jeito e publica o
     * Done neste StateFlow: se a tela ainda existe (rotação não a destrói), ela consome e relê o estado real;
     * se o usuário já saiu, o ViewModel limpo recebe o Done sem efeito. Um comando por vez por ViewModel: com
     * outro em andamento, o pedido é ignorado e a tela mantém os controles desabilitados.
     */
    private fun runLockCommand(lock: Device, action: LockAction, command: suspend () -> LockVolume?) {
        if (mutableLockCommand.value is LockCommand.Sending) return // um comando físico por vez
        mutableLockCommand.value = LockCommand.Sending(lock.ns, action)
        appScope.launch {
            val result = resultOf { command() }
            val error = result.exceptionOrNull()?.let { messageOf(it) }
            mutableLockCommand.value = LockCommand.Done(lock.ns, action, error, appliedVolume = result.getOrNull())
        }
    }

    suspend fun lockVolume(lock: Device): Result<LockVolume?> = resultOf { repository.lockVolume(lock) }

    /** A GDI pagina o histórico só por quantidade: [HISTORY_FIRST] ao abrir a tela, [HISTORY_MORE] em "Ver mais" (RF09). */
    suspend fun lockHistory(lock: Device, more: Boolean): Result<List<LockEvent>> =
        resultOf { repository.lockHistory(lock, if (more) HISTORY_MORE else HISTORY_FIRST) }

    suspend fun firmware(device: Device): Result<Firmware> = resultOf { repository.firmware(device) }
    suspend fun isOnline(device: Device): Result<Boolean?> = resultOf { repository.isOnline(device) }

    companion object {
        const val HISTORY_FIRST = 10
        const val HISTORY_MORE = 30
    }
}
