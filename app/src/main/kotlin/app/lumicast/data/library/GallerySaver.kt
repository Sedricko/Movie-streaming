package app.lumicast.data.library

import android.content.ContentUris
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
    // só conta como extensão o que parece uma (letra + até 4 letras/números): "Aula 1.5" não tem
    if (Regex("""\.[A-Za-z][A-Za-z0-9]{1,4}$""").containsMatchIn(clean)) return clean
    val ext = when (mimeType) { "video/x-matroska" -> "mkv"; "video/webm" -> "webm"; "video/quicktime" -> "mov"; else -> "mp4" }
    return "${clean.trimEnd('.')}.$ext"
}

internal fun safeFolderName(name: String) = name.replace(Regex("""[\\/:*?"<>|\n\r]"""), "_").trim().trimStart('.').ifBlank { "Pasta" }.take(60)

/**
 * O arquivo já está em Downloads/Lumicast/<pasta> (mesmo nome e tamanho)? Devolve a Uri ou null. Vale mesmo quando o app
 * perdeu a lembrança de ter salvado (reinstalou, limpou dados): sem isso baixava de novo e o Android criava "nome (1).mp4".
 */
fun findInDownloads(context: Context, folder: String, title: String, mimeType: String, size: Long): String? {
    val name = safeFileName(title, mimeType)
    val sub = "Lumicast/${safeFolderName(folder)}"
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        val f = File(File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), sub), name)
        return f.takeIf { it.isFile && (size <= 0 || it.length() == size) }?.let { Uri.fromFile(it).toString() }
    }
    val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
    context.contentResolver.query(
        collection,
        arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.SIZE),
        "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.IS_PENDING}=0",
        arrayOf(name, "${Environment.DIRECTORY_DOWNLOADS}/$sub/"),
        null,
    )?.use { c ->
        while (c.moveToNext()) if (size <= 0 || c.getLong(1) == size) return ContentUris.withAppendedId(collection, c.getLong(0)).toString()
    }
    return null
}

/** O arquivo salvo ainda existe? (o histórico guarda a Uri; o usuário pode ter apagado o arquivo pelo gerenciador). */
fun savedExists(context: Context, uri: String): Boolean = try {
    val u = Uri.parse(uri)
    if (u.scheme == "file") File(u.path.orEmpty()).let { it.isFile && it.length() > 0 }
    // Abre de verdade: o registro na mídia do Android pode continuar depois de o gerenciador de arquivos apagar o arquivo.
    else context.contentResolver.openFileDescriptor(u, "r")?.use { it.statSize > 0 } ?: false
} catch (e: java.io.FileNotFoundException) {
    false // sumiu de verdade
} catch (e: Exception) {
    true // não deu para saber (armazenamento indisponível, permissão): não apagar o "Baixado" por uma falha passageira
}

/** Apaga o arquivo salvo em Downloads (Uri de MediaStore ou arquivo). Sem erro = apagou ou já não existia; o Android pode recusar (SecurityException). */
fun deleteSaved(context: Context, uri: String) {
    val u = Uri.parse(uri)
    if (u.scheme == "file") u.path?.let { File(it).delete() }
    else if (context.contentResolver.delete(u, null, null) == 0 && savedExists(context, uri)) {
        throw java.io.IOException("O Android não apagou o arquivo.") // 0 linhas e o arquivo ainda abre: não é sucesso
    }
}

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
        }.getOrNull()?.let { if (it.sizeBytes > 0) it else { it.close(); null } } // fecha o descritor se o arquivo está vazio
    }
}
