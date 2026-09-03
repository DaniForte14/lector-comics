package com.dani.lector.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale

@Composable
fun Portada(
    uri: String?,
    modifier: Modifier = Modifier,
    cargar: suspend (String) -> ImageBitmap?,
    // Lo que ya esta en memoria, sin esperar. Por que hace falta las dos cosas:
    // [cargar] suspende, y suspender significa saltar a otro hilo y volver, o
    // sea uno o dos fotogramas con la carta en gris AUNQUE la portada estuviera
    // hecha. Al bajar y volver a subir en la biblioteca eso es justo el tiron
    // que se ve. Con esto la portada que ya esta puesta se pinta en el mismo
    // fotograma y [cargar] solo entra cuando de verdad hay que ir a buscarla.
    inmediato: (String) -> ImageBitmap? = { null },
    // OJO: vacio va ANTES de encima. Si fuera el ultimo, la lambda final de
    // todas las llamadas que ya hay se engancharia aqui y las marcas y chapas
    // se pintarian solo cuando NO hay portada, que es justo al reves.
    vacio: @Composable BoxScope.() -> Unit = {},
    encima: @Composable BoxScope.() -> Unit = {}
) {
    val yaEsta = remember(uri) { uri?.let(inmediato) }
    var bmp by remember(uri) { mutableStateOf(yaEsta) }
    // Hace falta saber si ya se ha INTENTADO, no solo si hay bitmap: mientras
    // carga tambien es null, y sin esto la carta diria "no se puede abrir"
    // durante un instante en cada portada que tarde.
    var intentado by remember(uri) { mutableStateOf(yaEsta != null) }
    LaunchedEffect(uri) {
        // Si ya estaba en memoria no se vuelve a pedir: seria una corrutina y
        // un salto de hilo por cada carta que entra en pantalla, para nada.
        if (yaEsta == null) {
            bmp = uri?.let { cargar(it) }
            intentado = true
        }
    }
    Box(modifier.background(Panel)) {
        val b = bmp
        if (b != null) {
            // Ya llega como ImageBitmap: la conversion se hace UNA vez, al
            // decodificar en Miniaturas, y no en cada repintado de cada carta.
            Image(b, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else if (intentado) vacio()
        encima()
    }
}
