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

/**
 * Lo contrario de [paraUrl]: %XX vuelve a ser el caracter que era.
 *
 * SUSTITUYE A `android.net.Uri.decode`, que es de Android. Lo usa
 * `Progreso.clave`, o sea **la clave con la que la copia de seguridad
 * reencuentra tus comics al restaurar**: las uris de SAF llegan codificadas y
 * "Green%20Lantern%2001.cbz" tiene que volver a ser "Green Lantern 01.cbz" o la
 * copia no casa con nada y se restaura en silencio... nada.
 *
 * NO CONVIERTE `+` EN ESPACIO, igual que `Uri.decode` y al reves que
 * `URLDecoder`. Un `+` en el nombre de un fichero es un `+`.
 *
 * Un %XX mal formado —"%4" al final, o "%zz"— se deja tal cual en vez de
 * lanzar: un nombre raro no puede tirar la restauracion entera.
 */
fun desdeUrl(s: String): String {
    val bytes = ArrayList<Byte>(s.length)
    var i = 0
    while (i < s.length) {
        val c = s[i]
        val hex = if (c == '%' && i + 2 < s.length)
            s.substring(i + 1, i + 3).toIntOrNull(16) else null
        if (hex != null) {
            bytes.add(hex.toByte())
            i += 3
        } else {
            // El resto va con sus propios bytes UTF-8: puede ser un acento que
            // nunca estuvo codificado.
            c.toString().encodeToByteArray().forEach { bytes.add(it) }
            i++
        }
    }
    return bytes.toByteArray().decodeToString()
}
