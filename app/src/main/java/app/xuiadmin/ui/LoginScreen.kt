package app.xuiadmin.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.xuiadmin.UiState
import app.xuiadmin.data.ServerConfig

@Composable
fun LoginScreen(state: UiState, onLogin: (ServerConfig) -> Unit) {
    val saved = state.saved
    var url by rememberSaveable { mutableStateOf(saved?.baseUrl ?: "") }
    var user by rememberSaveable { mutableStateOf(saved?.username ?: "") }
    var pass by rememberSaveable { mutableStateOf(saved?.password ?: "") }
    var trust by rememberSaveable { mutableStateOf(saved?.trustAll ?: false) }

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Xui Admin", color = GlassColors.Text, fontSize = 38.sp, fontWeight = FontWeight.Bold)
        Text("Управление панелью 3X-UI", color = GlassColors.TextDim, fontSize = 16.sp)
        VSpace(28)

        GlassCard(Modifier.fillMaxWidth()) {
            GlassField(url, { url = it }, "Адрес панели (https://host:port/путь)", keyboardType = KeyboardType.Uri)
            VSpace(12)
            GlassField(user, { user = it }, "Логин")
            VSpace(12)
            GlassField(pass, { pass = it }, "Пароль", password = true)
            VSpace(16)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Доверять любому сертификату", color = GlassColors.Text, fontSize = 15.sp)
                    Text("Только для самоподписанных. Небезопасно.", color = GlassColors.TextDim, fontSize = 12.sp)
                }
                GlassSwitch(trust) { trust = it }
            }
            state.error?.let {
                VSpace(12)
                Text(it, color = GlassColors.Red, fontSize = 14.sp)
            }
            VSpace(18)
            GlassButton(
                text = if (state.loading) "Подключение…" else "Войти",
                onClick = { onLogin(ServerConfig(url.trim(), user.trim(), pass, trust)) },
                modifier = Modifier.fillMaxWidth(),
                solid = true,
                enabled = !state.loading && url.isNotBlank() && user.isNotBlank() && pass.isNotBlank(),
            )
        }
    }
}
