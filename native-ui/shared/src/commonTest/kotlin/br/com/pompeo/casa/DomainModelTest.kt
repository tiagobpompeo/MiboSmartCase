package br.com.pompeo.casa

import br.com.pompeo.casa.domain.model.DeviceOrigin
import br.com.pompeo.casa.domain.model.DevicePage
import br.com.pompeo.casa.domain.model.LockVolume
import br.com.pompeo.casa.domain.model.OriginFilter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DomainModelTest {
    @Test
    fun originParseAcceptsSingularPluralCaseAndSpaces() {
        // Arrange: a GDI usa singular no item e plural no filtro do pedido.
        val cases = mapOf(
            "vinculado" to DeviceOrigin.LINKED,
            "vinculados" to DeviceOrigin.LINKED,
            "  VINCULADO " to DeviceOrigin.LINKED,
            "compartilhado" to DeviceOrigin.SHARED,
            "Compartilhados" to DeviceOrigin.SHARED,
            "\tcompartilhado\n" to DeviceOrigin.SHARED,
        )
        // Act
        val parsed = cases.keys.associateWith { DeviceOrigin.parse(it) }
        // Assert
        assertEquals(cases, parsed)
    }

    @Test
    fun originFilterAcceptsUnknownOrigin() {
        // Arrange: filtro -> origens aceitas no aparelho.
        val expected = mapOf(
            OriginFilter.ALL to setOf(DeviceOrigin.LINKED, DeviceOrigin.SHARED, DeviceOrigin.UNKNOWN),
            OriginFilter.LINKED to setOf(DeviceOrigin.LINKED, DeviceOrigin.UNKNOWN),
            OriginFilter.SHARED to setOf(DeviceOrigin.SHARED, DeviceOrigin.UNKNOWN),
        )
        // Act
        val accepted = OriginFilter.entries.associateWith { originFilter ->
            DeviceOrigin.entries.filter { originFilter.accepts(it) }.toSet()
        }
        // Assert
        assertEquals(expected, accepted)
        assertTrue(OriginFilter.entries.all { it.accepts(DeviceOrigin.UNKNOWN) }, "origem desconhecida nunca é descartada")
    }

    @Test
    fun devicePageHasMoreWhenFull() {
        // Arrange: a GDI não devolve total; página cheia é o único sinal de que pode haver mais.
        val pageSize = 5
        val fullPage = DevicePage(List(pageSize) { device("CAM$it") }, page = 1, pageSize = pageSize)
        val lastPage = DevicePage(List(2) { device("CAM$it") }, page = 2, pageSize = pageSize)
        // Act
        val fullHasMore = fullPage.hasMore
        val lastHasMore = lastPage.hasMore
        // Assert
        assertTrue(fullHasMore, "5 de 5: pode haver mais")
        assertFalse(lastHasMore, "2 de 5: fim da lista")
    }

    @Test
    fun lockVolumeFromLevel() {
        // Arrange: contrato da GDI é 0 a 3; fora disso não há volume conhecido.
        val levels = listOf(0, 1, 2, 3, 4, -1, null)
        // Act
        val volumes = levels.map { LockVolume.fromLevel(it) }
        // Assert
        assertEquals(listOf(LockVolume.MUTE, LockVolume.LOW, LockVolume.MEDIUM, LockVolume.HIGH, null, null, null), volumes)
    }
}
