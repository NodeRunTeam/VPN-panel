package app.xuiadmin.data

import android.net.Uri
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

/** Сборка клиентских ссылок (vless / vmess / trojan / shadowsocks) по настройкам inbound'а. */
object LinkBuilder {

    fun hostOf(url: String): String {
        val u = if (url.contains("://")) url else "https://$url"
        return Uri.parse(u).host.orEmpty()
    }

    private fun obj(s: String): JSONObject = runCatching { JSONObject(s) }.getOrDefault(JSONObject())
    private fun stream(ib: Inbound) = obj(obj(ib.raw).optString("streamSettings"))
    private fun settings(ib: Inbound) = obj(obj(ib.raw).optString("settings"))

    private fun externalProxy(ib: Inbound): JSONObject? =
        stream(ib).optJSONArray("externalProxy")?.optJSONObject(0)?.takeIf { it.optString("dest").isNotBlank() }

    fun defaultAddress(ib: Inbound, panelUrl: String): String =
        externalProxy(ib)?.optString("dest") ?: hostOf(panelUrl)

    fun defaultPort(ib: Inbound): Int {
        val p = externalProxy(ib)?.optInt("port", 0) ?: 0
        return if (p > 0) p else ib.port
    }

    private fun JSONObject.first(key: String): String = when (val v = opt(key)) {
        is JSONArray -> if (v.length() > 0) v.optString(0) else ""
        is String -> v
        else -> ""
    }

    private fun bracket(a: String) = if (a.contains(':') && !a.startsWith("[")) "[$a]" else a

    private fun query(p: Map<String, String>) =
        p.entries.joinToString("&") { "${it.key}=${Uri.encode(it.value)}" }

    fun build(ib: Inbound, c: ClientInfo, address: String, port: Int): String? {
        val st = stream(ib)
        val sett = settings(ib)
        val cj = obj(c.raw)
        val network = st.optString("network").ifBlank { "tcp" }
        val security = st.optString("security").ifBlank { "none" }
        val remark = listOf(ib.remark, c.email).filter { it.isNotBlank() }.joinToString("-")
        val host = bracket(address)

        var headerType = "none"
        var hostHdr = ""
        var path = ""
        var extra = LinkedHashMap<String, String>()

        when (network) {
            "tcp" -> {
                val header = st.optJSONObject("tcpSettings")?.optJSONObject("header")
                if (header != null && header.optString("type") == "http") {
                    headerType = "http"
                    val req = header.optJSONObject("request")
                    path = req?.optJSONArray("path")?.optString(0).orEmpty()
                    hostHdr = req?.optJSONObject("headers")?.first("Host").orEmpty()
                }
            }
            "ws", "httpupgrade" -> {
                val s = st.optJSONObject(if (network == "ws") "wsSettings" else "httpupgradeSettings")
                path = s?.optString("path").orEmpty()
                hostHdr = s?.optString("host").orEmpty()
                    .ifBlank { s?.optJSONObject("headers")?.first("Host").orEmpty() }
            }
            "xhttp", "splithttp" -> {
                val s = st.optJSONObject("xhttpSettings") ?: st.optJSONObject("splithttpSettings")
                path = s?.optString("path").orEmpty()
                hostHdr = s?.optString("host").orEmpty()
                s?.optString("mode")?.takeIf { it.isNotBlank() }?.let { extra["mode"] = it }
            }
            "grpc" -> {
                val s = st.optJSONObject("grpcSettings")
                path = s?.optString("serviceName").orEmpty()
                hostHdr = s?.optString("authority").orEmpty()
                extra["mode"] = if (s?.optBoolean("multiMode") == true) "multi" else "gun"
            }
            "kcp" -> {
                val s = st.optJSONObject("kcpSettings")
                headerType = s?.optJSONObject("header")?.optString("type").orEmpty().ifBlank { "none" }
                s?.optString("seed")?.takeIf { it.isNotBlank() }?.let { extra["seed"] = it }
            }
        }

        // общие параметры транспорта и безопасности
        val p = LinkedHashMap<String, String>()
        p["type"] = network
        if (headerType != "none") p["headerType"] = headerType
        if (network == "grpc") {
            if (path.isNotBlank()) p["serviceName"] = path
            if (hostHdr.isNotBlank()) p["authority"] = hostHdr
        } else {
            if (path.isNotBlank()) p["path"] = path
            if (hostHdr.isNotBlank()) p["host"] = hostHdr
        }
        p.putAll(extra)
        p["security"] = security

        var sni = ""
        var fp = ""
        var alpn = ""
        if (security == "tls") {
            val t = st.optJSONObject("tlsSettings")
            sni = t?.optString("serverName").orEmpty()
            fp = t?.optJSONObject("settings")?.optString("fingerprint").orEmpty()
            alpn = t?.optJSONArray("alpn")?.let { a -> (0 until a.length()).joinToString(",") { a.optString(it) } }.orEmpty()
            if (fp.isNotBlank()) p["fp"] = fp
            if (alpn.isNotBlank()) p["alpn"] = alpn
            if (sni.isNotBlank()) p["sni"] = sni
        } else if (security == "reality") {
            val r = st.optJSONObject("realitySettings")
            val rs = r?.optJSONObject("settings")
            sni = r?.optJSONArray("serverNames")?.optString(0).orEmpty()
            fp = rs?.optString("fingerprint").orEmpty()
            val pbk = rs?.optString("publicKey").orEmpty()
            val sid = r?.optJSONArray("shortIds")?.optString(0).orEmpty()
            val spx = rs?.optString("spiderX").orEmpty()
            val pqv = rs?.optString("mldsa65Verify").orEmpty()
            if (pbk.isNotBlank()) p["pbk"] = pbk
            if (fp.isNotBlank()) p["fp"] = fp
            if (sni.isNotBlank()) p["sni"] = sni
            if (sid.isNotBlank()) p["sid"] = sid
            if (spx.isNotBlank()) p["spx"] = spx
            if (pqv.isNotBlank()) p["pqv"] = pqv
        }

        return when (ib.protocol) {
            "vless" -> {
                val q = LinkedHashMap<String, String>()
                q["type"] = network
                q["encryption"] = sett.optString("encryption").ifBlank { "none" }
                p.forEach { (k, v) -> if (k != "type") q[k] = v }
                val flow = cj.optString("flow")
                if (network == "tcp" && (security == "tls" || security == "reality") && flow.isNotBlank()) q["flow"] = flow
                "vless://${c.key}@$host:$port?${query(q)}#${Uri.encode(remark)}"
            }
            "trojan" -> "trojan://${Uri.encode(c.key)}@$host:$port?${query(p)}#${Uri.encode(remark)}"
            "vmess" -> {
                val j = JSONObject()
                j.put("v", "2")
                j.put("ps", remark)
                j.put("add", address)
                j.put("port", port)
                j.put("id", c.key)
                j.put("scy", cj.optString("security").ifBlank { "auto" })
                j.put("net", network)
                j.put("type", if (network == "grpc") extra["mode"] ?: "gun" else headerType)
                j.put("host", hostHdr)
                j.put("path", path)
                j.put("tls", if (security == "tls") "tls" else "none")
                j.put("sni", sni)
                j.put("alpn", alpn)
                j.put("fp", fp)
                "vmess://" + Base64.encodeToString(j.toString().toByteArray(), Base64.NO_WRAP)
            }
            "shadowsocks" -> {
                val method = sett.optString("method").ifBlank { cj.optString("method") }
                val clientPw = cj.optString("password")
                val info = if (method.startsWith("2022")) "$method:${sett.optString("password")}:$clientPw" else "$method:$clientPw"
                val b64 = Base64.encodeToString(info.toByteArray(), Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE)
                "ss://$b64@$host:$port?type=$network#${Uri.encode(remark)}"
            }
            else -> null
        }
    }
}
