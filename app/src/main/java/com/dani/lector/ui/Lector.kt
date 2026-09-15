package com.dani.lector.ui

import android.app.Activity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.content.Intent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.dani.lector.VistaModelo
import com.dani.lector.datos.Comic
import com.dani.lector.datos.Globo
import com.dani.lector.datos.Paso
import com.dani.lector.datos.SecuenciaGlobos
import com.dani.lector.datos.Rastro
import com.dani.lector.datos.Exportar
import com.dani.lector.datos.Guias
import com.dani.lector.datos.Salto
import com.dani.lector.datos.Paginas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

enum class Modo { PAGINA, TIRA }

@Composable
fun PantallaLector(vm: VistaModelo, comic: Comic?, onAtras: () -> Unit) {

    // El comic actual vive AQUI y no en la navegacion: asi se puede encadenar
    // con el siguiente de la carpeta sin volver atras ni apilar pantallas.
    var actual by remember(comic?.uri) { mutableStateOf(comic) }

    val c = actual
    if (c == null) { Fallo("No hay ningún archivo seleccionado.", onAtras); return }
    val uri = c.uri

    // El visor es el sitio con mas formas de acabar en negro: fondo negro puro,
    // barras del sistema ocultas y varios caminos que pueden no pintar nada.
    LaunchedEffect(uri) { Rastro.apunta("visor: abre ${c.nombre}") }

    var resultado by remember(uri) { mutableStateOf<Paginas?>(null) }
    LaunchedEffect(uri) { resultado = withContext(Dispatchers.IO) { vm.paginas(uri) } }

    // Pantalla encendida y barras ocultas mientras lees.
    val vista = LocalView.current
    DisposableEffect(Unit) {
        val ventana = (vista.context as? Activity)?.window
        ventana?.let {
            it.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            WindowCompat.getInsetsController(it, vista).apply {
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            ventana?.let {
                it.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                WindowCompat.getInsetsController(it, vista)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    when (val r = resultado) {
        // null = todavia cargando. Si esto y "fallo" fueran lo mismo, la
        // ruedecita giraria para siempre cuando un fichero no se puede abrir.
        null -> Box(Modifier.fillMaxSize().background(Tinta), Alignment.Center) {
            CircularProgressIndicator()
        }
        is Paginas.Error -> {
            LaunchedEffect(r) { Rastro.apunta("visor: NO ABRE — ${r.motivo}") }
            Fallo(r.motivo, onAtras)
        }
        is Paginas.Ok -> Visor(vm, c, r.nombres, onAtras) { siguiente ->
            vm.abrir(siguiente)
            actual = siguiente
        }
    }
}

@Composable
private fun Fallo(motivo: String, onAtras: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Tinta).statusBarsPadding().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("No se puede abrir", style = Tipo.titulo, color = Hueso)
        Text(motivo, style = Tipo.secundario, color = Tenue, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp, bottom = 24.dp))
        Boton("Volver", onClick = onAtras)
    }
}

@Composable
private fun Visor(
    vm: VistaModelo,
    comic: Comic,
    paginas: List<String>,
    onAtras: () -> Unit,
    onSiguiente: (Comic) -> Unit
) {
    val uri = comic.uri
    val ancho = LocalConfiguration.current.screenWidthDp

    var modo by remember { mutableStateOf(Modo.PAGINA) }
    var controles by remember { mutableStateOf(false) }
    var tira by remember { mutableStateOf(false) }
    // Leido una vez y escrito a la vez en el estado y en los ajustes: asi el
    // interruptor responde en el mismo fotograma sin esperar al sello.
    var bocadillos by remember { mutableStateOf(vm.bocadillos) }

    // El indice de la pagina que se esta guardando o compartiendo, si hay
    // alguna. La pulsacion larga en el visor no hacia nada hasta ahora, asi que
    // el hueco estaba libre y no pisa ningun gesto.
    var exportando by remember(uri) { mutableStateOf<Int?>(null) }

    // El color de la portada de ESTE comic, para teñir las barras con el.
    // Sale de la miniatura que ya esta en cache para pintar el catalogo, asi
    // que en el caso normal no cuesta ni una lectura de disco. Empieza en null
    // y entonces todo se ve como siempre: si tarda o no hay portada legible,
    // no se nota nada raro, solo no hay tinte.
    val ambiente by produceState<Color?>(null, uri) {
        value = vm.colorDe(uri)
    }

    /*
     * Tocar el centro abre los controles CON la tira de miniaturas ya
     * desplegada, no vacios.
     *
     * Es lo que se quiere casi siempre: si interrumpes la lectura para tocar la
     * pantalla, es para saltar a otra pagina o para salir. Obligar a un segundo
     * toque en el contador para ver las miniaturas era un paso de mas.
     * El contador sigue estando y sirve para plegar la tira sin cerrar la barra.
     */
    val alternarControles = {
        val abrir = !controles
        controles = abrir
        tira = abrir
    }

    // Empieza por donde lo dejaste, salvo que vengas de un marcapaginas
    val inicio = remember(uri) {
        (vm.consumirArranque() ?: vm.marcas.de(uri)?.pagina ?: 0)
            .coerceIn(0, (paginas.size - 1).coerceAtLeast(0))
    }

    // El siguiente de la carpeta, para la tarjeta del final. Se busca al abrir
    // y no al llegar: si se buscara al llegar, la tarjeta apareceria vacia y se
    // rellenaria sola delante de tus narices.
    var siguiente by remember(uri) { mutableStateOf<Comic?>(null) }
    LaunchedEffect(uri) { siguiente = vm.siguienteComic(comic) }
    val guia = remember(comic.carpeta) { Guias.de(comic.carpeta) }

    val alcance = rememberCoroutineScope()

    val estadoVm by vm.estado.collectAsState()
    val marcadas = remember(uri, estadoVm.sello) { vm.marcadores.de(uri) }

    // ¿Dos paginas por pasada? Solo en horizontal y si esta el ajuste.
    val horizontal = LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val dobles = horizontal && vm.dobles

    /*
     * Las HOJAS son lo que se ve de una pasada: una pagina, o dos.
     *
     * La portada va siempre sola aunque esten las dobles. Es lo que hace un
     * comic de verdad —la portada es una pagina impar— y si se empareja con la
     * primera interior, TODAS las parejas siguientes salen desemparejadas y el
     * comic se lee mal de principio a fin.
     */
    val hojas = remember(paginas, dobles) {
        if (!dobles) paginas.indices.map { listOf(it) }
        else buildList {
            if (paginas.isNotEmpty()) add(listOf(0))
            var i = 1
            while (i < paginas.size) {
                add(if (i + 1 < paginas.size) listOf(i, i + 1) else listOf(i))
                i += 2
            }
        }
    }

    // Cada modo rellena esto con su forma de moverse, y las teclas de volumen
    // llaman aqui sin saber si por debajo hay un pager o una lista.
    var mover by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    val contexto = LocalContext.current
    DisposableEffect(contexto) {
        val actividad = contexto as? com.dani.lector.MainActivity
        actividad?.teclasVolumen = { subir -> mover?.invoke(subir) }
        onDispose { actividad?.teclasVolumen = null }
    }

    // NADA QUE PINTAR = MENSAJE, NUNCA UN RECTANGULO NEGRO.
    //
    // El fondo del visor es negro puro, asi que cualquier camino que no pinte
    // paginas deja una pantalla en negro de la que no se sale mas que con el
    // gesto de atras — y con las barras del sistema ocultas, eso parece la app
    // colgada. Aqui se dice el motivo con numeros, que es lo que este proyecto
    // lleva escrito desde el primer dia: "los mensajes de fallo llevan NUMEROS
    // Y NOMBRES, no adjetivos".
    LaunchedEffect(paginas, hojas, inicio) {
        Rastro.apunta(
            "visor: ${paginas.size} páginas, ${hojas.size} hojas, empieza en $inicio")
    }
    if (paginas.isEmpty() || hojas.isEmpty()) {
        Fallo("El cómic se abre pero no hay nada que enseñar: " +
              "${paginas.size} páginas y ${hojas.size} hojas.", onAtras)
        return
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when (modo) {
            Modo.PAGINA -> {
                // una hoja de mas: la tarjeta del siguiente comic
                val hojaInicial = remember(hojas, inicio) {
                    hojas.indexOfFirst { inicio in it }.coerceAtLeast(0)
                }
                val estado = rememberPagerState(initialPage = hojaInicial) { hojas.size + 1 }
                LaunchedEffect(estado.currentPage, hojas) {
                    // se apunta la ULTIMA pagina de la hoja: si ves la 8 y la 9,
                    // has leido hasta la 9, no hasta la 8
                    val p = (hojas.getOrNull(estado.currentPage)?.lastOrNull()
                        ?: (paginas.size - 1)).coerceIn(0, paginas.size - 1)
                    vm.marcarPagina(comic, p, paginas.size)
                    vm.precargar(uri, paginas, p, ancho * 2)
                }
                // Con la pagina ampliada hay que cortar el deslizamiento del
                // pager: si no, el arrastre se lo come el y no puedes moverte
                // por la pagina.
                var ampliada by remember { mutableStateOf(false) }
                val densidad = LocalDensity.current.density

                // LOS GLOBOS DE LA HOJA QUE SE VE, y el que esta abierto. Van
                // ETIQUETADOS con su hoja y solo se leen si la etiqueta es la de
                // ahora: asi el globo 2 de la pagina anterior no se pinta ni un
                // fotograma encima de la nueva. Y son dos estados que no cambian
                // de objeto, que es lo que necesitan las lambdas de los toques y
                // del volumen: se crean una vez y se quedan.
                var globosPagina by remember { mutableStateOf<Pair<Int, VistaModelo.Globos?>?>(null) }
                var globoAbierto by remember { mutableStateOf<Pair<Int, Int>?>(null) }

                LaunchedEffect(estado.currentPage, bocadillos, hojas) {
                    // Pagina nueva = pagina ENTERA, venga de un toque, del volumen
                    // o de un deslizamiento: los tres cambian currentPage.
                    globoAbierto = null
                    val hojaVista = estado.currentPage
                    val p = hojas.getOrNull(hojaVista)?.singleOrNull()
                    // Lo que ya no se mira deja de calcularse: solo cuentan la que
                    // se ve y la siguiente. Deslizando deprisa se lanzaban muchas y
                    // se quitaban la CPU unas a otras (tanda 34).
                    vm.soloGlobosDe(uri,
                        if (!bocadillos || p == null) emptySet()
                        else setOfNotNull(paginas[p], paginas.getOrNull(p + 1)))
                    if (!bocadillos || p == null) return@LaunchedEffect
                    // La que se ve PRIMERO, y la siguiente cuando ya esta: van en
                    // serie, asi que la que se pide antes es la que se calcula
                    // antes. Mientras se mira la pagina entera se calcula la
                    // siguiente, y al pasar su medio segundo no se ve.
                    val g = vm.globosDe(uri, paginas[p], p + 1)
                    globosPagina = hojaVista to g
                    paginas.getOrNull(p + 1)?.let { vm.prepararGlobos(uri, it, p + 2) }
                    // Y el detalle de ESTA pagina, que es de donde se recorta el
                    // globo: asi el primer toque no espera a decodificar. Va
                    // despues de los globos a proposito: la cache de detalle es
                    // de dos, y lo que tiene que caer es la de 1600 del OCR, que
                    // ya esta calculada, no esta.
                    if (!g?.globos.isNullOrEmpty()) withContext(Dispatchers.IO) {
                        vm.pagina(uri, paginas[p], ancho * 2 * 3)
                    }
                }

                // Tercios y volumen pasan por aqui. Con los bocadillos, sin zoom
                // y con una sola pagina manda SecuenciaGlobos; si no, se pasa de
                // pagina como siempre. Vale igual con el esqueleto, que solo pasa
                // pagina, que con la regla de verdad.
                // Con las animaciones del sistema puestas, la hoja se dobla al
                // pasar (ver el pager, mas abajo).
                val conPliegue = hayAnimaciones()

                // Pasar por toque, volumen o globo a globo: con la hoja que se
                // dobla, a PASO_PAGINA para que el doblez se vea; sin animaciones
                // del sistema, el paso de siempre del pager. El arrastre con el
                // dedo no pasa por aqui: va a su ritmo y el pager lo remata.
                suspend fun pasarA(hoja: Int) =
                    if (conPliegue) estado.animateScrollToPage(hoja, animationSpec = PASO_PAGINA)
                    else estado.animateScrollToPage(hoja)

                // Un toque que llega ANTES que los globos de la pagina espera por
                // ellos (tanda 34): sin esto, para SecuenciaGlobos "sin calcular"
                // es "sin globos", y el toque pasaba de pagina saltandoselos.
                var esperandoGlobos by remember { mutableStateOf(false) }

                fun decidir(adelante: Boolean) {
                    val hojaVista = estado.currentPage
                    val globoAGlobo = bocadillos && !ampliada &&
                        hojas.getOrNull(hojaVista)?.size == 1
                    val total = globosPagina?.takeIf { it.first == hojaVista }
                        ?.second?.globos?.size ?: 0
                    val actual = globoAbierto?.takeIf { it.first == hojaVista }?.second
                    val paso = when {
                        globoAGlobo -> SecuenciaGlobos.paso(actual, total, adelante)
                        adelante -> Paso.PaginaSiguiente
                        else -> Paso.PaginaAnterior
                    }
                    when (paso) {
                        is Paso.EnPagina -> globoAbierto = paso.globo?.let { hojaVista to it }
                        Paso.PaginaSiguiente -> alcance.launch {
                            pasarA((hojaVista + 1).coerceAtMost(hojas.size))
                        }
                        Paso.PaginaAnterior -> alcance.launch {
                            pasarA((hojaVista - 1).coerceAtLeast(0))
                        }
                    }
                }

                fun avanzar(adelante: Boolean) {
                    // Esperando, un segundo toque (o el volumen) no encola otro
                    // paso: se decide una vez, con los globos ya puestos.
                    if (esperandoGlobos) return
                    val hojaVista = estado.currentPage
                    val globoAGlobo = bocadillos && !ampliada &&
                        hojas.getOrNull(hojaVista)?.size == 1
                    val listos = globosPagina?.first == hojaVista
                    val actual = globoAbierto?.takeIf { it.first == hojaVista }?.second
                    // Solo se espera si los globos CAMBIAN la respuesta. Atras desde
                    // la pagina entera va a la anterior haya los que haya, y esperar
                    // ahi seria un toque parado para nada. Se le pregunta a la regla
                    // con cero globos y con muchos, en vez de copiar aqui cuando
                    // importan: si la regla cambia, esto sigue valiendo.
                    val importan = SecuenciaGlobos.paso(actual, 0, adelante) !=
                        SecuenciaGlobos.paso(actual, 1000, adelante)
                    if (!globoAGlobo || listos || !importan) {
                        decidir(adelante)
                        return
                    }
                    esperandoGlobos = true
                    alcance.launch {
                        try {
                            withTimeoutOrNull(ESPERA_GLOBOS_MS) {
                                snapshotFlow { globosPagina?.first == hojaVista }.first { it }
                            }
                        } finally {
                            esperandoGlobos = false
                        }
                        // Si mientras tanto se ha deslizado a otra, el toque ya no
                        // era para esta.
                        if (estado.currentPage == hojaVista) decidir(adelante)
                    }
                }

                // volumen arriba = atras, volumen abajo = adelante
                LaunchedEffect(estado) { mover = { subir -> avanzar(!subir) } }

                // LA HOJA QUE SE DOBLA (tanda 33, DISENO §25). El pager se queda
                // para el gesto y lo que cambia es como se pinta: la hoja de
                // debajo QUIETA y la que se pasa ENCIMA, doblada segun el avance.
                // Vale igual para el dedo, el toque, el volumen y el globo a
                // globo: todos mueven el mismo avance. Con las animaciones del
                // sistema apagadas, el paso de siempre.
                // La que se pasa es la de donde salio el gesto: settledPage no
                // cambia hasta que el pager se para, asi que dar marcha atras a
                // mitad de gesto sigue siendo la misma hoja. Salvo saliendo de la
                // tarjeta del siguiente, que no se dobla: ahi vuelve la ultima
                // pagina desdoblandose por encima, el paso de ida al reves.
                val deArriba = minOf(estado.settledPage, hojas.size - 1)

                HorizontalPager(
                    estado,
                    Modifier.fillMaxSize(),
                    userScrollEnabled = !ampliada
                ) { i ->
                    // Cuanto se ha ido esta hoja de su sitio: 0 quieta, 1 o -1
                    // fuera. Se lee DENTRO de las lambdas de pintar, asi que cada
                    // fotograma del paso solo repinta, no recompone.
                    val desplazada = { (estado.currentPage - i) + estado.currentPageOffsetFraction }
                    val trazos = remember(densidad) { TrazosDoblez(densidad) }
                    Box(
                        if (!conPliegue) Modifier
                        // La de debajo QUIETA: se le anula lo que la mueve el
                        // pager. Y la que ya ha salido del todo, invisible: con
                        // el desplazamiento anulado caeria encima de las demas.
                        else Modifier.zIndex(if (i == deArriba) 1f else 0f).graphicsLayer {
                            val f = desplazada()
                            translationX = f * size.width
                            alpha = if (kotlin.math.abs(f) < 1f) 1f else 0f
                        }
                    ) {
                        if (i >= hojas.size) TarjetaSiguiente(vm, siguiente, guia, onSiguiente, onAtras)
                        else {
                            val hoja = hojas[i]
                            PaginaConZoom(
                                vm, uri, hoja.map { paginas[it] },
                                // con dos paginas a lo ancho, cada una necesita la mitad
                                if (hoja.size > 1) ancho else ancho * 2,
                                hoja.first() + 1,
                                llenar = vm.llenar,
                                // Ampliar a mano (doble toque o pellizco) cierra el globo:
                                // son dos formas de acercarse y a la vez no caben.
                                onZoom = { ampliada = it; if (it) globoAbierto = null },
                                onToque = { fraccionX ->
                                    // tercios: los lados avanzan -una pagina, o un globo
                                    // con los bocadillos- y el centro saca los controles.
                                    // Con zoom puesto no se avanza, que ahi el dedo esta
                                    // para mover la imagen.
                                    when {
                                        ampliada -> alternarControles()
                                        fraccionX < 0.30f -> avanzar(false)
                                        fraccionX > 0.70f -> avanzar(true)
                                        else -> alternarControles()
                                    }
                                },
                                // La ULTIMA de la hoja: en doble pagina, la que estas
                                // mirando de verdad es la de la derecha, que es la misma
                                // que se apunta como leida.
                                onMantener = { exportando = hoja.last() },
                                // Se dobla DENTRO del zoom (PaginaConZoom lo pone
                                // despues de la escala): asi con "llenar" se dobla
                                // la pagina tal y como se ve, no su tamaño sin
                                // escalar.
                                transicion = if (conPliegue && i == deArriba)
                                    Modifier.hojaQueSeDobla(desplazada, trazos) else Modifier
                            )
                        }
                    }
                }

                // EL GLOBO ABIERTO, en coordenadas de PANTALLA: fuera del
                // graphicsLayer del zoom, porque con un globo abierto no hay zoom
                // manual. Sin pointerInput a proposito: los toques lo atraviesan
                // y llegan a la pagina de debajo, que es la que sabe de tercios,
                // doble toque y controles.
                val hojaVista = estado.currentPage
                val analizada = globosPagina?.takeIf { it.first == hojaVista }?.second
                val globo = globoAbierto?.takeIf { it.first == hojaVista }
                    ?.let { analizada?.globos?.getOrNull(it.second) }
                val paginaVista = hojas.getOrNull(hojaVista)?.singleOrNull()
                if (bocadillos && !ampliada && analizada != null && globo != null &&
                    paginaVista != null
                ) {
                    val conf = LocalConfiguration.current
                    GloboAmpliado(
                        vm, uri, paginas[paginaVista], ancho * 2, analizada, globo,
                        base = escalaBase(vm.llenar,
                            conf.screenHeightDp.toFloat() / conf.screenWidthDp, 1,
                            analizada.alto.toFloat() / analizada.ancho)
                    )
                }

                if (controles) Controles(
                    actual = ((hojas.getOrNull(estado.currentPage)?.last() ?: 0) + 1)
                        .coerceIn(1, paginas.size),
                    total = paginas.size,
                    modo = modo, onModo = { modo = it }, onAtras = onAtras,
                    vm = vm, uri = uri, paginas = paginas,
                    onIr = { p ->
                        alcance.launch {
                            estado.scrollToPage(hojas.indexOfFirst { p in it }.coerceAtLeast(0))
                        }
                    },
                    tira = tira, onTira = { tira = it },
                    ambiente = ambiente,
                    marcadas = marcadas,
                    titulo = comic.nombre.substringBeforeLast('.'),
                    onMarcar = {
                        val p = hojas.getOrNull(estado.currentPage)?.last() ?: 0
                        vm.alternarMarcador(uri, p)
                    },
                    bocadillos = bocadillos,
                    onBocadillos = { bocadillos = it; vm.bocadillos = it }
                )
            }
            Modo.TIRA -> {
                // Acotado aunque `inicio` ya venga acotado: si algun dia la
                // cuenta de paginas cambia entre que se calcula y que se pinta,
                // un indice fuera de rango aqui deja la tira EN BLANCO, sin
                // error y sin nada que tocar.
                val estado = rememberLazyListState(
                    initialFirstVisibleItemIndex =
                        inicio.coerceIn(0, (paginas.size - 1).coerceAtLeast(0))
                )
                LaunchedEffect(estado.firstVisibleItemIndex) {
                    vm.marcarPagina(comic, estado.firstVisibleItemIndex, paginas.size)
                    vm.precargar(uri, paginas, estado.firstVisibleItemIndex, ancho * 2)
                }
                LaunchedEffect(estado) {
                    mover = { subir ->
                        alcance.launch {
                            val destino = (estado.firstVisibleItemIndex + if (subir) -1 else 1)
                                .coerceIn(0, paginas.size)
                            estado.animateScrollToItem(destino)
                        }
                    }
                }

                LazyColumn(state = estado, modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(paginas) { i, nombre ->
                        PaginaSimple(vm, uri, nombre, ancho * 2, i + 1,
                            onMantener = { exportando = i }) { alternarControles() }
                    }
                    // en modo tira la tarjeta va al final del scroll, igual
                    item {
                        Box(Modifier.fillMaxWidth().height(420.dp)) {
                            TarjetaSiguiente(vm, siguiente, guia, onSiguiente, onAtras)
                        }
                    }
                }
                if (controles) Controles(
                    actual = estado.firstVisibleItemIndex + 1, total = paginas.size,
                    modo = modo, onModo = { modo = it }, onAtras = onAtras,
                    vm = vm, uri = uri, paginas = paginas,
                    onIr = { p -> alcance.launch { estado.scrollToItem(p) } },
                    tira = tira, onTira = { tira = it },
                    ambiente = ambiente,
                    marcadas = marcadas,
                    titulo = comic.nombre.substringBeforeLast('.'),
                    onMarcar = { vm.alternarMarcador(uri, estado.firstVisibleItemIndex) }
                )
            }
        }

        exportando?.let { i ->
            HojaExportar(vm, comic, paginas.getOrNull(i) ?: "", i + 1, ancho) {
                exportando = null
            }
        }
    }
}

/**
 * Guardar o compartir la pagina que tienes delante.
 *
 * LA PAGINA SE SACA OTRA VEZ Y EN GRANDE, no se reutiliza la que hay pintada:
 * esa esta decodificada al ancho de la PANTALLA, que para leer sobra y para
 * guardar es una imagen borrosa. Se pide al triple, que es la misma resolucion
 * que usa el zoom.
 *
 * Se pide al abrir la hoja y no al pulsar cada opcion: asi la espera ocurre una
 * vez, con la hoja ya delante enseñando que esta trabajando, en vez de despues
 * de tocar —que es cuando parece que el boton no ha hecho nada—.
 */
@Composable
private fun HojaExportar(
    vm: VistaModelo,
    comic: Comic,
    nombrePagina: String,
    numero: Int,
    ancho: Int,
    onCerrar: () -> Unit
) {
    val ctx = LocalContext.current
    val estadoHoja = rememberModalBottomSheetState()
    val alcance = rememberCoroutineScope()
    var bmp by remember(nombrePagina) { mutableStateOf<ImageBitmap?>(null) }
    var aviso by remember { mutableStateOf("") }
    // Mientras escribe: las dos opciones se apagan para que no se pueda pulsar
    // dos veces y salgan dos ficheros.
    var trabajando by remember { mutableStateOf(false) }

    LaunchedEffect(nombrePagina) {
        if (nombrePagina.isBlank()) return@LaunchedEffect
        bmp = withContext(Dispatchers.IO) { vm.pagina(comic.uri, nombrePagina, ancho * 3) }
    }

    val fichero = remember(comic.nombre, numero) {
        Exportar.nombre(comic.nombre, numero)
    }

    ModalBottomSheet(onDismissRequest = onCerrar, sheetState = estadoHoja,
        containerColor = Panel) {
        Column(Modifier.padding(bottom = 28.dp)) {
            Text("Página $numero", style = Tipo.destacado, color = Hueso,
                modifier = Modifier.padding(20.dp, 4.dp, 20.dp, 14.dp))

            Box(Modifier.padding(start = 20.dp).fillMaxWidth()
                .height(0.5.dp).background(Linea))

            val listo = bmp != null && !trabajando

            // Guardar en la galeria solo desde Android 10. Por debajo haria
            // falta WRITE_EXTERNAL_STORAGE, que es un permiso enorme para esto;
            // compartir hace lo mismo con dos toques mas y sin pedir nada.
            if (Exportar.sePuedeGuardar) OpcionMenu(
                "Guardar en la galería", "en el álbum «Lector»", activo = listo
            ) {
                val b = bmp ?: return@OpcionMenu
                // FUERA DEL HILO PRINCIPAL: comprimir un JPEG de 1200 px y
                // escribirlo son unos cuantos fotogramas perdidos, y se notan
                // justo al tocar, que es cuando peor sientan.
                trabajando = true
                alcance.launch {
                    val bien = withContext(Dispatchers.IO) {
                        // .asAndroidBitmap() porque comprimir a JPEG es de
                        // Android y Exportar se queda en :app. Una vez por
                        // pagina guardada, no por repintado.
                        Exportar.aGaleria(ctx, b.asAndroidBitmap(), fichero)
                    }
                    trabajando = false
                    aviso = if (bien) "Guardada en la galería"
                            else "No se ha podido guardar"
                }
            }

            OpcionMenu("Compartir", activo = listo) {
                val b = bmp ?: return@OpcionMenu
                trabajando = true
                alcance.launch {
                    val intent = withContext(Dispatchers.IO) {
                        Exportar.intentDeCompartir(ctx, b.asAndroidBitmap(), fichero)
                    }
                    trabajando = false
                    if (intent == null) aviso = "No se ha podido preparar la imagen"
                    else {
                        ctx.startActivity(Intent.createChooser(intent, null))
                        onCerrar()
                    }
                }
            }

            // Solo se habla cuando algo va mal o acaba de pasar algo: es la
            // regla del texto del proyecto. Mientras carga, las opciones ya
            // salen apagadas y eso lo dice todo sin una linea de prosa.
            if (aviso.isNotBlank()) Text(aviso, style = Tipo.pie, color = Tenue,
                modifier = Modifier.padding(20.dp, 6.dp))
        }
    }
}

/** Una pagina en pequeño, para la tira de saltar. */
@Composable
private fun Miniatura(
    vm: VistaModelo, uri: String, nombre: String, num: Int,
    actual: Boolean, marcada: Boolean, onIr: () -> Unit
) {
    var bmp by remember(nombre) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(nombre) {
        // 110 px: por debajo del limite de miniatura, asi va a su propia cache
        // y no echa fuera las paginas que estas leyendo
        bmp = withContext(Dispatchers.IO) { vm.pagina(uri, nombre, 110) }
    }
    Column(
        Modifier.padding(horizontal = 3.dp).clickableSimple(accion = onIr),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.width(50.dp).height(75.dp).caratula(FormaChapa).background(Panel)) {
            bmp?.let {
                Image(it, "Página $num", Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop)
            }
            // asi se encuentran los marcapaginas: pasando la tira
            if (marcada) Text("\u2605", fontSize = 13.sp, color = Acento,
                modifier = Modifier.align(Alignment.TopEnd).padding(2.dp))
        }
        Text("$num", style = Tipo.minuscula,
            color = if (actual) Acento else Apagado,
            modifier = Modifier.padding(top = 3.dp))
    }
}

/**
 * La pagina de mas que va detras de la ultima: el siguiente comic de la carpeta.
 *
 * Se hace asi, con una pagina de verdad dentro del pager, y no detectando un
 * deslizamiento "mas alla del final": un gesto que hay que adivinar no lo
 * descubre nadie, y ademas cuando es el ultimo comic de la carpeta no pasaria
 * nada y parece que la app se ha quedado colgada. Una tarjeta se ve y sabe
 * decir que no hay mas.
 */
@Composable
private fun TarjetaSiguiente(
    vm: VistaModelo,
    siguiente: Comic?,
    /** La guia de lectura de esta carpeta, si hay (ver [Guias]). */
    guia: String?,
    onSiguiente: (Comic) -> Unit,
    onAtras: () -> Unit
) {
    val enlaces = LocalUriHandler.current
    Column(
        Modifier.fillMaxSize().background(Tinta).padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (siguiente == null) {
            Text("Has terminado", style = Tipo.titulo, color = Hueso)
            Text("Era el último cómic de esta carpeta.",
                style = Tipo.secundario, color = Tenue, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp, bottom = 24.dp))
            Boton("Volver a la biblioteca", onClick = onAtras)
        } else {
            Text("SIGUIENTE", style = Tipo.minuscula, color = Apagado, letterSpacing = 1.sp)
            Box(
                Modifier.padding(top = 14.dp).width(150.dp).height(225.dp)
                    .caratula().clickableSimple { onSiguiente(siguiente) }
            ) {
                Portada(siguiente.uri, Modifier.fillMaxSize(), { vm.portada(it) }, { vm.portadaYa(it) })
            }
            Text(siguiente.nombre.substringBeforeLast('.'),
                style = Tipo.destacado, color = Hueso, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp))
            Spacer(Modifier.height(20.dp))
            Boton("Leer") { onSiguiente(siguiente) }
        }
        // Secundario y debajo: lo normal al terminar es seguir, no consultar.
        // Va en las dos ramas porque al acabar la carpeta es cuando mas falta
        // saber que viene despues.
        if (guia != null) {
            Boton("Orden de lectura", Modifier.padding(top = 12.dp), relleno = false) {
                enlaces.openUri(guia)
            }
        }
    }
}

/**
 * Pagina con zoom. El pellizco sigue el gesto al momento; el doble toque va
 * animado, que si no da un salto feo.
 */
@Composable
private fun PaginaConZoom(
    vm: VistaModelo, uri: String, nombres: List<String>, anchoPx: Int, num: Int,
    llenar: Boolean = false,
    onZoom: (Boolean) -> Unit = {},
    onToque: (Float) -> Unit,
    // ANTES de `transicion`, que ya tenia valor por defecto, y las dos antes de
    // ninguna lambda final: la trampa de la lambda final en este fichero ya ha
    // mordido. Ninguna llamada de aqui la usa pegada al parentesis.
    onMantener: () -> Unit = {},
    transicion: Modifier = Modifier
) {
    val escala = remember(nombres) { Animatable(1f) }
    val desX = remember(nombres) { Animatable(0f) }
    val desY = remember(nombres) { Animatable(0f) }
    val alcance = rememberCoroutineScope()

    /*
     * LA ESCALA DE PARTIDA.
     *
     * 1 = la pagina entera cabe a lo ancho. En un movil alargado eso deja
     * bandas negras arriba y abajo, porque una pagina de comic es menos
     * alargada que la pantalla.
     *
     * Con "llenar" se parte de la escala que hace que llegue de arriba abajo.
     * Y a partir de ahi TODO se mide contra esa base, no contra 1: el doble
     * toque vuelve a la base, el pellizco no puede bajar de ella, y "hay zoom"
     * significa "estas por encima de la base". Si no, en modo llenar la app
     * creeria que siempre hay zoom, el pasapaginas quedaria muerto y no se
     * podria deslizar para cambiar de pagina.
     */
    val pantalla = LocalConfiguration.current
    val proporcionPantalla =
        pantalla.screenHeightDp.toFloat() / pantalla.screenWidthDp.toFloat()
    var proporcionPagina by remember(nombres) { mutableStateOf(0f) }

    val base = escalaBase(llenar, proporcionPantalla, nombres.size, proporcionPagina)

    LaunchedEffect(base) {
        escala.snapTo(base); desX.snapTo(0f); desY.snapTo(0f)
    }

    LaunchedEffect(escala.value, base) { onZoom(escala.value > base * 1.01f) }

    /*
     * Al ampliar, la pagina se ve pixelada porque esta decodificada para el
     * tamaño de la PANTALLA: al agrandar cinco veces se estiran pixeles que no
     * existen. Cuando hay zoom se vuelve a decodificar mas grande.
     *
     * Solo mientras hay zoom y solo esta pagina: hacerlo siempre multiplicaria
     * la memoria por nueve para algo que no se ve el 95% del tiempo, y ya
     * sabemos como acaba eso.
     *
     * La espera de un cuarto de segundo es para no recargar en mitad del
     * pellizco: mientras el dedo se mueve la escala cambia sesenta veces por
     * segundo, y sin esto cada una lanzaria una decodificacion.
     */
    var detalle by remember(nombres) { mutableStateOf(false) }
    LaunchedEffect(escala.value > base * 1.4f) {
        val quiere = escala.value > base * 1.4f
        if (quiere != detalle) {
            delay(250)
            if ((escala.value > base * 1.4f) == quiere) detalle = quiere
        }
    }

    Box(
        Modifier.fillMaxSize()
            .pointerInput(nombres) {
                // Detector propio en vez de detectTransformGestures: aquel
                // consume TODOS los eventos, incluido el arrastre de un dedo,
                // y entonces el pasapaginas no recibe el deslizamiento.
                // Aqui solo se consume con dos dedos o si ya hay zoom.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)

                    // Cuanto se ha movido el dedo en este gesto y si ya cuenta
                    // como arrastre. Hace falta por esto: si con zoom se consume
                    // CUALQUIER movimiento, los dos o tres pixeles que se mueve
                    // el dedo al dar un doble toque se consumen tambien, el
                    // detector de toques de abajo ya no los ve, y el doble toque
                    // no llega nunca. Que es justo lo que pasaba: con zoom
                    // puesto, el doble toque no volvia al tamaño original.
                    var recorrido = 0f
                    var arrastrando = false

                    do {
                        val evento = awaitPointerEvent()
                        val dedos = evento.changes.count { it.pressed }
                        val hayZoom = escala.value > base * 1.01f
                        val zoom = evento.calculateZoom()
                        val arrastre = evento.calculatePan()

                        // con dos dedos es un pellizco desde el primer momento;
                        // con uno, hay que pasar del umbral del sistema
                        if (dedos > 1) {
                            arrastrando = true
                        } else if (hayZoom && !arrastrando) {
                            recorrido += kotlin.math.abs(arrastre.x) + kotlin.math.abs(arrastre.y)
                            if (recorrido > viewConfiguration.touchSlop) arrastrando = true
                        }

                        if (dedos > 1 || (hayZoom && arrastrando)) {
                            alcance.launch {
                                val nueva = (escala.value * zoom).coerceIn(base, base * 5f)
                                escala.snapTo(nueva)      // sigue el dedo, sin animar
                                if (nueva > base) {
                                    desX.snapTo(desX.value + arrastre.x)
                                    desY.snapTo(desY.value + arrastre.y)
                                } else { desX.snapTo(0f); desY.snapTo(0f) }
                            }
                            evento.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (evento.changes.any { it.pressed })
                }
            }
            .pointerInput(nombres) {
                detectTapGestures(
                    onTap = { posicion -> onToque(posicion.x / size.width) },
                    // EN EL MISMO DETECTOR y no en otro pointerInput: dos
                    // detectores sobre el mismo elemento se pisan, y en esta
                    // pantalla eso ya costo el doble toque una vez.
                    onLongPress = { onMantener() },
                    onDoubleTap = {
                        alcance.launch {
                            val destino = if (escala.value > base * 1.01f) base
                                          else base * 2.5f
                            if (destino == base) {
                                launch { desX.animateTo(0f, tween(250)) }
                                launch { desY.animateTo(0f, tween(250)) }
                            }
                            escala.animateTo(destino, tween(250))
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Row(
            // El zoom por FUERA y la transicion —la hoja que se dobla— por
            // DENTRO: el doblez se calcula sobre la pagina tal y como se ve,
            // tambien con "llenar", que la agranda por encima de la pantalla.
            Modifier.graphicsLayer(
                scaleX = escala.value, scaleY = escala.value,
                translationX = desX.value, translationY = desY.value
            ).then(transicion),
            verticalAlignment = Alignment.CenterVertically
        ) {
            nombres.forEachIndexed { i, n ->
                Box(Modifier.weight(1f)) {
                    Contenido(vm, uri, n, if (detalle) anchoPx * 3 else anchoPx,
                        num + i, Modifier.fillMaxWidth(),
                        onProporcion = { if (i == 0) proporcionPagina = it })
                }
            }
        }
    }
}

// EL PASO DE PAGINA POR TOQUE, VOLUMEN O GLOBO A GLOBO (tanda 33b), para tocarlo
// cuando Dani lo pruebe. El del pager por defecto dura ~0,2 s y el doblez apenas
// se veia; Dani lo quiere en ~0,5.
//
// LA CURVA es la ease-in-out de siempre (0.42, 0, 0.58, 1), simetrica: la
// esquina se levanta despacio, lo mas rapido es el medio —la hoja casi de canto,
// que es lo que menos se mira— y se posa despacio, como el papel que cae
// frenado por el aire. Una lineal arranca y para en seco, que en papel parece
// carton. Y FastOutSlowIn, la de Material, se come de golpe justo el principio,
// que es donde se ve el doblez.
private const val PASO_PAGINA_MS = 500

// LO QUE UN TOQUE ESPERA A LOS GLOBOS de la pagina que se ve (tanda 34). Casi
// siempre llegan antes: la que se ve se calcula la primera, desde que aparece.
// El tope es para la que tarda de mas —Absolute Batman llego a 3 s—: pasado, se
// pasa de pagina como si no tuviera globos. Un toque parado mucho mas que esto
// se lee como que la app se ha colgado, que es peor que saltarse los globos.
private const val ESPERA_GLOBOS_MS = 2000L
private val PASO_PAGINA = tween<Float>(PASO_PAGINA_MS, easing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f))

// LOS NUMEROS DE LA HOJA QUE SE DOBLA, puestos a ojo para tocarlos cuando Dani
// lo pruebe. Ninguno sale de medir.
//
//  - REVERSO_ACLARADO: el blanco que lleva encima la solapa. Es el reverso del
//    papel: el dibujo se transparenta, pero apagado. Sin esto la solapa parece
//    otra pagina pegada encima y no la misma hoja vista por detras.
//  - SOMBRA_OSCURA: el negro de la sombra junto al doblez, en lo mas oscuro.
//  - SOMBRA_ANCHO_DP: lo que tarda en desvanecerse desde el doblez. Mas estrecha
//    parece una raya; mas ancha, que la pagina de debajo esta sucia.
private const val REVERSO_ACLARADO = 0.6f
private const val SOMBRA_OSCURA = 0.35f
private const val SOMBRA_ANCHO_DP = 28f

/**
 * Lo que se reutiliza de un fotograma a otro mientras se pasa la hoja: los dos
 * Path, la matriz del reflejo y el degradado de la sombra. Pasar pagina son
 * unos treinta fotogramas seguidos, y crear esto en cada uno seria basura para
 * el recolector justo cuando se esta mirando si hay tirones (Fluidez).
 */
private class TrazosDoblez(densidad: Float) {
    val plana = Path()
    val solapa = Path()
    val reflejo = Matrix()
    val anchoSombra = SOMBRA_ANCHO_DP * densidad
    // Del eje hacia fuera y en PIXELES: por eso se monta aqui, con la densidad,
    // y no en un val de fichero como los degradados de fracciones.
    val sombra = Brush.horizontalGradient(
        listOf(Color.Black.copy(alpha = SOMBRA_OSCURA), Color.Transparent),
        startX = 0f, endX = anchoSombra
    )
}

/** El poligono en un Path que ya existe, sin crear otro. */
private fun Path.poligono(puntos: List<Offset>) {
    rewind()
    for (k in 0 until puntos.size) {
        val p = puntos[k]
        if (k == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
    }
    close()
}

/**
 * La matriz que refleja sobre el eje del doblez, sacada de
 * [HojaQueSeDobla.reflejar] con tres puntos: el origen y un paso por cada eje.
 * Un reflejo es afin, asi que tres puntos lo fijan. Y asi no hay una segunda
 * cuenta del reflejo que pueda discrepar de la que tiene prueba: si reflejar
 * esta bien, esto tambien.
 */
private fun Matrix.reflejoSobre(desde: Offset, hasta: Offset) {
    val o = HojaQueSeDobla.reflejar(Offset.Zero, desde, hasta)
    val ex = HojaQueSeDobla.reflejar(Offset(1f, 0f), desde, hasta) - o
    val ey = HojaQueSeDobla.reflejar(Offset(0f, 1f), desde, hasta) - o
    reset()
    values[Matrix.ScaleX] = ex.x
    values[Matrix.SkewY] = ex.y
    values[Matrix.SkewX] = ey.x
    values[Matrix.ScaleY] = ey.y
    values[Matrix.TranslateX] = o.x
    values[Matrix.TranslateY] = o.y
}

/**
 * La hoja que se pasa, doblada como papel segun cuanto se ha ido de su sitio.
 * La geometria es de [HojaQueSeDobla], en comun; aqui solo se pinta:
 *
 *  - lo PLANO, recortado a su poligono;
 *  - la SOLAPA, que es la MISMA pagina reflejada sobre el eje —el reverso del
 *    papel—, recortada a la suya y aclarada;
 *  - y una SOMBRA a lo largo del eje, hacia el lado que queda al descubierto:
 *    la que echa el doblez sobre la pagina de debajo. Con un degradado y sin
 *    blur, porque el blur pide API 31 y el minimo aqui es 26.
 *
 * El doblez es RECTO: la curvatura del papel pide shaders de tiempo de
 * ejecucion, que en Compose llegan con API 33 (DISENO §25).
 */
private fun Modifier.hojaQueSeDobla(desplazada: () -> Float, t: TrazosDoblez) =
    drawWithContent {
        val f = desplazada()
        val avance = kotlin.math.abs(f).coerceAtMost(1f)
        // Quieta, tal cual y sin recortes: es lo que pasa casi todo el tiempo.
        if (avance == 0f) {
            drawContent()
            return@drawWithContent
        }
        val p = HojaQueSeDobla.pliegue(size.width, size.height, avance, adelante = f > 0f)
        if (p.plana.size >= 3) {
            t.plana.poligono(p.plana)
            clipPath(t.plana) { this@drawWithContent.drawContent() }
        }
        if (p.solapa.size < 3) return@drawWithContent
        t.solapa.poligono(p.solapa)
        t.reflejo.reflejoSobre(p.ejeDesde, p.ejeHasta)
        clipPath(t.solapa) {
            withTransform({ transform(t.reflejo) }) { this@drawWithContent.drawContent() }
            drawRect(Color.White.copy(alpha = REVERSO_ACLARADO))
        }

        // La sombra, perpendicular al eje y hacia el lado CONTRARIO a la solapa:
        // la solapa se dobla por encima de lo plano, asi que el otro lado es el
        // que queda al descubierto, con la pagina de debajo.
        val eje = p.ejeHasta - p.ejeDesde
        val largo = eje.getDistance()
        if (largo == 0f) return@drawWithContent
        var nx = -eje.y / largo
        var ny = eje.x / largo
        var cx = 0f
        var cy = 0f
        for (k in 0 until p.solapa.size) {
            cx += p.solapa[k].x
            cy += p.solapa[k].y
        }
        cx /= p.solapa.size
        cy /= p.solapa.size
        if ((cx - p.ejeDesde.x) * nx + (cy - p.ejeDesde.y) * ny > 0f) {
            nx = -nx
            ny = -ny
        }
        val grados = Math.toDegrees(kotlin.math.atan2(ny, nx).toDouble()).toFloat()
        // Un rectangulo que cubre el eje de punta a punta, girado para que su
        // ancho vaya del eje hacia fuera: el degradado es el de siempre y lo
        // unico que cambia por fotograma es la transformacion.
        val cubre = size.width + size.height
        // Entra y sale con el paso, 0 en los dos extremos. A fuerza fija saltaria
        // de golpe al primer toque, y al final —con el eje ya en el borde
        // izquierdo y la solapa fuera de la pantalla— dejaria una raya oscura en
        // la pagina nueva que desapareceria de golpe al pararse el pager.
        val fuerza = kotlin.math.sin(Math.PI * avance).toFloat()
        withTransform({
            translate(p.ejeDesde.x, p.ejeDesde.y)
            rotate(grados, pivot = Offset.Zero)
        }) {
            drawRect(t.sombra, topLeft = Offset(0f, -cubre),
                size = Size(t.anchoSombra, 2 * cubre), alpha = fuerza)
        }
    }

/**
 * El globo abierto: el globo ampliado encima de la pagina, recortado por su
 * CONTORNO y no por el recuadro, que se llevaria las esquinas del dibujo de
 * alrededor.
 *
 * SIN OSCURECER LA PAGINA, desde la tanda 30. La 29 la ponia en negro al 60%
 * por detras, como Play Books, y Dani al probarlo: "que no se oscurezca lo
 * demas, simplemente que se haga el bocadillo mas grande y ya". El globo se
 * despega con el filo.
 *
 * Todo sale de la imagen analizada ([analizada], la de 1600 del OCR): a la de
 * DETALLE por proporcion, que es de donde se recorta para que no pixele; y a
 * PANTALLA con el mismo encaje que pinta la pagina —Fit, centrado y por la
 * escala de partida [base]—.
 */
@Composable
private fun GloboAmpliado(
    vm: VistaModelo, uri: String, nombre: String, anchoPx: Int,
    analizada: VistaModelo.Globos, globo: Globo, base: Float
) {
    // La del zoom, anchoPx * 3: la misma clave, asi que si ya se amplio a mano o
    // el Visor la dejo precalentada no se decodifica otra vez. Si fallara, la de
    // pantalla: pixela, pero se lee.
    var detalle by remember(nombre) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(nombre) {
        detalle = withContext(Dispatchers.IO) {
            vm.pagina(uri, nombre, anchoPx * 3) ?: vm.pagina(uri, nombre, anchoPx)
        }
    }
    val entrada = remember(globo) { Animatable(0f) }
    val ms = if (hayAnimaciones()) GLOBO_ENTRADA_MS else 0
    val img = detalle ?: return
    // Arranca cuando ya hay imagen, no antes: con el detalle aun decodificandose
    // la animacion se gastaria sin nada que enseñar.
    LaunchedEffect(globo) { entrada.animateTo(1f, tween(ms)) }

    val r = globo.recuadro
    Canvas(Modifier.fillMaxSize()) {
        val t = entrada.value
        val escala = minOf(size.width / analizada.ancho, size.height / analizada.alto) * base
        val x0 = (size.width - analizada.ancho * escala) / 2
        val y0 = (size.height - analizada.alto * escala) / 2
        val enPagina = Rect(x0 + r.izq * escala, y0 + r.arriba * escala,
            x0 + r.der * escala, y0 + r.abajo * escala)
        val destino = lerp(enPagina, encuadreGlobo(enPagina, size), t)

        val silueta = Path().apply {
            globo.contorno.forEachIndexed { i, p ->
                val x = destino.left + (p.x - r.izq) * destino.width / r.ancho
                val y = destino.top + (p.y - r.arriba) * destino.height / r.alto
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        // El mismo trozo en el detalle. Acotado a la imagen: por redondeo, un
        // globo pegado al filo de la pagina puede caer un pixel fuera.
        val kx = img.width.toFloat() / analizada.ancho
        val ky = img.height.toFloat() / analizada.alto
        val sx = (r.izq * kx).toInt().coerceIn(0, img.width - 1)
        val sy = (r.arriba * ky).toInt().coerceIn(0, img.height - 1)
        val sw = ((r.der * kx).toInt().coerceAtMost(img.width) - sx).coerceAtLeast(1)
        val sh = ((r.abajo * ky).toInt().coerceAtMost(img.height) - sy).coerceAtLeast(1)
        clipPath(silueta) {
            drawImage(img, IntOffset(sx, sy), IntSize(sw, sh),
                IntOffset(destino.left.toInt(), destino.top.toInt()),
                IntSize(destino.width.toInt().coerceAtLeast(1),
                    destino.height.toInt().coerceAtLeast(1)))
        }
        // El filo de las caratulas: sobre el negro, sin el, un globo de fondo
        // oscuro no tendria forma.
        drawPath(silueta, FiloColor, style = Stroke(FiloAncho.toPx()))
    }
}

@Composable
private fun PaginaSimple(
    vm: VistaModelo, uri: String, nombre: String, anchoPx: Int, num: Int,
    onMantener: () -> Unit = {}, onToque: () -> Unit
) {
    Box(
        Modifier.fillMaxWidth().pointerInput(nombre) {
            detectTapGestures(onTap = { onToque() }, onLongPress = { onMantener() })
        },
        contentAlignment = Alignment.Center
    ) {
        Contenido(vm, uri, nombre, anchoPx, num, Modifier.fillMaxWidth())
    }
}

@Composable
private fun Contenido(
    vm: VistaModelo, uri: String, nombre: String, anchoPx: Int, num: Int, mod: Modifier,
    onProporcion: (Float) -> Unit = {}
) {
    var bmp by remember(nombre) { mutableStateOf<ImageBitmap?>(null) }
    var fallo by remember(nombre) { mutableStateOf(false) }
    // Se guarda por NOMBRE y no por nombre+ancho a proposito: al pedir la
    // version de detalle, la pequeña sigue en pantalla mientras se decodifica
    // la grande. Si se borrara, ampliar daria un parpadeo con la ruedecita.
    LaunchedEffect(nombre, anchoPx) {
        val b = withContext(Dispatchers.IO) { vm.pagina(uri, nombre, anchoPx) }
        if (b != null) {
            bmp = b
            if (b.width > 0) onProporcion(b.height.toFloat() / b.width)
        } else if (bmp == null) fallo = true
    }
    when {
        bmp != null -> Image(bmp!!, "Página $num",
            mod.fillMaxWidth(), contentScale = ContentScale.Fit)
        fallo -> Box(Modifier.fillMaxWidth().height(200.dp), Alignment.Center) {
            Text("No se ha podido leer la página $num", style = Tipo.secundario, color = Tenue)
        }
        else -> Box(Modifier.fillMaxWidth().height(200.dp), Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}

/** Barra que aparece al tocar la pantalla. */
@Composable
private fun Controles(
    actual: Int, total: Int, modo: Modo, onModo: (Modo) -> Unit, onAtras: () -> Unit,
    titulo: String,
    vm: VistaModelo, uri: String, paginas: List<String>, onIr: (Int) -> Unit,
    tira: Boolean, onTira: (Boolean) -> Unit,
    marcadas: Set<Int>, onMarcar: () -> Unit,
    ambiente: Color? = null,
    // null = no se enseña. Solo lo pasa el modo pagina.
    bocadillos: Boolean = false,
    onBocadillos: ((Boolean) -> Unit)? = null
) {
    // Lo escrito en "ir a la pagina", o null si el dialogo no esta abierto.
    // Va aqui y no en el Visor porque los dos modos —pagina y tira— pintan este
    // mismo componente: escribirlo una vez lo deja puesto en los dos.
    var salto by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(
            // safeDrawing y NO statusBarsPadding. En el visor se ocultan las
            // barras del sistema (ver arriba, WindowInsetsControllerCompat), asi
            // que statusBarsPadding devuelve CERO y el titulo se quedaba pegado
            // al borde de arriba, debajo del agujero de la camara. safeDrawing
            // incluye el recorte de pantalla, que sigue estando aunque las
            // barras no se vean, que es justo el caso de este movil.
            //
            // El fondo va ANTES del padding a proposito: el color llega hasta el
            // borde y lo que se aparta es el texto.
            Modifier.fillMaxWidth().background(ambienteVelo(ambiente))
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // la barra del visor es la unica que se traduce mal a iOS: alli seria
            // translucida, pero blur pide API 31 y el minSdk es 26. Negro al 80%.
            Text("\u2039", fontSize = 28.sp, color = Hueso,
                modifier = Modifier.padding(end = 12.dp).clickableSimple(accion = onAtras))

            // El nombre arriba y el contador debajo, en la misma columna: en la
            // barra no cabian los dos en fila sin que el nombre se quedara en
            // tres letras. El contador sigue siendo el boton de las miniaturas.
            Column(Modifier.weight(1f).clickableSimple { onTira(!tira) }) {
                Text(titulo, style = Tipo.destacado, color = Hueso,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                // EL NOMBRE ABRE LA TIRA, EL NUMERO PIDE UN NUMERO. Antes toda
                // la columna hacia lo mismo; ahora el contador tiene su propio
                // toque, y como un hijo con `clickable` se come el evento, el de
                // la columna no llega a dispararse. Es el reparto obvio: tocas
                // un numero, escribes un numero.
                Text("$actual / $total", style = Tipo.pie,
                    color = if (tira) Acento else Tenue,
                    modifier = Modifier.padding(top = 1.dp)
                        .clickableSimple { salto = "" })
            }
            // el marcapaginas de la pagina en la que estas
            val estrella = (actual - 1) in marcadas
            Text(if (estrella) "\u2605" else "\u2606", fontSize = 21.sp,
                color = if (estrella) Acento else Tenue,
                modifier = Modifier.padding(end = 18.dp).clickableSimple(accion = onMarcar))

            // Texto que cambia de color y no un Interruptor: el de Componentes
            // es una fila de ajustes a todo el ancho y aqui no cabe. Acento
            // encendido y Tenue apagado, igual que la estrella.
            if (onBocadillos != null) Text("Bocadillos", style = Tipo.secundario,
                color = if (bocadillos) Acento else Tenue,
                modifier = Modifier.padding(end = 18.dp)
                    .clickableSimple { onBocadillos(!bocadillos) })

            Text(if (modo == Modo.PAGINA) "Página" else "Tira",
                style = Tipo.secundario, color = Acento,
                modifier = Modifier.clickableSimple {
                    onModo(if (modo == Modo.PAGINA) Modo.TIRA else Modo.PAGINA)
                })
        }
        Spacer(Modifier.weight(1f))

        if (tira) {
            val listaTira = rememberLazyListState(
                initialFirstVisibleItemIndex = (actual - 3).coerceAtLeast(0))
            LazyRow(
                state = listaTira,
                modifier = Modifier.fillMaxWidth()
                    .background(ambienteVelo(ambiente, desdeArriba = false))
                    .padding(vertical = 10.dp)
            ) {
                item { Spacer(Modifier.width(10.dp)) }
                itemsIndexed(paginas) { i, nombre ->
                    Miniatura(vm, uri, nombre, i + 1, i + 1 == actual, i in marcadas) {
                        onIr(i)
                    }
                }
                item { Spacer(Modifier.width(10.dp)) }
            }
        }

        // ── LA BARRA DE PROGRESO, ARRASTRABLE ──
        //
        // UN SOLO pointerInput y un detector propio, no dos modificadores: la
        // trampa de "el primero se come los eventos del segundo" ya mordio una
        // vez en esta pantalla (ver el zoom). Aqui el mismo gesto sirve de toque
        // y de arrastre, porque se apunta la posicion desde el primer contacto.
        //
        // Y SOLO SE VIAJA AL SOLTAR. Mientras el dedo se mueve, la barra enseña
        // a donde iria y el globo dice el numero, pero no se pasa de pagina: con
        // 500 paginas, seguir el dedo seria medio millar de decodificaciones.
        var anchoBarra by remember { mutableStateOf(0) }
        var arrastre by remember { mutableStateOf<Int?>(null) }
        val marcada = arrastre ?: (actual - 1)

        // SEPARADA DEL BORDE, no pegada a el.
        //
        // Debajo ya esta el hueco de la barra de gestos, pero eso solo evita que
        // la raya caiga ENCIMA; seguia quedando en la franja donde el sistema se
        // queda los deslizamientos para ir a inicio, que es la peor zona posible
        // para algo que ahora se arrastra. Ademas, un control pegado al borde se
        // lee como "el final de la pantalla" y no como algo que se pueda tocar
        // — el mismo motivo por el que la barra de pestañas de la biblioteca
        // flota en vez de ir pegada.
        Box(Modifier.fillMaxWidth().padding(bottom = 18.dp)) {
            // El globo con el numero, solo mientras arrastras. Encima de la
            // barra y pegado a donde esta el dedo, no centrado: si no, con la
            // mano tapando el borde no se ve nada.
            arrastre?.let { p ->
                val fraccion = if (total <= 1) 0f else p.toFloat() / (total - 1)
                Box(
                    Modifier.align(Alignment.TopStart)
                        .offset { IntOffset((anchoBarra * fraccion).toInt(), -74) }
                        .padding(start = 2.dp)
                        .clip(FormaChapa).background(Acento)
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text("${p + 1}", style = Tipo.destacado, color = SobreAcento)
                }
            }

            Box(
                Modifier.fillMaxWidth()
                    // Alto de dedo, raya fina: 3 dp es imposible de acertar. La
                    // caja recoge el toque y dentro se pinta la raya de siempre.
                    .height(if (arrastre != null) 22.dp else 16.dp)
                    .onSizeChanged { anchoBarra = it.width }
                    .pointerInput(total) {
                        awaitEachGesture {
                            val abajo = awaitFirstDown(requireUnconsumed = false)
                            abajo.consume()
                            var donde = Salto.deBarra(abajo.position.x, anchoBarra, total)
                            arrastre = donde
                            while (true) {
                                val e = awaitPointerEvent()
                                val ch = e.changes.firstOrNull() ?: break
                                if (!ch.pressed) break
                                donde = Salto.deBarra(ch.position.x, anchoBarra, total)
                                arrastre = donde
                                ch.consume()
                            }
                            arrastre = null
                            onIr(donde)
                        }
                    },
                contentAlignment = Alignment.BottomCenter
            ) {
                LinearProgressIndicator(
                    progress = {
                        if (total <= 0) 0f else (marcada + 1).toFloat() / total
                    },
                    modifier = Modifier.fillMaxWidth()
                        .height(if (arrastre != null) 6.dp else 3.dp),
                    color = Acento, trackColor = Color(0x33FFFFFF)
                )
            }
        }
        // Mismo motivo que arriba pero por abajo: con las barras ocultas no hay
        // navigationBarsPadding que valga, y la raya de progreso caia justo
        // donde esta la barra de gestos.
        Spacer(Modifier.windowInsetsPadding(
            WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)))
    }

    salto?.let { escrito ->
        val destino = Salto.destino(escrito, total)
        AlertDialog(
            onDismissRequest = { salto = null },
            title = { Text("Ir a la página") },
            text = {
                Column {
                    Campo(escrito, "1 - $total", numerico = true) { salto = it }
                }
            },
            confirmButton = {
                // Apagado mientras lo escrito no valga: es la unica pista de
                // que 900 no existe en un comic de 22, y llega antes de pulsar.
                TextButton(
                    onClick = { destino?.let(onIr); salto = null },
                    enabled = destino != null
                ) { Text("Ir") }
            },
            dismissButton = {
                TextButton({ salto = null }) { Text("Cancelar") }
            }
        )
    }
}
