package com.dani.lector.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dani.lector.VistaModelo
// EL MES VISIBLE ES UN LocalDate DEL DIA 1, y no un YearMonth: kotlinx-datetime
// no tiene YearMonth, y una fecha al dia 1 hace exactamente lo mismo —se compara,
// se le suma y se le resta un mes— sin inventar un tipo propio.
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import com.dani.lector.datos.Rastro
import com.dani.lector.datos.*
import kotlinx.coroutines.launch

// ═══════════════════════════ BIBLIOTECA ═══════════════════════════

/**
 * Navegacion por carpetas: se muestra tu arbol tal cual.
 * Un nivel cada vez, asi abrir una carpeta es instantaneo.
 */
/**
 * Catalogo: cada carpeta es una fila horizontal de portadas, como en las apps
 * de streaming. Arriba, un banner grande con lo que estabas leyendo.
 *
 * Sigue siendo navegacion por carpetas pura: cada fila es una carpeta tuya y
 * tocando su titulo entras dentro.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PantallaCarpeta(
    vm: VistaModelo,
    docId: String?,
    ruta: String,
    titulo: String,
    onCarpeta: (String, String, String) -> Unit,
    onLeer: (Comic) -> Unit,
    onMenu: (Comic) -> Unit,
    onAtras: (() -> Unit)?
) {
    val estado by vm.estado.collectAsState()
    // La clave es SOLO docId, no el sello ni el catalogo.
    //
    // Antes llevaba el sello, y eso hacia dos cosas malas de golpe cada vez que
    // marcabas una pagina: vaciaba la pantalla —rueda de carga a pantalla
    // completa— y volvia a preguntarle a SAF por una carpeta que no habia
    // cambiado. Salias de un comic y la biblioteca se reconstruia entera.
    //
    // Ahora el efecto de abajo si escucha al catalogo, asi que cuando los
    // ficheros cambian de verdad se relee; pero al releer se SUSTITUYE lo que
    // hay, no se borra antes. Sin parpadeo y sin rueda.
    var contenido by remember(docId) { mutableStateOf<Escaner.Contenido?>(null) }
    // Sin el sello en el remember, por lo mismo que el contenido: el efecto lo
    // vuelve a pedir, pero mientras tanto se sigue viendo lo anterior. Con el
    // sello aqui, el banner de "seguir leyendo" desaparecia y volvia a salir
    // cada vez que marcabas una pagina, que es el parpadeo mas cantoso de todos
    // porque es la tarjeta grande de arriba.
    var seguir by remember { mutableStateOf<Comic?>(null) }
    // El ultimo terminado, el actual y el siguiente. Solo se pide en la raiz:
    // es la fila de "tu recorrido" y ahi es donde se pinta.
    var recorrido by remember { mutableStateOf<Triple<Comic?, Comic?, Comic?>?>(null) }

    // El buscador mira TODA la biblioteca, no solo esta carpeta: si sabes lo
    // que quieres, no tiene sentido obligarte a navegar hasta el.
    var busqueda by remember { mutableStateOf("") }
    var resultados by remember { mutableStateOf<List<Comic>>(emptyList()) }
    var enCurso by remember(docId) { mutableStateOf<List<Comic>>(emptyList()) }
    LaunchedEffect(estado.sello, estado.catalogo, docId) {
        enCurso = if (docId == null) vm.enCurso() else emptyList()
    }
    // BUSCAR AQUI O EN TODO. Empieza en "toda la biblioteca", que es lo que
    // hacia siempre y lo que se quiere el 90% de las veces; el acotado es para
    // cuando ya sabes en que serie estas y "01" te devuelve cuarenta.
    //
    // Se reinicia al cambiar de carpeta (la clave es docId), como el filtro de
    // la rejilla y por lo mismo: un ambito heredado de otra carpeta parece que
    // la app no encuentra cosas que si tiene.
    var soloAqui by remember(docId) { mutableStateOf(false) }
    LaunchedEffect(busqueda, soloAqui, ruta, estado.sello, estado.catalogo) {
        resultados = when {
            busqueda.isBlank() -> emptyList()
            // Con prefijo y no con igualdad: buscando dentro de "Batman" tienen
            // que salir tambien los de "Batman/Vol 3". Lo que se acota es el
            // subarbol, no el nivel exacto.
            soloAqui -> Busqueda.de(
                vm.todosLosComics().filter {
                    it.carpeta == ruta || it.carpeta.startsWith("$ruta/")
                }, busqueda)
            else -> Busqueda.de(vm.todosLosComics(), busqueda)
        }
    }

    // Apuntar la busqueda al parar de teclear, no en cada letra: escribiendo
    // "batman" pasarias por "ba", "bat", "batm"... y el historial se llenaria
    // de fragmentos. Como el efecto se cancela y rearranca con cada cambio de
    // texto, la espera solo llega al final si has dejado de escribir.
    //
    // La clave es SOLO busqueda, sin estado.sello: recordarBusqueda sube el
    // sello, y con el en la clave esto se rearrancaria a si mismo cada segundo
    // y medio para siempre.
    LaunchedEffect(busqueda) {
        if (busqueda.isBlank()) return@LaunchedEffect
        kotlinx.coroutines.delay(1200)
        if (resultados.isNotEmpty()) vm.recordarBusqueda(busqueda)
    }

    // AL VOLVER A LA APP SE RELEE ESTA CARPETA.
    //
    // Al partir el sello en dos, el contenido paso a releerse solo cuando la
    // app cambia ficheros ella misma. Pero los ficheros tambien cambian por
    // fuera: copias un numero al movil y la app no se entera (Dani, 26/08/2026,
    // añadiendo el #34 de una serie). Y el momento en que eso pasa es siempre
    // el mismo — mientras la app no esta delante—, asi que volver a ella es
    // justo cuando toca mirar.
    //
    // Se relee SOLO esta carpeta, que es una consulta. Tirar el indice entero
    // significaria recorrer el arbol completo cada vez que vuelves de mirar un
    // mensaje, y eso si se nota. Para el repaso a fondo esta el boton de
    // Ajustes.
    // La racha sale del progreso entero y cuesta un recorrido de un mapa
    // pequeño; se recalcula solo cuando cambia algo.
    val racha = remember(estado.sello) {
        Racha.de(vm.marcas.todas().values.map { it.cuando }, System.currentTimeMillis())
    }

    // AL CAMBIAR DE CARPETA, ARRIBA DEL TODO.
    //
    // El LazyColumn es el MISMO objeto al bajar de carpeta —solo cambia el
    // docId, no la pantalla— asi que conserva la posicion de scroll. Y la
    // cabecera de esta pantalla vive DENTRO de la lista, como un item mas, para
    // que se vaya al hacer scroll. Las dos cosas juntas dan el fallo que vio
    // Dani: bajas por la raiz, entras en una carpeta con menos contenido, y te
    // quedas por debajo del final. Pantalla negra y sin cabecera, porque la
    // cabecera tambien se ha quedado arriba.
    //
    // Se pierde volver a donde estabas al subir de carpeta, y se acepta:
    // recordar la posicion de cada nivel es un mapa mas que mantener, y llegar
    // arriba del todo es lo que hace cualquier explorador de ficheros.
    val estadoLista = rememberLazyListState()
    LaunchedEffect(docId) { estadoLista.scrollToItem(0) }

    val ctxRastro = LocalContext.current
    LaunchedEffect(docId) {
        Rastro.apunta(ctxRastro, "carpeta: «${ruta.ifBlank { "raíz" }}»")
    }

    val delante = enPrimerPlano()
    LaunchedEffect(docId, estado.catalogo, delante) {
        if (delante) {
            contenido = vm.abrirCarpeta(docId, ruta)
            Rastro.apunta(ctxRastro, "  leída: ${contenido?.carpetas?.size} carpetas, " +
                "${contenido?.comics?.size} cómics")
        }
    }

    // La pantalla entera se tiñe del color de lo que estas leyendo, no solo el
    // visor. Es lo que hace el video en su pantalla de inicio y lo que da la
    // sensacion de que la app "sabe" que tienes abierto.
    //
    // El degradado se apaga en el 38% de la altura: teñir la pantalla entera
    // pone de color hasta la barra de pestañas y deja de parecer un ambiente
    // para parecer un tema mal elegido.
    val ctxColor = LocalContext.current
    val ambiente by produceState<Color?>(null, seguir?.uri) {
        value = seguir?.uri?.let { ColorPortada.de(ctxColor, it) }
    }
    // Se pide SIEMPRE, no solo en la raiz. El banner grande sigue saliendo solo
    // en la raiz (lo comprueba mas abajo), pero la barra flotante de abajo esta
    // en todas las carpetas, y este mismo valor es el que decide cuanto hueco
    // hay que dejar al final de la lista. Si solo se pidiera en la raiz, al
    // bajar a una subcarpeta el ultimo comic se quedaria debajo de la barra.
    LaunchedEffect(estado.sello) { seguir = vm.seguirLeyendo() }
    LaunchedEffect(estado.sello, estado.catalogo, docId) {
        recorrido = if (docId == null) vm.recorrido() else null
    }

    // remember: mismo motivo que VELO_CARTA, y aqui encima el degradado cubre
    // la pantalla entera. Solo cambia cuando cambia el color de ambiente.
    val fondo = remember(ambiente) {
        Brush.verticalGradient(
            0f to ambienteFondo(ambiente).first(),
            0.38f to Tinta,
            1f to Tinta
        )
    }
    Column(Modifier.fillMaxSize().background(fondo)) {
        val c = contenido
        if (c == null) {
            Cabecera(titulo, ruta, onAtras)
            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }

        // Estas dos van aqui y no dentro del LazyColumn A PROPOSITO: el cuerpo
        // de un LazyColumn no es Composable y remember no se puede llamar ahi.
        // Sin remember, chunked monta una lista de listas ENTERA cada vez que la
        // pantalla se recompone —con quinientos comics dentro, ciento setenta
        // listas nuevas para tirar a la basura— y el filtro otra lista mas.
        // EL HUECO DEL FINAL DE LA LISTA, y hay que recalcularlo cada vez que
        // cambia lo que flota abajo. La pildora nueva ocupa mas que la barra
        // pegada de antes —lleva su propio margen— y con los 80 dp viejos la
        // ultima fila de portadas se quedaba media tapada.
        //
        // La barra de "seguir leyendo" solo esta DENTRO de una carpeta (ver
        // MainActivity), asi que en la raiz no hay que reservarle sitio.
        val hueco = if (docId != null && seguir != null) 152.dp else 88.dp

        // ── el filtro de la rejilla ──
        //
        // Se reinicia al cambiar de carpeta (la clave es docId): entrar en una
        // serie y encontrarla filtrada por lo que elegiste en OTRA es de las
        // cosas que mas desconciertan, porque parece que faltan cómics.
        var filtro by remember(docId) { mutableStateOf(Filtro.TODOS) }

        // La hoja de opciones de la carpeta: el orden y el marcar en bloque.
        var opciones by remember { mutableStateOf(false) }

        // Los recuentos y el filtrado, en UNA pasada y con remember: esto corre
        // por cada repintado de la pantalla y la carpeta puede traer sesenta
        // comics. El sello entra en la clave porque marcar una pagina cambia a
        // que grupo pertenece un comic.
        // EL ORDEN, ANTES DE AGRUPAR. groupBy respeta el orden de entrada, asi
        // que ordenando aqui salen ordenadas las cuatro listas —todos, leidos,
        // leyendo y sin leer— de una vez. El sello entra en la clave porque
        // cambiar el orden lo sube.
        val ordenados = remember(c.comics, estado.sello) {
            OrdenCarpeta.de(c.comics, vm.orden)
        }
        val porFiltro = remember(ordenados, estado.sello) {
            ordenados.groupBy { comic ->
                val m = vm.marcas.de(comic.uri)
                when {
                    m?.terminado == true -> Filtro.LEIDOS
                    m != null && m.pagina > 0 -> Filtro.LEYENDO
                    else -> Filtro.SIN_LEER
                }
            }
        }
        val visibles = remember(porFiltro, filtro, ordenados) {
            if (filtro == Filtro.TODOS) ordenados
            else porFiltro[filtro].orEmpty()
        }
        val filasDeComics = remember(visibles) { visibles.chunked(3) }
        val sinElDelBanner = remember(enCurso, seguir?.uri) {
            enCurso.filter { it.uri != seguir?.uri }
        }

        // Solo cuando esta carpeta ES una serie: comics dentro y ninguna
        // subcarpeta. En "DC Comics", que es un cajon con carpetas dentro,
        // hablar de huecos no significa nada.
        //
        // Los especiales quedan fuera del conteo A PROPOSITO: un annual o un
        // one-shot no lleva la numeracion de la serie, y meterlo abriria
        // huecos que no existen.
        val numerosMios = remember(c.comics) {
            if (c.carpetas.isNotEmpty()) emptyList()
            else c.comics.filter { !it.esEspecial }.mapNotNull { it.numero }
        }
        val estadoSerie = remember(numerosMios) {
            Huecos.de(numerosMios).takeIf { it.tienes > 0 }
        }

        LazyColumn(Modifier.weight(1f), state = estadoLista) {

            // En la raiz, la cabecera con saludo y racha; dentro de una
            // carpeta, la de siempre, que lleva el "Atras" y la ruta.
            item {
                if (docId == null) CabeceraInicio(titulo, racha)
                else Cabecera(titulo, ruta, onAtras, linea = false)
            }
            item { Buscador(busqueda, "Buscar en toda la biblioteca") { busqueda = it } }

            // Recientes en pastillas y en UNA fila, no en lista vertical como
            // el video: alli la busqueda tiene pantalla propia y aqui vive
            // encima de la biblioteca. Ocho entradas en vertical empujarian el
            // banner fuera de la pantalla cada vez que abres la app.
            item {
                val recientes = remember(estado.sello) { vm.recientes }
                if (busqueda.isBlank() && recientes.isNotEmpty()) {
                    LazyRow(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 6.dp)) {
                        item { Spacer(Modifier.width(16.dp)) }
                        items(recientes) { r ->
                            Row(
                                Modifier.padding(end = 8.dp)
                                    .clip(FormaChapa).background(PanelAlto)
                                    .clickableSimple { busqueda = r }
                                    .padding(start = 11.dp, end = 3.dp,
                                             top = 5.dp, bottom = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(r, style = Tipo.minuscula, color = Tenue, maxLines = 1)
                                Text("\u2715", fontSize = 11.sp, color = Apagado,
                                    modifier = Modifier.padding(horizontal = 7.dp)
                                        .clickableSimple { vm.olvidarBusqueda(r) })
                            }
                        }
                        item { Spacer(Modifier.width(16.dp)) }
                    }
                }
            }

            if (busqueda.isNotBlank()) {
                // El par de chips solo DENTRO de una carpeta: en la raiz,
                // "aquí" y "toda la biblioteca" son lo mismo y serian dos
                // botones para elegir entre una cosa y esa misma cosa.
                if (docId != null) item {
                    Row(Modifier.fillMaxWidth().padding(16.dp, 4.dp, 16.dp, 0.dp)) {
                        ChipAmbito("En «$titulo»", soloAqui) { soloAqui = true }
                        ChipAmbito("En todo", !soloAqui) { soloAqui = false }
                    }
                }
                item {
                    Text(
                        if (resultados.isEmpty()) "Nada que se llame así."
                        else "${resultados.size} resultados",
                        Modifier.padding(20.dp, 10.dp), style = Tipo.pie, color = Tenue
                    )
                }
                items(resultados) { encontrado -> FilaResultado(vm, encontrado, estado.sello, onLeer) }
                item { Spacer(Modifier.height(hueco)) }
                return@LazyColumn
            }

            // ── lo que estabas leyendo: tarjeta HORIZONTAL ──
            //
            // Era un banner de 300 dp con la portada de fondo y el texto
            // encima. Se comia media pantalla para decir tres cosas, y encima
            // el titulo sobre el dibujo se leia regular. En horizontal ocupa la
            // mitad, la portada se ve entera y cabe mas informacion legible.
            //
            // Y el nombre va SIN el prefijo de la carpeta, como en la rejilla:
            // arriba ya pone donde estas y "313 - Green Lantern Corps Recharge
            // #04" gastaba dos lineas en repetirlo.
            val s = seguir
            if (s != null && docId == null) item {
                val marca = vm.marcas.de(s.uri)
                val nombre = remember(s.uri) {
                    Parser.sinPrefijoDeCarpeta(
                        s.nombre.substringBeforeLast('.'),
                        s.carpeta.trimEnd('/').substringAfterLast('/')
                    )
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(FormaTarjeta).background(Panel).escaneo()
                        .clickable { onLeer(s) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.width(88.dp).height(132.dp).caratula()) {
                        Portada(s.uri, Modifier.fillMaxSize(),
                            { vm.portada(it) }, { vm.portadaYa(it) })
                    }
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text("SEGUIR LEYENDO", style = Tipo.minuscula, color = Acento,
                            letterSpacing = 0.5.sp)
                        Text(nombre, style = Tipo.destacado, color = Hueso, maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 6.dp))
                        Text(s.carpeta.trimEnd('/').substringAfterLast('/')
                                .ifBlank { "en tu biblioteca" },
                            style = Tipo.minuscula, color = Tenue, maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 3.dp))
                        marca?.let {
                            LinearProgressIndicator(
                                progress = { it.porcentaje / 100f },
                                modifier = Modifier.fillMaxWidth().height(3.dp)
                                    .padding(top = 12.dp).clip(RoundedCornerShape(2.dp)),
                                color = Acento, trackColor = PanelAlto
                            )
                            Text("${it.porcentaje}% · pág. ${it.pagina + 1} de ${it.paginas}",
                                style = Tipo.minuscula, color = Tenue,
                                modifier = Modifier.padding(top = 7.dp))
                        }
                    }
                }
            }

            // ── de donde vienes y a donde vas ──
            recorrido?.let { (ultimo, actual, siguiente) ->
                if (ultimo != null || siguiente != null) item {
                    Recorrido(vm, ultimo, actual, siguiente, onLeer)
                }
            }

            // ── lo que tienes a medias, sin el del banner ──
            // (repetir la misma portada justo debajo parece un fallo)
            val restantes = sinElDelBanner
            if (restantes.isNotEmpty()) {
                item { TituloFila("En curso") }
                item { FilaPortadas(vm, restantes, onLeer, onMenu) }
            }

            // ── una fila por carpeta ──
            items(c.carpetas) { carp ->
                FilaCarpeta(vm, carp, onCarpeta, onLeer, onMenu)
            }

            // ── los comics de esta carpeta: rejilla, no carrusel ──
            if (c.comics.isNotEmpty()) {
                // El rotulo de la derecha dice como estan ordenados AHORA, y
                // la fila entera abre las opciones. Dos cosas en un sitio: se
                // ve el estado sin abrir nada, y el chevron invita a tocar.
                item {
                    TituloFila(
                        if (c.carpetas.isEmpty()) "Cómics" else "Sueltos aquí",
                        detalle = vm.orden.rotulo
                    ) { opciones = true }
                }

                // "SIGUE POR EL #7". Solo dentro de una carpeta: en la raiz eso
                // ya lo contesta la tarjeta de "seguir leyendo", que ademas
                // habla de toda la biblioteca y no de un sitio.
                if (docId != null) item {
                    val proximo = remember(c.comics, estado.sello) {
                        vm.siguienteSinLeer(c.comics)
                    }
                    if (proximo != null) FilaSeguirSerie(vm, proximo, onLeer)
                }
                if (estadoSerie != null) item {
                    TiraSerie(vm, ruta, titulo, estadoSerie, numerosMios, estado.sello)
                }
                // Los chips SOLO si hay algo que separar. Con todo sin leer, un
                // filtro es un control que no hace nada y encima ocupa una fila.
                if (porFiltro.size > 1) item {
                    ChipsFiltro(c.comics.size, porFiltro, filtro) { filtro = it }
                }
                if (filasDeComics.isEmpty()) item {
                    Text("Nada aquí con ese filtro.", Modifier.padding(20.dp, 8.dp),
                        style = Tipo.pie, color = Tenue)
                }
                // de tres en tres para poder usar LazyColumn sin anidar scrolls
                items(filasDeComics) { fila ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
                        fila.forEach { comic ->
                            Box(Modifier.weight(1f)) {
                                TarjetaComic(vm, comic, titulo, estado.sello, onLeer, onMenu)
                            }
                        }
                        // huecos para que la ultima fila no se estire
                        repeat(3 - fila.size) { Box(Modifier.weight(1f)) {} }
                    }
                }
            }

            if (c.carpetas.isEmpty() && c.comics.isEmpty()) item {
                Text("Esta carpeta está vacía.", Modifier.padding(20.dp), color = Tenue)
            }
            // El hueco de abajo cuenta con la barra de pestañas Y con la de
            // "seguir leyendo" cuando esta: sin esto, el ultimo comic de la
            // carpeta se quedaba debajo y no habia forma de llegar a el.
            item { Spacer(Modifier.height(hueco)) }
        }

        if (opciones) MenuCarpeta(
            vm = vm,
            comics = c.comics,
            leidos = porFiltro[Filtro.LEIDOS]?.size ?: 0
        ) { opciones = false }
    }
}

/**
 * Las opciones de una carpeta: como se ordena y marcar de golpe.
 *
 * LAS DOS EN LA MISMA HOJA porque las dos van de "esta carpeta entera", que es
 * lo que las distingue del menu de un comic suelto. Meter el orden en Ajustes
 * lo habria dejado a tres pantallas de donde se usa.
 */
@Composable
private fun MenuCarpeta(
    vm: VistaModelo,
    comics: List<Comic>,
    leidos: Int,
    onCerrar: () -> Unit
) {
    val estadoHoja = rememberModalBottomSheetState()
    // null = sin preguntar; true/false = esperando confirmacion de marcar o de
    // quitar. Una variable y no dos: no se pueden estar preguntando las dos.
    var confirmar by remember { mutableStateOf<Boolean?>(null) }

    ModalBottomSheet(onDismissRequest = onCerrar, sheetState = estadoHoja,
        containerColor = Panel) {
        Column(Modifier.padding(bottom = 28.dp)) {

            Column(Modifier.padding(20.dp, 4.dp, 20.dp, 14.dp)) {
                Text("Ordenar", style = Tipo.cuerpo, color = Hueso,
                    modifier = Modifier.padding(bottom = 10.dp))
                Segmentado(
                    Orden.entries.map { it.rotulo },
                    vm.orden.ordinal
                ) { i -> vm.orden = Orden.entries[i] }
            }

            Box(Modifier.padding(start = 20.dp).fillMaxWidth()
                .height(0.5.dp).background(Linea))

            // Solo se ofrece lo que cambia algo: en una carpeta ya entera leida,
            // "marcar todos como leidos" es un boton que no hace nada.
            if (leidos < comics.size) OpcionMenu(
                "Marcar todos como leídos",
                "${comics.size - leidos} sin leer"
            ) { confirmar = true }

            if (leidos > 0) OpcionMenu(
                "Quitar el leído de todos",
                "$leidos marcados"
            ) { confirmar = false }
        }
    }

    confirmar?.let { leido ->
        AlertDialog(
            onDismissRequest = { confirmar = null },
            title = { Text(if (leido) "Marcar ${comics.size} cómics" else "Quitar el leído") },
            // LA CONSECUENCIA, NO LA PREGUNTA. Lo que hay que decir aqui es que
            // se pierde por donde ibas, que es lo unico que no se puede
            // deshacer volviendo a pulsar.
            text = {
                Text(
                    if (leido) "Los que tengas a medias perderán la página por la que ibas."
                    else "Se borra por dónde ibas en todos los de esta carpeta."
                )
            },
            confirmButton = {
                TextButton({
                    vm.marcarCarpeta(comics, leido)
                    confirmar = null
                    onCerrar()
                }) { Text(if (leido) "Marcar" else "Quitar") }
            },
            dismissButton = {
                TextButton({ confirmar = null }) { Text("Cancelar") }
            }
        )
    }
}

/** Un comic encontrado por el buscador: portada pequeña, nombre y donde vive. */
@Composable
private fun FilaResultado(vm: VistaModelo, comic: Comic, sello: Int, onLeer: (Comic) -> Unit) {
    // El sello viene de fuera A PROPOSITO. Antes cada fila hacia su propio
    // collectAsState del estado del ViewModel: eso es una suscripcion por fila
    // que se crea y se tira en cada scroll, y ademas repinta TODAS las filas
    // cada vez que cambia cualquier cosa del estado. La pantalla ya lo recoge
    // una vez arriba; aqui solo hace falta el numero para saber si releer.
    val marca = remember(comic.uri, sello) { vm.marcas.de(comic.uri) }

    Row(
        Modifier.fillMaxWidth().clickable { onLeer(comic) }.padding(20.dp, 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(40.dp).height(60.dp).caratula(FormaChapa).background(Panel)) {
            Portada(comic.uri, Modifier.fillMaxSize(), { vm.portada(it) }, { vm.portadaYa(it) })
        }
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text(comic.nombre.substringBeforeLast('.'), style = Tipo.cuerpo, maxLines = 2,
                color = if (marca?.terminado == true) Apagado else Hueso)
            // la carpeta importa: dos ficheros se llaman igual en dos volumenes
            Text(comic.carpeta.ifBlank { "raíz" }, style = Tipo.pie, color = Tenue,
                maxLines = 1, modifier = Modifier.padding(top = 2.dp))
        }
        if (marca?.terminado == true) Text("\u2713", fontSize = 15.sp,
            color = Cian, modifier = Modifier.padding(start = 8.dp))
    }
    Box(Modifier.padding(start = 20.dp).fillMaxWidth().height(0.5.dp).background(Linea))
}

/** Una carpeta como fila: su nombre y una tira de portadas de lo que hay dentro. */
@Composable
private fun FilaCarpeta(
    vm: VistaModelo,
    carp: Carpeta,
    onCarpeta: (String, String, String) -> Unit,
    onLeer: (Comic) -> Unit,
    onMenu: (Comic) -> Unit
) {
    val estado by vm.estado.collectAsState()
    // Igual que arriba: el sello NO va en el remember, solo en el efecto. Esta
    // fila si tiene que reaccionar al progreso —la portada que enseña es la del
    // numero por el que vas— pero calcularla sale del indice que ya esta en
    // memoria, no del disco. Lo caro no era recalcular: era la rueda de carga
    // que aparecia en cada fila al vaciar la lista antes de rehacerla.
    var portadas by remember(carp.docId) { mutableStateOf<List<Comic>?>(null) }
    LaunchedEffect(carp.docId, estado.sello, estado.catalogo) {
        portadas = vm.portadasDe(carp.ruta)
    }

    TituloFila(carp.nombre, descripcion(carp)) {
        onCarpeta(carp.docId, carp.ruta, carp.nombre)
    }

    val p = portadas
    if (p == null) {
        Box(Modifier.fillMaxWidth().height(150.dp), Alignment.CenterStart) {
            CircularProgressIndicator(Modifier.padding(start = 20.dp).size(22.dp))
        }
    } else if (p.isEmpty()) {
        Text("nada dentro", fontSize = 12.sp, color = Apagado,
            modifier = Modifier.padding(20.dp, 0.dp, 0.dp, 12.dp))
    } else {
        FilaPortadas(vm, p, onLeer, onMenu, rutaBase = carp.ruta)
    }
}

/** Tira horizontal de portadas. */
@Composable
private fun FilaPortadas(
    vm: VistaModelo, comics: List<Comic>,
    onLeer: (Comic) -> Unit, onMenu: (Comic) -> Unit,
    // Va el ULTIMO y con valor por defecto para no tocar las llamadas que ya
    // habia. La fila de "En curso" no lo pasa: alli cada carta es un comic
    // suelto que estas leyendo, no el representante de una serie.
    rutaBase: String? = null
) {
    val estado by vm.estado.collectAsState()
    LazyRow(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        item { Spacer(Modifier.width(16.dp)) }
        items(comics) { comic ->
            val marca = remember(comic.uri, estado.sello) { vm.marcas.de(comic.uri) }
            // El nombre de la SERIE, que es la carpeta que contiene el comic:
            // el ULTIMO tramo de la ruta, no el primero. Debajo de "DC Comics"
            // interesa leer "Green Lantern Vol. 4", no "Green lantern".
            //
            // Los comics sueltos de la propia carpeta de la fila no llevan
            // etiqueta: no son una serie y ponerles el nombre de la carpeta en
            // la que ya estas no dice nada.
            val etiqueta = remember(comic.uri, rutaBase) {
                rutaBase?.let { base ->
                    if (comic.carpeta.trim('/') == base.trim('/')) null
                    else comic.carpeta.trimEnd('/').substringAfterLast('/').ifBlank { null }
                }
            }
            Box(
                Modifier.padding(4.dp).width(104.dp).height(156.dp)
                    .caratula()
                    .combinedClickable(
                        onClick = { onLeer(comic) },
                        onLongClick = { onMenu(comic) }
                    )
            ) {
                Portada(comic.uri, Modifier.fillMaxSize(), { vm.portada(it) }, { vm.portadaYa(it) },
                    vacio = { MotivoSinPortada(comic.uri) }
                ) {
                    if (etiqueta != null) {
                        Box(Modifier.matchParentSize().background(
                            Brush.verticalGradient(
                                0.45f to Color.Transparent,
                                1f to Tinta.copy(alpha = 0.9f)
                            )
                        ))
                        // Dos lineas, no tres: sobre una carta de 104 dp, tres
                        // lineas de titulo tapan medio dibujo y la fila entera
                        // pasa a leerse como un bloque de texto.
                        Text(etiqueta, style = Tipo.minuscula, maxLines = 2,
                            overflow = TextOverflow.Ellipsis, color = Hueso,
                            modifier = Modifier.align(Alignment.BottomStart)
                                .padding(start = 7.dp, end = 7.dp, bottom = 7.dp))
                    }

                    if (marca != null && marca.terminado != true) Box(
                        Modifier.align(Alignment.BottomStart).height(3.dp)
                            .fillMaxWidth(marca.porcentaje / 100f).background(Acento))

                    if (marca?.terminado == true) ChapaLeido(Modifier.align(Alignment.TopStart))
                    comic.numero?.let { n ->
                        Box(Modifier.align(Alignment.TopEnd).padding(6.dp)
                            .clip(FormaChapa)
                            .background(Color(0x99000000))
                            .padding(horizontal = 6.dp, vertical = 2.dp)) {
                            Text("$n", style = Tipo.minuscula, color = Hueso,
                                fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.width(16.dp)) }
    }
}

/**
 * El velo del pie de la carta, montado UNA vez para todas.
 *
 * Iba dentro del Composable, o sea un Brush nuevo por carta y por repintado. Y
 * un Brush no es una descripcion inerte: al pintarlo se le pide su shader y el
 * shader se guarda dentro del objeto, asi que un objeto nuevo cada vez es un
 * shader nuevo cada vez. Las paradas son fracciones, no pixeles, asi que este
 * degradado vale igual para cualquier tamaño de carta y puede vivir aqui.
 */
private val VELO_CARTA = Brush.verticalGradient(
    0.42f to Color.Transparent,
    1f to Tinta.copy(alpha = 0.88f)
)

/**
 * De donde vienes y a donde vas: el ultimo, el actual y el siguiente.
 *
 * REPITE A PROPOSITO el comic de la tarjeta de arriba, y esta vez la
 * duplicacion se queda. Son dos cosas distintas: la tarjeta es la ACCION —tiene
 * el progreso y es lo que tocas para seguir— y esto es la SECUENCIA, que solo
 * se entiende si el del medio esta. Sin el, las otras dos portadas no dicen
 * respecto a que son anterior y siguiente.
 *
 * (No es el caso de la barra flotante de abajo, que decia exactamente lo mismo
 * que la tarjeta y por eso se quito de la raiz.)
 */
@Composable
private fun Recorrido(
    vm: VistaModelo,
    ultimo: Comic?,
    actual: Comic?,
    siguiente: Comic?,
    onLeer: (Comic) -> Unit
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(FormaTarjeta).background(Panel).padding(vertical = 14.dp)
    ) {
        Text("TU RECORRIDO", style = Tipo.minuscula, color = Acento, letterSpacing = 0.5.sp,
            modifier = Modifier.padding(start = 16.dp, bottom = 12.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            Paso("ÚLTIMO", ultimo, vm, onLeer, Modifier.weight(1f))
            Paso("ACTUAL", actual, vm, onLeer, Modifier.weight(1f))
            Paso("SIGUIENTE", siguiente, vm, onLeer, Modifier.weight(1f))
        }
    }
}

/** Una columna del recorrido. Sin comic, un hueco: no se rellena con nada. */
@Composable
private fun Paso(
    rotulo: String,
    comic: Comic?,
    vm: VistaModelo,
    onLeer: (Comic) -> Unit,
    modifier: Modifier = Modifier
) {
    // El titulo se calcula SIEMPRE, tambien cuando no hay comic. Meter el
    // remember dentro de un ?.let lo convierte en una llamada condicional, y
    // eso en Compose es como se corrompe el estado recordado cuando el valor
    // pasa de null a no null.
    val titulo = remember(comic?.uri) {
        comic?.let {
            Parser.sinPrefijoDeCarpeta(
                it.nombre.substringBeforeLast('.'),
                it.carpeta.trimEnd('/').substringAfterLast('/')
            )
        } ?: "\u2014"
    }

    Column(
        modifier.padding(horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(rotulo, style = Tipo.minuscula, color = Tenue, letterSpacing = 0.5.sp)
        Box(
            Modifier.padding(top = 8.dp).fillMaxWidth().aspectRatio(0.66f)
                .then(if (comic != null) Modifier.caratula().clickable { onLeer(comic) }
                      else Modifier.clip(FormaCaratula).background(PanelAlto)),
            contentAlignment = Alignment.Center
        ) {
            if (comic != null)
                Portada(comic.uri, Modifier.fillMaxSize(), { vm.portada(it) }, { vm.portadaYa(it) })
            else
                Text("\u2014", style = Tipo.destacado, color = Apagado)
        }
        Text(
            titulo,
            style = Tipo.minuscula, color = if (comic != null) Hueso else Apagado,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 7.dp)
        )
    }
}

/** Los grupos de la rejilla. TODOS no es un grupo: es "no filtres". */
private enum class Filtro(val rotulo: String) {
    TODOS("Todos"), SIN_LEER("Sin leer"), LEYENDO("Leyendo"), LEIDOS("Leídos")
}

/**
 * Los chips de filtro de la rejilla.
 *
 * CON EL RECUENTO DENTRO, y eso es la mitad de para lo que sirven: "Sin leer 12"
 * contesta la pregunta sin tener que pulsarlo. Un chip que solo pone "Sin leer"
 * te obliga a tocarlo para saber si merece la pena tocarlo.
 *
 * Y LOS GRUPOS VACIOS NO SALEN. Un "Leídos 0" es un botón que no lleva a ningún
 * sitio, y ocupa lo mismo que uno que sí.
 */
@Composable
private fun ChipsFiltro(
    total: Int,
    porFiltro: Map<Filtro, List<Comic>>,
    elegido: Filtro,
    onElegir: (Filtro) -> Unit
) {
    LazyRow(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        item { Spacer(Modifier.width(16.dp)) }
        items(Filtro.entries.toList()) { f ->
            val cuantos = if (f == Filtro.TODOS) total else porFiltro[f]?.size ?: 0
            if (cuantos > 0) {
                val marcado = f == elegido
                Row(
                    Modifier.padding(end = 8.dp)
                        .clip(FormaChapa)
                        .background(if (marcado) Acento else PanelAlto)
                        .clickableSimple { onElegir(f) }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(f.rotulo, style = Tipo.minuscula,
                        color = if (marcado) SobreAcento else Tenue)
                    Text("$cuantos", style = Tipo.minuscula,
                        color = if (marcado) SobreAcento else Apagado,
                        modifier = Modifier.padding(start = 7.dp))
                }
            }
        }
        item { Spacer(Modifier.width(16.dp)) }
    }
}

/**
 * "Sigue por el #7": el boton que abre por donde ibas de esta carpeta.
 *
 * DICE CUAL ES, no "continuar". Un boton que pone "continuar" obliga a pulsarlo
 * para saber que hace; poniendo el numero, la mitad de las veces ya no hace
 * falta pulsarlo — solo querias saber por donde ibas.
 *
 * Y si lo tienes a medias lo dice debajo, porque cambia lo que va a pasar: no
 * abre por la primera pagina sino por la 12.
 */
@Composable
private fun FilaSeguirSerie(vm: VistaModelo, comic: Comic, onLeer: (Comic) -> Unit) {
    val marca = vm.marcas.de(comic.uri)
    // Sin numero —un annual, un one-shot— se dice el nombre, que es lo unico
    // que lo identifica. Con el prefijo de la carpeta fuera, como en la rejilla.
    val rotulo = remember(comic.uri) {
        comic.numero?.let { "Sigue por el #$it" }
            ?: "Sigue por «${Parser.sinPrefijoDeCarpeta(
                comic.nombre.substringBeforeLast('.'),
                comic.carpeta.trimEnd('/').substringAfterLast('/'))}»"
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)
            .clip(FormaChapa)
            .background(PanelAlto)
            .border(FiloAncho, FiloColor, FormaChapa)
            .clickableSimple { onLeer(comic) }
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("\u25b6", fontSize = 13.sp, color = Acento,
            modifier = Modifier.padding(end = 11.dp))
        Column(Modifier.weight(1f)) {
            Text(rotulo, style = Tipo.cuerpo, color = Hueso,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (marca != null && !marca.terminado && marca.pagina > 0)
                Text("por la página ${marca.pagina + 1} de ${marca.paginas}",
                    style = Tipo.pie, color = Tenue,
                    modifier = Modifier.padding(top = 2.dp))
        }
        Text("\u203a", fontSize = 20.sp, color = Apagado)
    }
}

/**
 * Chip de donde se busca. Mismo aspecto que los del filtro de la rejilla, que es
 * lo que hace que se entienda sin explicar nada: en esta app, una chapa
 * amarilla es "lo que esta puesto ahora".
 */
@Composable
private fun ChipAmbito(texto: String, marcado: Boolean, onElegir: () -> Unit) {
    Text(
        texto,
        style = Tipo.minuscula,
        color = if (marcado) SobreAcento else Tenue,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(end = 8.dp)
            .clip(FormaChapa)
            .background(if (marcado) Acento else PanelAlto)
            .clickableSimple(accion = onElegir)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    )
}

/**
 * La chapa de "leido" en la esquina de una portada.
 *
 * Cian y no verde: la paleta esta cerrada y aqui Cian ya es el color de lo que
 * esta bien y no pide nada. El tick va en Tinta —negro sobre color, como el
 * amarillo del acento— porque sobre un cian claro el blanco no se lee.
 *
 * Arriba a la IZQUIERDA para no chocar con la chapa del numero, que va a la
 * derecha y es la otra cosa que se mira de un vistazo en la rejilla.
 */
@Composable
private fun ChapaLeido(modifier: Modifier = Modifier) {
    Box(
        modifier.padding(6.dp).size(18.dp).clip(CircleShape).background(Cian),
        contentAlignment = Alignment.Center
    ) {
        Text("\u2713", fontSize = 11.sp, color = Tinta, fontWeight = FontWeight.Bold)
    }
}

/**
 * Lo que tienes de esta serie y lo que le falta.
 *
 * Dice SOLO lo que se sabe seguro, que es lo que hay entre el primero y el
 * ultimo que tienes (ver [Huecos]). Lo de si la serie sigue mas alla del ultimo
 * o empieza antes del primero no se sabe sin preguntar fuera, asi que aqui no
 * se insinua siquiera: se dice por donde va lo que tienes y ya.
 */
@Composable
private fun TiraSerie(
    vm: VistaModelo,
    ruta: String,
    titulo: String,
    local: Huecos.Estado,
    mios: List<Int>,
    sello: Int
) {
    val estado by vm.estado.collectAsState()
    var aviso by remember { mutableStateOf("") }
    var desvincular by remember { mutableStateOf(false) }
    val remoto = remember(ruta, sello, mios) { vm.estadoRemoto(ruta, mios) }

    Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp)) {
        if (remoto == null) {
            // Sin comprobar solo se sabe lo de en medio: los huecos entre el
            // primero y el ultimo que tienes. Donde acaba la serie hay que
            // preguntarlo fuera, y eso es lo que ofrece el enlace de abajo.
            Text(
                buildString {
                    append("${local.tienes} número${if (local.tienes > 1) "s" else ""}")
                    if (local.primero != null && local.ultimo != null &&
                        local.primero != local.ultimo)
                        append(", del #${local.primero} al #${local.ultimo}")
                },
                style = Tipo.pie, color = Tenue
            )
            // Sin huecos NO se dice nada. "Sin huecos entre el primero y el
            // ultimo que tienes" era una linea entera para decir que todo va
            // bien, y lo que va bien no necesita anunciarse.
            val falta = Huecos.texto(local)
            if (falta.isNotBlank()) Text(
                "Te faltan ${local.cuantosFaltan}: $falta",
                style = Tipo.pie, color = Acento,
                modifier = Modifier.padding(top = 2.dp)
            )
            Text(
                if (estado.cargando) estado.progreso.ifBlank { "Buscando..." }
                else "Comprobar en Comic Vine ›",
                style = Tipo.pie, color = if (estado.cargando) Tenue else Cian,
                modifier = Modifier.padding(top = 8.dp)
                    .clickableSimple(enabled = !estado.cargando) {
                        vm.comprobarSerie(ruta, titulo, null) { aviso = it }
                    }
            )
        } else {
            val (ficha, r) = remoto
            // TRES LINEAS COMO MUCHO, y cada una contesta una pregunta
            // distinta: que tengo, que viene, y de donde sale esto. Antes eran
            // seis y las tres de abajo eran explicaciones de como funciona la
            // app, que es justo lo que nadie vuelve a leer despues del primer
            // dia. Ver LECTOR-COMICS-DISENO.md, "las reglas del texto".
            Text(
                when {
                    r.completa && r.enEmision -> "Al día · los ${r.total} que hay"
                    r.completa -> "Completa · ${r.total} números"
                    else -> "${r.tienes} de ${r.total} · te faltan ${EstadoSerie.texto(r)}"
                },
                style = Tipo.pie,
                color = if (r.completa) Cian else Acento
            )

            // hoy() dentro de un remember: se pinta por fila, y sin el
            // LocalDate.now se rehacia en cada recomposicion de cada una.
            val hoy = remember { Novedades.hoy() }
            val proximo = remember(ficha.numeros) { Novedades.proximo(ficha.numeros, hoy) }
            if (r.enEmision) Text(
                proximo?.let { Novedades.fraseProximo(it, hoy) } ?: "En emisión",
                style = Tipo.minuscula, color = Tenue,
                modifier = Modifier.padding(top = 2.dp)
            )

            // La accion y la procedencia comparten linea: son las dos cosas
            // pequeñas que quedan y apiladas ocupaban el doble.
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SeguirSerie(vm, ficha)
                Text(" · ", style = Tipo.minuscula, color = Apagado)
                // ANTES PONIA "no es esta" PEGADO AL NOMBRE, y se leia como el
                // final de la frase: la app parecia estar llevandote la
                // contraria. Ahora es solo el nombre, en gris flojo, y al
                // tocarlo pregunta. Un texto que es un boton no puede estar
                // escrito como si fuera prosa.
                Text(
                    "${ficha.nombre}${ficha.anio?.let { " ($it)" } ?: ""}",
                    style = Tipo.minuscula, color = Apagado,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                        .clickableSimple { desvincular = true }
                )
            }
        }
    }

    if (desvincular) AlertDialog(
        onDismissRequest = { desvincular = false },
        confirmButton = {
            TextButton({ vm.olvidarSerie(ruta); desvincular = false }) {
                Text("Deshacer")
            }
        },
        dismissButton = { TextButton({ desvincular = false }) { Text("Cancelar") } },
        title = { Text("¿No es esta serie?") },
        text = { Text("Se borra el vínculo y podrás volver a buscarla.") }
    )

    if (aviso.isNotBlank()) AlertDialog(
        onDismissRequest = { aviso = "" },
        confirmButton = { TextButton({ aviso = "" }) { Text("Vale") } },
        title = { Text(titulo) },
        text = { Text(aviso) }
    )
}

/**
 * "Seguir" / "La sigues", y el permiso de notificaciones.
 *
 * EL PERMISO SE PIDE AQUI Y NO AL ARRANCAR. Desde Android 13 se pide en marcha
 * y el sistema solo deja preguntar una o dos veces: gastar la pregunta en el
 * primer arranque, cuando el usuario todavia no sabe para que las quiere la
 * app, es como se consigue un "no" permanente. Aqui acaba de pulsar "seguir".
 *
 * Y SEGUIR LA SERIE FUNCIONA IGUAL AUNQUE DIGA QUE NO: seguirla es una decision
 * tuya que se guarda, notificar es un permiso del sistema. Si lo deniega se
 * sigue guardando y comprobando, y ENTONCES si se dice —porque eso ya es algo
 * que va mal— en vez de dejar un interruptor que parece encendido y no hace
 * nada.
 */
@Composable
private fun SeguirSerie(vm: VistaModelo, ficha: Ficha) {
    val ctx = LocalContext.current
    var sinPermiso by remember { mutableStateOf(false) }

    val pedirPermiso = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concedido -> sinPermiso = !concedido }

    Text(
        if (sinPermiso) "Sin permiso para avisarte"
        else if (ficha.seguida) "\u2713 La sigues" else "Seguir",
        style = Tipo.minuscula,
        color = when {
            sinPermiso -> Alarma
            ficha.seguida -> Cian
            else -> Acento
        },
        modifier = Modifier.clickableSimple {
            val seguir = !ficha.seguida
            // Primero se guarda la decision y luego se pide el permiso: al
            // reves, el dialogo del sistema dejaria el boton a medias mientras
            // el usuario decide, y si lo cancela se queda sin seguir la serie
            // sin saber por que.
            vm.seguirSerie(ficha.ruta, seguir)
            sinPermiso = false
            if (seguir && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val ok = ContextCompat.checkSelfPermission(
                    ctx, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!ok) pedirPermiso.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    )
}

/**
 * Un comic en la rejilla: portada con el nombre ENCIMA.
 *
 * Antes el nombre iba debajo, en texto suelto sobre el fondo. Ponerlo sobre el
 * arte es lo que mas separa un catalogo con aspecto de app de musica de una
 * rejilla de miniaturas con pie de foto, y ademas gana sitio: la carta ocupa lo
 * mismo y el nombre no roba dos lineas por debajo.
 *
 * El velo es un degradado que empieza a media carta, no un rectangulo opaco:
 * asi el dibujo se sigue viendo entero y el texto tiene contra que leerse. Y va
 * de Tinta, no de negro puro, para que en 2077 herede el negro calido en vez de
 * meter un gris azulado que ahi canta.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TarjetaComic(
    vm: VistaModelo, comic: Comic, carpeta: String, sello: Int,
    onLeer: (Comic) -> Unit, onMenu: (Comic) -> Unit
) {
    // El sello entra por parametro y no se recoge aqui: ver FilaResultado. En
    // la rejilla se nota mas todavia, que son tres cartas por fila.
    val marca = remember(comic.uri, sello) { vm.marcas.de(comic.uri) }

    Column(
        Modifier.padding(4.dp).combinedClickable(
            onClick = { onLeer(comic) },
            onLongClick = { onMenu(comic) }
        )
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.66f).caratula()) {
            Portada(comic.uri, Modifier.fillMaxSize(), { vm.portada(it) }, { vm.portadaYa(it) },
                vacio = { MotivoSinPortada(comic.uri) }
            ) {
                // El orden de pintado importa: primero el velo, y el texto y
                // las marcas al final para que no se los coma nada.
                //
                // ANTES LO LEIDO SE OSCURECIA AL 70%, y con la rejilla llena de
                // cómics leidos la pantalla entera se apagaba: el catalogo
                // dejaba de parecer un catalogo. Ahora la portada se ve igual
                // de bien y lo dice la chapa de abajo, que ademas se lee de un
                // vistazo en vez de haber que comparar brillos entre cartas.
                Box(Modifier.matchParentSize().background(VELO_CARTA))

                // Sin el trozo que ya dice la carpeta: dentro de "Green Lantern
                // Vol. 4", las sesenta cartas ponian "Green Lantern Vol4 #NN" y
                // gastaban tres lineas en repetir donde estas.
                // remember: el titulo no cambia mientras no cambien el comic ni
                // la carpeta, y esto se pinta por cada carta de la rejilla.
                val titulo = remember(comic.uri, carpeta) {
                    Parser.sinPrefijoDeCarpeta(comic.nombre.substringBeforeLast('.'), carpeta)
                }
                Text(
                    titulo,
                    style = Tipo.minuscula, maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    color = if (marca?.terminado == true) Apagado else Hueso,
                    modifier = Modifier.align(Alignment.BottomStart)
                        .padding(start = 8.dp, end = 8.dp, bottom = 8.dp))

                if (marca?.terminado == true) ChapaLeido(Modifier.align(Alignment.TopStart))

                // La raya de progreso al filo de abajo, por debajo del nombre.
                if (marca?.terminado != true && marca != null) Box(
                    Modifier.align(Alignment.BottomStart).height(3.dp)
                        .fillMaxWidth(marca.porcentaje / 100f).background(Acento))

                comic.numero?.let { n ->
                    Box(Modifier.align(Alignment.TopEnd).padding(6.dp)
                        .clip(FormaChapa)
                        .background(Color(0x99000000))
                        .padding(horizontal = 6.dp, vertical = 2.dp)) {
                        Text("$n", style = Tipo.minuscula, color = Hueso,
                            fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

/**
 * Lo que se pinta en una carta cuya portada no se ha podido sacar.
 *
 * Antes se quedaba negra y muda. Con cuarenta numeros de una serie en RAR5 no
 * habia forma de saber cuales fallaban sin abrirlos uno por uno, y el mensaje
 * bueno —el que dice que es RAR5 y que hay que convertirlo— solo salia al
 * abrir. Es la trampa de siempre: un fallo que se calla parece un fallo de la
 * app, no del fichero.
 */
@Composable
private fun MotivoSinPortada(uri: String) {
    Box(Modifier.fillMaxSize().padding(6.dp), Alignment.Center) {
        Text(
            Miniaturas.motivo(uri) ?: "sin portada",
            style = Tipo.minuscula, color = Apagado,
            textAlign = TextAlign.Center, maxLines = 4,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun descripcion(c: Carpeta): String = buildList {
    if (c.subcarpetas > 0) add("${c.subcarpetas} carpetas")
    if (c.comics > 0) add("${c.comics} cómics")
}.joinToString(" · ").ifBlank { "vacía" }

// ═══════════════════════════ EL TODO ═══════════════════════════

/**
 * Los marcapaginas de todos los comics.
 *
 * Hay que resolver la uri de cada marcador contra la biblioteca para poder
 * abrirlo, y eso obliga a recorrer el arbol una vez al entrar. Los que ya no
 * esten —porque hayas movido o borrado el fichero— salen igual, en gris y sin
 * poder abrirse, en vez de desaparecer sin explicacion.
 */
@Composable
fun PantallaMarcadores(
    vm: VistaModelo,
    onLeer: (Comic, Int) -> Unit,
    onAtras: () -> Unit
) {
    val estado by vm.estado.collectAsState()
    val puntos = remember(estado.sello) { vm.marcadores.todos() }
    var comics by remember { mutableStateOf<Map<String, Comic>?>(null) }
    LaunchedEffect(estado.sello) {
        comics = vm.todosLosComics().associateBy { it.uri }
    }

    Column(Modifier.fillMaxSize().background(Tinta).navigationBarsPadding()) {
        Cabecera("Marcapáginas", "${puntos.size} guardados", onAtras)

        if (puntos.isEmpty()) {
            Text("Ninguno todavía.\n\nMientras lees, toca el centro y dale a la " +
                 "estrella.",
                Modifier.padding(20.dp), style = Tipo.secundario, color = Tenue)
            return@Column
        }

        val mapa = comics
        LazyColumn(Modifier.weight(1f)) {
            items(puntos) { p ->
                val comic = mapa?.get(p.uri)
                Row(
                    Modifier.fillMaxWidth()
                        .clickable(enabled = comic != null) {
                            comic?.let { onLeer(it, p.pagina) }
                        }
                        .padding(20.dp, 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.width(44.dp).height(66.dp).caratula(FormaChapa)
                        .background(Panel)) {
                        if (comic != null) Portada(comic.uri, Modifier.fillMaxSize(),
                            { vm.portada(it) }, { vm.portadaYa(it) })
                    }
                    Column(Modifier.padding(start = 14.dp).weight(1f)) {
                        Text(
                            comic?.nombre?.substringBeforeLast('.')
                                ?: p.uri.substringAfterLast("%2F").substringAfterLast('/'),
                            style = Tipo.cuerpo,
                            color = if (comic == null) Apagado else Hueso
                        )
                        Text(
                            if (mapa == null) "buscándolo..."
                            else if (comic == null) "ya no está en tu biblioteca"
                            else "página ${p.pagina + 1}",
                            style = Tipo.pie, color = Tenue,
                            modifier = Modifier.padding(top = 3.dp)
                        )
                    }
                    Text("\u2715", fontSize = 15.sp, color = Apagado,
                        modifier = Modifier.padding(start = 10.dp)
                            .clickableSimple { vm.alternarMarcador(p.uri, p.pagina) })
                }
                Box(Modifier.padding(start = 20.dp).fillMaxWidth()
                    .height(0.5.dp).background(Linea))
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/**
 * La pestaña de Lecturas: lo que llevas leido.
 *
 * ANTES ERA UN ENLACE dentro de una pantalla de listas, y las listas iban del
 * catalogo mundial de un personaje: "0 de 2411 numeros" de Batman. Al quitar
 * las listas (02/09/2026) esto pasa a ser la pestaña entera y a contar SOLO
 * tus ficheros, que es de lo que va el resto de la app.
 *
 * Los comics se piden con LaunchedEffect y no en la composicion: recorrer el
 * arbol es una suspension, y aunque salga del indice cacheado, leer disco al
 * pintar es la regla que mas duele romper aqui.
 */
@Composable
fun PantallaEstadisticas(
    vm: VistaModelo,
    onMarcadores: () -> Unit,
    onLeer: (Comic) -> Unit,
    // Nulo cuando es una pestaña del carrusel: ahí no hay a dónde volver y la
    // flecha lo prometería. Cabecera ya sabe no pintarla.
    onAtras: (() -> Unit)?
) {
    val estado by vm.estado.collectAsState()

    var comics by remember { mutableStateOf<List<Comic>?>(null) }
    LaunchedEffect(estado.sello, estado.catalogo) { comics = vm.todosLosComics() }

    val lista = comics
    // MEDIDO EN EL MOVIL (03/09/2026): 4-6 ms con 293 comics. Se cronometro
    // porque recorre la biblioteca entera en el hilo principal y se sospechaba
    // de ella; **no era**. Se queda donde esta y sin cronometro: mover esto a
    // Dispatchers.Default seria pagar un salto de hilo por cinco milisegundos.
    val r = remember(lista, estado.sello) {
        lista?.let { Estadisticas.calcular(vm.marcas.todas(), it) }
    }

    // POR DONDE VAS BAJANDO, igual que la pila de carpetas de la biblioteca:
    // el arbol de cada uno es distinto —el de Dani es DC Comics / personaje /
    // serie— asi que en vez de adivinar los niveles, se navegan.
    var camino by remember { mutableStateOf("") }
    val nivel = remember(lista, estado.sello, camino) {
        lista?.let { Estadisticas.avance(vm.marcas.todas(), it, camino) } ?: emptyList()
    }
    // Se lleva por fuera del NavHost, asi que necesita su propio BackHandler o
    // el gesto de atras se lo salta y sale de la pantalla. Misma trampa que con
    // la pila de carpetas.
    BackHandler(enabled = camino.isNotBlank()) { camino = padreDe(camino) }

    val seguidas = remember(estado.sello) { vm.seriesSeguidas() }

    // El calendario. El mes visible es estado de la pantalla; lo que se leyó,
    // una funcion pura sobre el progreso.
    val hoy = remember { Novedades.hoy() }

    // Lo que esta anunciado y sin salir de las series que sigues. Fuera del
    // LazyColumn, como todo lo demas: su cuerpo no es Composable y remember no
    // se puede llamar ahi. Y con el MISMO `hoy` que el calendario: un solo sitio
    // decide que dia es hoy, que es la regla de toda esta parte.
    val agenda = remember(seguidas, hoy) { Novedades.agenda(seguidas, hoy) }
    var mesVisible by remember { mutableStateOf(LocalDate(hoy.year, hoy.monthNumber, 1)) }
    val porDia = remember(lista, estado.sello, mesVisible) {
        lista?.let {
            Calendario.porDia(vm.sesiones.todas(), it, mesVisible.year, mesVisible.monthNumber)
        } ?: emptyMap()
    }
    // El dia cuyo detalle esta abierto. null = ninguno.
    var diaAbierto by remember(mesVisible) { mutableStateOf<Int?>(null) }

    diaAbierto?.let { dia ->
        DetalleDelDia(
            dia = dia, mes = mesVisible, leidos = porDia[dia].orEmpty(),
            onLeer = { diaAbierto = null; onLeer(it) },
            onCerrar = { diaAbierto = null }
        )
    }

    Column(Modifier.fillMaxSize().background(Tinta).navigationBarsPadding()) {
        Cabecera("Lecturas", "Qué llevas leído", onAtras)

        if (r == null) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }

        LazyColumn(Modifier.weight(1f)) {
            item {
                Text("Marcapáginas  ›", style = Tipo.secundario, color = Acento,
                    modifier = Modifier.padding(20.dp, 4.dp, 20.dp, 8.dp)
                        .clickableSimple(accion = onMarcadores))
            }
            item {
                Row(Modifier.fillMaxWidth().padding(12.dp, 8.dp)) {
                    Cifra("${r.terminados}", "cómics leídos", Modifier.weight(1f))
                    Cifra("${r.paginas}", "páginas", Modifier.weight(1f))
                    Cifra("${r.racha}",
                        if (r.racha == 1) "día seguido" else "días seguidos",
                        Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth().padding(12.dp, 0.dp)) {
                    Cifra("${r.comics}", "cómics", Modifier.weight(1f))
                    Cifra("${r.seriesCompletas}/${r.series}", "series completas",
                        Modifier.weight(1f))
                    Cifra("${r.dias}", "días leyendo", Modifier.weight(1f))
                }

                if (r.empezados > 0) Text("Y ${r.empezados} cómics a medias.",
                    Modifier.padding(20.dp, 14.dp, 20.dp, 0.dp),
                    style = Tipo.pie, color = Tenue)
            }

            item {
                CalendarioMes(vm, mesVisible, porDia, hoy, { mesVisible = it }) { diaAbierto = it }
            }

            // ── qué sale próximamente ──
            //
            // POR FECHA, mezclando series, que es lo unico que aporta sobre la
            // lista de abajo: aquella ya dice el proximo de cada serie, pero
            // ordenada por serie. La pregunta que contesta esta no es "¿que
            // viene de Batman?" sino "¿que es lo siguiente que me llega?".
            //
            // Va ANTES de "siguiendo" porque es lo que caduca: la lista de
            // series seguidas es la misma toda la semana y esto cambia solo.
            if (agenda.isNotEmpty()) {
                item {
                    Text("PRÓXIMAMENTE", style = Tipo.pie, color = Tenue,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.padding(20.dp, 22.dp, 20.dp, 4.dp))
                }
                // SIN `key`, al reves que la lista de seguidas. Ahi la clave es
                // la ruta de una carpeta tuya y es unica de verdad; aqui saldria
                // de datos de Comic Vine, donde un numero repetido no es
                // imposible — y dos claves iguales en un LazyColumn no se ven
                // raras: revientan la pantalla.
                items(agenda) { p -> FilaPrevista(p, hoy) }
            }

            // ── las series que sigues ──
            //
            // Aqui y no en Ajustes: seguir una serie es una decision de lectura,
            // no un ajuste de la app. Y hasta ahora solo se veian de una en una
            // entrando en la carpeta de cada una, asi que no habia forma de
            // dejar de seguir algo sin ir a buscarlo.
            if (seguidas.isNotEmpty()) {
                item {
                    Text("SIGUIENDO", style = Tipo.pie, color = Tenue, letterSpacing = 0.5.sp,
                        modifier = Modifier.padding(20.dp, 22.dp, 20.dp, 4.dp))
                }
                // CLAVE CON PREFIJO, Y NO SOLO LA RUTA. Las dos listas de esta
                // pantalla —las que sigues y el nivel que estas mirando— van en
                // el MISMO LazyColumn, asi que comparten espacio de claves: una
                // serie seguida que ademas aparezca en el nivel actual repetia
                // clave y Compose cerraba la app.
                //   IllegalArgumentException: Key "..." was already used
                // Paso en el movil el 03/09/2026 con "Absolute green lantern",
                // seguida y a la vez dentro de "DC Comics/Green lantern".
                items(seguidas, key = { "seguida:${it.ruta}" }) { f ->
                    FilaSeguida(f) { vm.seguirSerie(f.ruta, false) }
                }
            }

            item {
                Row(
                    Modifier.fillMaxWidth().padding(20.dp, 22.dp, 20.dp, 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (camino.isBlank()) "TU BIBLIOTECA"
                        else camino.uppercase(),
                        style = Tipo.pie, color = Tenue, letterSpacing = 0.5.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (camino.isNotBlank()) Text("subir  \u2191",
                        style = Tipo.minuscula, color = Acento,
                        modifier = Modifier.clickableSimple { camino = padreDe(camino) })
                }
            }
            items(nivel, key = { "nivel:${it.ruta}" }) { a ->
                // Solo se puede bajar si hay algo debajo. Una fila pulsable que
                // no lleva a ningun sitio se lee como que la app falla.
                val puedeBajar = !a.hoja
                Column(
                    Modifier.fillMaxWidth()
                        .then(if (puedeBajar) Modifier.clickableSimple { camino = a.ruta }
                              else Modifier)
                        .padding(20.dp, 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(a.nombre, style = Tipo.destacado, color = Hueso,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f))
                        Text("${a.porcentaje}%", style = Tipo.pie, color = Acento)
                        if (puedeBajar) Text("\u203a", fontSize = 20.sp, color = Apagado,
                            modifier = Modifier.padding(start = 8.dp))
                    }
                    Text("${a.leidos} de ${a.total}", style = Tipo.pie, color = Tenue,
                        modifier = Modifier.padding(top = 2.dp))
                    LinearProgressIndicator(
                        progress = {
                            if (a.total == 0) 0f else a.leidos.toFloat() / a.total
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                            .height(3.dp).clip(RoundedCornerShape(2.dp)),
                        color = Acento, trackColor = PanelAlto
                    )
                }
            }
            // La píldora flota sobre las tres pestañas desde que se puede
            // deslizar entre ellas, así que aquí también hay que dejarle sitio.
            item { Spacer(Modifier.height(96.dp)) }
        }
    }
}

/**
 * El mes con las portadas de lo que leiste cada dia.
 *
 * LO QUE SE VE ES LA PORTADA, NO UN PUNTO. Un calendario con marcas de colores
 * dice cuanto has leido; uno con portadas dice QUE leiste, y eso es lo que hace
 * que te pares a mirarlo. Es lo mejor que tiene Mistbook y aqui sale gratis:
 * las miniaturas ya estan en cache para pintar el catalogo.
 *
 * NO SE PUEDE PASAR DEL MES ACTUAL. Un calendario de lectura no tiene futuro, y
 * una flecha que lleva a doce casillas vacias es una flecha rota.
 *
 * Las semanas empiezan en LUNES y las calcula [Calendario.semanas], fuera de
 * Compose: es aritmetica con dos casos de borde —el mes que empieza en domingo
 * y el que necesita seis filas— y eso se prueba mejor donde se puede probar.
 */
@Composable
private fun CalendarioMes(
    vm: VistaModelo,
    mes: LocalDate,
    porDia: Map<Int, List<Calendario.Leido>>,
    hoy: LocalDate,
    onMes: (LocalDate) -> Unit,
    onDia: (Int) -> Unit
) {
    val semanas = remember(mes) { Calendario.semanas(mes.year, mes.monthNumber) }
    val haySiguiente = mes < LocalDate(hoy.year, hoy.monthNumber, 1)

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(FormaTarjeta).background(Panel).padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${mes.year}", style = Tipo.minuscula, color = Tenue)
                Text(Calendario.nombreMes(mes.monthNumber),
                    style = Tipo.destacado, color = Hueso)
            }
            Text("‹", fontSize = 26.sp, color = Acento,
                modifier = Modifier.clickableSimple { onMes(mes.minus(DatePeriod(months = 1))) }
                    .padding(horizontal = 12.dp))
            Text("›", fontSize = 26.sp,
                color = if (haySiguiente) Acento else Apagado,
                modifier = Modifier.clickableSimple(enabled = haySiguiente) {
                    onMes(mes.plus(DatePeriod(months = 1)))
                }.padding(horizontal = 12.dp))
        }

        Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp)) {
            Calendario.DIAS.forEach {
                Text(it, style = Tipo.minuscula, color = Tenue,
                    textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }

        semanas.forEach { semana ->
            Row(Modifier.fillMaxWidth()) {
                semana.forEach { dia ->
                    Box(Modifier.weight(1f).aspectRatio(0.78f).padding(2.dp)) {
                        if (dia != null) Casilla(vm, dia, porDia[dia].orEmpty(),
                            esHoy = mes.year == hoy.year &&
                                mes.monthNumber == hoy.monthNumber && dia == hoy.dayOfMonth,
                            onDia = onDia)
                    }
                }
            }
        }
    }
}

/** Un dia del calendario: la portada de lo ultimo que leiste, o solo el numero. */
@Composable
private fun Casilla(
    vm: VistaModelo,
    dia: Int,
    leidos: List<Calendario.Leido>,
    esHoy: Boolean,
    onDia: (Int) -> Unit
) {
    val primero = leidos.firstOrNull()
    Box(
        Modifier.fillMaxSize()
            .then(if (primero != null)
                Modifier.caratula(FormaChapa).clickable { onDia(dia) }
            else Modifier.clip(FormaChapa))
    ) {
        if (primero != null) {
            Portada(primero.comic.uri, Modifier.fillMaxSize(),
                { vm.portada(it) }, { vm.portadaYa(it) })
            // El numero tiene que leerse sobre cualquier portada, y una portada
            // puede ser blanca: por eso chapa oscura debajo y no solo color.
            Box(Modifier.align(Alignment.TopStart).padding(2.dp)
                .clip(FormaChapa).background(Color(0xB3000000))
                .padding(horizontal = 4.dp)) {
                Text("$dia", style = Tipo.minuscula, color = Hueso)
            }
            // Mas de uno ese dia: se dice, porque solo se ve la portada de uno.
            if (leidos.size > 1) Text("+${leidos.size - 1}",
                style = Tipo.minuscula, color = Hueso,
                modifier = Modifier.align(Alignment.BottomEnd)
                    .padding(3.dp))
        } else {
            Text("$dia", style = Tipo.minuscula,
                color = if (esHoy) Acento else Apagado,
                modifier = Modifier.align(Alignment.TopStart).padding(4.dp))
        }
    }
}

/**
 * Lo que leiste un dia concreto, con cuanto de cada cosa.
 *
 * ES LA MITAD DE PARA LO QUE SIRVE EL CALENDARIO. La casilla enseña la portada
 * de lo ultimo, que dice "ese dia lei"; esto dice QUE y CUANTO, que es lo que
 * Dani pidio: "este dias has leido 3 paginas de este comic otras 3 de este".
 *
 * Las paginas salen del diario, no de la marca: son las nuevas vistas ESE dia,
 * asi que un tomo repartido en tres tardes reparte tambien su cuenta.
 */
@Composable
private fun DetalleDelDia(
    dia: Int,
    mes: LocalDate,
    leidos: List<Calendario.Leido>,
    onLeer: (Comic) -> Unit,
    onCerrar: () -> Unit
) {
    val total = leidos.sumOf { it.paginas }
    AlertDialog(
        onDismissRequest = onCerrar,
        confirmButton = { TextButton(onClick = onCerrar) { Text("Cerrar") } },
        title = {
            Text("$dia de ${Calendario.nombreMes(mes.monthNumber).lowercase()}",
                style = Tipo.destacado, color = Hueso)
        },
        text = {
            Column {
                Text(
                    "$total página${if (total == 1) "" else "s"} · " +
                    "${leidos.size} cómic${if (leidos.size == 1) "" else "s"}",
                    style = Tipo.pie, color = Acento,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
                leidos.forEach { l ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clickableSimple { onLeer(l.comic) }
                            .padding(vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(l.comic.nombre.substringBeforeLast('.'),
                                style = Tipo.secundario, color = Hueso,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(l.comic.carpeta.trimEnd('/').substringAfterLast('/'),
                                style = Tipo.minuscula, color = Tenue, maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp))
                        }
                        Column(horizontalAlignment = Alignment.End,
                               modifier = Modifier.padding(start = 12.dp)) {
                            Text(l.tramo, style = Tipo.minuscula, color = Acento)
                            Text("${l.paginas} en total", style = Tipo.minuscula,
                                color = Apagado, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                }
            }
        }
    )
}

/**
 * Un numero que aun no ha salido: de que serie, cual, y cuando llega.
 *
 * LA FECHA A LA DERECHA Y EN ACENTO porque es la columna que se lee: la lista
 * esta ordenada por ella, asi que el ojo baja por ese lado. El nombre de la
 * serie va arriba y el numero debajo, no al reves, porque con quince filas lo
 * que buscas es tu serie.
 *
 * "aproximada" solo cuando lo es. Es la regla de siempre del proyecto —decir de
 * donde sale el dato— con el minimo de letra: una palabra, y solo en las filas
 * donde Comic Vine no traia la fecha de venta y ha habido que calcularla.
 */
@Composable
private fun FilaPrevista(p: Novedades.Prevista, hoy: LocalDate) {
    Row(
        Modifier.fillMaxWidth().padding(20.dp, 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(p.serie, style = Tipo.secundario, color = Hueso,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(p.etiqueta, style = Tipo.minuscula, color = Tenue,
                modifier = Modifier.padding(top = 2.dp))
        }
        Column(horizontalAlignment = Alignment.End,
               modifier = Modifier.padding(start = 12.dp)) {
            Text(Novedades.cuandoSale(p.cuando, hoy), style = Tipo.minuscula,
                color = Acento, maxLines = 1)
            if (p.estimada) Text("aproximada", style = Tipo.minuscula, color = Apagado,
                modifier = Modifier.padding(top = 2.dp))
        }
    }
    Box(Modifier.padding(start = 20.dp).fillMaxWidth().height(0.5.dp).background(Linea))
}

/**
 * Una serie que sigues: como se llama, que viene, y la cruz para dejarla.
 *
 * La cruz va en Tenue y no en Alarma: dejar de seguir no borra nada —el registro
 * de lo ya avisado se conserva— asi que pintarlo de rojo seria avisar de un
 * peligro que no existe.
 */
@Composable
private fun FilaSeguida(f: Ficha, onDejar: () -> Unit) {
    val hoy = remember { Novedades.hoy() }
    val proximo = remember(f.numeros) { Novedades.proximo(f.numeros, hoy) }
    Row(
        Modifier.fillMaxWidth().padding(20.dp, 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("${f.nombre}${f.anio?.let { " ($it)" } ?: ""}",
                style = Tipo.secundario, color = Hueso,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                proximo?.let { Novedades.fraseProximo(it, hoy) }
                    ?: "Sin nada anunciado todavía.",
                style = Tipo.minuscula, color = Tenue,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Text("\u2715", fontSize = 15.sp, color = Tenue,
            modifier = Modifier.clickableSimple(accion = onDejar)
                .padding(start = 14.dp, top = 4.dp, bottom = 4.dp))
    }
    Box(Modifier.padding(start = 20.dp).fillMaxWidth().height(0.5.dp).background(Linea))
}

/** El nivel de arriba de una ruta: "DC/Batman/Vol 3" -> "DC/Batman". */
private fun padreDe(ruta: String) = ruta.trim('/').substringBeforeLast('/', "")

@Composable
private fun Cifra(valor: String, etiqueta: String, modifier: Modifier = Modifier) {
    Column(
        modifier.padding(4.dp).clip(FormaTarjeta).background(Panel).padding(12.dp)
    ) {
        Text(valor, style = Tipo.titulo, color = Acento)
        Text(etiqueta, style = Tipo.minuscula, color = Tenue,
            modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
fun PantallaAjustes(
    cvInicial: String,
    onGuardar: (String) -> Unit,
    onCarpeta: () -> Unit,
    vm: VistaModelo,
    onAtras: (() -> Unit)?
) {
    var cv by remember { mutableStateOf(cvInicial) }
    var recortar by remember { mutableStateOf(vm.recortar) }
    var dobles by remember { mutableStateOf(vm.dobles) }
    var llenar by remember { mutableStateOf(vm.llenar) }
    var copia by remember { mutableStateOf("") }
    var conversion by remember { mutableStateOf("") }
    var limpieza by remember { mutableStateOf("") }
    // Cual de los dos trabajos largos esta corriendo. Los dos leen el mismo
    // estado.cargando del modelo, asi que sin esto el avance de la limpieza
    // salia debajo del boton de convertir.
    var tarea by remember { mutableStateOf("") }
    // Hace falta para el avance de la conversion de CBR: esta pantalla no leia
    // el estado del modelo porque hasta ahora no lanzaba ningun trabajo largo.
    val estado by vm.estado.collectAsState()
    val alcance = rememberCoroutineScope()

    val guardarCopia = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { destino -> if (destino != null) alcance.launch { copia = vm.exportarA(destino) } }

    val abrirCopia = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { origen -> if (origen != null) alcance.launch { copia = vm.importarDe(origen) } }

    // La carpeta de la copia automatica. Se pide el ARBOL y con permiso
    // PERSISTENTE: sin eso, mañana la app no puede volver a escribir ahi.
    val ctxAjustes = LocalContext.current
    val elegirCarpetaCopia = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            runCatching {
                ctxAjustes.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            vm.elegirCarpetaCopia(uri.toString())
        }
    }
    var guardado by remember { mutableStateOf(false) }
    var conexion by remember { mutableStateOf("") }
    var probando by remember { mutableStateOf(false) }

    LaunchedEffect(probando) {
        if (probando) { conexion = vm.probarConexion(); probando = false }
    }

    Column(Modifier.fillMaxSize().background(Tinta).navigationBarsPadding()) {
        Cabecera("Ajustes", "", onAtras)
        // Sin relleno lateral aqui: lo pone Grupo, que es quien dibuja la
        // tarjeta. Con los dos, las tarjetas salian estrechas y descentradas.
        LazyColumn(Modifier.padding(vertical = 6.dp)) {
            item {
                Grupo("Carpeta de cómics") {
                    Fila("Elegir carpeta", detalle = vm.raiz ?: "sin elegir",
                        chevron = true, ultima = true, onClick = onCarpeta)
                }

                Grupo("Lector") {
                    Interruptor(
                        "Recortar bordes",
                        "Quita el marco liso de cada página.",
                        recortar
                    ) { recortar = it; vm.recortar = it }

                    Interruptor(
                        "Doble página en horizontal",
                        "Al girar el móvil, dos páginas a la vez.",
                        dobles
                    ) { dobles = it; vm.dobles = it }

                    // Segmentado y no interruptor: "Llenar la pantalla: apagado"
                    // no dice que pasa entonces. Con las dos opciones a la vista
                    // se entiende sin leer la explicacion.
                    Column(Modifier.padding(start = 16.dp, end = 16.dp,
                                            top = 12.dp, bottom = 14.dp)) {
                        Text("Cómo encaja la página", style = Tipo.cuerpo, color = Hueso)
                        Text(
                            "Llenar se come los laterales; amplía para verlos.",
                            style = Tipo.pie, color = Tenue,
                            modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                        )
                        Segmentado(listOf("Encajar", "Llenar"), if (llenar) 1 else 0) { i ->
                            llenar = i == 1
                            vm.llenar = llenar
                        }
                    }
                }

                Grupo("Cómics en CBR") {
                    Column(Modifier.padding(16.dp)) {
                        Text("Un CBR grande puede cerrar la app. Convertirlo lo arregla.",
                            style = Tipo.pie, color = Tenue,
                            modifier = Modifier.padding(bottom = 10.dp))
                        Text("Solo borra el CBR si el CBZ tiene todas las páginas.",
                            style = Tipo.pie, color = Apagado,
                            modifier = Modifier.padding(bottom = 12.dp))
                        Interruptor(
                            "Convertirlos solos",
                            "Al abrir la app, convierte los CBR nuevos.",
                            vm.autoConvertir, ultima = true
                        ) { vm.autoConvertir = it }

                        Spacer(Modifier.height(14.dp))

                        Boton(
                            if (tarea == "convertir") "Convirtiendo..."
                            else "Buscar y convertir ahora",
                            activo = !estado.cargando
                        ) {
                            conversion = ""
                            tarea = "convertir"
                            vm.convertirCbr { m -> conversion = m; tarea = "" }
                        }
                        if (tarea == "convertir" && estado.progreso.isNotBlank())
                            Text(estado.progreso, style = Tipo.minuscula, color = Tenue,
                                modifier = Modifier.padding(top = 10.dp))
                        if (conversion.isNotBlank())
                            Text(conversion, style = Tipo.pie, color = Tenue,
                                modifier = Modifier.padding(top = 10.dp))
                    }
                }

                Grupo("Limpiar la biblioteca") {
                    Column(Modifier.padding(16.dp)) {
                        Text("Arregla nombres y borra duplicados de descargas repetidas.",
                            style = Tipo.pie, color = Tenue,
                            modifier = Modifier.padding(bottom = 10.dp))
                        Text("Solo borra una copia si el original está al lado y tiene " +
                             "las mismas páginas.",
                            style = Tipo.pie, color = Apagado,
                            modifier = Modifier.padding(bottom = 12.dp))
                        Boton(
                            if (tarea == "limpiar") "Trabajando..."
                            else "Limpiar nombres y duplicados",
                            relleno = false,
                            activo = !estado.cargando
                        ) {
                            limpieza = ""
                            tarea = "limpiar"
                            vm.limpiarBiblioteca { m -> limpieza = m; tarea = "" }
                        }
                        if (tarea == "limpiar" && estado.progreso.isNotBlank())
                            Text(estado.progreso, style = Tipo.minuscula, color = Tenue,
                                modifier = Modifier.padding(top = 10.dp))
                        if (limpieza.isNotBlank())
                            Text(limpieza, style = Tipo.pie, color = Tenue,
                                modifier = Modifier.padding(top = 10.dp))
                    }
                }

                Grupo("Cómics nuevos") {
                    Column(Modifier.padding(16.dp)) {
                        Text("Repasa toda la biblioteca de arriba abajo. Los cómics que " +
                             "copies aparecen solos al volver a la app.",
                            style = Tipo.pie, color = Tenue,
                            modifier = Modifier.padding(bottom = 12.dp))
                        Boton("Buscar cómics nuevos", relleno = false,
                              activo = !estado.cargando) { vm.repasarBiblioteca() }
                    }
                }

                Grupo("Espacio que ocupa la app") {
                    Column(Modifier.padding(16.dp)) {
                        // Se relee con el sello: al vaciar sube, y asi el numero
                        // que se ve es el de despues y no el de antes.
                        val copias = remember(estado.sello) { vm.cacheConvertidos() }
                        val portadas = remember(estado.sello) { vm.cachePortadas() }
                        fun mb(b: Long) = "%.1f".format(b / 1048576.0) + " MB"

                        Text("No son tus cómics: son copias que la app se fabrica. " +
                             "Borrarlas no pierde nada.",
                            style = Tipo.pie, color = Tenue,
                            modifier = Modifier.padding(bottom = 14.dp))

                        Text("Cómics convertidos · ${mb(copias)}",
                            style = Tipo.cuerpo, color = Hueso)
                        Text("Se borran solos a los 14 días sin abrirlos.",
                            style = Tipo.pie, color = Apagado,
                            modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
                        Boton("Borrar los convertidos", relleno = false,
                              activo = copias > 0 && !estado.cargando) {
                            vm.vaciarConvertidos()
                        }

                        Text("Portadas · ${mb(portadas)}", style = Tipo.cuerpo, color = Hueso,
                            modifier = Modifier.padding(top = 18.dp))
                        Text("Las miniaturas del catálogo. Se rehacen solas.",
                            style = Tipo.pie, color = Apagado,
                            modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
                        Boton("Borrar las portadas", relleno = false,
                              activo = portadas > 0 && !estado.cargando) {
                            vm.vaciarPortadas()
                        }
                    }
                }

                Grupo("Copia de seguridad") {
                    Column(Modifier.padding(16.dp)) {
                        Text("Lo que has leído no sale de ningún sitio: si lo pierdes, " +
                             "se pierde.",
                            style = Tipo.pie, color = Tenue,
                            modifier = Modifier.padding(bottom = 12.dp))

                        val carpeta = remember(estado.sello) { vm.nombreCarpetaCopia() }
                        Text(
                            if (carpeta == null) "Elegir carpeta para la copia automática  ›"
                            else "Copia automática en «$carpeta»  ·  cambiar",
                            style = Tipo.pie, color = if (carpeta == null) Acento else Cian,
                            modifier = Modifier.padding(bottom = 10.dp)
                                .clickableSimple { elegirCarpetaCopia.launch(null) }
                        )
                        if (carpeta != null) {
                            var alSalir by remember(estado.sello) {
                                mutableStateOf(vm.copiaAlSalir)
                            }
                            Interruptor(
                                "Guardarla al salir de la app",
                                "Solo si has leído algo desde la última.",
                                alSalir, ultima = true
                            ) { vm.copiaAlSalir = it; alSalir = it }
                        }
                        Row {
                            Boton("Guardar copia", Modifier.weight(1f)) {
                                copia = ""
                                guardarCopia.launch("lector-copia.json")
                            }
                            Spacer(Modifier.width(10.dp))
                            Boton("Restaurar", Modifier.weight(1f), relleno = false) {
                                copia = ""
                                abrirCopia.launch(arrayOf("application/json"))
                            }
                        }
                        if (copia.isNotBlank()) Text(copia, style = Tipo.secundario,
                            color = Tenue, modifier = Modifier.padding(top = 10.dp))
                    }
                }

                // Las dos claves en UNA tarjeta y con un solo boton de guardar:
                // antes parecian dos ajustes independientes con un boton suelto
                // debajo, y no se sabia a cual de los dos aplicaba.
                Grupo("Comic Vine") {
                    Column(Modifier.padding(16.dp)) {
                        Text("Los números de cada serie. Gratis en " +
                             "comicvine.gamespot.com/api",
                            style = Tipo.pie, color = Tenue,
                            modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
                        Campo(cv, "Clave de API", oculto = true) { cv = it; guardado = false }

                        Spacer(Modifier.height(18.dp))
                        Row {
                            Boton("Guardar", Modifier.weight(1f)) {
                                onGuardar(cv.trim()); guardado = true
                            }
                            Spacer(Modifier.width(10.dp))
                            Boton(if (probando) "Probando..." else "Probar",
                                Modifier.weight(1f),
                                activo = !probando, relleno = false) { probando = true }
                        }
                        if (guardado) Text("Guardado.", style = Tipo.secundario,
                            color = Cian,
                            modifier = Modifier.padding(top = 10.dp))

                        if (conexion.isNotBlank()) Text(conexion,
                            style = Tipo.minuscula,
                            color = if (conexion.startsWith("OK")) Cian else Alarma,
                            modifier = Modifier.padding(top = 12.dp))

                        Text("Se guarda solo en este móvil, o en local.properties.",
                            style = Tipo.pie, color = Apagado,
                            modifier = Modifier.padding(top = 18.dp))
                    }   // cierra el Column del relleno de la tarjeta
                }       // cierra el Grupo "Comic Vine"

                // ─────────────────── DIAGNOSTICO ───────────────────
                //
                // Existe por la pantalla en negro del 02/09/2026, que no se
                // pudo diagnosticar ni adivinando ni preguntando: pasa usando
                // el movil por ahi, no enchufado al PC, y Dani no sabe decir
                // cuando. Ver datos/Rastro.kt.
                Grupo("Diagnóstico") {
                    Column(Modifier.padding(16.dp)) {
                        Text("Si la app se queda en negro: ciérrala del todo, " +
                             "vuelve a abrirla y copia esto.",
                            style = Tipo.pie, color = Tenue,
                            modifier = Modifier.padding(bottom = 12.dp))

                        var rastro by remember { mutableStateOf("") }
                        LaunchedEffect(estado.sello) {
                            rastro = com.dani.lector.datos.Rastro.leer(ctxAjustes)
                        }
                        // Las ultimas de todas: son las de justo antes del
                        // fallo, que es lo unico que importa mirando a mano.
                        Text(
                            rastro.trim().lines().takeLast(12).joinToString("\n"),
                            style = Tipo.minuscula, color = Apagado,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        Row {
                            Boton("Copiar todo", Modifier.weight(1f)) {
                                val cp = ctxAjustes.getSystemService(
                                    android.content.ClipboardManager::class.java)
                                cp?.setPrimaryClip(android.content.ClipData.newPlainText(
                                    "rastro", rastro))
                                copia = "Rastro copiado. Pégalo donde haga falta."
                            }
                            Spacer(Modifier.width(10.dp))
                            Boton("Borrar", Modifier.weight(1f), relleno = false) {
                                com.dani.lector.datos.Rastro.limpiar(ctxAjustes)
                                rastro = ""
                            }
                        }
                    }
                }

                // Sitio para la píldora, igual que en las otras dos pestañas.
                Spacer(Modifier.height(96.dp))
            }
        }
    }
}


/**
 * Menu al mantener pulsado un comic. Va como hoja inferior en vez de dialogo:
 * en un movil se alcanza con el pulgar sin recolocar la mano.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuComic(vm: VistaModelo, comic: Comic, onLeer: (Comic) -> Unit, onCerrar: () -> Unit) {
    val estado by vm.estado.collectAsState()
    val marca = remember(comic.uri, estado.sello) { vm.marcas.de(comic.uri) }
    val estadoHoja = rememberModalBottomSheetState()

    ModalBottomSheet(onDismissRequest = onCerrar, sheetState = estadoHoja,
        containerColor = Panel) {
        Column(Modifier.padding(bottom = 28.dp)) {

            // ficha: portada y datos
            Row(Modifier.padding(20.dp, 4.dp, 20.dp, 16.dp)) {
                Box(Modifier.width(66.dp).height(99.dp).caratula()) {
                    Portada(comic.uri, Modifier.fillMaxSize(), { vm.portada(it) }, { vm.portadaYa(it) })
                }
                Column(Modifier.padding(start = 14.dp)) {
                    Text(comic.nombre.substringBeforeLast('.'),
                        style = Tipo.destacado, color = Hueso, maxLines = 3)
                    Text(comic.carpeta.ifBlank { "raíz" },
                        style = Tipo.pie, color = Apagado,
                        modifier = Modifier.padding(top = 4.dp))
                    Text(buildString {
                        comic.numero?.let { append("nº $it") } ?: append("sin número")
                        marca?.let { append(" · ${it.porcentaje}% leído") }
                    }, style = Tipo.pie, color = Tenue, modifier = Modifier.padding(top = 2.dp))
                }
            }
            Box(Modifier.padding(start = 20.dp).fillMaxWidth()
                .height(0.5.dp).background(Linea))

            OpcionMenu("Leer") { onCerrar(); onLeer(comic) }

            if (marca?.terminado == true)
                OpcionMenu("Marcar como no leído") {
                    vm.marcarLeido(comic, false); onCerrar()
                }
            else
                OpcionMenu("Marcar como leído") {
                    vm.marcarLeido(comic, true); onCerrar()
                }

            if (marca != null && !marca.terminado)
                OpcionMenu("Empezar de nuevo", "vuelve a la primera página") {
                    vm.reiniciarProgreso(comic); onCerrar()
                }
        }
    }
}

