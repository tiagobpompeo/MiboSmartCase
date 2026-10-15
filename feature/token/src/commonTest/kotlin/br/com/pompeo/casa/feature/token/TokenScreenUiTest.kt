package br.com.pompeo.casa.feature.token

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.waitUntilExactlyOneExists
import br.com.pompeo.casa.FakeDeviceRepository
import br.com.pompeo.casa.FakeErrorMapper
import br.com.pompeo.casa.FakeTokenRepository
import br.com.pompeo.casa.domain.AppError
import br.com.pompeo.casa.domain.DevicesState
import br.com.pompeo.casa.domain.model.DeviceQuery
import br.com.pompeo.casa.domain.usecase.SubmitTokenUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Teste de UI da tela do token (RF01, RF04), escrito uma vez em commonTest e executado no simulador iOS e, como
 * teste instrumentado, no aparelho Android. A tela recebe o ViewModel real com os fakes de :core:testing;
 * nenhuma chamada de rede acontece.
 *
 * Cobre: título e "Entrar" desabilitado com o campo vazio; "Entrar" só habilita com token plausível (8+
 * caracteres); token aceito pela 1.ª página chama onConnected e é persistido; token recusado mostra a mensagem
 * amigável do AppError, não navega e deixa tentar de novo (RF04); o botão do olho alterna mostrar/ocultar o token.
 */
@OptIn(ExperimentalTestApi::class)
class TokenScreenUiTest {
    private fun viewModel(
        tokens: FakeTokenRepository = FakeTokenRepository(),
        devices: FakeDeviceRepository = FakeDeviceRepository(tokens),
    ): TokenViewModel = TokenViewModel(tokens, SubmitTokenUseCase(tokens, devices), FakeErrorMapper)

    @Test
    fun showsTheTitleAndKeepsEnterDisabledWhileTheFieldIsEmpty() = runComposeUiTest {
        setContent { TokenScreen(viewModel(), onConnected = {}) }
        onNodeWithText("Conectar à conta Intelbras").assertIsDisplayed()
        onNodeWithText("Entrar").assertIsNotEnabled()
    }

    @Test
    fun enterOnlyBecomesEnabledOnceTheTokenLooksPlausible() = runComposeUiTest {
        setContent { TokenScreen(viewModel(), onConnected = {}) }
        // 7 caracteres: implausível (TokenFormat exige 8), então a tela nem deixa chamar a GDI.
        onNodeWithText("Token de acesso").performTextInput("Ot_1234")
        onNodeWithText("Entrar").assertIsNotEnabled()
        // O 8.º caractere cruza o limite.
        onNodeWithText("Token de acesso").performTextInput("5")
        onNodeWithText("Entrar").assertIsEnabled()
    }

    @Test
    fun anAcceptedTokenCallsOnConnectedAndPersistsIt() = runComposeUiTest {
        // Arrange: o FakeDeviceRepository devolve a 1.ª página vazia por padrão, que o caso de uso aceita.
        val tokens = FakeTokenRepository()
        var connected = false
        setContent { TokenScreen(viewModel(tokens), onConnected = { connected = true }) }
        // Act
        onNodeWithText("Token de acesso").performTextInput("Ot_valid_0001")
        onNodeWithText("Entrar").performClick()
        // Assert: a tela navega e o token só foi guardado depois de validado.
        waitUntil(timeoutMillis = 5_000) { connected }
        assertEquals(listOf("Ot_valid_0001"), tokens.persistedValues)
    }

    @Test
    fun aRejectedTokenShowsTheFriendlyErrorAndStaysOnTheScreen() = runComposeUiTest {
        // Arrange: a 1.ª página responde como a GDI responde a um token inválido (HTTP 401).
        val tokens = FakeTokenRepository()
        val rejected = AppError.TokenInvalid("HTTP 401: Não autorizado")
        val devices = FakeDeviceRepository(tokens) { DevicesState.Error(rejected, DeviceQuery()) }
        var connected = false
        setContent { TokenScreen(viewModel(tokens, devices), onConnected = { connected = true }) }
        // Act
        onNodeWithText("Token de acesso").performTextInput("Ot_rejected_0001")
        onNodeWithText("Entrar").performClick()
        // Assert: mensagem amigável (RF04), sem navegar nem persistir, e "Entrar" volta para a nova tentativa.
        waitUntilExactlyOneExists(hasText(rejected.userMessage), timeoutMillis = 5_000)
        onNodeWithText(rejected.userMessage).assertIsDisplayed()
        assertFalse(connected)
        assertTrue(tokens.persistedValues.isEmpty())
        onNodeWithText("Entrar").assertIsEnabled()
    }

    @Test
    fun theEyeButtonTogglesBetweenShowingAndHidingTheToken() = runComposeUiTest {
        setContent { TokenScreen(viewModel(), onConnected = {}) }
        val field = onNodeWithText("Token de acesso")
        field.performTextInput("Ot_valid_0001")
        // Oculto por padrão: o campo exibe bolinhas, não o token.
        field.assert(displaysText("•".repeat(13)))
        onNodeWithContentDescription("Mostrar token").performClick()
        onNodeWithContentDescription("Ocultar token").assertIsDisplayed()
        onNodeWithContentDescription("Mostrar token").assertDoesNotExist()
        field.assert(displaysText("Ot_valid_0001"))
        onNodeWithContentDescription("Ocultar token").performClick()
        onNodeWithContentDescription("Mostrar token").assertIsDisplayed()
        onNodeWithContentDescription("Ocultar token").assertDoesNotExist()
        field.assert(displaysText("•".repeat(13)))
    }

    /**
     * O que o campo realmente exibe (EditableText, já com a máscara de senha). hasText/onNodeWithText não servem
     * aqui: também casam com InputText, que guarda o token digitado mesmo enquanto ele está oculto.
     */
    private fun displaysText(text: String): SemanticsMatcher =
        SemanticsMatcher("displays '$text'") { node -> node.config.getOrNull(SemanticsProperties.EditableText)?.text == text }
}
