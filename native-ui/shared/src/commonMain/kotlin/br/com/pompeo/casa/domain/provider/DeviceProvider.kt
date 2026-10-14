package br.com.pompeo.casa.domain.provider

import br.com.pompeo.casa.domain.model.Device
import br.com.pompeo.casa.domain.model.DevicePage
import br.com.pompeo.casa.domain.model.DeviceQuery
import br.com.pompeo.casa.domain.model.Firmware
import br.com.pompeo.casa.domain.model.LockDetails
import br.com.pompeo.casa.domain.model.LockEvent
import br.com.pompeo.casa.domain.model.LockVolume
import br.com.pompeo.casa.domain.model.StreamSession

/** Um parceiro de casa inteligente. Hoje só a GDI (Intelbras); outro parceiro = outra implementação registrada na DI. */
interface DeviceProvider {
    val id: String            // "gdi"
    val displayName: String   // "Intelbras Casa Inteligente"
    suspend fun listDevices(query: DeviceQuery): DevicePage
    /** [channel] = canalVideo: 0 na câmera de uma lente; 0 (móvel) ou 1 (fixa) nas "Dual". */
    suspend fun startLive(camera: Device, channel: Int = 0): StreamSession
    suspend fun stopLive(session: StreamSession)
    suspend fun firmware(device: Device): Firmware
    suspend fun isOnline(device: Device): Boolean?
    /** null quando o parceiro não tem fechaduras. */
    val locks: LockController?
}

interface LockController {
    /** Leituras em paralelo, cada uma isolada: um endpoint fora do ar não derruba os outros. */
    suspend fun details(lock: Device): LockDetails
    suspend fun setOpen(lock: Device, open: Boolean)
    /** Lança em erro de API (o chamador decide). */
    suspend fun volume(lock: Device): LockVolume?
    suspend fun setVolume(lock: Device, volume: LockVolume)
    /** 10 na abertura da tela; 30 em "Ver mais" (RF09). */
    suspend fun history(lock: Device, count: Int): List<LockEvent>
}
