package com.daviddelgado.agenda.core.data.session

/**
 * Almacenamiento seguro cifrado (punto 4 de markdown.md):
 * EncryptedSharedPreferences/KeyStore en Android, Keychain en iOS.
 */
expect class SecureStorage {
    fun putString(
        key: String,
        value: String,
    )

    fun getString(key: String): String?

    fun remove(key: String)
}
