package br.com.pompeo.casa.domain.repository

import br.com.pompeo.casa.domain.DevicesState
import br.com.pompeo.casa.domain.model.Device
import br.com.pompeo.casa.domain.model.DeviceQuery
import br.com.pompeo.casa.domain.model.Firmware
import br.com.pompeo.casa.domain.model.LockDetails
import br.com.pompeo.casa.domain.model.LockEvent
import br.com.pompeo.casa.domain.model.LockVolume
import br.com.pompeo.casa.domain.model.OriginFilter
import br.com.pompeo.casa.domain.model.StreamSession
import kotlinx.coroutines.flow.StateFlow

/** Dispositivos de todos os parceiros registrados (RF02, RF07, RF08) e as operações sobre cada um. */
interface DeviceRepository {
    val query: StateFlow<DeviceQuery>
    val state: StateFlow<DevicesState>
    /** Acumulado de todas as páginas carregadas (sem duplicatas), na ordem da API. As telas de detalhe leem daqui. */
    val devices: StateFlow<List<Device>>
    val providerNames: List<String>
    fun providerName(device: Device): String?

    /** Primeira página com a query atual. Devolve o estado calculado mesmo quando ele ficou velho e não foi publicado. */
    suspend fun loadFirstPage(): DevicesState
    fun refresh()
    /** Próxima página; erro aqui vira [DevicesState.Content.loadMoreError] e não apaga a lista. */
    fun loadMore()
    fun setOrigin(filter: OriginFilter)
    fun setPageSize(size: Int)
    /** Cancela consultas em voo, apaga o token e volta a [DevicesState.NoToken]. */
    fun logout()

    /** [channel] = lente (0/1), repassada ao parceiro sem interpretação. */
    suspend fun startLive(camera: Device, channel: Int = 0): StreamSession
    suspend fun stopLive(session: StreamSession)
    suspend fun lockDetails(lock: Device): LockDetails
    suspend fun setLock(lock: Device, open: Boolean)
    /** Só grava; reler e decidir o que mostrar é do [br.com.pompeo.casa.domain.usecase.ChangeLockVolumeUseCase]. */
    suspend fun setLockVolume(lock: Device, volume: LockVolume)
    suspend fun lockVolume(lock: Device): LockVolume?
    suspend fun lockHistory(lock: Device, quantity: Int): List<LockEvent>
    suspend fun firmware(device: Device): Firmware
    suspend fun isOnline(device: Device): Boolean?
}
