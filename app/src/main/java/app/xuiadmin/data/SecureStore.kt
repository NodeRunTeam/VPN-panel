package app.xuiadmin.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Хранит данные входа в зашифрованном виде (Android Keystore). Если шифрование недоступно — ничего не сохраняет. */
class SecureStore(context: Context) {

    private val prefs: SharedPreferences? = runCatching {
        val key = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "xui_secure",
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }.getOrNull()

    fun load(): ServerConfig? {
        val p = prefs ?: return null
        val url = p.getString("url", null) ?: return null
        return ServerConfig(
            baseUrl = url,
            username = p.getString("user", "") ?: "",
            password = p.getString("pass", "") ?: "",
            trustAll = p.getBoolean("trust", false),
        )
    }

    fun save(c: ServerConfig) {
        prefs?.edit()
            ?.putString("url", c.baseUrl)
            ?.putString("user", c.username)
            ?.putString("pass", c.password)
            ?.putBoolean("trust", c.trustAll)
            ?.apply()
    }

    fun clear() {
        prefs?.edit()?.clear()?.apply()
    }
}
