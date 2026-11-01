package app.xuiadmin.data

data class ServerConfig(
    val baseUrl: String,
    val username: String,
    val password: String,
    val trustAll: Boolean,
)

data class ServerStatus(
    val cpu: Double,
    val memCurrent: Long,
    val memTotal: Long,
    val diskCurrent: Long,
    val diskTotal: Long,
    val xrayState: String,
    val xrayVersion: String,
    val uptime: Long,
    val tcp: Int,
    val udp: Int,
    val netUp: Long,
    val netDown: Long,
)

data class ClientInfo(
    val key: String,      // идентификатор для API (id / password / email — зависит от протокола)
    val email: String,
    val enable: Boolean,
    val expiryTime: Long,
    val total: Long,
    val up: Long,
    val down: Long,
    val raw: String,      // исходный JSON клиента
)

data class Inbound(
    val id: Int,
    val remark: String,
    val enable: Boolean,
    val protocol: String,
    val port: Int,
    val up: Long,
    val down: Long,
    val total: Long,
    val expiryTime: Long,
    val clients: List<ClientInfo>,
    val raw: String,      // исходный JSON inbound (нужен для update)
)

data class Snapshot(
    val status: ServerStatus?,
    val inbounds: List<Inbound>,
    val onlines: Set<String>,
)
