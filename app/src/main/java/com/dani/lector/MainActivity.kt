package com.dani.lector

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import kotlinx.coroutines.launch
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.compose.BackHandler
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dani.lector.ui.*

class MainActivity : ComponentActivity() {

    /**
     * Lo que hace el lector con las teclas de volumen mientras esta abierto.
     *
     * Se registra desde el visor y se borra al salir, asi que fuera del lector
     * el volumen sigue siendo el volumen. Va aqui y no en un Modifier porque
     * las teclas de volumen no llegan al arbol de Compose salvo que algo tenga
     * el foco, y pelearse con el foco en una pantalla sin controles es peor.
     */
    var teclasVolumen: ((subir: Boolean) -> Unit)? = null

    override fun onKeyDown(codigo: Int, evento: android.view.KeyEvent): Boolean {
        val f = teclasVolumen
        if (f != null && esVolumen(codigo)) {
            f(codigo == android.view.KeyEvent.KEYCODE_VOLUME_UP)
            return true
        }
        return super.onKeyDown(codigo, evento)
    }

    // hay que comerse tambien el KeyUp o el sistema saca la barra de volumen
    override fun onKeyUp(codigo: Int, evento: android.view.KeyEvent): Boolean {
        if (teclasVolumen != null && esVolumen(codigo)) return true
        return super.onKeyUp(codigo, evento)
    }

    private fun esVolumen(codigo: Int) =
        codigo == android.view.KeyEvent.KEYCODE_VOLUME_UP ||
        codigo == android.view.KeyEvent.KEYCODE_VOLUME_DOWN

    /**
     * El comic que llega de fuera con "abrir con...", si hay alguno.
     *
     * ESTADO DE COMPOSE Y NO UNA VARIABLE NORMAL: la pantalla ya esta montada
     * cuando llega el segundo intent, asi que hace falta que el cambio repinte.
     *
     * La actividad es `singleTask` (ver el manifiesto) para que SIEMPRE haya una
     * sola: con el modo normal, cada "abrir con" crea otra instancia, y eso
     * significa DOS VistaModelo y dos [Progreso], cada uno con su copia en
     * memoria del progreso pisandose al guardar. Es la trampa de los almacenes
     * duplicados que este proyecto ya tiene documentada.
     */
    private var invitado by mutableStateOf<android.net.Uri?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        invitado = deIntent(intent)
    }

    /** El uri de un VIEW, o null si el intent es cualquier otra cosa. */
    private fun deIntent(intent: Intent?): android.net.Uri? =
        if (intent?.action == Intent.ACTION_VIEW) intent.data else null

    /**
     * El nombre que enseña el proveedor, para el titulo y para sacar el numero.
     *
     * Si no lo dice —pasa con algun `file://`— se cae al ultimo tramo de la
     * ruta, y si tampoco, a un nombre generico. Un comic sin titulo se puede
     * leer igual; lo que no se puede es reventar por no saber como se llama.
     */
    private fun nombreDe(uri: android.net.Uri): String {
        runCatching {
            contentResolver.query(uri, null, null, null, null)?.use { c ->
                val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (i >= 0 && c.moveToFirst() && !c.isNull(i)) return c.getString(i)
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: "Cómic"
    }

    override fun onCreate(b: Bundle?) {
        enableEdgeToEdge()   // Android 15 lo impone; mejor pedirlo y controlarlo
        super.onCreate(b)
        // Mide los fotogramas que llegan tarde, que es de lo que Dani se queja
        // de verdad. Ver Fluidez: el rastro cuenta sucesos y un tiron no es un
        // suceso.
        com.dani.lector.datos.Fluidez.vigilar(this)
        invitado = deIntent(intent)
        setContent {
            TemaLector {
                val vm: VistaModelo = viewModel()
                val nav = rememberNavController()
                val estado by vm.estado.collectAsState()

                // LA COPIA AL SALIR, enganchada al ciclo de vida.
                //
                // ON_STOP y no ON_PAUSE: pausa tambien pasa al abrir un dialogo
                // del sistema o al bajar la persiana de notificaciones, y eso no
                // es salir de la app. Va aqui arriba, fuera del NavHost, para
                // que se dispare estes en la pantalla que estes.
                val duenio = androidx.lifecycle.compose.LocalLifecycleOwner.current
                DisposableEffect(duenio) {
                    val ojo = androidx.lifecycle.LifecycleEventObserver { _, evento ->
                        com.dani.lector.datos.Rastro.apunta(this@MainActivity, "ciclo: $evento")
                        if (evento == androidx.lifecycle.Lifecycle.Event.ON_STOP)
                            vm.copiaAlSalirSiToca()
                    }
                    duenio.lifecycle.addObserver(ojo)
                    onDispose { duenio.lifecycle.removeObserver(ojo) }
                }

                // UN COMIC QUE LLEGA DE FUERA. Se abre en el visor sin pasar por
                // la biblioteca: no esta en ella y no tiene por que estarlo.
                //
                // Funciona aunque no haya carpeta elegida, porque "leer" es un
                // destino aparte de "principal". Instalar la app y abrir un CBZ
                // que te acaban de mandar tiene que funcionar a la primera.
                // SIN LIMPIAR `invitado` DENTRO: cambiar la clave de un
                // LaunchedEffect desde dentro lo CANCELA, y con la espera de
                // abajo eso significa que no llegaria a navegar nunca. No hace
                // falta: la clave solo cambia cuando llega otro comic.
                LaunchedEffect(invitado) {
                    val uri = invitado ?: return@LaunchedEffect
                    val nombre = nombreDe(uri)
                    com.dani.lector.datos.Rastro.apunta(
                        this@MainActivity, "de fuera: $nombre")
                    vm.abrir(com.dani.lector.datos.Comic(
                        uri = uri.toString(),
                        nombre = nombre,
                        // Sin carpeta: NO es "la raiz". Si pusiera la raiz, el
                        // visor buscaria el "siguiente comic" entre los sueltos
                        // de tu biblioteca y ofreceria uno que no tiene nada que
                        // ver. Con la uri fuera del indice, siguienteComic ya
                        // devuelve null solo.
                        carpeta = "",
                        numero = com.dani.lector.datos.Parser.numeroDe(nombre),
                        esEspecial = com.dani.lector.datos.Parser.esEspecial(nombre)
                    ))
                    // EL NAVHOST TODAVIA NO ESTA EN PIE en la primera
                    // composicion, y nav.ir() se niega a navegar hasta que lo
                    // esta —es el cerrojo de la pantalla en negro—. Se espera en
                    // vez de saltarselo: el cerrojo se queda entero y abrir un
                    // comic con la app cerrada no se pierde. Con tope, que una
                    // espera sin fin es peor que no navegar.
                    var vueltas = 0
                    while (!nav.enPie() && vueltas < 120) {
                        kotlinx.coroutines.delay(16); vueltas++
                    }
                    nav.ir(this@MainActivity, "leer")
                }

                val elegirCarpeta = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocumentTree()
                ) { uri ->
                    if (uri != null) {
                        // LECTURA Y ESCRITURA desde el 25/08/2026.
                        //
                        // Hasta entonces se pedia solo lectura a proposito, y era
                        // lo correcto: la app no escribia nada. Ahora si, porque
                        // el conversor de CBR a CBZ deja el CBZ al lado del
                        // original y mueve el original a una subcarpeta.
                        //
                        // OJO: un permiso ya concedido NO se amplia solo. Quien
                        // eligiera la carpeta antes de este cambio la tiene en
                        // solo lectura y tendra que volver a elegirla para que la
                        // conversion pueda escribir.
                        contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        )
                        vm.elegirCarpeta(uri.toString())
                    }
                }

                // La pila de carpetas por las que vas bajando. Se guarda aqui
                // en vez de en la ruta de navegacion: los identificadores de
                // documento llevan barras y romperian la URL.
                val pila = remember { mutableStateListOf<Triple<String?, String, String>>() }
                LaunchedEffect(estado.hayCarpeta) {
                    if (pila.isEmpty()) pila.add(Triple(null, "", "Cómics"))
                }

                // CADA CAMBIO DE PANTALLA, APUNTADO. Es la miga que de verdad
                // hace falta: si la app se queda en negro, lo primero que hay
                // que saber es en que destino estaba.
                LaunchedEffect(nav) {
                    nav.currentBackStackEntryFlow.collect { entrada ->
                        com.dani.lector.datos.Rastro.apunta(
                            this@MainActivity, "pantalla: ${entrada.destination.route}")
                    }
                }

                // TRES PESTAÑAS, UN CARRUSEL, UN SOLO DESTINO.
                //
                // Antes Biblioteca, Lecturas y Ajustes eran tres destinos del
                // NavHost apilados uno sobre otro, asi que para ir de una a otra
                // habia que tocar la barra y volver con "Atras". Dani pidio pasar
                // deslizando, y eso solo sale bien si las tres son HERMANAS y no
                // una encima de otra.
                //
                // De regalo se lleva por delante el fallo de la pantalla negra:
                // desde estas tres ya no se puede sacar nada de la pila, porque
                // no hay "Atras" que lo haga. Los cerrojos de nav.atras() siguen
                // puestos para el visor y los marcapaginas, que si se apilan.
                NavHost(nav, "principal") {

                    composable("principal") {
                        if (!estado.hayCarpeta) {
                            SinCarpeta { elegirCarpeta.launch(null) }
                        } else {
                            val paginas = rememberPagerState(initialPage = 0) { 3 }
                            val alcance = rememberCoroutineScope()
                            fun irA(p: Int) { alcance.launch { paginas.animateScrollToPage(p) } }

                            // ATRAS, DE FUERA HACIA DENTRO. Se registran en este
                            // orden porque el ULTIMO gana: primero "vuelve a la
                            // biblioteca", y encima "sube de carpeta", que es el
                            // que tiene que atender cuando ya estas en ella.
                            BackHandler(enabled = paginas.currentPage != 0) { irA(0) }
                            BackHandler(enabled = paginas.currentPage == 0 && pila.size > 1) {
                                pila.removeAt(pila.lastIndex)
                            }

                            // el comic cuyo menu esta abierto, si hay alguno
                            var menu by remember { mutableStateOf<com.dani.lector.datos.Comic?>(null) }

                            // Los CBR que hayan aparecido desde la ultima vez y
                            // los numeros nuevos. El modelo se encarga de que
                            // pase UNA vez por sesion.
                            //
                            // COLA Y NO UNA VARIABLE SUELTA: los dos tardan lo
                            // suyo y pueden terminar a la vez. Con una sola
                            // variable el segundo pisaba al primero y el aviso se
                            // perdia; con dos dialogos, se solapaban.
                            val avisos = remember { mutableStateListOf<Pair<String, String>>() }
                            LaunchedEffect(estado.hayCarpeta) {
                                if (!estado.hayCarpeta) return@LaunchedEffect
                                vm.revisarCbr { avisos.add("Cómics nuevos preparados" to it) }
                                vm.revisarNovedades { avisos.add("Ha salido algo nuevo" to it) }
                            }

                            // Sin el sello en el remember: si no, la barra se
                            // vacia y vuelve a aparecer cada vez que marcas una
                            // pagina. El efecto ya la actualiza.
                            var seguirBarra by remember {
                                mutableStateOf<com.dani.lector.datos.Comic?>(null)
                            }
                            LaunchedEffect(estado.sello, estado.catalogo) {
                                seguirBarra = vm.seguirLeyendo()
                            }

                            LaunchedEffect(paginas.currentPage) {
                                com.dani.lector.datos.Rastro.apunta(this@MainActivity,
                                    "pestaña: ${paginas.currentPage}")
                            }

                            // LAS TRES PAGINAS SE QUEDAN VIVAS, y esto es el
                            // arreglo de los tirones del 04/09/2026.
                            //
                            // Por defecto el pager DESTRUYE la pagina que no se
                            // ve. En el rastro se veia clarisimo: cada vuelta a
                            // la pestaña 0 apuntaba otra vez "carpeta: «raíz»",
                            // que sale de un LaunchedEffect(docId) — o sea que
                            // el docId no habia cambiado y la pantalla se estaba
                            // RECREANDO entera. Cada deslizamiento rehacia la
                            // biblioteca de cero: releer la carpeta, recomponer
                            // el banner, el recorrido y los dos carruseles, y
                            // perder todos los `remember` por el camino.
                            //
                            // Con 1 se mantienen compuestas las vecinas. Cuesta
                            // memoria —tres pantallas ligeras— y ahorra el
                            // trabajo entero en cada pasada, que es justo donde
                            // Fluidez medía 17 de cada 300 fotogramas largos.
                            HorizontalPager(
                                paginas, Modifier.fillMaxSize(),
                                beyondViewportPageCount = 1
                            ) { pagina ->
                                when (pagina) {
                                    0 -> {
                                        val actual = pila.lastOrNull()
                                            ?: Triple(null, "", "Cómics")
                                        PantallaCarpeta(
                                            vm,
                                            docId = actual.first,
                                            ruta = actual.second,
                                            titulo = actual.third,
                                            onCarpeta = { id, ruta, nombre ->
                                                pila.add(Triple(id, ruta, nombre))
                                            },
                                            onLeer = {
                                                vm.abrir(it); nav.ir(this@MainActivity, "leer")
                                            },
                                            onMenu = { menu = it },
                                            onAtras = if (pila.size > 1) {
                                                { pila.removeAt(pila.lastIndex) }
                                            } else null
                                        )
                                    }
                                    1 -> PantallaEstadisticas(vm,
                                        onMarcadores = {
                                            nav.ir(this@MainActivity, "marcadores")
                                        },
                                        onLeer = {
                                            vm.abrir(it); nav.ir(this@MainActivity, "leer")
                                        },
                                        // Sin "Atras": ahora es una pestaña y no
                                        // una pantalla apilada, asi que la flecha
                                        // prometeria volver a algun sitio.
                                        onAtras = null)
                                    else -> {
                                        val app = application as LectorApp
                                        PantallaAjustes(
                                            cvInicial = app.claveComicVine(),
                                            onGuardar = { cv -> app.guardarClave(cv) },
                                            onCarpeta = { elegirCarpeta.launch(null) },
                                            vm = vm,
                                            onAtras = null)
                                    }
                                }
                            }

                            // MIENTRAS CONVIERTE, DECIRLO. Sin esto la app se
                            // pone a trabajar sola al abrirla y desde fuera solo
                            // se ve que va lenta y se calienta. Un aviso que dice
                            // por que tarda no molesta; una app que tarda sin
                            // explicarse, si.
                            if (estado.cargando && estado.progreso.isNotBlank()) Box(
                                Modifier.fillMaxSize().safeDrawingPadding(),
                                contentAlignment = Alignment.TopCenter
                            ) {
                                Row(
                                    Modifier.padding(12.dp)
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(Color(0xE6000000))
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(estado.progreso, fontSize = 12.sp, color = Color.White,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }

                            // EL AVISO DE DESHACER, encima de las pestañas y
                            // debajo de todo lo demas. Va aqui fuera del pager
                            // porque marcar una carpeta se hace en la
                            // biblioteca pero el aviso tiene que sobrevivir a
                            // que te vayas a otra pestaña mientras dura.
                            AvisoDeshacer(estado.deshacer,
                                onDeshacer = { vm.deshacerMarcado() },
                                onCerrar = { vm.cerrarDeshacer() })

                            BarraInferior(
                                vm = vm,
                                // La barra de "seguir leyendo" solo donde el
                                // banner NO se ve: dentro de una carpeta, o en
                                // las otras dos pestañas. En la raiz de la
                                // biblioteca diria lo mismo que la tarjeta.
                                seguir = if (pila.size > 1 || paginas.currentPage != 0)
                                             seguirBarra else null,
                                onLeer = { vm.abrir(it); nav.ir(this@MainActivity, "leer") },
                                pagina = paginas.currentPage,
                                // TOCAR "BIBLIOTECA" ESTANDO YA EN ELLA SUBE A
                                // LA RAIZ. Es lo que hacen las barras de
                                // pestañas de siempre y lo que Dani pidio al
                                // usarla: bajando tres carpetas, volver arriba
                                // eran tres toques de atras o cerrar la app.
                                //
                                // Solo cuando YA estas en la pestaña: viniendo
                                // de Lecturas, el primer toque cambia de
                                // pestaña y te deja donde estabas, que es lo
                                // que se espera. Al inicio se va con el
                                // segundo.
                                onIr = { destino ->
                                    if (destino == 0 && paginas.currentPage == 0 &&
                                        pila.size > 1
                                    ) {
                                        // Se vacia hasta la raiz de golpe, no de
                                        // una en una: la raiz es el primer
                                        // elemento y no se puede quitar.
                                        pila.removeRange(1, pila.size)
                                    } else irA(destino)
                                }
                            )

                            avisos.firstOrNull()?.let { (titulo, texto) ->
                                AlertDialog(
                                    onDismissRequest = { avisos.removeAt(0) },
                                    confirmButton = {
                                        TextButton({ avisos.removeAt(0) }) { Text("Vale") }
                                    },
                                    title = { Text(titulo) },
                                    text = { Text(texto) }
                                )
                            }

                            menu?.let { c ->
                                MenuComic(vm, c,
                                    onLeer = {
                                        vm.abrir(it); nav.ir(this@MainActivity, "leer")
                                    },
                                    onCerrar = { menu = null })
                            }
                        }
                    }

                    composable("leer") {
                        PantallaLector(vm, vm.leyendo, onAtras = { nav.atras(this@MainActivity) })
                    }

                    composable("marcadores") {
                        PantallaMarcadores(vm,
                            onLeer = { comic, pagina ->
                                vm.abrirEn(comic, pagina); nav.ir(this@MainActivity, "leer")
                            },
                            onAtras = { nav.atras(this@MainActivity) })
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun SinCarpeta(onElegir: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Tinta).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Elige tu carpeta de cómics", style = Tipo.titulo, color = Hueso)
        Text("La app mostrará tu árbol de carpetas tal como lo tengas organizado.",
            style = Tipo.secundario, color = Tenue,
            modifier = Modifier.padding(top = 10.dp, bottom = 24.dp))
        Boton("Elegir carpeta", onClick = onElegir)
    }
}

/**
 * Barra de pestañas al estilo de iOS: icono arriba, rotulo pequeño en caja
 * normal debajo, y la activa en color de acento. Nada de mayusculas espaciadas,
 * que es justo lo contrario de lo que hace Apple.
 *
 * Sin desenfoque detras a proposito: `Modifier.blur` pide API 31 y el minSdk de
 * este proyecto es 26, asi que en un movil de 2018 se veria distinto. Fondo
 * solido y en paz.
 */
@androidx.compose.runtime.Composable
private fun BarraInferior(
    vm: VistaModelo,
    seguir: com.dani.lector.datos.Comic?,
    onLeer: (com.dani.lector.datos.Comic) -> Unit,
    pagina: Int,
    onIr: (Int) -> Unit
) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
        if (seguir != null) BarraSeguirLeyendo(vm, seguir, onLeer)
        // PILDORA FLOTANTE Y NO UNA BARRA PEGADA AL BORDE. La barra de antes
        // ocupaba todo el ancho con una linea encima, que es el patron de
        // Material de hace diez años; flotando se lee como un control y no como
        // el final de la pantalla, y deja ver que la lista sigue por debajo.
        //
        // Chaflan y no esquina redonda: en 2077 nada se redondea. Y el filo,
        // porque sobre negro puro un panel oscuro sin borde no tiene forma.
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .clip(FormaChapa)
                .background(PanelAlto)
                .border(FiloAncho, FiloColor, FormaChapa)
                // LA PILDORA SE COME EL TOQUE. Solo las tres pestañas atendian
                // punteros; el relleno de 4 dp, los huecos del SpaceEvenly y el
                // filo dejaban pasar el toque a la lista de debajo, asi que
                // apuntar a "Lecturas" y abrir un comic sin querer era lo
                // normal. Un detectTapGestures vacio los absorbe sin añadir
                // semantica de boton, que es lo que haria clickable.
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // La marcada la dice el carrusel, no una constante: al deslizar
            // tiene que moverse sola, que es medio motivo de deslizar.
            Pestana("Biblioteca", Icons.Filled.Home, pagina == 0) { onIr(0) }
            Pestana("Lecturas", Icons.AutoMirrored.Filled.List, pagina == 1) { onIr(1) }
            Pestana("Ajustes", Icons.Filled.Settings, pagina == 2) { onIr(2) }
        }
    }
}

/**
 * La barra de "seguir leyendo", pegada encima de las pestañas.
 *
 * Es el mini reproductor de las apps de musica: lo que tienes a medias, siempre
 * a un toque, sin volver al principio. Aqui gana mas que alli, porque en la
 * biblioteca te metes por carpetas y el banner grande solo esta en la raiz: en
 * cuanto bajas un nivel, lo que estabas leyendo desaparecia de la vista.
 *
 * Va teñida con el color de SU portada, no con el de la pantalla: es una pieza
 * del comic que estas leyendo, no del sitio donde estas. Ver ColorPortada y
 * LECTOR-COMICS-DISENO seccion 5.
 *
 * OJO: [Portada] lleva `cargar` como TERCER parametro y `encima` como ultimo.
 * Pasar la lambda pegada al parentesis la engancharia a `encima` y la portada
 * saldria vacia sin ningun error que lo explique. Va posicional, dentro.
 */
@androidx.compose.runtime.Composable
private fun BarraSeguirLeyendo(
    vm: VistaModelo,
    comic: com.dani.lector.datos.Comic,
    onLeer: (com.dani.lector.datos.Comic) -> Unit
) {
    val ambiente by produceState<Color?>(null, comic.uri) {
        value = vm.colorDe(comic.uri)
    }
    val estado by vm.estado.collectAsState()
    val marca = remember(comic.uri, estado.sello) { vm.marcas.de(comic.uri) }

    Column(
        Modifier.fillMaxWidth()
            // OPACA del todo, al reves que las barras del visor. Alli la
            // translucidez es buena porque deja ver la pagina que hay debajo;
            // aqui la barra flota sobre una lista que se MUEVE, y con el 93%
            // de ambienteBarra se leia el "2 carpetas · 1 comics" de la fila de
            // abajo a traves del texto de la barra.
            .background(ambienteBarra(ambiente).copy(alpha = 1f))
            .clickable { onLeer(comic) }
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.width(34.dp).height(48.dp).caratula(FormaChapa).background(Panel)) {
                Portada(comic.uri, Modifier.fillMaxSize(), { vm.portada(it) })
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(comic.nombre.substringBeforeLast('.'),
                    style = Tipo.destacado, color = Hueso,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    marca?.let { "página ${it.pagina + 1} de ${it.paginas}" }
                        ?: comic.carpeta.ifBlank { "en tu biblioteca" },
                    style = Tipo.minuscula, color = Tenue,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            // El triangulo en vez de un icono de Material: es el unico glifo de
            // toda la barra y asi no entra otra dependencia de iconos por uno.
            Text("\u25B6", fontSize = 20.sp, color = Acento,
                modifier = Modifier.padding(start = 10.dp, end = 4.dp))
        }
        // La raya de progreso al borde de abajo, pegada a la linea de las
        // pestañas: dice por donde vas sin ocupar una fila de texto.
        marca?.let {
            LinearProgressIndicator(
                progress = { it.porcentaje / 100f },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = Acento, trackColor = Color(0x22FFFFFF)
            )
        }
    }
}

@androidx.compose.runtime.Composable
private fun Pestana(texto: String, icono: ImageVector, activa: Boolean, onClick: () -> Unit) {
    // LA ACTIVA VA EN CAPSULA RELLENA, con el contenido en SobreAcento: negro
    // sobre amarillo, que es la marca de la casa. Pintar el icono de amarillo y
    // ya, como antes, se distingue peor de lo que parece cuando los tres iconos
    // son del mismo gris.
    val color = if (activa) SobreAcento else Apagado
    Row(
        Modifier.clip(FormaChapa)
            .background(if (activa) Acento else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icono, null, tint = color, modifier = Modifier.size(20.dp))
        // El rotulo solo en la activa: los otros dos iconos se entienden y asi
        // la pildora no se come media pantalla de ancho.
        if (activa) Text(texto, color = color, fontSize = 12.sp,
            modifier = Modifier.padding(start = 7.dp))
    }
}

/**
 * "Hecho. ¿Deshago?" flotando encima de las pestañas.
 *
 * SE VA SOLO A LOS SIETE SEGUNDOS, y el temporizador vive en el ViewModel y no
 * aqui: si viviera en la pantalla, cambiar de pestaña lo reiniciaria o lo
 * mataria, y el aviso duraria lo que le apeteciera.
 *
 * Con la X aparte del "Deshacer": sin ella, la unica forma de quitarlo de en
 * medio seria deshacer, que es justo lo contrario de lo que quieres si te lo
 * estas leyendo para confirmar que salio bien.
 */
@androidx.compose.runtime.Composable
private fun AvisoDeshacer(texto: String, onDeshacer: () -> Unit, onCerrar: () -> Unit) {
    if (texto.isBlank()) return
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
        Row(
            Modifier.fillMaxWidth()
                // 74 dp: justo encima de la pildora de pestañas. Sin esto se
                // pintaria debajo y no se veria.
                .padding(start = 16.dp, end = 16.dp, bottom = 74.dp)
                .navigationBarsPadding()
                .clip(FormaChapa)
                .background(PanelAlto)
                .border(FiloAncho, FiloColor, FormaChapa)
                .padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(texto, style = Tipo.pie, color = Hueso, modifier = Modifier.weight(1f),
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Deshacer", style = Tipo.secundario, color = Acento,
                modifier = Modifier.clip(FormaChapa)
                    .clickableSimple(accion = onDeshacer)
                    .padding(horizontal = 10.dp, vertical = 4.dp))
            Text("\u2715", fontSize = 13.sp, color = Apagado,
                modifier = Modifier.clip(FormaChapa)
                    .clickableSimple(accion = onCerrar)
                    .padding(horizontal = 8.dp, vertical = 4.dp))
        }
    }
}

/**
 * Navegar UNA vez por toque, aunque el dedo pulse tres.
 *
 * EL FALLO QUE ARREGLAN (Dani, 03/09/2026): "salgo de lecturas dándole al botón
 * de atrás de arriba a la izquierda y si le doy mucho... se pone la pantalla en
 * negro". Y era literal:
 *
 *  - Primer toque: popBackStack() saca "lecturas" y vuelve a "inicio".
 *  - Segundo toque, mientras la transicion todavia esta en marcha: el boton de
 *    la pantalla saliente SIGUE ahi y vuelve a llamar, y esta vez saca
 *    "inicio".
 *  - Con la pila vacia el NavHost no tiene ningun destino que pintar. No es que
 *    la pantalla falle: es que NO HAY pantalla. De ahi el negro absoluto y que
 *    no responda a nada.
 *
 * En el rastro se veia como un "carpeta: «raíz»" sin su "leída:" detras, y se
 * leyo mal: parecia que abrirCarpeta se colgaba, cuando lo que pasaba es que la
 * pantalla se destruia a mitad de cargar y su corrutina moria con ella.
 *
 * LA GUARDA: solo navega el destino que esta RESUMED. Durante una transicion
 * ninguno lo esta, asi que el segundo toque no hace nada. Vale igual para
 * navigate: dos toques seguidos apilaban la misma pantalla dos veces.
 */
private fun NavHostController.enPie(): Boolean =
    currentBackStackEntry?.lifecycle?.currentState
        ?.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) == true

private fun NavHostController.atras(ctx: android.content.Context) {
    // DOS CERROJOS, Y HACEN FALTA LOS DOS.
    //
    // El de arriba pilla el toque repetido durante la transicion, pero depende
    // de ganar una carrera. Este de aqui es incondicional: sin destino DEBAJO
    // del actual, sacar el actual deja el NavHost sin nada que pintar. Da igual
    // por que se llame ni cuantas veces: la pila no se puede vaciar.
    if (previousBackStackEntry == null) {
        com.dani.lector.datos.Rastro.apunta(ctx, "  (atrás ignorado: ya no hay a dónde volver)")
        return
    }
    if (enPie()) popBackStack()
    else com.dani.lector.datos.Rastro.apunta(ctx, "  (atrás ignorado: toque repetido)")
}

private fun NavHostController.ir(ctx: android.content.Context, ruta: String) {
    if (enPie()) navigate(ruta)
    else com.dani.lector.datos.Rastro.apunta(ctx, "  (ir a $ruta ignorado: toque repetido)")
}
