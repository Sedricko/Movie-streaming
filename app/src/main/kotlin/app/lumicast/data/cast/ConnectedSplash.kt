package app.lumicast.data.cast

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.ContextCompat
import app.lumicast.R
import java.io.ByteArrayOutputStream

/**
 * Tela "conectado" que o app manda para a TV ao escolhê-la (Full HD, PNG). A tela de espera do Chromecast é do
 * Google; sem receptor próprio registrado, mostrar uma imagem nossa é o jeito de a TV dizer "estou com o Lumicast".
 * Cores do design system: fundo Noite, marca âmbar, texto Papel.
 */
fun renderConnectedSplash(context: Context, tvName: String): ByteArray {
    val w = 1920
    val h = 1080
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.drawColor(Color.rgb(0x15, 0x12, 0x1F))

    val logo = 300
    ContextCompat.getDrawable(context, R.drawable.ic_logo)!!.apply {
        setBounds((w - logo) / 2, 250, (w + logo) / 2, 250 + logo)
        draw(c)
    }

    fun text(size: Float, color: Int, bold: Boolean) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
        letterSpacing = if (bold) -0.01f else 0f
    }
    c.drawText("Lumicast", w / 2f, 660f, text(104f, Color.rgb(0xF1, 0xEE, 0xF8), bold = true))
    c.drawText("Conectado · $tvName", w / 2f, 750f, text(44f, Color.rgb(0xFF, 0xB4, 0x54), bold = true))
    c.drawText("Escolha um vídeo no celular para começar.", w / 2f, 820f, text(34f, Color.rgb(0xA8, 0xA2, 0xBD), bold = false))

    return ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray().also { bmp.recycle() }
}
