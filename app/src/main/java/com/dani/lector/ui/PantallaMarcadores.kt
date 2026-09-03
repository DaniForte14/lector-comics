package com.dani.lector.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dani.lector.VistaModelo
import com.dani.lector.datos.*

// La pantalla de marcapaginas. Salio de Pantallas.kt al partirlo por pantallas.

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
