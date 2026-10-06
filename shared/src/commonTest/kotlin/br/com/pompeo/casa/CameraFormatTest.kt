package br.com.pompeo.casa

import br.com.pompeo.casa.ui.formatSeconds
import kotlin.test.Test
import kotlin.test.assertEquals

class CameraFormatTest {
    @Test
    fun formatSecondsTruncatesToTenths() {
        assertEquals("3,1 s", formatSeconds(3_140))
        assertEquals("3,1 s", formatSeconds(3_199)) // trunca: não arredonda para 3,2
        assertEquals("0,0 s", formatSeconds(0))
        assertEquals("0,9 s", formatSeconds(999))
        assertEquals("21,0 s", formatSeconds(21_000))
        assertEquals("37,8 s", formatSeconds(37_850))
    }
}
