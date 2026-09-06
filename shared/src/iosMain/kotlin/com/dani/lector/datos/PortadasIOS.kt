package com.dani.lector.datos

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.concurrent.Volatile

/**
 * iOS — Las [Portadas] del iPad. **SOLO CACHE DE MEMORIA.**
 *
 * NO HAY CACHE DE DISCO, Y ES UN HUECO A PROPOSITO, NO UN OLVIDO. [Disco] solo
 * sabe de `String` —leer, escribir y borrar texto— y una portada son bytes de
 * un JPEG. Pasarlos por base64 para que quepan seria inflarlos un tercio y
 * meter imagenes en el sitio donde viven el progreso y los marcadores, que es
 * texto pequeño que se relee entero. La cache de disco de verdad es su propia
 * tanda, con `NSFileManager` y ficheros binarios en la carpeta Caches. **Hasta
 * entonces, cerrar la app tira las portadas y hay que volver a sacarlas del
 * comic**: por eso [tamano] devuelve 0 —no hay nada guardado que enseñar en
 * Ajustes— y [limpiar] solo vacia la memoria.
 *
 * EL TECHO ES UN NUMERO FIJO DE PORTADAS Y NO UNA FRACCION DE LA MEMORIA. En
 * Android sale de `Runtime.maxMemory()`, que **no existe fuera de la JVM**, y
 * en iOS no hay a quien preguntarle cuanto queda: pasarse de la cuota no da un
 * `OutOfMemoryError` que atrapar, **el sistema mata la app sin avisar**. Asi
 * que se cuenta en portadas, que es lo unico que se sabe seguro.
 *
 * ESCRITO Y SIN COMPILAR: desde Windows no hay Kotlin/Native. Lo ve por primera
 * vez el runner de macOS del CI.
 */
class PortadasIOS(
    // SU PROPIO [ArchivoIOS], no el que usa el lector. `ArchivoIOS` recuerda el
    // indice del ULTIMO archivo abierto, y la rejilla pide portadas de comics
    // distintos uno detras de otro: compartirlo tiraria el indice de la pagina
    // que se esta leyendo en cada scroll de la biblioteca.
    private val archivo: Archivo = ArchivoIOS()
) : Portadas {

    private companion object {

        /** El ancho de `Miniaturas` en Android. Misma rejilla, mismo ancho. */
        const val ANCHO = 220

        /**
         * Cuantas portadas caben en memoria. **Subir o bajar aqui.**
         *
         * LA CUENTA: una portada de 220 px de ancho sale de unos 220x330, y en
         * iOS una imagen va en RGBA8888 —cuatro bytes por pixel, el doble que
         * el RGB_565 de Android— asi que son unos 290 KB cada una. Sesenta son
         * **17 MB**, que es menos de la mitad del techo de 48 MB que se permite
         * Android.
         *
         * POR QUE TAN CORTO, teniendo el iPad 4 GB: porque el castigo no es el
         * mismo. En Android pasarse llena el recolector de basura y se nota un
         * tiron; aqui **se cierra la app**, sin excepcion, sin rastro y sin
         * poder reproducirlo. Y sesenta ya cubren de sobra lo que se ve: en la
         * rejilla caben una docena y los carruseles gastan otras tantas, asi
         * que al volver hacia atras siguen puestas, que es para lo que sirve.
         */
        const val TECHO_PORTADAS = 60
    }

    /**
     * LA MEMORIA SE GUARDA COMO MAPAS INMUTABLES QUE SE REEMPLAZAN ENTEROS, y
     * no como un `HashMap` que se va tocando.
     *
     * Es por [enMemoria], que **no suspende**: se la llama desde la composicion,
     * o sea desde el hilo de la interfaz, mientras [obtener] esta metiendo
     * portadas desde otro. Un `HashMap` leido a la vez que se le inserta puede
     * pillarlo rehaciendo su tabla, y eso en Kotlin/Native no da una excepcion
     * ordenada. Cambiando la referencia entera, quien lee ve el mapa de antes o
     * el de despues, nunca uno a medias. Copiar sesenta entradas por portada
     * nueva no se nota al lado de descomprimir y decodificar un JPEG.
     *
     * ESCRIBE UNO SOLO CADA VEZ: todo lo que toca estas tres variables pasa por
     * [turno].
     *
     * Y LAS TRES VAN CON `@Volatile`, QUE NO ES LO MISMO QUE LO DE ARRIBA. Que
     * una referencia se lea entera evita el desgarro, pero **no promete que el
     * valor nuevo se vea**: el hilo de la interfaz puede quedarse leyendo la
     * referencia vieja de su cache indefinidamente. `@Volatile` es lo que obliga
     * a que el escrito en `Default` llegue al que lee en la interfaz. Sin esto
     * no se cierra nada —el peor caso es una carta gris de mas y volver a sacar
     * la portada— pero cuesta una anotacion.
     *
     * [orden] no lo necesita —solo se lee con [turno] cogido— y la lleva igual,
     * para que nadie tenga que averiguar cual de las tres era la que no.
     */
    @Volatile
    private var memoria: Map<String, ImageBitmap> = emptyMap()

    /** Orden de entrada, para saber a quien echar. Lo mas viejo, primero. */
    @Volatile
    private var orden: List<String> = emptyList()

    /**
     * Los que ya sabemos que no se pueden abrir — un CBR, que en el iPad no
     * tiene motor, o un ZIP roto. Sin esto se reintenta abrir un fichero de
     * decenas de megas en cada recomposicion de la lista.
     */
    @Volatile
    private var fallidos: Set<String> = emptySet()

    /**
     * UNA PORTADA CADA VEZ. Android se permite tres a la vez con un `Semaphore`;
     * aqui no, y por lo mismo del techo: tres JPEG grandes descomprimiendose a
     * la vez son tres picos de memoria simultaneos que nadie esta contando. De
     * paso deja un solo escritor sobre los tres mapas de arriba y hace que el
     * indice de [archivo] no se pise a si mismo.
     */
    private val turno = Mutex()

    /**
     * `Default` y no `IO`: con kotlinx-coroutines 1.9.0 **`Dispatchers.IO` es
     * `internal` en Kotlin/Native** y no compila. Lo mismo que ya se cazo en el
     * CI con `BibliotecaIOS`.
     */
    override suspend fun obtener(uri: String): ImageBitmap? = withContext(Dispatchers.Default) {
        // Antes del cerrojo: si ya esta, no se hace cola para nada.
        memoria[uri]?.let { return@withContext it }
        if (uri in fallidos) return@withContext null

        turno.withLock {
            // Otra vez dentro: mientras se esperaba el turno puede que la haya
            // sacado el de delante, que es justo lo que pasa cuando la rejilla
            // pide la misma portada dos veces al entrar.
            memoria[uri]?.let { return@withLock it }
            if (uri in fallidos) return@withLock null

            val nombres = (archivo.paginas(uri) as? Paginas.Ok)?.nombres
            // La portada es la primera pagina, ya ordenada por `Imagenes`.
            val img = nombres?.firstOrNull()?.let { archivo.pagina(uri, it, ANCHO) }

            if (img == null) {
                fallidos = fallidos + uri
                return@withLock null
            }
            guardar(uri, img)
            img
        }
    }

    /** Solo se llama con [turno] cogido. */
    private fun guardar(uri: String, img: ImageBitmap) {
        var nuevaMemoria = memoria + (uri to img)
        var nuevoOrden = orden + uri
        while (nuevoOrden.size > TECHO_PORTADAS) {
            nuevaMemoria = nuevaMemoria - nuevoOrden.first()
            nuevoOrden = nuevoOrden.drop(1)
        }
        // Primero el mapa y luego el orden: quien lee solo mira el mapa, asi que
        // el orden puede ir un instante por detras sin que se vea nada.
        memoria = nuevaMemoria
        orden = nuevoOrden
    }

    override fun enMemoria(uri: String): ImageBitmap? = memoria[uri]

    /**
     * SIEMPRE 0, mientras no haya cache de disco. Este numero es el de Ajustes,
     * "Portadas · N MB", y mide lo que ocupan en el almacenamiento: hoy, nada.
     * Lo que hay en memoria se va al cerrar la app y no es lo que se pregunta.
     */
    override fun tamano(): Long = 0

    override fun limpiar() {
        memoria = emptyMap()
        orden = emptyList()
        // Los fallidos tambien: vaciar la cache es la forma que tiene Dani de
        // decir "vuelve a intentarlo" despues de arreglar un fichero.
        fallidos = emptySet()
    }
}
