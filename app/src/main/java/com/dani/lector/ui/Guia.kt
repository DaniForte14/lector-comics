package com.dani.lector.ui

import android.annotation.SuppressLint
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Una guia de lectura (ver [com.dani.lector.datos.Guias]) a pantalla completa:
 * el HTML del artefacto, metido en el APK y abierto en un WebView. Abrirla
 * fuera sacaba de la app y pedia la sesion de claude.ai, porque el artefacto es
 * privado (tanda 36). La abren el visor, desde la tarjeta del final, y la
 * carpeta del personaje (tanda 37).
 *
 * UN DIALOG, no una capa encima: es su propia ventana, asi que tapa lo que haya
 * donde se llame sin que la pantalla tenga que envolverse en un Box, y atras lo
 * cierra solo. Al cerrarlo sigues donde estabas.
 *
 * `guias/marcar.js` se mete AL ABRIRLA, no dentro de los HTML: asi siguen siendo
 * copias exactas de los artefactos y se pueden volver a copiar sin perder el
 * tachado. Por eso JavaScript y el almacenamiento del WebView van encendidos;
 * solo se carga lo que va en el APK. Los enlaces de dentro (el post de Reddit)
 * salen al navegador: sin WebViewClient, es lo que hace WebView por defecto.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PantallaGuia(ruta: String, onCerrar: () -> Unit) {
    Dialog(onCerrar, DialogProperties(usePlatformDefaultWidth = false)) {
        AndroidView(
            factory = { ctx ->
                val marcar = ctx.assets.open("guias/marcar.js").bufferedReader().use { it.readText() }
                val html = ctx.assets.open(ruta).bufferedReader().use { it.readText() }
                    .replace("</body>", "<script>$marcar</script></body>")
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    loadDataWithBaseURL("file:///android_asset/guias/", html, "text/html", "utf-8", null)
                }
            },
            modifier = Modifier.fillMaxSize().background(Tinta)
        )
    }
}
