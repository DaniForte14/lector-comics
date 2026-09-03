package com.dani.lector.ui

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Aritmetica de color, sin Android.
 *
 * `android.graphics.Color.colorToHSV` y `HSVToColor` no existen fuera de
 * Android, y de ellas depende lo que tiñe la pantalla del color de la portada
 * que estas leyendo. Asi que se escriben aqui, y **con pruebas**: son
 * conversiones con casos de borde —el gris, el rojo que da la vuelta en 360, el
 * negro— que si se tuercen no dan ningun error, solo colores raros.
 */
object Colores {

    /** Tono en [0,360), saturacion y valor en [0,1]. Igual que colorToHSV. */
    fun aHsv(c: Color): Triple<Float, Float, Float> {
        val r = c.red; val v = c.green; val a = c.blue
        val alto = max(r, max(v, a))
        val bajo = min(r, min(v, a))
        val d = alto - bajo

        // UN GRIS NO TIENE TONO. Devolver 0 es la convencion de Android y
        // ademas es lo que hace falta: `oscurecer` mira la saturacion para no
        // inventarle color a una portada en blanco y negro.
        val h = when {
            d == 0f -> 0f
            alto == r -> 60f * (((v - a) / d) % 6f)
            alto == v -> 60f * (((a - r) / d) + 2f)
            else      -> 60f * (((r - v) / d) + 4f)
        }
        return Triple(
            if (h < 0f) h + 360f else h,
            if (alto == 0f) 0f else d / alto,
            alto
        )
    }

    /** Lo contrario. Alfa opaco, igual que HSVToColor. */
    fun desdeHsv(h: Float, s: Float, v: Float): Color {
        val hh = ((h % 360f) + 360f) % 360f
        val c = v * s
        val x = c * (1f - abs((hh / 60f) % 2f - 1f))
        val m = v - c
        val (r, g, b) = when ((hh / 60f).toInt()) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        return Color(r + m, g + m, b + m)
    }

    /**
     * El mismo color, oscuro y SIN perder el tono.
     *
     * Mezclar contra negro parece lo obvio y es justo lo que no funciona: al
     * mezclar se pierde saturacion, y un verde al 20% contra un negro casi puro
     * da un gris con una idea de verde que en pantalla NO SE VE. Fue el primer
     * intento y se quedo invisible en el movil.
     *
     * Aqui se baja el BRILLO y se sostiene la saturacion, que es lo que deja un
     * verde oscuro distinguible de un rojo oscuro.
     *
     * OJO con [saturacionMinima]: NO se aplica a los grises. Si la portada era
     * en blanco y negro, forzarle saturacion seria inventarle un tono, que es
     * exactamente lo que el calculo del color dominante se cuida de no hacer.
     */
    fun oscurecer(base: Color, brillo: Float, saturacionMinima: Float = 0.55f): Color {
        val (h, s0, _) = aHsv(base)
        val s = if (s0 < 0.10f) s0 else max(s0, saturacionMinima)
        return desdeHsv(h, s, brillo.coerceIn(0f, 1f))
    }
}
