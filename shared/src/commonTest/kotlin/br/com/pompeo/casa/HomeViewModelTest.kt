package br.com.pompeo.casa

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.com.pompeo.casa.domain.AppError
import br.com.pompeo.casa.domain.DevicesState
import br.com.pompeo.casa.domain.model.DeviceQuery
import br.com.pompeo.casa.domain.model.StreamSession
import br.com.pompeo.casa.ui.HomeViewModel
import br.com.pompeo.casa.ui.TokenValidation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @BeforeTest fun setUp() { Dispatchers.setMain(StandardTestDispatcher()) } // viewModelScope usa Main; runTest herda o relógio
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun validationRunsInViewModelScopeAndReportsDone() = runTest {
        // Arrange: a 1.ª página demora 1 s (tempo virtual).
        val tokens = FakeTokenRepository()
        val devices = FakeDeviceRepository(tokens, firstPageDelayMs = 1_000)
        val vm = homeViewModel(devices, tokens)
        // Act: submitToken não suspende; o trabalho segue no viewModelScope.
        vm.submitToken("  Ot_valid_0001  ")
        // Assert
        assertEquals(TokenValidation.Validating, vm.tokenValidation.value)
        advanceUntilIdle()
        assertEquals(TokenValidation.Done, vm.tokenValidation.value)
        assertEquals(listOf("Ot_valid_0001"), tokens.persistedValues)
        assertNull(vm.notice.value)
    }

    @Test
    fun rejectedTokenBecomesFailed() = runTest {
        // Arrange
        val tokens = FakeTokenRepository()
        val devices = FakeDeviceRepository(tokens) { DevicesState.Error(AppError.TokenInvalid("HTTP 401: Não autorizado"), DeviceQuery()) }
        val vm = homeViewModel(devices, tokens)
        // Act
        vm.submitToken("Ot_rejected_0001")
        advanceUntilIdle()
        // Assert
        val failed = assertIs<TokenValidation.Failed>(vm.tokenValidation.value)
        assertIs<AppError.TokenInvalid>(failed.error)
        assertNull(vm.token.value)
        assertTrue(tokens.persistedValues.isEmpty())
    }

    @Test
    fun acceptedTokenThatCouldNotBeStoredRaisesANotice() = runTest {
        // Arrange: Keystore/Keychain quebrado.
        val tokens = FakeTokenRepository(canPersist = false)
        val devices = FakeDeviceRepository(tokens)
        val vm = homeViewModel(devices, tokens)
        // Act
        vm.submitToken("Ot_valid_0001")
        advanceUntilIdle()
        // Assert: segue para a Home e avisa lá, em vez de fingir erro.
        assertEquals(TokenValidation.Done, vm.tokenValidation.value)
        assertEquals(HomeViewModel.TOKEN_NOT_PERSISTED, vm.notice.value)
        assertEquals("Ot_valid_0001", vm.token.value)
    }

    @Test
    fun clearingTheViewModelMidValidationDropsTheSessionToken() = runTest {
        // Arrange: a 1.ª página demora 1 s (tempo virtual), então a validação fica em andamento.
        val tokens = FakeTokenRepository()
        val devices = FakeDeviceRepository(tokens, firstPageDelayMs = 1_000)
        val store = ViewModelStore()
        // Caminho real de ciclo de vida: store.clear() cancela o viewModelScope.
        val vm = ViewModelProvider.create(store, viewModelFactory { initializer { homeViewModel(devices, tokens) } })[HomeViewModel::class]
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

    @Test
    fun startLiveRethrowsCancellation() = runTest {
        // Arrange: o vídeo demora 1 s (tempo virtual), então a tela pode sair no meio.
        val tokens = FakeTokenRepository("Ot_session_000001")
        val devices = FakeDeviceRepository(tokens).apply { startLiveDelayMs = 1_000 }
        val vm = homeViewModel(devices, tokens)
        var result: Result<StreamSession>? = null
        val live = launch { result = vm.startLive(device("CAM1")) }
        runCurrent()
        // Act
        live.cancel()
        runCurrent()
        // Assert: com runCatching a tentativa cancelada viraria Result.failure("Job was cancelled").
        assertTrue(live.isCompleted)
        assertNull(result, "cancelamento não vira Result.failure")
    }
}
