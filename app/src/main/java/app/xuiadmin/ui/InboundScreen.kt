package app.xuiadmin.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.xuiadmin.data.ClientInfo
import app.xuiadmin.data.Inbound

@Composable
fun InboundScreen(
    ib: Inbound?,
    onlines: Set<String>,
    onBack: () -> Unit,
    onToggleInbound: (Inbound, Boolean) -> Unit,
    onToggleClient: (Inbound, ClientInfo, Boolean) -> Unit,
    onResetClient: (Inbound, ClientInfo) -> Unit,
) {
    if (ib == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val bars = WindowInsets.systemBars.asPaddingValues()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, bars.calculateTopPadding() + 8.dp, 16.dp, bars.calculateBottomPadding() + 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Назад", onBack)
                HSpace(12)
                Column(Modifier.weight(1f)) {
                    Text(
                        ib.remark.ifBlank { "Inbound ${ib.id}" },
                        color = GlassColors.Text, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                    )
                    Text("${ib.protocol.uppercase()} · порт ${ib.port}", color = GlassColors.TextDim, fontSize = 13.sp)
                }
                GlassSwitch(ib.enable) { onToggleInbound(ib, it) }
            }
        }

        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Трафик inbound", color = GlassColors.TextDim, fontSize = 13.sp)
                Text(
                    "↑ ${formatBytes(ib.up)}   ↓ ${formatBytes(ib.down)}",
                    color = GlassColors.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                )
                VSpace(4)
                Text("Лимит: ${formatTotal(ib.total)} · до ${formatExpiry(ib.expiryTime)}", color = GlassColors.TextDim, fontSize = 13.sp)
            }
        }

        item {
            Text("Клиенты (${ib.clients.size})", color = GlassColors.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }

        items(ib.clients, key = { it.key + it.email }) { c ->
            ClientCard(
                c = c,
                online = c.email in onlines,
                onToggle = { onToggleClient(ib, c, it) },
                onReset = { onResetClient(ib, c) },
            )
        }
    }
}

@Composable
private fun ClientCard(c: ClientInfo, online: Boolean, onToggle: (Boolean) -> Unit, onReset: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(if (online) GlassColors.Green else Color.White.copy(alpha = 0.3f))
            HSpace(10)
            Column(Modifier.weight(1f)) {
                Text(
                    c.email.ifBlank { "(без email)" },
                    color = GlassColors.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                )
                Text(if (online) "онлайн" else "офлайн", color = GlassColors.TextDim, fontSize = 12.sp)
            }
            GlassSwitch(c.enable, onToggle)
        }
        VSpace(10)
        val used = c.up + c.down
        Text(
            "↑ ${formatBytes(c.up)}   ↓ ${formatBytes(c.down)}   из ${formatTotal(c.total)}",
            color = GlassColors.TextDim, fontSize = 13.sp,
        )
        if (c.total > 0) {
            VSpace(6)
            val f = used.toFloat() / c.total.toFloat()
            GlassProgress(f, if (f > 0.9f) GlassColors.Red else GlassColors.Blue)
        }
        VSpace(6)
        Text("До: ${formatExpiry(c.expiryTime)}", color = GlassColors.TextDim, fontSize = 13.sp)
        VSpace(10)
        GlassButton("Сбросить трафик", onReset, tint = GlassColors.Orange, small = true, modifier = Modifier.align(Alignment.End))
    }
}
