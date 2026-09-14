package com.dani.lector.ui

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.tan

/**
 * El doblez de una hoja al pasar pagina, en pixeles de la hoja (0,0 arriba a
 * la izquierda).
 *
 * @property plana lo que queda plano de la hoja de encima. Vacia = ya ha
 *   pasado entera y solo se ve la de debajo.
 * @property solapa el trozo doblado, ya en su sitio: es el REVERSO de la hoja,
 *   asi que se pinta con la imagen reflejada sobre el eje. Vacia = sin doblez.
 * @property ejeDesde, ejeHasta dos puntos de la linea del doblez: con ellos se
 *   refleja la imagen al pintar la solapa y se pone la sombra.
 */
data class Pliegue(
    val plana: List<Offset>,
    val solapa: List<Offset>,
    val ejeDesde: Offset,
    val ejeHasta: Offset
)

/**
 * La hoja que se dobla al pasar pagina, como el "efecto 3D" de Google Play
 * Libros. Decidido por Dani el 14/09/2026: siempre, sin ajuste. Ver
 * `docs/DISENO.md` §25.
 *
 * Aqui solo hay GEOMETRIA —donde cae el doblez, que queda plano, donde va la
 * solapa—, pura y con pruebas, y en comun para que el iPad la use igual. Pintar
 * es de quien la llama.
 *
 * EL MODELO ES EL DE COGER LA ESQUINA CON EL PULGAR. La esquina de abajo a la
 * derecha se lleva por un arco hasta el borde izquierdo: sube y vuelve a bajar,
 * como la de una hoja de verdad que pasa por el aire. El eje del doblez es la
 * MEDIATRIZ entre donde estaba la esquina y donde esta ahora, que es justo lo
 * que hace el papel: reflejar la esquina sobre el eje la deja donde la tiene el
 * dedo. De ahi sale todo lo demas —que primero se levanta solo la esquina, que
 * el eje se inclina y se endereza—, sin tener que ajustarlo a mano.
 *
 * LA SOLAPA SE SALE DE LA HOJA POR LA IZQUIERDA en la segunda mitad: la esquina
 * reflejada cruza el borde izquierdo a medio camino y al final la hoja entera
 * queda al otro lado de x = 0. Se deja asi, sin recortar: en un visor de una
 * sola pagina no hay lomo donde apoyarla, la hoja se va de la pantalla, y
 * recortarla a la pantalla es cosa de quien pinta. Por arriba tambien asoma un
 * poco cuando la esquina va por lo alto del arco.
 */
object HojaQueSeDobla {

    /**
     * Grados sobre la vertical del eje al empezar. Cogida por la esquina, una
     * hoja de papel se dobla en una diagonal empinada, de unos 30°: mas tumbada
     * pareceria que se arranca la esquina, mas derecha pareceria que se pasa
     * cogida por el canto. A mitad de camino baja sola a unos 20° y al final es
     * vertical, porque el arco de la esquina ya no sube: la hoja se va por el
     * borde izquierdo derecha.
     */
    private const val ANGULO_INICIAL = 30.0

    /**
     * Cuanto sube la esquina a mitad de camino, en anchos de hoja. Sale del
     * angulo: cerca del principio el eje se inclina h/s sobre la vertical, con
     * h = ancho * ELEVACION * sen(pi * avance) y s = 2 * ancho * avance, que
     * tiende a ELEVACION * pi / 2. Para 30° da 0,37: la esquina sube un tercio
     * largo del ancho, que es lo que se ve al levantar una hoja con el pulgar.
     */
    private val ELEVACION = (2 * tan(ANGULO_INICIAL * PI / 180) / PI).toFloat()

    /**
     * @param avance 0 = hoja plana, 1 = hoja pasada entera. Fuera de [0,1] se
     *   acota.
     * @param adelante true: la hoja se levanta por el borde derecho (se va a la
     *   pagina siguiente); false: por el izquierdo (se vuelve a la anterior).
     */
    fun pliegue(ancho: Float, alto: Float, avance: Float, adelante: Boolean): Pliegue {
        val hacia = pliegueAdelante(ancho, alto, avance.coerceIn(0f, 1f))
        if (adelante) return hacia
        // Atras es el espejo de adelante: la hoja se levanta por el borde
        // izquierdo, empezando por la esquina de abajo a la izquierda.
        fun espejo(o: Offset) = Offset(ancho - o.x, o.y)
        return Pliegue(hacia.plana.map(::espejo), hacia.solapa.map(::espejo), espejo(hacia.ejeDesde), espejo(hacia.ejeHasta))
    }

    private fun pliegueAdelante(ancho: Float, alto: Float, avance: Float): Pliegue {
        val hoja = listOf(Offset(0f, 0f), Offset(ancho, 0f), Offset(ancho, alto), Offset(0f, alto))
        if (avance <= 0f) return Pliegue(hoja, emptyList(), Offset(ancho, 0f), Offset(ancho, alto))
        if (avance >= 1f) {
            // Al final el eje es el borde izquierdo y la hoja entera esta al otro
            // lado. Se da aparte y no con la cuenta de abajo porque sen(pi) en
            // Float no es cero y dejaria una plana de anchura casi nula.
            val eje = Offset(0f, 0f) to Offset(0f, alto)
            return Pliegue(emptyList(), hoja.map { reflejar(it, eje.first, eje.second) }, eje.first, eje.second)
        }

        // La esquina, de (ancho, alto) a (ancho - s, alto - h).
        val s = 2 * ancho * avance
        val h = ancho * ELEVACION * sin(PI.toFloat() * avance)
        val medio = Offset(ancho - s / 2, alto - h / 2)
        // Positivo del lado de la esquina, que es el que se levanta. La normal
        // del eje es (s, h): de donde estaba la esquina a donde esta.
        fun lado(p: Offset) = (p.x - medio.x) * s + (p.y - medio.y) * h
        // El eje nunca es horizontal (s > 0), asi que siempre corta y = 0 e
        // y = alto: esos son sus dos puntos, aunque caigan fuera de la hoja.
        fun xEn(y: Float) = medio.x + h * (medio.y - y) / s
        val desde = Offset(xEn(0f), 0f)
        val hasta = Offset(xEn(alto), alto)

        val plana = cortar(hoja) { lado(it) }
        val trozo = cortar(hoja) { -lado(it) }
        return Pliegue(plana, trozo.map { reflejar(it, desde, hasta) }, desde, hasta)
    }

    /**
     * El trozo de [poligono] donde [f] no es positiva, siendo [f] lineal: se
     * recorren los lados y se corta cada uno por donde [f] cambia de signo. Un
     * resultado de menos de tres puntos —el eje rozando una esquina— es vacio.
     */
    private fun cortar(poligono: List<Offset>, f: (Offset) -> Float): List<Offset> {
        val dentro = mutableListOf<Offset>()
        for (i in poligono.indices) {
            val p = poligono[i]
            val q = poligono[(i + 1) % poligono.size]
            val fp = f(p)
            val fq = f(q)
            if (fp <= 0f) dentro += p
            if ((fp < 0f && fq > 0f) || (fp > 0f && fq < 0f)) {
                val t = fp / (fp - fq)
                dentro += Offset(p.x + (q.x - p.x) * t, p.y + (q.y - p.y) * t)
            }
        }
        return if (dentro.size >= 3) dentro else emptyList()
    }

    /**
     * [p] reflejado sobre la recta que pasa por [desde] y [hasta]: el pie de la
     * perpendicular, y otro tanto al otro lado. Si los dos puntos son el mismo
     * no hay recta, y el punto se queda donde esta.
     */
    fun reflejar(p: Offset, desde: Offset, hasta: Offset): Offset {
        val dx = hasta.x - desde.x
        val dy = hasta.y - desde.y
        val largo2 = dx * dx + dy * dy
        if (largo2 == 0f) return p
        val t = ((p.x - desde.x) * dx + (p.y - desde.y) * dy) / largo2
        val pieX = desde.x + t * dx
        val pieY = desde.y + t * dy
        return Offset(2 * pieX - p.x, 2 * pieY - p.y)
    }
}
