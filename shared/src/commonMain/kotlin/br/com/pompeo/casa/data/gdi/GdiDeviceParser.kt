package br.com.pompeo.casa.data.gdi

import br.com.pompeo.casa.domain.model.Device
import br.com.pompeo.casa.domain.model.DeviceKind
import br.com.pompeo.casa.domain.model.DeviceOrigin
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

/**
 * O Swagger não documenta o corpo de /produtos/listar-dispositivos/v1: o parser aceita variações de
 * envelope e de nomes de campo e devolve [Device] com providerId = "gdi".
 */
object GdiDeviceParser {
    const val PROVIDER_ID = "gdi"

    private val nsKeys = listOf("ns", "numeroSerie", "serial", "serialNumber", "deviceSn")
    private val nameKeys = listOf("nome", "name", "deviceName", "nomeDispositivo", "apelido")
    private val productKeys = listOf("idProduto", "productId", "pid")
    private val modelKeys = listOf("modelo", "model", "productName", "nomeProduto")
    private val typeKeys = listOf("tipo", "type", "categoria", "category", "deviceType", "tipoDispositivo")
    private val onlineKeys = listOf("online", "isOnline", "status")
    private val originKeys = listOf("origem", "origin")

    fun parse(root: JsonElement): List<Device> = collectObjects(root).mapNotNull { it.toDevice() }.distinctBy { it.ns }

    /**
     * Tipo pelo modelo ou nome (a GDI não devolve categoria). O hub MCA 1002 tem modelo "IOT-ZG2-IB". A ordem importa.
     * Medido na conta de teste: IM (câmera), MFR (fechadura), MCA/IOT-ZG (hub). Hipótese, sem exemplar na conta:
     * MFV/MFD (fechadura), MSM/MSA/MTU (sensor), MLS/ELW (lâmpada).
     */
    internal fun inferKind(type: String?, model: String?, name: String?): DeviceKind {
        val t = type?.lowercase().orEmpty()
        val tags = listOfNotNull(model, name).map { it.trim().uppercase() }
        fun any(vararg prefixes: String) = tags.any { tag -> prefixes.any { tag.startsWith(it) } }
        return when {
            "cam" in t || any("IM") -> DeviceKind.CAMERA
            "fechadura" in t || "lock" in t || any("MFR", "MFV", "MFD") -> DeviceKind.LOCK
            "hub" in t || any("MCA", "IOT-ZG") -> DeviceKind.HUB
            "sensor" in t || any("MSM", "MSA", "MTU") -> DeviceKind.SENSOR
            "lamp" in t || any("MLS", "ELW") -> DeviceKind.LAMP
            type == null && model == null -> DeviceKind.CAMERA // sem pista nenhuma: a linha original da GDI era só câmeras
            else -> DeviceKind.OTHER
        }
    }

    private fun JsonObject.toDevice(): Device? {
        val ns = first(nsKeys) ?: return null
        val model = first(modelKeys)
        val type = first(typeKeys)
        val name = first(nameKeys) ?: model ?: ns
        return Device(
            providerId = PROVIDER_ID,
            ns = ns,
            name = name,
            kind = inferKind(type, model, name),
            model = model,
            productId = first(productKeys),
            // "online": true literal, ou "status": "online"/"offline", ou "1".
            online = onlineKeys.firstNotNullOfOrNull { key -> bool(key) ?: (this[key] as? JsonPrimitive)?.contentOrNull?.let { it == "1" || it.equals("online", true) } },
            origin = DeviceOrigin.parse(first(originKeys)),
            subdevice = bool("subdispositivo") ?: false,
            parentNs = first(listOf("dispositivoPai")),
            parentProductId = first(listOf("idProdutoDispositivoPai")),
            version = first(listOf("versao", "version")),
            lastOnline = first(listOf("ultimaVezOnline"))?.let(GdiFormat::dateTime),
            updateAvailable = bool("atualizacaoDisponivel"),
        )
    }

    private fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull
    private fun JsonObject.first(keys: List<String>): String? =
        keys.firstNotNullOfOrNull { key -> (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } }

    /** Todos os objetos que parecem dispositivos (têm campo de número de série), em qualquer nível; não desce dentro deles. */
    private fun collectObjects(element: JsonElement): List<JsonObject> =
        when (element) {
            is JsonObject -> if (nsKeys.any { element.containsKey(it) }) listOf(element) else element.values.flatMap { collectObjects(it) }
            is JsonArray -> element.flatMap { collectObjects(it) }
            else -> emptyList()
        }
}
