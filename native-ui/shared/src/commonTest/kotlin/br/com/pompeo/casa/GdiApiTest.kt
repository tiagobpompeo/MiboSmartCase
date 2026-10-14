package br.com.pompeo.casa

import br.com.pompeo.casa.data.TokenRepositoryImpl
import br.com.pompeo.casa.data.gdi.GdiApi
import br.com.pompeo.casa.data.gdi.GdiDeviceProvider
import br.com.pompeo.casa.data.gdi.GdiErrorMapper
import br.com.pompeo.casa.data.gdi.GdiException
import br.com.pompeo.casa.data.gdi.GdiLockController
import br.com.pompeo.casa.data.gdi.GdiNoTokenException
import br.com.pompeo.casa.domain.AppError
import br.com.pompeo.casa.domain.model.DeviceKind
import br.com.pompeo.casa.domain.model.DeviceOrigin
import br.com.pompeo.casa.domain.model.DeviceQuery
import br.com.pompeo.casa.domain.model.LockEvent
import br.com.pompeo.casa.domain.model.LockVolume
import br.com.pompeo.casa.domain.model.OriginFilter
import br.com.pompeo.casa.domain.model.StreamSession
import br.com.pompeo.casa.domain.repository.TokenRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Cliente GDI testado com MockEngine: sem rede, roda na JVM e no simulador iOS. Tokens SEMPRE falsos. */
class GdiApiTest {
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private fun tokens(token: String?) = TokenRepositoryImpl(InMemoryTokenStorage(token))

    private fun api(tokens: TokenRepository, handler: suspend (HttpRequestData, Int) -> Pair<HttpStatusCode, String>): GdiApi {
        var calls = 0
        val engine = MockEngine { request ->
            calls++
            val (status, body) = handler(request, calls)
            respond(body, status, jsonHeaders)
        }
        // ContentNegotiation serializa o JsonObject do setBody em TextContent (por isso o cast abaixo).
        val client = HttpClient(engine) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        return GdiApi(tokens, baseUrl = "https://gdi.test", client = client)
    }

    private val HttpRequestData.bodyText: String get() = (body as io.ktor.http.content.TextContent).text
    // Asserções de corpo por substring (não dependem da ordem das chaves).

    // Fechadura Zigbee: ns composto esperado LOCK1_HUB1_PHUB; o idProduto da própria fechadura é PLOCK1.
    private val lock = device("LOCK1", kind = DeviceKind.LOCK).copy(subdevice = true, parentNs = "HUB1", parentProductId = "PHUB")
    private val camera = device("CAM1")

    /** Rotas de fechadura como a GDI real: com o ns puro (sem o composto) ela responde 404 "Dispositivo não encontrado". */
    private fun lockController(route: (path: String, body: String) -> Pair<HttpStatusCode, String>): GdiLockController =
        GdiLockController(
            api(tokens(TOKEN)) { request, _ ->
                val body = request.bodyText
                if (""""ns":"$LOCK_API_NS"""" in body) route(request.url.encodedPath, body)
                else HttpStatusCode.NotFound to DEVICE_NOT_FOUND
            },
        )

    @Test
    fun sendsBearerAndJsonBody() = runTest {
        // Arrange
        var seen: HttpRequestData? = null
        val gdi = api(tokens(TOKEN)) { request, _ ->
            seen = request
            HttpStatusCode.OK to EMPTY_LIST
        }
        // Act
        gdi.listDevices(page = 2, pageSize = 5)
        // Assert
        val request = assertNotNull(seen)
        val body = request.bodyText
        assertEquals(HttpMethod.Post, request.method)
        assertEquals(LISTING_PATH, request.url.encodedPath)
        assertEquals("Bearer $TOKEN", request.headers[HttpHeaders.Authorization])
        assertTrue(request.body.contentType?.match(ContentType.Application.Json) == true, "${request.body.contentType}")
        // Os três campos são obrigatórios: sem "origem" a GDI responde HTTP 500.
        assertTrue(""""pagina":2""" in body && """"tamanhoPagina":5""" in body && """"origem":"todos"""" in body, body)
    }

    @Test
    fun providerSendsOriginFilterAndParsesPage() = runTest {
        // Arrange
        var body = ""
        val provider = GdiDeviceProvider(
            api(tokens(TOKEN)) { request, _ ->
                body = request.bodyText
                HttpStatusCode.OK to SHARED_FULL_PAGE
            },
        )
        // Act
        val page = provider.listDevices(DeviceQuery(origin = OriginFilter.SHARED, page = 1, pageSize = 2))
        // Assert
        assertTrue(""""origem":"compartilhados"""" in body && """"pagina":1""" in body && """"tamanhoPagina":2""" in body, body)
        assertEquals(listOf("CAM1", "CAM2"), page.devices.map { it.ns })
        assertTrue(page.hasMore, "página cheia: pode haver mais")
        assertEquals("gdi", provider.id)
        assertTrue(page.devices.all { it.providerId == "gdi" && it.origin == DeviceOrigin.SHARED })
    }

    @Test
    fun renewsTokenOnce401AndRetries() = runTest {
        // Arrange
        val storage = InMemoryTokenStorage(OLD_TOKEN)
        val tokens = TokenRepositoryImpl(storage)
        val requests = mutableListOf<String>()
        var renewBody = ""
        val gdi = api(tokens) { request, _ ->
            val path = request.url.encodedPath
            val authorization = request.headers[HttpHeaders.Authorization]
            requests += "$path $authorization"
            when {
                path == RENEW_PATH -> {
                    renewBody = request.bodyText
                    HttpStatusCode.OK to RENEWED
                }
                authorization == "Bearer $OLD_TOKEN" -> HttpStatusCode.Unauthorized to UNAUTHORIZED_BODY
                else -> HttpStatusCode.OK to ONLINE_TRUE
            }
        }
        // Act
        val online = gdi.isOnline("CAM1")
        // Assert
        assertEquals(true, online)
        assertEquals(
            listOf("$ONLINE_PATH Bearer $OLD_TOKEN", "$RENEW_PATH Bearer $OLD_TOKEN", "$ONLINE_PATH Bearer $NEW_TOKEN"),
            requests,
            "uma renovação e uma única repetição",
        )
        // A GDI exige o token atual no header E no corpo da renovação.
        assertTrue(""""token":"$OLD_TOKEN"""" in renewBody, renewBody)
        assertEquals(NEW_TOKEN, tokens.token.value)
        assertEquals(listOf<String?>(NEW_TOKEN), storage.writes, "o token antigo deixa de valer: o novo é persistido")
    }

    @Test
    fun parallelExpiredCallsRenewTheTokenOnlyOnce() = runTest {
        val tokens = tokens("Ot_old_token_0001")
        // Contadores atômicos: o MockEngine pode atender em threads do pool de IO; commonTest não tem AtomicInteger.
        val renewCalls = MutableStateFlow(0)
        val oldTokenHits = MutableStateFlow(0)
        val bothSent = CompletableDeferred<Unit>()
        val engine = MockEngine { req ->
            when {
                req.url.encodedPath.endsWith("renovar-token/v1") -> {
                    renewCalls.update { it + 1 }
                    respond("""{"status":"sucesso","data":{"token":"Ot_new_token_0001"}}""", HttpStatusCode.OK, jsonHeaders)
                }
                req.headers[HttpHeaders.Authorization] == "Bearer Ot_old_token_0001" -> {
                    // Barreira: as duas chamadas saem com o token antigo antes de qualquer uma receber o 401.
                    if (oldTokenHits.updateAndGet { it + 1 } == 2) bothSent.complete(Unit)
                    bothSent.await()
                    respond("""{"status":"erro","msg":"Não autorizado"}""", HttpStatusCode.Unauthorized, jsonHeaders)
                }
                else -> respond("""{"status":"sucesso","data":{"online":true}}""", HttpStatusCode.OK, jsonHeaders)
            }
        }
        val client = HttpClient(engine) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        val api = GdiApi(tokens, baseUrl = "https://gdi.test", client = client)
        val results = listOf(async { api.isOnline("NS1") }, async { api.isOnline("NS2") }).awaitAll()
        assertEquals(listOf<Boolean?>(true, true), results, "as duas chamadas repetidas com o token novo")
        assertEquals(2, oldTokenHits.value)
        assertEquals(1, renewCalls.value, "exatamente uma renovação para dois 401 paralelos")
        assertEquals("Ot_new_token_0001", tokens.token.value)
    }

    @Test
    fun failedRenewalBecomesTokenInvalid() = runTest {
        // Arrange: o 401 dispara a renovação, que a GDI recusa com o 400 real.
        val tokens = tokens(OLD_TOKEN)
        val gdi = api(tokens) { request, _ ->
            if (request.url.encodedPath == RENEW_PATH) HttpStatusCode.BadRequest to RENEW_REFUSED
            else HttpStatusCode.Unauthorized to UNAUTHORIZED_BODY
        }
        // Act
        val error = assertFailsWith<GdiException> { gdi.isOnline("CAM1") }
        // Assert
        assertEquals(401, error.httpStatus, "mantém o status original, não o 400 da renovação")
        assertIs<AppError.TokenInvalid>(GdiErrorMapper.toAppError(error))
        assertEquals(OLD_TOKEN, tokens.token.value, "renovação recusada não troca o token")
    }

    @Test
    fun parsesStreamSessionAndSurfacesApiErrors() = runTest {
        // Arrange: 1.ª chamada com a forma do Swagger; 2.ª com HTTP 402.
        val gdi = api(tokens(TOKEN)) { _, call ->
            if (call == 1) HttpStatusCode.OK to SWAGGER_STREAM
            else HttpStatusCode.PaymentRequired to QUOTA_EXCEEDED
        }
        // Act
        val session = gdi.createVideoStream("CAM1")
        val error = assertFailsWith<GdiException> { gdi.createVideoStream("CAM1") }
        // Assert
        assertEquals(
            StreamSession(
                sessionId = "session-0001",
                streamUrl = "https://stream.gdi.test/CAM1.mp4",
                monitorUrl = "https://stream.gdi.test/monitor/CAM1",
                quotaGb = 4.5,
            ),
            session,
        )
        assertEquals(402, error.httpStatus)
        assertEquals("Cota de vídeo esgotada", error.message)
    }

    @Test
    fun readsLockStateAndSendsControlCommand() = runTest {
        // Arrange
        var commandBody = ""
        val locks = lockController { path, body ->
            when (path) {
                LOCK_OPEN_PATH -> HttpStatusCode.OK to """{"status":"sucesso","data":{"aberto":false}}"""
                LOCK_REMOTE_PATH -> HttpStatusCode.OK to """{"status":"sucesso","data":{"habilitado":true}}"""
                BATTERY_PATH -> HttpStatusCode.OK to """{"status":"sucesso","data":{"bateria":26}}"""
                HISTORY_PATH -> HttpStatusCode.OK to REAL_HISTORY
                CONTROL_PATH -> {
                    commandBody = body
                    HttpStatusCode.OK to COMMAND_OK
                }
                else -> HttpStatusCode.OK to COMMAND_OK
            }
        }
        // Act
        val details = locks.details(lock)
        locks.setOpen(lock, open = true)
        // Assert
        assertEquals(false, details.open)
        assertEquals(true, details.remoteEnabled)
        assertEquals(26, details.battery)
        assertEquals(
            listOf(LockEvent("02/10/2026 16:29:32", "Remoto (APP)"), LockEvent("02/10/2026 15:10:00", "Por dentro (manual)")),
            details.history,
        )
        assertNull(details.historyError)
        assertTrue(
            """"ns":"$LOCK_API_NS"""" in commandBody && """"aberto":true""" in commandBody && """"idProduto":"PLOCK1"""" in commandBody,
            commandBody,
        )
    }

    @Test
    fun readsAndWritesLockVolume() = runTest {
        // Arrange
        var writeBody = ""
        val locks = lockController { path, body ->
            when (path) {
                VOLUME_PATH -> HttpStatusCode.OK to """{"status":"sucesso","data":{"volume":2}}"""
                SET_VOLUME_PATH -> {
                    writeBody = body
                    HttpStatusCode.OK to COMMAND_OK
                }
                else -> HttpStatusCode.NotFound to DEVICE_NOT_FOUND
            }
        }
        // Act
        val volume = locks.volume(lock)
        locks.setVolume(lock, LockVolume.LOW)
        // Assert
        assertEquals(LockVolume.MEDIUM, volume)
        assertTrue(
            """"ns":"LOCK1_HUB1_PHUB"""" in writeBody && """"idProduto":"PLOCK1"""" in writeBody && """"volume":1""" in writeBody,
            writeBody,
        )
    }

    @Test
    fun volumeServerErrorBecomesNullWithExplanationInDetails() = runTest {
        // Arrange: leitura de volume com o HTTP 500 real e histórico vazio.
        val locks = lockController { path, _ ->
            when (path) {
                VOLUME_PATH -> HttpStatusCode.InternalServerError to VOLUME_UNKNOWN_ERROR
                HISTORY_PATH -> HttpStatusCode.OK to EMPTY_LIST
                else -> HttpStatusCode.OK to COMMAND_OK
            }
        }
        // Act
        val details = locks.details(lock)
        // Assert
        assertNull(details.volume)
        assertEquals("a API respondeu HTTP 500", details.volumeError)
        assertTrue(details.history.isEmpty())
        assertNull(details.historyError, "histórico vazio não é erro")
    }

    @Test
    fun lockCommandsDoNotRequireDataField() = runTest {
        // Arrange: os dois primeiros comandos recebem só {"status":"sucesso"}; o terceiro, HTTP 200 com envelope de erro.
        var commands = 0
        val locks = lockController { _, _ ->
            commands++
            if (commands <= 2) HttpStatusCode.OK to COMMAND_OK
            else HttpStatusCode.OK to """{"status":"erro","msg":"Fechadura não respondeu"}"""
        }
        // Act: os dois primeiros não podem lançar.
        locks.setOpen(lock, open = false)
        locks.setVolume(lock, LockVolume.HIGH)
        val error = assertFailsWith<GdiException> { locks.setOpen(lock, open = true) }
        // Assert
        val appError = assertIs<AppError.Server>(GdiErrorMapper.toAppError(error))
        assertEquals(200, appError.httpStatus)
    }

    @Test
    fun historyRequestsThirtyItemsOnVerMais() = runTest {
        // Arrange
        var historyBody = ""
        val locks = lockController { path, body ->
            if (path == HISTORY_PATH) historyBody = body
            HttpStatusCode.OK to REAL_HISTORY
        }
        // Act
        val events = locks.history(lock, VER_MAIS_COUNT)
        // Assert
        assertTrue(""""quantidade":30""" in historyBody && """"ns":"$LOCK_API_NS"""" in historyBody, historyBody)
        assertFalse("idProduto" in historyBody, "o histórico vai sem idProduto (contrato real)")
        assertEquals(2, events.size)
    }

    @Test
    fun historyServerErrorIsReportedSeparatelyFromEmpty() = runTest {
        // Arrange
        val locks = lockController { path, _ ->
            when (path) {
                HISTORY_PATH -> HttpStatusCode.ServiceUnavailable to """{"message":"Service Unavailable"}"""
                VOLUME_PATH -> HttpStatusCode.OK to """{"status":"sucesso","data":{"volume":0}}"""
                else -> HttpStatusCode.OK to COMMAND_OK
            }
        }
        // Act
        val details = locks.details(lock)
        // Assert
        assertEquals(AppError.Server(503, detail = null).userMessage, details.historyError)
        assertTrue(details.history.isEmpty())
        assertEquals(LockVolume.MUTE, details.volume, "volume 0 é mudo, não ausência de volume")
        assertNull(details.volumeError)
    }

    @Test
    fun streamChannelIsSentAsCanalVideo() = runTest {
        // Arrange
        val bodies = mutableListOf<String>()
        val provider = GdiDeviceProvider(
            api(tokens(TOKEN)) { request, _ ->
                bodies += request.bodyText
                HttpStatusCode.OK to REAL_RTSP_STREAM
            },
        )
        // Act
        provider.startLive(camera)
        provider.startLive(camera, channel = 1)
        // Assert
        assertEquals(2, bodies.size)
        assertTrue(""""canalVideo":0""" in bodies[0], bodies[0])
        assertTrue(""""canalVideo":1""" in bodies[1], bodies[1])
        // streamId 0 com canal 0 dá HTTP 500 na GDI; e o vídeo usa o ns puro.
        assertTrue(bodies.all { """"streamId":1""" in it && """"ns":"CAM1"""" in it }, bodies.toString())
    }

    @Test
    fun onlineUsesCompositeNsOnlyForSubdevices() = runTest {
        // Arrange: como a GDI real, o ns puro da fechadura Zigbee responde online=false.
        val bodies = mutableListOf<String>()
        val provider = GdiDeviceProvider(
            api(tokens(TOKEN)) { request, _ ->
                val body = request.bodyText
                bodies += body
                HttpStatusCode.OK to if (""""ns":"LOCK1"""" in body) ONLINE_FALSE else ONLINE_TRUE
            },
        )
        // Act
        val lockOnline = provider.isOnline(lock)
        val cameraOnline = provider.isOnline(camera)
        // Assert
        assertEquals(true, lockOnline, "subdispositivo consulta com o ns composto")
        assertEquals(true, cameraOnline)
        assertTrue(""""ns":"$LOCK_API_NS"""" in bodies[0], bodies[0])
        assertTrue(""""ns":"CAM1"""" in bodies[1], bodies[1])
    }

    @Test
    fun acceptsRealRtspOnlyStreamResponse() = runTest {
        // Arrange
        var requests = 0
        val gdi = api(tokens(TOKEN)) { _, _ ->
            requests++
            HttpStatusCode.OK to REAL_RTSP_STREAM
        }
        // Act
        val session = gdi.createVideoStream("CAM1")
        gdi.endSession(session.sessionId)
        // Assert
        assertEquals(REAL_RTSP_URL, session.streamUrl)
        assertNull(session.sessionId)
        assertNull(session.monitorUrl)
        assertNull(session.quotaGb)
        assertEquals(1, requests, "sem session_id não há sessão a encerrar: a URL expira sozinha")
    }

    @Test
    fun parsesJsonServedAsTextPlain() = runTest {
        // Arrange: a GDI serve o JSON com content-type text/plain, que o ContentNegotiation não converte.
        val engine = MockEngine { respond(SHARED_FULL_PAGE, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Text.Plain.toString())) }
        val client = HttpClient(engine) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        val provider = GdiDeviceProvider(GdiApi(tokens(TOKEN), baseUrl = "https://gdi.test", client = client))
        // Act
        val page = provider.listDevices(DeviceQuery())
        // Assert
        assertEquals(listOf("CAM1", "CAM2"), page.devices.map { it.ns })
        assertEquals(listOf<Boolean?>(true, false), page.devices.map { it.online })
    }

    @Test
    fun failsFastWithoutToken() = runTest {
        // Arrange
        var requests = 0
        val gdi = api(tokens(null)) { _, _ ->
            requests++
            HttpStatusCode.OK to EMPTY_LIST
        }
        // Act
        val error = assertFailsWith<GdiNoTokenException> { gdi.listDevices() }
        // Assert
        assertEquals(AppError.TokenMissing, GdiErrorMapper.toAppError(error))
        assertEquals(0, requests, "sem token nenhuma requisição sai")
    }

    @Test
    fun unauthorizedStringBodyBecomesTokenInvalid() = runTest {
        // Arrange: o 401 real tem corpo string JSON, inclusive na tentativa de renovação.
        val gdi = api(tokens(OLD_TOKEN)) { _, _ -> HttpStatusCode.Unauthorized to UNAUTHORIZED_BODY }
        // Act
        val error = assertFailsWith<GdiException> { gdi.isOnline("CAM1") }
        // Assert
        val appError = assertIs<AppError.TokenInvalid>(GdiErrorMapper.toAppError(error))
        assertEquals("HTTP 401: Não autorizado", appError.detail, "texto sem as aspas da string JSON")
    }

    @Test
    fun expiredTokenBecomesTokenExpired() = runTest {
        // Arrange: toda rota, inclusive a renovação, responde o 403 real de token expirado.
        val gdi = api(tokens(OLD_TOKEN)) { _, _ -> HttpStatusCode.Forbidden to TOKEN_EXPIRED }
        // Act
        val error = assertFailsWith<GdiException> { gdi.listDevices() }
        // Assert
        val appError = assertIs<AppError.TokenExpired>(GdiErrorMapper.toAppError(error))
        assertEquals(403, error.httpStatus)
        assertEquals(
            "Seu token expirou (ele vale cerca de 2 horas). Gere um novo no portal (Contas → Token Temporário) e tente de novo.",
            appError.userMessage,
        )
        assertEquals("HTTP 403: Token expirado, por favor gere um novo token", appError.detail)
    }

    private companion object {
        const val TOKEN = "Ot_abc_test_0001"
        const val OLD_TOKEN = "Ot_old_token_0001"
        const val NEW_TOKEN = "Ot_new_token_0001"
        const val LOCK_API_NS = "LOCK1_HUB1_PHUB"
        /** Quantidade pedida em "Ver mais" do histórico (RF09). */
        const val VER_MAIS_COUNT = 30

        const val LISTING_PATH = "/produtos/listar-dispositivos/v1"
        const val ONLINE_PATH = "/produtos/online/v1"
        const val BATTERY_PATH = "/produtos/bateria/v1"
        const val RENEW_PATH = "/autenticacao/renovar-token/v1"
        const val LOCK_OPEN_PATH = "/fechaduras/status-abertura/v1"
        const val LOCK_REMOTE_PATH = "/fechaduras/status-abrir-remoto/v1"
        const val HISTORY_PATH = "/fechaduras/historico-abertura/v1"
        const val CONTROL_PATH = "/fechaduras/controle-fechadura/v1"
        const val VOLUME_PATH = "/fechaduras/volume/v1"
        const val SET_VOLUME_PATH = "/fechaduras/mudar-volume/v1"

        // Corpos com a forma medida na API real (ns e URLs fictícios).
        const val UNAUTHORIZED_BODY = "\"Não autorizado\""
        const val DEVICE_NOT_FOUND = "\"Dispositivo não encontrado\""
        const val TOKEN_EXPIRED = """{"status":"erro","msg":"Token expirado, por favor gere um novo token"}"""
        const val RENEW_REFUSED = """{"status":"erro","msg":"Não foi possível renovar o token, por favor gere um novo"}"""
        const val RENEWED = """{"status":"sucesso","data":{"token":"$NEW_TOKEN"}}"""
        const val ONLINE_TRUE = """{"status":"sucesso","data":{"online":true}}"""
        const val ONLINE_FALSE = """{"status":"sucesso","data":{"online":false}}"""
        const val EMPTY_LIST = """{"status":"sucesso","data":[]}"""
        const val COMMAND_OK = """{"status":"sucesso"}"""
        const val VOLUME_UNKNOWN_ERROR = """{"msg":"Erro desconhecido, por favor tente novamente mais tarde"}"""
        const val QUOTA_EXCEEDED = """{"status":"erro","msg":"Cota de vídeo esgotada"}"""
        const val SHARED_FULL_PAGE =
            """{"status":"sucesso","data":[""" +
                """{"ns":"CAM1","modelo":"iM5","nome":"Garagem","status":"online","origem":"compartilhado"},""" +
                """{"ns":"CAM2","modelo":"iM4 Dual","nome":"Sala","status":"offline","origem":"compartilhado"}]}"""
        const val REAL_HISTORY =
            """{"status":"sucesso","data":[""" +
                """{"tempoLocal":"20261002T162932","nome":"APP","tipo":"usuarioRemoto"},""" +
                """{"tempoLocal":"20261002T151000","nome":"","tipo":"interno"}]}"""
        const val REAL_RTSP_URL = "rtsp://liveopenrtspproxy.imoulife.test:8554/live/CAM1?expire=1790000000"
        const val REAL_RTSP_STREAM = """{"data":{"url":"$REAL_RTSP_URL"}}"""
        const val SWAGGER_STREAM =
            """{"status":"sucesso","data":{"url":"https://stream.gdi.test/CAM1.mp4",""" +
                """"monitor_url":"https://stream.gdi.test/monitor/CAM1","session_id":"session-0001","quota_gb":4.5}}"""
    }
}
