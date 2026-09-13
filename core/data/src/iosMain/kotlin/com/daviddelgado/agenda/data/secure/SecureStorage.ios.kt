package com.daviddelgado.agenda.data.secure

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFTypeRefVar
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

private const val KEYCHAIN_SERVICE = "com.daviddelgado.agenda"

/**
 * Envoltorio de Keychain (Security.framework) para iOS, equivalente al
 * EncryptedSharedPreferences de Android. Patron basado en el de la libreria
 * `russhwolf/multiplatform-settings`. Verificar/ajustar en Xcode antes de produccion.
 */
@OptIn(ExperimentalForeignApi::class)
actual class SecureStorage {
    private fun baseQuery(key: String): Map<Any?, Any?> =
        mapOf(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to KEYCHAIN_SERVICE,
            kSecAttrAccount to key,
        )

    actual fun putString(
        key: String,
        value: String,
    ) {
        val data = NSString.create(string = value).dataUsingEncoding(NSUTF8StringEncoding) ?: return
        val query = baseQuery(key)
        @Suppress("UNCHECKED_CAST")
        SecItemDelete(query as CFDictionaryRef)
        val newItem = query + mapOf<Any?, Any?>(kSecValueData to data)
        @Suppress("UNCHECKED_CAST")
        SecItemAdd(newItem as CFDictionaryRef, null)
    }

    actual fun getString(key: String): String? {
        val query =
            baseQuery(key) +
                mapOf<Any?, Any?>(
                    kSecReturnData to true,
                    kSecMatchLimit to kSecMatchLimitOne,
                )
        return memScoped {
            val result = alloc<CFTypeRefVar>()

            @Suppress("UNCHECKED_CAST")
            val status = SecItemCopyMatching(query as CFDictionaryRef, result.ptr)
            if (status != errSecSuccess) return@memScoped null
            val data = result.value as? NSData ?: return@memScoped null
            NSString.create(data = data, encoding = NSUTF8StringEncoding) as String?
        }
    }

    actual fun remove(key: String) {
        @Suppress("UNCHECKED_CAST")
        SecItemDelete(baseQuery(key) as CFDictionaryRef)
    }
}
