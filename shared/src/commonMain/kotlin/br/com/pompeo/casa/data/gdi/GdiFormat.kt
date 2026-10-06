package br.com.pompeo.casa.data.gdi

/** Formatação dos campos crus da GDI. Sem String.format (não existe em commonMain). */
object GdiFormat {
    /** "20261002T162932" -> "02/10/2026 16:29:32"; com "Z" no fim, marca UTC. Texto que não casa volta cru. */
    fun dateTime(raw: String?): String {
        val m = Regex("""(\d{4})(\d{2})(\d{2})T(\d{2})(\d{2})(\d{2})(Z?)""").matchEntire(raw?.trim().orEmpty()) ?: return raw.orEmpty()
        val (y, mo, d, h, mi, s, z) = m.destructured
        return "$d/$mo/$y $h:$mi:$s" + if (z == "Z") " UTC" else ""
    }

    /** Origem da abertura no histórico. Só usuarioRemoto e interno foram vistos na API; o resto é hipótese. */
    fun lockEventType(type: String, name: String): String =
        when (type) {
            "usuarioRemoto" -> "Remoto" + if (name.isNotBlank()) " ($name)" else ""
            "interno" -> "Por dentro (manual)"
            "senha" -> "Senha" + if (name.isNotBlank()) " ($name)" else ""
            "digital" -> "Digital" + if (name.isNotBlank()) " ($name)" else ""
            "cartao", "tag" -> "Cartão/Tag" + if (name.isNotBlank()) " ($name)" else ""
            "chave" -> "Chave mecânica"
            else -> listOf(type, name).filter { it.isNotBlank() }.joinToString(" • ").ifBlank { "Desconhecido" }
        }
}
