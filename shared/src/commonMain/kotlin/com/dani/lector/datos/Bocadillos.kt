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
     * del resto. Y si no lo alcanza ninguno —dos globos unidos, cada uno con
     * su bloque—, la ventana se ensancha a la de sus bloques vecinos: ver
     * `globoDe`.
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
     * Cuantos puntos puede tener un contorno, salvo que aflojar para llegar
     * aqui dejara una letra fuera: EL TEXTO MANDA SOBRE EL TOPE.
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
        // Sin texto no se buscan ni las viñetas: una pagina sin lineas no lee
        // ni un pixel.
        if (enLaPagina(lineas, ancho, alto).isEmpty()) return emptyList()
        return globosEn(lineas, ancho, alto, Vinetas.de(ancho, alto, pixel), pixel)
    }

    /**
     * [globos] con las viñetas ya dadas. Aparte para que las pruebas puedan
     * poner las viñetas a mano y probar el reparto sin depender de como las
     * encuentra [Vinetas].
     */
    internal fun globosEn(
        lineas: List<Recuadro>,
        ancho: Int, alto: Int,
        vinetas: List<Recuadro>,
        pixel: (x: Int, y: Int) -> Int
    ): List<Globo> {
        val dentro = enLaPagina(lineas, ancho, alto)
        if (dentro.isEmpty()) return emptyList()
        val pagina = Recuadro(0, 0, ancho, alto)

        val bloques = agrupar(dentro)
        val ventanas = bloques.map { b -> envolvente(b).let { c -> c to ventanaDe(c, b.maxOf { it.alto }) } }
        val encontrados = mutableListOf<Pair<Int, Globo>>()
        for (bloque in bloques) {
            val caja = envolvente(bloque)
            val suya = vinetaQueContiene(caja, vinetas)
            // EL TEXTO QUE NO CAE EN NINGUNA VIÑETA —un globo en el margen de
            // arriba que pisa la primera— se lee con la mas cercana, y no al
            // final: al final salia despues de todo lo de la pagina. Pero su
            // relleno NO se recorta a esa viñeta, que le cortaria justo la mitad
            // que esta en el margen: se queda como antes de las viñetas.
            val orden = if (suya >= 0) suya else vinetaMasCercana(caja, vinetas)
            val marco = if (suya >= 0) vinetas[suya] else pagina
            val globo = globoDe(bloque, dentro, ventanas, marco, ancho, alto, pixel) ?: continue
            val i = encontrados.indexOfFirst { casiIguales(it.second.recuadro, globo.recuadro) }
            if (i < 0) encontrados += orden to globo
            else encontrados[i] = minOf(encontrados[i].first, orden) to fusionar(encontrados[i].second, globo)
        }
        // Primero por viñeta y, dentro de cada una, por filas.
        return encontrados.groupBy { it.first }.entries.sortedBy { it.key }
            .flatMap { e -> ordenar(e.value.map { it.second }) }
    }

    // Una caja del OCR puede asomar fuera de la imagen por un pixel; se recorta
    // para que ningun acceso se salga de la pagina.
    private fun enLaPagina(lineas: List<Recuadro>, ancho: Int, alto: Int) = lineas
        .map { Recuadro(maxOf(0, it.izq), maxOf(0, it.arriba), minOf(ancho, it.der), minOf(alto, it.abajo)) }
        .filter { it.ancho > 0 && it.alto > 0 }

    /** La ventana de un bloque sin recortar a nada: su caja, ampliada lo que dice [LINEAS_DE_MARGEN]. */
    private fun ventanaDe(caja: Recuadro, linea: Int): Recuadro {
        val m = maxOf(caja.ancho, caja.alto) / 2 + LINEAS_DE_MARGEN * linea
        return Recuadro(caja.izq - m, caja.arriba - m, caja.der + m, caja.abajo + m)
    }

    private fun seTocan(a: Recuadro, b: Recuadro) =
        a.izq < b.der && b.izq < a.der && a.arriba < b.abajo && b.arriba < a.abajo

    /** La viñeta que contiene el centro de [caja], o -1 si ninguna. */
    private fun vinetaQueContiene(caja: Recuadro, vinetas: List<Recuadro>): Int {
        val cx = (caja.izq + caja.der) / 2
        val cy = (caja.arriba + caja.abajo) / 2
        return vinetas.indexOfFirst { cx >= it.izq && cx < it.der && cy >= it.arriba && cy < it.abajo }
    }

    /** La viñeta mas cercana al centro de [caja], contando desde su borde. */
    private fun vinetaMasCercana(caja: Recuadro, vinetas: List<Recuadro>): Int {
        val cx = (caja.izq + caja.der) / 2
        val cy = (caja.arriba + caja.abajo) / 2
        var mejor = 0
        var menor = Long.MAX_VALUE
        for ((i, v) in vinetas.withIndex()) {
            val dx = maxOf(v.izq - cx, 0, cx - (v.der - 1)).toLong()
            val dy = maxOf(v.arriba - cy, 0, cy - (v.abajo - 1)).toLong()
            if (dx * dx + dy * dy < menor) {
                menor = dx * dx + dy * dy
                mejor = i
            }
        }
        return mejor
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
     * esta en uno. [todas] son todas las lineas de la pagina.
     *
     * Relleno 4-conexo desde todos los pixeles de la caja del texto que tengan
     * el color del globo. Las letras quedan como agujeros y da igual: el
     * contorno sigue solo el borde de fuera.
     *
     * CON PILA EXPLICITA Y NUNCA RECURSIVO: un globo son decenas de miles de
     * pixeles, y en recursivo eso es otras tantas llamadas anidadas.
     */
    private fun globoDe(
        bloque: List<Recuadro>, todas: List<Recuadro>, ventanas: List<Pair<Recuadro, Recuadro>>,
        marco: Recuadro, ancho: Int, alto: Int, pixel: (x: Int, y: Int) -> Int
    ): Globo? {
        val caja = envolvente(bloque)
        val fondo = claroDominante(caja, pixel) ?: return null

        val linea = bloque.maxOf { it.alto }
        // DOS GLOBOS UNIDOS POR UN PICO pueden medir mas que la ventana de
        // cualquiera de sus dos bloques, y entonces los dos se escapan, cada uno
        // por su lado. Medido en Green Lantern Corps Recharge #04, pag. 5: dos
        // globos del mismo personaje, unidos, de unos 550 px entre los dos, y
        // ninguno de los dos salia. Por eso la ventana se ensancha a la de cada
        // bloque vecino cuya caja cae dentro de ella. Una vez y no en cadena:
        // en una pagina llena de texto, encadenando, la ventana acabaria siendo
        // la viñeta entera.
        val propia = ventanaDe(caja, linea)
        var amplia = propia
        for ((otraCaja, otraVentana) in ventanas) {
            if (otraCaja != caja && seTocan(otraCaja, propia)) amplia = envolvente(listOf(amplia, otraVentana))
        }
        // La ventana se recorta a la viñeta, y la viñeta ya esta dentro de la
        // pagina.
        val v = Recuadro(
            maxOf(marco.izq, amplia.izq), maxOf(marco.arriba, amplia.arriba),
            minOf(marco.der, amplia.der), minOf(marco.abajo, amplia.abajo)
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
        // LA VIÑETA DETECTADA ACABA UNOS PIXELES DENTRO DE LA CALLE: la esquina
        // redondeada de un marco, o un globo que se sale de el, mancha las
        // primeras lineas de la calle, y [Vinetas], que no perdona ni un pixel,
        // lleva el borde hasta pasarlas. Queda un pasillo blanco de 3 o 4 px por
        // dentro del borde, y un globo que toca la calle se escapaba por el hasta
        // el fondo de la ventana (medido en Absolute Batman #01, pags. 5 y 17).
        // Por eso, en esta franja pegada a un lado de calle, el relleno solo
        // avanza HACIA la calle y nunca a lo largo: el globo sigue llegando hasta
        // el borde, y el pasillo, que corre paralelo al borde, no se recorre.
        val franja = maxOf(4, linea / 2)

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
                val enIzq = calleIzq && x < v.izq + franja
                val enDer = calleDer && x >= v.der - franja
                val enArriba = calleArriba && y < v.arriba + franja
                val enAbajo = calleAbajo && y >= v.abajo - franja
                if (enIzq || enDer || enArriba || enAbajo) {
                    if (enIzq) mirar(x - 1, y)
                    if (enDer) mirar(x + 1, y)
                    if (enArriba) mirar(x, y - 1)
                    if (enAbajo) mirar(x, y + 1)
                } else {
                    mirar(x - 1, y); mirar(x + 1, y); mirar(x, y - 1); mirar(x, y + 1)
                }
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

        // LO QUE SE RECORTA NO ES EL RELLENO TAL CUAL. Dani vio letras cortadas
        // al ampliar, y salian de dos sitios:
        //  - una letra pegada al trazo corta el relleno, que la rodea por
        //    dentro: el borde hace una muesca justo encima de la letra y el
        //    recorte se la lleva. Por eso se suman las CAJAS DEL OCR a la
        //    mancha: una letra no puede quedar fuera, esten como esten sus
        //    pixeles. Y no un cierre de una altura de linea, que tapa las
        //    muescas estrechas a ciegas, incluido el cuello de dos globos
        //    unidos, que es lo que el contorno tiene que respetar.
        //  - el trazo negro quedaba fuera del relleno, y el globo perdia su
        //    borde. Por eso la mancha se ENSANCHA lo que mide el trazo.
        //
        // Las cajas son TODAS las que tengan algo de este relleno dentro, no
        // solo las del bloque: si dos bloques dan el mismo globo, el contorno
        // que se quede tiene que guardar las letras de los dos.
        val texto = todas.mapNotNull { t ->
            val c = Recuadro(maxOf(v.izq, t.izq), maxOf(v.arriba, t.arriba), minOf(v.der, t.der), minOf(v.abajo, t.abajo))
            val suya = c.ancho > 0 && c.alto > 0 && (t in bloque || (c.arriba until c.abajo).any { y ->
                (c.izq until c.der).any { x -> estado[(y - v.arriba) * an + (x - v.izq)] == GLOBO }
            })
            if (suya) c else null
        }

        // El trazo va con la pluma de las letras, que es un quinto de su
        // altura, poco mas o menos. Para tocar mirando la sonda: si sobra, entra
        // un filo del dibujo de alrededor, que se nota menos que un globo sin
        // borde.
        val trazo = maxOf(2, linea / 5)
        var mIzq = izq
        var mArriba = arriba
        var mDer = der
        var mAbajo = abajo
        for (t in texto) {
            mIzq = minOf(mIzq, t.izq)
            mArriba = minOf(mArriba, t.arriba)
            mDer = maxOf(mDer, t.der - 1)
            mAbajo = maxOf(mAbajo, t.abajo - 1)
        }
        // Solo la zona del globo, no la ventana entera: es lo que se ensancha.
        val zona = Recuadro(
            maxOf(v.izq, mIzq - trazo), maxOf(v.arriba, mArriba - trazo),
            minOf(v.der, mDer + 1 + trazo), minOf(v.abajo, mAbajo + 1 + trazo)
        )
        val zw = zona.ancho
        val zh = zona.alto
        val base = BooleanArray(zw * zh)
        for (y in zona.arriba until zona.abajo) for (x in zona.izq until zona.der) {
            if (estado[(y - v.arriba) * an + (x - v.izq)] == GLOBO) base[(y - zona.arriba) * zw + (x - zona.izq)] = true
        }
        for (t in texto) for (y in t.arriba until t.abajo) for (x in t.izq until t.der) {
            base[(y - zona.arriba) * zw + (x - zona.izq)] = true
        }
        val mancha = ensanchar(base, zw, zh, trazo)

        // El recuadro es el de la mancha y no el del relleno: quien pinta
        // recorta primero por el recuadro, asi que lo que se quedara fuera de el
        // —el trazo, una letra— no saldria aunque el contorno lo abarcara.
        var rIzq = Int.MAX_VALUE
        var rArriba = Int.MAX_VALUE
        var rDer = -1
        var rAbajo = -1
        for (y in 0 until zh) for (x in 0 until zw) {
            if (!mancha[y * zw + x]) continue
            if (x < rIzq) rIzq = x
            if (x > rDer) rDer = x
            if (y < rArriba) rArriba = y
            if (y > rAbajo) rAbajo = y
        }
        val recuadro = Recuadro(zona.izq + rIzq, zona.arriba + rArriba, zona.izq + rDer + 1, zona.arriba + rAbajo + 1)

        // El contorno sale de la mancha, SIN LEER NI UN PIXEL MAS. Se empieza
        // por el primero lleno de la fila de arriba: por encima no hay nada y a
        // su izquierda tampoco, que es lo que el recorrido necesita.
        var inicio = rIzq
        while (!mancha[rArriba * zw + inicio]) inicio++
        val borde = bordeExterior(zona.izq + inicio, zona.arriba + rArriba, 4 * mancha.size + 8) { x, y ->
            x >= zona.izq && x < zona.der && y >= zona.arriba && y < zona.abajo &&
                mancha[(y - zona.arriba) * zw + (x - zona.izq)]
        }
        val poligono = borde?.let { simplificar(it, texto) }?.takeIf { it.size >= 3 }
            ?: esquinas(recuadro)
        return Globo(recuadro, poligono)
    }

    /**
     * [m] ensanchada [r] pixeles por cada lado, con un cuadrado: primero en
     * horizontal y luego en vertical, contando cuantos hay encendidos en la
     * ventana que se desliza. Lineal en el area, sea cual sea [r].
     */
    private fun ensanchar(m: BooleanArray, w: Int, h: Int, r: Int): BooleanArray {
        val horizontal = BooleanArray(w * h)
        for (y in 0 until h) {
            var cuenta = 0
            for (x in 0 until minOf(r, w)) if (m[y * w + x]) cuenta++
            for (x in 0 until w) {
                if (x + r < w && m[y * w + x + r]) cuenta++
                if (x - r - 1 >= 0 && m[y * w + x - r - 1]) cuenta--
                horizontal[y * w + x] = cuenta > 0
            }
        }
        val fuera = BooleanArray(w * h)
        for (x in 0 until w) {
            var cuenta = 0
            for (y in 0 until minOf(r, h)) if (horizontal[y * w + x]) cuenta++
            for (y in 0 until h) {
                if (y + r < h && horizontal[(y + r) * w + x]) cuenta++
                if (y - r - 1 >= 0 && horizontal[(y - r - 1) * w + x]) cuenta--
                fuera[y * w + x] = cuenta > 0
            }
        }
        return fuera
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
     * guardar el primer y el ultimo pixel de cada fila, ni la envolvente
     * convexa: en esos mismos globos unidos, las dos se llevan el dibujo de en
     * medio. Esto sigue cualquier forma.
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
     * borde hecho de pixeles: por debajo se guardarian los peldaños. Con eso el
     * texto no corre peligro, porque la mancha lo rodea con el trazo de sobra.
     * Si salen mas de [TOPE_PUNTOS], se afloja hasta que quepan, pero cada
     * vuelta se comprueba que no se queda fuera ni un pixel de [texto]: una
     * recta que ataja una curva puede pasar por encima de una letra, y eso es
     * justo lo que se esta arreglando. Si aflojar corta una letra, se queda la
     * vuelta anterior aunque tenga mas puntos.
     */
    private fun simplificar(borde: List<Punto>, texto: List<Recuadro>): List<Punto> {
        // Cerrado: el primero se repite al final, y la recta entre dos puntos
        // iguales mide la distancia a ese punto, que es justo lo que hace falta
        // para partir el anillo por su punto mas lejano.
        val anillo = borde + borde[0]
        var tolerancia = 1.0
        var mejor = douglasPeucker(anillo, tolerancia).dropLast(1)
        if (!textoDentro(mejor, texto)) return borde
        while (mejor.size > TOPE_PUNTOS) {
            tolerancia *= 1.5
            val otro = douglasPeucker(anillo, tolerancia).dropLast(1)
            if (!textoDentro(otro, texto)) break
            mejor = otro
        }
        return mejor
    }

    /**
     * Si todos los pixeles de las cajas de [texto] caen dentro de [poligono],
     * por su centro. Basta con el filo de cada caja: para que el poligono se
     * meta dentro de una caja tiene que cruzar su filo.
     */
    private fun textoDentro(poligono: List<Punto>, texto: List<Recuadro>): Boolean {
        if (poligono.size < 3) return false
        for (t in texto) {
            for (x in t.izq until t.der) {
                if (!contiene(poligono, x + 0.5, t.arriba + 0.5) || !contiene(poligono, x + 0.5, t.abajo - 0.5)) return false
            }
            for (y in t.arriba until t.abajo) {
                if (!contiene(poligono, t.izq + 0.5, y + 0.5) || !contiene(poligono, t.der - 0.5, y + 0.5)) return false
            }
        }
        return true
    }

    /** Punto en poligono, par-impar: cuantas veces corta los lados un rayo hacia la derecha. */
    private fun contiene(p: List<Punto>, x: Double, y: Double): Boolean {
        var dentro = false
        var j = p.size - 1
        for (i in p.indices) {
            val a = p[i]
            val b = p[j]
            if ((a.y > y) != (b.y > y) && x < (b.x - a.x) * (y - a.y) / (b.y - a.y) + a.x) dentro = !dentro
            j = i
        }
        return dentro
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
     * misma mancha y los dos suman las cajas de texto de los dos. Cuando no, es
     * que uno se ha colado por un pixel de ruido que el otro no alcanzo, y el
     * mayor es el mas completo. Unir los dos poligonos daria, como mucho, ese
     * pixel, y es otro algoritmo entero.
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
     * POR NIVELES, que es la regla de Dani para las dobles paginas y vale para
     * todo: se empieza por la izquierda, se va hasta la derecha y se baja. Un
     * globo entra en la fila si EMPIEZA a la altura de la fila: si su parte de
     * arriba no se separa de la del primero de la fila mas de media altura del
     * mas bajo de los dos.
     *
     * Hasta la tanda 32 entraba si SOLAPABA con la fila, y la fila crecia con
     * cada globo que entraba. En la doble pagina de Green Lantern Corps
     * Recharge eso se vio en el movil: un globo alto estiraba la fila hacia
     * abajo y arrastraba a otros de mas abajo, que al ordenar la fila de
     * izquierda a derecha salian antes que los de su altura (el de la altura
     * 550 detras de los de la 767 y la 1115). Comparando solo con el primero,
     * un globo alto se lee en su nivel y ya no arrastra a nadie.
     *
     * ponytail: filas dentro de la viñeta y nada mas. Si [Vinetas] no ha podido
     * cortar —calles en diagonal, una pagina a sangre— esto vuelve a ser la
     * pagina entera, con su techo de siempre: el globo de la derecha mas alto
     * se lee antes que los de en medio.
     */
    private fun ordenar(globos: List<Globo>): List<Globo> {
        val filas = mutableListOf<MutableList<Globo>>()
        for (g in globos.sortedWith(compareBy({ it.recuadro.arriba }, { it.recuadro.izq }))) {
            val r = g.recuadro
            val fila = filas.lastOrNull()
            val primero = fila?.first()?.recuadro
            if (fila != null && primero != null && (r.arriba - primero.arriba) * 2 <= minOf(r.alto, primero.alto)) {
                fila += g
            } else {
                filas += mutableListOf(g)
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
