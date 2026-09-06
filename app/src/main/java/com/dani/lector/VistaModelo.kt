package com.dani.lector

import com.dani.lector.red.jsonLaxo
import com.dani.lector.red.optJSONArray
import com.dani.lector.red.optJSONObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.datetime.minus
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dani.lector.datos.*
import com.dani.lector.red.FuenteComics
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

data class Estado(
    val cargando: Boolean = false,
    val progreso: String = "",
    /**
     * El aviso de "hecho, ¿deshago?" que flota abajo, o vacio si no hay.
     *
     * Va en el estado y no en la pantalla porque tiene que sobrevivir a cambiar
     * de pestaña: marcas una carpeta, te vas a Lecturas y vuelves, y el aviso
     * sigue ahi los segundos que le queden.
     */
    val deshacer: String = "",
    val hayCarpeta: Boolean = false,
    /**
     * Cambia cuando se guarda CUALQUIER cosa: una pagina leida, un marcador,
     * un ajuste, una lista. Sirve para repintar lo que sale de las marcas.
     */
    val sello: Int = 0,
    /**
     * Cambia SOLO cuando cambian los FICHEROS: otra carpeta raiz, una tanda de
     * conversion, una limpieza de nombres.
     *
     * Existe separado del sello porque leer la carpeta cuesta una consulta a
     * SAF y el sello sube constantemente. Atados al mismo numero, marcar una
     * pagina y salir del comic significaba vaciar la pantalla, poner la rueda
     * y volver a preguntarle al disco por una carpeta que no habia cambiado.
     *
     * La regla para decidir donde va cada cosa: ¿cambia lo que HAY en el disco,
     * o solo lo que sabemos SOBRE ello? Lo primero sube el catalogo; lo segundo,
     * solo el sello. En caso de duda, catalogo: recargar de mas es lento, pero
     * recargar de menos es enseñar algo que ya no existe.
     */
    val catalogo: Int = 0
)

class VistaModelo(app: Application) : AndroidViewModel(app) {

    private val ctx get() = getApplication<Application>()
    // UNA SOLA LINEA DECIDE DONDE VAN LOS AJUSTES, igual que con el disco de
    // abajo. En Android son las SharedPreferences de siempre —el mismo fichero
    // y las mismas claves, aqui no se migra nada—, y en iOS sera NSUserDefaults.
    private val ajustes: Preferencias = PreferenciasAndroid(ctx)

    // Y otra que decide quien abre los comics. En iOS sera un ArchivoIOS con su
    // propio motor de descompresion; el camino —listar paginas, decodificar
    // una, precargar las de al lado— es el mismo y vive detras de la interfaz.
    private val archivo: Archivo = ArchivoAndroid(ctx)

    // Y la tercera: quien recorre tu biblioteca. En Android es SAF; en iOS sera
    // UIDocumentPicker con marcadores con permiso, que no se parece en nada.
    private val biblioteca: Biblioteca = BibliotecaAndroid(ctx)

    // Y la cuarta: la cache de portadas.
    private val portadas: Portadas = PortadasAndroid(ctx)

    // UN SOLO DISCO PARA LOS CUATRO ALMACENES. Es la unica linea de la app que
    // decide donde se guardan las cosas; en iOS sera un DiscoIOS y no cambia
    // nada mas. Misma jugada que LectorApp con la fuente de datos.
    private val disco = DiscoAndroid(ctx)

    val marcas = Progreso(disco)
    val marcadores = Marcadores(disco)
    val seriesRemotas = SeriesRemotas(disco)

    /** El diario de lectura: que leiste, que dia y cuanto. */
    val sesiones = Sesiones(disco)

    /** UNICO sitio del que salen los datos de fuera. Lo decide LectorApp. */
    private val fuente: FuenteComics get() = (ctx as LectorApp).fuente

    private val _estado = MutableStateFlow(Estado())
    val estado = _estado.asStateFlow()

    var raiz: String?
        get() = ajustes.texto("raiz")
        private set(v) { ajustes.ponTexto("raiz", v) }

    init {
        _estado.update { it.copy(hayCarpeta = raiz != null) }
        precalentar()
    }

    /**
     * Leer los tres JSON ANTES de que los pida una pantalla.
     *
     * POR QUE. `Progreso`, `Sesiones` y `SeriesRemotas` cargan su fichero la
     * primera vez que alguien les pregunta, y esa primera vez pasaba **dentro de
     * un `remember` de la pestaña de Lecturas**, o sea en el hilo principal y en
     * mitad de la composicion: al deslizar por primera vez de la biblioteca a
     * Lecturas se leian y parseaban tres ficheros de golpe sin soltar la UI. Es
     * el tiron que se nota al abrir la app y cambiar de pestaña.
     *
     * Aqui no se calcula nada ni se toca el estado: solo se deja la cache
     * caliente para que la pantalla se la encuentre hecha. Va en `viewModelScope`
     * y no en la corrutina de una pantalla, por la misma razon que el indice:
     * salirse de la pantalla no debe cancelarlo.
     *
     * Y APUNTA LO QUE TARDA, que es la unica forma de saber si esto sobraba.
     * Ajustes > Diagnostico.
     */
    private fun precalentar() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val t0 = System.currentTimeMillis()
            marcas.todas(); sesiones.todas(); seriesRemotas.todas()
            Rastro.apunta(ctx, "  fichas precargadas en " +
                "${System.currentTimeMillis() - t0} ms")
        }
    }

    /**
     * Todos los comics del arbol, cacheado.
     *
     * Recorrerlo entero cuesta, y hacen falta en tres sitios: el buscador, el
     * "seguir leyendo" y el siguiente comic de la carpeta. Se tira la cache al
     * cambiar de carpeta raiz; si añades ficheros con la app abierta, hay que
     * volver a entrar para que aparezcan.
     */
    private var indice: List<Comic>? = null

    private val cerrojoIndice = kotlinx.coroutines.sync.Mutex()
    private var trabajoIndice: kotlinx.coroutines.Deferred<List<Comic>>? = null

    /**
     * Todos los comics de la biblioteca. Recorre el arbol UNA vez y lo cachea.
     *
     * DOS ARREGLOS DEL 03/09/2026, Y LOS DOS SALIERON DEL RASTRO. La version de
     * antes eran cuatro lineas sin candado, y con el indice vacio fallaba de dos
     * formas que se sumaban:
     *
     * 1. **Varios recorridos a la vez.** Al volver a la biblioteca se piden el
     *    "seguir leyendo", el "en curso" y la fila del recorrido, y cada uno
     *    llamaba aqui por su cuenta: cuatro o cinco barridos completos del arbol
     *    simultaneos, peleandose por SAF, que los sirve de uno en uno. En el
     *    rastro se ve como "carpeta: raíz" sin su "leída:" detras, cinco
     *    segundos, hasta que Dani se rendia y salia.
     * 2. **El trabajo se tiraba al cambiar de pantalla.** El barrido corria en
     *    la corrutina de la pantalla que lo pedia; al salir de Lecturas antes de
     *    que acabara, se cancelaba y el indice se quedaba a null. Por eso el
     *    fallo aparecia SIEMPRE volviendo de Lecturas.
     *
     * Ahora el barrido es un unico [trabajoIndice] en `viewModelScope`: quien
     * llegue segundo espera al mismo trabajo en vez de lanzar otro, y salirse de
     * la pantalla ya no lo mata, porque no es suyo.
     */
    suspend fun todosLosComics(): List<Comic> {
        indice?.let { return it }
        val trabajo = cerrojoIndice.withLock {
            indice?.let { return it }
            trabajoIndice ?: viewModelScope.async(kotlinx.coroutines.Dispatchers.IO) {
                // Con hora de EMPIEZA y no solo de acabado: hace falta para ver
                // si una lectura lenta de carpeta cae DENTRO de un recorrido del
                // arbol, que es la hipotesis de los ~720 ms.
                Rastro.apunta(ctx, "  índice: empieza")
                val t0 = System.currentTimeMillis()
                comicsBajo(null).also {
                    Rastro.apunta(ctx, "  índice: ${it.size} cómics en " +
                        "${System.currentTimeMillis() - t0} ms")
                }
            }.also { trabajoIndice = it }
        }
        val l = trabajo.await()
        cerrojoIndice.withLock { indice = l; trabajoIndice = null }
        return l
    }

    /**
     * Tirar el indice: los ficheros han cambiado y lo que hay ya no los
     * describe. Cancela tambien el barrido en curso, que estaria construyendo
     * una foto de antes del cambio.
     */
    private fun tirarIndice() {
        indice = null
        trabajoIndice?.cancel()
        trabajoIndice = null
    }

    fun elegirCarpeta(uri: String) {
        tirarIndice()
        cbrRevisados = false        // otra carpeta, otros ficheros por mirar
        raiz = uri
        _estado.update {
            it.copy(hayCarpeta = true, sello = it.sello + 1, catalogo = it.catalogo + 1)
        }
    }

    // ─────────────────────── NAVEGACION POR CARPETAS ───────────────────────

    suspend fun abrirCarpeta(docId: String?, ruta: String = ""): Contenido {
        val r = raiz ?: return Contenido(emptyList(), emptyList())
        return biblioteca.abrir(r, docId, ruta)
    }

    private suspend fun comicsBajo(docId: String?, ruta: String = ""): List<Comic> {
        val r = raiz ?: return emptyList()
        return biblioteca.todosBajo(r, docId, ruta)
    }

    // ─────────────────────────── LECTURA ───────────────────────────

    var leyendo: Comic? = null
        private set

    fun abrir(c: Comic) {
        Rastro.apunta(ctx, "abrir cómic: ${c.nombre} (en ${c.carpeta})")
        leyendo = c; arranque = null; techoMarcador = -1
    }

    /**
     * Por que pagina ibas cuando entraste por un marcapaginas.
     *
     * -1 cuando has abierto el comic normalmente. Ver [Progreso.cuenta]: es lo
     * que impide que mirar una pagina marcada de un comic ya leido lo devuelva
     * a "En curso" y le quite la marca de leido.
     */
    private var techoMarcador: Int = -1

    /**
     * Por que pagina tiene que abrirse el visor, cuando no es "por donde ibas".
     *
     * Se consume al leerlo a proposito: si se quedara puesto, al girar el movil
     * o al volver de otra pantalla te devolveria al marcapaginas en vez de a
     * donde estabas leyendo.
     */
    private var arranque: Int? = null

    fun abrirEn(c: Comic, pagina: Int) {
        Rastro.apunta(ctx, "abrir cómic por marcapáginas: ${c.nombre}, página $pagina")
        leyendo = c
        arranque = pagina
        // Se lee ANTES de abrir, porque en cuanto el visor pinte la primera
        // pagina va a llamar a marcarPagina y ya seria tarde.
        techoMarcador = marcas.de(c.uri)?.pagina ?: -1
    }

    fun consumirArranque(): Int? {
        val p = arranque
        arranque = null
        return p
    }

    /** Pone o quita un marcapaginas. Devuelve true si ha quedado puesto. */
    fun alternarMarcador(uri: String, pagina: Int): Boolean {
        val puesto = marcadores.alternar(uri, pagina)
        _estado.update { it.copy(sello = it.sello + 1) }
        return puesto
    }

    /**
     * Recortar el marco liso de cada pagina. Encendido por defecto: en un movil
     * se gana bastante pantalla y el recorte se descarta solo si sale raro.
     */
    var recortar: Boolean
        get() = ajustes.si("recortar", true)
        set(v) {
            ajustes.ponSi("recortar", v)
            _estado.update { it.copy(sello = it.sello + 1) }
        }

    /**
     * Llenar la pantalla de arriba abajo en vez de encajar la pagina entera.
     *
     * Apagado por defecto A PROPOSITO. Encajar a lo ancho deja bandas negras en
     * un movil alargado, pero enseña la pagina COMPLETA, que en un comic es lo
     * que quieres: llenando la pantalla se recortan los laterales y con ellos
     * se van trozos de viñeta.
     */
    var llenar: Boolean
        get() = ajustes.si("llenar", false)
        set(v) {
            ajustes.ponSi("llenar", v)
            _estado.update { it.copy(sello = it.sello + 1) }
        }

    /**
     * Convertir a CBZ los CBR nuevos en cuanto la app los ve. Puesto de serie.
     *
     * Lo pidio Dani asi de claro: "en cuanto un CBR entre en la carpeta que se
     * convierta en CBZ, se borre el CBR y ese CBZ sustituya al CBR donde
     * estaba". Es un interruptor y no una decision fija porque la conversion
     * TARDA y calienta: si algun dia estorba, se apaga y queda el boton de
     * Ajustes de siempre.
     */
    var autoConvertir: Boolean
        get() = ajustes.si("autoConvertir", true)
        set(v) {
            ajustes.ponSi("autoConvertir", v)
            _estado.update { it.copy(sello = it.sello + 1) }
        }

    /** Dos paginas a la vez al girar el movil. Solo aplica en horizontal. */
    var dobles: Boolean
        get() = ajustes.si("dobles", true)
        set(v) {
            ajustes.ponSi("dobles", v)
            _estado.update { it.copy(sello = it.sello + 1) }
        }

    fun paginas(uri: String) = archivo.paginas(uri)
    fun pagina(uri: String, nombre: String, ancho: Int) =
        archivo.pagina(uri, nombre, ancho, recortar)
    suspend fun portada(uri: String) = portadas.obtener(uri)

    /** La portada solo si ya esta en memoria. Para pintar sin esperar al scroll. */
    fun portadaYa(uri: String) = portadas.enMemoria(uri)

    /**
     * El color con el que se tiñe la interfaz, sacado de la portada.
     *
     * Va aqui y no se llama a [ColorPortada] desde la pantalla porque lo que
     * necesita es [portadas], que es privado a proposito: la pantalla no tiene
     * por que saber de donde salen las miniaturas.
     */
    suspend fun colorDe(uri: String) = ColorPortada.de(portadas, uri)

    /**
     * Lo que ocupan los comics convertidos que se guardan en la cache.
     *
     * Se enseña en Ajustes porque son TOMOS ENTEROS y se acumulan sin que se
     * note: en el movil de Dani llegaron a 3,78 GB antes de que nadie mirara.
     * Un numero que no se ve no lo vigila nadie.
     */
    fun cacheConvertidos(): Long = Rar5.tamano(ctx)

    fun vaciarConvertidos() {
        Rar5.limpiar(ctx)
        _estado.update { it.copy(sello = it.sello + 1) }
    }

    /** Lo que ocupan las miniaturas del catalogo. */
    fun cachePortadas(): Long = portadas.tamano()

    fun vaciarPortadas() {
        portadas.limpiar()
        ColorPortada.olvidar()      // los colores salen de las miniaturas
        _estado.update { it.copy(sello = it.sello + 1) }
    }

    fun precargar(uri: String, nombres: List<String>, actual: Int, ancho: Int) =
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            archivo.precargar(uri, nombres, actual, ancho, recortar)
        }

    /**
     * Guarda por que pagina vas y, si la carpeta esta vinculada a una serie
     * del TODO, marca ese numero como leido al llegar al final.
     */
    fun marcarPagina(comic: Comic, pagina: Int, paginas: Int) {
        // Consultar un marcapaginas no es leer. Ver Progreso.cuenta.
        if (!Progreso.cuenta(pagina, techoMarcador)) return
        // Has pasado de donde ibas: esto ya es leer, y lo de aqui en adelante
        // cuenta aunque vuelvas atras una pagina.
        techoMarcador = -1
        marcas.marcar(comic.uri, pagina, paginas)
        // Y al diario, que es de donde sale el calendario. Va aqui y no en
        // marcas: la marca dice POR DONDE VAS —una sola por comic— y el diario
        // dice QUE DIAS LEISTE, que son cosas distintas y la primera no puede
        // contestar la segunda.
        val ahora = System.currentTimeMillis()
        sesiones.apuntar(comic.uri, Calendario.fecha(ahora).toString(), pagina, ahora)
        _estado.update { it.copy(sello = it.sello + 1) }
    }

    /**
     * El ultimo que terminaste, lo que estas leyendo, y el siguiente.
     *
     * Los tres salen del indice que ya esta en memoria, en una sola pasada: es
     * para pintar una fila de la pantalla de inicio y no puede costar tres
     * recorridos del arbol.
     *
     * Cualquiera de los tres puede ser null y la pantalla lo enseña como hueco:
     * el primer dia no hay ultimo, y el ultimo numero de una carpeta no tiene
     * siguiente. Inventarse algo para rellenar seria peor que el hueco.
     */
    suspend fun recorrido(): Triple<Comic?, Comic?, Comic?> {
        // UNA sola llamada y de ahi salen los tres. Antes esto llamaba a
        // todosLosComics(), a seguirLeyendo() y a siguienteComic(), y las tres
        // volvian a pedir la lista: tres barridos del arbol para pintar una fila.
        val todos = todosLosComics()
        val porUri = todos.associateBy { it.uri }

        val ultimo = marcas.todas().entries
            .filter { it.value.terminado }
            .sortedByDescending { it.value.cuando }
            .firstNotNullOfOrNull { porUri[it.key] }

        val actual = marcas.ultimoAbierto()?.let { porUri[it.first] }

        val siguiente = actual?.let { a ->
            val hermanos = todos.filter { it.carpeta == a.carpeta }
                .sortedWith(compareBy({ it.numero ?: Int.MAX_VALUE }, { it.nombre.lowercase() }))
            val i = hermanos.indexOfFirst { it.uri == a.uri }
            hermanos.getOrNull(i + 1).takeIf { i >= 0 }
        }
        return Triple(ultimo, actual, siguiente)
    }

    /** Lo ultimo que estabas leyendo y no terminaste, para el banner de arriba. */
    suspend fun seguirLeyendo(): Comic? {
        val (uri, _) = marcas.ultimoAbierto() ?: return null
        return todosLosComics().firstOrNull { it.uri == uri }
    }

    /**
     * El siguiente comic de la misma carpeta, para encadenar al terminar uno.
     *
     * Se ordena por numero cuando el parser lo ha sacado del nombre, y por
     * nombre cuando no: si un fichero no tiene numero se va al final en vez de
     * colarse en medio de la serie.
     */
    suspend fun siguienteComic(actual: Comic): Comic? {
        val hermanos = todosLosComics()
            .filter { it.carpeta == actual.carpeta }
            .sortedWith(compareBy({ it.numero ?: Int.MAX_VALUE }, { it.nombre.lowercase() }))
        val i = hermanos.indexOfFirst { it.uri == actual.uri }
        return hermanos.getOrNull(i + 1).takeIf { i >= 0 }
    }

    /**
     * Los comics empezados y sin terminar, del mas reciente al mas viejo.
     *
     * El banner enseña solo el ultimo que tocaste; si llevas tres a medias, los
     * otros dos estaban enterrados en sus carpetas y no habia forma de
     * retomarlos sin acordarte de donde estaban.
     */
    suspend fun enCurso(tope: Int = 12): List<Comic> {
        val porUri = todosLosComics().associateBy { it.uri }
        return marcas.todas().entries
            .filter { !it.value.terminado && it.value.pagina > 0 }
            .sortedByDescending { it.value.cuando }
            .mapNotNull { porUri[it.key] }
            .take(tope)
    }

    /**
     * Convierte a CBZ todos los CBR de la biblioteca.
     *
     * Tarda MUCHO —cada comic se descomprime y se vuelve a empaquetar— asi que
     * va con avisos de avance por pantalla. Al terminar se sube el sello para
     * que el catalogo se relea: donde habia un .cbr ahora hay un .cbz.
     */
    fun convertirCbr(aviso: (String) -> Unit) {
        trabajo?.cancel()
        trabajo = viewModelScope.launch {
            val r = raiz
            if (r == null) { aviso("Antes elige tu carpeta de cómics."); return@launch }

            _estado.update { it.copy(cargando = true, progreso = "Buscando CBR...") }
            val res = ConversorCarpeta.convertir(ctx, r, todosLosComics()) { texto ->
                _estado.update { it.copy(progreso = texto) }
            }
            // El indice se tira entero: los ficheros han cambiado de nombre y de
            // sitio, y el que hubiera en memoria ya no describe lo que hay.
            tirarIndice()
            _estado.update {
                it.copy(cargando = false, progreso = "",
                        sello = it.sello + 1, catalogo = it.catalogo + 1)
            }
            aviso(res.mensaje + if (res.fallidos.isEmpty()) ""
                  else "\n\n" + res.fallidos.joinToString("\n"))
        }
    }

    /**
     * Volver a mirar la biblioteca entera: ficheros nuevos, CBR por convertir.
     *
     * Existe aparte del refresco automatico porque cuesta lo suyo: tira el
     * indice, y eso obliga a recorrer TODO el arbol la proxima vez que alguien
     * lo pida. Al volver a la app se relee solo la carpeta que estas mirando,
     * que es una consulta; esto es el repaso a fondo, y lo pides tu.
     */
    fun repasarBiblioteca() {
        tirarIndice()
        cbrRevisados = false
        _estado.update { it.copy(sello = it.sello + 1, catalogo = it.catalogo + 1) }
    }

    // ─────────────────── QUE DICE COMIC VINE DE UNA SERIE ───────────────────

    /**
     * Cuanto tiene que hacer que salio el ultimo numero para darla por muerta.
     *
     * Cuatro meses y no uno: la fecha que da Comic Vine es la de PORTADA, que
     * en los comics americanos va dos o tres meses por delante de la fecha en
     * que el numero llega a la tienda. Con un mes, cualquier serie viva
     * pareceria terminada la mitad del tiempo.
     */
    private val DIAS_VIVA = 120L

    private fun fechaCorte(): String =
        // Sale del calendario español y no de la zona del movil, igual que todo
        // lo que decide fechas en esta app. Ver Novedades.ZONA.
        Novedades.hoy()
            .minus(kotlinx.datetime.DatePeriod(days = (DIAS_VIVA).toInt())).toString()

    /** El estado de una carpeta segun lo ya guardado. Sin red y sin esperar. */
    fun estadoRemoto(ruta: String, mios: List<Int>): Pair<Ficha, EstadoSerie.Resumen>? {
        val f = seriesRemotas.de(ruta) ?: return null
        return f to EstadoSerie.de(mios, f.numeros, fechaCorte())
    }

    /**
     * Pregunta a Comic Vine que numeros tiene esta serie y lo guarda.
     *
     * [nombre] es el de la carpeta, que es lo unico que se tiene para buscar.
     * Acertar con el volumen bueno entre los candidatos falla de vez en cuando
     * —esta documentado que falla— asi que se guarda TAMBIEN como se llama
     * alli y de que año es: si la carpeta dice "Green Lantern Vol. 4" y esto
     * responde "Green Lantern (1990)", lo vas a ver y vas a saber que ha
     * cogido la que no era. Enseñar de donde sale un dato es lo que permite
     * desconfiar de el.
     */
    fun comprobarSerie(ruta: String, nombre: String, anio: Int?, aviso: (String) -> Unit) {
        if (!fuente.disponible) { aviso("No hay ninguna fuente de datos configurada."); return }
        trabajo?.cancel()
        trabajo = viewModelScope.launch {
            _estado.update { it.copy(cargando = true, progreso = "Buscando «$nombre»...") }

            val vol = runCatching { fuente.volumen(nombre, anio) }.getOrNull()
            if (vol == null || vol.id.isBlank()) {
                _estado.update { it.copy(cargando = false, progreso = "") }
                aviso(fuente.ultimoFallo()
                    ?: "No he encontrado ninguna serie que se llame «$nombre».")
                return@launch
            }

            _estado.update { it.copy(progreso = "Trayendo los números de ${vol.nombre}...") }
            val nums = runCatching { fuente.numerosDe(vol.id) }.getOrDefault(emptyList())

            if (nums.isEmpty()) {
                _estado.update { it.copy(cargando = false, progreso = "") }
                aviso(fuente.ultimoFallo()
                    ?: "He encontrado «${vol.nombre}» pero no me ha dado sus números.")
                return@launch
            }

            seriesRemotas.guardar(Ficha(
                ruta = ruta,
                volumenId = vol.id,
                nombre = vol.nombre,
                anio = vol.anio,
                numeros = nums,
                cuando = System.currentTimeMillis()
            ))
            _estado.update {
                it.copy(cargando = false, progreso = "", sello = it.sello + 1)
            }
        }
    }

    /** Deshacer el vinculo de una carpeta, cuando ha cogido la serie que no era. */
    /**
     * Prueba de conexion para Ajustes.
     *
     * Se quedo en una sola fuente al quitar las wikis y Gemini: la unica red
     * que le queda a la app es Comic Vine.
     */
    suspend fun probarConexion(): String =
        if (!fuente.disponible) "Sin clave de Comic Vine."
        else (fuente as? com.dani.lector.red.ComicVine)?.probar() ?: "Sin diagnóstico."

    fun olvidarSerie(ruta: String) {
        seriesRemotas.olvidar(ruta)
        _estado.update { it.copy(sello = it.sello + 1) }
    }

    // ─────────────────── NUMEROS NUEVOS DE LO QUE SIGUES ───────────────────

    /** Una vez por sesion, como la revision de CBR. */
    private var novedadesRevisadas = false
    private var revisionNovedades: Job? = null

    /**
     * El repaso lento, el que corre al abrir la app.
     *
     * ESTO NO ES EL AVISO DE NUMERO NUEVO. De eso se encarga el trabajo diario
     * de [Vigilante], que corre con la app cerrada y solo mira las series que
     * sigues. Lo de aqui es el repaso de fondo: tres series por pasada, la mas
     * vieja primero, para que el "te faltan N de M" del resto de la biblioteca
     * no se quede rancio.
     *
     * Como mucho una vez cada 20 horas: sin ese freno, abrir la app cuatro
     * veces en una tarde se come la cuota de peticiones de la hora y el 420
     * deja el resto de la app sin poder consultar nada.
     *
     * Si de paso pilla algo que ya deberia estar en la tienda, lo dice en un
     * dialogo — pero sin duplicar el aviso del trabajo diario, porque las dos
     * pasadas escriben en el mismo registro de avisados.
     */
    fun revisarNovedades(aviso: (String) -> Unit) {
        if (novedadesRevisadas || !fuente.disponible) return
        novedadesRevisadas = true

        val ahora = System.currentTimeMillis()
        // 20 horas y no 24 a proposito: con 24 clavadas, quien abre la app cada
        // mañana a la misma hora se salta la comprobacion un dia si y otro no,
        // porque llega siempre unos minutos antes de cumplirse el plazo.
        if (ahora - ajustes.largo("novedades_vistas", 0) < 20 * 60 * 60 * 1000) return

        revisionNovedades?.cancel()
        revisionNovedades = viewModelScope.launch {
            ajustes.ponLargo("novedades_vistas", ahora)

            val avisos = Vigilante.pasada(
                ctx = ctx,
                fuente = fuente,
                seriesRemotas = seriesRemotas,
                fechaCorte = fechaCorte(),
                // Aqui NO se filtra por seguidas: este repaso existe para que
                // el "te faltan N" de las demas series no se quede rancio. De
                // las seguidas se encarga ademas el trabajo diario, y como las
                // dos pasadas comparten el registro de avisados, la que llegue
                // primero deja a la otra sin nada que decir. No hay aviso doble.
                soloSeguidas = false,
                tope = 3
            ) { texto -> _estado.update { it.copy(cargando = true, progreso = texto) } }

            _estado.update {
                it.copy(cargando = false, progreso = "", sello = it.sello + 1)
            }
            // Solo si ha pasado algo, igual que con los CBR: un mensaje cada vez
            // que abres la app diciendo que no hay nada nuevo es ruido.
            if (avisos.isNotEmpty()) aviso(
                avisos.joinToString("\n") { Novedades.texto(it.serie, it.nuevos) }
            )
        }
    }

    /** Las series que sigues, para poder verlas todas juntas. */
    fun seriesSeguidas(): List<Ficha> =
        seriesRemotas.todas().filter { it.seguida }.sortedBy { it.nombre.lowercase() }

    /**
     * Seguir o dejar de seguir una serie.
     *
     * Al EMPEZAR a seguirla se da por visto todo lo que ya existe. Sin esa
     * linea de salida, seguir una serie de sesenta numeros suelta sesenta
     * notificaciones esa misma noche — y una sola vez que pase eso, se apagan
     * las notificaciones de la app para siempre.
     *
     * Al dejar de seguirla NO se borra lo avisado: si vuelves a seguirla, no
     * tiene sentido que te cuente otra vez lo de los meses que estuviste fuera.
     */
    fun seguirSerie(ruta: String, seguir: Boolean) {
        val f = seriesRemotas.de(ruta) ?: return
        seriesRemotas.guardar(
            if (seguir) f.copy(seguida = true, avisados = f.avisados + Novedades.etiquetasDe(f.numeros))
            else f.copy(seguida = false)
        )
        _estado.update { it.copy(sello = it.sello + 1) }
    }

    /** Una vez por sesion, o se reengancharia a si misma sin parar. */
    private var cbrRevisados = false
    private var revision: Job? = null

    /**
     * Busca CBR y los convierte, sin que nadie pulse nada.
     *
     * COMO DE "EN CUANTO ENTRE" ES ESTO, que es lo unico que no sale como se
     * pidio: la app no puede vigilar tu carpeta mientras no la estas usando.
     * Para eso haria falta un servicio en segundo plano despertandose cada
     * poco, que es exactamente lo que se acaba de quitar de en medio porque
     * calentaba el movil. Asi que esto se dispara cuando la app ABRE la
     * biblioteca: la primera vez que ve el fichero, lo convierte. Si no estas
     * usando la app, da igual que sea un momento u otro.
     *
     * PRIMER FILTRO POR EXTENSION, y esto si es un recorte a proposito: el
     * boton de Ajustes mira la FIRMA de todos los ficheros, porque hay .cbr que
     * por dentro son ZIP. Hacer eso aqui seria una lectura de disco por cada
     * comic de la biblioteca cada vez que abres la app, y para nada el 99% de
     * las veces. Aqui se miran solo los que se LLAMAN .cbr; a los raros los
     * sigue cogiendo el boton.
     *
     * No usa [trabajo] a proposito: eso cancelaria lo que estuvieras haciendo.
     */
    fun revisarCbr(aviso: (String) -> Unit) {
        if (!autoConvertir || cbrRevisados || raiz == null) return
        cbrRevisados = true

        revision?.cancel()
        revision = viewModelScope.launch {
            val r = raiz ?: return@launch
            val sospechosos = todosLosComics().filter {
                it.nombre.substringAfterLast('.', "").lowercase() == "cbr"
            }
            if (sospechosos.isEmpty()) return@launch

            _estado.update { it.copy(cargando = true, progreso = "Preparando cómics nuevos...") }
            val res = ConversorCarpeta.convertir(ctx, r, sospechosos) { texto ->
                _estado.update { it.copy(progreso = texto) }
            }
            tirarIndice()
            _estado.update {
                it.copy(cargando = false, progreso = "",
                        sello = it.sello + 1, catalogo = it.catalogo + 1)
            }
            // Solo se avisa si ha pasado algo. Un mensaje al abrir la app cada
            // vez, diciendo que no habia nada que hacer, es ruido.
            if (res.convertidos > 0 || res.fallidos.isNotEmpty()) aviso(res.mensaje)
        }
    }

    /**
     * Limpia nombres raros y duplicados de la biblioteca.
     *
     * Igual que la conversion: tarda, avisa por pantalla, y al terminar tira el
     * indice porque los ficheros han cambiado de nombre o han desaparecido.
     */
    fun limpiarBiblioteca(aviso: (String) -> Unit) {
        trabajo?.cancel()
        trabajo = viewModelScope.launch {
            if (raiz == null) { aviso("Antes elige tu carpeta de cómics."); return@launch }

            _estado.update { it.copy(cargando = true, progreso = "Revisando...") }
            val res = ConversorCarpeta.limpiar(ctx, todosLosComics()) { texto ->
                _estado.update { it.copy(progreso = texto) }
            }
            tirarIndice()
            _estado.update {
                it.copy(cargando = false, progreso = "",
                        sello = it.sello + 1, catalogo = it.catalogo + 1)
            }
            aviso(res.mensaje + if (res.fallidos.isEmpty()) ""
                  else "\n\n" + res.fallidos.joinToString("\n"))
        }
    }


    // ─────────────────────── BUSQUEDAS RECIENTES ───────────────────────
    //
    // En las prefs y no en un JSON aparte: son ocho cadenas cortas, no hace
    // falta fichero y no entran en la copia de seguridad. Que se pierdan al
    // reinstalar no le duele a nadie, al reves que lo leido.

    /** Lo ultimo que buscaste, de lo mas reciente a lo mas viejo. */
    val recientes: List<String>
        get() = ajustes.texto("recientes").orEmpty()
            .split("\n").filter { it.isNotBlank() }

    private fun guardarRecientes(l: List<String>) {
        ajustes.ponTexto("recientes", l.joinToString("\n"))
        _estado.update { it.copy(sello = it.sello + 1) }
    }

    /**
     * Apunta una busqueda. La misma dos veces no se duplica: sube arriba.
     *
     * Se compara NORMALIZADO para que "Green Lantern" y "green lantern" sean la
     * misma entrada, pero se guarda lo que tecleaste, no la version normalizada:
     * en la lista tiene que salir tal cual lo escribiste.
     *
     * Menos de dos letras no se apunta: al teclear "batman" pasarias por "b" y
     * "ba", y el historial se llenaria de fragmentos.
     */
    fun recordarBusqueda(texto: String) {
        val t = texto.trim()
        if (t.length < 2) return
        val clave = Parser.normalizar(t)
        val nueva = listOf(t) + recientes.filter { Parser.normalizar(it) != clave }
        guardarRecientes(nueva.take(8))
    }

    fun olvidarBusqueda(texto: String) = guardarRecientes(recientes.filter { it != texto })

    /**
     * UNA carta por SERIE, y cada una enseñando por donde seguirias.
     *
     * Antes esta fila era `comicsBajo(...).take(12)`: los doce primeros comics
     * que salieran, aplastados. Debajo de "DC Comics" te salian doce numeros
     * sueltos de Green Lantern y ni rastro de las otras series, que es una fila
     * que no dice nada de lo que tienes.
     *
     * Ahora hay una carta por SERIE, y la portada que enseña es la del numero
     * por el que ibas: si tienes esa serie empezada, sale por donde la dejaste,
     * no por el #01 que ya leiste hace un mes.
     *
     * Serie = la carpeta que contiene los comics DE VERDAD, no la subcarpeta
     * directa de esta fila. Debajo de "DC Comics" lo util es ver "Absolute
     * Green Lantern", "Green Lantern Vol. 4" y "Absolute Batman" por separado;
     * agrupar por el nivel de arriba dejaba dos cartas, "Green lantern" y
     * "Batman", que es tan poco informativo como la lista aplastada de antes.
     *
     * Los comics sueltos de este nivel salen enteros, uno por carta: no son una
     * serie y agruparlos en una sola carta los escondería.
     *
     * Se lee la carpeta UNA vez y se agrupa en memoria. Preguntar por cada
     * subcarpeta serian tantos recorridos de disco como series tengas.
     *
     * Ya no recibe docId: desde que esto sale del indice cacheado y no de SAF,
     * la ruta es lo unico que hace falta. Se quita del parametro en vez de
     * dejarlo ahi sin usar, que es una invitacion a creer que hace algo.
     */
    suspend fun portadasDe(ruta: String, cuantas: Int = 24): List<Comic> {
        // Del indice que YA esta en memoria, no volviendo a recorrer el disco.
        //
        // Antes esto era comicsBajo(docId, ruta), que lanza una consulta a SAF
        // por cada subcarpeta. Con una fila por carpeta en la pantalla de
        // inicio, eso son decenas de consultas CADA VEZ que se repinta la lista
        // —y se repinta con cada cambio de sello, que sube al marcar una pagina,
        // al volver del visor...—. Era la causa gorda del tiron.
        //
        // todosLosComics() recorre el disco UNA vez y se queda cacheado, asi que
        // filtrar por la ruta sale gratis.
        val base = ruta.trim('/')
        val todos = todosLosComics().filter {
            base.isBlank() || it.carpeta.trim('/') == base ||
                it.carpeta.trim('/').startsWith("$base/")
        }
        val sueltos = mutableListOf<Comic>()
        val porSerie = LinkedHashMap<String, MutableList<Comic>>()

        todos.forEach { c ->
            // Los que estan sueltos en ESTA carpeta no son una serie: van uno
            // por carta. Los demas se agrupan por la carpeta que los contiene,
            // que es la serie de verdad por hondo que este.
            if (c.carpeta.trim('/') == base) sueltos.add(c)
            else porSerie.getOrPut(c.carpeta) { mutableListOf() }.add(c)
        }

        return (porSerie.values.map { porDondeIbas(it) } + sueltos).take(cuantas)
    }

    /**
     * De un monton de numeros de la misma serie, cual enseñar.
     *
     * Por orden: el que tienes a medias (el mas reciente, si hay varios), el
     * primero que no hayas terminado, y si esta todo leido el primero de todos.
     */
    private fun porDondeIbas(comics: List<Comic>): Comic {
        val orden = comics.sortedWith(
            compareBy({ it.numero ?: Int.MAX_VALUE }, { it.nombre.lowercase() })
        )
        orden.mapNotNull { c -> marcas.de(c.uri)?.let { c to it } }
            .filter { !it.second.terminado && it.second.pagina > 0 }
            .maxByOrNull { it.second.cuando }
            ?.let { return it.first }
        return orden.firstOrNull { marcas.de(it.uri)?.terminado != true } ?: orden.first()
    }

    /**
     * Marcar a mano sin abrirlo.
     *
     * "No leido" BORRA la marca en vez de guardar un cero, y eso es lo que
     * quita el comic de "En curso" y de las estadisticas. Guardar "vas por la
     * pagina 0" lo dejaria contando como empezado.
     */
    fun marcarLeido(comic: Comic, leido: Boolean) {
        if (!leido) {
            marcas.olvidar(comic.uri)
            _estado.update { it.copy(sello = it.sello + 1) }
            return
        }
        // CUANTAS PAGINAS TIENE, DE VERDAD. Antes esto ponia `?: 1` para el
        // comic que nunca habias abierto, y ese 1 no era un detalle: la marca es
        // "vas por la pagina N de M" y M es lo que suman las estadisticas. Diez
        // tomos marcados a mano añadian DIEZ paginas leidas. Abrir el fichero
        // para contarlas cuesta una lectura de disco y va fuera del hilo
        // principal; el `?: 1` se queda solo para el comic que no se puede leer.
        viewModelScope.launch(Dispatchers.IO) {
            val total = cuantasPaginas(comic.uri)
            marcas.marcarTerminado(comic.uri, total)
            _estado.update { it.copy(sello = it.sello + 1) }
        }
    }

    /**
     * Las paginas de un comic sin abrirlo en el visor, para marcarlo a mano.
     *
     * Si no se puede leer —un CBR roto, un fichero que ya no esta— devuelve la
     * cuenta que ya hubiera guardada, y en ultimo caso 1. Marcar como leido algo
     * ilegible sigue siendo una peticion legitima: el usuario lo ha leido en
     * otro sitio y solo quiere que deje de salir en "En curso".
     */
    private fun cuantasPaginas(uri: String): Int {
        val guardada = marcas.de(uri)?.paginas ?: 0
        val p = archivo.paginas(uri)
        return if (p is Paginas.Ok && p.nombres.isNotEmpty()) p.nombres.size
               else guardada.coerceAtLeast(1)
    }

    /**
     * Marcar (o desmarcar) de golpe todos los comics de una carpeta.
     *
     * Para lo que se lee FUERA del movil: en el iPad, en papel, en el ordenador.
     * Sin esto la unica forma de ponerlo al dia era abrir cada numero y pasarlo
     * entero, que es exactamente el trabajo que la app deberia ahorrar.
     *
     * VA CON AVISO DE PROGRESO porque no es instantaneo: marcar leidos treinta
     * comics son treinta lecturas de disco para contar sus paginas. Es el mismo
     * trato que la conversion de CBR — una app que tarda sin explicarse parece
     * rota.
     *
     * DESMARCAR NO CUESTA NADA (es borrar la marca) pero va por el mismo sitio
     * para no tener dos caminos que puedan discrepar.
     */
    fun marcarCarpeta(comics: List<Comic>, leido: Boolean) {
        if (comics.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            _estado.update { it.copy(cargando = true, progreso = "Marcando...") }
            // LA FOTO DE ANTES, para poder deshacer. Se guarda el valor exacto
            // —incluida la fecha— y tambien los que NO tenian marca, como null:
            // deshacer tiene que volver a quitar las que esto creo, no dejarlas
            // a cero, que es otra cosa distinta.
            val antes = comics.associate { it.uri to marcas.de(it.uri) }
            // En tanda: si no, cada comic reescribe progreso.json entero.
            marcas.tanda {
                comics.forEachIndexed { i, comic ->
                    if (leido) marcas.marcarTerminado(comic.uri, cuantasPaginas(comic.uri))
                    else marcas.olvidar(comic.uri)
                    _estado.update {
                        it.copy(progreso = "Marcando ${i + 1} de ${comics.size}...")
                    }
                }
            }
            ofrecerDeshacer(antes,
                if (leido) "${comics.size} marcados como leídos"
                else "quitado el leído a ${comics.size}")
            _estado.update {
                it.copy(cargando = false, progreso = "", sello = it.sello + 1)
            }
        }
    }

    // ─────────────────── DESHACER LO ULTIMO ───────────────────

    /**
     * Lo que habia antes del ultimo marcado en bloque, por si hay que volver.
     *
     * SOLO UN PASO, y a proposito: un historial de verdad significa decidir
     * cuando se tira, que pasa al cerrar la app y que pasa si mientras tanto has
     * leido. Lo que hace falta aqui es la red del "acabo de darle sin querer a
     * los treinta", y para eso un paso basta.
     */
    private var deshacerAntes: Map<String, Marca?>? = null

    /**
     * Cual es el aviso que se esta enseñando ahora.
     *
     * Sin esto, el temporizador del aviso VIEJO borraria el aviso NUEVO al
     * cumplirse: marcas una carpeta, marcas otra a los dos segundos, y el aviso
     * de la segunda desaparece a los cuatro en vez de a los ocho.
     */
    private var vezDeshacer = 0

    private suspend fun ofrecerDeshacer(antes: Map<String, Marca?>, texto: String) {
        deshacerAntes = antes
        vezDeshacer++
        val mia = vezDeshacer
        _estado.update { it.copy(deshacer = texto) }
        viewModelScope.launch {
            kotlinx.coroutines.delay(7000)
            if (vezDeshacer == mia) {
                deshacerAntes = null
                _estado.update { it.copy(deshacer = "") }
            }
        }
    }

    /** Volver a dejar las marcas como estaban antes del ultimo marcado en bloque. */
    fun deshacerMarcado() {
        val antes = deshacerAntes ?: return
        deshacerAntes = null
        vezDeshacer++
        _estado.update { it.copy(deshacer = "") }
        viewModelScope.launch(Dispatchers.IO) {
            marcas.tanda { antes.forEach { (uri, marca) -> marcas.restaurar(uri, marca) } }
            _estado.update { it.copy(sello = it.sello + 1) }
        }
    }

    /** Cerrar el aviso sin deshacer nada. */
    fun cerrarDeshacer() {
        vezDeshacer++
        deshacerAntes = null
        _estado.update { it.copy(deshacer = "") }
    }

    /**
     * Por cual seguir en esta carpeta. Ver [Siguiente] para la regla.
     *
     * Se le pasan los comics EN ORDEN DE NUMERO y no en el que el usuario haya
     * elegido para verlos: "sigue por el #7" habla de la serie, no de como esta
     * ordenada la pantalla ahora mismo.
     */
    fun siguienteSinLeer(comics: List<Comic>): Comic? =
        Siguiente.de(OrdenCarpeta.de(comics, Orden.NUMERO), marcas.todas())

    /**
     * Como se ordenan los comics dentro de una carpeta.
     *
     * GLOBAL Y NO POR CARPETA a proposito: un orden distinto en cada carpeta se
     * olvida en cuanto lo eliges, y despues no se entiende por que una serie
     * sale al reves que la de al lado. Se elige una vez y vale para todas.
     */
    var orden: Orden
        get() = Orden.entries.getOrNull(ajustes.entero("orden", 0)) ?: Orden.NUMERO
        set(v) {
            ajustes.ponEntero("orden", v.ordinal)
            _estado.update { it.copy(sello = it.sello + 1) }
        }

    /** Empezar de nuevo: olvida por que pagina ibas. */
    fun reiniciarProgreso(comic: Comic) {
        marcas.olvidar(comic.uri)
        _estado.update { it.copy(sello = it.sello + 1) }
    }

    private var trabajo: Job? = null


    // ─────────────────── COPIA DE SEGURIDAD ───────────────────

    /**
     * Guarda el progreso y los marcapaginas en un fichero.
     *
     * YA NO GUARDA LISTAS: se quitaron el 02/09/2026. Lo que queda es lo unico
     * que no se puede volver a pedir a nadie — por que pagina vas de cada comic
     * y donde tienes los marcapaginas— y por eso esta copia importa mas ahora
     * que antes, no menos.
     *
     * Se guarda POR CARPETA Y NOMBRE, no por uri: las uris de SAF llevan dentro
     * la ruta y el permiso concreto, asi que al reinstalar o al volver a elegir
     * la carpeta cambian todas.
     */
    /** El contenido de la copia. Lo comparten la copia a mano y la automatica. */
    private fun copiaJson(): JsonObject = buildJsonObject {
        // v3: entra el diario de lectura. Las versiones viejas se siguen
        // leyendo: al importar, lo que no venga simplemente no se toca.
        put("version", 3)
        put("progreso", marcas.exportar())
        put("marcadores", marcadores.exportar())
        put("sesiones", sesiones.exportar())
    }

    suspend fun exportarA(destino: android.net.Uri): String =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val raiz = copiaJson()
                escribir(destino, raiz)
                "Copia guardada: ${raiz.optJSONObject("progreso")?.size} cómics y " +
                "${raiz.optJSONArray("marcadores")?.size} marcapáginas."
            }.getOrElse { "No se ha podido guardar: ${it.javaClass.simpleName}" }
        }

    /**
     * "wt" y no "w": TRUNCA el fichero antes de escribir.
     *
     * Con "w" a secas, si la copia nueva es mas corta que la anterior —borras
     * comics, se van marcas— queda la cola de la vieja pegada al final y el
     * JSON no se puede leer. Es un fichero que solo se lee el dia que hace
     * falta, asi que un fallo asi no se descubre hasta el peor momento.
     */
    private fun escribir(destino: android.net.Uri, datos: JsonObject) {
        ctx.contentResolver.openOutputStream(destino, "wt")?.use {
            it.write(datos.toString().toByteArray())
        }
    }

    // ─────────────────── LA COPIA AUTOMATICA ───────────────────

    /**
     * La carpeta donde se deja la copia sola, si has elegido una.
     *
     * Se guarda el uri del ARBOL de SAF, con permiso persistente. Un uri de
     * fichero suelto no vale: los que da el selector de "guardar como" son de
     * un solo uso y no se pueden volver a abrir mañana.
     */
    var carpetaCopia: String?
        get() = ajustes.texto("carpeta_copia")
        private set(v) { ajustes.ponTexto("carpeta_copia", v) }

    var copiaAlSalir: Boolean
        get() = ajustes.si("copia_al_salir", true)
        set(v) { ajustes.ponSi("copia_al_salir", v)
                 _estado.update { it.copy(sello = it.sello + 1) } }

    fun elegirCarpetaCopia(uri: String) {
        carpetaCopia = uri
        // Se olvida cuando se hizo la ultima: la carpeta es otra y ahi todavia
        // no hay copia, por muy al dia que estuviera la de antes.
        ajustes.ponLargo("copia_hecha", 0)
        _estado.update { it.copy(sello = it.sello + 1) }
    }

    /** El nombre de la carpeta elegida, para enseñarlo en Ajustes. */
    fun nombreCarpetaCopia(): String? = carpetaCopia?.let {
        android.net.Uri.decode(it).substringAfterLast('/').substringAfterLast(':')
    }

    /**
     * Deja la copia al salir de la app, si hay carpeta y si algo ha cambiado.
     *
     * CUANDO SE LLAMA: al pasar la app a segundo plano (ON_STOP), que es el
     * momento en que de verdad "sales". Hacerlo al marcar cada pagina seria
     * escribir un fichero por pagina leida.
     *
     * SI NO HA CAMBIADO NADA, NO SE ESCRIBE. Se compara la marca mas reciente
     * con la de la ultima copia: sin eso, cada vez que abres y cierras la app
     * se reescribe el fichero, y si esa carpeta la sincroniza otra app, cada
     * reescritura es una subida y una version nueva en la nube para nada.
     */
    fun copiaAlSalirSiToca() {
        if (!copiaAlSalir) return
        val carpeta = carpetaCopia ?: return

        val cambio = maxOf(
            marcas.todas().values.maxOfOrNull { it.cuando } ?: 0L,
            marcadores.todos().maxOfOrNull { it.cuando } ?: 0L,
            sesiones.todas().maxOfOrNull { it.cuando } ?: 0L
        )
        if (cambio == 0L || cambio <= ajustes.largo("copia_hecha", 0)) return

        // viewModelScope y no una corrutina suelta: si el sistema mata el
        // proceso a mitad, la copia se queda sin escribir y se hara la proxima
        // vez. Lo que no puede pasar es bloquear el cierre de la app.
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val destino = ficheroDeCopia(android.net.Uri.parse(carpeta)) ?: return@launch
                escribir(destino, copiaJson())
                ajustes.ponLargo("copia_hecha", cambio)
            }
        }
    }

    /**
     * El fichero de la copia dentro de la carpeta elegida: se busca y, si no
     * esta, se crea.
     *
     * SE BUSCA POR PREFIJO y no por nombre exacto, porque **SAF le pone SU
     * extension al fichero que creas**: pidiendo "lector-copia" con mime
     * application/json, unos proveedores dejan "lector-copia" y otros
     * "lector-copia.json". Es la misma trampa que ya costo las chapas de los
     * numeros al convertir los CBR.
     */
    private fun ficheroDeCopia(arbol: android.net.Uri): android.net.Uri? {
        val cr = ctx.contentResolver
        val idRaiz = android.provider.DocumentsContract.getTreeDocumentId(arbol)
        val hijos = android.provider.DocumentsContract
            .buildChildDocumentsUriUsingTree(arbol, idRaiz)

        cr.query(hijos, arrayOf(
            android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME
        ), null, null, null)?.use { c ->
            while (c.moveToNext()) {
                if (c.getString(1).startsWith(NOMBRE_COPIA))
                    return android.provider.DocumentsContract
                        .buildDocumentUriUsingTree(arbol, c.getString(0))
            }
        }
        val padre = android.provider.DocumentsContract
            .buildDocumentUriUsingTree(arbol, idRaiz)
        return android.provider.DocumentsContract
            .createDocument(cr, padre, "application/json", NOMBRE_COPIA)
    }

    private val NOMBRE_COPIA = "lector-copia"

    /**
     * Mete una copia encima de lo que tengas.
     *
     * NO SE PISA NADA: en el progreso gana la marca mas reciente y los
     * marcapaginas se suman. Marcar de mas es recuperable; desmarcar, no.
     */
    suspend fun importarDe(origen: android.net.Uri): String =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val texto = ctx.contentResolver.openInputStream(origen)
                    ?.bufferedReader()?.use { it.readText() } ?: return@runCatching "Vacío."
                val raiz = jsonLaxo.parseToJsonElement(texto) as? JsonObject
                    ?: return@runCatching "La copia no tiene el formato esperado."

                // clave estable -> uri de AHORA, para recolocar contra la
                // biblioteca del momento de restaurar
                val actuales = todosLosComics().associate { Progreso.clave(it.uri) to it.uri }

                val paginas = marcas.importar(
                    raiz.optJSONObject("progreso") ?: JsonObject(emptyMap()), actuales)
                val puntos = marcadores.importar(
                    raiz.optJSONArray("marcadores") ?: JsonArray(emptyList()), actuales)
                // Una copia de la v2 no trae diario: se lee como vacio y no
                // pasa nada. Un fichero viejo tiene que seguir restaurando.
                val dias = sesiones.importar(
                    raiz.optJSONArray("sesiones") ?: JsonArray(emptyList()), actuales)

                _estado.update { it.copy(sello = it.sello + 1) }
                "Restaurado: $paginas cómics, $puntos marcapáginas y $dias días de lectura."
            }.getOrElse { "No se ha podido leer la copia: ${it.javaClass.simpleName}" }
        }
}
