package br.com.pompeo.casa.domain

/** Converte falhas técnicas em erros que a UI sabe explicar (RF04). */
interface ErrorMapper {
    fun toAppError(error: Throwable): AppError
    /** Texto curto para caber entre parênteses na UI: "Volume indisponível (a API respondeu HTTP 500)". */
    fun toShortMessage(error: Throwable): String
}
