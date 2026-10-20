package app.lumicast.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

/** Raios por papel (não usar um raio único): miniatura 14 · cartão 20 · folha 28. */
object LumiShapes {
    val Thumb = RoundedCornerShape(14.dp)
    val Card = RoundedCornerShape(20.dp)
    val Sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
}

private val MaterialShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = LumiShapes.Thumb,
    medium = LumiShapes.Card,
    large = LumiShapes.Sheet,
    extraLarge = LumiShapes.Sheet,
)

val LocalLumiExtras = staticCompositionLocalOf { DarkExtras }

object LumiTheme {
    val extras: LumiExtras
        @Composable @ReadOnlyComposable get() = LocalLumiExtras.current
}

@Composable
fun LumicastTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLumiExtras provides if (darkTheme) DarkExtras else LightExtras) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme, // cores dinâmicas desligadas de propósito
            typography = LumiTypography,
            shapes = MaterialShapes,
            content = content,
        )
    }
}
