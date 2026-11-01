package app.xuiadmin.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.xuiadmin.AppViewModel
import app.xuiadmin.Screen
import kotlinx.coroutines.delay

private class Confirm(val title: String, val text: String, val action: () -> Unit)

@Composable
fun XuiApp(vm: AppViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf<Confirm?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val loggedIn = state.screen != Screen.Login

    // Автообновление раз в 10 секунд, пока приложение на экране
    LaunchedEffect(loggedIn) {
        if (loggedIn) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    delay(10_000)
                    vm.refresh(silent = true)
                }
            }
        }
    }

    BackHandler(enabled = state.screen == Screen.Inbound) { vm.back() }

    MaterialTheme(colorScheme = darkColorScheme()) {
        GlassBackground {
            Crossfade(state.screen, label = "screen") { s ->
                when (s) {
                    Screen.Login -> LoginScreen(state, vm::login)

                    Screen.Dashboard -> DashboardScreen(
                        state = state,
                        onRefresh = { vm.refresh() },
                        onLogout = vm::logout,
                        onOpen = vm::open,
                        onToggleInbound = vm::toggleInbound,
                        onRestartXray = {
                            confirm = Confirm("Перезапустить Xray?", "Активные соединения будут разорваны.") { vm.restartXray() }
                        },
                    )

                    Screen.Inbound -> InboundScreen(
                        ib = state.inbounds.firstOrNull { it.id == state.selectedInboundId },
                        onlines = state.onlines,
                        onBack = vm::back,
                        onToggleInbound = vm::toggleInbound,
                        onToggleClient = vm::toggleClient,
                        onResetClient = { ib, c ->
                            confirm = Confirm("Сбросить трафик?", "Счётчик клиента ${c.email} будет обнулён.") { vm.resetClient(ib, c) }
                        },
                    )
                }
            }

            state.message?.let { msg ->
                LaunchedEffect(msg) {
                    delay(2200)
                    vm.clearMessage()
                }
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(24.dp)
                ) {
                    Box(Modifier.glass(RoundedCornerShape(50)).padding(horizontal = 20.dp, vertical = 12.dp)) {
                        Text(msg, color = Color.White)
                    }
                }
            }
        }

        confirm?.let { c ->
            AlertDialog(
                onDismissRequest = { confirm = null },
                title = { Text(c.title) },
                text = { Text(c.text) },
                confirmButton = { TextButton(onClick = { c.action(); confirm = null }) { Text("Да") } },
                dismissButton = { TextButton(onClick = { confirm = null }) { Text("Отмена") } },
                containerColor = Color(0xFF1B2342),
                shape = RoundedCornerShape(28.dp),
            )
        }
    }
}
