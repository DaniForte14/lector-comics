package com.dani.lector.datos

import kotlin.math.abs

/**
 * Casa tus carpetas con las series del TODO.
 *
 * El problema de fondo: tus carpetas dicen "vol.6" y Comic Vine dice el año.
 * Nadie publica esa equivalencia como dato duro, asi que hay dos caminos:
 *
 *  - LA WIKI (fiable): Marvel y DC tienen paginas tituladas "Daredevil Vol 6".
 *    Si la respuesta llega, manda ella. La trae [com.dani.lector.red.Wiki].
 *  - DEDUCIRLO (de emergencia): ordenar por año las series que se llaman igual
 *    y contar. Funciona a menudo, pero se desplaza en cuanto la base de datos
 *    mete una reedicion, asi que sale con confianza MEDIA para que lo mires tu.
 */
object Vinculador {

    private val RE_VOL = Regex("""(?i)\bv(?:ol)?\.?\s*(\d{1,2})\b""")
    private val RE_ANIO = Regex("""\b(19|20)\d{2}\b""")

    data class Propuesta(
        val serie: Serie,
        val carpeta: Carpeta,
        val motivo: String,
        /** ALTA se aplica sola; MEDIA se sugiere y la confirmas tu. */
        val confianza: String
    )

    /** Lo que se puede sacar del nombre de una carpeta. */
    private data class Pista(val base: String, val volumen: Int?, val anio: Int?)

    private fun leer(nombre: String): Pista {
        val vol = RE_VOL.find(nombre)?.groupValues?.get(1)?.toIntOrNull()
        val anio = RE_ANIO.find(nombre)?.value?.toIntOrNull()
        var base = RE_VOL.replace(nombre, " ")
        base = Regex("""\([^)]*\)""").replace(base, " ")
        base = RE_ANIO.replace(base, " ")
        return Pista(Parser.normalizar(base), vol, anio)
    }

    /**
     * Para que series merece la pena preguntar a la wiki.
     *
     * Solo las que aparecen en alguna carpeta con "vol.N" en el nombre: si tus
     * carpetas llevan el año, la wiki no aporta nada y son dos peticiones de red
     * por serie que no hay que gastar.
     */
    fun seriesConVolumen(lista: Lista, carpetas: List<Carpeta>): List<String> {
        val bases = carpetas.map { leer(it.nombre) }
            .filter { it.volumen != null && it.anio == null }
            .map { it.base }
            .toSet()
        if (bases.isEmpty()) return emptyList()
        return lista.series.map { it.nombre }
            .distinctBy { Parser.normalizar(it) }
            .filter { Parser.normalizar(it) in bases }
    }

    /**
     * Numero de volumen de cada serie, deducido del año. Solo se usa cuando la
     * wiki no ha contestado.
     *
     * OJO: la base de datos lista muchas mas series con el mismo nombre de las
     * que cuentan para la numeracion oficial. Reediciones, recopilatorios y
     * ediciones extranjeras se llaman igual, y al contarlas TODAS los ordinales
     * se desplazan: el vol.6 de Daredevil salia como 2006 en vez de 2019.
     *
     * Por eso se filtra antes:
     *  - solo la editorial mayoritaria del grupo (fuera ediciones ajenas)
     *  - un volumen por año (se queda el que mas numeros tiene)
     *  - fuera los de un solo numero, que suelen ser especiales sueltos
     */
    private fun ordinales(series: List<Serie>): Map<String, Int> {
        val out = mutableMapOf<String, Int>()
        series.groupBy { Parser.normalizar(it.nombre) }.forEach { (_, grupo) ->
            val editorialPrincipal = grupo.mapNotNull { it.editorial }
                .groupingBy { it }.eachCount()
                .maxByOrNull { it.value }?.key

            val limpio = grupo
                .filter { editorialPrincipal == null || it.editorial == editorialPrincipal }
                .filter { it.numeros > 1 }
                .groupBy { it.anio ?: 0 }
                .mapNotNull { (_, mismoAnio) -> mismoAnio.maxByOrNull { it.numeros } }
                .sortedBy { it.anio ?: 9999 }

            limpio.forEachIndexed { i, s -> out[s.id] = i + 1 }
        }
        return out
    }

    /**
     * @param volumenes lo que dice la wiki: nombre de serie normalizado -> (volumen -> año).
     *        Si viene vacio, el vinculador funciona como siempre, deduciendo.
     */
    fun proponer(
        lista: Lista,
        carpetas: List<Carpeta>,
        volumenes: Map<String, Map<Int, Int>> = emptyMap()
    ): List<Propuesta> {
        val ord = ordinales(lista.series)
        val out = mutableListOf<Propuesta>()
        val usadas = mutableSetOf<String>()

        for (carp in carpetas) {
            val p = leer(carp.nombre)
            if (p.base.isBlank()) continue

            // el nombre tiene que encajar en algun sentido
            val candidatas = lista.series.filter { s ->
                val n = Parser.normalizar(s.nombre)
                n == p.base || n.contains(p.base) || p.base.contains(n)
            }
            if (candidatas.isEmpty()) continue

            // 1. año exacto: es la senal mas fuerte
            val porAnio = candidatas.filter { it.anio != null && it.anio == p.anio }

            // 2. lo que dice la wiki para ese volumen.
            //    Se exige nombre identico: si no, "Daredevil Vol 6" podria caer en
            //    "Daredevil: Black Armor" solo por coincidir el año.
            //    Y se admite un año de margen porque Comic Vine fecha por portada
            //    y las wikis por publicacion, y a fin de año no coinciden.
            val anioWiki = p.volumen?.let { volumenes[p.base]?.get(it) }
            val porWiki = if (anioWiki == null) emptyList() else candidatas.filter { s ->
                val a = s.anio ?: return@filter false
                Parser.normalizar(s.nombre) == p.base && abs(a - anioWiki) <= 1
            }

            // 3. numero de volumen deducido contando series por año
            val porVol = candidatas.filter { p.volumen != null && ord[it.id] == p.volumen }

            // 4. nombre identico y unica candidata
            val exactas = candidatas.filter { Parser.normalizar(it.nombre) == p.base }

            val (elegida, motivo, confianza) = when {
                porWiki.size == 1 && porAnio.size == 1 && porWiki[0].id == porAnio[0].id ->
                    Triple(porWiki[0], "la wiki da vol.${p.volumen} = $anioWiki y el año encaja", "ALTA")
                porAnio.size == 1 ->
                    Triple(porAnio[0], "año ${p.anio}", "ALTA")
                // La wiki lo dice literalmente, no es una deduccion: confianza alta.
                porWiki.size == 1 ->
                    Triple(porWiki[0], "la wiki dice que vol.${p.volumen} es de $anioWiki", "ALTA")
                // Sin wiki solo queda contar, y contar falla con las reediciones.
                porVol.size == 1 ->
                    Triple(porVol[0], "vol.${p.volumen} sería ${porVol[0].anio} (deducido)", "MEDIA")
                exactas.size == 1 ->
                    Triple(exactas[0], "único con ese nombre", "MEDIA")
                candidatas.size == 1 ->
                    Triple(candidatas[0], "única candidata", "MEDIA")
                else -> Triple(null, "", "")
            }

            if (elegida != null && elegida.id !in usadas) {
                usadas.add(elegida.id)
                out.add(Propuesta(elegida, carp, motivo, confianza))
            }
        }
        return out.sortedBy { if (it.confianza == "ALTA") 0 else 1 }
    }
}
