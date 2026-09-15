package com.dani.lector.datos

/**
 * La guia de lectura que toca a una carpeta, para la tarjeta del final del visor.
 *
 * Las guias son dos artefactos de claude.ai escritos a mano: Green Lantern a
 * partir de The Book of Oa (r/Greenlantern) y Barry y Wally sin mas fuente. Son
 * CRITERIO, no datos: nadie los ha cruzado con Comic Vine. Por eso se enseñan
 * tal cual, como paginas, y la app no saca de ellas ni una cifra.
 *
 * Van COPIADAS en el APK (`app/src/main/assets/guias/`), no enlazadas: el
 * enlace sacaba de la app y el artefacto, que es privado, pedia la sesion de
 * claude.ai (tanda 36). Lo que se pierde: si se corrige un artefacto, hay que
 * volver a copiarlo (su `index.html`) y compilar. Los originales:
 * - Green Lantern: https://claude.ai/artifact/Cyu2nwXvVF4ZkFiSWPGWwy
 * - Barry y Wally: https://claude.ai/artifact/MhPUgXk14PryDDSnQcpP2n
 */
object Guias {

    /** Rutas dentro de `assets`: el visor les pone delante `file:///android_asset/`. */
    const val GREEN_LANTERN = "guias/green-lantern.html"
    const val FLASH = "guias/barry-y-wally.html"

    private val porPalabra = listOf("lantern" to GREEN_LANTERN, "flash" to FLASH)

    /**
     * La guia para la carpeta [ruta], o `null` si no hay.
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
