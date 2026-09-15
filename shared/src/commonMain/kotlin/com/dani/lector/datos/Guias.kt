package com.dani.lector.datos

/**
 * La guia de lectura que toca a una carpeta, para la tarjeta del final del visor.
 *
 * Las guias son dos artefactos de claude.ai escritos a mano: Green Lantern a
 * partir de The Book of Oa (r/Greenlantern) y Barry y Wally sin mas fuente. Son
 * CRITERIO, no datos: nadie los ha cruzado con Comic Vine. Por eso se abren
 * fuera, en el navegador, y la app no saca de ellos ni una cifra.
 *
 * Se enlazan en vez de meterlos dentro porque el 02/09/2026 ya salio de la app
 * un orden de lectura entero que no se usaba (docs/CONTEXTO.md, "La amputacion").
 * Un enlace cuesta una linea y, si se corrige la guia, se ve sin compilar. Si
 * esta vez se usa, entonces se mete de verdad.
 */
object Guias {

    const val GREEN_LANTERN = "https://claude.ai/artifact/Cyu2nwXvVF4ZkFiSWPGWwy"
    const val FLASH = "https://claude.ai/artifact/MhPUgXk14PryDDSnQcpP2n"

    private val porPalabra = listOf("lantern" to GREEN_LANTERN, "flash" to FLASH)

    /**
     * La direccion de la guia para la carpeta [ruta], o `null` si no hay.
     *
     * Busca en la RUTA ENTERA y no solo en el nombre de la carpeta, para que
     * "Green Lantern/Blackest Night" tambien cuente.
     */
    // ponytail: una palabra por guia; "Sinestro" o "Far Sector" en una carpeta
    // suelta no casan. Si las guias entran en la app, por volumenId de SeriesRemotas.
    fun de(ruta: String): String? {
        val r = ruta.lowercase()
        return porPalabra.firstOrNull { (palabra, _) -> palabra in r }?.second
    }
}
