package com.dani.lector.ui

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size

// Las cuentas del globo ampliado. En comun y no en el lector de Android por dos
// razones: deciden algo con casos de borde, y eso en este proyecto va en una
// funcion pura con su prueba al lado (EncuadreGloboTest); y el iPad las va a
// necesitar tal cual. Publicas porque :shared es otro modulo e internal no llega
// a :app.

/**
 * La escala de partida de una hoja: 1 si encaja a lo ancho, mas si "llenar" la
 * estira de arriba abajo. Aparte porque la usan DOS: el zoom, que parte de ella,
 * y el globo ampliado, que tiene que saber donde esta pintada la pagina para
 * salir de su sitio. Con dos cuentas, el dia que se tocara una el globo saldria
 * corrido en modo llenar.
 */
fun escalaBase(
    llenar: Boolean, proporcionPantalla: Float, paginas: Int, proporcionPagina: Float
): Float {
    if (!llenar || proporcionPagina <= 0f) return 1f
    return (proporcionPantalla * paginas / proporcionPagina).coerceIn(1f, 3f)
}

// LOS NUMEROS DEL GLOBO AMPLIADO, puestos a ojo mirando Play Books y para
// tocarlos cuando Dani lo pruebe. Ninguno sale de medir.
//
//  - GLOBO_OSCURO: el negro de la pagina de detras. Lo bastante para que el
//    globo se despegue, no tanto como para perder donde estas.
//  - GLOBO_ANCHO_MAX / GLOBO_ALTO_MAX: lo mas grande que se hace, en fraccion
//    de la pantalla. Menos de alto porque arriba y abajo tiene que seguir
//    viendose algo de la pagina.
//  - GLOBO_AMPLIACION_MAX: por encima de 2,5 veces, un globo pequeño enseña
//    los pixeles aunque salga del detalle.
//  - GLOBO_ENTRADA_MS: lo que tarda en salir de su sitio. Una vez por globo, y
//    nada sigue animandose despues: CONTEXTO.md cuenta lo que calentaba el movil.
const val GLOBO_OSCURO = 0.6f
const val GLOBO_ANCHO_MAX = 0.92f
const val GLOBO_ALTO_MAX = 0.80f
const val GLOBO_AMPLIACION_MAX = 2.5f
const val GLOBO_ENTRADA_MS = 200

/**
 * Donde se pinta el globo ampliado, en pixeles de pantalla, a partir de donde
 * esta pintado en la pagina.
 *
 * Lo mas grande que quepa en GLOBO_ANCHO_MAX x GLOBO_ALTO_MAX sin pasar de
 * GLOBO_AMPLIACION_MAX, y nunca mas pequeño que en la pagina: un globo que ya
 * ocupa media pantalla se queda como esta. Centrado sobre el propio globo —el
 * ojo ya esta ahi— y empujado hacia dentro si se saldria por un borde.
 */
fun encuadreGlobo(enPagina: Rect, pantalla: Size): Rect {
    val zoom = minOf(
        pantalla.width * GLOBO_ANCHO_MAX / enPagina.width,
        pantalla.height * GLOBO_ALTO_MAX / enPagina.height,
        GLOBO_AMPLIACION_MAX
    ).coerceAtLeast(1f)
    val ancho = enPagina.width * zoom
    val alto = enPagina.height * zoom
    // Si ni asi cabe (zoom 1 y un globo mas grande que la pantalla), al centro:
    // coerceIn con el minimo por encima del maximo lanza una excepcion.
    val cx = if (ancho >= pantalla.width) pantalla.width / 2
        else enPagina.center.x.coerceIn(ancho / 2, pantalla.width - ancho / 2)
    val cy = if (alto >= pantalla.height) pantalla.height / 2
        else enPagina.center.y.coerceIn(alto / 2, pantalla.height - alto / 2)
    return Rect(cx - ancho / 2, cy - alto / 2, cx + ancho / 2, cy + alto / 2)
}
