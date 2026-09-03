package com.dani.lector.red

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Las cuatro funciones de org.json que usaba ComicVine, sobre kotlinx.
 *
 * POR QUE ESTO Y NO CLASES @Serializable. La respuesta de Comic Vine es un
 * objeto enorme del que la app usa seis campos, cambia sin avisar y trae campos
 * a null o ausentes segun el volumen. Con clases habria que declarar toda esa
 * forma y **un campo inesperado tira el parseo entero**; leyendo a mano, lo que
 * no esta simplemente no esta, que es como llevaba funcionando con org.json.
 *
 * Y ASI EL CAMBIO A KOTLIN MULTIPLATFORM NO TOCA LA LOGICA. `org.json` es de
 * Android y no existe en iOS, pero manteniendo los mismos nombres —optString,
 * optInt, optJSONArray, optJSONObject— el codigo que interpreta la respuesta se
 * queda igual, letra por letra. Si algo se rompe en el port, no fue aqui.
 *
 * TODAS DEVUELVEN VACIO O NULL EN VEZ DE LANZAR, igual que las `opt` de
 * org.json. Es lo que hace que un volumen sin editorial no reviente la busqueda.
 */

private fun JsonElement.obj(): JsonObject? = this as? JsonObject

/**
 * El lector de JSON de toda la app. Permisivo a proposito: un fichero guardado
 * por una version anterior tiene campos que ya no existen, y eso no puede
 * impedir leer los que si.
 */
val jsonLaxo = Json { ignoreUnknownKeys = true; isLenient = true }

fun JsonObject.optString(clave: String): String =
    (this[clave] as? JsonPrimitive)?.takeIf { it.isString || it.content != "null" }
        ?.content.orEmpty()

fun JsonObject.optInt(clave: String, porDefecto: Int = 0): Int =
    (this[clave] as? JsonPrimitive)?.content?.toIntOrNull() ?: porDefecto

fun JsonObject.optJSONArray(clave: String): JsonArray? = this[clave] as? JsonArray

fun JsonObject.optJSONObject(clave: String): JsonObject? = this[clave] as? JsonObject

fun JsonObject.optLong(clave: String, porDefecto: Long = 0L): Long =
    (this[clave] as? JsonPrimitive)?.content?.toLongOrNull() ?: porDefecto

fun JsonObject.optBoolean(clave: String, porDefecto: Boolean = false): Boolean =
    (this[clave] as? JsonPrimitive)?.content?.toBooleanStrictOrNull() ?: porDefecto

fun JsonObject.has(clave: String): Boolean = containsKey(clave)

fun JsonArray.length(): Int = size

/** El elemento i del array como texto suelto. Para arrays de cadenas. */
fun JsonArray.optString(i: Int): String =
    (this.getOrNull(i) as? JsonPrimitive)?.content.orEmpty()

fun JsonArray.getJSONObject(i: Int): JsonObject =
    this[i].obj() ?: JsonObject(emptyMap())
