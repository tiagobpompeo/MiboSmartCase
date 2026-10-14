package br.com.pompeo.casa.feature.camera

import androidx.lifecycle.ViewModel
import br.com.pompeo.casa.domain.ErrorMapper
import br.com.pompeo.casa.domain.model.Device
import br.com.pompeo.casa.domain.model.StreamSession
import br.com.pompeo.casa.domain.repository.DeviceRepository
import br.com.pompeo.casa.domain.resultOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel da tela de vídeo ao vivo (RF03). Só conhece o domain (repositório, ErrorMapper), injetado pelo
 * Koin; nunca uma classe de data. Vive com a entrada da câmera na pilha de navegação: nasce ao abrir a câmera
 * e morre ao voltar. Por isso o encerramento da sessão vai para [appScope], e não para o viewModelScope.
 */
class CameraViewModel(
    private val repository: DeviceRepository,
    private val errors: ErrorMapper,
    private val appScope: CoroutineScope,
) : ViewModel() {
    /** Acumulado das páginas carregadas: a tela acha a câmera pelo ns e sai se ela sumir (logout/refresh). */
    val devices: StateFlow<List<Device>> = repository.devices

    fun providerName(device: Device): String? = repository.providerName(device)

    /** Texto amigável de uma falha (RF04): a tela nunca importa o mapeador de data/gdi. */
    fun messageOf(error: Throwable): String = errors.toAppError(error).userMessage

    // resultOf (domain) e não runCatching: cancelamento é relançado; senão a tentativa antiga escreveria
    // "Job was cancelled" como erro por cima da nova (armadilha 28).
    suspend fun startLive(camera: Device, channel: Int = 0): Result<StreamSession> =
        resultOf { repository.startLive(camera, channel) }

    /**
     * No appScope, não no viewModelScope. A tela chama isto no onDispose, quando o escopo dela já está
     * cancelado, e este ViewModel morre junto com a entrada da pilha logo em seguida: um viewModelScope seria
     * cancelado antes de o pedido sair. Só um escopo que vive enquanto o app vive garante que o encerramento
     * chegue ao parceiro. resultOf engole a falha: não há mais tela para mostrá-la e o RTSP vence sozinho.
     */
    fun stopLive(session: StreamSession) {
        appScope.launch { resultOf { repository.stopLive(session) } }
    }
}
