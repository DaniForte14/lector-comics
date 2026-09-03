package com.dani.lector.datos

/** Que hay dentro de un fichero de comic, mirando sus primeros bytes. */
enum class Formato { ZIP, RAR4, RAR5, DESCONOCIDO }

/**
 * Detecta el formato por la firma, no por la extension.
 *
 * Distinguir RAR4 de RAR5 no es un capricho: junrar lee RAR4 y NO lee RAR5, y
 * hasta ahora la app solo miraba si ponia "Rar!". Resultado: los dos casos
 * daban el mismo error vago —"CBR vacio o comprimido con RAR5"— y no habia
 * forma de saber si el fichero estaba roto o si simplemente era del formato
 * que no se sabe leer.
 *
 * Las firmas son:
 *   RAR4   52 61 72 21 1A 07 00
 *   RAR5   52 61 72 21 1A 07 01 00
 * O sea, los siete primeros bytes son iguales salvo el septimo: 0 para RAR4 y
 * 1 para RAR5. Ahi se decide todo.
 */
object Formatos {

    fun de(cabecera: ByteArray): Formato {
        if (cabecera.size >= 2 &&
            cabecera[0] == 0x50.toByte() && cabecera[1] == 0x4B.toByte()) return Formato.ZIP

        if (cabecera.size >= 7 &&
            cabecera[0] == 0x52.toByte() &&   // R
            cabecera[1] == 0x61.toByte() &&   // a
            cabecera[2] == 0x72.toByte() &&   // r
            cabecera[3] == 0x21.toByte() &&   // !
            cabecera[4] == 0x1A.toByte() &&
            cabecera[5] == 0x07.toByte()
        ) {
            return when {
                cabecera[6] == 0x00.toByte() -> Formato.RAR4
                cabecera[6] == 0x01.toByte() -> Formato.RAR5
                else -> Formato.DESCONOCIDO
            }
        }
        return Formato.DESCONOCIDO
    }

    /** Cuantos bytes hay que leer para decidir. */
    const val BYTES = 8
}
