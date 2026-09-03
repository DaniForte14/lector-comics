package com.dani.lector.datos

/**
 * Saca el numero de un comic del nombre del fichero.
 *
 * Ya no hace falta adivinar la serie: eso lo dice la carpeta. Solo el numero.
 * Probado contra: "Daredevil 36.cbz", "Daredevil 035.cbz", "Daredevil #28.cbz",
 * "Moon Knight#14.cbz", "Moon Knight nº14.cbz", "Daredevil Vol.7 #01 [x].cbz"
 *
 * El orden de las reglas importa y esta explicado dentro de [numeroDe]: primero
 * la almohadilla, y solo si no hay, el barrido de derecha a izquierda.
 */
object Parser {

    private val RE_PARENT = Regex("""\([^)]*\)""")
    private val RE_CORCH = Regex("""\[[^\]]*\]""")
    private val RE_ESPECIAL = Regex("""(?i)\b(annual|special|king.?size|one.?shot|tpb|omnibus)\b""")
    private val RE_VOL = Regex("""(?i)\bv(?:ol)?\.?\s?\d{1,2}\b""")
    private val RE_ESPACIOS = Regex("""\s+""")
    /** letra pegada a almohadilla o cifra: "Knight#14", "Knight14" */
    private val RE_PEGADO = Regex("""([A-Za-zÀ-ÿ])(#?\d)""")

    val EXTENSIONES = setOf("cbz", "cbr", "zip")

    fun esComic(nombre: String) =
        nombre.substringAfterLast('.', "").lowercase() in EXTENSIONES

    fun numeroDe(nombreFichero: String): Int? {
        var s = nombreFichero.substringBeforeLast('.')
        s = RE_PARENT.replace(s, " ")
        s = RE_CORCH.replace(s, " ")
        s = s.replace('_', ' ')
        s = RE_ESPECIAL.replace(s, " ")
        s = RE_VOL.replace(s, " ")
        s = RE_PEGADO.replace(s, "$1 $2")
        s = RE_ESPACIOS.replace(s, " ").trim()

        val tokens = s.split(" ").filter { it.isNotBlank() }

        // LA ALMOHADILLA MANDA, y va antes que cualquier otra regla.
        //
        // El barrido de abajo va del final hacia el principio, y eso se rompe
        // en cuanto el TITULO del numero acaba en una cifra. Caso real de la
        // biblioteca de Dani (26/08/2026):
        //
        //     "Green Lantern Vol4 #34 - Secret Origin 6 -"  ->  daba 6
        //
        // Ese comic salia colocado entre el #06 y el #07 de la serie, y la
        // carpeta decia que faltaba el 34. Toda la coleccion de "Secret Origin"
        // estaba mal por lo mismo: son seis partes numeradas dentro del titulo.
        //
        // Un "#" delante de una cifra no es ambiguo: quien escribe el nombre
        // esta diciendo cual es el numero de la grapa. Si esta, se hace caso y
        // no se sigue mirando. Se coge el PRIMERO porque el numero de la serie
        // va delante del titulo, no al reves.
        for (t in tokens) {
            if (!t.startsWith("#")) continue
            val n = t.drop(1).takeWhile { it.isDigit() }.toIntOrNull() ?: continue
            if (n in 0..9999) return n
        }

        for (i in tokens.indices.reversed()) {
            val t = tokens[i]
            val digitos = t.filter { it.isDigit() }
            if (digitos.isEmpty()) continue
            // un año suelto no es un numero de grapa
            if (digitos.length == 4 && digitos.toInt() in 1930..2100 && t.all { it.isDigit() })
                continue
            // "Knight" tiene cifras? no. "14a" si. "Amazing" no.
            if (t.count { it.isLetter() } > 2) continue
            val n = digitos.toIntOrNull() ?: continue
            if (n in 0..9999) return n
        }
        return null
    }

    fun esEspecial(nombre: String) = RE_ESPECIAL.containsMatchIn(nombre)

    /** Para comparar sin que tilde, caso o guiones molesten. */
    /**
     * Quita del nombre del fichero el trozo que ya dice la carpeta.
     *
     * Dentro de "Green Lantern Vol. 4", el fichero
     * "Green Lantern Vol4 #00 Secret Origin" se queda en "#00 Secret Origin".
     * Repetir el nombre de la carpeta en cada una de las sesenta cartas es
     * gastar tres lineas de titulo en decir donde estas, que ya lo pone arriba.
     *
     * La comparacion va NORMALIZADA por los dos lados, que es lo que hace que
     * funcione: la carpeta dice "Vol. 4" y el fichero "Vol4", y como cadenas no
     * se parecen. Sin tildes ni signos, "greenlanternvol4" es prefijo de
     * "greenlanternvol400secretorigin" y ya si.
     *
     * Se recorre el original contando SOLO los caracteres que sobreviven a
     * normalizar, para saber por donde cortar en el texto de verdad y no en el
     * normalizado, que es otra cadena.
     *
     * Devuelve el nombre entero si no encaja o si al quitarlo no queda nada:
     * una carta sin titulo es peor que una con el titulo repetido.
     */
    fun sinPrefijoDeCarpeta(nombre: String, carpeta: String): String {
        val objetivo = normalizar(carpeta)
        if (objetivo.isBlank()) return nombre
        var vistos = 0
        for (i in nombre.indices) {
            val n = normChar(nombre[i]) ?: continue
            // En cuanto un caracter no cuadra, esto no es un prefijo y se corta.
            if (n != objetivo[vistos]) return nombre
            vistos++
            if (vistos == objetivo.length) {
                val resto = nombre.drop(i + 1).trimStart(' ', '-', '_', '.', ',')
                return resto.ifBlank { nombre }
            }
        }
        return nombre
    }

    /**
     * El caracter normalizado, o null si normalizar lo tiraria.
     *
     * Existe por RENDIMIENTO, y el motivo merece quedar escrito. La primera
     * version llamaba a [normalizar] —que monta una cadena y pasa una expresion
     * regular— UNA VEZ POR CARACTER del nombre, y ademas otra vez sobre el
     * trozo entero al final. Eso se ejecuta por cada carta de la rejilla y en
     * cada repintado: con sesenta comics en pantalla y haciendo scroll, son
     * miles de expresiones regulares por segundo. Se notaba.
     *
     * Asi es una comparacion de caracteres, sin reservar memoria, y ademas
     * corta en cuanto algo no cuadra en vez de recorrerlo entero.
     */
    private fun normChar(c: Char): Char? {
        val m = when (val b = c.lowercaseChar()) {
            'á' -> 'a'; 'é' -> 'e'; 'í' -> 'i'; 'ó' -> 'o'; 'ú' -> 'u'; 'ü' -> 'u'
            else -> b
        }
        return if (m in 'a'..'z' || m in '0'..'9') m else null
    }

    fun normalizar(s: String): String = s.lowercase()
        .replace("á", "a").replace("é", "e").replace("í", "i")
        .replace("ó", "o").replace("ú", "u").replace("ü", "u")
        .replace(Regex("[^a-z0-9]"), "")
}
