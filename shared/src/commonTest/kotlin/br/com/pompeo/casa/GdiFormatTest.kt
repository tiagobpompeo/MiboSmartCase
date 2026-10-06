package br.com.pompeo.casa

import br.com.pompeo.casa.data.gdi.GdiFormat
import kotlin.test.Test
import kotlin.test.assertEquals

class GdiFormatTest {
    @Test
    fun dateTimeFormatsLocalAndUtcAndKeepsUnknownRaw() {
        // Arrange: cru da GDI (AAAAMMDDTHHMMSS, "Z" = UTC) -> texto exibido.
        val cases = mapOf<String?, String>(
            "20261002T162932" to "02/10/2026 16:29:32",
            "20261002T192905Z" to "02/10/2026 19:29:05 UTC",
            " 20261005T120000Z " to "05/10/2026 12:00:00 UTC",
            "2026-10-02 16:29" to "2026-10-02 16:29",
            null to "",
        )
        // Act
        val formatted = cases.keys.associateWith { GdiFormat.dateTime(it) }
        // Assert
        assertEquals(cases, formatted)
    }

    @Test
    fun lockEventTypeTranslatesKnownTypes() {
        // Arrange: (tipo, nome) -> descrição. Só usuarioRemoto e interno foram vistos na API; os demais são hipótese.
        val cases = mapOf(
            ("usuarioRemoto" to "APP") to "Remoto (APP)",
            ("usuarioRemoto" to "") to "Remoto",
            ("interno" to "") to "Por dentro (manual)",
            ("senha" to "Ana") to "Senha (Ana)",
            ("digital" to "Ana") to "Digital (Ana)",
            ("cartao" to "") to "Cartão/Tag",
            ("tag" to "Chaveiro") to "Cartão/Tag (Chaveiro)",
            ("chave" to "") to "Chave mecânica",
            ("x" to "y") to "x • y",
            ("" to "") to "Desconhecido",
        )
        // Act
        val translated = cases.keys.associateWith { (type, name) -> GdiFormat.lockEventType(type, name) }
        // Assert
        assertEquals(cases, translated)
    }
}
