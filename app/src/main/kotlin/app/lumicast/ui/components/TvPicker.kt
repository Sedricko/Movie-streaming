package app.lumicast.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.lumicast.domain.CastDevice

/** Lista de TVs da rede para escolher uma. Usada na aba TV e, se ainda não há TV, ao transmitir. */
@Composable
fun TvPicker(
    devices: List<CastDevice>,
    selectedId: String?,
    onPick: (CastDevice) -> Unit,
    onRescan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (devices.isEmpty()) {
            Text(
                "Procurando TVs… Confira se o celular e a TV estão no mesmo Wi-Fi.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        devices.forEach { d ->
            DeviceRow(device = d, active = d.id == selectedId, actionLabel = if (d.id == selectedId) "Escolhida" else "Escolher", onClick = { onPick(d) })
        }
        OutlinedButton(onClick = onRescan) { Text("Procurar de novo") }
    }
}
