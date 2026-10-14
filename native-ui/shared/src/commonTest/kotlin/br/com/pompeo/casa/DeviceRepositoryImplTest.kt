package br.com.pompeo.casa

import br.com.pompeo.casa.data.DeviceRepositoryImpl
import br.com.pompeo.casa.data.TokenRepositoryImpl
import br.com.pompeo.casa.data.gdi.GdiException
import br.com.pompeo.casa.domain.AppError
import br.com.pompeo.casa.domain.DevicesState
import br.com.pompeo.casa.domain.model.Device
import br.com.pompeo.casa.domain.model.DeviceKind
import br.com.pompeo.casa.domain.model.DeviceOrigin
import br.com.pompeo.casa.domain.model.DeviceQuery
import br.com.pompeo.casa.domain.model.LockVolume
import br.com.pompeo.casa.domain.model.OriginFilter
import br.com.pompeo.casa.domain.provider.DeviceProvider
import br.com.pompeo.casa.domain.provider.LockController
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Regras (M5): escopo do repositório sempre backgroundScope; nunca advanceUntilIdle para trabalho do repositório
// (ele não espera o backgroundScope): advanceTimeBy(<ms do FakeProvider>) + runCurrent().
@OptIn(ExperimentalCoroutinesApi::class)
class DeviceRepositoryImplTest {
    private val seven = (1..7).map { device("D$it") } // página 1 cheia (5), página 2 curta (2)
    private val byOrigin = listOf(
        device("L1", origin = DeviceOrigin.LINKED),
        device("S1", origin = DeviceOrigin.SHARED),
        device("U1", origin = DeviceOrigin.UNKNOWN),
    )

    /** Token só na sessão (como durante a validação): nada carrega até o teste pedir. */
    private fun TestScope.signedIn(vararg providers: DeviceProvider): DeviceRepositoryImpl {
        val tokens = TokenRepositoryImpl(InMemoryTokenStorage())
        val repo = DeviceRepositoryImpl(tokens, providers.toList(), backgroundScope)
        tokens.setSession(SESSION_TOKEN)
        return repo
    }

    @Test
    fun withoutTokenStateIsNoToken() = runTest {
        // Arrange
        val provider = FakeProvider(seven)
        val repo = DeviceRepositoryImpl(TokenRepositoryImpl(InMemoryTokenStorage()), listOf(provider), backgroundScope)
        // Act
        runCurrent()
        val direct = repo.loadFirstPage()
        // Assert
        assertEquals(DevicesState.NoToken, repo.state.value)
        assertEquals(DevicesState.NoToken, direct)
        assertTrue(provider.queries.isEmpty(), "sem token nenhum parceiro é consultado")
    }

    @Test
    fun persistedTokenLoadsFirstPageAtStartup() = runTest {
        // Arrange
        val provider = FakeProvider(seven)
        // Act
        val repo = DeviceRepositoryImpl(TokenRepositoryImpl(InMemoryTokenStorage("Ot_old_token_0001")), listOf(provider), backgroundScope)
        // Assert: o launch do init ainda não rodou (StandardTestDispatcher).
        assertEquals(DevicesState.Loading, repo.state.value)
        runCurrent()
        val content = assertIs<DevicesState.Content>(repo.state.value)
        assertEquals(5, content.devices.size)
        assertTrue(content.hasMore)
        assertEquals(DeviceQuery(OriginFilter.ALL, 1, 5), content.query)
        assertEquals(content.devices, repo.devices.value)
    }

    @Test
    fun loadMoreAccumulatesPagesAndStopsWhenPageIsShort() = runTest {
        // Arrange
        val provider = FakeProvider(seven)
        val repo = signedIn(provider)
        repo.loadFirstPage()
        // Act
        repo.loadMore()
        repo.loadMore() // segundo toque antes de a página chegar
        runCurrent()
        repo.loadMore() // página curta: não há mais o que pedir
        runCurrent()
        // Assert
        val content = assertIs<DevicesState.Content>(repo.state.value)
        assertEquals(seven.map { it.ns }, content.devices.map { it.ns })
        assertFalse(content.hasMore)
        assertEquals(2, content.pagesLoaded)
        assertEquals(listOf(1, 2), provider.queries.map { it.page })
    }

    @Test
    fun loadMoreErrorKeepsListAndExposesError() = runTest {
        // Arrange
        val provider = FakeProvider(seven, failWhen = { if (it.page == 2) GdiException(503, "Serviço indisponível") else null })
        val repo = signedIn(provider)
        repo.loadFirstPage()
        // Act
        repo.loadMore()
        runCurrent()
        // Assert
        val content = assertIs<DevicesState.Content>(repo.state.value)
        assertEquals(5, content.devices.size)
        assertFalse(content.loadingMore)
        assertTrue(content.hasMore, "o rodapé continua oferecendo nova tentativa")
        val error = assertIs<AppError.Server>(content.loadMoreError)
        assertEquals(503, error.httpStatus)
        assertEquals(5, repo.devices.value.size)
    }

    @Test
    fun emptyAndErrorStates() = runTest {
        // Arrange
        val emptyRepo = signedIn(FakeProvider(emptyList()))
        val offlineRepo = signedIn(FakeProvider(seven, failWhen = { IOException("Sem rede") }))
        // Act
        val empty = emptyRepo.loadFirstPage()
        val offline = offlineRepo.loadFirstPage()
        // Assert
        assertEquals(DevicesState.Empty(DeviceQuery()), empty)
        assertEquals(empty, emptyRepo.state.value)
        val error = assertIs<DevicesState.Error>(offline)
        assertIs<AppError.Network>(error.error)
        assertEquals(error, offlineRepo.state.value)
    }

    @Test
    fun originFilterIsSentAndAppliedDefensivelyOnDevice() = runTest {
        // Arrange: o parceiro falso ignora a origem, como uma API que não filtrou.
        val provider = FakeProvider(byOrigin)
        val repo = signedIn(provider)
        // Act
        repo.setOrigin(OriginFilter.SHARED)
        runCurrent()
        // Assert
        assertEquals(listOf(OriginFilter.SHARED), provider.queries.map { it.origin })
        val content = assertIs<DevicesState.Content>(repo.state.value)
        assertEquals(listOf("S1", "U1"), content.devices.map { it.ns }, "LINKED sai; origem desconhecida fica")
        assertEquals(OriginFilter.SHARED, repo.query.value.origin)
        assertNull(content.note)
    }

    @Test
    fun fallsBackToAllWhenApiRejectsOriginValue() = runTest {
        // Arrange: a API recusa qualquer valor de "origem" diferente de "todos".
        val provider = FakeProvider(
            byOrigin,
            failWhen = { if (it.origin != OriginFilter.ALL) GdiException(400, "Parâmetro inválido para 'origem'") else null },
        )
        val repo = signedIn(provider)
        // Act
        repo.setOrigin(OriginFilter.LINKED)
        runCurrent()
        // Assert
        assertEquals(listOf(OriginFilter.LINKED, OriginFilter.ALL), provider.queries.map { it.origin })
        val content = assertIs<DevicesState.Content>(repo.state.value)
        assertEquals(listOf("L1", "U1"), content.devices.map { it.ns })
        assertEquals(OriginFilter.LINKED, content.query.origin)
        assertEquals(DeviceRepositoryImpl.FILTER_ON_DEVICE_NOTE, content.note)
    }

    @Test
    fun setPageSizeRestartsFromPageOne() = runTest {
        // Arrange: duas páginas já carregadas.
        val provider = FakeProvider(seven)
        val repo = signedIn(provider)
        repo.loadFirstPage()
        repo.loadMore()
        runCurrent()
        // Act
        repo.setPageSize(2)
        runCurrent()
        // Assert
        val content = assertIs<DevicesState.Content>(repo.state.value)
        assertEquals(DeviceQuery(pageSize = 2), content.query)
        assertEquals(1, content.pagesLoaded)
        assertEquals(listOf("D1", "D2"), content.devices.map { it.ns })
        assertEquals(DeviceQuery(pageSize = 2), provider.queries.last())
        assertEquals(DeviceQuery(pageSize = 2), repo.query.value)
    }

    @Test
    fun refreshWithContentKeepsListVisibleWhileRefreshing() = runTest {
        // Arrange
        var slow = false // ligado só depois da 1.ª página
        val provider = FakeProvider(seven, delayFor = { if (slow) 1_000 else 0 })
        val repo = signedIn(provider)
        repo.loadFirstPage()
        slow = true
        // Act
        repo.refresh()
        runCurrent()
        // Assert: durante o refresh, só a barra fina; a lista continua na tela.
        val refreshing = assertIs<DevicesState.Content>(repo.state.value)
        assertTrue(refreshing.refreshing)
        assertEquals(5, refreshing.devices.size)
        assertEquals(5, repo.devices.value.size)
        advanceTimeBy(1_000)
        runCurrent()
        val refreshed = assertIs<DevicesState.Content>(repo.state.value)
        assertFalse(refreshed.refreshing)
        assertEquals(5, refreshed.devices.size)
    }

    @Test
    fun staleFirstPageIsNotPublishedAfterOriginChanged() = runTest {
        val mixed = listOf(device("L1", origin = DeviceOrigin.LINKED), device("S1", origin = DeviceOrigin.SHARED))
        // "todos" demora 1 s; "compartilhados" 0,5 s (tempo virtual).
        val provider = FakeProvider(mixed, delayFor = { if (it.origin == OriginFilter.ALL) 1_000 else 500 })
        val tokens = TokenRepositoryImpl(InMemoryTokenStorage())
        // backgroundScope: os launches do repositório compartilham o relógio virtual e são cancelados no fim do teste.
        val repo = DeviceRepositoryImpl(tokens, listOf(provider), backgroundScope)
        tokens.setSession("Ot_session_000001")
        val stale = async { repo.loadFirstPage() } // chamada direta, como o SubmitTokenUseCase faz
        runCurrent()
        repo.setOrigin(OriginFilter.SHARED)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(DevicesState.Loading, repo.state.value, "resposta antiga descartada")
        assertTrue(repo.devices.value.isEmpty())
        val staleResult = assertIs<DevicesState.Content>(stale.await())
        assertEquals(OriginFilter.ALL, staleResult.query.origin, "o chamador direto ainda recebe o resultado")
        // advanceUntilIdle para quando só restam coroutines do backgroundScope: avance o relógio explicitamente.
        advanceTimeBy(500)
        runCurrent()
        val content = assertIs<DevicesState.Content>(repo.state.value)
        assertEquals(OriginFilter.SHARED, content.query.origin)
        assertEquals(listOf("S1"), content.devices.map { it.ns })
    }

    @Test
    fun changingOriginCancelsLoadMoreInFlight() = runTest {
        // Arrange: página 2 lenta (10 s) já em voo.
        var slow = false // ligado só depois da 1.ª página
        val provider = FakeProvider(seven, delayFor = { if (slow) 10_000 else 0 })
        val repo = signedIn(provider)
        repo.loadFirstPage()
        slow = true
        repo.loadMore()
        runCurrent()
        // Act
        repo.setOrigin(OriginFilter.SHARED)
        runCurrent()
        // Assert: a consulta nova não esperou a página 2 terminar.
        assertEquals(listOf(1, 2, 1), provider.queries.map { it.page })
        assertFalse(assertIs<DevicesState.Content>(repo.state.value).loadingMore)
        advanceTimeBy(10_000)
        runCurrent()
        val content = assertIs<DevicesState.Content>(repo.state.value)
        assertEquals(OriginFilter.SHARED, content.query.origin)
        assertEquals(1, content.pagesLoaded)
        assertFalse(content.loadingMore)
    }

    @Test
    fun originChangeCancelsRefreshEvenWhenLoadMoreWasRequestedRightAfter() = runTest {
        var slow = false // ligado só depois da 1.ª página
        val provider = FakeProvider(seven, delayFor = { if (slow) 10_000 else 0 })
        val tokens = TokenRepositoryImpl(InMemoryTokenStorage())
        val repo = DeviceRepositoryImpl(tokens, listOf(provider), backgroundScope)
        tokens.setSession("Ot_session_000001")
        repo.loadFirstPage()
        slow = true
        // Sem rodar o dispatcher entre as chamadas: o refresh ainda não marcou refreshing = true.
        repo.refresh()
        repo.loadMore()
        repo.setOrigin(OriginFilter.SHARED)
        runCurrent()
        assertEquals(OriginFilter.SHARED, provider.queries.last().origin, "consulta nova não espera a antiga")
        assertEquals(listOf(1, 1), provider.queries.map { it.page }, "a página 2 do 'carregar mais' nunca foi pedida")
        advanceTimeBy(10_000)
        runCurrent()
        val content = assertIs<DevicesState.Content>(repo.state.value)
        assertFalse(content.loadingMore)
        assertFalse(content.refreshing)
    }

    @Test
    fun loadMoreDuringRefreshIsIgnored() = runTest {
        // Arrange: refresh lento (1 s) com a lista na tela.
        var slow = false // ligado só depois da 1.ª página
        val provider = FakeProvider(seven, delayFor = { if (slow) 1_000 else 0 })
        val repo = signedIn(provider)
        repo.loadFirstPage()
        slow = true
        repo.refresh()
        runCurrent()
        // Act
        repo.loadMore()
        runCurrent()
        // Assert
        assertFalse(assertIs<DevicesState.Content>(repo.state.value).loadingMore)
        advanceTimeBy(1_000)
        runCurrent()
        val content = assertIs<DevicesState.Content>(repo.state.value)
        assertEquals(1, content.pagesLoaded)
        assertEquals(listOf(1, 1), provider.queries.map { it.page }, "a página 2 nunca foi pedida")
    }

    @Test
    fun firstPageWhileLoadMoreInFlightClearsLoadingMoreAndDropsStalePage() = runTest {
        // Arrange: página 2 lenta (1 s) segurando o lock.
        var slow = false // ligado só depois da 1.ª página
        val provider = FakeProvider(seven, delayFor = { if (slow && it.page == 2) 1_000 else 0 })
        val repo = signedIn(provider)
        repo.loadFirstPage()
        slow = true
        repo.loadMore()
        runCurrent()
        // Act: chamada direta (como o SubmitTokenUseCase), que não cancela o "carregar mais".
        val firstPage = async { repo.loadFirstPage() }
        advanceTimeBy(1_000)
        runCurrent()
        // Assert
        val content = assertIs<DevicesState.Content>(firstPage.await())
        assertEquals(content, repo.state.value)
        assertFalse(content.loadingMore)
        assertFalse(content.refreshing)
        assertEquals(1, content.pagesLoaded)
        assertEquals(seven.take(5).map { it.ns }, content.devices.map { it.ns }, "a página 2 velha não foi publicada")
        assertEquals(content.devices, repo.devices.value)
        assertEquals(listOf(1, 2, 1), provider.queries.map { it.page })
    }

    @Test
    fun logoutDiscardsRefreshInFlight() = runTest {
        // Arrange: token restaurado do armazenamento; a carga de abertura demora 1 s.
        val storage = InMemoryTokenStorage("Ot_old_token_0001")
        val tokens = TokenRepositoryImpl(storage)
        val provider = FakeProvider(seven, delayFor = { 1_000 })
        val repo = DeviceRepositoryImpl(tokens, listOf(provider), backgroundScope)
        runCurrent()
        // Act
        repo.logout()
        advanceTimeBy(1_000)
        runCurrent()
        // Assert
        assertEquals(DevicesState.NoToken, repo.state.value)
        assertTrue(repo.devices.value.isEmpty())
        assertEquals(1, provider.queries.size)
        assertNull(tokens.token.value)
        assertNull(storage.value)
        assertNull(storage.writes.last(), "o storage termina apagado")
    }

    @Test
    fun lockOperationsDelegateToTheLockOwner() = runTest {
        // Arrange
        val locks = CountingLockController()
        val repo = signedIn(FakeProvider(emptyList(), locks = locks))
        val lock = device("LOCK1", kind = DeviceKind.LOCK)
        // Act + Assert: gravar não relê (reler é do ChangeLockVolumeUseCase).
        repo.setLockVolume(lock, LockVolume.LOW)
        assertEquals(LockVolume.LOW, locks.fake.volume)
        assertEquals(0, locks.volumeReads)
        // Erro de escrita sobe para o chamador.
        locks.fake.writeError = GdiException(500, "Erro interno")
        assertFailsWith<GdiException> { repo.setLockVolume(lock, LockVolume.HIGH) }
        assertEquals(LockVolume.LOW, locks.fake.volume)
        // A quantidade do histórico é repassada como veio.
        repo.lockHistory(lock, quantity = 10)
        repo.lockHistory(lock, quantity = 30)
        assertEquals(listOf(10, 30), locks.fake.historyRequests)
        // Parceiro sem fechaduras.
        val withoutLocks = signedIn(FakeProvider(emptyList()))
        assertFailsWith<IllegalStateException> { withoutLocks.lockDetails(lock) }
    }

    @Test
    fun startLivePassesLensChannelToProvider() = runTest {
        // Arrange: dois parceiros; cada câmera pertence a um.
        val fake = FakeProvider(emptyList())
        val other = FakeProvider(emptyList(), id = OTHER_PROVIDER)
        // Nome próprio no 2.º parceiro (o FakeProvider tem nome fixo): providerName precisa achar o parceiro certo.
        val otherNamed = object : DeviceProvider by other {
            override val displayName: String = OTHER_PROVIDER_NAME
        }
        val repo = signedIn(fake, otherNamed)
        val camera = device("CAM1")
        val otherCamera = device("CAM2", providerId = OTHER_PROVIDER)
        // Act
        repo.startLive(camera)
        repo.startLive(camera, channel = 1)
        repo.startLive(camera, channel = 0)
        val otherSession = repo.startLive(otherCamera, channel = 1)
        // Assert
        assertEquals(listOf(0, 1, 0), fake.liveChannels)
        assertEquals(listOf(1), other.liveChannels)
        assertEquals("rtsp://fake/CAM2", otherSession.streamUrl)
        assertEquals(fake.displayName, repo.providerName(camera))
        assertEquals(OTHER_PROVIDER_NAME, repo.providerName(otherCamera))
        assertEquals(listOf(fake.displayName, OTHER_PROVIDER_NAME), repo.providerNames)
        assertNull(repo.providerName(device("X1", providerId = "desconhecido")))
    }

    /** Conta as leituras de volume: FakeLockController é final e não registra leituras. */
    private class CountingLockController(val fake: FakeLockController = FakeLockController()) : LockController by fake {
        var volumeReads = 0
            private set

        override suspend fun volume(lock: Device): LockVolume? {
            volumeReads++
            return fake.volume(lock)
        }
    }

    private companion object {
        const val SESSION_TOKEN = "Ot_session_000001"
        const val OTHER_PROVIDER = "outro"
        const val OTHER_PROVIDER_NAME = "Outro parceiro"
    }
}
