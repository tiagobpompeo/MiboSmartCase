package br.com.pompeo.casa.domain.usecase

import br.com.pompeo.casa.domain.model.Device
import br.com.pompeo.casa.domain.model.LockVolume
import br.com.pompeo.casa.domain.repository.DeviceRepository
import br.com.pompeo.casa.domain.resultOf

/** Grava o volume da fechadura e devolve o volume a exibir. */
class ChangeLockVolumeUseCase(private val devices: DeviceRepository) {
    /**
     * Relê porque a GDI não devolve o valor novo. Releitura que falha (HTTP 500 real) ou vem vazia assume o
     * pedido: a escrita foi aceita. Erro na ESCRITA sobe para o chamador, que mostra a falha.
     */
    suspend operator fun invoke(lock: Device, volume: LockVolume): LockVolume {
        devices.setLockVolume(lock, volume)
        return resultOf { devices.lockVolume(lock) }.getOrNull() ?: volume
    }
}
