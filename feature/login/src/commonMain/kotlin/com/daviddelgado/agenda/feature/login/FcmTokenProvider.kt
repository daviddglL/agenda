package com.daviddelgado.agenda.feature.login

/**
 * Token push de este dispositivo (Firebase Cloud Messaging en Android; ver
 * AgendaFirebaseMessagingService en :androidApp para el resto del flujo de recordatorios push).
 * Cada plataforma provee su implementacion real via [platformLoginModule]; en tests se inyecta
 * un fake.
 */
interface FcmTokenProvider {
    suspend fun currentToken(): String?
}
