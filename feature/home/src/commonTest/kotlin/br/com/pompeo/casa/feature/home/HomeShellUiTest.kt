package br.com.pompeo.casa.feature.home

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import br.com.pompeo.casa.FakeDeviceRepository
import br.com.pompeo.casa.FakeErrorMapper
import br.com.pompeo.casa.FakeTokenRepository
import br.com.pompeo.casa.device
import br.com.pompeo.casa.domain.DevicesState
import br.com.pompeo.casa.domain.model.Device
import br.com.pompeo.casa.domain.model.DeviceKind
import br.com.pompeo.casa.domain.model.DeviceQuery
import br.com.pompeo.casa.domain.model.OriginFilter
import br.com.pompeo.casa.domain.repository.DeviceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Teste de UI da casca da Home (RF02 lista, RF07 filtro de origem, RF08 paginação e a aba Definições), escrito
 * uma vez em commonTest e executado no simulador iOS e, como teste instrumentado, no aparelho Android. A tela
 * recebe o ViewModel real com os fakes de :core:testing; nenhuma chamada de rede acontece. A Home não dispara a
 * primeira página (isso é do DeviceRepositoryImpl, no init dele), só observa o estado do repositório: por isso
 * cada caso entrega o repositório já no estado que quer ver desenhado.
 */
@OptIn(ExperimentalTestApi::class)
class HomeShellUiTest {
    private val camera = device("CAM1", DeviceKind.CAMERA)
    private val lock = device("LOCK1", DeviceKind.LOCK)
    private val hub = device("HUB1", DeviceKind.HUB)
    private val page = listOf(camera, lock, hub)

    private fun loadedPage(hasMore: Boolean) = DevicesState.Content(page, DeviceQuery(), hasMore = hasMore, pagesLoaded = 1)

    @Test
    fun showsEveryDeviceOfTheLoadedPageByName() = runComposeUiTest {
        show(loadedPage(hasMore = false))
        page.forEach { onNodeWithText(it.name).performScrollTo().assertIsDisplayed() }
        // Página veio incompleta: a GDI não devolve total, então o rodapé fecha a lista em vez de oferecer mais.
        onNodeWithText("Sem mais informações").performScrollTo().assertIsDisplayed()
        onNodeWithText("1 página(s) · 3 dispositivo(s) · 5 por página").assertExists()
    }

    @Test
    fun emptyPageUnderAFilterSaysSoAndTheChipsStillChangeTheOrigin() = runComposeUiTest {
        val home = show(DevicesState.Empty(DeviceQuery(origin = OriginFilter.SHARED)))
        onNodeWithText("Nenhum dispositivo encontrado").performScrollTo().assertIsDisplayed()
        onNodeWithText("Nenhum dispositivo compartilhado com esta conta.").assertExists()
        onNodeWithText("Vinculados").performScrollTo().performClick()
        assertEquals(listOf(OriginFilter.LINKED), home.repository.origins)
    }

    @Test
    fun offersLoadMoreWhileThePageCameFullAndAsksTheRepositoryForTheNextOne() = runComposeUiTest {
        val home = show(loadedPage(hasMore = true))
        onNodeWithText("Sem mais informações").assertDoesNotExist()
        onNodeWithText("Carregar mais").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(1, home.repository.loadMores)
    }

    @Test
    fun tappingACardRoutesCamerasToOnCameraAndTheRestToOnDevice() = runComposeUiTest {
        val home = show(loadedPage(hasMore = false))
        onNodeWithText(camera.name).performScrollTo().performClick()
        assertEquals(listOf(camera), home.openedCameras)
        assertTrue(home.openedDevices.isEmpty())
        onNodeWithText(lock.name).performScrollTo().performClick()
        assertEquals(listOf(lock), home.openedDevices)
        assertEquals(listOf(camera), home.openedCameras)
    }

    @Test
    fun signingOutFromTheSettingsTabCallsOnLogout() = runComposeUiTest {
        val home = show(loadedPage(hasMore = false))
        onNodeWithText("Definições").performClick()
        onNodeWithText("Conta Intelbras").assertIsDisplayed()
        onNodeWithText("Sair").performClick()
        assertEquals(1, home.logouts)
    }

    /** Casca sob teste: ViewModel real com os fakes, e os callbacks que o App receberia. */
    private class HomeUnderTest(initial: DevicesState) {
        val tokens = FakeTokenRepository("Ot_session_000001")
        val repository = SeededRepository(FakeDeviceRepository(tokens), initial)
        val vm = HomeViewModel(repository, tokens, FakeErrorMapper)
        val openedCameras = mutableListOf<Device>()
        val openedDevices = mutableListOf<Device>()
        var logouts = 0
    }

    private fun ComposeUiTest.show(initial: DevicesState): HomeUnderTest =
        HomeUnderTest(initial).also { home ->
            setContent {
                HomeShell(
                    home.vm,
                    onCamera = { home.openedCameras += it },
                    onDevice = { home.openedDevices += it },
                    onLogout = { home.logouts++ },
                )
            }
        }

    /**
     * O fake de core:testing só sai de NoToken por loadFirstPage() (suspensa) e não registra loadMore/setOrigin;
     * este decorador entrega o estado já pronto (e a query que o acompanha) e conta os dois pedidos. O resto
     * (logout, parceiros, token) segue para o fake.
     */
    private class SeededRepository(delegate: DeviceRepository, initial: DevicesState) : DeviceRepository by delegate {
        override val state: StateFlow<DevicesState> = MutableStateFlow(initial)
        override val query: StateFlow<DeviceQuery> = MutableStateFlow(initial.queryOrDefault())
        var loadMores = 0
        val origins = mutableListOf<OriginFilter>()
        override fun loadMore() { loadMores++ }
        override fun setOrigin(filter: OriginFilter) { origins += filter }
    }
}

private fun DevicesState.queryOrDefault(): DeviceQuery =
    when (this) {
        is DevicesState.Content -> query
        is DevicesState.Empty -> query
        is DevicesState.Error -> query
        DevicesState.NoToken, DevicesState.Loading -> DeviceQuery()
    }
