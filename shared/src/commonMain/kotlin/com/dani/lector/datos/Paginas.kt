package com.dani.lector.datos

/**
 * Resultado de leer un comic: o las paginas, o el motivo por el que no se ha
 * podido.
 *
 * Vive aqui y no junto a quien abre el ZIP porque **es el contrato, no la
 * fontaneria**: son una lista de nombres o una cadena, y de eso no sabe nada
 * ninguna plataforma. Lo usan el visor, el conversor y el ViewModel.
 *
 * EL MOTIVO ES TEXTO Y NO UN CODIGO DE ERROR, a proposito: lo unico que se hace
 * con el es enseñarlo, y cada fallo tiene su explicacion propia —"este CBR usa
 * RAR5 y no se ha podido convertir", "el archivo no tiene imagenes dentro"—.
 * Una enumeracion obligaria a traducir el codigo a frase en la pantalla, que es
 * donde peor se mantiene.
 */
sealed class Paginas {
    data class Ok(val nombres: List<String>) : Paginas()
    data class Error(val motivo: String) : Paginas()
}
