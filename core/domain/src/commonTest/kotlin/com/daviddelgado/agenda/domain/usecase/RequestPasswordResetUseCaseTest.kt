package com.daviddelgado.agenda.domain.usecase

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RequestPasswordResetUseCaseTest {
    @Test
    fun delegaEnElRepositorio() =
        runTest {
            val repository = FakeAuthRepository()
            val useCase = RequestPasswordResetUseCase(repository)

            useCase("olvide@test.com")

            assertEquals("olvide@test.com", repository.passwordResetRequestedFor)
        }
}
