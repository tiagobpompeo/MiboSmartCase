package br.com.pompeo.casa.data.security

import br.com.pompeo.casa.domain.TokenStorage
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/**
 * Token no Keychain do iOS (item genérico, service `br.com.pompeo.casa`, account `gdi-token`),
 * acessível depois do primeiro desbloqueio e só neste aparelho: não migra por backup criptografado
 * (simétrico ao allowBackup=false do Android). Bindings platform.* já vêm no Kotlin/Native: sem .def de cinterop.
 * Defensivo: qualquer status diferente de errSecSuccess na leitura vira "sem token".
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class KeychainTokenStorage : TokenStorage {
    override fun read(): String? = memScoped {
        val service = CFBridgingRetain(SERVICE) // +1: o dicionário sem callbacks não segura os valores
        val account = CFBridgingRetain(ACCOUNT)
        val query = CFDictionaryCreateMutable(null, 5, null, null)
        try {
            CFDictionaryAddValue(query, kSecClass, kSecClassGenericPassword)
            CFDictionaryAddValue(query, kSecAttrService, service)
            CFDictionaryAddValue(query, kSecAttrAccount, account)
            CFDictionaryAddValue(query, kSecReturnData, kCFBooleanTrue)
            CFDictionaryAddValue(query, kSecMatchLimit, kSecMatchLimitOne)
            val result = alloc<CFTypeRefVar>()
            val status = SecItemCopyMatching(query, result.ptr)
            if (status != errSecSuccess) return@memScoped null
            // O item copiado chega com +1: CFBridgingRelease transfere a posse para o Kotlin/Native.
            val data = CFBridgingRelease(result.value) as? NSData ?: return@memScoped null
            // toString() e não `as String`: NSString e String são tipos distintos para o compilador K/N.
            NSString.create(data, NSUTF8StringEncoding)?.toString()
        } finally {
            release(query, service, account)
        }
    }

    override fun write(token: String?) {
        delete() // delete + add: mais simples que SecItemUpdate e evita errSecDuplicateItem
        if (token == null) return
        val data = NSString.create(string = token).dataUsingEncoding(NSUTF8StringEncoding)
            ?: throw IllegalStateException("Token não codificável em UTF-8")
        val service = CFBridgingRetain(SERVICE)
        val account = CFBridgingRetain(ACCOUNT)
        val value = CFBridgingRetain(data)
        val attributes = CFDictionaryCreateMutable(null, 5, null, null)
        try {
            CFDictionaryAddValue(attributes, kSecClass, kSecClassGenericPassword)
            CFDictionaryAddValue(attributes, kSecAttrService, service)
            CFDictionaryAddValue(attributes, kSecAttrAccount, account)
            CFDictionaryAddValue(attributes, kSecValueData, value)
            // Item antigo (AfterFirstUnlock) ainda é lido: a busca não filtra acessibilidade; o delete() do início o troca.
            CFDictionaryAddValue(attributes, kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
            // Falha é relatada ao TokenRepositoryImpl (sessão só em memória); o OSStatus vai na mensagem, nunca o token.
            val status = SecItemAdd(attributes, null)
            if (status != errSecSuccess) throw IllegalStateException("Keychain recusou a gravação (OSStatus $status)")
        } finally {
            release(attributes, service, account, value)
        }
    }

    private fun delete() {
        val service = CFBridgingRetain(SERVICE)
        val account = CFBridgingRetain(ACCOUNT)
        val query = CFDictionaryCreateMutable(null, 3, null, null)
        try {
            CFDictionaryAddValue(query, kSecClass, kSecClassGenericPassword)
            CFDictionaryAddValue(query, kSecAttrService, service)
            CFDictionaryAddValue(query, kSecAttrAccount, account)
            SecItemDelete(query)
        } finally {
            release(query, service, account)
        }
    }

    /** Libera só o que criamos/retivemos; as constantes kSec… e kCFBooleanTrue são globais e não se liberam. */
    private fun release(dictionary: CFDictionaryRef?, vararg values: CFTypeRef?) {
        CFRelease(dictionary)
        values.forEach { CFRelease(it) }
    }

    private companion object {
        const val SERVICE = "br.com.pompeo.casa"
        const val ACCOUNT = "gdi-token"
    }
}
