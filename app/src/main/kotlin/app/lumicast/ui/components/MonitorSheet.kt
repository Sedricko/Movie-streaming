package app.lumicast.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.lumicast.Diag
import app.lumicast.Health
import app.lumicast.Level
import app.lumicast.ui.theme.LumiShapes
import app.lumicast.ui.theme.LumiTheme
import kotlinx.coroutines.delay

/** Leituras da sessão com estado (Boa/Atenção/Ruim), pistas de causa e envio do relatório completo. Tudo local. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorSheet(onShare: () -> Unit, onDismiss: () -> Unit) {
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(1_000); tick++ } } // números ao vivo enquanto a folha está aberta
    val health = remember(tick) { Diag.health() }
    val hints = remember(tick) { Diag.hints() }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Monitoramento", style = MaterialTheme.typography.titleLarge)
            Text(
                "Medidas desde que o app abriu. Nada sai do celular sem você enviar.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            health.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { HealthCard(it, Modifier.weight(1f)) }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            if (hints.isNotEmpty()) {
                Text("Pistas", style = MaterialTheme.typography.titleMedium)
                hints.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
            }
            Text("Todas as medidas", style = MaterialTheme.typography.titleMedium)
            Text(
                Diag.statsText().ifBlank { "Nada medido ainda. Transmita um vídeo e volte aqui." },
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onShare, modifier = Modifier.fillMaxWidth()) { Text("Enviar diagnóstico completo") }
        }
    }
}

@Composable
private fun HealthCard(h: Health, modifier: Modifier = Modifier) {
    val tone: Color = when (h.level) {
        Level.Ok -> LumiTheme.extras.ok
        Level.Warn -> MaterialTheme.colorScheme.primary
        Level.Bad -> MaterialTheme.colorScheme.error
        Level.None -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val word = when (h.level) { Level.Ok -> "Boa"; Level.Warn -> "Atenção"; Level.Bad -> "Ruim"; Level.None -> "Sem dados" }
    Column(
        modifier.clip(LumiShapes.Card).background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(h.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(h.value, style = MaterialTheme.typography.titleLarge)
        Text(
            word,
            style = MaterialTheme.typography.labelMedium,
            color = tone,
            modifier = Modifier.padding(top = 2.dp).clip(CircleShape).background(tone.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 1.dp),
        )
    }
}
