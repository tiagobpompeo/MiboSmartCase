package br.com.pompeo.casa.feature.device

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import br.com.pompeo.casa.FakeDeviceRepository
import br.com.pompeo.casa.FakeErrorMapper
import br.com.pompeo.casa.FakeLockController
import br.com.pompeo.casa.FakeTokenRepository
import br.com.pompeo.casa.device
import br.com.pompeo.casa.domain.model.Device
import br.com.pompeo.casa.domain.model.DeviceKind
import br.com.pompeo.casa.domain.model.LockDetails
import br.com.pompeo.casa.domain.model.LockVolume
import br.com.pompeo.casa.domain.repository.DeviceRepository
import br.com.pompeo.casa.domain.usecase.ChangeLockVolumeUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Testes de UI da tela de detalhe (RF05 fechadura, RF06 volume, RF09 histórico e o hub), escritos uma vez em
 * commonTest e executados no simulador iOS e, como teste instrumentado, no aparelho Android. A tela recebe o
 * [DeviceViewModel] real com os fakes de :core:testing: todo comando "físico" chega ao [FakeLockController], que
 * é um dublê em memória, nunca à API da Intelbras. Cobre: leitura da fechadura (estado, bateria, conexão e a
 * instrução de segurar); a porta só muda com toque longo + confirmação e cada confirmação manda um único comando;
 * o cartão Volume com os quatro níveis e a troca confirmada; o histórico que abre com 10 eventos e pede 30 em
 * "Ver mais"; e o hub com os subdispositivos e o diálogo de firmware consultado na hora.
 */
@OptIn(ExperimentalTestApi::class)
class DeviceScreenUiTest {
    private val lock = device("LOCK1", kind = DeviceKind.LOCK)

    /**
     * Embrulho do [FakeDeviceRepository] para a tela: publica a lista de dispositivos (o fake publica vazio, e a
     * tela acha o dispositivo pelo ns da rota nela) e, como o parceiro real (GdiDeviceProvider.details), a leitura
     * completa da fechadura já traz os 10 primeiros eventos do histórico (o [FakeLockController] devolve a lista
     * vazia). O resto (porta, volume, firmware, online) é delegado ao fake sem mudança.
     */
    private class ScreenRepository(private val inner: FakeDeviceRepository, devices: List<Device>) : DeviceRepository by inner {
        override val devices: StateFlow<List<Device>> = MutableStateFlow(devices)
        override suspend fun lockDetails(lock: Device): LockDetails =
            inner.lockDetails(lock).copy(history = inner.lockHistory(lock, DeviceViewModel.HISTORY_FIRST))
    }

    /**
     * Mesma montagem do DeviceViewModelTest. O appScope de produção vive enquanto o app vive; aqui é um escopo
     * Unconfined: a fechadura falsa responde sem suspender, então o comando vai de Sending a Done dentro do
     * próprio clique em "Confirmar" (Dispatchers.Main não gira no binário de teste).
     */
    private fun viewModel(devices: List<Device>, locks: FakeLockController = FakeLockController()): DeviceViewModel {
        val repository = ScreenRepository(FakeDeviceRepository(FakeTokenRepository("Ot_session_000001"), locks = locks), devices)
        return DeviceViewModel(repository, ChangeLockVolumeUseCase(repository), FakeErrorMapper, appScope = CoroutineScope(Dispatchers.Unconfined))
    }

    /** Espera um texto que depende de uma corrotina (leitura da fechadura, histórico, firmware) chegar à tela. */
    private fun ComposeUiTest.awaitText(text: String, count: Int = 1) =
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(text).fetchSemanticsNodes().size == count }

    @Test
    fun aLockShowsItsDoorStateBatteryConnectionAndTheHoldInstruction() = runComposeUiTest {
        // Arrange: o FakeLockController responde porta fechada, 50 % de bateria e abertura remota habilitada.
        setContent { DeviceScreen(viewModel(listOf(lock)), ns = "LOCK1", onBack = {}) }
        // Assert
        awaitText("Porta fechada")
        onNodeWithText("Dispositivo LOCK1").assertIsDisplayed()
        onNodeWithText("Porta fechada").assertIsDisplayed()
        onNodeWithText("50%").assertIsDisplayed()
        onNodeWithText("Online").assertIsDisplayed()
        onNodeWithText("Pressione e segure para abrir a porta.").assertIsDisplayed()
    }

    @Test
    fun theDoorOnlyChangesAfterAHoldAndAConfirmation() = runComposeUiTest {
        // Arrange
        val locks = FakeLockController()
        setContent { DeviceScreen(viewModel(listOf(lock), locks), ns = "LOCK1", onBack = {}) }
        awaitText("Porta fechada")
        val circle = onNodeWithContentDescription("Pressione e segure para abrir a porta")
        // Act/Assert: um toque simples não abre a confirmação (RF05: só segurando 600 ms).
        circle.performClick()
        onNodeWithText("Destrancar a fechadura?").assertDoesNotExist()
        // Segurar abre a confirmação; "Cancelar" fecha sem mandar nada ao dublê.
        circle.performTouchInput { longClick(durationMillis = 1_000) }
        onNodeWithText("Destrancar a fechadura?").assertIsDisplayed()
        onNodeWithText("O comando será enviado para Dispositivo LOCK1 pela nuvem Intelbras.").assertIsDisplayed()
        onNodeWithText("Cancelar").performClick()
        onNodeWithText("Destrancar a fechadura?").assertDoesNotExist()
        assertEquals(emptyList(), locks.doorCommands)
        // Segurar de novo e confirmar manda exatamente um comando de abrir ao FakeLockController (não à API).
        circle.performTouchInput { longClick(durationMillis = 1_000) }
        onNodeWithText("Confirmar").performClick()
        waitUntil(timeoutMillis = 5_000) { locks.doorCommands.isNotEmpty() }
        onNodeWithText("Destrancar a fechadura?").assertDoesNotExist()
        assertEquals(listOf(true), locks.doorCommands)
    }

    @Test
    fun theVolumeCardOffersTheFourLevelsAndChangesTheVolumeAfterConfirmation() = runComposeUiTest {
        // Arrange: o dublê começa em Médio.
        val locks = FakeLockController()
        setContent { DeviceScreen(viewModel(listOf(lock), locks), ns = "LOCK1", onBack = {}) }
        awaitText("Porta fechada")
        // Act: abrir o cartão Volume.
        onNodeWithText("Volume").performScrollTo().performClick()
        // Assert: os quatro níveis, com o atual selecionado.
        listOf("Mudo", "Baixo", "Médio", "Alto").forEach { onNodeWithText(it).performScrollTo().assertIsDisplayed() }
        onNodeWithText("Médio").assertIsSelected()
        onNodeWithText("Alto").assertIsNotSelected()
        // Act: escolher Alto passa pela confirmação (RF06) e só então grava no dublê.
        onNodeWithText("Alto").performClick()
        onNodeWithText("Alterar o volume para Alto?").assertIsDisplayed()
        assertEquals(LockVolume.MEDIUM, locks.volume)
        onNodeWithText("Confirmar").performClick()
        waitUntil(timeoutMillis = 5_000) { locks.volume == LockVolume.HIGH }
        // Assert: a tela mostra o volume relido pelo caso de uso.
        awaitText("Volume alterado para Alto")
        onNodeWithText("Alto").assertIsSelected()
        onNodeWithText("Médio").assertIsNotSelected()
    }

    @Test
    fun theHistoryOpensWithTenEventsAndSeeMoreFetchesThirty() = runComposeUiTest {
        // Arrange: a leitura completa já traz os 10 primeiros eventos (RF09), como no parceiro real.
        val locks = FakeLockController()
        setContent { DeviceScreen(viewModel(listOf(lock), locks), ns = "LOCK1", onBack = {}) }
        awaitText("Remoto (APP)", count = DeviceViewModel.HISTORY_FIRST)
        // Assert: 10 eventos, o último horário visível ao rolar, e o cartão oferece "Ver mais".
        onNodeWithText("Histórico de abertura").performScrollTo().assertIsDisplayed()
        onNodeWithText("02/10/2026 16:29:09").performScrollTo().assertIsDisplayed()
        onAllNodesWithText("Remoto (APP)").assertCountEquals(DeviceViewModel.HISTORY_FIRST)
        onNodeWithContentDescription("Ver mais").assertExists()
        assertEquals(listOf(DeviceViewModel.HISTORY_FIRST), locks.historyRequests)
        // Act: "Ver mais" pede 30 ao dublê.
        onNodeWithText("Ver mais").performScrollTo().assertIsDisplayed().performClick()
        awaitText("Remoto (APP)", count = DeviceViewModel.HISTORY_MORE)
        // Assert: expandido, o botão some e o cabeçalho passa a oferecer "Ver menos".
        onNodeWithText("Ver mais").assertDoesNotExist()
        onNodeWithContentDescription("Ver menos").assertExists()
        assertEquals(listOf(DeviceViewModel.HISTORY_FIRST, DeviceViewModel.HISTORY_MORE), locks.historyRequests)
    }

    @Test
    fun aHubListsItsChildrenAndTheFirmwareDialogQueriesTheLiveVersion() = runComposeUiTest {
        // Arrange: a listagem diz "atualização disponível"; a consulta na hora (fake) responde 1.0 atualizado.
        val hub = device("HUB1", kind = DeviceKind.HUB).copy(version = "2.3.1", updateAvailable = true)
        val sensor = device("S1", kind = DeviceKind.SENSOR).copy(subdevice = true, parentNs = "HUB1")
        setContent { DeviceScreen(viewModel(listOf(hub, sensor)), ns = "HUB1", onBack = {}) }
        // Assert: cabeçalho, subdispositivos e a linha de firmware da listagem.
        onNodeWithText("Online").assertIsDisplayed()
        onNodeWithText("Dispositivos conectados ao hub (1)").performScrollTo().assertIsDisplayed()
        onNodeWithText("Dispositivo S1").performScrollTo().assertIsDisplayed()
        onNodeWithText("Sensor • online").assertIsDisplayed()
        onNodeWithText("Firmware 2.3.1").performScrollTo().assertIsDisplayed()
        onNodeWithText("Atualização disponível").assertIsDisplayed()
        // Act: o botão Firmware consulta o firmware na hora.
        onNodeWithContentDescription("Firmware").performClick()
        // Assert
        awaitText("Versão 1.0")
        onNodeWithText("Atualizado").assertIsDisplayed()
        onNodeWithText("Entendi").performClick()
        onNodeWithText("Versão 1.0").assertDoesNotExist()
    }
}
