package app.lumicast.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * O Feixe — assinatura do app. Círculo âmbar de 56 dp.
 * Em repouso: sólido. Conectando ([connecting]): ícone piscando. Conectado ([active]): selo fixo no canto
 * (aparece mesmo com animações reduzidas) + anel que pulsa devagar (2,4 s).
 */
@Composable
fun BeamButton(
    active: Boolean,
    onClick: () -> Unit,
    connecting: Boolean = false,
    description: String,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = rememberReduceMotion()
    Box(modifier = modifier.size(56.dp), contentAlignment = Alignment.Center) {
        if (active && !reduceMotion) PulseRing()
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .size(56.dp)
                .semantics { contentDescription = description },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (active) Icons.Rounded.CastConnected else Icons.Rounded.Cast,
                    contentDescription = null,
                    modifier = if (connecting && !reduceMotion) Modifier.alpha(blink()) else Modifier,
                )
            }
        }
        if (active && !connecting) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .size(16.dp)
                    .border(2.dp, MaterialTheme.colorScheme.background, CircleShape)
                    .padding(2.dp)
                    .background(MaterialTheme.colorScheme.tertiary, CircleShape),
            )
        }
    }
}

@Composable
private fun blink(): Float {
    val t = rememberInfiniteTransition(label = "conectando")
    val a by t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "pisca")
    return a
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.PulseRing() {
    val transition = rememberInfiniteTransition(label = "feixe")
    val p by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2_400, easing = LinearEasing), RepeatMode.Restart),
        label = "anel",
    )
    Box(
        modifier = Modifier
            .matchParentSize()
            .scale(1f + 0.5f * p)
            .alpha(1f - p)
            .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
    )
}
