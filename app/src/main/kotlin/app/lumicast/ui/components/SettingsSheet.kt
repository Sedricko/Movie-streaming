package app.lumicast.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.lumicast.domain.AppSettings
import app.lumicast.domain.StorageInfo
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.TextButton
import app.lumicast.domain.ThemeMode
import androidx.compose.material3.FilterChip

/** Configurações: um interruptor por recurso automático, com uma frase dizendo o que ele faz. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    settings: AppSettings,
    onChange: (AppSettings) -> Unit,
    storage: StorageInfo?,
    onClearTelegram: () -> Unit,
    onClearConverted: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Configurações", style = MaterialTheme.typography.titleLarge)
            SettingSwitch(
                "Continuar de onde parou",
                "Ao transmitir um vídeo já começado, volta ao ponto onde você parou.",
                settings.resume,
            ) { onChange(settings.copy(resume = it)) }
            SettingSwitch(
                "Próximo episódio automático",
                "Sem fila, ao terminar um vídeo toca o próximo do mesmo chat ou pasta, em ordem 1→9.",
                settings.autoNext,
            ) { onChange(settings.copy(autoNext = it)) }
            SettingSwitch(
                "Procurar TVs DLNA",
                "Além de Chromecast e Google TV, mostra Smart TVs Samsung, LG, Sony e outras que aceitam DLNA.",
                settings.dlna,
            ) { onChange(settings.copy(dlna = it)) }
            SettingSwitch(
                "Teclas de volume controlam a TV",
                "Com o app aberto e transmitindo, as teclas de volume do celular mudam o volume da TV.",
                settings.volumeKeys,
            ) { onChange(settings.copy(volumeKeys = it)) }
            SettingSwitch(
                "Converter formato automaticamente",
                "Se a TV recusar o vídeo (ex.: MKV ou HEVC), o celular converte para MP4 e transmite. Leva alguns minutos; o convertido fica guardado.",
                settings.autoConvert,
            ) { onChange(settings.copy(autoConvert = it)) }
            SettingSwitch(
                "Legendas automáticas",
                "Se houver um arquivo .srt ou .vtt no mesmo chat com o nome ou o número do episódio, ele aparece na TV.",
                settings.subtitles,
            ) { onChange(settings.copy(subtitles = it)) }
            SettingSwitch(
                "Mostrar o logo na TV ao conectar",
                "Ao escolher a TV, ela mostra “Lumicast conectado” até você mandar um vídeo, e volta a mostrar quando a fila acaba.",
                settings.showSplash,
            ) { onChange(settings.copy(showSplash = it)) }
            Column {
                Text("Tema", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(ThemeMode.System to "Do sistema", ThemeMode.Light to "Claro", ThemeMode.Dark to "Escuro").forEach { (mode, label) ->
                        FilterChip(selected = settings.theme == mode, onClick = { onChange(settings.copy(theme = mode)) }, label = { Text(label) })
                    }
                }
            }

            Text("Armazenamento", style = MaterialTheme.typography.titleLarge)
            StorageRow("Cache do Telegram", "Vídeos vistos ou transmitidos, guardados para não baixar de novo.", storage?.telegramBytes, "Limpar", onClearTelegram)
            StorageRow("Vídeos convertidos", "Versões MP4 feitas pelo conversor.", storage?.convertedBytes, "Apagar", onClearConverted)
            Column {
                Text("Limite do cache do Telegram", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Passou do limite, os vídeos mais antigos saem do cache ao parar de transmitir. Os salvos na galeria nunca são apagados.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to "Sem limite", 2 to "2 GB", 5 to "5 GB", 10 to "10 GB").forEach { (gb, label) ->
                        FilterChip(selected = settings.cacheLimitGb == gb, onClick = { onChange(settings.copy(cacheLimitGb = gb)) }, label = { Text(label) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingSwitch(title: String, description: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(checked, role = Role.Switch, onValueChange = onChecked),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null) // a linha inteira é o toque (acessível com TalkBack)
    }
}

@Composable
private fun StorageRow(title: String, description: String, bytes: Long?, action: String, onAction: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("$title · ${bytes?.let(::formatSize) ?: "calculando…"}", style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = onAction, enabled = (bytes ?: 0) > 0) { Text(action) }
    }
}
