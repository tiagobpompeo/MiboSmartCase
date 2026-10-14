package br.com.pompeo.casa.feature.token

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.pompeo.casa.domain.AppError
import br.com.pompeo.casa.domain.ErrorMapper
import br.com.pompeo.casa.domain.repository.TokenRepository
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
    /** GDI aceitou: a tela navega e chama [TokenViewModel.tokenValidationHandled]. */
    data object Done : TokenValidation
}

/**
 * ViewModel da tela de token (RF01). Só conhece o domain (TokenRepository, caso de uso, ErrorMapper), injetado
 * pelo Koin; nunca uma classe de data. O aviso "token não persistido" não mora aqui: a Home lê tokens.persisted.
 */
class TokenViewModel(
    private val tokens: TokenRepository,
    private val submitTokenUseCase: SubmitTokenUseCase,
    private val errors: ErrorMapper,
) : ViewModel() {
    /** Token de local.properties (desenvolvimento): só pré-preenche a tela de token. */
    val suggestedToken: String? get() = tokens.suggested

    private val mutableValidation = MutableStateFlow<TokenValidation>(TokenValidation.Idle)
    val tokenValidation: StateFlow<TokenValidation> = mutableValidation.asStateFlow()

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
            mutableValidation.value = error?.let(TokenValidation::Failed) ?: TokenValidation.Done
        }
    }

    /** A tela consumiu o resultado: sem isso, um Done antigo navegaria sozinho na próxima visita. */
    fun tokenValidationHandled() { mutableValidation.value = TokenValidation.Idle }
}
