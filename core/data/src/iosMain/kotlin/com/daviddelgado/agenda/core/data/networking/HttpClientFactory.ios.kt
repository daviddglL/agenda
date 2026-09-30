package com.daviddelgado.agenda.core.data.networking

import com.daviddelgado.agenda.core.data.session.TokenProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin

/**
 * En iOS el certificate pinning se valida contra el Keychain/NSURLSession nativo.
 * NOTA: la validacion SPKI concreta (comparar [NetworkConfig.certificatePinsSha256] contra
 * el certificado del `SecTrustRef` recibido en el challenge) requiere Xcode/macOS para
 * compilar y probar el interop con Security.framework: dejar implementada y verificada
 * por el equipo iOS antes de ir a produccion.
 */
actual fun createHttpClient(
    config: NetworkConfig,
    tokenProvider: TokenProvider,
): HttpClient =
    HttpClient(Darwin) {
        installAgendaPlugins(config, tokenProvider)
        engine {
            configureRequest {
                setAllowsCellularAccess(true)
            }
        }
    }
