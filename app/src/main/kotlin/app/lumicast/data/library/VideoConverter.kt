package app.lumicast.data.library

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.Composition
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Converte qualquer vídeo que o Android consiga ler (MKV, HEVC, AC3…) para MP4 H.264 + AAC, o formato que
 * toda TV aceita. Usa o chip de vídeo do celular (Media3 Transformer). Converte o arquivo inteiro: o MP4 só
 * fica tocável quando termina, então não dá para transmitir durante a conversão.
 */
class VideoConverter(private val context: Context) {

    suspend fun toMp4(input: Uri, output: File, onProgress: (Int) -> Unit): File = withContext(Dispatchers.Main) {
        output.parentFile?.mkdirs()
        val part = File(output.path + ".part")
        coroutineScope {
            var transformer: Transformer? = null
            val poll = launch {
                val holder = ProgressHolder()
                while (true) {
                    delay(500)
                    if (transformer?.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) onProgress(holder.progress)
                }
            }
            try {
                suspendCancellableCoroutine { cont ->
                    val t = Transformer.Builder(context)
                        .setVideoMimeType(MimeTypes.VIDEO_H264)
                        .setAudioMimeType(MimeTypes.AUDIO_AAC)
                        .addListener(object : Transformer.Listener {
                            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                                cont.resume(Unit)
                            }

                            override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                                cont.resumeWithException(exportException)
                            }
                        })
                        .build()
                    transformer = t
                    cont.invokeOnCancellation { t.cancel() }
                    t.start(MediaItem.fromUri(input), part.path)
                }
                if (!part.renameTo(output)) error("Não foi possível salvar o vídeo convertido.")
                output
            } finally {
                poll.cancel()
                part.delete() // sobra de conversão cancelada ou com erro
            }
        }
    }
}
