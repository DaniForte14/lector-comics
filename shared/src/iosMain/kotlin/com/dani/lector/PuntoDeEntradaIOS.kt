package com.dani.lector

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeUIViewController
import com.dani.lector.datos.ArchivoIOS
import com.dani.lector.datos.BibliotecaIOS
import com.dani.lector.datos.Comic
import com.dani.lector.datos.Paginas
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.UIKit.UIViewController
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text

/**
 * iOS — Por donde entra la app del iPad. Lo llama `App.swift`.
 *
 * `ComposeUIViewController` envuelve una funcion `@Composable` en algo que UIKit
 * sabe enseñar. Es la unica costura entre Swift y Kotlin en todo el proyecto: de
 * aqui hacia dentro **ya no hay Swift**.
 */
fun puntoDeEntrada(): UIViewController = ComposeUIViewController { PantallaSonda() }

/**
 * iOS — LA SONDA, Y ES CODIGO PARA TIRAR.
 *
 * **NO ES LA INTERFAZ DE LA APP.** Las cuatro pantallas de verdad siguen en
 * `:app` y su mudanza a Compose Multiplatform es la mitad del trabajo que falta.
 * Esto son cuarenta lineas cuyo unico objetivo es **convertir cuatro "compila"
 * en "funciona"**: `BibliotecaIOS` lista, `ArchivoIOS` abre, `ZipIOS`
 * descomprime e `ImagenIOS` decodifica. Si sale una pagina en la pantalla, las
 * cuatro piezas estan bien; si no sale, el fallo esta acotado a cuatro ficheros
 * y no a treinta.
 *
 * SE BORRA en cuanto la biblioteca de verdad arranque en el iPad.
 *
 * **LEE DE LA CARPETA DOCUMENTS DE LA APP, no de una que elijas.** El selector
 * de documentos y los marcadores son la otra mitad del riesgo y van en su propia
 * tanda; mezclarlos aqui seria no saber cual de las dos cosas ha fallado. Los
 * CBZ se meten desde la app Archivos del iPad, que para eso el `Info.plist`
 * lleva `UIFileSharingEnabled`.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
private fun PantallaSonda() {
    val biblioteca = remember { BibliotecaIOS() }
    val archivo = remember { ArchivoIOS() }

    val documentos = remember {
        NSSearchPathForDirectoriesInDomains(
            NSDocumentDirectory, NSUserDomainMask, true
        ).first() as String
    }

    var comics by remember { mutableStateOf<List<Comic>>(emptyList()) }
    var pagina by remember { mutableStateOf<ImageBitmap?>(null) }
    var recado by remember { mutableStateOf("Leyendo Documents…") }

    LaunchedEffect(Unit) {
        comics = biblioteca.abrir(documentos, null).comics
        recado = if (comics.isEmpty())
            "No hay ningún CBZ en Documents.\n\nMétele uno desde la app Archivos " +
            "del iPad: En mi iPad > Lector."
        else "${comics.size} cómic(s). Toca uno."
    }

    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            val actual = pagina
            if (actual != null) {
                Box(Modifier.fillMaxSize().background(Color.Black).clickable { pagina = null }) {
                    Image(actual, null, Modifier.fillMaxSize())
                }
            } else {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(recado, style = MaterialTheme.typography.bodyLarge)
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(comics) { c ->
                            Text(
                                "${c.nombre}  ·  nº ${c.numero ?: "?"}",
                                Modifier.fillMaxWidth().clickable {
                                    // La primera pagina y nada mas: si esta sale,
                                    // el resto es la misma llamada con otro nombre.
                                    when (val p = archivo.paginas(c.uri)) {
                                        is Paginas.Ok -> {
                                            val primera = p.nombres.firstOrNull()
                                            pagina = primera?.let {
                                                archivo.pagina(c.uri, it, anchoMax = 1200)
                                            }
                                            if (pagina == null) recado = "No se pudo decodificar."
                                        }
                                        is Paginas.Error -> recado = p.motivo
                                    }
                                }.padding(vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
