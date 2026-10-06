package br.com.pompeo.casa.data.gdi

import br.com.pompeo.casa.domain.model.Firmware
import br.com.pompeo.casa.domain.model.LockEvent
import br.com.pompeo.casa.domain.model.StreamSession
import br.com.pompeo.casa.domain.repository.TokenRepository
import br.com.pompeo.casa.domain.resultOf
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Erro devolvido pela GDI (HTTP fora de 2xx ou envelope `{"status":"erro"}`). */
open class GdiException(val httpStatus: Int, message: String) : Exception(message)

/** Chamada sem token configurado: a UI leva para a tela de token em vez de "token inválido". */
class GdiNoTokenException : GdiException(401, "Nenhum token GDI configurado")

/**
 * Cliente da API GDI. Todas as rotas são POST + JSON com `Authorization: Bearer <token>`.
 * Kotlin puro + Ktor: o mesmo código roda no Android (OkHttp) e no iOS (Darwin).
 * [client] é injetável para os testes usarem MockEngine.
 */
class GdiApi(
    private val tokens: TokenRepository,
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val client: HttpClient = defaultClient(),
) {
    // Uma renovação por vez: as 5 leituras paralelas da fechadura não podem renovar o mesmo token 5 vezes.
    private val renewLock = Mutex()

    suspend fun listDevices(page: Int = 1, pageSize: Int = 50, origin: String = "todos"): JsonObject =
        post("/produtos/listar-dispositivos/v1") {
            // Os três campos são obrigatórios: sem "origem" a API devolve HTTP 500.
            put("tamanhoPagina", pageSize)
            put("pagina", page)
            put("origem", origin)
        }

    suspend fun isOnline(ns: String): Boolean? = post("/produtos/online/v1") { put("ns", ns) }.findBoolean("online")

    /** Abre uma sessão ao vivo. [streamGb] limita a banda cobrada da cota. streamId 0 + canal 0 dá HTTP 500. */
    suspend fun createVideoStream(ns: String, streamGb: Double = 0.2, channel: Int = 0, streamId: Int = 1): StreamSession {
        val json = post("/cameras/criar-fluxo-video/v1") {
            put("ns", ns)
            put("stream_gb", streamGb)
            put("canalVideo", channel)
            put("streamId", streamId)
        }
        val data = json["data"]?.jsonObject ?: throw GdiException(200, json.message() ?: "Resposta sem data")
        return StreamSession(
            sessionId = data.string("session_id"),
            streamUrl = data.string("url") ?: throw GdiException(200, "Sessão sem URL de vídeo"),
            monitorUrl = data.string("monitor_url"),
            quotaGb = data["quota_gb"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull(),
        )
    }

    /** Sem session_id (resposta RTSP real) não há o que encerrar: a URL expira sozinha (parâmetro expire). */
    suspend fun endSession(sessionId: String?) {
        if (sessionId == null) return
        resultOf { post("/streaming/encerrar-sessao/v1") { put("session_id", sessionId) } } // falha ao encerrar não é erro de tela
    }

    // ---------- Fechadura (subdispositivo Zigbee: ns = NsFechadura_NsHub_IdProdutoHub) ----------

    suspend fun lockIsOpen(ns: String, productId: String): Boolean? =
        post("/fechaduras/status-abertura/v1") { put("ns", ns); put("idProduto", productId) }.findBoolean("aberto")

    suspend fun lockRemoteOpenEnabled(ns: String, productId: String): Boolean? =
        post("/fechaduras/status-abrir-remoto/v1") { put("ns", ns); put("idProduto", productId) }.findBoolean("habilitado")

    suspend fun battery(ns: String, productId: String): Int? =
        post("/produtos/bateria/v1") { put("ns", ns); put("idProduto", productId) }.findString("bateria")?.toIntOrNull()

    /** Sem idProduto no corpo (contrato real). */
    suspend fun lockHistory(ns: String, count: Int = 10): List<LockEvent> {
        val data = post("/fechaduras/historico-abertura/v1") { put("ns", ns); put("quantidade", count) }["data"]
        return (data as? JsonArray).orEmpty().mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            // Formatado aqui: só data/gdi conhece o formato cru da GDI; a UI recebe o texto pronto.
            LockEvent(
                time = GdiFormat.dateTime(obj.string("tempoLocal")),
                description = GdiFormat.lockEventType(obj.string("tipo").orEmpty(), obj.string("nome").orEmpty()),
            )
        }
    }

    /** Aciona a fechadura REAL. [open] = true destranca; false tranca. A UI só chama após confirmação. */
    suspend fun controlLock(ns: String, productId: String, open: Boolean) {
        post("/fechaduras/controle-fechadura/v1") { put("ns", ns); put("aberto", open); put("idProduto", productId) }
    }

    /** Volume atual (0 = mudo … 3 = alto). Fato: devolveu HTTP 500 na fechadura de teste. */
    suspend fun lockVolume(ns: String, productId: String): Int? =
        post("/fechaduras/volume/v1") { put("ns", ns); put("idProduto", productId) }.findString("volume")?.toIntOrNull()

    suspend fun setLockVolume(ns: String, productId: String, level: Int) {
        post("/fechaduras/mudar-volume/v1") { put("ns", ns); put("idProduto", productId); put("volume", level) }
    }

    suspend fun firmware(ns: String): Firmware {
        val json = post("/produtos/versao/v1") { put("ns", ns) }
        return Firmware(json.findString("versao"), json.findBoolean("atualizacaoDisponivel"))
    }

    /** Troca o token atual por um novo e o persiste: o antigo deixa de valer. */
    suspend fun renewToken(): String = renewLock.withLock { renewLocked() }

    /**
     * Serializa a renovação. Quem entra depois e encontra um token diferente do que falhou só repete a
     * chamada com ele: outra requisição paralela já renovou, e renovar de novo um token morto é recusado.
     */
    private suspend fun renewedTokenAfter(failed: String): String =
        renewLock.withLock {
            val latest = tokens.token.value ?: throw GdiNoTokenException()
            if (latest != failed) latest else renewLocked()
        }

    /** Só com [renewLock] já adquirido (o Mutex do kotlinx não é reentrante). */
    private suspend fun renewLocked(): String {
        val current = tokens.token.value ?: throw GdiNoTokenException()
        val response: HttpResponse = client.post("$baseUrl/autenticacao/renovar-token/v1") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $current")
            setBody(buildJsonObject { put("token", current) }) // a API exige o token no header E no corpo
        }
        val json = response.parseJson()
        val renewed = json.findString("token") ?: throw GdiException(response.status.value, json.message() ?: "Falha ao renovar token")
        tokens.persist(renewed)
        return renewed
    }

    private suspend fun post(path: String, body: JsonObjectBuilder.() -> Unit): JsonObject {
        // Corpo montado à mão: todos os campos vão explícitos (DTO com encodeDefaults=false mandaria "{}" e daria 500).
        val payload = buildJsonObject(body)
        val sentWith = tokens.token.value ?: throw GdiNoTokenException()
        val first = send(path, payload, sentWith)
        // 401 (inválido) ou 403 (expirado): renova uma vez (ou reaproveita a renovação de uma chamada paralela) e repete.
        if (first.status.value == 401 || first.status.value == 403) {
            val renewed =
                try {
                    renewedTokenAfter(failed = sentWith)
                } catch (noToken: GdiNoTokenException) {
                    // Subclasse de GdiException: precisa vir antes. Logout durante a chamada = "sem token".
                    throw noToken
                } catch (renewError: GdiException) {
                    // Mantém o status ORIGINAL (401 => TokenInvalid, 403 => TokenExpired); o motivo da renovação vai na mensagem.
                    // Falha de rede na renovação não é capturada: sobe como está e vira Network/Timeout.
                    throw GdiException(first.status.value, renewError.message ?: first.parseJson().message() ?: "Token inválido ou expirado")
                }
            return send(path, payload, renewed).successJson() // repete uma única vez
        }
        return first.successJson()
    }

    private suspend fun send(path: String, payload: JsonObject, token: String): HttpResponse =
        client.post("$baseUrl$path") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(payload)
        }

    /** Sucesso = HTTP 2xx e envelope sem "status":"erro". NÃO exige "data": comandos respondem só {"status":"sucesso"}. */
    private suspend fun HttpResponse.successJson(): JsonObject {
        val json = parseJson()
        if (status.value !in 200..299 || json.string("status") == "erro")
            throw GdiException(status.value, json.message() ?: "HTTP ${status.value}")
        return json
    }

    // A GDI responde JSON com "content-type: text/plain"; por isso lemos o texto e convertemos
    // manualmente em vez de depender do ContentNegotiation. Corpo que não é objeto vira {"raw": texto}:
    // o 401 real é a string JSON "Não autorizado", guardada sem as aspas para o detalhe ficar legível.
    private suspend fun HttpResponse.parseJson(): JsonObject {
        val text = bodyAsText()
        val element = runCatching { lenientJson.parseToJsonElement(text) }.getOrNull()
        return element as? JsonObject
            ?: buildJsonObject { put("raw", ((element as? JsonPrimitive)?.contentOrNull ?: text).take(500)) }
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api-casainteligente.intelbras.com.br"
        private val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }

        // ContentNegotiation fica instalado só para SERIALIZAR o pedido (setBody(JsonObject)).
        // Sem plugin de Logging: ele imprimiria o header Authorization (token) no logcat/console.
        fun defaultClient(): HttpClient = HttpClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; isLenient = true }) }
            install(HttpTimeout) { requestTimeoutMillis = 20_000; connectTimeoutMillis = 10_000 }
        }

        internal fun JsonObject.message(): String? = string("msg") ?: string("message") ?: string("erro") ?: string("raw")
        internal fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

        /** Procura uma chave em qualquer profundidade (as respostas da GDI variam de envelope). */
        internal fun JsonElement.findString(key: String): String? = find(key)?.let { (it as? JsonPrimitive)?.contentOrNull }
        internal fun JsonElement.findBoolean(key: String): Boolean? =
            (find(key) as? JsonPrimitive)?.contentOrNull?.let { it == "true" || it == "1" }

        private fun JsonElement.find(key: String): JsonElement? {
            if (this is JsonObject) {
                this[key]?.let { return it }
                for (value in values) value.find(key)?.let { return it }
            }
            if (this is JsonArray) for (value in this) value.find(key)?.let { return it }
            return null
        }
    }
}
