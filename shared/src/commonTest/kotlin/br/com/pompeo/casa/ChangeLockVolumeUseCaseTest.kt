package br.com.pompeo.casa

import br.com.pompeo.casa.domain.model.DeviceKind
import br.com.pompeo.casa.domain.model.LockVolume
import br.com.pompeo.casa.domain.usecase.ChangeLockVolumeUseCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ChangeLockVolumeUseCaseTest {
    private val lock = device("LOCK1", kind = DeviceKind.LOCK)

    @Test
    fun writesThenRereadsTheVolume() = runTest {
        // Arrange
        val locks = FakeLockController()
        val changeVolume = ChangeLockVolumeUseCase(FakeDeviceRepository(FakeTokenRepository(), locks = locks))
        // Act
        val shown = changeVolume(lock, LockVolume.HIGH)
        // Assert
        assertEquals(LockVolume.HIGH, shown)
        assertEquals(LockVolume.HIGH, locks.volume)
    }

    @Test
    fun rereadFailureAssumesTheRequestedVolume() = runTest {
        // Arrange: leitura com o HTTP 500 real.
        val locks = FakeLockController().apply { readError = IllegalStateException("HTTP 500") }
        val changeVolume = ChangeLockVolumeUseCase(FakeDeviceRepository(FakeTokenRepository(), locks = locks))
        // Act
        val shown = changeVolume(lock, LockVolume.LOW)
        // Assert
        assertEquals(LockVolume.LOW, shown)
        assertEquals(LockVolume.LOW, locks.volume)
    }

    @Test
    fun writeFailureIsPropagatedWithoutReread() = runTest {
        // Arrange: a escrita falha; o volume gravado continua MEDIUM.
        val locks = FakeLockController().apply { writeError = IllegalStateException("HTTP 500") }
        val changeVolume = ChangeLockVolumeUseCase(FakeDeviceRepository(FakeTokenRepository(), locks = locks))
        // Act
        val error = assertFailsWith<IllegalStateException> { changeVolume(lock, LockVolume.HIGH) }
        // Assert: o erro sobe em vez de virar "volume pedido" pelo fallback da releitura.
        assertEquals("HTTP 500", error.message)
        assertEquals(LockVolume.MEDIUM, locks.volume)
    }
}
