package app.xuiadmin.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.xuiadmin.ThemeMode
import app.xuiadmin.UiState
import app.xuiadmin.data.ClientInfo
import app.xuiadmin.data.Inbound
import app.xuiadmin.data.LinkBuilder

@Composable
fun ConfirmDialog(title: String, text: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    GlassDialog(onDismiss) {
        Text(title, color = GlassColors.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        VSpace(8)
        Text(text, color = GlassColors.TextDim, fontSize = 15.sp)
        VSpace(18)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassButton("Отмена", onDismiss, Modifier.weight(1f), tint = GlassColors.Slate)
            GlassButton("Да", onConfirm, Modifier.weight(1f))
        }
    }
}

@Composable
private fun SwitchRow(title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            Text(title, color = GlassColors.Text, fontSize = 16.sp)
            Text(hint, color = GlassColors.TextDim, fontSize = 12.sp)
        }
        GlassSwitch(checked, onChange)
    }
}

@Composable
fun SettingsDialog(
    state: UiState,
    onTheme: (ThemeMode) -> Unit,
    onBlur: (Boolean) -> Unit,
    onAnimate: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    GlassDialog(onDismiss) {
        Text("Настройки", color = GlassColors.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        VSpace(14)
        Text("Тема", color = GlassColors.TextDim, fontSize = 13.sp)
        VSpace(8)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                ThemeMode.System to "Системная",
                ThemeMode.Light to "Светлая",
                ThemeMode.Dark to "Тёмная",
            ).forEach { (mode, label) ->
                GlassButton(
                    text = label,
                    onClick = { onTheme(mode) },
                    modifier = Modifier.weight(1f),
                    tint = if (state.themeMode == mode) GlassColors.Blue else GlassColors.Slate,
                    small = true,
                )
            }
        }
        VSpace(18)
        SwitchRow("Размытие стекла", "Android 12 и новее", state.blur, onBlur)
        VSpace(12)
        SwitchRow("Анимация фона", "Выключите, чтобы экономить заряд", state.animateBg, onAnimate)
        VSpace(20)
        GlassButton("Готово", onDismiss, Modifier.fillMaxWidth())
    }
}

@Composable
fun AddClientDialog(onDismiss: () -> Unit, onCreate: (email: String, gb: Double, days: Int) -> Unit) {
    var email by remember { mutableStateOf("user-" + (1000..9999).random()) }
    var gbText by remember { mutableStateOf("0") }
    var daysText by remember { mutableStateOf("0") }
    GlassDialog(onDismiss) {
        Text("Новый клиент", color = GlassColors.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("0 — без лимита", color = GlassColors.TextDim, fontSize = 13.sp)
        VSpace(14)
        GlassField(email, { email = it.trim() }, "Email / имя клиента")
        VSpace(10)
        GlassField(gbText, { gbText = it }, "Лимит трафика, ГБ", keyboardType = KeyboardType.Decimal)
        VSpace(10)
        GlassField(daysText, { daysText = it.filter(Char::isDigit) }, "Срок, дней", keyboardType = KeyboardType.Number)
        VSpace(18)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassButton("Отмена", onDismiss, Modifier.weight(1f), tint = GlassColors.Slate)
            GlassButton(
                text = "Создать",
                onClick = {
                    onCreate(
                        email,
                        gbText.replace(',', '.').toDoubleOrNull() ?: 0.0,
                        daysText.toIntOrNull() ?: 0,
                    )
                },
                modifier = Modifier.weight(1f),
                enabled = email.isNotBlank(),
            )
        }
    }
}

@Composable
fun ShareDialog(ib: Inbound, client: ClientInfo, panelUrl: String, onDismiss: () -> Unit) {
    var address by remember { mutableStateOf(LinkBuilder.defaultAddress(ib, panelUrl)) }
    var portText by remember { mutableStateOf(LinkBuilder.defaultPort(ib).toString()) }
    var copied by remember { mutableStateOf(false) }
    val port = portText.toIntOrNull() ?: ib.port
    val link = remember(address, port, ib, client) {
        runCatching { LinkBuilder.build(ib, client, address.trim(), port) }.getOrNull()
    }
    val clipboard = LocalClipboardManager.current
    val ctx = LocalContext.current

    GlassDialog(onDismiss) {
        Text(
            client.email.ifBlank { "Клиент" },
            color = GlassColors.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1,
        )
        Text("${ib.protocol.uppercase()} · ${ib.remark}", color = GlassColors.TextDim, fontSize = 13.sp)
        VSpace(14)
        if (link == null) {
            Text(
                "Для протокола ${ib.protocol} ссылка пока не поддерживается.",
                color = GlassColors.TextDim, fontSize = 14.sp,
            )
        } else {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                QrCode(link, Modifier.size(240.dp))
            }
            VSpace(12)
            SelectionContainer {
                Text(link, color = GlassColors.TextDim, fontSize = 11.sp, maxLines = 5, overflow = TextOverflow.Ellipsis)
            }
            VSpace(12)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassButton(
                    text = if (copied) "Скопировано ✓" else "Копировать",
                    onClick = { clipboard.setText(AnnotatedString(link)); copied = true },
                    modifier = Modifier.weight(1f),
                    small = true,
                )
                GlassButton(
                    text = "Поделиться",
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, link)
                        }
                        ctx.startActivity(Intent.createChooser(send, null))
                    },
                    modifier = Modifier.weight(1f),
                    tint = GlassColors.Green,
                    small = true,
                )
            }
        }
        VSpace(16)
        GlassField(address, { address = it; copied = false }, "Адрес сервера в ссылке")
        VSpace(10)
        GlassField(portText, { portText = it.filter(Char::isDigit); copied = false }, "Порт", keyboardType = KeyboardType.Number)
        VSpace(16)
        GlassButton("Закрыть", onDismiss, Modifier.fillMaxWidth(), tint = GlassColors.Slate)
    }
}
