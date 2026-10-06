package br.com.pompeo.casa.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import br.com.pompeo.casa.domain.TokenStorage
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Token cifrado com AES-256/GCM por uma chave que nunca sai do Android Keystore; o par
 * `iv:ciphertext` (Base64) fica em SharedPreferences privadas. Não usamos a security-crypto
 * (EncryptedSharedPreferences) porque a Google a descontinuou em 2024; o Keystore direto é o
 * caminho recomendado e não traz dependência extra.
 */
class KeystoreTokenStorage(context: Context) : TokenStorage {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun read(): String? =
        runCatching {
            // Sem token salvo é sucesso com null, não erro: o getOrElse abaixo fica só para falha de decifragem.
            val stored = prefs.getString(KEY, null) ?: return@runCatching null
            val (iv, cipherText) = stored.split(':', limit = 2).map { Base64.decode(it, Base64.NO_WRAP) }
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv))
            String(cipher.doFinal(cipherText), Charsets.UTF_8)
        }.getOrElse {
            // Chave perdida (restauração, reset) ou dado corrompido: apaga e trata como "sem token".
            prefs.edit().remove(KEY).apply()
            null
        }

    override fun write(token: String?) {
        if (token == null) {
            prefs.edit().remove(KEY).apply()
            return
        }
        // Sem IV aqui: o Keystore exige IV aleatório (randomizedEncryptionRequired) e gera um; lemos de cipher.iv.
        // Falha do Keystore sobe para o TokenRepositoryImpl, que mantém a sessão só em memória (persisted = false).
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val cipherText = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        prefs.edit().putString(KEY, encode(cipher.iv) + ":" + encode(cipherText)).apply() // Base64 não contém ':'
    }

    private fun encode(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) } // load(null) é obrigatório
        (keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                // Sem setUserAuthenticationRequired: exigiria biometria a cada leitura e invalidaria a chave ao trocar o bloqueio.
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "casa.token"
        const val PREFS = "casa.secure"
        const val KEY = "gdi-token"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
    }
}
