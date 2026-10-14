package br.com.pompeo.casa

import br.com.pompeo.casa.domain.TokenFormat
import kotlin.test.Test
import kotlin.test.assertEquals

class TokenFormatTest {
    @Test
    fun normalizeTrimsAndHandlesNull() {
        // Arrange: cru -> normalizado.
        val cases = mapOf<String?, String>(
            "  Ot_abc_0001\n" to "Ot_abc_0001",
            "Ot_abc_0001" to "Ot_abc_0001",
            "   " to "",
            null to "",
        )
        // Act
        val normalized = cases.keys.associateWith { TokenFormat.normalize(it) }
        // Assert
        assertEquals(cases, normalized)
    }

    @Test
    fun plausibleNeedsEightCharsWithoutInnerSpaces() {
        // Arrange: cru -> plausível? O limite é 8 caracteres depois do trim.
        val cases = mapOf<String?, Boolean>(
            "Ot_abc12" to true,
            "  Ot_abc12  " to true,
            "Ot_abc1" to false,
            "  Ot_abc1  " to false,
            "Ot_abc 12345" to false,
            "Ot_abc\t12345" to false,
            "" to false,
            null to false,
        )
        // Act
        val plausible = cases.keys.associateWith { TokenFormat.isPlausible(it) }
        // Assert
        assertEquals(cases, plausible)
    }

    @Test
    fun maskShowsOnlyEdgesAndHidesShortTokens() {
        // Arrange: abaixo de 12 caracteres a máscara não mostra nada, para não vazar quase o token inteiro.
        val cases = mapOf<String?, String>(
            "Ot_abcdefghwxyz" to "Ot_ab…wxyz",
            "  Ot_abc_00001  " to "Ot_ab…0001",
            "Ot_abc_0001" to "••••",
            null to "••••",
        )
        // Act
        val masked = cases.keys.associateWith { TokenFormat.mask(it) }
        // Assert
        assertEquals(cases, masked)
    }
}
