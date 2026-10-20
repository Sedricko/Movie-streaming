package app.lumicast.ui.components

import java.util.Locale

fun formatDuration(totalSec: Int): String {
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.ROOT, "%d:%02d", m, s)
}

fun formatSize(bytes: Long): String {
    val mib = bytes / (1024.0 * 1024.0)
    return if (mib >= 1024) String.format(Locale("pt", "BR"), "%.1f GB", mib / 1024.0)
    else String.format(Locale("pt", "BR"), "%.0f MB", mib)
}
