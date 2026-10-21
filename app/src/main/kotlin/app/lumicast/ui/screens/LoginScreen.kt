package app.lumicast.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.auth.api.identity.GetPhoneNumberHintIntentRequest
import com.google.android.gms.auth.api.identity.Identity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.lumicast.domain.Account
import app.lumicast.domain.AuthState
import app.lumicast.ui.theme.LumiTheme

/** Um passo por tela: telefone → código → senha (se existir). */
@Composable
fun LoginScreen(
    state: AuthState,
    onPhone: (String) -> Unit,
    onCode: (String) -> Unit,
    onResendCode: () -> Unit,
    onPassword: (String) -> Unit,
    /** Outras contas já usadas neste celular: dá para voltar a elas sem entrar nesta. */
    otherAccounts: List<Account> = emptyList(),
    onSwitchAccount: (String) -> Unit = {},
) {
    // "Mudar número" volta para a tela do telefone; o TDLib aceita outro número mesmo esperando o código.
    var editingPhone by rememberSaveable { mutableStateOf(false) }
    var phone by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Lumicast", style = MaterialTheme.typography.displayMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Seus vídeos do Telegram, na TV.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(40.dp))

        when (val shown = if (editingPhone && state is AuthState.WaitCode) AuthState.WaitPhone else state) {
            AuthState.Loading -> CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)

            AuthState.WaitPhone, is AuthState.Failed -> {
                if (shown is AuthState.Failed) {
                    Text(shown.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                }
                PhoneNumberHint(alreadyTyped = phone.isNotEmpty()) { phone = it }
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Telefone com DDD") },
                    placeholder = { Text("+55 11 91234-5678") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = { editingPhone = false; onPhone(phone) }, enabled = phone.length >= 8, modifier = Modifier.fillMaxWidth()) {
                    Text("Entrar com o Telegram")
                }
            }

            is AuthState.WaitCode -> {
                Text(
                    "Digite o código que chegou no app do Telegram.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = LumiTheme.extras.fonte,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("Código") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = { onCode(code) }, enabled = code.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                    Text("Confirmar código")
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Enviado para ${shown.phone}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { code = ""; onResendCode() }) { Text("Reenviar código") }
                    TextButton(onClick = { code = ""; editingPhone = true }) { Text("Mudar número") }
                }
            }

            AuthState.WaitPassword -> {
                Text("Sua conta tem senha em duas etapas.", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Senha") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = { onPassword(password) }, enabled = password.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                    Text("Entrar")
                }
            }

            is AuthState.Ready -> Unit
        }
        otherAccounts.filter { it.name.isNotBlank() }.forEach { a ->
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { onSwitchAccount(a.id) }) { Text("Voltar para a conta de ${a.name}") }
        }
    }
}

/** Sugere o número do chip (Phone Number Hint do Google): o usuário toca no número e ele já vem preenchido. */
@Composable
private fun PhoneNumberHint(alreadyTyped: Boolean, onNumber: (String) -> Unit) {
    val activity = LocalContext.current as? Activity ?: return
    val client = remember { Identity.getSignInClient(activity) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        runCatching { client.getPhoneNumberFromIntent(result.data) }.onSuccess(onNumber)
    }
    LaunchedEffect(Unit) {
        if (alreadyTyped) return@LaunchedEffect
        client.getPhoneNumberHintIntent(GetPhoneNumberHintIntentRequest.builder().build())
            .addOnSuccessListener { launcher.launch(IntentSenderRequest.Builder(it).build()) } // sem chip/Play Services: segue digitando
    }
}
