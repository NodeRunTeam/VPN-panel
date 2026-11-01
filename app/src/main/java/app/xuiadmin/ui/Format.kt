package app.xuiadmin.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatBytes(b: Long): String {
    if (b < 1024) return "$b B"
    val units = arrayOf("KB", "MB", "GB", "TB", "PB")
    var v = b / 1024.0
    var i = 0
    while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
    return String.format(Locale.US, if (v >= 100) "%.0f %s" else "%.1f %s", v, units[i])
}

fun formatTotal(total: Long): String = if (total <= 0) "∞" else formatBytes(total)

fun formatExpiry(ms: Long): String = when {
    ms == 0L -> "∞"
    ms < 0 -> "${-ms / 86_400_000} дн. с первого подключения"
    else -> SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(ms))
}

fun formatUptime(sec: Long): String {
    val d = sec / 86400
    val h = sec % 86400 / 3600
    val m = sec % 3600 / 60
    return when {
        d > 0 -> "$d д $h ч"
        h > 0 -> "$h ч $m мин"
        else -> "$m мин"
    }
}
