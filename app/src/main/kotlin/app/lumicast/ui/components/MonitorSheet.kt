package app.lumicast.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.lumicast.Diag

/** Métricas da sessão (gargalos, erros, vazão) e envio do relatório completo. Tudo local. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorSheet(onShare: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Monitoramento", style = MaterialTheme.typography.titleLarge)
            Text(
                "Medidas desde que o app abriu. Espera alta no servidor = download lento; respostas interrompidas = TV fechou a conexão.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                Diag.statsText().ifBlank { "Nada medido ainda. Transmita um vídeo e volte aqui." },
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
            )
            Button(onClick = onShare, modifier = Modifier.fillMaxWidth()) { Text("Enviar diagnóstico") }
        }
    }
}
