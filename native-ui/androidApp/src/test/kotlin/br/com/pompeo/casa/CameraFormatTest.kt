package br.com.pompeo.casa

import br.com.pompeo.casa.ui.formatSeconds
import kotlin.test.Test
import kotlin.test.assertEquals

class CameraFormatTest {
    @Test
    fun formatSecondsTruncatesToTenths() {
        // Arrange: ms -> texto; trunca em décimos (3199 não arredonda para 3,2).
        val cases = mapOf(
            3_140L to "3,1 s",
            3_199L to "3,1 s",
            950L to "0,9 s",
            999L to "0,9 s",
            0L to "0,0 s",
            21_000L to "21,0 s",
            37_850L to "37,8 s",
        )
        // Act
        val formatted = cases.keys.associateWith(::formatSeconds)
        // Assert
        assertEquals(cases, formatted)
    }
}
