package br.com.pompeo.casa

import br.com.pompeo.casa.domain.AppError
import br.com.pompeo.casa.domain.DevicesState
import br.com.pompeo.casa.domain.model.DeviceQuery
import br.com.pompeo.casa.domain.usecase.SubmitTokenUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SubmitTokenUseCaseTest {
    @Test
    fun implausibleTokenIsRejectedWithoutLoadingAPage() = runTest {
        // Arrange
        val tokens = FakeTokenRepository()
        val devices = FakeDeviceRepository(tokens)
        val submitToken = SubmitTokenUseCase(tokens, devices)
        // Act
        val errors = listOf("curto", "   ", "Ot_abc def_0001").map { submitToken(it) }
        // Assert
        errors.forEach { assertEquals(AppError.TokenMissing, it) }
        assertEquals(0, devices.firstPageLoads)
        assertNull(tokens.token.value)
    }

    @Test
    fun rejectedTokenIsNotPersistedAndLogsOut() = runTest {
        // Arrange
        val tokens = FakeTokenRepository()
        val devices = FakeDeviceRepository(tokens) { DevicesState.Error(AppError.TokenInvalid("HTTP 401"), DeviceQuery()) }
        val submitToken = SubmitTokenUseCase(tokens, devices)
        // Act
        val error = submitToken("  Ot_rejected_0001  ")
        // Assert
        assertIs<AppError.TokenInvalid>(error)
        assertTrue(tokens.persistedValues.isEmpty())
        assertNull(tokens.token.value)
        assertEquals(1, devices.logouts)
    }

    @Test
    fun expiredTokenReportsExpiry() = runTest {
        // Arrange
        val tokens = FakeTokenRepository()
        val devices = FakeDeviceRepository(tokens) {
            DevicesState.Error(AppError.TokenExpired("HTTP 403: Token expirado, por favor gere um novo token"), DeviceQuery())
        }
        val submitToken = SubmitTokenUseCase(tokens, devices)
        // Act
        val error = submitToken("Ot_expired_0001")
        // Assert
        assertIs<AppError.TokenExpired>(error)
        assertTrue(tokens.persistedValues.isEmpty())
        assertNull(tokens.token.value)
    }

    @Test
    fun acceptedTokenIsPersistedTrimmed() = runTest {
        // Arrange
        val tokens = FakeTokenRepository()
        val devices = FakeDeviceRepository(tokens) {
            DevicesState.Content(listOf(device("CAM1")), DeviceQuery(), hasMore = false, pagesLoaded = 1)
        }
        val submitToken = SubmitTokenUseCase(tokens, devices)
        // Act
        val error = submitToken("  Ot_valid_0001  ")
        // Assert
        assertNull(error)
        assertEquals(listOf("Ot_valid_0001"), tokens.persistedValues)
        assertTrue(tokens.persisted)
    }

    @Test
    fun emptyAccountStillPersists() = runTest {
        // Arrange
        val tokens = FakeTokenRepository()
        val devices = FakeDeviceRepository(tokens) { DevicesState.Empty(DeviceQuery()) }
        val submitToken = SubmitTokenUseCase(tokens, devices)
        // Act
        val error = submitToken("Ot_empty_account_0001")
        // Assert
        assertNull(error)
        assertEquals(listOf("Ot_empty_account_0001"), tokens.persistedValues)
        assertEquals(0, devices.logouts)
    }

    @Test
    fun persistsTheTokenRenewedDuringValidation() = runTest {
        // Arrange: a GDI renova o token durante a 1.ª página e a sessão passa a ter o valor novo.
        val tokens = FakeTokenRepository()
        val devices = FakeDeviceRepository(tokens) {
            tokens.setSession("Ot_renewed_0002")
            DevicesState.Empty(DeviceQuery())
        }
        val submitToken = SubmitTokenUseCase(tokens, devices)
        // Act
        val error = submitToken("Ot_old_token_0001")
        // Assert
        assertNull(error)
        assertEquals(listOf("Ot_renewed_0002"), tokens.persistedValues)
        assertEquals("Ot_renewed_0002", tokens.token.value)
    }

    @Test
    fun cancelledValidationLeavesNoUnvalidatedSessionToken() = runTest {
        // Arrange: a 1.ª página demora 1 s (tempo virtual).
        val tokens = FakeTokenRepository()
        val devices = FakeDeviceRepository(tokens, firstPageDelayMs = 1_000)
        val submitToken = SubmitTokenUseCase(tokens, devices)
        var returned = false
        val validation = launch {
            submitToken("Ot_interrupted_0123")
            returned = true // só chega aqui se invoke engolir o cancelamento e devolver um AppError
        }
        runCurrent()
        assertEquals("Ot_interrupted_0123", tokens.token.value)
        // Act
        validation.cancel()
        runCurrent()
        // Assert
        assertNull(tokens.token.value)
        assertEquals(DevicesState.NoToken, devices.state.value)
        assertTrue(tokens.persistedValues.isEmpty())
        assertFalse(returned, "o cancelamento é relançado: invoke não devolve um AppError")
    }

    @Test
    fun succeedsEvenWhenSecureStorageFails() = runTest {
        // Arrange: Keystore/Keychain quebrado.
        val tokens = FakeTokenRepository(canPersist = false)
        val devices = FakeDeviceRepository(tokens)
        val submitToken = SubmitTokenUseCase(tokens, devices)
        // Act
        val error = submitToken("Ot_valid_0001")
        // Assert: aceito e válido nesta sessão, mas não gravado.
        assertNull(error)
        assertFalse(tokens.persisted)
        assertEquals("Ot_valid_0001", tokens.token.value)
        assertEquals(0, devices.logouts)
    }
}
