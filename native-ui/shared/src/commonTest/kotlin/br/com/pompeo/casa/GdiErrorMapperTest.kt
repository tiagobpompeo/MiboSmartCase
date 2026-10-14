package br.com.pompeo.casa

import br.com.pompeo.casa.data.gdi.GdiErrorMapper
import br.com.pompeo.casa.data.gdi.GdiException
import br.com.pompeo.casa.data.gdi.GdiNoTokenException
import br.com.pompeo.casa.domain.AppError
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class GdiErrorMapperTest {
    @Test
    fun tokenErrors() {
        // Arrange
        val noToken = GdiNoTokenException()
        val unauthorized = GdiException(401, "Não autorizado")
        val renewalRefused = GdiException(400, "Não foi possível renovar o token, por favor gere um novo")
        // Act
        val noTokenError = GdiErrorMapper.toAppError(noToken)
        val unauthorizedError = GdiErrorMapper.toAppError(unauthorized)
        val renewalError = GdiErrorMapper.toAppError(renewalRefused)
        // Assert
        assertEquals(AppError.TokenMissing, noTokenError, "sem token leva à tela de token, não a \"token inválido\"")
        assertEquals("HTTP 401: Não autorizado", assertIs<AppError.TokenInvalid>(unauthorizedError).detail)
        assertIs<AppError.TokenInvalid>(renewalError, "400 da renovação recusada é problema de token")
    }

    @Test
    fun expiredTokenHasItsOwnExplanation() {
        // Arrange: 403 com qualquer texto e "expirado" em outros status e caixas.
        val errors = listOf(
            GdiException(403, "Token expirado, por favor gere um novo token"),
            GdiException(403, "Forbidden"),
            GdiException(401, "Token EXPIRADO"),
            GdiException(200, "token Expirado, gere outro"),
        )
        // Act
        val mapped = errors.map { GdiErrorMapper.toAppError(it) }
        // Assert
        assertTrue(mapped.all { it is AppError.TokenExpired }, mapped.toString())
        assertNotEquals(AppError.TokenInvalid(detail = null).userMessage, mapped.first().userMessage)
        assertTrue("expirou" in mapped.first().userMessage, mapped.first().userMessage)
    }

    @Test
    fun networkAndTimeoutErrors() {
        // Arrange: ConnectTimeout é falta de rede (nem conectou); os outros dois são a plataforma demorando.
        val network = listOf(
            IOException("Connection reset by peer"),
            ConnectTimeoutException("Connect timeout has expired", null),
        )
        val timeout = listOf(
            HttpRequestTimeoutException("https://gdi.test/produtos/online/v1", REQUEST_TIMEOUT_MS, null),
            SocketTimeoutException("Socket timeout has expired", null),
        )
        // Act
        val networkMapped = network.map { GdiErrorMapper.toAppError(it) }
        val timeoutMapped = timeout.map { GdiErrorMapper.toAppError(it) }
        // Assert
        assertTrue(networkMapped.all { it is AppError.Network }, networkMapped.toString())
        assertTrue(timeoutMapped.all { it is AppError.Timeout }, timeoutMapped.toString())
    }

    @Test
    fun serverAndUnexpectedErrors() {
        // Arrange
        val unavailable = GdiException(503, "Service Unavailable")
        val errorEnvelope = GdiException(200, "Fechadura não respondeu")
        val paymentRequired = GdiException(402, "Cota de vídeo esgotada")
        val bug = IllegalStateException("estado inesperado")
        // Act
        val unavailableError = GdiErrorMapper.toAppError(unavailable)
        val envelopeError = GdiErrorMapper.toAppError(errorEnvelope)
        val paymentError = GdiErrorMapper.toAppError(paymentRequired)
        val bugError = GdiErrorMapper.toAppError(bug)
        // Assert
        assertEquals(503, assertIs<AppError.Server>(unavailableError).httpStatus)
        assertEquals(200, assertIs<AppError.Server>(envelopeError).httpStatus, "HTTP 200 com {\"status\":\"erro\"} é erro da plataforma")
        assertEquals("HTTP 402: Cota de vídeo esgotada", assertIs<AppError.Unexpected>(paymentError).detail)
        assertEquals("estado inesperado", assertIs<AppError.Unexpected>(bugError).detail)
    }

    @Test
    fun shortMessageCitesHttpStatusOnlyForGdiErrors() {
        // Arrange
        val gdiError = GdiException(500, "Erro desconhecido, por favor tente novamente mais tarde")
        val networkError = IOException("Connection reset by peer")
        val noToken = GdiNoTokenException()
        // Act
        val gdiMessage = GdiErrorMapper.toShortMessage(gdiError)
        val networkMessage = GdiErrorMapper.toShortMessage(networkError)
        val noTokenMessage = GdiErrorMapper.toShortMessage(noToken)
        // Assert
        assertEquals("a API respondeu HTTP 500", gdiMessage)
        assertEquals(AppError.Network(detail = null).userMessage, networkMessage)
        assertEquals(AppError.TokenMissing.userMessage, noTokenMessage, "sem token não é resposta da API")
    }

    private companion object {
        const val REQUEST_TIMEOUT_MS = 20_000L
    }
}
