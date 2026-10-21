package app.lumicast.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.lumicast.domain.CastDevice
import app.lumicast.domain.TgVideo
import app.lumicast.ui.components.formatDuration
import app.lumicast.ui.components.parseClock
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.KeyboardType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val SWITCH_OPTIONS = listOf(0 to "Ao terminar", 5 to "5 min", 10 to "10 min", 30 to "30 min")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(
    queue: List<TgVideo>,
    devices: List<CastDevice>,
    scheduledAt: Long?,
    switchEveryMin: Int,
    trimStart: Int,
    trimEnd: Int,
    onTrim: (startSec: Int, endSec: Int) -> Unit,
    onMove: (index: Int, delta: Int) -> Unit,
    onRemove: (Int) -> Unit,
    onSwitchEvery: (Int) -> Unit,
    onStart: (CastDevice, atMillis: Long?) -> Unit,
    onCancelSchedule: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var startAt by remember { mutableStateOf<Long?>(null) }
    var picking by remember { mutableStateOf(false) }
    val hhmm = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TrimSettings(trimStart, trimEnd, onTrim)

        Text("Fila (${queue.size})", style = MaterialTheme.typography.titleLarge)
        if (queue.isEmpty()) {
            Text("Toque em um vídeo na Biblioteca e escolha “Adicionar à fila”.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }
        queue.forEachIndexed { i, video ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${i + 1}. ${video.title}", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(formatDuration(video.durationSec), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { onMove(i, -1) }, enabled = i > 0) { Icon(Icons.Rounded.KeyboardArrowUp, "Subir ${video.title}") }
                IconButton(onClick = { onMove(i, 1) }, enabled = i < queue.lastIndex) { Icon(Icons.Rounded.KeyboardArrowDown, "Descer ${video.title}") }
                IconButton(onClick = { onRemove(i) }) { Icon(Icons.Rounded.Close, "Tirar ${video.title} da fila") }
            }
        }

        Text("Trocar para o próximo vídeo", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SWITCH_OPTIONS.forEach { (min, label) ->
                FilterChip(selected = switchEveryMin == min, onClick = { onSwitchEvery(min) }, label = { Text(label) })
            }
        }

        if (scheduledAt != null) {
            Text("A fila começa às ${hhmm.format(Date(scheduledAt))}.", style = MaterialTheme.typography.bodyLarge)
            OutlinedButton(onClick = onCancelSchedule) { Text("Cancelar horário") }
        } else {
            Text("Quando começar", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = startAt == null, onClick = { startAt = null }, label = { Text("Agora") })
                FilterChip(
                    selected = startAt != null,
                    onClick = { picking = true },
                    label = { Text(startAt?.let { "Às ${hhmm.format(Date(it))}" } ?: "Escolher horário") },
                )
            }
            if (devices.isEmpty()) {
                Text("Nenhuma TV encontrada. Confira o Wi-Fi.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            devices.forEach { d ->
                Button(onClick = { onStart(d, startAt) }, modifier = Modifier.fillMaxWidth()) { Text("Iniciar fila na ${d.name}") }
            }
        }
    }

    if (picking) {
        val now = Calendar.getInstance()
        val state = rememberTimePickerState(now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), is24Hour = true)
        AlertDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    val at = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, state.hour); set(Calendar.MINUTE, state.minute); set(Calendar.SECOND, 0)
                        if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_MONTH, 1) // horário que já passou = amanhã
                    }
                    startAt = at.timeInMillis
                    picking = false
                }) { Text("Confirmar") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancelar") } },
            text = { TimePicker(state) },
        )
    }
}

/** "Começar em" / "Terminar em": vale para todos os vídeos transmitidos (ex.: pular abertura e créditos). */
@Composable
private fun TrimSettings(trimStart: Int, trimEnd: Int, onTrim: (Int, Int) -> Unit) {
    var start by remember(trimStart) { mutableStateOf(if (trimStart > 0) formatDuration(trimStart) else "") }
    var end by remember(trimEnd) { mutableStateOf(if (trimEnd > 0) formatDuration(trimEnd) else "") }
    val startSec = if (start.isBlank()) 0 else parseClock(start)
    val endSec = if (end.isBlank()) 0 else parseClock(end)
    val valid = startSec != null && endSec != null && (endSec == 0 || endSec > startSec)
    val changed = valid && (startSec != trimStart || endSec != trimEnd)

    Text("Começo e fim de cada vídeo", style = MaterialTheme.typography.titleMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            start, { start = it }, Modifier.weight(1f), singleLine = true,
            label = { Text("Começar em") }, placeholder = { Text("1:30") }, isError = startSec == null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        OutlinedTextField(
            end, { end = it }, Modifier.weight(1f), singleLine = true,
            label = { Text("Terminar em") }, placeholder = { Text("14:50") }, isError = endSec == null || !valid,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
    }
    Text(
        when {
            !valid -> "Use minutos:segundos (ex.: 1:30). O fim precisa ser depois do começo."
            trimEnd > 0 -> "Ao chegar no fim, passa para o próximo da fila. Deixe em branco para o vídeo inteiro."
            else -> "Deixe em branco para o vídeo inteiro."
        },
        style = MaterialTheme.typography.bodySmall,
        color = if (valid) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
    )
    if (changed) Button(onClick = { onTrim(startSec!!, endSec!!) }) { Text("Salvar") }
}
