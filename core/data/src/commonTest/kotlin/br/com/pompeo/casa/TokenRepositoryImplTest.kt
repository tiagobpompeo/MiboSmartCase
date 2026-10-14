package br.com.pompeo.casa

import br.com.pompeo.casa.data.TokenRepositoryImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TokenRepositoryImplTest {
    @Test
    fun loadsPersistedTokenAndIgnoresBlankSuggestion() {
        // Arrange
        val storage = InMemoryTokenStorage(PERSISTED_TOKEN)
        // Act
        val tokens = TokenRepositoryImpl(storage, suggested = "   ")
        // Assert
        assertEquals(PERSISTED_TOKEN, tokens.token.value)
        assertTrue(tokens.persisted)
        assertNull(tokens.suggested, "sugestão em branco não conta")
        assertEquals("Ot_pe…3456", tokens.masked())
    }

    @Test
    fun suggestionIsNotALogin() {
        // Arrange
        val storage = InMemoryTokenStorage()
        // Act
        val tokens = TokenRepositoryImpl(storage, suggested = "  $SUGGESTED_TOKEN ")
        // Assert
        assertEquals(SUGGESTED_TOKEN, tokens.suggested)
        assertNull(tokens.token.value, "a sugestão só pré-preenche a tela de token")
        assertFalse(tokens.persisted)
        assertNull(tokens.masked())
        assertTrue(storage.writes.isEmpty())
    }

    @Test
    fun sessionStaysInMemoryUntilPersist() {
        // Arrange
        val storage = InMemoryTokenStorage()
        val tokens = TokenRepositoryImpl(storage)
        // Act
        tokens.setSession(SESSION_TOKEN)
        val writesAfterSession = storage.writes.toList()
        tokens.persist(SESSION_TOKEN)
        val writesAfterPersist = storage.writes.toList()
        tokens.clear()
        // Assert
        assertTrue(writesAfterSession.isEmpty(), "setSession não grava: o token ainda está em validação")
        assertEquals(listOf<String?>(SESSION_TOKEN), writesAfterPersist)
        assertEquals(listOf(SESSION_TOKEN, null), storage.writes, "clear grava null")
        assertNull(tokens.token.value)
    }

    @Test
    fun persistedTellsWhetherTheSessionTokenIsStored() {
        // Arrange
        val tokens = TokenRepositoryImpl(InMemoryTokenStorage())
        val observed = mutableListOf(tokens.persisted)
        // Act
        tokens.setSession(SESSION_TOKEN)
        observed += tokens.persisted
        tokens.persist(SESSION_TOKEN)
        observed += tokens.persisted
        tokens.setSession(OTHER_SESSION_TOKEN)
        observed += tokens.persisted
        tokens.clear()
        observed += tokens.persisted
        // Assert
        assertEquals(listOf(false, false, true, false, false), observed)
    }

    @Test
    fun storageFailureKeepsTheTokenInMemoryOnly() {
        // Arrange
        val storage = ThrowingTokenStorage()
        val tokens = TokenRepositoryImpl(storage)
        // Act
        val stored = tokens.persist(SESSION_TOKEN)
        val tokenAfterPersist = tokens.token.value
        val persistedAfterPersist = tokens.persisted
        tokens.clear()
        // Assert
        assertFalse(stored, "a gravação falhou: o token vale só nesta sessão")
        assertEquals(SESSION_TOKEN, tokenAfterPersist)
        assertFalse(persistedAfterPersist)
        assertNull(tokens.token.value, "clear não lança e o token sai da memória")
        assertEquals(2, storage.writeAttempts)
    }

    @Test
    fun unreadableStorageStartsWithoutToken() {
        // Arrange
        val storage = ThrowingTokenStorage(initial = PERSISTED_TOKEN, readFails = true)
        // Act
        val tokens = TokenRepositoryImpl(storage)
        // Assert
        assertNull(tokens.token.value)
        assertFalse(tokens.persisted)
        assertNull(tokens.masked())
    }

    @Test
    fun inMemoryStorageBehavesLikeRealOne() {
        // Arrange: grava como o app faria antes de ser fechado.
        val storage = InMemoryTokenStorage()
        TokenRepositoryImpl(storage).persist("  $SESSION_TOKEN ")
        // Act: "reabre o app", faz logout e "reabre" de novo sobre o mesmo armazenamento.
        val reopened = TokenRepositoryImpl(storage).token.value
        TokenRepositoryImpl(storage).clear()
        val afterLogout = TokenRepositoryImpl(storage).token.value
        // Assert
        assertEquals(SESSION_TOKEN, reopened, "grava o token já normalizado")
        assertNull(afterLogout)
        assertEquals(listOf(SESSION_TOKEN, null), storage.writes)
    }

    private companion object {
        const val PERSISTED_TOKEN = "Ot_persisted_0123456"
        const val SUGGESTED_TOKEN = "Ot_suggested_0001"
        const val SESSION_TOKEN = "Ot_session_000001"
        const val OTHER_SESSION_TOKEN = "Ot_session_000002"
    }
}
