package br.com.pompeo.casa.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.pompeo.casa.domain.AppError
import br.com.pompeo.casa.domain.TokenFormat
import br.com.pompeo.casa.platform.isDebugBuild

/** Tela inicial sem token salvo (RF01): cola o token, valida na GDI e só então segue para a Home. */
@Composable
fun TokenScreen(vm: HomeViewModel, onConnected: () -> Unit) {
    val suggested = vm.suggestedToken
    // `remember` e não `rememberSaveable`: o token digitado não deve ir para o estado salvo do sistema (Bundle).
    var token by remember { mutableStateOf(suggested.orEmpty()) }
    var visible by rememberSaveable { mutableStateOf(false) }
    // A validação mora no ViewModel: rotação durante "Validando token…" não a cancela nem perde o resultado.
    val validation by vm.tokenValidation.collectAsStateWithLifecycle()
    val validating = validation is TokenValidation.Validating
    val error = (validation as? TokenValidation.Failed)?.error
    val canSubmit = TokenFormat.isPlausible(token) && !validating

    LaunchedEffect(validation) {
        if (validation is TokenValidation.Done) {
            vm.tokenValidationHandled()
            onConnected()
        }
    }
    fun submit() { if (canSubmit) vm.submitToken(token) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(32.dp))
        Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(64.dp), tint = MiboColors.Green)
        // Uma linha como em app-01: no iPhone de 390 pt o título quebrava em duas.
        FitText(
            "Conectar à conta Intelbras",
            maxSize = 26.sp,
            minSize = 20.sp,
            modifier = Modifier.padding(top = 16.dp),
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            "Cole o token temporário gerado no portal Casa Inteligente (menu Contas → Token Temporário). " +
                "O token fica guardado com criptografia neste aparelho.",
            Modifier.padding(vertical = 16.dp),
            color = MiboColors.TextSecondary,
            textAlign = TextAlign.Center,
        )
        TokenField(
            token = token,
            onTokenChange = { token = it },
            visible = visible,
            onToggleVisible = { visible = !visible },
            enabled = !validating,
            isError = error != null,
            prefilled = suggested != null && token == suggested,
            onDone = ::submit,
        )
        SubmitButton(validating = validating, enabled = canSubmit, onClick = ::submit)
        error?.let { ValidationError(it) }
        Spacer(Modifier.height(32.dp)) // espaço fixo: weight não funciona em coluna rolável
        // Domínio inteiro na 2.ª linha, como em app-01: sem a quebra explícita o iOS parte o host no hífen de "api-".
        Text(
            "O token nunca é enviado para fora do domínio\napi-casainteligente.intelbras.com.br.",
            Modifier.padding(top = 24.dp),
            color = MiboColors.TextSecondary,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun TokenField(
    token: String,
    onTokenChange: (String) -> Unit,
    visible: Boolean,
    onToggleVisible: () -> Unit,
    enabled: Boolean,
    isError: Boolean,
    prefilled: Boolean,
    onDone: () -> Unit,
) {
    OutlinedTextField(
        value = token,
        onValueChange = onTokenChange,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        label = { Text("Token de acesso") },
        trailingIcon = {
            IconButton(onClick = onToggleVisible) {
                Icon(
                    if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (visible) "Ocultar token" else "Mostrar token",
                )
            }
        },
        supportingText = if (prefilled) {
            { Text("Pré-preenchido pelo local.properties (desenvolvimento)") }
        } else {
            null
        },
        isError = isError,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        // Password também desliga sugestões e autocorreção do teclado: o token não vai para o dicionário.
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        singleLine = true,
    )
}

@Composable
private fun SubmitButton(validating: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        enabled = enabled,
        // Durante a validação o botão fica desabilitado, mas continua verde para o spinner branco aparecer.
        colors = if (validating) {
            ButtonDefaults.buttonColors(disabledContainerColor = MiboColors.Green, disabledContentColor = Color.White)
        } else {
            ButtonDefaults.buttonColors()
        },
    ) {
        if (validating) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                Text("Validando token…")
            }
        } else {
            Text("Entrar")
        }
    }
}

/** Mensagem amigável sempre; o detalhe técnico (HTTP, corpo da GDI) só em build de debug (RF04). */
@Composable
private fun ValidationError(error: AppError) {
    Text(
        error.userMessage,
        Modifier.padding(top = 16.dp),
        color = MaterialTheme.colorScheme.error,
        textAlign = TextAlign.Center,
    )
    val detail = error.detail
    if (isDebugBuild && detail != null) {
        Text(
            detail,
            Modifier.padding(top = 8.dp),
            color = MiboColors.TextSecondary,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
        )
    }
}
