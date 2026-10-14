package br.com.pompeo.casa.feature.camera

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.com.pompeo.casa.FakeDeviceRepository
import br.com.pompeo.casa.FakeErrorMapper
import br.com.pompeo.casa.FakeTokenRepository
import br.com.pompeo.casa.device
import br.com.pompeo.casa.domain.model.StreamSession
import br.com.pompeo.casa.domain.repository.DeviceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CameraViewModelTest {
    // Main de teste: runTest herda o relógio. Se stopLive voltasse ao viewModelScope (que usa Main), ele
    // andaria neste mesmo relógio e store.clear() o cancelaria antes de runCurrent(): o teste do appScope
    // pegaria a regressão em flagrante.
    @BeforeTest fun setUp() { Dispatchers.setMain(StandardTestDispatcher()) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun startLiveRethrowsCancellation() = runTest {
        // Arrange: o vídeo demora 1 s (tempo virtual), então a tela pode sair no meio.
        val devices = FakeDeviceRepository(FakeTokenRepository(TOKEN)).apply { startLiveDelayMs = 1_000 }
        val vm = CameraViewModel(devices, FakeErrorMapper, appScope = backgroundScope)
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

    @Test
    fun stopLiveRunsInAppScopeAndSurvivesTheViewModel() = runTest {
        // Arrange: o repositório falso registra os encerramentos. backgroundScope faz o papel do appScope:
        // anda no relógio do runTest e só é cancelado no fim do teste, como o real, que só morre com o app.
        val stops = mutableListOf<StreamSession>()
        val repository = object : DeviceRepository by FakeDeviceRepository(FakeTokenRepository(TOKEN)) {
            override suspend fun stopLive(session: StreamSession) { stops += session }
        }
        val store = ViewModelStore()
        // Caminho real de ciclo de vida: store.clear() é o que a pilha de navegação faz ao descartar a entrada.
        val vm = ViewModelProvider.create(
            store,
            viewModelFactory { initializer { CameraViewModel(repository, FakeErrorMapper, appScope = backgroundScope) } },
        )[CameraViewModel::class]
        val session = StreamSession(null, "rtsp://fake/CAM1", null, null)
        // Act: a tela sai (onDispose chama stopLive) e o ViewModel morre em seguida, antes de o pedido andar.
        vm.stopLive(session)
        store.clear()
        val beforeClock = stops.toList()
        runCurrent()
        // Assert: no viewModelScope o clear() teria cancelado o pedido; no appScope ele chega ao repositório.
        assertTrue(beforeClock.isEmpty(), "stopLive não bloqueia o onDispose: o pedido só anda com o relógio")
        assertEquals(listOf(session), stops)
    }

    private companion object {
        const val TOKEN = "Ot_session_000001"
    }
}
