package app.xuiadmin

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.xuiadmin.data.ClientInfo
import app.xuiadmin.data.Inbound
import app.xuiadmin.data.SecureStore
import app.xuiadmin.data.UiPrefs
import app.xuiadmin.data.ServerConfig
import app.xuiadmin.data.ServerStatus
import app.xuiadmin.data.XuiClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

enum class ThemeMode { System, Light, Dark }

enum class Screen { Login, Dashboard, Inbound }

data class UiState(
    val screen: Screen = Screen.Login,
    val loading: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val status: ServerStatus? = null,
    val inbounds: List<Inbound> = emptyList(),
    val onlines: Set<String> = emptySet(),
    val selectedInboundId: Int? = null,
    val saved: ServerConfig? = null,
    val themeMode: ThemeMode = ThemeMode.System,
    val blur: Boolean = true,
    val animateBg: Boolean = true,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val store = SecureStore(app)
    private val prefs = UiPrefs(app)
    private var client: XuiClient? = null
    private var refreshing = false

    private val _state = MutableStateFlow(
        UiState(
            saved = store.load(),
            themeMode = runCatching { ThemeMode.valueOf(prefs.theme) }.getOrDefault(ThemeMode.System),
            blur = prefs.blur,
            animateBg = prefs.animate,
        )
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        _state.value.saved?.let { login(it) }   // автовход, если данные сохранены
    }

    fun login(cfg: ServerConfig) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val c = XuiClient(cfg)
                c.login()
                client = c
                store.save(cfg)
                val d = c.loadAll()
                _state.update {
                    it.copy(
                        loading = false, screen = Screen.Dashboard, saved = cfg,
                        status = d.status, inbounds = d.inbounds, onlines = d.onlines,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = friendly(e)) }
            }
        }
    }

    fun logout() {
        store.clear()
        client = null
        _state.update { UiState(themeMode = it.themeMode, blur = it.blur, animateBg = it.animateBg) }
    }

    fun refresh(silent: Boolean = false) {
        val c = client ?: return
        if (refreshing) return
        refreshing = true
        viewModelScope.launch {
            try { reload(c, silent) } finally { refreshing = false }
        }
    }

    fun open(id: Int) = _state.update { it.copy(screen = Screen.Inbound, selectedInboundId = id) }
    fun back() = _state.update { it.copy(screen = Screen.Dashboard, selectedInboundId = null) }
    fun clearMessage() = _state.update { it.copy(message = null) }

    fun toggleInbound(ib: Inbound, enable: Boolean) =
        action(if (enable) "Inbound включён" else "Inbound выключен") { it.setInboundEnabled(ib, enable) }

    fun toggleClient(ib: Inbound, cl: ClientInfo, enable: Boolean) =
        action(if (enable) "Клиент включён" else "Клиент выключен") { it.setClientEnabled(ib, cl, enable) }

    fun resetClient(ib: Inbound, cl: ClientInfo) =
        action("Трафик сброшен") { it.resetClientTraffic(ib, cl) }

    fun addClient(ib: Inbound, email: String, gb: Double, days: Int) = action("Клиент создан") {
        val total = (gb * 1024.0 * 1024.0 * 1024.0).toLong()
        val expiry = if (days > 0) System.currentTimeMillis() + days * 86_400_000L else 0L
        it.addClient(ib, email, total, expiry)
    }

    fun deleteClient(ib: Inbound, cl: ClientInfo) =
        action("Клиент удалён") { it.deleteClient(ib, cl) }

    fun setTheme(m: ThemeMode) {
        prefs.theme = m.name
        _state.update { it.copy(themeMode = m) }
    }

    fun setBlur(v: Boolean) {
        prefs.blur = v
        _state.update { it.copy(blur = v) }
    }

    fun setAnimate(v: Boolean) {
        prefs.animate = v
        _state.update { it.copy(animateBg = v) }
    }

    fun restartXray() = action("Xray перезапущен") { it.restartXray() }

    // ---------- внутреннее ----------

    private suspend fun reload(c: XuiClient, silent: Boolean) {
        if (!silent) _state.update { it.copy(loading = true) }
        try {
            val d = c.loadAll()
            _state.update {
                it.copy(
                    loading = false, error = null,
                    status = d.status ?: it.status, inbounds = d.inbounds, onlines = d.onlines,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(loading = false, error = friendly(e)) }
        }
    }

    private fun action(okMessage: String, block: suspend (XuiClient) -> Unit) {
        val c = client ?: return
        viewModelScope.launch {
            try {
                block(c)
                _state.update { it.copy(message = okMessage) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = friendly(e)) }
            }
            reload(c, true)
        }
    }

    private fun friendly(e: Exception): String = when (e) {
        is UnknownHostException -> "Сервер не найден: проверьте адрес"
        is ConnectException, is SocketTimeoutException -> "Нет соединения с сервером"
        is SSLException -> "Ошибка TLS-сертификата. Для самоподписанного включите «Доверять любому сертификату»"
        is IllegalArgumentException -> "Некорректный адрес панели"
        else -> e.message ?: "Неизвестная ошибка"
    }
}
