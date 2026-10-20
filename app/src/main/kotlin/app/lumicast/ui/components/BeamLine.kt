package app.lumicast.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp

/**
 * Linha do Feixe: liga o celular à TV. O ponto de luz só se move enquanto algo está chegando ([buffering]);
 * movimento = "carregando". Conectado e estável = linha cheia e parada.
 */
@Composable
fun BeamLine(buffering: Boolean, modifier: Modifier = Modifier) {
    val reduceMotion = rememberReduceMotion()
    val color = MaterialTheme.colorScheme.primary
    val moving = buffering && !reduceMotion

    val progress: Float = if (moving) {
        val transition = rememberInfiniteTransition(label = "linha")
        val p by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1_600, easing = LinearEasing), RepeatMode.Restart),
            label = "ponto",
        )
        p
    } else 0f

    Canvas(modifier = modifier.fillMaxWidth().height(20.dp)) {
        val y = size.height / 2f
        val stroke = 2.dp.toPx()
        drawLine(
            color = if (buffering) color.copy(alpha = 0.35f) else color,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        if (buffering) {
            val x = if (moving) size.width * progress else size.width / 2f
            drawCircle(color = color, radius = 5.dp.toPx(), center = Offset(x, y))
        }
    }
}
