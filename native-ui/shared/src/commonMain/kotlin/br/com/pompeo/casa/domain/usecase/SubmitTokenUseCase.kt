package br.com.pompeo.casa.domain.usecase

import br.com.pompeo.casa.domain.AppError
import br.com.pompeo.casa.domain.DevicesState
import br.com.pompeo.casa.domain.TokenFormat
import br.com.pompeo.casa.domain.repository.DeviceRepository
import br.com.pompeo.casa.domain.repository.TokenRepository
import kotlin.coroutines.cancellation.CancellationException

/**
 * Valida o token carregando a primeira página e só então o persiste (inclusive com a conta vazia).
 * Em falha ou cancelamento o token sai da memória e o estado volta a NoToken.
 */
class SubmitTokenUseCase(private val tokens: TokenRepository, private val devices: DeviceRepository) {
    /** null = token aceito; senão, o erro que a tela de token mostra. */
    suspend operator fun invoke(raw: String): AppError? {
        val token = TokenFormat.normalize(raw)
        if (!TokenFormat.isPlausible(token)) return AppError.TokenMissing // nem chama a API
        tokens.setSession(token)
        return when (val firstPage = loadFirstPageOrLogout()) {
            is DevicesState.Error -> reject(firstPage.error)
            DevicesState.NoToken -> reject(AppError.TokenMissing)
            else -> {
                // Persiste o valor ATUAL: a GDI pode ter renovado o token durante a validação.
                tokens.persist(tokens.token.value ?: token)
                null
            }
        }
    }

    private suspend fun loadFirstPageOrLogout(): DevicesState =
        try {
            devices.loadFirstPage()
        } catch (e: CancellationException) {
            // Validação interrompida (ViewModel encerrado): token não validado não pode ficar na sessão.
            devices.logout()
            throw e
        }

    private fun reject(error: AppError): AppError {
        devices.logout()
        return error
    }
}
