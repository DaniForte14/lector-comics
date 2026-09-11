package com.dani.lector.datos

import kotlin.math.abs

/**
 * Los globos de una pagina, a partir de las lineas de texto que da el OCR.
 *
 * El OCR solo sabe donde hay letras. Lo que hace de un monton de letras un
 * globo es que esten metidas en una mancha clara y CERRADA, y eso es lo que se
 * mira aqui: se rellena desde el texto hacia fuera y, si el relleno se choca
 * con un borde oscuro por todos lados, es un globo; si se escapa, no.
 *
 * Cuatro pasos: juntar las lineas en bloques, rellenar cada bloque hasta su
 * contorno, fusionar los bloques que dan el mismo globo y ordenar para leer.
 * Todo con aritmetica sobre un accesor de pixeles, igual que [Recorte], para
 * que se pruebe con paginas pintadas a mano en un `IntArray`.
 */
object Bocadillos {

    /**
     * Cuanto se puede alejar un pixel del color del globo y seguir siendo
     * globo, en suma de diferencias RGB como [Recorte].
     *
     * MAS HOLGADO QUE LOS 28 DE [Recorte], y se puede: aqui un pixel que no
     * pasa solo deja un agujero y el relleno lo rodea, asi que una tolerancia
     * estricta no rompe nada. Lo que si hace falta es aguantar el papel de un
     * escaneo viejo, que amarillea mas por un lado que por otro. Y el riesgo de
     * pasarse es pequeño: el contorno de un globo es tinta negra, a mas de 600
     * de distancia del blanco.
     *
     * Y EN ANDROID LA PAGINA LLEGA EN RGB_565: `ComicZip` decodifica asi a
     * partir de 1200 de ancho. Rojo y azul van en pasos de 8 y verde de 4, asi
     * que un mismo blanco con el ruido de un JPEG se reparte entre casillas
     * vecinas: hasta ~40 de suma entre los dos extremos. Contra el color del
     * globo, que es la MEDIA de su casilla y cae en medio, son ~20. 60 deja
     * margen para eso y para el papel.
     *
     * `BocadillosTest` tiene un globo cuantizado a 565, y conviene saber que
     * guarda y que no: falla con 8 —por debajo del escalon de 565, o comparando
     * colores exactos— pero pasa igual con 12 que con 60, por lo de los
     * agujeros de arriba (medido). El 60 no lo sujeta ninguna prueba: lo decide
     * la sonda con paginas de verdad.
     */
    private const val TOLERANCIA = 60

    /**
     * A partir de que brillo (0-255) un pixel es "claro". El globo se busca
     * entre los claros para no confundirlo con la tinta de las letras, que es
     * lo otro que hay dentro de la caja del texto.
     */
    private const val CLARO = 128

    /**
     * La ventana del relleno es la caja del bloque ampliada en la mitad de su
     * lado mayor MAS estas alturas de linea por cada lado.
     *
     * La mitad es la curva: una elipse que envuelve un rectangulo de texto le
     * saca un 20% por cada lado, mas el aire que deja el rotulista. Las lineas
     * son para el caso que no sale del tamaño del bloque: DOS bloques de texto
     * en el mismo globo, donde el globo se extiende desde uno hasta mas alla
     * del otro. Basta con que uno de los dos lo alcance: la fusion se encarga
     * del resto.
     *
     * Es un numero para tocar mirando la sonda, en las dos direcciones: si sale
     * pequeño, se descartan globos de verdad por tocar la ventana; si sale
     * grande, el blanco de una viñeta entera cerrada por su marco pasa por
     * globo.
     */
    private const val LINEAS_DE_MARGEN = 6

    /**
     * Dos rellenos son el mismo globo si sus recuadros coinciden en esta
     * proporcion (interseccion entre union). Casi, y no del todo: dos bloques
     * del mismo globo rellenan la misma mancha, pero uno puede colarse por un
     * pixel de ruido que el otro no alcanza.
     */
    private const val CASI_IGUALES = 0.8

    // El estado de cada pixel de la ventana. Se mira una sola vez: en Android
    // `pixel` es un `Bitmap.getPixel` y no es gratis.
    private const val SIN_MIRAR: Byte = 0
    private const val GLOBO: Byte = 1
    private const val OTRA_COSA: Byte = 2

    /**
     * Los globos, ya en orden de lectura.
     *
     * @param lineas lo que devuelve [DetectorTexto], en pixeles de la pagina.
     * @param pixel el color ARGB del pixel (x, y), como `Bitmap.getPixel`.
     */
    fun globos(
        lineas: List<Recuadro>,
        ancho: Int, alto: Int,
        pixel: (x: Int, y: Int) -> Int
    ): List<Recuadro> {
        // Una caja del OCR puede asomar fuera de la imagen por un pixel; se
        // recorta para que ningun acceso se salga de la pagina.
        val dentro = lineas
            .map { Recuadro(maxOf(0, it.izq), maxOf(0, it.arriba), minOf(ancho, it.der), minOf(alto, it.abajo)) }
            .filter { it.ancho > 0 && it.alto > 0 }
        if (dentro.isEmpty()) return emptyList()

        val encontrados = mutableListOf<Recuadro>()
        for (bloque in agrupar(dentro)) {
            val globo = contorno(bloque, ancho, alto, pixel) ?: continue
            val i = encontrados.indexOfFirst { casiIguales(it, globo) }
            if (i < 0) encontrados += globo
            else encontrados[i] = envolvente(listOf(encontrados[i], globo))
        }
        return ordenar(encontrados)
    }

    /**
     * Las lineas de un mismo bloque: se solapan en horizontal y el hueco entre
     * ellas es menor que una linea. Union-find, porque "junto a" encadena: la
     * primera y la tercera de un globo no se tocan, pero las dos tocan la
     * segunda.
     *
     * El hueco se mide contra la MAS BAJA de las dos lineas: un grito en letra
     * grande no puede tragarse el texto normal que tenga a una altura suya.
     */
    private fun agrupar(lineas: List<Recuadro>): List<List<Recuadro>> {
        val jefe = IntArray(lineas.size) { it }
        fun raiz(i: Int): Int {
            var r = i
            while (jefe[r] != r) r = jefe[r]
            return r
        }
        for (i in lineas.indices) for (j in i + 1 until lineas.size) {
            val a = lineas[i]
            val b = lineas[j]
            val solapeH = minOf(a.der, b.der) - maxOf(a.izq, b.izq)
            val hueco = maxOf(a.arriba, b.arriba) - minOf(a.abajo, b.abajo)
            if (solapeH > 0 && hueco < minOf(a.alto, b.alto)) jefe[raiz(i)] = raiz(j)
        }
        return lineas.indices.groupBy { raiz(it) }.values.map { g -> g.map { lineas[it] } }
    }

    /**
     * El recuadro del globo que contiene [bloque], o null si no esta en uno.
     *
     * Relleno 4-conexo desde todos los pixeles de la caja del texto que tengan
     * el color del globo. Las letras quedan como agujeros y da igual: lo que se
     * devuelve es el recuadro que envuelve lo rellenado.
     *
     * CON PILA EXPLICITA Y NUNCA RECURSIVO: un globo son decenas de miles de
     * pixeles, y en recursivo eso es otras tantas llamadas anidadas.
     */
    private fun contorno(
        bloque: List<Recuadro>, ancho: Int, alto: Int, pixel: (x: Int, y: Int) -> Int
    ): Recuadro? {
        val caja = envolvente(bloque)
        val fondo = claroDominante(caja, pixel) ?: return null

        val linea = bloque.maxOf { it.alto }
        val margen = maxOf(caja.ancho, caja.alto) / 2 + LINEAS_DE_MARGEN * linea
        // La ventana se recorta a la pagina, asi que tocar su borde cubre los
        // dos casos: salirse de la ventana y llegar al filo de la pagina.
        val v = Recuadro(
            maxOf(0, caja.izq - margen), maxOf(0, caja.arriba - margen),
            minOf(ancho, caja.der + margen), minOf(alto, caja.abajo + margen)
        )
        val an = v.ancho

        // Del tamaño de la ventana y no de la pagina: es lo que acota la memoria.
        val estado = ByteArray(an * v.alto)
        var pila = IntArray(256)
        var tope = 0

        fun mirar(x: Int, y: Int) {
            val i = (y - v.arriba) * an + (x - v.izq)
            if (estado[i] != SIN_MIRAR) return
            if (distancia(pixel(x, y), fondo) <= TOLERANCIA) {
                estado[i] = GLOBO
                if (tope == pila.size) pila = pila.copyOf(tope * 2)
                pila[tope++] = i
            } else {
                estado[i] = OTRA_COSA
            }
        }

        var izq = Int.MAX_VALUE
        var arriba = Int.MAX_VALUE
        var der = -1
        var abajo = -1
        for (sy in caja.arriba until caja.abajo) for (sx in caja.izq until caja.der) {
            mirar(sx, sy)
            while (tope > 0) {
                val i = pila[--tope]
                val x = v.izq + i % an
                val y = v.arriba + i / an
                // Se ha escapado: onomatopeya, texto sobre el dibujo, un cielo
                // blanco. Se corta en el acto; seguir rellenando seria gastar
                // tiempo en algo que ya se sabe que no es un globo.
                if (x == v.izq || y == v.arriba || x == v.der - 1 || y == v.abajo - 1) return null
                if (x < izq) izq = x
                if (x > der) der = x
                if (y < arriba) arriba = y
                if (y > abajo) abajo = y
                mirar(x - 1, y); mirar(x + 1, y); mirar(x, y - 1); mirar(x, y + 1)
            }
        }
        if (der < 0) return null

        // UN GLOBO ENVUELVE SU TEXTO. Si lo rellenado no llega a cubrir la caja
        // de las letras es que solo se han encontrado islas claras sueltas
        // —texto sobre un dibujo con zonas claras cerradas— y eso no se amplia.
        // Con un cuarto de linea de holgura, porque la caja del OCR puede rozar
        // el contorno en un globo apretado.
        val holgura = linea / 4
        if (izq > caja.izq + holgura || arriba > caja.arriba + holgura ||
            der < caja.der - 1 - holgura || abajo < caja.abajo - 1 - holgura
        ) return null

        return Recuadro(izq, arriba, der + 1, abajo + 1)
    }

    /**
     * El color del globo: el claro que mas se repite DENTRO de la caja del
     * texto. Null si ahi no hay nada claro.
     *
     * No se supone blanco: las cartelas amarillas y el papel viejo cuentan. Y
     * se mira dentro y no alrededor porque, entre letra y letra, lo unico que
     * hay es el globo; alrededor, en un globo justo, ya puede estar el dibujo.
     *
     * Por casillas de 16 niveles por canal, para que el ruido del escaneo no
     * reparta el mismo color entre cien valores distintos, y se devuelve la
     * media de la casilla que gana. Muestreando, con unas 64 x 64 muestras como
     * mucho: para saber que color manda no hace falta mirarlos todos.
     */
    private fun claroDominante(caja: Recuadro, pixel: (x: Int, y: Int) -> Int): Int? {
        val cuenta = IntArray(4096)
        val sumaR = IntArray(4096)
        val sumaG = IntArray(4096)
        val sumaB = IntArray(4096)
        val pasoX = maxOf(1, caja.ancho / 64)
        val pasoY = maxOf(1, caja.alto / 64)
        var y = caja.arriba
        while (y < caja.abajo) {
            var x = caja.izq
            while (x < caja.der) {
                val p = pixel(x, y)
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                if (r * 299 + g * 587 + b * 114 >= CLARO * 1000) {
                    val k = ((r shr 4) shl 8) or ((g shr 4) shl 4) or (b shr 4)
                    cuenta[k]++
                    sumaR[k] += r
                    sumaG[k] += g
                    sumaB[k] += b
                }
                x += pasoX
            }
            y += pasoY
        }
        var mejor = -1
        for (k in cuenta.indices) {
            if (cuenta[k] > 0 && (mejor < 0 || cuenta[k] > cuenta[mejor])) mejor = k
        }
        if (mejor < 0) return null
        val n = cuenta[mejor]
        return (0xFF shl 24) or ((sumaR[mejor] / n) shl 16) or ((sumaG[mejor] / n) shl 8) or (sumaB[mejor] / n)
    }

    /**
     * Orden de lectura occidental: filas de arriba abajo y, dentro de cada
     * fila, de izquierda a derecha.
     *
     * Un globo entra en la fila si comparte con ella al menos la mitad de su
     * altura (o de la de la fila, si es mas baja). Con que se tocaran bastaria
     * para meter en la misma fila el globo de la viñeta de abajo que sube unos
     * pixeles, y la fila se iria encadenando hasta el final de la pagina.
     *
     * ponytail: filas sobre la pagina entera, sin saber de viñetas. Falla en
     * maquetaciones raras —viñetas en diagonal, una viñeta alta a la izquierda
     * con dos apiladas a la derecha y globos a distinta altura, globos que
     * cruzan de una viñeta a otra—. Si la sonda lo enseña, el arreglo es
     * detectar primero los marcos de viñeta y ordenar dentro de cada una.
     */
    private fun ordenar(globos: List<Recuadro>): List<Recuadro> {
        val filas = mutableListOf<MutableList<Recuadro>>()
        var arriba = 0
        var abajo = 0
        for (g in globos.sortedWith(compareBy({ it.arriba }, { it.izq }))) {
            val fila = filas.lastOrNull()
            val solape = minOf(g.abajo, abajo) - maxOf(g.arriba, arriba)
            if (fila != null && solape * 2 >= minOf(g.alto, abajo - arriba)) {
                fila += g
                abajo = maxOf(abajo, g.abajo)
            } else {
                filas += mutableListOf(g)
                arriba = g.arriba
                abajo = g.abajo
            }
        }
        return filas.flatMap { fila -> fila.sortedBy { it.izq } }
    }

    private fun casiIguales(a: Recuadro, b: Recuadro): Boolean {
        val iz = maxOf(a.izq, b.izq)
        val ar = maxOf(a.arriba, b.arriba)
        val de = minOf(a.der, b.der)
        val ab = minOf(a.abajo, b.abajo)
        if (de <= iz || ab <= ar) return false
        val interseccion = (de - iz).toLong() * (ab - ar)
        val union = a.ancho.toLong() * a.alto + b.ancho.toLong() * b.alto - interseccion
        return interseccion >= union * CASI_IGUALES
    }

    private fun envolvente(rs: List<Recuadro>) = Recuadro(
        rs.minOf { it.izq }, rs.minOf { it.arriba }, rs.maxOf { it.der }, rs.maxOf { it.abajo }
    )

    private fun distancia(a: Int, b: Int): Int =
        abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)) +
            abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)) +
            abs((a and 0xFF) - (b and 0xFF))
}
