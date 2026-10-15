package br.com.pompeo.casa.feature.camera

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.waitUntilExactlyOneExists
import androidx.compose.ui.unit.dp
import br.com.pompeo.casa.FakeDeviceRepository
import br.com.pompeo.casa.FakeErrorMapper
import br.com.pompeo.casa.FakeTokenRepository
import br.com.pompeo.casa.device
import br.com.pompeo.casa.domain.model.Device
import br.com.pompeo.casa.domain.model.StreamSession
import br.com.pompeo.casa.domain.repository.DeviceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Teste de UI da tela de vídeo ao vivo (RF03), escrito uma vez em commonTest e executado no simulador iOS e,
 * como teste instrumentado, no aparelho Android. Cobre o que a tela mostra enquanto pede a sessão à GDI
 * (nome da câmera, etapa e porcentagem), o erro com "Tentar novamente" quando o pedido falha, os avisos dos
 * controles sem rota na GDI (PTZ, gravar, Multiview), a troca de lente da iM4 Dual e o botão voltar.
 *
 * O player real (expect CameraPlayer) nunca é composto: no iOS ele exige o NativeVideoPlayer registrado pelo
 * Swift e no Android carrega a libVLC. Por isso a sessão ao vivo fica pendente para sempre ou falha; a tela
 * nunca chega ao estado "AO VIVO". A tela recebe o ViewModel real com os fakes de :core:testing; nenhuma
 * chamada de rede acontece.
 */
@OptIn(ExperimentalTestApi::class)
class CameraScreenUiTest {
    /**
     * Repositório da tela: o FakeDeviceRepository nasce com a lista de dispositivos vazia e o startLive dele
     * nunca falha, então este delegado expõe a [camera] na lista e entrega a sessão a [live]. [channels] registra
     * o canalVideo de cada pedido (0 = lente móvel, 1 = lente fixa).
     */
    private class CameraRepository(
        camera: Device,
        private val live: suspend (channel: Int) -> StreamSession,
    ) : DeviceRepository by FakeDeviceRepository(FakeTokenRepository()) {
        override val devices: StateFlow<List<Device>> = MutableStateFlow(listOf(camera))
        val channels = mutableListOf<Int>()

        override suspend fun startLive(camera: Device, channel: Int): StreamSession {
            channels += channel
            return live(channel)
        }
    }

    /** A GDI nunca responde: a tela fica em "Solicitando sessão" e o player não é composto. */
    private fun pendingRepository(camera: Device = device(NS)) = CameraRepository(camera) { awaitCancellation() }

    /** A GDI falha na hora: o FakeErrorMapper transforma qualquer falha em AppError.Unexpected. */
    private fun failingRepository(camera: Device = device(NS)) =
        CameraRepository(camera) { throw IllegalStateException("HTTP 500 em criar-fluxo-video") }

    // O appScope só recebe stopLive, e stopLive só roda com sessão aberta: nestes testes nunca há uma.
    private fun viewModel(repository: DeviceRepository) =
        CameraViewModel(repository, FakeErrorMapper, appScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))

    /**
     * Moldura de celular em pé. O canvas do runComposeUiTest no simulador iOS é 1024×768 (paisagem): nele a
     * coluna da tela (cabeçalho + vídeo 16:9 + PTZ + barra inferior) não cabe e o PTZ ficaria com altura zero.
     */
    @Composable
    private fun Phone(content: @Composable () -> Unit) {
        Box(Modifier.requiredSize(PHONE_WIDTH, PHONE_HEIGHT)) { content() }
    }

    @Test
    fun showsTheCameraNameAndTheConnectingStepWhileTheSessionIsPending() = runComposeUiTest {
        val repository = pendingRepository()
        setContent { Phone { CameraScreen(viewModel(repository), ns = NS, onBack = {}) } }

        onNodeWithText("Dispositivo $NS").assertIsDisplayed()
        onNodeWithText("1/1").assertIsDisplayed()
        onNodeWithText(CONNECTING_STEP).assertIsDisplayed()
        onNodeWithText("%", substring = true).assertIsDisplayed()
        onNodeWithText("AO VIVO").assertDoesNotExist()
        onNodeWithText(RETRY).assertDoesNotExist()
        assertEquals(listOf(0), repository.channels, "abre a lente móvel (canalVideo 0) ao entrar")
    }

    @Test
    fun showsTheErrorAndRequestsTheSessionAgainOnRetry() = runComposeUiTest {
        val repository = failingRepository()
        setContent { Phone { CameraScreen(viewModel(repository), ns = NS, onBack = {}) } }

        waitUntilExactlyOneExists(hasText(RETRY))
        onNodeWithText(UNEXPECTED_MESSAGE).assertIsDisplayed()
        onNodeWithText(CONNECTING_STEP).assertDoesNotExist()

        onNodeWithText(RETRY).performClick()

        waitUntil("a GDI recebe o segundo pedido") { repository.channels.size == 2 }
        assertEquals(listOf(0, 0), repository.channels, "tentar novamente repete a mesma lente")
        waitUntilExactlyOneExists(hasText(RETRY))
        onNodeWithText(UNEXPECTED_MESSAGE).assertIsDisplayed()
    }

    @Test
    fun warnsThatPtzIsNotAvailableOnTheGdiApi() = runComposeUiTest {
        setContent { Phone { CameraScreen(viewModel(pendingRepository()), ns = NS, onBack = {}) } }

        onNodeWithContentDescription("Abrir controle PTZ").performClick()
        onNodeWithContentDescription("Para cima").performClick()

        waitUntilExactlyOneExists(hasText("PTZ não disponível na API GDI"))
        onNodeWithText("PTZ não disponível na API GDI").assertIsDisplayed()

        onNodeWithContentDescription("Fechar PTZ").performClick()
        onNodeWithContentDescription("Para cima").assertDoesNotExist()
        onNodeWithContentDescription("Abrir controle PTZ").assertIsDisplayed()
    }

    @Test
    fun warnsThatRecordingAndMultiviewAreUnavailableOnTheGdiApi() = runComposeUiTest {
        setContent { Phone { CameraScreen(viewModel(pendingRepository()), ns = NS, onBack = {}) } }

        onNodeWithContentDescription("Gravar").performClick()
        waitUntilExactlyOneExists(hasText("Indisponível na API GDI"))
        onNodeWithText("Indisponível na API GDI").assertIsDisplayed()

        onNodeWithContentDescription("Multiview").performClick()
        waitUntilExactlyOneExists(hasText("Multiview indisponível na API GDI"))
        onNodeWithText("Multiview indisponível na API GDI").assertIsDisplayed()
    }

    @Test
    fun switchesToTheFixedLensOnADualCameraAndGoesBackFromTheHeader() = runComposeUiTest {
        val repository = pendingRepository(device(NS).copy(model = "iM4 Dual"))
        var backs = 0
        setContent { Phone { CameraScreen(viewModel(repository), ns = NS, onBack = { backs++ }) } }

        onNodeWithText("Lente móvel").assertIsDisplayed()
        onNodeWithText("Lente fixa").assertIsDisplayed()
        onNodeWithText(SAME_STREAM_NOTE).assertDoesNotExist()

        onNodeWithText("Lente fixa").performClick()

        waitUntil("a GDI recebe o pedido da lente fixa") { repository.channels.size == 2 }
        assertEquals(listOf(0, 1), repository.channels, "trocar de lente reinicia a sessão no canalVideo 1")
        onNodeWithText(SAME_STREAM_NOTE).assertIsDisplayed()

        onNodeWithContentDescription("Voltar").performClick()
        assertEquals(1, backs, "o botão voltar devolve a navegação à lista")
    }

    private companion object {
        const val NS = "CAM1"
        const val CONNECTING_STEP = "Solicitando sessão à Intelbras"
        const val RETRY = "Tentar novamente"
        /** userMessage de AppError.Unexpected, o que o FakeErrorMapper devolve para qualquer falha. */
        const val UNEXPECTED_MESSAGE = "Algo deu errado. Tente novamente."
        val PHONE_WIDTH = 360.dp
        val PHONE_HEIGHT = 720.dp
    }
}
