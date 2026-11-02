package app.xuiadmin.data

import android.content.Context

/** Несекретные настройки интерфейса. */
class UiPrefs(context: Context) {
    private val p = context.getSharedPreferences("ui", Context.MODE_PRIVATE)

    var theme: String
        get() = p.getString("theme", "System") ?: "System"
        set(v) { p.edit().putString("theme", v).apply() }

    var blur: Boolean
        get() = p.getBoolean("blur", true)
        set(v) { p.edit().putBoolean("blur", v).apply() }

    var animate: Boolean
        get() = p.getBoolean("animate", true)
        set(v) { p.edit().putBoolean("animate", v).apply() }
}
