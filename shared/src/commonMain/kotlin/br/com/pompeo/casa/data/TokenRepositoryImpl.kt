package br.com.pompeo.casa.data

import br.com.pompeo.casa.domain.TokenFormat
import br.com.pompeo.casa.domain.TokenStorage
import br.com.pompeo.casa.domain.repository.TokenRepository
import kotlin.concurrent.Volatile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Token da conta Intelbras: em memória durante a validação, no armazenamento seguro depois dela.
 * [suggested] é só o pré-preenchimento vindo de local.properties (desenvolvimento), nunca login.
 */
class TokenRepositoryImpl(private val storage: TokenStorage, suggested: String? = null) : TokenRepository {
    override val suggested: String? = TokenFormat.normalize(suggested).takeIf { it.isNotEmpty() }

    // Lido uma vez na criação: a leitura do Keystore/Keychain é síncrona e barata. Falha na leitura = sem token.
    private val mutable = MutableStateFlow(TokenFormat.normalize(runCatching { storage.read() }.getOrNull()).takeIf { it.isNotEmpty() })
    override val token: StateFlow<String?> = mutable.asStateFlow()

    @Volatile // kotlin.concurrent.Volatile: a anotação multiplataforma (kotlin.jvm.Volatile não compila no iOS)
    override var persisted: Boolean = mutable.value != null
        private set

    override fun setSession(token: String) {
        mutable.value = TokenFormat.normalize(token).takeIf { it.isNotEmpty() }
        persisted = false
    }

    /**
     * Devolve false se a gravação falhou: o Keystore/Keychain pode lançar e isso não pode derrubar o app
     * depois de a GDI já ter aceitado o token — ele vale só nesta sessão.
     */
    override fun persist(token: String): Boolean {
        setSession(token)
        persisted = mutable.value != null && runCatching { storage.write(mutable.value) }.isSuccess
        return persisted
    }

    override fun clear() {
        mutable.value = null
        persisted = false
        // Falha ao apagar não pode impedir o logout: o token já saiu da memória.
        runCatching { storage.write(null) }
    }

    override fun masked(): String? = mutable.value?.let(TokenFormat::mask)
}
