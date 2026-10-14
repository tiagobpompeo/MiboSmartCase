package br.com.pompeo.casa.feature.device

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.com.pompeo.casa.FakeDeviceRepository
import br.com.pompeo.casa.FakeErrorMapper
import br.com.pompeo.casa.FakeLockController
import br.com.pompeo.casa.FakeTokenRepository
import br.com.pompeo.casa.device
import br.com.pompeo.casa.domain.model.DeviceKind
import br.com.pompeo.casa.domain.model.LockVolume
import br.com.pompeo.casa.domain.usecase.ChangeLockVolumeUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.assertIs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceViewModelTest {
    private val lock = device("LOCK1", kind = DeviceKind.LOCK)

    /**
     * O appScope de produção vive enquanto o app vive; aqui é o próprio TestScope do runTest, com o
     * StandardTestDispatcher: o comando só anda quando o teste avança o relógio virtual (runCurrent/advanceUntilIdle).
     * Sem Dispatchers.setMain: o ViewModel não toca o viewModelScope.
     */
    private fun TestScope.deviceViewModel(locks: FakeLockController): DeviceViewModel {
        val repository = FakeDeviceRepository(FakeTokenRepository("Ot_session_000001"), locks = locks)
        return DeviceViewModel(repository, ChangeLockVolumeUseCase(repository), FakeErrorMapper, appScope = this)
    }

    @Test
    fun doorCommandRunsInAppScopeAndReportsDone() = runTest {
        // Arrange: o comando demora 1 s (tempo virtual).
        val locks = FakeLockController().apply { commandDelayMs = 1_000 }
        val vm = deviceViewModel(locks)
        // Act: sendDoorCommand não suspende; o envio segue no appScope.
        vm.sendDoorCommand(lock, open = true)
        // Assert
        assertEquals(LockCommand.Sending("LOCK1", LockAction.Door(open = true)), vm.lockCommand.value)
        advanceUntilIdle()
        assertEquals(LockCommand.Done("LOCK1", LockAction.Door(open = true), error = null), vm.lockCommand.value)
        assertEquals(listOf(true), locks.doorCommands)
    }

    @Test
    fun clearingTheViewModelMidCommandDoesNotCancelTheCommand() = runTest {
        // Arrange: o comando demora 1 s (tempo virtual) e o ViewModel nasce pelo caminho real de ciclo de vida.
        val locks = FakeLockController().apply { commandDelayMs = 1_000 }
        val store = ViewModelStore()
        val vm = ViewModelProvider.create(store, viewModelFactory { initializer { this@runTest.deviceViewModel(locks) } })[DeviceViewModel::class]
        vm.sendDoorCommand(lock, open = true)
        runCurrent()
        assertEquals(LockCommand.Sending("LOCK1", LockAction.Door(open = true)), vm.lockCommand.value)
        // Act: sair da tela limpa o ViewModel (store.clear() cancelaria o viewModelScope; o appScope segue vivo).
        store.clear()
        advanceUntilIdle()
        // Assert: o pedido terminou e publicou o Done; com viewModelScope ele ficaria preso em Sending.
        assertEquals(LockCommand.Done("LOCK1", LockAction.Door(open = true), error = null), vm.lockCommand.value)
        assertEquals(listOf(true), locks.doorCommands)
    }

    @Test
    fun doorCommandFailureIsReportedAsDoneWithError() = runTest {
        // Arrange
        val failure = IllegalStateException("HTTP 500")
        val locks = FakeLockController().apply { commandError = failure }
        val vm = deviceViewModel(locks)
        // Act
        vm.sendDoorCommand(lock, open = false)
        advanceUntilIdle()
        // Assert: o texto já vem amigável; a tela só acrescenta "Comando falhou:".
        assertEquals(LockCommand.Done("LOCK1", LockAction.Door(open = false), error = vm.messageOf(failure)), vm.lockCommand.value)
    }

    @Test
    fun volumeCommandReportsTheAppliedVolume() = runTest {
        // Arrange: a escrita é aceita, mas a releitura falha como o HTTP 500 real.
        val locks = FakeLockController().apply { readError = IllegalStateException("HTTP 500") }
        val vm = deviceViewModel(locks)
        // Act
        vm.changeLockVolume(lock, LockVolume.HIGH)
        advanceUntilIdle()
        // Assert: o ChangeLockVolumeUseCase real assume o pedido quando a releitura falha.
        val expected = LockCommand.Done("LOCK1", LockAction.Volume(LockVolume.HIGH), error = null, appliedVolume = LockVolume.HIGH)
        assertEquals(expected, vm.lockCommand.value)
        assertEquals(LockVolume.HIGH, locks.volume)
    }

    @Test
    fun volumeWriteFailureIsReportedAsDoneWithErrorAndKeepsTheOldVolume() = runTest {
        // Arrange: a própria escrita falha.
        val failure = IllegalStateException("HTTP 500")
        val locks = FakeLockController().apply { writeError = failure }
        val vm = deviceViewModel(locks)
        // Act
        vm.changeLockVolume(lock, LockVolume.HIGH)
        advanceUntilIdle()
        // Assert: sem appliedVolume, a tela não mostra um volume que não foi aplicado.
        val expected = LockCommand.Done("LOCK1", LockAction.Volume(LockVolume.HIGH), error = vm.messageOf(failure), appliedVolume = null)
        assertEquals(expected, vm.lockCommand.value)
        assertEquals(LockVolume.MEDIUM, locks.volume)
    }

    @Test
    fun secondCommandWhileSendingIsIgnored() = runTest {
        // Arrange: o 1.º comando demora 1 s (tempo virtual) e ainda está em andamento.
        val locks = FakeLockController().apply { commandDelayMs = 1_000 }
        val vm = deviceViewModel(locks)
        vm.sendDoorCommand(lock, open = true)
        runCurrent()
        // Act
        vm.sendDoorCommand(lock, open = false)
        vm.changeLockVolume(lock, LockVolume.LOW)
        advanceUntilIdle()
        // Assert: só o 1.º chegou à fechadura e é o resultado publicado.
        assertEquals(listOf(true), locks.doorCommands)
        assertEquals(LockVolume.MEDIUM, locks.volume)
        assertEquals(LockCommand.Done("LOCK1", LockAction.Door(open = true), error = null), vm.lockCommand.value)
    }

    @Test
    fun handledCommandFreesTheSlotForTheNextOne() = runTest {
        // Arrange: um comando concluído.
        val locks = FakeLockController()
        val vm = deviceViewModel(locks)
        vm.sendDoorCommand(lock, open = true)
        advanceUntilIdle()
        // Act
        vm.lockCommandHandled()
        val afterHandled = vm.lockCommand.value
        vm.sendDoorCommand(lock, open = false)
        advanceUntilIdle()
        // Assert
        assertNull(afterHandled)
        assertEquals(listOf(true, false), locks.doorCommands)
    }

    @Test
    fun historyAsksTenOnOpenAndThirtyOnSeeMore() = runTest {
        // Arrange
        val locks = FakeLockController()
        val vm = deviceViewModel(locks)
        // Act
        val first = vm.lockHistory(lock, more = false).getOrThrow()
        val more = vm.lockHistory(lock, more = true).getOrThrow()
        // Assert: a GDI pagina só por quantidade (RF09).
        assertEquals(DeviceViewModel.HISTORY_FIRST, first.size)
        assertEquals(DeviceViewModel.HISTORY_MORE, more.size)
        assertEquals(listOf(10, 30), locks.historyRequests)
    }

    @Test
    fun theInFlightGuardIsSharedBetweenViewModelInstances() = runTest {
        // Sair da tela no meio do envio e voltar cria um ViewModel novo: ele deve ver o comando em andamento.
        val locks = FakeLockController().apply { commandDelayMs = 1_000 }
        val repository = FakeDeviceRepository(FakeTokenRepository("Ot_session_000001"), locks = locks)
        val shared = MutableStateFlow<LockCommand?>(null)
        val first = DeviceViewModel(repository, ChangeLockVolumeUseCase(repository), FakeErrorMapper, appScope = this, commandState = shared)
        val second = DeviceViewModel(repository, ChangeLockVolumeUseCase(repository), FakeErrorMapper, appScope = this, commandState = shared)

        first.sendDoorCommand(lock, open = true)
        runCurrent()
        second.sendDoorCommand(lock, open = false) // ignorado: já há um comando em voo no app
        advanceUntilIdle()

        assertEquals(listOf(true), locks.doorCommands)
        assertIs<LockCommand.Done>(second.lockCommand.value) // a instância nova recebe o resultado
    }
}
