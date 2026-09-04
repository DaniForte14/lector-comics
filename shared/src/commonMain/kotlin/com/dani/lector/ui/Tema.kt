package com.dani.lector.ui

import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * EL INTERRUPTOR DE ESTETICA.
 *
 * Cambiar esta linea cambia la app entera: color, forma y tipografia. Lo de
 * iOS no esta comentado ni borrado, esta aqui al lado; comentar codigo es
 * dejarlo pudrirse, y asi las dos opciones se mantienen vivas y se puede
 * volver en un segundo.
 *
 * Funciona porque todo el diseño pasa por los tokens de este fichero: los
 * nombres (Tinta, Panel, Hueso, FormaTarjeta...) son los mismos en las dos
 * esteticas y las pantallas no saben cual esta puesta.
 */
enum class Estilo { IOS, CYBERPUNK }

val ESTILO = Estilo.CYBERPUNK

private val cyber = ESTILO == Estilo.CYBERPUNK

// ─────────────────────────── COLOR ───────────────────────────
//
// IOS: negro puro, grises neutros, UN acento. El color es informacion.
//
// CYBERPUNK (2077): el amarillo acido es la marca de la casa, y no se usa como
//      detalle sino como MANCHA: bloques enteros de amarillo con el texto en
//      negro encima. El cian y el rojo son los secundarios. El fondo es negro
//      con un punto de calor, no azulado: en el juego todo parece impreso sobre
//      metal sucio, no iluminado por un rotulo frio.

val Tinta     = if (cyber) Color(0xFF08070A) else Color(0xFF000000)
val Panel     = if (cyber) Color(0xFF15140F) else Color(0xFF1C1C1E)
val PanelAlto = if (cyber) Color(0xFF211F16) else Color(0xFF2C2C2E)
val Linea     = if (cyber) Color(0xFF4A431A) else Color(0xFF38383A)
val Hueso     = if (cyber) Color(0xFFF3F0E0) else Color(0xFFFFFFFF)
val Tenue     = if (cyber) Color(0xFFB8B08A) else Color(0x99EBEBF5)
val Apagado   = if (cyber) Color(0xFF7A7357) else Color(0x7AEBEBF5)

/** El acento: lo que se puede tocar. El amarillo de 2077. */
val Acento    = if (cyber) Color(0xFFFCEE0A) else Color(0xFFE11D2E)

/**
 * Lo que va ENCIMA del acento.
 *
 * Sobre ese amarillo el texto blanco es ilegible. En el juego siempre va en
 * negro, y ese contraste bestia es justo lo que le da el aspecto de rotulo
 * industrial.
 */
val SobreAcento = if (cyber) Color(0xFF08070A) else Color(0xFFFFFFFF)

/** El segundo neon: cian, para lo informativo. */
val Cian      = if (cyber) Color(0xFF02D7F2) else Color(0xFF0A84FF)

/** Alarma y destructivo. */
val Alarma    = if (cyber) Color(0xFFFF003C) else Color(0xFFFF453A)

// ─────────────────────────── FORMA ───────────────────────────
//
// Aqui esta media estetica y es lo que mas distingue a 2077 de cualquier otra
// cosa oscura con neones: las esquinas no se redondean, se CORTAN en diagonal.
// Un chaflan. Compose lo trae de serie con CutCornerShape, asi que sale gratis.
//
// Se corta en diagonales opuestas (arriba a la derecha y abajo a la izquierda),
// que es como estan casi todos los paneles del juego.

val FormaTarjeta: Shape = if (cyber)
    CutCornerShape(topStart = 0.dp, topEnd = 12.dp, bottomEnd = 0.dp, bottomStart = 12.dp)
else RoundedCornerShape(12.dp)

val FormaBoton: Shape = if (cyber)
    CutCornerShape(topStart = 0.dp, topEnd = 14.dp, bottomEnd = 0.dp, bottomStart = 14.dp)
else RoundedCornerShape(12.dp)

/** En las portadas el chaflan es pequeño: comerse una esquina de dibujo canta. */
val FormaCaratula: Shape = if (cyber)
    CutCornerShape(topStart = 0.dp, topEnd = 8.dp, bottomEnd = 0.dp, bottomStart = 0.dp)
else RoundedCornerShape(10.dp)

val FormaChapa: Shape = if (cyber)
    CutCornerShape(topStart = 0.dp, topEnd = 5.dp, bottomEnd = 0.dp, bottomStart = 5.dp)
else RoundedCornerShape(6.dp)

/**
 * La pista del interruptor, que es lo unico que lleva otra forma DENTRO.
 *
 * El chaflan de fuera es el de dentro MAS el hueco que los separa (5 + 3). Con
 * los dos iguales, el hueco se estrecha justo en las esquinas cortadas y el
 * interruptor se ve torcido sin que se sepa por que. Es la regla de las formas
 * concentricas, y aqui es el unico sitio de la app donde hay dos anidadas.
 */
val FormaPista: Shape = if (cyber)
    CutCornerShape(topStart = 0.dp, topEnd = 8.dp, bottomEnd = 0.dp, bottomStart = 8.dp)
else RoundedCornerShape(9.dp)

/**
 * Cuanto se apaga la portada de un comic ya leido.
 *
 * VUELVE UNA DECISION QUE SE HABIA DESHECHO, y conviene saber por que las dos
 * veces. La capa negra al 70% se quito el 03/09/2026 porque con la rejilla llena
 * de leidos **la pantalla entera se apagaba** y el catalogo dejaba de parecer un
 * catalogo; se cambio por la chapa de leido sola. Dani lo uso y pidio lo
 * contrario (04/09/2026): *"los preferia cuando estaban difuminados"*, y que la
 * chapa sola es casi imperceptible.
 *
 * Asi que vuelve el velo, **pero no al 70% que causo el problema**: a 0,55, que
 * apaga lo suficiente para distinguirlo de un vistazo y deja la portada
 * reconocible. La chapa se queda ademas, y encima de un fondo apagado se lee
 * mucho mejor que antes.
 *
 * ES UN NUMERO PARA TOCAR: si la rejilla vuelve a parecer apagada, se baja aqui
 * y vale para la rejilla y para los carruseles a la vez.
 */
val VELO_LEIDO = 0.55f

/** Grosor del filo de las caratulas: en cyberpunk se ve, en iOS casi no. */
val FiloAncho = if (cyber) 1.dp else 0.5.dp
val FiloColor = if (cyber) Color(0x66FCEE0A) else Color(0x1AFFFFFF)

// ─────────────────────────── TIPOGRAFIA ───────────────────────────
//
// IOS: interletrado NEGATIVO en los cuerpos grandes. Es la mitad del efecto.
//
// 2077: lo contrario. Interletrado ABIERTO, casi todo en negrita, y
//      monoespaciada en cualquier cosa que sea un dato (años, numeros, rutas,
//      diagnosticos). La monoespaciada no es capricho: alinea las cifras en
//      columna y da el aire de terminal SIN meter ninguna fuente en el APK,
//      porque FontFamily.Monospace ya la trae el sistema. Es la respuesta a lo
//      que no se pudo hacer con SF Pro.

private val Mono = FontFamily.Monospace

object Tipo {
    val grande = if (cyber)
        TextStyle(fontSize = 29.sp, lineHeight = 35.sp,
                  fontWeight = FontWeight.Black, letterSpacing = 2.sp)
    else
        TextStyle(fontSize = 34.sp, lineHeight = 41.sp,
                  fontWeight = FontWeight.Bold, letterSpacing = (-1.0).sp)

    val titulo = if (cyber)
        TextStyle(fontSize = 20.sp, lineHeight = 26.sp,
                  fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
    else
        TextStyle(fontSize = 22.sp, lineHeight = 28.sp,
                  fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)

    val subtitulo = if (cyber)
        TextStyle(fontSize = 17.sp, lineHeight = 23.sp,
                  fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
    else
        TextStyle(fontSize = 20.sp, lineHeight = 25.sp,
                  fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp)

    val destacado = if (cyber)
        TextStyle(fontSize = 16.sp, lineHeight = 21.sp,
                  fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
    else
        TextStyle(fontSize = 17.sp, lineHeight = 22.sp,
                  fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp)

    val cuerpo = if (cyber)
        TextStyle(fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.3.sp)
    else
        TextStyle(fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = (-0.4).sp)

    val secundario = if (cyber)
        TextStyle(fontSize = 14.sp, lineHeight = 20.sp,
                  fontWeight = FontWeight.Medium, letterSpacing = 0.6.sp)
    else
        TextStyle(fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = (-0.2).sp)

    // los datos, en monoespaciada
    val pie = if (cyber)
        TextStyle(fontSize = 12.sp, lineHeight = 18.sp,
                  fontFamily = Mono, letterSpacing = 0.6.sp)
    else
        TextStyle(fontSize = 13.sp, lineHeight = 18.sp)

    val minuscula = if (cyber)
        TextStyle(fontSize = 11.sp, lineHeight = 16.sp,
                  fontFamily = Mono, letterSpacing = 1.sp)
    else
        TextStyle(fontSize = 12.sp, lineHeight = 16.sp)
}

// ─────────────────────── COLOR AMBIENTE ───────────────────────
//
// El color que sale de la portada (ver datos/ColorPortada) NO se pinta crudo:
// pasa por aqui, como todo lo demas. Si se pintara directo, el interruptor de
// estetica dejaria de mandar y una portada rosa chicle romperia el aspecto de
// 2077 en cuanto abrieras ese tomo.
//
// Por eso las dos esteticas lo usan con fuerza muy distinta:
//
//  - En IOS se permite el degradado rico de Apple Music: el color ocupa arriba
//    y se funde a negro puro. Alli el fondo negro es un lienzo y el color es
//    informacion, asi que puede mandar.
//  - En 2077 es un TINTE, no un baño. El fondo tiene que seguir pareciendo
//    metal sucio impreso, no una pantalla de color. Y sobre todo: NO se deja
//    subir el brillo, porque lo unico que no se puede tocar es que el amarillo
//    del acento destaque sobre el fondo. Un ambiente fuerte se lo comeria.

/** Degradado de fondo [arriba, abajo] para una pantalla teñida por su portada. */
fun ambienteFondo(base: Color?): List<Color> = when {
    base == null -> listOf(Tinta, Tinta)
    cyber -> listOf(Colores.oscurecer(base, 0.20f), Tinta)
    else  -> listOf(Colores.oscurecer(base, 0.30f), Color.Black)
}

/**
 * Velo para la barra de arriba del visor: del tinte solido a transparente.
 *
 * Va en degradado y no en color plano porque asi el color ocupa MUCHA mas
 * pantalla sin tapar la pagina, que es lo que hace el efecto en el video que
 * dio origen a esto. El texto vive en la parte de arriba, que es la opaca.
 */
fun ambienteVelo(base: Color?, desdeArriba: Boolean = true): Brush {
    val c = ambienteBarra(base)
    // La tira de miniaturas necesita MAS opacidad que la barra de arriba: alli
    // solo hay texto, y aqui las miniaturas tienen que recortarse contra algo o
    // se confunden con la pagina del comic que hay detras.
    val opaco = if (desdeArriba) 0.97f else 0.99f
    val medio = if (desdeArriba) 0.86f else 0.94f
    val paradas = listOf(c.copy(alpha = opaco), c.copy(alpha = medio), Color.Transparent)
    return Brush.verticalGradient(if (desdeArriba) paradas else paradas.reversed())
}

/**
 * Color solido para las barras que se pintan ENCIMA de una pagina de comic.
 *
 * Lleva alfa a proposito: es lo que sustituye al negro al 80% de siempre. Sin
 * portada legible devuelve exactamente ese negro, asi que el visor se ve como
 * hasta ahora y esto no puede romper nada.
 */
fun ambienteBarra(base: Color?): Color = when {
    base == null -> Color(0xCC000000)
    // Se oscurece por BRILLO, no mezclando contra negro. El primer intento
    // mezclaba y no se veia nada en el movil: mezclar mata la saturacion y
    // deja un gris. Ver Colores.oscurecer.
    cyber -> Colores.oscurecer(base, 0.15f).copy(alpha = 0.93f)
    else  -> Colores.oscurecer(base, 0.20f).copy(alpha = 0.90f)
}

@Composable
fun TemaLector(contenido: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Acento,
            background = Tinta,
            surface = Panel,
            surfaceVariant = PanelAlto,
            outline = Linea,
            onPrimary = SobreAcento,
            onBackground = Hueso,
            onSurface = Hueso
        ),
        content = contenido
    )
}

