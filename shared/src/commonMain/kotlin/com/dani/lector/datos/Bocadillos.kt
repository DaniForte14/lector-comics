package com.dani.lector.datos

/**
 * Los globos de una pagina, a partir de las lineas de texto que da el OCR.
 *
 * El OCR solo sabe donde hay letras. Lo que hace de un monton de letras un
 * globo es que esten metidas en una mancha clara y CERRADA, y eso es lo que se
 * mira aqui: se rellena desde el texto hacia fuera y, si el relleno se choca
 * con un borde oscuro por todos lados, es un globo; si se escapa, no.
 *
 * Los pasos: buscar las viñetas ([Vinetas]), juntar las lineas en bloques,
 * rellenar cada bloque hasta su borde dentro de su viñeta, sacar de ese relleno
 * el contorno, fusionar los bloques que dan el mismo globo y ordenar para leer.
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
     * lado mayor MAS estas alturas de linea por cada lado, y recortada a su
     * viñeta.
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

    /**
     * Cuantos puntos puede tener un contorno como mucho.
     *
     * Con 64, un globo de 400 px de ancho —grande en una pagina de 1600— queda
     * en tramos de unos 20 px, y lo que una elipse de radio 200 se separa de
     * esos tramos rectos es un cuarto de pixel: a la vista, curvo. Y deja sitio
     * de sobra para el pico, que se lleva unos pocos puntos. Mas no se ve, y
     * es trabajo de mas para quien recorta el globo al pintarlo.
     */
    private const val TOPE_PUNTOS = 64

    // El estado de cada pixel de la ventana. Se mira una sola vez: en Android
    // `pixel` es un `Bitmap.getPixel` y no es gratis.
    private const val SIN_MIRAR: Byte = 0
    private const val GLOBO: Byte = 1
    private const val OTRA_COSA: Byte = 2

    // Las ocho vecinas, en el sentido de las agujas del reloj EN PANTALLA —la y
    // crece hacia abajo—, empezando por el este.
    private val DX = intArrayOf(1, 1, 0, -1, -1, -1, 0, 1)
    private val DY = intArrayOf(0, 1, 1, 1, 0, -1, -1, -1)

    // De un desplazamiento (dx, dy) a su indice en DX/DY, por (dy + 1) * 3 + (dx + 1).
    // Una tabla y no una busqueda: el centro, que no es vecina, no se pide nunca.
    private val DIRECCION = intArrayOf(5, 6, 7, 4, -1, 0, 3, 2, 1)

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
    ): List<Globo> {
        // Una caja del OCR puede asomar fuera de la imagen por un pixel; se
        // recorta para que ningun acceso se salga de la pagina.
        val dentro = lineas
            .map { Recuadro(maxOf(0, it.izq), maxOf(0, it.arriba), minOf(ancho, it.der), minOf(alto, it.abajo)) }
            .filter { it.ancho > 0 && it.alto > 0 }
        if (dentro.isEmpty()) return emptyList()

        // Una vez por pagina, y solo si hay texto: sin lineas no se lee nada.
        val vinetas = Vinetas.de(ancho, alto, pixel)
        val pagina = Recuadro(0, 0, ancho, alto)

        val encontrados = mutableListOf<Pair<Int, Globo>>()
        for (bloque in agrupar(dentro)) {
            val n = vinetaDe(envolvente(bloque), vinetas)
            val globo = globoDe(bloque, vinetas.getOrElse(n) { pagina }, ancho, alto, pixel) ?: continue
            val i = encontrados.indexOfFirst { casiIguales(it.second.recuadro, globo.recuadro) }
            if (i < 0) encontrados += n to globo
            else encontrados[i] = minOf(encontrados[i].first, n) to fusionar(encontrados[i].second, globo)
        }
        // Primero por viñeta y, dentro de cada una, por filas. Lo que no cae en
        // ninguna —texto en la calle o en el margen— va al final.
        return (0..vinetas.size).flatMap { v -> ordenar(encontrados.filter { it.first == v }.map { it.second }) }
    }

    /** La viñeta que contiene el centro de [caja], o `vinetas.size` si ninguna. */
    private fun vinetaDe(caja: Recuadro, vinetas: List<Recuadro>): Int {
        val cx = (caja.izq + caja.der) / 2
        val cy = (caja.arriba + caja.abajo) / 2
        val i = vinetas.indexOfFirst { cx >= it.izq && cx < it.der && cy >= it.arriba && cy < it.abajo }
        return if (i >= 0) i else vinetas.size
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
     * El globo que contiene [bloque] dentro de la viñeta [marco], o null si no
     * esta en uno.
     *
     * Relleno 4-conexo desde todos los pixeles de la caja del texto que tengan
     * el color del globo. Las letras quedan como agujeros y da igual: el
     * recuadro envuelve lo rellenado y el contorno sigue solo su borde de fuera.
     *
     * CON PILA EXPLICITA Y NUNCA RECURSIVO: un globo son decenas de miles de
     * pixeles, y en recursivo eso es otras tantas llamadas anidadas.
     */
    private fun globoDe(
        bloque: List<Recuadro>, marco: Recuadro, ancho: Int, alto: Int, pixel: (x: Int, y: Int) -> Int
    ): Globo? {
        val caja = envolvente(bloque)
        val fondo = claroDominante(caja, pixel) ?: return null

        val linea = bloque.maxOf { it.alto }
        val margen = maxOf(caja.ancho, caja.alto) / 2 + LINEAS_DE_MARGEN * linea
        // La ventana se recorta a la viñeta, y la viñeta ya esta dentro de la
        // pagina.
        val v = Recuadro(
            maxOf(marco.izq, caja.izq - margen), maxOf(marco.arriba, caja.arriba - margen),
            minOf(marco.der, caja.der + margen), minOf(marco.abajo, caja.abajo + margen)
        )
        if (v.ancho <= 0 || v.alto <= 0) return null
        val an = v.ancho

        // Un lado de la ventana que es el de la viñeta, y no el de la pagina, da
        // a una CALLE: el globo que llega ahi es de los que rompen el marco, no
        // uno que se escapa, y se queda cortado en ese borde. Tocar el borde de
        // la pagina, o el de la ventana en mitad de la viñeta, sigue siendo
        // escaparse.
        //
        // Lo que se acepta con esto: un globo que cruza DE VERDAD de una viñeta a
        // otra sale cortado por la calle. Se ve medio globo en vez de ninguno.
        val calleIzq = v.izq == marco.izq && marco.izq > 0
        val calleArriba = v.arriba == marco.arriba && marco.arriba > 0
        val calleDer = v.der == marco.der && marco.der < ancho
        val calleAbajo = v.abajo == marco.abajo && marco.abajo < alto

        // Del tamaño de la ventana y no de la pagina: es lo que acota la memoria.
        val estado = ByteArray(an * v.alto)
        var pila = IntArray(256)
        var tope = 0

        fun mirar(x: Int, y: Int) {
            // La caja del texto puede asomar fuera de su viñeta, y desde un
            // borde de calle se sigue rellenando: todo lo de fuera no existe.
            if (x < v.izq || x >= v.der || y < v.arriba || y >= v.abajo) return
            val i = (y - v.arriba) * an + (x - v.izq)
            if (estado[i] != SIN_MIRAR) return
            if (distanciaRgb(pixel(x, y), fondo) <= TOLERANCIA) {
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
                if ((x == v.izq && !calleIzq) || (y == v.arriba && !calleArriba) ||
                    (x == v.der - 1 && !calleDer) || (y == v.abajo - 1 && !calleAbajo)
                ) return null
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

        val recuadro = Recuadro(izq, arriba, der + 1, abajo + 1)

        // El contorno sale del mismo `estado`, SIN LEER NI UN PIXEL MAS. Se
        // empieza por el primero lleno de la fila de arriba: por encima no hay
        // nada y a su izquierda tampoco, que es lo que el recorrido necesita.
        var inicio = izq
        while (estado[(arriba - v.arriba) * an + (inicio - v.izq)] != GLOBO) inicio++
        val borde = bordeExterior(inicio, arriba, 4 * estado.size + 8) { x, y ->
            x >= v.izq && x < v.der && y >= v.arriba && y < v.abajo &&
                estado[(y - v.arriba) * an + (x - v.izq)] == GLOBO
        }
        val poligono = borde?.let { simplificar(it) }?.takeIf { it.size >= 3 }
            ?: esquinas(recuadro)
        return Globo(recuadro, poligono)
    }

    /**
     * El borde exterior de la mancha, pixel a pixel y en orden: el recorrido de
     * Moore. Se va pegado a la mancha, mirando sus ocho vecinas en el sentido
     * del reloj desde la ultima vacia, y la primera llena es el paso siguiente.
     *
     * POR QUE ESTO Y NO RAYOS DESDE EL CENTRO DEL TEXTO, que es mas corto: un
     * rayo solo ve el primer borde que cruza, asi que pierde el pico del globo
     * y cualquier forma que no se vea entera desde el centro. Y la que mas se
     * da en un comic son DOS GLOBOS UNIDOS POR UN CUELLO: el relleno los junta
     * y los rayos no pueden seguir la muesca de entre los dos. Tampoco vale
     * guardar el primer y el ultimo pixel de cada fila: en esos mismos globos
     * unidos, la fila que cruza los dos se lleva el dibujo de en medio. Esto
     * sigue cualquier forma, y las letras, que son agujeros dentro, no las toca.
     *
     * Se para al volver al inicio para dar el MISMO paso que al salir, y no
     * solo al volver a pisarlo: por un cuello de un pixel se pasa dos veces por
     * el mismo sitio. Null si se pasa de [maxPasos], que no deberia ocurrir, pero
     * un bucle sin fin aqui colgaria el lector; quien llama pone el recuadro.
     */
    private fun bordeExterior(
        x0: Int, y0: Int, maxPasos: Int, lleno: (x: Int, y: Int) -> Boolean
    ): List<Punto>? {
        val borde = mutableListOf(Punto(x0, y0))
        var x = x0
        var y = y0
        // La vacia por la que se "entra": al oeste del primero no hay nada.
        var atras = 4
        var primerPaso: Punto? = null
        for (paso in 0 until maxPasos) {
            var k = 1
            while (k <= 8 && !lleno(x + DX[(atras + k) % 8], y + DY[(atras + k) % 8])) k++
            if (k > 8) return borde // un pixel suelto, sin vecinas
            val d = (atras + k) % 8
            val nx = x + DX[d]
            val ny = y + DY[d]
            if (x == x0 && y == y0) {
                val siguiente = Punto(nx, ny)
                if (primerPaso == null) primerPaso = siguiente
                else if (siguiente == primerPaso) return borde.subList(0, borde.size - 1)
            }
            // La ultima vacia antes de la llena, vista desde el pixel nuevo: por
            // ahi se "entra" en el siguiente barrido. Son vecinas en el anillo,
            // asi que la diferencia es siempre de -1 a 1.
            val previa = (atras + k - 1) % 8
            atras = DIRECCION[(y + DY[previa] - ny + 1) * 3 + (x + DX[previa] - nx + 1)]
            x = nx
            y = ny
            borde += Punto(x, y)
        }
        return null
    }

    /**
     * El borde, con los puntos justos: Douglas-Peucker. Se queda con el punto
     * que mas se aparta de la recta entre los extremos, parte por ahi y repite,
     * hasta que nada se aparta mas de la tolerancia.
     *
     * La tolerancia empieza en UN PIXEL, que es lo que mide la escalera de un
     * borde hecho de pixeles: por debajo se guardarian los peldaños. Si aun asi
     * salen mas de [TOPE_PUNTOS], se afloja hasta que quepan; lo que se pierde
     * entonces es detalle de un borde muy ondulado, no la forma.
     */
    private fun simplificar(borde: List<Punto>): List<Punto> {
        // Cerrado: el primero se repite al final, y la recta entre dos puntos
        // iguales mide la distancia a ese punto, que es justo lo que hace falta
        // para partir el anillo por su punto mas lejano.
        val anillo = borde + borde[0]
        var tolerancia = 1.0
        while (true) {
            val puntos = douglasPeucker(anillo, tolerancia).dropLast(1)
            if (puntos.size <= TOPE_PUNTOS) return puntos
            tolerancia *= 1.5
        }
    }

    // Con pila y no recursivo, por lo mismo que el relleno: un borde son miles
    // de puntos.
    private fun douglasPeucker(p: List<Punto>, tolerancia: Double): List<Punto> {
        val guardar = BooleanArray(p.size)
        guardar[0] = true
        guardar[p.size - 1] = true
        val limite = tolerancia * tolerancia
        val pila = ArrayList<Int>()
        pila += 0
        pila += p.size - 1
        while (pila.isNotEmpty()) {
            val j = pila.removeAt(pila.size - 1)
            val i = pila.removeAt(pila.size - 1)
            var mas = -1
            var lejos = limite
            for (k in i + 1 until j) {
                val d = distancia2(p[k], p[i], p[j])
                if (d > lejos) { lejos = d; mas = k }
            }
            if (mas >= 0) {
                guardar[mas] = true
                pila += i; pila += mas
                pila += mas; pila += j
            }
        }
        return p.filterIndexed { i, _ -> guardar[i] }
    }

    /** Distancia al cuadrado del punto [q] al segmento [a]-[b]. */
    private fun distancia2(q: Punto, a: Punto, b: Punto): Double {
        val dx = (b.x - a.x).toDouble()
        val dy = (b.y - a.y).toDouble()
        val largo2 = dx * dx + dy * dy
        val t = if (largo2 == 0.0) 0.0
        else (((q.x - a.x) * dx + (q.y - a.y) * dy) / largo2).coerceIn(0.0, 1.0)
        val ex = a.x + t * dx - q.x
        val ey = a.y + t * dy - q.y
        return ex * ex + ey * ey
    }

    private fun esquinas(r: Recuadro) = listOf(
        Punto(r.izq, r.arriba), Punto(r.der, r.arriba), Punto(r.der, r.abajo), Punto(r.izq, r.abajo)
    )

    /**
     * Dos bloques que dan el mismo globo: el recuadro es el que envuelve los
     * dos, y el contorno, el del relleno MAS GRANDE.
     *
     * Casi siempre son el mismo contorno, porque los dos bloques rellenan la
     * misma mancha. Cuando no, es que uno se ha colado por un pixel de ruido
     * que el otro no alcanzo, y el mayor es el mas completo. Unir los dos
     * poligonos daria, como mucho, ese pixel, y es otro algoritmo entero.
     */
    private fun fusionar(a: Globo, b: Globo): Globo {
        val areaA = a.recuadro.ancho.toLong() * a.recuadro.alto
        val areaB = b.recuadro.ancho.toLong() * b.recuadro.alto
        return Globo(
            envolvente(listOf(a.recuadro, b.recuadro)),
            if (areaA >= areaB) a.contorno else b.contorno
        )
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
     * Orden de lectura occidental DENTRO DE UNA VIÑETA: filas de arriba abajo
     * y, dentro de cada fila, de izquierda a derecha. Entre viñetas manda el
     * orden de [Vinetas].
     *
     * Un globo entra en la fila si comparte con ella al menos la mitad de su
     * altura (o de la de la fila, si es mas baja). Con que se tocaran bastaria
     * para meter en la misma fila el globo que sube unos pixeles, y la fila se
     * iria encadenando hasta el final de la viñeta.
     *
     * ponytail: filas dentro de la viñeta y nada mas. Si [Vinetas] no ha podido
     * cortar —calles en diagonal, una pagina a sangre— esto vuelve a ser la
     * pagina entera, con su techo de siempre: el globo de la derecha mas alto
     * se lee antes que los de en medio.
     */
    private fun ordenar(globos: List<Globo>): List<Globo> {
        val filas = mutableListOf<MutableList<Globo>>()
        var arriba = 0
        var abajo = 0
        for (g in globos.sortedWith(compareBy({ it.recuadro.arriba }, { it.recuadro.izq }))) {
            val r = g.recuadro
            val fila = filas.lastOrNull()
            val solape = minOf(r.abajo, abajo) - maxOf(r.arriba, arriba)
            if (fila != null && solape * 2 >= minOf(r.alto, abajo - arriba)) {
                fila += g
                abajo = maxOf(abajo, r.abajo)
            } else {
                filas += mutableListOf(g)
                arriba = r.arriba
                abajo = r.abajo
            }
        }
        return filas.flatMap { fila -> fila.sortedBy { it.recuadro.izq } }
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
}
