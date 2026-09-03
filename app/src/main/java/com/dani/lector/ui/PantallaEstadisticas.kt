package com.dani.lector.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.dani.lector.datos.*

// La pestaña de Lecturas: cifras, calendario, agenda y series seguidas.
// Salio de Pantallas.kt al partirlo por pantallas.

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
