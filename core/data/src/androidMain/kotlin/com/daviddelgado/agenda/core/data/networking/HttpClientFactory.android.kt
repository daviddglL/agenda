package com.daviddelgado.agenda.core.data.networking

import com.daviddelgado.agenda.core.data.session.TokenProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.CertificatePinner

actual fun createHttpClient(
    config: NetworkConfig,
    tokenProvider: TokenProvider,
): HttpClient =
    HttpClient(OkHttp) {
        installAgendaPlugins(config, tokenProvider)
        engine {
            config {
                if (config.certificatePinsSha256.isNotEmpty()) {
                    val pinner = CertificatePinner.Builder()
                    config.certificatePinsSha256.forEach { pin ->
                        pinner.add(config.host, pin)
                    }
                    certificatePinner(pinner.build())
                }
            }
        }
    }
