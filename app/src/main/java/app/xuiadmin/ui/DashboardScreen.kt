package app.xuiadmin.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
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
import app.xuiadmin.data.LinkBuilder
import app.xuiadmin.data.ServerStatus
import kotlin.math.roundToInt

private val CpuColors = listOf(Color(0xFF38BDF8), Color(0xFF8B5CF6))
private val RamColors = listOf(Color(0xFFFBBF24), Color(0xFFF97316))
private val DiskColors = listOf(Color(0xFF22D3EE), Color(0xFF3B82F6))

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
    val rawUrl = state.saved?.baseUrl.orEmpty()
    val host = LinkBuilder.hostOf(rawUrl).ifBlank { rawUrl }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, bars.calculateTopPadding() + 8.dp, 16.dp, bars.calculateBottomPadding() + 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Xui Admin", color = GlassColors.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text("Панель управления", color = GlassColors.TextDim, fontSize = 13.sp)
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

        state.status?.let { s ->
            item { HeroCard(host, s, state.cpuHistory) }

            item {
                Row(
                    Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MetricCard(
                        title = "CPU",
                        fraction = (s.cpu / 100.0).toFloat(),
                        valueText = "${s.cpu.roundToInt()}%",
                        sub = "нагрузка процессора",
                        colors = CpuColors,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                    val ram = frac(s.memCurrent, s.memTotal)
                    MetricCard(
                        title = "RAM",
                        fraction = ram,
                        valueText = "${(ram * 100).roundToInt()}%",
                        sub = "${formatBytes(s.memCurrent)} / ${formatBytes(s.memTotal)}",
                        colors = RamColors,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }

            item {
                Row(
                    Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val disk = frac(s.diskCurrent, s.diskTotal)
                    MetricCard(
                        title = "Диск",
                        fraction = disk,
                        valueText = "${(disk * 100).roundToInt()}%",
                        sub = "${formatBytes(s.diskCurrent)} / ${formatBytes(s.diskTotal)}",
                        colors = DiskColors,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                    NetworkCard(s, Modifier.weight(1f).fillMaxHeight())
                }
            }

            item { XrayCard(s, onRestartXray) }
        }

        item { InboundsCard(state.inbounds, state.loading, onOpen, onToggleInbound) }

        if (state.inbounds.isNotEmpty()) {
            item { TrafficCard(state.inbounds) }
        }
    }
}

private fun frac(cur: Long, total: Long): Float = if (total <= 0) 0f else cur.toFloat() / total.toFloat()

private fun xrayLabel(state: String): Pair<String, Color> = when (state) {
    "running" -> "Xray работает" to GlassColors.Green
    "stop" -> "Xray остановлен" to GlassColors.Orange
    else -> "Xray: ошибка" to GlassColors.Red
}

@Composable
private fun HeroCard(host: String, s: ServerStatus, history: List<Float>) {
    val (label, color) = xrayLabel(s.xrayState)
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text("Сервер", color = GlassColors.TextDim, fontSize = 13.sp)
                Text(host, color = GlassColors.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                VSpace(6)
                Text(
                    "Аптайм ${formatUptime(s.uptime)} · TCP ${s.tcp} · UDP ${s.udp}",
                    color = GlassColors.TextDim, fontSize = 13.sp,
                )
            }
            Chip(label, color)
        }
        VSpace(10)
        val points = if (history.size >= 2) history else listOf(s.cpu.toFloat(), s.cpu.toFloat())
        GlowLine(points, Color(0xFF3B82F6), Modifier.fillMaxWidth().height(72.dp))
        Text("Нагрузка CPU за последние минуты", color = GlassColors.TextDim, fontSize = 11.sp)
    }
}

@Composable
private fun MetricCard(
    title: String,
    fraction: Float,
    valueText: String,
    sub: String,
    colors: List<Color>,
    modifier: Modifier = Modifier,
) {
    GlassCard(modifier) {
        Text(title, color = GlassColors.TextDim, fontSize = 13.sp)
        VSpace(10)
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            RingGauge(fraction, colors, Modifier.size(100.dp), stroke = 9.dp) {
                Text(valueText, color = GlassColors.Text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        VSpace(10)
        Text(sub, color = GlassColors.TextDim, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun NetworkCard(s: ServerStatus, modifier: Modifier = Modifier) {
    GlassCard(modifier) {
        Text("Сеть", color = GlassColors.TextDim, fontSize = 13.sp)
        VSpace(10)
        Text("↑ ${formatBytes(s.netUp)}/с", color = GlassColors.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        VSpace(4)
        Text("↓ ${formatBytes(s.netDown)}/с", color = GlassColors.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        VSpace(14)
        GlassDivider()
        VSpace(10)
        Text("Соединения", color = GlassColors.TextDim, fontSize = 12.sp)
        Text("TCP ${s.tcp} · UDP ${s.udp}", color = GlassColors.Text, fontSize = 15.sp, maxLines = 1)
    }
}

@Composable
private fun XrayCard(s: ServerStatus, onRestart: () -> Unit) {
    val (label, color) = xrayLabel(s.xrayState)
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(color)
            HSpace(10)
            Column(Modifier.weight(1f)) {
                Text(label, color = GlassColors.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(s.xrayVersion.ifBlank { "версия неизвестна" }, color = GlassColors.TextDim, fontSize = 12.sp)
            }
            GlassButton("Перезапуск", onRestart, tint = GlassColors.Orange, small = true)
        }
    }
}

@Composable
private fun InboundsCard(
    list: List<Inbound>,
    loading: Boolean,
    onOpen: (Int) -> Unit,
    onToggle: (Inbound, Boolean) -> Unit,
) {
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Inbound’ы", color = GlassColors.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("${list.size}", color = GlassColors.TextDim, fontSize = 14.sp)
        }
        VSpace(4)
        if (list.isEmpty() && !loading) {
            VSpace(8)
            Text("Inbound’ов пока нет", color = GlassColors.TextDim, fontSize = 14.sp)
        }
        list.forEachIndexed { i, ib ->
            if (i > 0) GlassDivider()
            Row(
                Modifier.fillMaxWidth().clickable { onOpen(ib.id) }.padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusDot(if (ib.enable) GlassColors.Green else LocalGlass.current.offDot)
                HSpace(12)
                Column(Modifier.weight(1f)) {
                    Text(
                        ib.remark.ifBlank { "Inbound ${ib.id}" },
                        color = GlassColors.Text, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1,
                    )
                    Text(
                        "${ib.protocol.uppercase()} · порт ${ib.port} · клиентов ${ib.clients.size}",
                        color = GlassColors.TextDim, fontSize = 12.sp, maxLines = 1,
                    )
                }
                HSpace(8)
                Text(formatBytes(ib.up + ib.down), color = GlassColors.TextDim, fontSize = 12.sp, maxLines = 1)
                HSpace(6)
                GlassSwitch(ib.enable) { onToggle(ib, it) }
            }
        }
    }
}

@Composable
private fun TrafficCard(list: List<Inbound>) {
    val items = list
        .sortedByDescending { it.up + it.down }
        .take(7)
        .map { it.remark.ifBlank { "#${it.id}" } to (it.up + it.down) }
    val total = list.sumOf { it.up + it.down }
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Трафик по inbound’ам", color = GlassColors.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("всего ${formatBytes(total)}", color = GlassColors.TextDim, fontSize = 12.sp)
        }
        VSpace(14)
        BarsChart(items)
    }
}
