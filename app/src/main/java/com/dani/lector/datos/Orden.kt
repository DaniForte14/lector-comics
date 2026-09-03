package com.dani.lector.datos

/**
 * Como se ordenan los comics dentro de una carpeta.
 *
 * El nombre corto es el que se enseña en la fila de "Cómics", que tiene sitio
 * para tres o cuatro letras y no para "por numero de grapa".
 */
enum class Orden(val rotulo: String) {
    NUMERO("Nº"),
    NOMBRE("Nombre"),
    NUEVOS("Recientes")
}

/**
 * Ordenar la lista de una carpeta.
 *
 * PURA Y APARTE DEL ESCANER a proposito: el escaner lee de SAF y no se puede
 * probar aqui; esto es una comparacion y si se rompe, se rompe en silencio —una
 * lista mal ordenada no da ningun error, solo se ve rara—. Con `OrdenTest` al
 * lado, cambiarlo se nota.
 */
object OrdenCarpeta {

    /**
     * Los sin numero van SIEMPRE al final, tambien ordenando por nombre.
     *
     * En una carpeta de serie, lo que no lleva numero son los especiales y los
     * one-shots: mezclados por orden alfabetico se meten entre las grapas y
     * parece que falta algo. Al final se ven como lo que son, un apendice.
     */
    fun de(comics: List<Comic>, orden: Orden): List<Comic> = when (orden) {
        Orden.NUMERO -> comics.sortedWith(
            compareBy({ it.numero ?: Int.MAX_VALUE }, { it.nombre.lowercase() }))

        Orden.NOMBRE -> comics.sortedBy { it.nombre.lowercase() }

        // Descendente: "recientes" quiere decir que lo nuevo va arriba, que es
        // justo para lo que se elige este orden. Y a igualdad de fecha —copiar
        // una carpeta entera de golpe deja a todos con la misma— se cae al
        // numero, que es el orden por defecto: asi una tanda copiada de una vez
        // no sale barajada al azar.
        Orden.NUEVOS -> comics.sortedWith(
            compareByDescending<Comic> { it.cuando }
                .thenBy { it.numero ?: Int.MAX_VALUE }
                .thenBy { it.nombre.lowercase() })
    }
}
