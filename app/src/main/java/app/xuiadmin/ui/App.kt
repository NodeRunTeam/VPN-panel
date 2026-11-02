package app.xuiadmin.ui

import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.xuiadmin.AppViewModel
import app.xuiadmin.Screen
import app.xuiadmin.ThemeMode
import kotlinx.coroutines.delay

private class Confirm(val title: String, val text: String, val action: () -> Unit)

@Composable
fun XuiApp(vm: AppViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf<Confirm?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val loggedIn = state.screen != Screen.Login

    val dark = when (state.themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }

    // Цвет значков в системных панелях зависит от темы
    val activity = LocalContext.current as ComponentActivity
    DisposableEffect(dark) {
        val transparent = android.graphics.Color.TRANSPARENT
        val style = if (dark) {
            SystemBarStyle.dark(transparent)
        } else {
            SystemBarStyle.light(transparent, transparent)
        }
        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        onDispose { }
    }

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

    CompositionLocalProvider(LocalGlass provides (if (dark) DarkGlass else LightGlass)) {
        MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
            GlassBackground(dark = dark, blur = state.blur, animate = state.animateBg) {
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
                            onSettings = { showSettings = true },
                        )

                        Screen.Inbound -> InboundScreen(
                            ib = state.inbounds.firstOrNull { it.id == state.selectedInboundId },
                            onlines = state.onlines,
                            panelUrl = state.saved?.baseUrl.orEmpty(),
                            onBack = vm::back,
                            onToggleInbound = vm::toggleInbound,
                            onToggleClient = vm::toggleClient,
                            onResetClient = { ib, c ->
                                confirm = Confirm("Сбросить трафик?", "Счётчик клиента ${c.email} будет обнулён.") { vm.resetClient(ib, c) }
                            },
                            onDeleteClient = { ib, c ->
                                confirm = Confirm("Удалить клиента?", "Клиент ${c.email} будет удалён без возможности восстановления.") { vm.deleteClient(ib, c) }
                            },
                            onAddClient = vm::addClient,
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
                            .padding(24.dp),
                    ) {
                        GlassSurface(shape = RoundedCornerShape(50)) {
                            Text(
                                msg,
                                color = GlassColors.Text,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                            )
                        }
                    }
                }
            }

            confirm?.let { c ->
                ConfirmDialog(
                    title = c.title,
                    text = c.text,
                    onConfirm = { c.action(); confirm = null },
                    onDismiss = { confirm = null },
                )
            }
            if (showSettings) {
                SettingsDialog(
                    state = state,
                    onTheme = vm::setTheme,
                    onBlur = vm::setBlur,
                    onAnimate = vm::setAnimate,
                    onDismiss = { showSettings = false },
                )
            }
        }
    }
}
