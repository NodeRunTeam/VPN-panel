package app.xuiadmin.data

import android.annotation.SuppressLint
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class XuiException(message: String) : Exception(message)

/**
 * Клиент HTTP API панели 3X-UI.
 * Авторизация: POST /login (form) -> session-cookie. Дальше запросы /panel/api/...
 * Если сессия истекла (панель отвечает не-JSON / 404) — клиент сам логинится заново и повторяет запрос.
 */
class XuiClient(private val cfg: ServerConfig) {

    private val cookies = MemoryCookieJar()
    private val http: OkHttpClient = buildHttp()
    private val base: String = cfg.baseUrl.trim()
        .let { if (it.startsWith("http://") || it.startsWith("https://")) it else "https://$it" }
        .trimEnd('/')

    // ---------- публичные методы ----------

    suspend fun login() = withContext(Dispatchers.IO) {
        val body = FormBody.Builder()
            .add("username", cfg.username)
            .add("password", cfg.password)
            .build()
        val req = Request.Builder().url("$base/login").post(body).build()
        http.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            val json = parseJson(text)
                ?: throw XuiException("HTTP ${resp.code}: панель вернула не JSON. Проверьте адрес и путь панели (webBasePath)")
            if (!json.optBoolean("success", false)) {
                throw XuiException(json.optString("msg").ifBlank { "Неверный логин или пароль" })
            }
        }
    }

    suspend fun loadAll(): Snapshot = coroutineScope {
        val s = async { runCatching { serverStatus() }.getOrNull() }
        val i = async { inbounds() }
        val o = async { onlines() }
        Snapshot(s.await(), i.await(), o.await())
    }

    suspend fun serverStatus(): ServerStatus {
        // В разных версиях панели эндпоинт бывает GET или POST — пробуем оба
        val json = try {
            api("GET", "/panel/api/server/status")
        } catch (e: XuiException) {
            api("POST", "/panel/api/server/status")
        }
        val o = json.optJSONObject("obj") ?: throw XuiException("Пустой ответ статуса")
        val mem = o.optJSONObject("mem")
        val disk = o.optJSONObject("disk")
        val xray = o.optJSONObject("xray")
        val net = o.optJSONObject("netIO")
        return ServerStatus(
            cpu = o.optDouble("cpu", 0.0),
            memCurrent = mem?.optLong("current") ?: 0L,
            memTotal = mem?.optLong("total") ?: 0L,
            diskCurrent = disk?.optLong("current") ?: 0L,
            diskTotal = disk?.optLong("total") ?: 0L,
            xrayState = xray?.optString("state") ?: "",
            xrayVersion = xray?.optString("version") ?: "",
            uptime = o.optLong("uptime"),
            tcp = o.optInt("tcpCount"),
            udp = o.optInt("udpCount"),
            netUp = net?.optLong("up") ?: 0L,
            netDown = net?.optLong("down") ?: 0L,
        )
    }

    suspend fun inbounds(): List<Inbound> {
        val arr = api("GET", "/panel/api/inbounds/list").optJSONArray("obj") ?: return emptyList()
        return (0 until arr.length()).map { parseInbound(arr.getJSONObject(it)) }
    }

    suspend fun onlines(): Set<String> = try {
        val arr = api("POST", "/panel/api/inbounds/onlines").optJSONArray("obj")
        if (arr == null) emptySet() else (0 until arr.length()).map { arr.optString(it) }.toSet()
    } catch (e: XuiException) {
        emptySet()
    }

    /** Включить / выключить inbound. Шлём исходный объект целиком, меняя только enable. */
    suspend fun setInboundEnabled(ib: Inbound, enable: Boolean) {
        val o = JSONObject(ib.raw)
        o.put("enable", enable)
        val form = LinkedHashMap<String, String>()
        for (k in o.keys()) {
            if (k == "clientStats" || k == "id") continue
            val v = o.get(k)
            if (v == JSONObject.NULL) continue
            form[k] = v.toString()
        }
        api("POST", "/panel/api/inbounds/update/${ib.id}", form)
    }

    suspend fun setClientEnabled(ib: Inbound, client: ClientInfo, enable: Boolean) {
        val c = JSONObject(client.raw)
        c.put("enable", enable)
        val settings = JSONObject().put("clients", JSONArray().put(c)).toString()
        api(
            "POST",
            "/panel/api/inbounds/updateClient/${Uri.encode(client.key)}",
            mapOf("id" to ib.id.toString(), "settings" to settings),
        )
    }

    suspend fun resetClientTraffic(ib: Inbound, client: ClientInfo) {
        api("POST", "/panel/api/inbounds/${ib.id}/resetClientTraffic/${Uri.encode(client.email)}")
    }

    suspend fun restartXray() {
        api("POST", "/panel/api/server/restartXrayService")
    }

    /** Создать клиента. Поля подбираются по протоколу inbound'а. */
    suspend fun addClient(ib: Inbound, email: String, totalBytes: Long, expiryMs: Long) {
        val settingsObj = runCatching { JSONObject(JSONObject(ib.raw).optString("settings")) }.getOrDefault(JSONObject())
        val c = JSONObject()
        when (ib.protocol) {
            "vless" -> {
                c.put("id", UUID.randomUUID().toString())
                // flow: берём у любого существующего клиента, иначе для tcp+reality/tls ставим vision
                var flow = ""
                val existing = settingsObj.optJSONArray("clients")
                if (existing != null) {
                    for (i in 0 until existing.length()) {
                        val f = existing.optJSONObject(i)?.optString("flow").orEmpty()
                        if (f.isNotBlank()) { flow = f; break }
                    }
                }
                if (flow.isBlank()) {
                    val st = runCatching { JSONObject(JSONObject(ib.raw).optString("streamSettings")) }
                        .getOrDefault(JSONObject())
                    val net = st.optString("network").ifBlank { "tcp" }
                    val sec = st.optString("security")
                    if (net == "tcp" && (sec == "reality" || sec == "tls")) flow = "xtls-rprx-vision"
                }
                c.put("flow", flow)
            }
            "vmess" -> {
                c.put("id", UUID.randomUUID().toString())
                c.put("security", "auto")
            }
            "trojan" -> c.put("password", randomToken(10))
            "shadowsocks" -> {
                val m = settingsObj.optString("method")
                c.put("method", "")
                c.put(
                    "password",
                    when {
                        m.contains("2022-blake3-aes-128") -> randomKey(16)
                        m.startsWith("2022") -> randomKey(32)
                        else -> randomToken(16)
                    },
                )
            }
            "hysteria", "hysteria2" -> c.put("auth", randomToken(16))
            else -> throw XuiException("Протокол ${ib.protocol} пока не поддерживается")
        }
        c.put("email", email)
        c.put("limitIp", 0)
        c.put("totalGB", totalBytes)
        c.put("expiryTime", expiryMs)
        c.put("enable", true)
        c.put("tgId", "")
        c.put("subId", randomToken(16))
        c.put("comment", "")
        c.put("reset", 0)
        val settings = JSONObject().put("clients", JSONArray().put(c)).toString()
        api("POST", "/panel/api/inbounds/addClient", mapOf("id" to ib.id.toString(), "settings" to settings))
    }

    suspend fun deleteClient(ib: Inbound, client: ClientInfo) {
        api("POST", "/panel/api/inbounds/${ib.id}/delClient/${Uri.encode(client.key)}")
    }

    private fun randomToken(n: Int): String {
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        val r = SecureRandom()
        return buildString { repeat(n) { append(chars[r.nextInt(chars.length)]) } }
    }

    private fun randomKey(bytes: Int): String {
        val b = ByteArray(bytes)
        SecureRandom().nextBytes(b)
        return Base64.encodeToString(b, Base64.NO_WRAP)
    }

    // ---------- внутренности ----------

    private fun parseInbound(o: JSONObject): Inbound {
        val stats = HashMap<String, JSONObject>()
        o.optJSONArray("clientStats")?.let { arr ->
            for (i in 0 until arr.length()) {
                val s = arr.getJSONObject(i)
                stats[s.optString("email")] = s
            }
        }
        val protocol = o.optString("protocol")
        val clientsArr = runCatching { JSONObject(o.optString("settings")).optJSONArray("clients") }.getOrNull()
        val clients = mutableListOf<ClientInfo>()
        if (clientsArr != null) {
            for (i in 0 until clientsArr.length()) {
                val c = clientsArr.getJSONObject(i)
                val email = c.optString("email")
                val st = stats[email]
                val key = if (protocol == "shadowsocks") email else
                    c.optString("id").ifBlank { c.optString("password") }
                        .ifBlank { c.optString("auth") }
                        .ifBlank { email }
                clients += ClientInfo(
                    key = key,
                    email = email,
                    enable = c.optBoolean("enable", true),
                    expiryTime = c.optLong("expiryTime"),
                    total = st?.optLong("total") ?: c.optLong("totalGB"),
                    up = st?.optLong("up") ?: 0L,
                    down = st?.optLong("down") ?: 0L,
                    raw = c.toString(),
                )
            }
        }
        return Inbound(
            id = o.optInt("id"),
            remark = o.optString("remark"),
            enable = o.optBoolean("enable", true),
            protocol = protocol,
            port = o.optInt("port"),
            up = o.optLong("up"),
            down = o.optLong("down"),
            total = o.optLong("total"),
            expiryTime = o.optLong("expiryTime"),
            clients = clients,
            raw = o.toString(),
        )
    }

    private suspend fun api(
        method: String,
        path: String,
        form: Map<String, String>? = null,
    ): JSONObject = withContext(Dispatchers.IO) {
        var (code, text) = raw(method, path, form)
        var json = parseJson(text)
        if (json == null) {            // сессия, скорее всего, истекла
            login()
            val r = raw(method, path, form)
            code = r.first
            text = r.second
            json = parseJson(text)
        }
        if (json == null) throw XuiException("HTTP $code: неожиданный ответ панели (эндпоинт не найден?)")
        if (!json.optBoolean("success", false)) {
            throw XuiException(json.optString("msg").ifBlank { "Ошибка панели (HTTP $code)" })
        }
        json
    }

    private fun raw(method: String, path: String, form: Map<String, String>?): Pair<Int, String> {
        val rb = Request.Builder().url(base + path)
        if (method == "GET") {
            rb.get()
        } else {
            val fb = FormBody.Builder()
            form?.forEach { (k, v) -> fb.add(k, v) }
            rb.post(fb.build())
        }
        http.newCall(rb.build()).execute().use { r ->
            return r.code to r.body?.string().orEmpty()
        }
    }

    private fun parseJson(text: String): JSONObject? =
        try { JSONObject(text) } catch (e: JSONException) { null }

    private fun buildHttp(): OkHttpClient {
        val b = OkHttpClient.Builder()
            .cookieJar(cookies)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
        if (cfg.trustAll) {
            val ctx = SSLContext.getInstance("TLS")
            ctx.init(null, arrayOf<TrustManager>(TrustAll), SecureRandom())
            b.sslSocketFactory(ctx.socketFactory, TrustAll)
            b.hostnameVerifier { _, _ -> true }
        }
        return b.build()
    }
}

private class MemoryCookieJar : CookieJar {
    private val store = mutableListOf<Cookie>()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        for (n in cookies) {
            store.removeAll { it.name == n.name && it.domain == n.domain && it.path == n.path }
            store.add(n)
        }
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        store.removeAll { it.expiresAt < now }
        return store.filter { it.matches(url) }
    }
}

@SuppressLint("CustomX509TrustManager", "TrustAllX509TrustManager")
private object TrustAll : X509TrustManager {
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}
