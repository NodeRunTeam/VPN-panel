package app.xuiadmin.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.xuiadmin.UiState
import app.xuiadmin.data.Inbound
import app.xuiadmin.data.ServerStatus
import kotlin.math.roundToInt

@Composable
fun DashboardScreen(
    state: UiState,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
    onOpen: (Int) -> Unit,
    onToggleInbound: (Inbound, Boolean) -> Unit,
    onRestartXray: () -> Unit,
    onSettings: () -> Unit,
) {
    val bars = WindowInsets.systemBars.asPaddingValues()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, bars.calculateTopPadding() + 8.dp, 16.dp, bars.calculateBottomPadding() + 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Xui Admin", color = GlassColors.Text, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Text(state.saved?.baseUrl.orEmpty(), color = GlassColors.TextDim, fontSize = 12.sp, maxLines = 1)
                }
                if (state.loading) {
                    CircularProgressIndicator(Modifier.size(22.dp), color = GlassColors.Text, strokeWidth = 2.dp)
                    HSpace(12)
                }
                GlassIconButton(Icons.Default.Refresh, "Обновить", onRefresh)
                HSpace(8)
                GlassIconButton(Icons.Default.Settings, "Настройки", onSettings)
                HSpace(8)
                GlassIconButton(Icons.AutoMirrored.Filled.ExitToApp, "Выйти", onLogout)
            }
        }

        state.error?.let { err ->
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text(err, color = GlassColors.Red, fontSize = 14.sp)
                }
            }
        }

        state.status?.let { s -> item { StatusCard(s, onRestartXray) } }

        item {
            Text("Inbound’ы", color = GlassColors.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }

        items(state.inbounds, key = { it.id }) { ib ->
            InboundCard(ib, onOpen, onToggleInbound)
        }

        if (state.inbounds.isEmpty() && !state.loading) {
            item { Text("Inbound’ов пока нет", color = GlassColors.TextDim) }
        }
    }
}

@Composable
private fun StatusCard(s: ServerStatus, onRestart: () -> Unit) {
    val (label, color) = when (s.xrayState) {
        "running" -> "Xray работает" to GlassColors.Green
        "stop" -> "Xray остановлен" to GlassColors.Orange
        else -> "Xray: ошибка" to GlassColors.Red
    }
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(color)
            HSpace(8)
            Text(label, color = GlassColors.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(s.xrayVersion, color = GlassColors.TextDim, fontSize = 13.sp)
        }
        VSpace(14)
        Meter("CPU", (s.cpu / 100.0).toFloat(), "${s.cpu.roundToInt()}%", GlassColors.Blue)
        VSpace(10)
        Meter("RAM", frac(s.memCurrent, s.memTotal), "${formatBytes(s.memCurrent)} / ${formatBytes(s.memTotal)}", GlassColors.Purple)
        VSpace(10)
        Meter("Диск", frac(s.diskCurrent, s.diskTotal), "${formatBytes(s.diskCurrent)} / ${formatBytes(s.diskTotal)}", GlassColors.Orange)
        VSpace(14)
        Text("Сеть: ↑ ${formatBytes(s.netUp)}/с   ↓ ${formatBytes(s.netDown)}/с", color = GlassColors.TextDim, fontSize = 13.sp)
        Text("Аптайм ${formatUptime(s.uptime)} · TCP ${s.tcp} · UDP ${s.udp}", color = GlassColors.TextDim, fontSize = 13.sp)
        VSpace(14)
        GlassButton("Перезапустить Xray", onRestart, tint = GlassColors.Orange, small = true)
    }
}

private fun frac(cur: Long, total: Long): Float = if (total <= 0) 0f else cur.toFloat() / total.toFloat()

@Composable
private fun Meter(label: String, fraction: Float, value: String, color: Color) {
    Column {
        Row {
            Text(label, color = GlassColors.Text, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text(value, color = GlassColors.TextDim, fontSize = 13.sp)
        }
        VSpace(6)
        GlassProgress(fraction, color)
    }
}

@Composable
private fun InboundCard(ib: Inbound, onOpen: (Int) -> Unit, onToggle: (Inbound, Boolean) -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), onClick = { onOpen(ib.id) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    ib.remark.ifBlank { "Inbound ${ib.id}" },
                    color = GlassColors.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                )
                Text("${ib.protocol.uppercase()} · порт ${ib.port}", color = GlassColors.TextDim, fontSize = 13.sp)
            }
            GlassSwitch(ib.enable) { onToggle(ib, it) }
        }
        VSpace(10)
        Text(
            "↑ ${formatBytes(ib.up)}   ↓ ${formatBytes(ib.down)}   из ${formatTotal(ib.total)}",
            color = GlassColors.TextDim, fontSize = 13.sp,
        )
        Text(
            "Клиентов: ${ib.clients.size} · до ${formatExpiry(ib.expiryTime)}",
            color = GlassColors.TextDim, fontSize = 13.sp,
        )
    }
}
