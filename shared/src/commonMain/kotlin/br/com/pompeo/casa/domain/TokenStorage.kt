package br.com.pompeo.casa.domain

/** Persistência segura do token entre execuções (RF01 + critério "persistência local/segurança"). */
interface TokenStorage {
    fun read(): String?
    /** write(null) apaga. */
    fun write(token: String?)
}
