package br.com.pompeo.casa.domain

import kotlin.coroutines.cancellation.CancellationException

/** Como runCatching, mas relança CancellationException: cancelar a tela/escopo nunca vira "erro" na UI. */
inline fun <T> resultOf(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }
