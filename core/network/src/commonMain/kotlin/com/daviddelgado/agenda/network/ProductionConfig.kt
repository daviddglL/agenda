package com.daviddelgado.agenda.network

/**
 * Configuracion de PRODUCCION (punto 4 de markdown.md: HTTPS/TLS 1.3 + certificate pinning).
 * Placeholder deliberado: este proyecto todavia no tiene un backend desplegado en un dominio
 * real, asi que no hay pines de certificado de verdad que fijar. Cuando lo haya:
 *
 * 1. Cambia [BASE_URL] por la URL https real del backend desplegado.
 * 2. Obten el pin SHA-256 de la clave publica de su certificado:
 *    `openssl s_client -connect tu-dominio.com:443 -servername tu-dominio.com </dev/null 2>/dev/null \
 *      | openssl x509 -pubkey -noout \
 *      | openssl pkey -pubin -outform der \
 *      | openssl dgst -sha256 -binary \
 *      | openssl enc -base64`
 *    El resultado se usa como "sha256/<ese-valor>=" en [CERTIFICATE_PINS_SHA256].
 * 3. Incluye TAMBIEN el pin de un certificado intermedio/CA de respaldo (backup pin): si
 *    el certificado principal caduca o se rota antes de actualizar la app, un unico pin
 *    dejaria a todos los usuarios sin poder conectar hasta la siguiente actualizacion.
 *
 * [DataModule] (Android/iOS) usa esta configuracion solo en compilaciones de release; en
 * debug se sigue apuntando al servidor de desarrollo local (ver `ANDROID_EMULATOR_BASE_URL`
 * / `IOS_SIMULATOR_BASE_URL`), que no tiene ni necesita pinning.
 */
object ProductionConfig {
    // TODO(produccion): sustituir por la URL real cuando el backend este desplegado.
    const val BASE_URL: String = "https://api.agenda.example.com/"

    // TODO(produccion): sustituir por los pines reales (principal + respaldo). Vacio en el
    // codigo entra en vigor igualmente (Ktor/OkHttp no pinnean nada si la lista esta vacia),
    // asi que dejar esto sin rellenar en un release real seria un fallo de seguridad
    // silencioso: se pierde el punto 4 de markdown.md sin que ningun test lo detecte.
    val CERTIFICATE_PINS_SHA256: List<String> = emptyList()
}
