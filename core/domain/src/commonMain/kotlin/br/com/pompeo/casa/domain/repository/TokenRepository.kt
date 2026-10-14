package br.com.pompeo.casa.domain.repository

import kotlinx.coroutines.flow.StateFlow

/** Token da conta: em memória durante a validação, no armazenamento seguro depois dela (RF01). */
interface TokenRepository {
    /** Pré-preenchimento vindo de local.properties (desenvolvimento); nunca login automático. */
    val suggested: String?
    val token: StateFlow<String?>
    /** O token em memória está gravado no armazenamento seguro? false = vale só nesta sessão. */
    val persisted: Boolean
    /** Só memória: usado enquanto a primeira página ainda está validando o token. */
    fun setSession(token: String)
    /** Memória + armazenamento seguro. false = a gravação falhou e o token vale só nesta sessão. */
    fun persist(token: String): Boolean
    fun clear()
    /** "Ot_ab…wxyz" para exibir em tela; nunca o token inteiro. */
    fun masked(): String?
}
