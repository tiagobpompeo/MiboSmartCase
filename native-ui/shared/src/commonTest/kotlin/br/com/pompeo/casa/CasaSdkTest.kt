package br.com.pompeo.casa

import br.com.pompeo.casa.domain.AppError
import br.com.pompeo.casa.domain.DevicesState
import br.com.pompeo.casa.domain.model.DeviceKind
import br.com.pompeo.casa.domain.model.DeviceQuery
import br.com.pompeo.casa.domain.model.LockVolume
import br.com.pompeo.casa.domain.repository.DeviceRepository
import br.com.pompeo.casa.domain.usecase.ChangeLockVolumeUseCase
import br.com.pompeo.casa.domain.usecase.SubmitTokenUseCase
import br.com.pompeo.casa.sdk.CasaSdk
import br.com.pompeo.casa.sdk.Outcome
import br.com.pompeo.casa.sdk.SessionInfo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A ponte para o Swift é testada aqui, no comum, com os mesmos fakes dos outros testes: o contrato que o
 * SwiftUI consome (StateWatcher, Outcome, SessionInfo) não depende de iOS para ser verificado.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CasaSdkTest {
    private val lock = device("LOCK1", kind = DeviceKind.LOCK)

    private fun TestScope.sdk(tokens: FakeTokenRepository, devices: DeviceRepository): CasaSdk =
        CasaSdk(
            tokens = tokens,
            devices = devices,
            submitToken = SubmitTokenUseCase(tokens, devices),
            changeLockVolume = ChangeLockVolumeUseCase(devices),
            errors = FakeErrorMapper,
            dispatcher = StandardTestDispatcher(testScheduler), // no iOS é o Main; aqui, o relógio virtual
        )

    @Test
    fun sessionWatcherReflectsTokenChangesAndStopsAfterCancel() = runTest {
        val tokens = FakeTokenRepository()
        val sdk = sdk(tokens, FakeDeviceRepository(tokens))
        val seen = mutableListOf<SessionInfo>()

        val handle = sdk.session.watch { seen += it }
        runCurrent()
        tokens.persist("Ot_abcdefghwxyz")
        runCurrent()
        handle.cancel()
        tokens.clear()
        runCurrent()

        assertEquals(listOf(false, true), seen.map { it.hasToken })
        assertEquals("Ot_ab…wxyz", seen.last().maskedToken) // nunca o token inteiro
        assertTrue(seen.last().persisted)
        // Depois de cancel() nada mais chega ao Swift, mas o valor atual continua legível de forma síncrona.
        assertFalse(sdk.session.value.hasToken)
    }

    @Test
    fun submitTokenReturnsNullOnSuccessAndAppErrorOnFailure() = runTest {
        val tokens = FakeTokenRepository()
        val ok = sdk(tokens, FakeDeviceRepository(tokens) { DevicesState.Empty(DeviceQuery()) })
        assertNull(ok.submitToken("Ot_abcdefghwxyz"))
        assertTrue(ok.session.value.hasToken)

        val tokens2 = FakeTokenRepository()
        val rejected = sdk(tokens2, FakeDeviceRepository(tokens2) { DevicesState.Error(AppError.TokenInvalid("401"), DeviceQuery()) })
        assertIs<AppError.TokenInvalid>(rejected.submitToken("Ot_abcdefghwxyz"))
        assertFalse(rejected.session.value.hasToken) // token recusado não fica na sessão

        // Formato implausível nem chama a API.
        assertIs<AppError.TokenMissing>(ok.submitToken("abc"))
    }

    @Test
    fun outcomeCarriesTheValueOrTheMappedError() = runTest {
        val tokens = FakeTokenRepository("Ot_abcdefghwxyz")
        val locks = FakeLockController()
        val sdk = sdk(tokens, FakeDeviceRepository(tokens, locks = locks))

        val details = sdk.lockDetails(lock)
        assertTrue(details.isSuccess)
        assertEquals(50, assertNotNull(details.valueOrNull).battery)
        assertNull(details.errorOrNull)

        // Falha de escrita: vira AppError (mapeado) em vez de exceção atravessando a ponte.
        locks.writeError = IllegalStateException("HTTP 500")
        val changed = sdk.changeLockVolume(lock, LockVolume.HIGH)
        assertIs<Outcome.Failure>(changed)
        assertEquals("HTTP 500", (changed.errorOrNull as AppError.Unexpected).detail)
        assertNull(changed.valueOrNull)
    }

    @Test
    fun setLockReturnsNullWhenSentAndTheErrorWhenRefused() = runTest {
        val tokens = FakeTokenRepository("Ot_abcdefghwxyz")
        val locks = FakeLockController()
        val sdk = sdk(tokens, FakeDeviceRepository(tokens, locks = locks))

        assertNull(sdk.setLock(lock, open = true))
        locks.commandError = IllegalStateException("recusado")
        assertIs<AppError.Unexpected>(sdk.setLock(lock, open = false))
        assertEquals(listOf(true, false), locks.doorCommands)
    }

    @Test
    fun isOnlineTurnsFailureIntoNull() = runTest {
        val tokens = FakeTokenRepository("Ot_abcdefghwxyz")
        val failing = object : DeviceRepository by FakeDeviceRepository(tokens) {
            override suspend fun isOnline(device: br.com.pompeo.casa.domain.model.Device): Boolean? = throw IllegalStateException("500")
        }
        assertNull(sdk(tokens, failing).isOnline(lock)) // a tela mostra "—", não inventa estado
        assertEquals(true, sdk(tokens, FakeDeviceRepository(tokens)).isOnline(lock.copy(online = true)))
    }
}
