package com.dani.lector.red

/**
 * Codificar un texto para meterlo en una URL. `URLEncoder` es de la JVM.
 *
 * SE DEJA PASAR SOLO LO "SIN RESERVAR" de RFC 3986 —letras ASCII, digitos y
 * `-_.~`— y todo lo demas va en %XX sobre sus bytes UTF-8.
 *
 * EL ESPACIO SALE COMO %20 Y NO COMO +, y esto no es un detalle. `URLEncoder`
 * es codificacion de FORMULARIO y ahi el espacio es `+`. En `/volumes/?filter=`
 * se sabia que Comic Vine acepta `+`, porque la app llevaba asi desde siempre;
 * en `/search/?query=` NO se sabe, porque todas las pruebas se hicieron desde el
 * navegador, que manda %20. Si alli el `+` no valiera, se estaria buscando la
 * cadena literal "Green+Lantern" y no encontraria nada nunca, sin dar un error.
 * %20 vale en los dos sitios y es lo unico comprobado.
 *
 * Va suelta y no dentro de ComicVine para poder probarla: es la clase de codigo
 * que se equivoca sin dar la cara.
 */
fun paraUrl(s: String): String = buildString {
    for (b in s.encodeToByteArray()) {
        val n = b.toInt() and 0xFF
        val c = n.toChar()
        // El `n < 128` es la guarda que importa: un byte de un caracter UTF-8
        // multibyte puede parecer una letra si se mira como Char suelto.
        if (n < 128 && (c.isLetterOrDigit() || c in "-_.~")) append(c)
        else append('%').append(n.toString(16).uppercase().padStart(2, '0'))
    }
}
