package app.lumicast.data.library

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import app.lumicast.domain.StreamSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.nio.ByteBuffer
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

/** Copia o vídeo baixado para Downloads/Lumicast/<pasta> (aparece na galeria). Devolve a Uri do arquivo salvo. */
fun saveToDownloads(context: Context, src: File, folder: String, title: String, mimeType: String): String {
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
        return uri.toString()
    } else {
        // shortcut: Android 8/9 salva na pasta do app (Android/data/...), sem galeria; pedir WRITE_EXTERNAL_STORAGE se fizer falta.
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), subdir).apply { mkdirs() }
        val dest = File(dir, name)
        src.copyTo(dest, overwrite = true)
        return Uri.fromFile(dest).toString()
    }
}

/** Vídeo que já está salvo no celular: a TV lê direto do arquivo, sem baixar do Telegram de novo. */
class SavedVideoSource private constructor(private val pfd: ParcelFileDescriptor, override val mimeType: String) : StreamSource {
    private val channel = FileInputStream(pfd.fileDescriptor).channel
    override val sizeBytes: Long = pfd.statSize
    override val downloadedFraction: Float get() = 1f

    // FileChannel.read(dst, posição) não mexe na posição do canal: seguro com a TV lendo em duas conexões.
    override suspend fun read(offset: Long, buffer: ByteArray, length: Int): Int = withContext(Dispatchers.IO) {
        if (offset >= sizeBytes || length <= 0) -1 else channel.read(ByteBuffer.wrap(buffer, 0, length), offset)
    }

    override fun close() {
        runCatching { channel.close(); pfd.close() }
    }

    companion object {
        /** null se o arquivo sumiu (o usuário apagou da galeria, por exemplo). */
        fun open(context: Context, uri: String, mimeType: String): SavedVideoSource? = runCatching {
            SavedVideoSource(context.contentResolver.openFileDescriptor(Uri.parse(uri), "r")!!, mimeType)
        }.getOrNull()?.takeIf { it.sizeBytes > 0 }
    }
}
