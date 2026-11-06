package app.lumicast.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.lumicast.domain.AppSettings
import app.lumicast.domain.StorageInfo
import app.lumicast.domain.ThemeMode
import app.lumicast.ui.theme.LumiShapes

/**
 * Configurações em cinco seções por contexto de uso, abertas uma de cada vez (sanfona): Reprodução, Transmissão,
 * Downloads e espaço, Aparência, Conta e ajuda. Cada seção mostra um resumo do que tem dentro, fechada.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    settings: AppSettings,
    onChange: (AppSettings) -> Unit,
    storage: StorageInfo?,
    onClearTelegram: () -> Unit,
    onClearConverted: () -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenMonitor: () -> Unit,
    onDismiss: () -> Unit,
) {
    var open by remember { mutableStateOf<Section?>(Section.Playback) }
    fun toggle(s: Section) { open = if (open == s) null else s }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Configurações", style = MaterialTheme.typography.titleLarge)

            SettingsSection("Reprodução", "${listOf(settings.resume, settings.autoNext, settings.autoConvert).count { it }} de 3 ligadas", open == Section.Playback, { toggle(Section.Playback) }) {
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
                    "Converter formato automaticamente",
                    "Se a TV recusar o vídeo (ex.: MKV ou HEVC), o celular converte para MP4 e transmite. Leva alguns minutos; o convertido fica guardado.",
                    settings.autoConvert,
                ) { onChange(settings.copy(autoConvert = it)) }
            }

            SettingsSection("Transmissão", "${listOf(settings.dlna, settings.volumeKeys, settings.showSplash).count { it }} de 3 ligadas", open == Section.Cast, { toggle(Section.Cast) }) {
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
                    "Mostrar o logo na TV ao conectar",
                    "Ao escolher a TV, ela mostra “Lumicast conectado” até você mandar um vídeo, e volta a mostrar quando a fila acaba.",
                    settings.showSplash,
                ) { onChange(settings.copy(showSplash = it)) }
            }

            SettingsSection(
                "Downloads e espaço",
                if (settings.cacheLimitGb == 0) "sem limite de cache" else "cache até ${settings.cacheLimitGb} GB",
                open == Section.Storage,
                { toggle(Section.Storage) },
            ) {
                SettingSwitch(
                    "Baixar a fila adiante",
                    "Com a fila tocando, o celular baixa os próximos vídeos na ordem da fila, até o último, e salva em Downloads/Lumicast/Fila. Usa espaço e dados.",
                    settings.prefetch,
                ) { onChange(settings.copy(prefetch = it)) }
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

            SettingsSection("Aparência", when (settings.theme) { ThemeMode.System -> "do sistema"; ThemeMode.Light -> "claro"; ThemeMode.Dark -> "escuro" }, open == Section.Look, { toggle(Section.Look) }) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(ThemeMode.System to "Do sistema", ThemeMode.Light to "Claro", ThemeMode.Dark to "Escuro").forEach { (mode, label) ->
                        FilterChip(selected = settings.theme == mode, onClick = { onChange(settings.copy(theme = mode)) }, label = { Text(label) })
                    }
                }
            }

            SettingsSection("Conta e ajuda", "contas, permissões, monitoramento", open == Section.Account, { toggle(Section.Account) }) {
                SettingLink("Contas", "Trocar ou adicionar uma conta do Telegram", onOpenAccounts)
                SettingLink("Permissões", "Notificações, dispositivos próximos e bateria", onOpenPermissions)
                SettingLink("Monitoramento", "Ver medidas e enviar o diagnóstico", onOpenMonitor)
            }
        }
    }
}

private enum class Section { Playback, Cast, Storage, Look, Account }

/** Seção que abre e fecha; fechada, mostra só o título e um resumo. */
@Composable
private fun SettingsSection(title: String, summary: String, expanded: Boolean, onToggle: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(LumiShapes.Card).background(MaterialTheme.colorScheme.surface)) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (!expanded) Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = if (expanded) "Fechar $title" else "Abrir $title")
        }
        if (expanded) {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
        }
    }
}

@Composable
private fun SettingLink(title: String, description: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
