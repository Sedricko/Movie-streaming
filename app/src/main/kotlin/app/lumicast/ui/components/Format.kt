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

/** "1:30" → 90, "14:50" → 890, "1:02:03" → 3723, "90" → 90; vazio ou inválido → null. */
fun parseClock(text: String): Int? {
    val parts = text.trim().split(':')
    if (parts.isEmpty() || parts.size > 3 || parts.any { it.isBlank() }) return null
    val nums = parts.map { it.trim().toIntOrNull()?.takeIf { n -> n >= 0 } ?: return null }
    if (nums.size > 1 && nums.drop(1).any { it >= 60 }) return null
    return nums.fold(0) { acc, n -> acc * 60 + n }
}
