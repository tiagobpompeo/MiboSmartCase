package br.com.pompeo.casa.feature.home

import androidx.lifecycle.ViewModel
import br.com.pompeo.casa.domain.DevicesState
import br.com.pompeo.casa.domain.ErrorMapper
import br.com.pompeo.casa.domain.model.Device
import br.com.pompeo.casa.domain.model.DeviceQuery
import br.com.pompeo.casa.domain.model.OriginFilter
import br.com.pompeo.casa.domain.repository.DeviceRepository
import br.com.pompeo.casa.domain.repository.TokenRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ViewModel da Home (RF02, RF07, RF08): lista de dispositivos, filtro de origem, paginação e a aba Definições.
 * Só conhece o domain (interfaces de repositório e ErrorMapper), injetado pelo Koin; nunca uma classe de data.
 * Token (RF01), câmera (RF03) e fechadura (RF05/RF06) têm ViewModels próprios nas features delas.
 */
class HomeViewModel(
    private val repository: DeviceRepository,
    private val tokens: TokenRepository,
    private val errors: ErrorMapper,
) : ViewModel() {
    val state: StateFlow<DevicesState> = repository.state
    val query: StateFlow<DeviceQuery> = repository.query
    val devices: StateFlow<List<Device>> = repository.devices
    /** A GDI pode renovar o token durante o uso; a aba Definições acompanha o valor atual pela máscara. */
    val token: StateFlow<String?> = tokens.token

    val providerNames: List<String> get() = repository.providerNames
    fun maskedToken(): String? = tokens.masked()
    fun providerName(device: Device): String? = repository.providerName(device)

    /** Aviso único para a Home (Snackbar), consumido por [noticeShown]. */
    private val mutableNotice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = mutableNotice.asStateFlow()

    init {
        // Token aceito pela GDI mas não gravado (Keystore/Keychain falhou): a tela de token segue para a Home
        // em vez de fingir erro, e é aqui, na chegada, que o aviso nasce. A validação mora em outra feature,
        // então o único sinal que chega a este ViewModel é o estado do repositório: há token e ele não persistiu.
        if (tokens.token.value != null && !tokens.persisted) mutableNotice.value = TOKEN_NOT_PERSISTED
    }

    fun noticeShown() { mutableNotice.value = null }

    /** Sair: o repositório cancela consultas em voo, apaga o token e volta a [DevicesState.NoToken]. */
    fun logout() = repository.logout()

    /** Texto amigável de uma falha (RF04): a tela nunca importa o mapeador de data/gdi. */
    fun messageOf(error: Throwable): String = errors.toAppError(error).userMessage

    fun refresh() = repository.refresh()
    fun loadMore() = repository.loadMore()
    fun setOrigin(filter: OriginFilter) = repository.setOrigin(filter)
    fun setPageSize(size: Int) = repository.setPageSize(size)

    companion object {
        const val TOKEN_NOT_PERSISTED = "Token válido, mas não foi possível guardá-lo neste aparelho."
    }
}
