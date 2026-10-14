package br.com.pompeo.casa.feature.home

import br.com.pompeo.casa.FakeDeviceRepository
import br.com.pompeo.casa.FakeErrorMapper
import br.com.pompeo.casa.FakeTokenRepository
import br.com.pompeo.casa.device
import br.com.pompeo.casa.domain.AppError
import br.com.pompeo.casa.domain.DevicesState
import br.com.pompeo.casa.domain.TokenFormat
import br.com.pompeo.casa.domain.model.OriginFilter
import br.com.pompeo.casa.domain.repository.DeviceRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * O ViewModel da Home não lança corrotinas (tudo é delegado ao repositório), então os testes dispensam
 * runTest e Dispatchers.setMain.
 */
class HomeViewModelTest {
    private fun homeViewModel(devices: DeviceRepository, tokens: FakeTokenRepository) =
        HomeViewModel(devices, tokens, FakeErrorMapper)

    @Test
    fun tokenPresentButNotPersistedRaisesTheNoticeOnInit() {
        // Arrange: Keystore/Keychain quebrado; a tela de token validou e tentou gravar antes de chegar aqui.
        val tokens = FakeTokenRepository(canPersist = false)
        tokens.persist("Ot_valid_0001")
        assertFalse(tokens.persisted)
        // Act: a Home nasce com o token válido só nesta sessão.
        val vm = homeViewModel(FakeDeviceRepository(tokens), tokens)
        // Assert: avisa em vez de fingir erro, e o aviso é consumido uma vez só.
        assertEquals(HomeViewModel.TOKEN_NOT_PERSISTED, vm.notice.value)
        vm.noticeShown()
        assertNull(vm.notice.value)
    }

    @Test
    fun persistedTokenRaisesNoNotice() {
        // Arrange: token já gravado (abertura normal do app).
        val tokens = FakeTokenRepository("Ot_session_000001")
        // Act
        val vm = homeViewModel(FakeDeviceRepository(tokens), tokens)
        // Assert
        assertNull(vm.notice.value)
    }

    @Test
    fun missingTokenRaisesNoNotice() {
        // Arrange: sem token não há o que avisar (o App nem chega à Home).
        val tokens = FakeTokenRepository()
        // Act
        val vm = homeViewModel(FakeDeviceRepository(tokens), tokens)
        // Assert
        assertNull(vm.notice.value)
    }

    @Test
    fun logoutClearsTheSessionThroughTheRepository() {
        // Arrange
        val tokens = FakeTokenRepository("Ot_session_000001")
        val devices = FakeDeviceRepository(tokens)
        val vm = homeViewModel(devices, tokens)
        // Act
        vm.logout()
        // Assert: o repositório é quem apaga o token e volta a NoToken; o ViewModel só repassa.
        assertEquals(1, devices.logouts)
        assertNull(tokens.token.value)
        assertEquals(DevicesState.NoToken, devices.state.value)
        assertNull(vm.token.value)
    }

    @Test
    fun exposesTheRepositoryFlowsAndTheMaskedToken() {
        // Arrange
        val tokens = FakeTokenRepository("Ot_session_000001")
        val devices = FakeDeviceRepository(tokens)
        // Act
        val vm = homeViewModel(devices, tokens)
        // Assert: mesmos fluxos do repositório (uma fonte de verdade), token só mascarado (regra 0.2.2).
        assertSame(devices.state, vm.state)
        assertSame(devices.query, vm.query)
        assertSame(devices.devices, vm.devices)
        assertSame(tokens.token, vm.token)
        assertEquals(TokenFormat.mask("Ot_session_000001"), vm.maskedToken())
        assertEquals(devices.providerNames, vm.providerNames)
        assertEquals(devices.providerName(device("CAM1")), vm.providerName(device("CAM1")))
    }

    @Test
    fun listActionsAreDelegatedToTheRepository() {
        // Arrange: o fake de core:testing não conta refresh/loadMore/filtros, então um decorador local conta.
        val tokens = FakeTokenRepository("Ot_session_000001")
        val devices = CountingRepository(FakeDeviceRepository(tokens))
        val vm = homeViewModel(devices, tokens)
        // Act
        vm.refresh()
        vm.loadMore()
        vm.setOrigin(OriginFilter.SHARED)
        vm.setPageSize(50)
        // Assert
        assertEquals(1, devices.refreshes)
        assertEquals(1, devices.loadMores)
        assertEquals(listOf(OriginFilter.SHARED), devices.origins)
        assertEquals(listOf(50), devices.pageSizes)
    }

    @Test
    fun messageOfUsesTheErrorMapper() {
        // Arrange
        val tokens = FakeTokenRepository("Ot_session_000001")
        val vm = homeViewModel(FakeDeviceRepository(tokens), tokens)
        val failure = IllegalStateException("HTTP 500")
        // Act
        val message = vm.messageOf(failure)
        // Assert: o FakeErrorMapper devolve Unexpected; a tela recebe o texto amigável, nunca o cru.
        assertEquals(AppError.Unexpected("HTTP 500").userMessage, message)
    }

    /** Decorador que registra as chamadas da lista; o resto segue para o fake de core:testing. */
    private class CountingRepository(private val delegate: DeviceRepository) : DeviceRepository by delegate {
        var refreshes = 0
        var loadMores = 0
        val origins = mutableListOf<OriginFilter>()
        val pageSizes = mutableListOf<Int>()
        override fun refresh() { refreshes++; delegate.refresh() }
        override fun loadMore() { loadMores++; delegate.loadMore() }
        override fun setOrigin(filter: OriginFilter) { origins += filter; delegate.setOrigin(filter) }
        override fun setPageSize(size: Int) { pageSizes += size; delegate.setPageSize(size) }
    }
}
