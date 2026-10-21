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
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
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
import app.lumicast.ui.components.formatClockDigits
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.KeyboardType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val ROW_HEIGHT = 64.dp
private val ROW_GAP = 12.dp
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
    onDownloadQueue: () -> Unit,
    onClearQueue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var startAt by remember { mutableStateOf<Long?>(null) }
    var dragIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val rowPx = with(LocalDensity.current) { (ROW_HEIGHT + ROW_GAP).toPx() } // distância entre linhas = altura + espaço da Column
    val haptics = LocalHapticFeedback.current
    var picking by remember { mutableStateOf(false) }
    val hhmm = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(ROW_GAP),
    ) {
        TrimSettings(trimStart, trimEnd, onTrim)

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Fila (${queue.size})", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (queue.isNotEmpty()) {
                TextButton(onClick = onDownloadQueue) { Text("Baixar tudo") }
                TextButton(onClick = onClearQueue) { Text("Limpar") }
            }
        }
        if (queue.isEmpty()) {
            Text("Toque em um vídeo na Biblioteca e escolha “Adicionar à fila”.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }
        Text("Segure um vídeo e arraste para mudar a ordem.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        // Sem key(): cada linha é uma posição fixa; o gesto continua na linha onde o dedo começou
        // e o deslocamento é aplicado à posição para onde o vídeo já foi movido (dragIndex).
        queue.forEachIndexed { i, video ->
            val dragging = dragIndex == i
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(ROW_HEIGHT)
                    .zIndex(if (dragging) 1f else 0f)
                    .graphicsLayer { translationY = if (dragging) dragOffset else 0f }
                    .background(if (dragging) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent, MaterialTheme.shapes.medium)
                    .pointerInput(i, queue.size) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                dragIndex = i
                                dragOffset = 0f
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                val from = dragIndex ?: return@detectDragGesturesAfterLongPress
                                dragOffset += amount.y
                                val to = (from + (dragOffset / rowPx).roundToInt()).coerceIn(0, queue.lastIndex)
                                if (to != from) {
                                    onMove(from, to - from)
                                    dragOffset -= (to - from) * rowPx
                                    dragIndex = to
                                }
                            },
                            onDragEnd = { dragIndex = null; dragOffset = 0f },
                            onDragCancel = { dragIndex = null; dragOffset = 0f },
                        )
                    }
                    .semantics {
                        customActions = listOfNotNull(
                            CustomAccessibilityAction("Subir") { onMove(i, -1); true }.takeIf { i > 0 },
                            CustomAccessibilityAction("Descer") { onMove(i, 1); true }.takeIf { i < queue.lastIndex },
                        )
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.DragIndicator, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text("${i + 1}. ${video.title}", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(formatDuration(video.durationSec), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
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
    // Digita só números e o ":" aparece sozinho (o teclado numérico do celular não tem ":").
    fun field(sec: Int) = (if (sec > 0) formatDuration(sec) else "").let { TextFieldValue(it, TextRange(it.length)) }
    fun masked(v: TextFieldValue) = formatClockDigits(v.text).let { TextFieldValue(it, TextRange(it.length)) }
    var start by remember(trimStart) { mutableStateOf(field(trimStart)) }
    var end by remember(trimEnd) { mutableStateOf(field(trimEnd)) }
    val startSec = if (start.text.isBlank()) 0 else parseClock(start.text)
    val endSec = if (end.text.isBlank()) 0 else parseClock(end.text)
    val valid = startSec != null && endSec != null && (endSec == 0 || endSec > startSec)
    val changed = valid && (startSec != trimStart || endSec != trimEnd)

    Text("Começo e fim de cada vídeo", style = MaterialTheme.typography.titleMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            start, { start = masked(it) }, Modifier.weight(1f), singleLine = true,
            label = { Text("Começar em") }, placeholder = { Text("1:30") }, supportingText = { Text("min:seg") }, isError = startSec == null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        OutlinedTextField(
            end, { end = masked(it) }, Modifier.weight(1f), singleLine = true,
            label = { Text("Terminar em") }, placeholder = { Text("14:50") }, supportingText = { Text("min:seg") }, isError = endSec == null || !valid,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
    }
    Text(
        when {
            !valid -> "Segundos vão até 59, e o fim precisa ser depois do começo. Ex.: 130 = 1:30, 1450 = 14:50."
            trimEnd > 0 -> "Ao chegar no fim, passa para o próximo da fila. Deixe em branco para o vídeo inteiro."
            else -> "Deixe em branco para o vídeo inteiro."
        },
        style = MaterialTheme.typography.bodySmall,
        color = if (valid) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
    )
    if (changed) Button(onClick = { onTrim(startSec!!, endSec!!) }) { Text("Salvar") }
}
