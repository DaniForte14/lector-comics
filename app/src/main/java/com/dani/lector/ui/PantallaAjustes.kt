package com.dani.lector.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dani.lector.VistaModelo
import com.dani.lector.datos.*
import kotlinx.coroutines.launch

// Ajustes: la pantalla mas pegada a Android de toda la app (SAF para elegir
// carpeta y fichero). Salio de Pantallas.kt al partirlo por pantallas.

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
