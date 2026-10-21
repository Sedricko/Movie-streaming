package app.lumicast.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.lumicast.domain.Transfer
import app.lumicast.ui.theme.LumiShapes

/** Download ou conversão em andamento. Fica acima das abas, visível em qualquer tela. */
@Composable
fun TransferBar(transfer: Transfer, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(LumiShapes.Card)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                val pct = transfer.fraction?.let { " · ${(it * 100).toInt()}%" }.orEmpty()
                Text("${transfer.title}$pct", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(transfer.detail, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            TextButton(onClick = onCancel) { Text("Cancelar") }
        }
        val bar = Modifier.fillMaxWidth().padding(end = 12.dp, top = 4.dp)
        // Sem porcentagem ainda (ex.: conversão começando): barra animada.
        transfer.fraction?.let { f -> LinearProgressIndicator(progress = { f }, modifier = bar) } ?: LinearProgressIndicator(modifier = bar)
    }
}
