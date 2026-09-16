package com.daviddelgado.agenda.domain.usecase

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RegisterFcmTokenUseCaseTest {
    @Test
    fun delegaEnElRepositorio() =
        runTest {
            val repository = FakeAuthRepository()
            val useCase = RegisterFcmTokenUseCase(repository)

            useCase("token-de-prueba")

            assertEquals("token-de-prueba", repository.tokenRegistrado)
        }
}
