package app.lumicast.data.library

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/** Nome seguro para arquivo/pasta, com extensão de vídeo. */
internal fun safeFileName(title: String, mimeType: String = ""): String {
    val clean = title.replace(Regex("""[\\/:*?"<>|\n\r]"""), "_").trim().trimStart('.').ifBlank { "video" }.take(120)
    if (clean.contains('.') && !clean.endsWith('.')) return clean
    val ext = when (mimeType) { "video/x-matroska" -> "mkv"; "video/webm" -> "webm"; "video/quicktime" -> "mov"; else -> "mp4" }
    return "${clean.trimEnd('.')}.$ext"
}

internal fun safeFolderName(name: String) = name.replace(Regex("""[\\/:*?"<>|\n\r]"""), "_").trim().trimStart('.').ifBlank { "Pasta" }.take(60)

/** Copia o vídeo baixado para Downloads/Lumicast/<pasta> (aparece na galeria). */
fun saveToDownloads(context: Context, src: File, folder: String, title: String, mimeType: String) {
    val name = safeFileName(title, mimeType)
    val subdir = "Lumicast/${safeFolderName(folder)}"
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$subdir")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Não foi possível criar o arquivo em Downloads. Confira o espaço livre.")
        runCatching {
            resolver.openOutputStream(uri)!!.use { out -> src.inputStream().use { it.copyTo(out) } }
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        }.onFailure { resolver.delete(uri, null, null); throw it } // não deixar arquivo pela metade na galeria
    } else {
        // shortcut: Android 8/9 salva na pasta do app (Android/data/...), sem galeria; pedir WRITE_EXTERNAL_STORAGE se fizer falta.
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), subdir).apply { mkdirs() }
        src.copyTo(File(dir, name), overwrite = true)
    }
}
