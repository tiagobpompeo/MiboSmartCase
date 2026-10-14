package br.com.pompeo.casa.domain

import kotlin.jvm.JvmStatic

/**
 * Regras de forma do token. Kotlin puro: commonMain não compila Java.
 * Funções com @JvmStatic: o Java chama TokenFormat.mask(...) como estático, sem passar por TokenFormat.INSTANCE.
 */
object TokenFormat {
    private const val MIN_LENGTH = 8
    private const val MASK_MIN_LENGTH = 12
    private const val HIDDEN = "••••"

    /** `trim`; `""` para null. */
    @JvmStatic
    fun normalize(raw: String?): String = raw?.trim().orEmpty()

    /** Normalizado com pelo menos 8 caracteres e sem espaço em branco interno. */
    @JvmStatic
    fun isPlausible(raw: String?): Boolean {
        val value = normalize(raw)
        return value.length >= MIN_LENGTH && value.none { it.isWhitespace() }
    }

    /** "Ot_ab…wxyz": 5 primeiros + "…" + 4 últimos; tokens curtos (< 12) viram "••••" para não vazar. */
    @JvmStatic
    fun mask(token: String?): String {
        val value = normalize(token)
        if (value.length < MASK_MIN_LENGTH) return HIDDEN
        return value.take(5) + "…" + value.takeLast(4)
    }
}
