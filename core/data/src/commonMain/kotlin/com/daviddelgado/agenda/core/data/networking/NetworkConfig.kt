package com.daviddelgado.agenda.core.data.networking

/**
 * @param baseUrl URL base del backend, terminada en "/" (p.ej. "http://10.0.2.2:8080/" para
 * el emulador de Android, que ve el localhost del PC en esa IP).
 * @param certificatePinsSha256 hashes SHA-256 (formato "sha256/xxxx=") de las claves publicas
 * del backend, usados para SSL Certificate Pinning (punto 4 de seguridad de markdown.md).
 */
data class NetworkConfig(
    val baseUrl: String,
    val certificatePinsSha256: List<String> = emptyList(),
) {
    /** Solo el host, sin esquema ni puerto ni ruta: es lo que espera el pinner de OkHttp. */
    val host: String
        get() = baseUrl.substringAfter("://").substringBefore("/").substringBefore(":")
}
