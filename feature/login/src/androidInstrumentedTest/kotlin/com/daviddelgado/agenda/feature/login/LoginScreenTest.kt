package com.daviddelgado.agenda.feature.login

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.daviddelgado.agenda.domain.model.User
import com.daviddelgado.agenda.domain.repository.AuthRepository
import com.daviddelgado.agenda.domain.usecase.LoginUseCase
import com.daviddelgado.agenda.domain.usecase.RegisterFcmTokenUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

/**
 * AuthRepository falso propio de este test instrumentado (no se reutiliza el privado de
 * LoginViewModelTest.kt: vive en commonTest, un source set que este no hereda por defecto).
 */
private class FakeAuthRepository(private val error: Throwable? = null) : AuthRepository {
    private val current = MutableStateFlow<User?>(null)

    override fun observeCurrentUser(): Flow<User?> = current

    override suspend fun login(
        email: String,
        password: String,
    ): Result<User> = error?.let { Result.failure(it) } ?: Result.success(User("u-1", "David", email))

    override suspend fun register(
        name: String,
        email: String,
        password: String,
    ): Result<User> = Result.success(User("u-1", name, email))

    override suspend fun logout() = Unit

    override suspend fun restoreSession(): User? = null

    override suspend fun deleteAccount(): Result<Unit> = Result.success(Unit)

    override suspend fun registerFcmToken(token: String): Result<Unit> = Result.success(Unit)

    override suspend fun requestPasswordReset(email: String): Result<Unit> = Result.success(Unit)

    override suspend fun resetPassword(
        email: String,
        code: String,
        newPassword: String,
    ): Result<Unit> = Result.success(Unit)
}

/** FcmTokenProvider falso: sin token, no importa para estos tests de UI (Task 10). */
private class FakeFcmTokenProvider : FcmTokenProvider {
    override suspend fun currentToken(): String? = null
}

/**
 * Tests de UI de Compose (punto 5 de markdown.md) para LoginScreen: corren en un
 * dispositivo/emulador real montando la pantalla con un ViewModel real + este fake, sin
 * pasar por Koin (LoginScreen acepta el viewModel como parametro con valor por defecto).
 */
class LoginScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun montarPantalla(
        repository: FakeAuthRepository = FakeAuthRepository(),
        onNavigateToHome: () -> Unit = {},
        onNavigateToRegister: () -> Unit = {},
    ) {
        composeRule.setContent {
            LoginScreen(
                onNavigateToHome = onNavigateToHome,
                onNavigateToRegister = onNavigateToRegister,
                viewModel =
                    LoginViewModel(
                        LoginUseCase(repository),
                        RegisterFcmTokenUseCase(repository),
                        FakeFcmTokenProvider(),
                    ),
            )
        }
    }

    @Test
    fun muestraLosCamposDeEmailYContrasenaYElBotonDeEntrar() {
        montarPantalla()

        composeRule.onNodeWithText("Email").assertIsDisplayed()
        composeRule.onNodeWithText("Contrasena").assertIsDisplayed()
        composeRule.onNodeWithText("Entrar").assertIsDisplayed()
    }

    @Test
    fun enviarConCamposVaciosMuestraElMensajeDeError() {
        montarPantalla()

        composeRule.onNodeWithText("Entrar").performClick()

        composeRule.onNodeWithText("Introduce email y contrasena").assertIsDisplayed()
    }

    @Test
    fun escribirEnElCampoDeEmailLoRellena() {
        montarPantalla()

        composeRule.onNodeWithText("Email").performTextInput("david@test.com")

        composeRule.onNodeWithText("david@test.com").assertIsDisplayed()
    }

    @Test
    fun unLoginCorrectoNavegaAHome() {
        var navego = false
        montarPantalla(onNavigateToHome = { navego = true })

        composeRule.onNodeWithText("Email").performTextInput("david@test.com")
        composeRule.onNodeWithText("Contrasena").performTextInput("secreta123")
        composeRule.onNodeWithText("Entrar").performClick()

        composeRule.waitUntil(timeoutMillis = LOGIN_TIMEOUT_MILLIS) { navego }
    }

    @Test
    fun unLoginFallidoMuestraElErrorYNoNavega() {
        var navego = false
        montarPantalla(
            repository = FakeAuthRepository(IllegalStateException("Email o contrasena incorrectos")),
            onNavigateToHome = { navego = true },
        )

        composeRule.onNodeWithText("Email").performTextInput("david@test.com")
        composeRule.onNodeWithText("Contrasena").performTextInput("mala")
        composeRule.onNodeWithText("Entrar").performClick()

        composeRule.onNodeWithText("Email o contrasena incorrectos").assertIsDisplayed()
        assert(!navego)
    }

    @Test
    fun pulsarCrearCuentaNavegaAlRegistro() {
        var navegoARegistro = false
        montarPantalla(onNavigateToRegister = { navegoARegistro = true })

        composeRule.onNodeWithText("Crear una cuenta").performClick()

        composeRule.waitUntil(timeoutMillis = LOGIN_TIMEOUT_MILLIS) { navegoARegistro }
    }
}

private const val LOGIN_TIMEOUT_MILLIS = 5_000L
