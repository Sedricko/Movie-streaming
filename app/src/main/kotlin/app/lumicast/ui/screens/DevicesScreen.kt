package app.lumicast.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.lumicast.domain.CastDevice
import app.lumicast.ui.components.DeviceRow
import app.lumicast.ui.components.EmptyState

@Composable
fun DevicesScreen(
    devices: List<CastDevice>,
    activeDeviceId: String?,
    onRescan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("TVs na rede", style = MaterialTheme.typography.titleLarge)
        if (devices.isEmpty()) {
            EmptyState(
                title = "Nenhuma TV encontrada",
                message = "Confira se o celular e a TV estão no mesmo Wi-Fi.",
                action = { OutlinedButton(onClick = onRescan) { Text("Procurar de novo") } },
            )
        } else {
            devices.forEach { d ->
                DeviceRow(
                    device = d,
                    active = d.id == activeDeviceId,
                    actionLabel = if (d.id == activeDeviceId) "Transmitindo" else "Escolher",
                    onClick = { /* M3: definir TV preferida (DataStore) */ },
                )
            }
            OutlinedButton(onClick = onRescan) { Text("Procurar de novo") }
        }
    }
}
