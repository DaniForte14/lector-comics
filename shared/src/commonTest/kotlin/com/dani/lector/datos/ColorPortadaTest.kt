package com.dani.lector.datos

import androidx.compose.ui.graphics.Color
import com.dani.lector.ui.Colores
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `dominante` decide el color con el que se tiñe media interfaz, y **si se
 * tuerce no da ningun error**: las portadas salen de un color raro y nadie sabe
 * por que. Es la misma razon por la que se prueba `Colores`.
 *
 * SE PRUEBA LA SOBRECARGA QUE RECIBE UN ACCESOR, no la que recibe un
 * `ImageBitmap`. No es una comodidad: **un `ImageBitmap` no se puede construir
 * aqui**. En la JVM sale `Method createBitmap in android.graphics.Bitmap not
 * mocked`, y aunque en el simulador de iOS si funcionaria, `commonTest` corre
 * en las dos piernas y la prueba se quedaria roja en Windows para siempre.
 *
 * LOS COLORES SE CONSTRUYEN CON `Colores.desdeHsv` Y NO CON `Color(r, g, b)`,
 * para poder decir "tono 217.5, saturado, brillo medio" en vez de tres numeros
 * de los que no se deduce nada. `Colores` ya tiene su propia prueba de ida y
 * vuelta, asi que apoyarse en el aqui no esconde nada.
 *
 * LOS TONOS ELEGIDOS SON SIEMPRE EL CENTRO DE SU CASILLA (7.5, 22.5, 37.5...),
 * porque `dominante` agrupa en 24 casillas de 15 grados y devuelve el centro de
 * la que gana. Con un tono cualquiera habria que hacer la cuenta a mano en cada
 * asercion; con el centro, la casilla que entra es la que sale. **La casilla,
 * no el grado exacto**: ver [casilla] para por que eso no es lo mismo.
 */
class ColorPortadaTest {

    private fun cerca(a: Float, b: Float, m: Float = 0.01f) = abs(a - b) < m

    /** El tono de lo que devuelve `dominante`. */
    private fun tonoDe(c: Color) = Colores.aHsv(c).first

    /**
     * La casilla de tono a la que pertenece un color: 24 de 15 grados, las
     * mismas que usa `dominante`.
     *
     * SE COMPARAN CASILLAS Y NO GRADOS, Y NO ES PEREZA. La primera version de
     * esta prueba comparaba el tono con margen de 0.01 grados y **fallaron siete
     * de ocho** con el algoritmo funcionando bien. El motivo: `Color` de Compose
     * guarda el sRGB con 8 bits por canal, asi que `desdeHsv(217.5)` no vuelve
     * como 217.5 sino como **217.377** — la ida y vuelta pierde una decima de
     * grado, y ese redondeo esta tanto en el color que entra como en el que
     * sale. Ampliar el margen taparia el problema; comparar casillas dice lo que
     * la funcion decide de verdad, que es en que casilla cae.
     */
    private fun casilla(c: Color) = (tonoDe(c) / 15f).toInt().coerceIn(0, 23)

    /**
     * Una imagen de [lado] x [lado] donde el color de cada pixel lo decide
     * [pinta]. Es el accesor que espera `dominante`, con nombre.
     */
    private fun colorDe(lado: Int, pinta: (Int, Int) -> Color) =
        ColorPortada.dominante(lado, lado, pinta)

    // Los dos que se descartan, con el nombre de lo que son en una pagina de
    // comic de verdad.
    private val negroVineta = Colores.desdeHsv(0f, 0.5f, 0.10f)      // v < 0.15
    private val blancoBocadillo = Colores.desdeHsv(0f, 0.15f, 0.98f) // v > 0.95 y s < 0.20

    // ─────────────────────────── 1. un tono solo ───────────────────────────

    // Lo minimo que tiene que cumplir: si la portada entera es de un color, ese
    // es el color. Si esto falla, no hace falta mirar lo demas.
    @Test fun `una portada de un solo tono devuelve ese tono`() {
        val azul = Colores.desdeHsv(217.5f, 0.8f, 0.6f)
        val r = colorDe(40) { _, _ -> azul }

        assertEquals(casilla(azul), casilla(r), "casilla de tono")
        val (_, s, v) = Colores.aHsv(r)
        assertTrue(cerca(s, 0.8f), "saturacion: $s")
        assertTrue(cerca(v, 0.6f), "brillo: $v")
    }

    // ──────────────────── 2. blanco y negro: gris, sin tono ────────────────────

    /**
     * Una portada en blanco y negro tiene que dar un GRIS del brillo medio, no
     * un tono inventado. Es la rama `peso[mejor] <= 0`.
     *
     * Importa mas de lo que parece: `Colores.oscurecer` mira la saturacion para
     * decidir si le sube el color a la portada, y con un gris de verdad
     * (saturacion 0) la deja en paz. Si aqui saliera "rojo apagado", el tema
     * cogeria ese rojo y lo subiria hasta 0.55 de saturacion: una portada en
     * blanco y negro tiñendo la pantalla de rojo.
     */
    @Test fun `una portada en blanco y negro da gris y no un tono inventado`() {
        // Tablero de negro puro y blanco puro: brillo medio 0.5 exacto.
        val r = colorDe(40) { x, y -> if ((x + y) % 2 == 0) Color.Black else Color.White }

        val (_, s, v) = Colores.aHsv(r)
        assertTrue(cerca(s, 0f), "una portada gris no puede tener saturacion: $s")
        assertTrue(cerca(v, 0.5f, 0.05f), "brillo medio de negro y blanco: $v")
        assertTrue(cerca(r.red, r.green) && cerca(r.green, r.blue), "no es gris: $r")
    }

    // ────────────── 3. el filtro de negro de viñeta y blanco de bocadillo ──────────────

    /**
     * En una pagina de comic, el negro de las viñetas y el blanco de los
     * bocadillos son la mitad del papel y no dicen nada del tono. Aqui son el
     * 90% y el color de verdad el 10%, y tiene que ganar el 10%.
     *
     * LOS DOS DESCARTADOS LLEVAN COLOR A PROPOSITO —el negro con saturacion 0.5
     * y el blanco con 0.15— y no son gris puro. Con gris puro esta prueba no
     * valdria para nada: un gris pesa cero por su saturacion, asi que perderia
     * igual aunque alguien quitara el filtro. Tintados, si se quita el filtro
     * suman 0.147 contra 0.09 del azul y **la prueba se pone roja**, que es
     * justo para lo que esta.
     */
    @Test fun `el negro de vineta y el blanco de bocadillo no cuentan`() {
        val azul = Colores.desdeHsv(217.5f, 0.9f, 0.6f)
        // De cada diez filas: una de azul, cuatro de negro y cinco de blanco.
        // El azul es el 10% de la imagen y los descartados el 90%.
        val r = colorDe(40) { _, y ->
            when {
                y % 10 == 4 -> azul
                y % 2 == 0 -> negroVineta
                else -> blancoBocadillo
            }
        }

        assertEquals(casilla(azul), casilla(r), "tenia que ganar el azul")
    }

    // ──────────────── 4. la mancha grande le gana al detalle chillon ────────────────

    /**
     * Un detalle chillon en una esquina no puede decidir el color de la
     * portada. Aqui el 85% es un verde apagado y el 15% un magenta a tope.
     *
     * AVISO, Y NO ES UN FALLO DE LA PRUEBA SINO SU LIMITE: esta pasa igual si
     * alguien quita el peso por saturacion y cuenta pixeles a pelo, porque 85
     * es mas que 15 de las dos maneras. La que se pone roja si se quita el peso
     * es la de abajo. Van las dos porque son dos promesas distintas: esta dice
     * que manda el area, y la otra que a igualdad de area manda el color.
     */
    @Test fun `una mancha grande apagada gana a un detalle pequeno chillon`() {
        val verdeApagado = Colores.desdeHsv(112.5f, 0.30f, 0.55f)
        val magentaChillon = Colores.desdeHsv(307.5f, 1.0f, 0.60f)
        val r = colorDe(40) { _, y -> if (y % 20 < 17) verdeApagado else magentaChillon }

        assertEquals(casilla(verdeApagado), casilla(r), "tenia que ganar la mancha")
    }

    /**
     * A MENOS AREA PERO MAS COLOR, gana el color. El 60% es un rosa palido
     * —saturacion 0.15, apenas por encima del corte— y el 40% un magenta
     * saturado.
     *
     * ESTA ES LA QUE SE PONE ROJA SI ALGUIEN "SIMPLIFICA" EL PESO. Contando
     * pixeles a pelo ganaria el rosa 60 a 40; con el peso por saturacion el
     * magenta suma 0.38 contra 0.09 y gana. Es la promesa del comentario
     * "un rojo intenso pesa mas que un rosa palido".
     */
    @Test fun `a igualdad de area gana el mas saturado`() {
        val rosaPalido = Colores.desdeHsv(7.5f, 0.15f, 0.60f)
        val magenta = Colores.desdeHsv(307.5f, 0.95f, 0.60f)
        val r = colorDe(40) { _, y -> if (y % 10 < 6) rosaPalido else magenta }

        assertEquals(casilla(magenta), casilla(r), "tenia que ganar el saturado")
    }

    /**
     * La otra mitad del peso: lo cerca que queda del brillo medio. El 60% es un
     * color casi apagado (brillo 0.20, justo por encima del corte) y el 40%
     * esta en el brillo medio.
     *
     * Tambien se pone roja si se quita ese factor: sin el ganaria el oscuro 60
     * a 40. Con el, el oscuro pesa 0.288 y el otro 0.32.
     */
    @Test fun `a igualdad de saturacion gana el que esta en el brillo medio`() {
        val casiApagado = Colores.desdeHsv(277.5f, 0.8f, 0.20f)
        val brilloMedio = Colores.desdeHsv(52.5f, 0.8f, 0.60f)
        val r = colorDe(40) { _, y -> if (y % 10 < 6) casiApagado else brilloMedio }

        assertEquals(casilla(brilloMedio), casilla(r), "tenia que ganar el del brillo medio")
    }

    // ─────────────────────── 5. el salto en imagenes pequenas ───────────────────────

    /**
     * `salto` sale de `min(ancho, alto) / MUESTRA`, y con una imagen de menos de
     * 40 px esa division da CERO. El `maxOf(1, ...)` es lo unico que lo impide.
     *
     * QUE PASA SI SE CAE ESE `maxOf`: `x += 0` es un bucle infinito, asi que
     * esta prueba no se pondria roja, **se quedaria colgada**. Es feo y es la
     * unica señal que hay; vale mas eso que no mirarlo.
     *
     * El pixel de color esta en (7, 5), que no es multiplo de nada: si el salto
     * saliera mayor que uno, se lo saltaria y la imagen quedaria en negro puro,
     * o sea gris por la rama de "sin color".
     */
    @Test fun `en una imagen de menos de 40 px se miran todos los pixeles`() {
        val naranja = Colores.desdeHsv(37.5f, 0.9f, 0.6f)
        val r = colorDe(12) { x, y -> if (x == 7 && y == 5) naranja else Color.Black }

        assertEquals(casilla(naranja), casilla(r), "no se miro el pixel (7,5)")
    }

    /** Y con una grande el salto es mayor que uno y tiene que seguir acertando. */
    @Test fun `en una imagen grande el salto no cambia el resultado`() {
        val azul = Colores.desdeHsv(217.5f, 0.8f, 0.6f)
        val r = colorDe(400) { _, _ -> azul }

        assertEquals(casilla(azul), casilla(r), "casilla de tono")
    }
}
