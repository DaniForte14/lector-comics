package com.dani.lector.datos

import com.dani.lector.red.NumeroRemoto

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

// ─────────────────────────── LA LECTURA ───────────────────────────

/**
 * Por donde vas en un numero concreto.
 *
 * Vivia dentro de Progreso.kt, que necesita el Context de Android para saber
 * donde escribir. La marca en si no sabe nada de Android —son tres numeros y
 * dos cuentas— y la usan Siguiente, EstadoSerie y Estadisticas, que si son
 * comunes. Por eso se muda aqui: quien la guarda es de cada plataforma, lo que
 * guarda es de los dos.
 */
data class Marca(val pagina: Int, val paginas: Int, val cuando: Long) {
    val terminado get() = paginas > 0 && pagina >= paginas - 1
    val porcentaje get() = if (paginas <= 1) 0 else (pagina * 100) / (paginas - 1)
}

/**
 * Lo que leiste de un comic en un dia concreto.
 *
 * Mismo caso que [Marca]: vivia en `Sesiones.kt`, que necesita el Context para
 * saber donde escribir. El dato en si no sabe de Android, y lo usa `Calendario`,
 * que es comun.
 */
data class Sesion(
    val uri: String,
    /** "aaaa-mm-dd", en la zona de la app. Ordena bien como texto. */
    val dia: String,
    /** Por que pagina ibas al empezar ese dia. */
    val desde: Int,
    /** La mas lejos que llegaste ese dia. */
    val hasta: Int,
    /** Paginas nuevas vistas ese dia. */
    val paginas: Int,
    val cuando: Long
)

// ─────────────────────────── LAS SERIES SEGUIDAS ───────────────────────────

/**
 * Lo que sabemos de una serie tuya vinculada a Comic Vine.
 *
 * ESTABA ANIDADA DENTRO DE `SeriesRemotas`, que necesita el Context para saber
 * donde guardar el JSON. La ficha en si son datos, y la usa `Novedades.agenda`,
 * que es comun. Se saca a primer nivel porque Kotlin no deja poner un alias de
 * tipo dentro de una clase: el nombre pasa de `Ficha` a `Ficha`.
 */
/**
 * @param ruta       la carpeta tuya, tal como la nombra el escaner
 * @param volumenId  el id en Comic Vine
 * @param nombre     como se llama alli, para que puedas ver si acerto
 * @param cuando     cuando se pidieron los numeros, en milisegundos
 */
data class Ficha(
    val ruta: String,
    val volumenId: String,
    val nombre: String,
    val anio: Int?,
    val numeros: List<NumeroRemoto>,
    val cuando: Long,
    /** Si quieres que la app te avise cuando salga un numero nuevo. */
    val seguida: Boolean = false,
    /**
     * Etiquetas de las que YA se ha avisado, o que se dan por vistas.
     *
     * Se guarda aparte de [numeros] por el mismo motivo que [volumenId] se
     * guarda aparte de ellos: son cosas distintas con vidas distintas. Los
     * numeros son un dato que se vuelve a pedir entero cada vez; esto es la
     * memoria de lo que ya te hemos contado, y perderla significaria
     * repetirte sesenta avisos.
     *
     * Al empezar a seguir una serie se rellena con TODO lo que hay en ese
     * momento (ver [Novedades.etiquetasDe]). Esa es la linea de salida.
     */
    val avisados: Set<String> = emptySet()
)
