package br.com.pompeo.casa.data.gdi

import br.com.pompeo.casa.domain.model.Device
import br.com.pompeo.casa.domain.model.DevicePage
import br.com.pompeo.casa.domain.model.DeviceQuery
import br.com.pompeo.casa.domain.model.Firmware
import br.com.pompeo.casa.domain.model.LockDetails
import br.com.pompeo.casa.domain.model.LockEvent
import br.com.pompeo.casa.domain.model.LockVolume
import br.com.pompeo.casa.domain.model.StreamSession
import br.com.pompeo.casa.domain.provider.DeviceProvider
import br.com.pompeo.casa.domain.provider.LockController
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** Subdispositivos (Zigbee) são endereçados na GDI como NsDispositivo_NsHub_IdProdutoHub. */
internal val Device.gdiApiNs: String
    get() = if (subdevice && parentNs != null && parentProductId != null) "${ns}_${parentNs}_$parentProductId" else ns

/** Parceiro Intelbras (GDI): adapta o [GdiApi] ao contrato neutro [DeviceProvider]. */
class GdiDeviceProvider(private val api: GdiApi) : DeviceProvider {
    override val id: String = GdiDeviceParser.PROVIDER_ID
    override val displayName: String = "Intelbras Casa Inteligente"

    override suspend fun listDevices(query: DeviceQuery): DevicePage {
        val json = api.listDevices(page = query.page, pageSize = query.pageSize, origin = query.origin.apiValue)
        return DevicePage(GdiDeviceParser.parse(json), page = query.page, pageSize = query.pageSize)
    }

    /** [channel] vira canalVideo; online e vídeo usam o ns puro. */
    override suspend fun startLive(camera: Device, channel: Int): StreamSession = api.createVideoStream(camera.ns, channel = channel)
    override suspend fun stopLive(session: StreamSession) = api.endSession(session.sessionId)
    override suspend fun firmware(device: Device): Firmware = api.firmware(device.gdiApiNs)
    override suspend fun isOnline(device: Device): Boolean? = api.isOnline(device.ns)
    override val locks: LockController = GdiLockController(api)
}

/** Fechadura MFR via GDI (subdispositivo Zigbee do hub). */
class GdiLockController(private val api: GdiApi) : LockController {
    /** 5 leituras independentes em paralelo; cada uma com runCatching, então uma falha não cancela as irmãs. */
    override suspend fun details(lock: Device): LockDetails = coroutineScope {
        val pid = lock.gdiProductId
        val ns = lock.gdiApiNs
        // runCatching também captura CancellationException; aqui é inofensivo: o coroutineScope cancelado relança no await.
        val open = async { runCatching { api.lockIsOpen(ns, pid) }.getOrNull() }
        val remote = async { runCatching { api.lockRemoteOpenEnabled(ns, pid) }.getOrNull() }
        val battery = async { runCatching { api.battery(ns, pid) }.getOrNull() }
        val volume = async { runCatching { LockVolume.fromLevel(api.lockVolume(ns, pid)) } }
        val history = async { runCatching { api.lockHistory(ns, DEFAULT_HISTORY) } }
        val volumeResult = volume.await()
        val historyResult = history.await()
        LockDetails(
            open = open.await(),
            remoteEnabled = remote.await(),
            battery = battery.await(),
            volume = volumeResult.getOrNull(),
            // Fato: /fechaduras/volume/v1 devolveu HTTP 500; a UI mostra "a API respondeu HTTP 500".
            volumeError = volumeResult.exceptionOrNull()?.toShortMessage(),
            history = historyResult.getOrDefault(emptyList()),
            historyError = historyResult.exceptionOrNull()?.toAppError()?.userMessage,
        )
    }

    override suspend fun setOpen(lock: Device, open: Boolean) = api.controlLock(lock.gdiApiNs, lock.gdiProductId, open)
    override suspend fun volume(lock: Device): LockVolume? = LockVolume.fromLevel(api.lockVolume(lock.gdiApiNs, lock.gdiProductId))
    override suspend fun setVolume(lock: Device, volume: LockVolume) = api.setLockVolume(lock.gdiApiNs, lock.gdiProductId, volume.level)
    override suspend fun history(lock: Device, count: Int): List<LockEvent> = api.lockHistory(lock.gdiApiNs, count)

    // O corpo leva o idProduto da PRÓPRIA fechadura; o do hub vai só dentro do ns composto.
    private val Device.gdiProductId: String get() = requireNotNull(productId) { "Fechadura sem idProduto" }

    companion object {
        const val DEFAULT_HISTORY = 10
    }
}
