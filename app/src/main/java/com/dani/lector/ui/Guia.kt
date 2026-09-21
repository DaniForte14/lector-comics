package com.dani.lector.ui

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.dani.lector.R
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dani.lector.VistaModelo
import com.dani.lector.datos.Guias

/**
 * Una guia de lectura (ver [Guias]) a pantalla completa: el HTML del artefacto,
 * metido en el APK y abierto en un WebView. Abrirla fuera sacaba de la app y
 * pedia la sesion de claude.ai, porque el artefacto es privado (tanda 36). La
 * abren el visor, desde la tarjeta del final, la carpeta del personaje (37) y
 * las tarjetas de Lecturas (39).
 *
 * UN DIALOG, no una capa encima: es su propia ventana, asi que tapa lo que haya
 * donde se llame sin que la pantalla tenga que envolverse en un Box, y atras lo
 * cierra solo. Al cerrarlo sigues donde estabas.
 *
 * `guias/marcar.js` se mete AL ABRIRLA, no dentro de los HTML: asi siguen siendo
 * copias exactas de los artefactos y se pueden volver a copiar sin perder el
 * tachado. Delante va `LEIDAS_APP`, lo tachado que guarda la app, y la guia lo
 * devuelve por el puente `Lector` cada vez que cambia (tanda 39: hasta entonces
 * vivia en el localStorage del WebView y la app no podia pintar el progreso).
 * El puente solo lo ve lo que va en el APK: los enlaces de dentro (el post de
 * Reddit) salen al navegador, que sin WebViewClient es lo que hace WebView.
 */
@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
fun PantallaGuia(vm: VistaModelo, ruta: String, onCerrar: () -> Unit) {
    Dialog(onCerrar, DialogProperties(usePlatformDefaultWidth = false)) {
        AndroidView(
            factory = { ctx ->
                val marcar = ctx.assets.open("guias/marcar.js").bufferedReader().use { it.readText() }
                val leidas = vm.guiaLeidas(ruta) ?: "null"
                val html = ctx.assets.open(ruta).bufferedReader().use { it.readText() }
                    .replace("</body>",
                        "<script>var LEIDAS_APP = $leidas;</script><script>$marcar</script></body>")
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    addJavascriptInterface(PuenteGuia(vm, ruta), "Lector")
                    loadDataWithBaseURL("file:///android_asset/guias/", html, "text/html", "utf-8", null)
                }
            },
            modifier = Modifier.fillMaxSize().background(Tinta)
        )
    }
}

/**
 * Lo unico que la guia puede pedirle a la app: guardar lo tachado. Llega por el
 * hilo del puente del WebView, no por el de la pantalla; solo escribe
 * preferencias, que eso lo aguanta.
 */
private class PuenteGuia(private val vm: VistaModelo, private val ruta: String) {
    @JavascriptInterface
    fun guardar(leidas: String, hechas: Int, total: Int) = vm.guardarGuia(ruta, leidas, hechas, total)
}

/**
 * La tarjeta de una guia en Lecturas, como una portada (tanda 39): el emblema
 * de su heroe de fondo, el nombre abajo en grande y la barra de lo tachado. Sin
 * abrir nunca, la guia no ha contado aun cuantos apartados tiene, y en vez de
 * una barra vacia dice "sin empezar".
 *
 * La imagen, recortada al centro (`Crop`), que es donde esta el emblema en las
 * dos. Encima, un velo negro arriba y abajo: sin el, el texto blanco se pierde
 * en los rayos de Flash y en el brillo verde.
 */
@Composable
fun TarjetaGuia(guia: Guias.Guia, hechas: Int, total: Int, onAbrir: () -> Unit) {
    val claro = Color.White.copy(alpha = 0.8f)
    Box(
        Modifier.width(128.dp).height(190.dp).clip(FormaBoton).background(Tinta)
            .clickableSimple(accion = onAbrir)
    ) {
        Image(painterResource(imagenDe(guia.ruta)), null, Modifier.matchParentSize(),
            contentScale = ContentScale.Crop)
        Box(Modifier.matchParentSize().background(VELO_GUIA))
        Box(Modifier.matchParentSize().padding(12.dp)) {
            Text("${simboloDe(guia.ruta)} ${guia.partes.uppercase()}", Modifier.align(Alignment.TopStart),
                style = Tipo.minuscula, color = claro)
            Column(Modifier.align(Alignment.BottomStart)) {
                Text(guia.nombre.uppercase(), color = Color.White, style = TextStyle(
                    fontSize = 17.sp, lineHeight = 19.sp,
                    fontWeight = FontWeight.ExtraBold, fontStyle = FontStyle.Italic))
                Text(guia.anios, Modifier.padding(top = 4.dp), style = Tipo.minuscula, color = claro)
                if (total > 0) {
                    Box(Modifier.padding(top = 10.dp).fillMaxWidth().height(4.dp)
                        .background(Color.White.copy(alpha = 0.2f))) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(Guias.progreso(hechas, total))
                            .background(Color.White))
                    }
                    Text("$hechas / $total", Modifier.padding(top = 4.dp), style = Tipo.minuscula, color = claro)
                } else {
                    Text("SIN EMPEZAR", Modifier.padding(top = 10.dp), style = Tipo.minuscula, color = claro)
                }
            }
        }
    }
}

// Las eligio Dani (21/09/2026). En `res/drawable-nodpi`: sin versiones por
// densidad, que para una foto que se recorta no aportan nada.
private fun imagenDe(ruta: String): Int =
    if (ruta == Guias.GREEN_LANTERN) R.drawable.guia_green_lantern else R.drawable.guia_flash

// Oscuro arriba (el rotulo) y mas abajo (nombre, años y barra); en medio, el
// emblema a la vista.
private val VELO_GUIA = Brush.verticalGradient(
    0f to Color.Black.copy(alpha = 0.55f),
    0.3f to Color.Transparent,
    0.5f to Color.Transparent,
    1f to Color.Black.copy(alpha = 0.85f)
)

private fun simboloDe(ruta: String) = if (ruta == Guias.GREEN_LANTERN) "◉" else "⚡"
