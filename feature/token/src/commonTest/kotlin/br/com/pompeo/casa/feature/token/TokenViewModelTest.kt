package br.com.pompeo.casa.feature.token

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.com.pompeo.casa.FakeDeviceRepository
import br.com.pompeo.casa.FakeErrorMapper
import br.com.pompeo.casa.FakeTokenRepository
import br.com.pompeo.casa.domain.AppError
import br.com.pompeo.casa.domain.DevicesState
import br.com.pompeo.casa.domain.model.DeviceQuery
import br.com.pompeo.casa.domain.usecase.SubmitTokenUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TokenViewModelTest {
    /** viewModelScope usa Main: o dispatcher de teste compartilha o relógio virtual do runTest e sai no fim. */
    private fun viewModelTest(block: suspend TestScope.() -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try { block() } finally { Dispatchers.resetMain() }
    }

    /** Caso de uso real por cima dos fakes: o teste cobre a cola ViewModel ↔ domain, não só o estado. */
    private fun tokenViewModel(tokens: FakeTokenRepository, devices: FakeDeviceRepository) =
        TokenViewModel(tokens, SubmitTokenUseCase(tokens, devices), FakeErrorMapper)

    @Test
    fun validationRunsInViewModelScopeAndReportsDone() = viewModelTest {
        // Arrange: a 1.ª página demora 1 s (tempo virtual).
        val tokens = FakeTokenRepository()
        val devices = FakeDeviceRepository(tokens, firstPageDelayMs = 1_000)
        val vm = tokenViewModel(tokens, devices)
        // Act: submitToken não suspende; o trabalho segue no viewModelScope.
        vm.submitToken("  Ot_valid_0001  ")
        // Assert
        assertEquals(TokenValidation.Validating, vm.tokenValidation.value)
        advanceUntilIdle()
        assertEquals(TokenValidation.Done, vm.tokenValidation.value)
        assertEquals(listOf("Ot_valid_0001"), tokens.persistedValues)
    }

    @Test
    fun rejectedTokenBecomesFailed() = viewModelTest {
        // Arrange
        val tokens = FakeTokenRepository()
        val devices = FakeDeviceRepository(tokens) { DevicesState.Error(AppError.TokenInvalid("HTTP 401: Não autorizado"), DeviceQuery()) }
        val vm = tokenViewModel(tokens, devices)
        // Act
        vm.submitToken("Ot_rejected_0001")
        advanceUntilIdle()
        // Assert
        val failed = assertIs<TokenValidation.Failed>(vm.tokenValidation.value)
        assertIs<AppError.TokenInvalid>(failed.error)
        assertNull(tokens.token.value)
        assertTrue(tokens.persistedValues.isEmpty())
    }

    @Test
    fun unexpectedFailureIsMappedInsteadOfCrashing() = viewModelTest {
        // Arrange: a 1.ª página lança algo que o caso de uso não traduz.
        val tokens = FakeTokenRepository()
        val devices = FakeDeviceRepository(tokens) { throw IllegalStateException("boom") }
        val vm = tokenViewModel(tokens, devices)
        // Act
        vm.submitToken("Ot_broken_000001")
        advanceUntilIdle()
        // Assert: o ErrorMapper é a rede de segurança; a tela recebe um AppError, nunca uma exceção.
        val failed = assertIs<TokenValidation.Failed>(vm.tokenValidation.value)
        val error = assertIs<AppError.Unexpected>(failed.error)
        assertEquals("boom", error.detail)
        assertTrue(tokens.persistedValues.isEmpty())
    }

    @Test
    fun acceptedTokenThatCouldNotBeStoredStillReportsDone() = viewModelTest {
        // Arrange: Keystore/Keychain quebrado.
        val tokens = FakeTokenRepository(canPersist = false)
        val vm = tokenViewModel(tokens, FakeDeviceRepository(tokens))
        // Act
        vm.submitToken("Ot_valid_0001")
        advanceUntilIdle()
        // Assert: segue para a Home em vez de fingir erro; o aviso é dela, lendo tokens.persisted.
        assertEquals(TokenValidation.Done, vm.tokenValidation.value)
        assertEquals("Ot_valid_0001", tokens.token.value)
        assertFalse(tokens.persisted)
    }

    @Test
    fun secondTapWhileValidatingIsIgnored() = viewModelTest {
        // Arrange: a 1.ª validação demora 1 s (tempo virtual) e ainda está em andamento.
        val tokens = FakeTokenRepository()
        val devices = FakeDeviceRepository(tokens, firstPageDelayMs = 1_000)
        val vm = tokenViewModel(tokens, devices)
        vm.submitToken("Ot_first_0000001")
        runCurrent()
        // Act
        vm.submitToken("Ot_second_000002")
        advanceUntilIdle()
        // Assert: só o 1.º chegou à API e foi persistido.
        assertEquals(1, devices.firstPageLoads)
        assertEquals(listOf("Ot_first_0000001"), tokens.persistedValues)
        assertEquals(TokenValidation.Done, vm.tokenValidation.value)
    }

    @Test
    fun handledResultGoesBackToIdle() = viewModelTest {
        // Arrange: uma validação concluída.
        val tokens = FakeTokenRepository()
        val vm = tokenViewModel(tokens, FakeDeviceRepository(tokens))
        vm.submitToken("Ot_valid_0001")
        advanceUntilIdle()
        assertEquals(TokenValidation.Done, vm.tokenValidation.value)
        // Act
        vm.tokenValidationHandled()
        // Assert: sem isso, um Done antigo navegaria sozinho na próxima visita à tela.
        assertEquals(TokenValidation.Idle, vm.tokenValidation.value)
    }

    @Test
    fun clearingTheViewModelMidValidationDropsTheSessionToken() = viewModelTest {
        // Arrange: a 1.ª página demora 1 s (tempo virtual), então a validação fica em andamento.
        val tokens = FakeTokenRepository()
        val devices = FakeDeviceRepository(tokens, firstPageDelayMs = 1_000)
        val store = ViewModelStore()
        // Caminho real de ciclo de vida: store.clear() cancela o viewModelScope.
        val vm = ViewModelProvider.create(store, viewModelFactory { initializer { tokenViewModel(tokens, devices) } })[TokenViewModel::class]
        vm.submitToken("Ot_interrupted_0123")
        runCurrent()
        assertEquals("Ot_interrupted_0123", tokens.token.value)
        // Act
        store.clear()
        runCurrent()
        // Assert
        assertNull(tokens.token.value)
        assertEquals(DevicesState.NoToken, devices.state.value)
        assertTrue(tokens.persistedValues.isEmpty())
    }
}
