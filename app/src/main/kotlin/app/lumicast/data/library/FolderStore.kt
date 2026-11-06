package app.lumicast.data.library

import android.content.Context
import app.lumicast.domain.TgVideo
import app.lumicast.domain.VideoFolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Pastas do usuário, salvas como JSON nas preferências do app. */
class FolderStore(context: Context, prefsName: String) {
    private val prefs = context.getSharedPreferences(prefsName, 0)
    private val _folders = MutableStateFlow(decodeFolders(prefs.getString(KEY, null)))
    val folders: StateFlow<List<VideoFolder>> = _folders.asStateFlow()

    fun update(change: (List<VideoFolder>) -> List<VideoFolder>) = synchronized(this) {
        _folders.value = change(_folders.value)
        prefs.edit().putString(KEY, encodeFolders(_folders.value)).apply()
    }

    private companion object { const val KEY = "folders" }
}

internal fun encodeFolders(folders: List<VideoFolder>): String = JSONArray().apply {
    folders.forEach { f ->
        put(JSONObject().put("id", f.id).put("name", f.name).put("videos", JSONArray().apply {
            f.videos.forEach { v ->
                put(
                    JSONObject().put("fileId", v.fileId).put("chatId", v.chatId).put("messageId", v.messageId)
                        .put("title", v.title).put("durationSec", v.durationSec).put("sizeBytes", v.sizeBytes)
                        .put("mimeType", v.mimeType).put("thumbnailFileId", v.thumbnailFileId ?: JSONObject.NULL),
                )
            }
        }))
    }
}.toString()

internal fun decodeFolders(json: String?): List<VideoFolder> {
    if (json.isNullOrBlank()) return emptyList()
    val arr = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList() // JSON corrompido não pode travar o app
    // Um registro ruim não pode apagar os outros (a fila usa o mesmo formato): cada pasta e cada vídeo é lido sozinho.
    return (0 until arr.length()).mapNotNull { i ->
        runCatching {
            val f = arr.getJSONObject(i)
            val vs = f.getJSONArray("videos")
            VideoFolder(f.getString("id"), f.getString("name"), (0 until vs.length()).mapNotNull { j -> runCatching { decodeVideo(vs.getJSONObject(j)) }.getOrNull() })
        }.getOrNull()
    }
}

private fun decodeVideo(v: JSONObject) = TgVideo(
    fileId = v.getInt("fileId"), chatId = v.getLong("chatId"), messageId = v.getLong("messageId"),
    title = v.getString("title"), durationSec = v.getInt("durationSec"), sizeBytes = v.getLong("sizeBytes"),
    mimeType = v.getString("mimeType"),
    thumbnailFileId = if (v.isNull("thumbnailFileId")) null else v.getInt("thumbnailFileId"),
)
