package com.dani.lector.datos

/**
 * Ir a una pagina concreta: pasar de lo que se escribe a un indice de pagina.
 *
 * PARECE UNA LINEA Y TIENE CUATRO CASOS, y por eso esta aqui fuera y no dentro
 * del dialogo: sin numero, con letras, cero o negativo, y pasado del final. Los
 * cuatro tienen que dar lo mismo —no muevas nada— y esa es justo la clase de
 * cosa que se escribe bien y se rompe al mes siguiente. Con `SaltoTest` al lado,
 * romperlo se nota.
 *
 * DENTRO SE CUENTA DESDE CERO Y FUERA DESDE UNO. Toda la app trabaja con
 * indices (la pagina 1 es la 0) y al lector se le enseñan siempre en base 1.
 * La resta va aqui, en un solo sitio, en vez de repartida por la interfaz.
 */
object Salto {

    /**
     * El indice al que ir, o `null` si lo escrito no sirve.
     *
     * `null` y no un valor acotado a proposito: si escribes 900 en un comic de
     * 22, colocarte en la ultima es adivinar lo que querias decir. No haciendo
     * nada, ves que no ha pasado nada y corriges.
     */
    fun destino(texto: String, total: Int): Int? {
        val n = texto.trim().toIntOrNull() ?: return null
        return if (n in 1..total) n - 1 else null
    }

    /**
     * La pagina que toca al arrastrar la barra de progreso.
     *
     * AQUI SI SE ACOTA, al reves que arriba, y no es una incoherencia: el dedo
     * se sale de la barra constantemente —arrastras y te pasas por la derecha—
     * y ahi lo que quieres decir es "la ultima". Escribir 900 es un error;
     * arrastrar hasta el borde es una intencion.
     *
     * [ancho] a cero se da de verdad: el primer fotograma antes de que la barra
     * se haya medido. Sin la guarda es una division por cero.
     */
    fun deBarra(x: Float, ancho: Int, total: Int): Int {
        if (total <= 0) return 0
        if (ancho <= 0) return 0
        val fraccion = (x / ancho).coerceIn(0f, 1f)
        // El .toInt() trunca, asi que el ultimo pixel daria `total` y hay que
        // acotar otra vez: con 84 paginas, x = ancho da 84 y la ultima es la 83.
        return (fraccion * total).toInt().coerceIn(0, total - 1)
    }
}
