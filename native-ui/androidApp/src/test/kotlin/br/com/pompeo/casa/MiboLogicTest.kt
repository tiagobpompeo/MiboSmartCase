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

class MiboLogicTest {
    @Test
    fun lensBadgeComesFromDualInModelOrName() {
        // Arrange: (modelo, nome) -> lentes; "dual" em qualquer caixa no modelo ou no nome.
        val cases = mapOf<Pair<String?, String?>, Int>(
            ("iM4 Dual" to null) to 2,
            (null to "Câmera iM4 DUAL") to 2,
            ("IM4 C" to "Garagem dual") to 2,
            ("iM3 C" to null) to 1,
            (null to null) to 1,
        )
        // Act
        val counts = cases.keys.associateWith { (model, name) -> lensCount(model, name) }
        // Assert
        assertEquals(cases, counts)
        assertEquals(2, LENS_LABELS.size)
    }

    @Test
    fun weekInitialsFollowPortugueseNames() {
        // Arrange: dia ISO -> inicial (segunda=1 … domingo=7); fora da faixa -> "?".
        val cases = mapOf(1 to "S", 2 to "T", 3 to "Q", 4 to "Q", 5 to "S", 6 to "S", 7 to "D", 0 to "?")
        // Act
        val initials = cases.keys.associateWith(::weekInitial)
        // Assert
        assertEquals(cases, initials)
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
        // Arrange
        val onlineCases = mapOf<Boolean?, String>(true to "Online", false to "Offline", null to "—")
        val sharedCases = mapOf<DeviceOrigin?, String?>(
            DeviceOrigin.SHARED to "compartilhado",
            DeviceOrigin.LINKED to null,
            DeviceOrigin.UNKNOWN to null,
            null to null,
        )
        // Act
        val onlineLabels = onlineCases.keys.associateWith(::onlineLabel)
        val sharedLabels = sharedCases.keys.associateWith(::sharedLabel)
        // Assert
        assertEquals(onlineCases, onlineLabels)
        assertEquals(sharedCases, sharedLabels)
    }

    @Test
    fun deviceSubtitleJoinsModelHubAndShared() {
        // Arrange
        val sharedLockViaHub = device("LOCK1", kind = DeviceKind.LOCK, origin = DeviceOrigin.SHARED).copy(model = "MFR 2030", subdevice = true)
        val linkedCameraWithoutModel = device("CAM1", origin = DeviceOrigin.LINKED)
        // Act
        val sharedLockSubtitle = deviceSubtitle(sharedLockViaHub)
        val linkedCameraSubtitle = deviceSubtitle(linkedCameraWithoutModel)
        // Assert
        assertEquals("MFR 2030 • via hub • compartilhado", sharedLockSubtitle)
        assertEquals("Câmera", linkedCameraSubtitle)
    }

    private companion object {
        const val TODAY = 5
        const val DAYS_IN_SEPTEMBER = 30
        const val DAYS_IN_WEEK = 7
        const val ISO_MONDAY = 1
    }
}
