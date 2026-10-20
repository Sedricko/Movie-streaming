package app.lumicast.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// Tokens do docs/03-design-system.md — não invente cores fora desta lista.
private val Noite = Color(0xFF15121F)
private val Tela = Color(0xFF1F1B2D)
private val Nevoa = Color(0xFF2C2740)
private val Papel = Color(0xFFF1EEF8)
private val PapelSuave = Color(0xFFA8A2BD)
private val Feixe = Color(0xFFFFB454)
private val FeixeSuave = Color(0xFF3A2E1C)
private val Fonte = Color(0xFF4FA3F7)
private val Erro = Color(0xFFFF6B6B)
private val Ok = Color(0xFF5BD6A4)

private val NoiteClaro = Color(0xFFF7F5FB)
private val TelaClaro = Color(0xFFFFFFFF)
private val NevoaClaro = Color(0xFFECE8F5)
private val PapelClaro = Color(0xFF1B1730)
private val PapelSuaveClaro = Color(0xFF5C5675)
private val FeixeClaro = Color(0xFFB26A00)
private val FeixeSuaveClaro = Color(0xFFFFE3BD)
private val FonteClaro = Color(0xFF1B6FC2)
private val ErroClaro = Color(0xFFC62828)
private val OkClaro = Color(0xFF13795B)

@Immutable
data class LumiExtras(
    val fonte: Color,
    val ok: Color,
    val feixeSuave: Color,
)

val DarkExtras = LumiExtras(fonte = Fonte, ok = Ok, feixeSuave = FeixeSuave)
val LightExtras = LumiExtras(fonte = FonteClaro, ok = OkClaro, feixeSuave = FeixeSuaveClaro)

val DarkScheme: ColorScheme = darkColorScheme(
    primary = Feixe,
    onPrimary = Noite,
    primaryContainer = FeixeSuave,
    onPrimaryContainer = Feixe,
    secondary = Fonte,
    onSecondary = Noite,
    background = Noite,
    onBackground = Papel,
    surface = Tela,
    onSurface = Papel,
    surfaceVariant = Nevoa,
    onSurfaceVariant = PapelSuave,
    surfaceContainer = Tela,
    surfaceContainerHigh = Nevoa,
    surfaceContainerHighest = Nevoa,
    outline = PapelSuave,
    outlineVariant = Nevoa,
    error = Erro,
    onError = Noite,
)

val LightScheme: ColorScheme = lightColorScheme(
    primary = FeixeClaro,
    onPrimary = Color.White,
    primaryContainer = FeixeSuaveClaro,
    onPrimaryContainer = FeixeClaro,
    secondary = FonteClaro,
    onSecondary = Color.White,
    background = NoiteClaro,
    onBackground = PapelClaro,
    surface = TelaClaro,
    onSurface = PapelClaro,
    surfaceVariant = NevoaClaro,
    onSurfaceVariant = PapelSuaveClaro,
    surfaceContainer = TelaClaro,
    surfaceContainerHigh = NevoaClaro,
    surfaceContainerHighest = NevoaClaro,
    outline = PapelSuaveClaro,
    outlineVariant = NevoaClaro,
    error = ErroClaro,
    onError = Color.White,
)
