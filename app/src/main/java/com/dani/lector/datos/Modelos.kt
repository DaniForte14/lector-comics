package com.dani.lector.datos

// ─────────────────────────── LA BIBLIOTECA ───────────────────────────

/** Una carpeta de tu arbol. */
data class Carpeta(
    val docId: String,
    val nombre: String,
    /** Ruta desde la raiz: "Daredevil/Daredevil vol.6 (2019)" */
    val ruta: String,
    val subcarpetas: Int,
    val comics: Int
)

/** Un comic dentro de una carpeta. */
data class Comic(
    val uri: String,
    val nombre: String,
    /** Ruta de la carpeta que lo contiene. */
    val carpeta: String,
    /**
     * Id de documento de la carpeta que lo contiene, en el arbol de SAF.
     *
     * La ruta de arriba es para enseñarsela al usuario y para comparar; esta es
     * la que hace falta para CREAR un fichero al lado o para mover el original,
     * porque en SAF no se puede deducir el padre a partir del documento.
     */
    val padreId: String = "",
    /** Numero sacado del nombre. null si no se ha podido. */
    val numero: Int?,
    val esEspecial: Boolean,
    /**
     * Ultima modificacion del fichero segun SAF, para ordenar por "recientes".
     *
     * NO ES LA FECHA EN QUE LO AÑADISTE, porque eso SAF no lo guarda: es la de
     * modificacion. Copiando ficheros al movil casi siempre coinciden —la copia
     * pone la fecha de la copia— pero si algun dia salen en un orden raro, la
     * explicacion esta aqui.
     *
     * Con valor por defecto para que ordenar por fecha no sea un requisito de
     * quien construya un Comic desde otro sitio.
     */
    val cuando: Long = 0L
)
