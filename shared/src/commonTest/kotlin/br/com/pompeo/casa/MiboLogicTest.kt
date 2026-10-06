package br.com.pompeo.casa

import br.com.pompeo.casa.domain.model.DeviceKind
import br.com.pompeo.casa.domain.model.DeviceOrigin
import br.com.pompeo.casa.platform.WEEK_STRIP_OFFSETS
import br.com.pompeo.casa.platform.buildWeekStrip
import br.com.pompeo.casa.platform.weekInitial
import br.com.pompeo.casa.ui.LENS_LABELS
import br.com.pompeo.casa.ui.deviceSubtitle
import br.com.pompeo.casa.ui.lensCount
import br.com.pompeo.casa.ui.onlineLabel
import br.com.pompeo.casa.ui.sharedLabel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MiboLogicTest {
    @Test
    fun lensBadgeComesFromDualInModelOrName() {
        assertEquals(2, lensCount("iM4 Dual"))
        assertEquals(2, lensCount(model = null, name = "Câmera iM4 DUAL"))
        assertEquals(2, lensCount(model = "IM4 C", name = "Garagem dual"))
        assertEquals(1, lensCount("iM3 C"))
        assertEquals(1, lensCount(model = null, name = null))
        assertEquals(2, LENS_LABELS.size)
    }

    @Test
    fun weekInitialsFollowPortugueseNames() {
        assertEquals(listOf("S", "T", "Q", "Q", "S", "S", "D"), (1..7).map(::weekInitial))
        assertEquals("?", weekInitial(0))
    }

    @Test
    fun weekStripGoesFromSixDaysAgoToTomorrowWithTodayAtIndexSix() {
        // Arrange: calendário falso com hoje (deslocamento 0) = segunda-feira, 05/10; setembro tem 30 dias.
        val dayOfMonthAt = { offset: Int -> (TODAY + offset).let { if (it < 1) it + DAYS_IN_SEPTEMBER else it } }
        val isoDayOfWeekAt = { offset: Int -> offset.mod(DAYS_IN_WEEK) + ISO_MONDAY }
        // Act
        val strip = buildWeekStrip(dayOfMonthAt, isoDayOfWeekAt)
        // Assert
        assertEquals(WEEK_STRIP_OFFSETS.count(), strip.size)
        assertEquals(listOf("29", "30", "01", "02", "03", "04", "05", "06"), strip.map { it.day })
        assertEquals(listOf("T", "Q", "Q", "S", "S", "D", "S", "T"), strip.map { it.initial })
        assertEquals(listOf(6), strip.indices.filter { strip[it].isToday })
    }

    @Test
    fun onlineAndSharedLabels() {
        assertEquals("Online", onlineLabel(true))
        assertEquals("Offline", onlineLabel(false))
        assertEquals("—", onlineLabel(null))
        assertEquals("compartilhado", sharedLabel(DeviceOrigin.SHARED))
        assertNull(sharedLabel(DeviceOrigin.LINKED))
        assertNull(sharedLabel(DeviceOrigin.UNKNOWN))
        assertNull(sharedLabel(null))
    }

    @Test
    fun deviceSubtitleJoinsModelHubAndShared() {
        val sharedLockViaHub = device("LOCK1", kind = DeviceKind.LOCK, origin = DeviceOrigin.SHARED).copy(model = "MFR 2030", subdevice = true)
        val linkedCameraWithoutModel = device("CAM1", origin = DeviceOrigin.LINKED)
        assertEquals("MFR 2030 • via hub • compartilhado", deviceSubtitle(sharedLockViaHub))
        assertEquals("Câmera", deviceSubtitle(linkedCameraWithoutModel))
    }

    private companion object {
        const val TODAY = 5
        const val DAYS_IN_SEPTEMBER = 30
        const val DAYS_IN_WEEK = 7
        const val ISO_MONDAY = 1
    }
}
