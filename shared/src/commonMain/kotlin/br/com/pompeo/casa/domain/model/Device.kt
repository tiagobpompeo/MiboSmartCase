package br.com.pompeo.casa.domain.model

/** Linhas Mibo suportadas pela GDI (câmeras iM, fechaduras, hubs Zigbee, sensores, lâmpadas). */
enum class DeviceKind(val label: String) {
    CAMERA("Câmera"), LOCK("Fechadura"), HUB("Hub"), SENSOR("Sensor"), LAMP("Lâmpada"), OTHER("Dispositivo"),
}

/** Origem do vínculo com a conta, como a GDI devolve por dispositivo ("vinculado" / "compartilhado"). */
enum class DeviceOrigin(val label: String) {
    LINKED("Vinculado"), SHARED("Compartilhado"), UNKNOWN("—");

    companion object {
        // Singular no item da listagem, plural no filtro do pedido: aceita os dois.
        fun parse(raw: String?): DeviceOrigin =
            when (raw?.trim()?.lowercase()) {
                "vinculado", "vinculados" -> LINKED
                "compartilhado", "compartilhados" -> SHARED
                else -> UNKNOWN
            }
    }
}

/** Dispositivo de qualquer parceiro. [providerId] identifica o parceiro ("gdi"). */
data class Device(
    val providerId: String,
    /** Identificador no parceiro (número de série na GDI). */
    val ns: String,
    val name: String,
    val kind: DeviceKind,
    val model: String?,
    val productId: String?,
    /** Três estados: null = a API não informou (não é "offline"). */
    val online: Boolean?,
    val origin: DeviceOrigin,
    val subdevice: Boolean = false,
    val parentNs: String? = null,
    val parentProductId: String? = null,
    val version: String? = null,
    /** Último contato já formatado pelo parceiro ("dd/MM/aaaa HH:mm:ss", " UTC" quando a origem marca). */
    val lastOnline: String? = null,
    val updateAvailable: Boolean? = null,
) {
    val isCamera: Boolean get() = kind == DeviceKind.CAMERA
    val isLock: Boolean get() = kind == DeviceKind.LOCK
}

/**
 * Sessão ao vivo. O Swagger documenta url (MP4 por HTTPS), monitor_url e session_id, mas a API real
 * devolve só `url` = rtsp:// de um proxy da nuvem; por isso os demais campos são opcionais.
 */
data class StreamSession(val sessionId: String?, val streamUrl: String, val monitorUrl: String?, val quotaGb: Double?)

/**
 * Um registro do histórico de aberturas (RF09), já pronto para exibir: o parceiro converte o formato cru dele
 * ([time] = "dd/MM/aaaa HH:mm:ss"; [description] = "Remoto (APP)", "Por dentro (manual)"…), e a UI não conhece a GDI.
 */
data class LockEvent(val time: String, val description: String)

data class Firmware(val version: String?, val updateAvailable: Boolean?)

/** Volume da fechadura MFR: 0 = mudo … 3 = alto (contrato da GDI: 0 a 3). */
enum class LockVolume(val level: Int, val label: String) {
    MUTE(0, "Mudo"), LOW(1, "Baixo"), MEDIUM(2, "Médio"), HIGH(3, "Alto");

    companion object {
        fun fromLevel(level: Int?): LockVolume? = entries.firstOrNull { it.level == level }
    }
}

/**
 * Leitura completa da fechadura. Campos nulos = endpoint falhou ou não respondeu (cada um é
 * independente). [volumeError]/[historyError] separam "vazio" de "erro" na UI.
 */
data class LockDetails(
    val open: Boolean?,
    val remoteEnabled: Boolean?,
    val battery: Int?,
    val volume: LockVolume?,
    val volumeError: String?,
    val history: List<LockEvent>,
    val historyError: String? = null,
)
