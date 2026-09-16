package com.daviddelgado.agenda.domain.usecase

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class ResetPasswordUseCaseTest {
    @Test
    fun delegaEnElRepositorio() =
        runTest {
            val repository = FakeAuthRepository()
            val useCase = ResetPasswordUseCase(repository)

            useCase("olvide@test.com", "123456", "nueva123")

            assertTrue(repository.passwordWasReset)
        }
}
