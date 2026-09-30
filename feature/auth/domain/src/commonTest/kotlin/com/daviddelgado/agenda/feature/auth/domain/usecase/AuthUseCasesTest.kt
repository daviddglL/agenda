package com.daviddelgado.agenda.feature.auth.domain.usecase

import com.daviddelgado.agenda.feature.auth.domain.model.User
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthUseCasesTest {
    private val usuario = User("u-1", "David", "david@test.com")

    @Test
    fun elLoginCorrectoDevuelveElUsuario() =
        runTest {
            val repository = FakeAuthRepository(usuario)

            val resultado = LoginUseCase(repository)("david@test.com", "secreta123")

            assertEquals(usuario, resultado.getOrNull())
        }

    @Test
    fun elLoginFallidoDevuelveElErrorDelServidor() =
        runTest {
            val repository = FakeAuthRepository(failWith = IllegalArgumentException("Email o contrasena incorrectos"))

            val resultado = LoginUseCase(repository)("david@test.com", "mala")

            assertTrue(resultado.isFailure)
            assertEquals("Email o contrasena incorrectos", resultado.exceptionOrNull()?.message)
        }

    @Test
    fun elRegistroCorrectoDevuelveElUsuario() =
        runTest {
            val repository = FakeAuthRepository(usuario)

            val resultado = RegisterUseCase(repository)("David", "david@test.com", "secreta123")

            assertEquals(usuario, resultado.getOrNull())
        }

    @Test
    fun recuperarSesionDevuelveNullSiNoHabiaSesionGuardada() =
        runTest {
            val repository = FakeAuthRepository(usuario)

            assertNull(RestoreSessionUseCase(repository)())
        }

    @Test
    fun recuperarSesionDevuelveElUsuarioSiElTokenSigueValido() =
        runTest {
            val repository = FakeAuthRepository(usuario)
            repository.sessionRestored = usuario

            assertEquals(usuario, RestoreSessionUseCase(repository)())
        }

    @Test
    fun cerrarSesionAvisaAlRepositorio() =
        runTest {
            val repository = FakeAuthRepository(usuario)

            LogoutUseCase(repository)()

            assertTrue(repository.loggedOut)
        }

    @Test
    fun borrarLaCuentaBorraTambienLaSesion() =
        runTest {
            val repository = FakeAuthRepository(usuario)

            val resultado = DeleteAccountUseCase(repository)()

            assertTrue(resultado.isSuccess)
            assertTrue(repository.accountDeleted)
        }

    @Test
    fun borrarLaCuentaPropagaElErrorSiElServidorFalla() =
        runTest {
            val repository = FakeAuthRepository(failWith = IllegalStateException("500"))

            val resultado = DeleteAccountUseCase(repository)()

            assertTrue(resultado.isFailure)
            assertFalse(repository.accountDeleted)
        }
}
