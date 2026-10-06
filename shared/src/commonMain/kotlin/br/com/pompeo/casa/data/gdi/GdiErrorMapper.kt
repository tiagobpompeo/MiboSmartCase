package br.com.pompeo.casa.data.gdi

import br.com.pompeo.casa.domain.AppError
import br.com.pompeo.casa.domain.ErrorMapper
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.io.IOException

/** Traduz falhas técnicas (Ktor, GDI) em [AppError] que a UI sabe explicar (RF04). A ordem dos ramos importa. */
object GdiErrorMapper : ErrorMapper {
    /** Texto real (05/10/2026) quando o token já não pode ser renovado (HTTP 400). */
    private const val RENEW_FAILED_FRAGMENT = "renovar o token"
    /** Fato: token expirado = HTTP 403 {"status":"erro","msg":"Token expirado, por favor gere um novo token"}. */
    private const val EXPIRED_FRAGMENT = "expirado"

    override fun toAppError(error: Throwable): AppError =
        when (error) {
            is GdiNoTokenException -> AppError.TokenMissing // subclasse de GdiException: vem antes
            is GdiException -> mapGdi(error)
            // Timeout antes de IOException: no JVM SocketTimeoutException também é IOException.
            is HttpRequestTimeoutException, is SocketTimeoutException -> AppError.Timeout(error.message)
            is ConnectTimeoutException, is IOException -> AppError.Network(error.message)
            else ->
                // UnresolvedAddressException (DNS) não é IOException e não existe em todos os alvos: reconhecida pelo nome.
                if (error::class.simpleName == "UnresolvedAddressException") AppError.Network(error.message)
                else AppError.Unexpected(error.message ?: error::class.simpleName)
        }

    /** Erro da GDI cita só o status ("a API respondeu HTTP 500"); o resto usa a mensagem amigável. */
    override fun toShortMessage(error: Throwable): String =
        if (error is GdiException && error !is GdiNoTokenException) "a API respondeu HTTP ${error.httpStatus}"
        else toAppError(error).userMessage

    private fun mapGdi(error: GdiException): AppError {
        val detail = "HTTP ${error.httpStatus}: ${error.message}"
        return when {
            error.httpStatus == 403 || error.message?.contains(EXPIRED_FRAGMENT, ignoreCase = true) == true -> AppError.TokenExpired(detail)
            error.httpStatus == 401 -> AppError.TokenInvalid(detail)
            error.httpStatus == 400 && error.message?.contains(RENEW_FAILED_FRAGMENT, ignoreCase = true) == true -> AppError.TokenInvalid(detail)
            // 2xx com envelope {"status":"erro"} também é a plataforma respondendo com erro.
            error.httpStatus >= 500 || error.httpStatus in 200..299 -> AppError.Server(error.httpStatus, detail)
            else -> AppError.Unexpected(detail)
        }
    }
}

// Atalhos para a própria camada data (DeviceRepositoryImpl, GdiLockController). A apresentação usa a interface
// ErrorMapper do domain, injetada no ViewModel, e nunca importa estas funções.
fun Throwable.toAppError(): AppError = GdiErrorMapper.toAppError(this)
fun Throwable.toShortMessage(): String = GdiErrorMapper.toShortMessage(this)
