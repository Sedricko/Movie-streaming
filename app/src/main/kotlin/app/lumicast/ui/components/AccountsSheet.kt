package app.lumicast.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.lumicast.domain.Account

/** Contas do Telegram neste celular. Trocar reabre o app já na outra conta. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsSheet(accounts: List<Account>, currentId: String, onSwitch: (String) -> Unit, onAdd: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Contas", style = MaterialTheme.typography.titleLarge)
            Text(
                "Cada conta tem as próprias pastas, favoritos e progresso. Trocar reabre o app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            accounts.forEach { a ->
                val current = a.id == currentId
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = !current) { onSwitch(a.id) }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Icon(Icons.Rounded.AccountCircle, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(a.name.ifBlank { "Conta sem login" }, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    if (current) Icon(Icons.Rounded.Check, contentDescription = "Conta atual", tint = MaterialTheme.colorScheme.primary)
                }
            }
            OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) { Text("Adicionar conta") }
        }
    }
}
