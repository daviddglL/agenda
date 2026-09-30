package com.daviddelgado.agenda.feature.auth.domain.fcm

/**
 * Token push de este dispositivo (Firebase Cloud Messaging en Android; ver
 * AgendaFirebaseMessagingService en :androidApp para el resto del flujo de recordatorios push).
 * Cada plataforma provee su implementacion real via `platformAuthDataModule` (feature:auth:data); en tests se inyecta
 * un fake.
 */
interface FcmTokenProvider {
    suspend fun currentToken(): String?
}
