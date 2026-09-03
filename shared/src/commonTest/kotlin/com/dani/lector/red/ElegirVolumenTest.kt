package com.dani.lector.red

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.Test

/**
 * La regla que impide que se cuele basura de Comic Vine.
 *
 * Los casos NO son inventados: salen de las respuestas reales recogidas el
 * 25/08/2026 y descritas en `docs/CONTEXTO.md` (seccion de Comic Vine). Los
 * candidatos van recortados a lo que decide —nombre, año, numeros, editorial—
 * porque es lo unico que mira la funcion.
 *
 * POR QUE ESTA PRUEBA VALE LO QUE VALE: `elegirVolumen` es la unica regla que
 * queda entre la fuente y la app desde que se fue el modelo. Si se relaja, no
 * da ningun error: simplemente empareja la serie con otra y las cifras de la
 * pantalla pasan a ser de otro comic.
 */
class ElegirVolumenTest {

    private fun vol(nombre: String, anio: Int?, numeros: Int, editorial: String? = "DC Comics") =
        VolumenRemoto(nombre, anio, numeros, editorial)

    // Regla 1. De los 17 resultados de "Absolute Batman", catorce son tomos
    // recopilatorios y ediciones sueltas cuyo nombre NO es el buscado.
    @Test fun `el nombre tiene que ser exactamente el mismo`() {
        val candidatos = listOf(
            vol("Absolute Batman: The Absolute Universe", 2025, 1),
            vol("Absolute Batman Deluxe Edition", 2025, 2),
            vol("Absolute Batman", 2024, 12)
        )
        assertEquals(2024, elegirVolumen(candidatos, "Absolute Batman", 2024)?.anio)
    }

    // El caso que motivo la regla: "Green Lantern" no puede emparejar con
    // "Green Lantern Corps Quarterly", que es otra serie.
    @Test fun `un nombre que empieza igual no vale`() {
        val candidatos = listOf(vol("Green Lantern Corps Quarterly", 2005, 9))
        assertNull(elegirVolumen(candidatos, "Green Lantern", 2005))
    }

    @Test fun `sin ningun nombre exacto devuelve null`() {
        assertNull(elegirVolumen(listOf(vol("Batman", 2016, 145)), "Absolute Batman", 2024))
        assertNull(elegirVolumen(emptyList(), "Absolute Batman", 2024))
    }

    // Regla 2. Entre los 20 primeros de `query=Green Lantern` hay ediciones de
    // ECC, Panini, Planeta DeAgostini, Editorial Televisa y TM-Semic con el
    // nombre exacto. Nueve candidatos son de DC: gana DC y caen las
    // traducciones. Lo mismo tiro la edicion francesa de Urban Comics.
    @Test fun `solo la editorial mayoritaria`() {
        val candidatos = listOf(
            vol("Green Lantern", 2021, 400, "ECC Ediciones"),
            vol("Green Lantern", 2021, 300, "Panini Comics"),
            vol("Green Lantern", 2021, 12, "DC Comics"),
            vol("Green Lantern", 2005, 67, "DC Comics"),
            vol("Green Lantern", 1990, 181, "DC Comics")
        )
        val elegido = elegirVolumen(candidatos, "Green Lantern", 2021)
        assertEquals("DC Comics", elegido?.editorial)
        assertEquals(12, elegido?.numeros)
    }

    // Si NADIE trae editorial no se puede descartar por ahi, y quedarse sin
    // candidatos seria peor que no filtrar.
    @Test fun `sin editorial no se filtra por editorial`() {
        val candidatos = listOf(vol("Green Lantern", 2021, 12, null))
        assertEquals(12, elegirVolumen(candidatos, "Green Lantern", 2021)?.numeros)
    }

    // Regla 3. El año exacto manda sobre el de margen aunque el de margen
    // tenga mas numeros: el margen es un apaño para el desfase de fechado,
    // no un empate.
    @Test fun `el anio exacto gana al del margen`() {
        val candidatos = listOf(
            vol("Green Lantern", 2022, 99),
            vol("Green Lantern", 2021, 12)
        )
        assertEquals(2021, elegirVolumen(candidatos, "Green Lantern", 2021)?.anio)
    }

    // Comic Vine fecha por portada y las wikis por publicacion: a fin de año
    // no coinciden y un año de diferencia sigue siendo la misma serie.
    @Test fun `un anio de margen si no hay exacto`() {
        val candidatos = listOf(vol("Green Lantern", 2023, 37))
        assertEquals(2023, elegirVolumen(candidatos, "Green Lantern", 2024)?.anio)
    }

    @Test fun `dos anios de diferencia ya no vale`() {
        val candidatos = listOf(vol("Green Lantern", 2023, 37))
        assertNull(elegirVolumen(candidatos, "Green Lantern", 2021))
    }

    // Un candidato sin año no puede colarse por el margen: no se sabe si cae
    // dentro o fuera, y aqui solo entra lo que se sabe.
    @Test fun `un candidato sin anio no entra por el margen`() {
        val candidatos = listOf(vol("Green Lantern", null, 999))
        assertNull(elegirVolumen(candidatos, "Green Lantern", 2021))
    }

    // Regla 4. Entre la serie y un especial con el mismo nombre y año,
    // queremos la serie.
    @Test fun `a igualdad gana la que mas numeros tiene`() {
        val candidatos = listOf(
            vol("Absolute Batman", 2024, 1),
            vol("Absolute Batman", 2024, 12)
        )
        assertEquals(12, elegirVolumen(candidatos, "Absolute Batman", 2024)?.numeros)
    }

    // Cuando la wiki no dice el año, la unica preferencia que queda es la
    // serie larga sobre el especial.
    @Test fun `sin anio buscado gana la que mas numeros tiene`() {
        val candidatos = listOf(
            vol("Green Lantern", 2005, 67),
            vol("Green Lantern", 1990, 181)
        )
        assertEquals(181, elegirVolumen(candidatos, "Green Lantern", null)?.numeros)
    }

    // La normalizacion quita mayusculas, acentos y todo lo que no sea letra o
    // numero. Es lo que hace que los dos puntos y el guion no separen series
    // que son la misma.
    @Test fun `mayusculas acentos y puntuacion no separan`() {
        val candidatos = listOf(vol("Green Lantern: Rebirth", 2004, 6))
        assertEquals(6, elegirVolumen(candidatos, "green lantern rebirth", 2004)?.numeros)
        assertEquals(6, elegirVolumen(candidatos, "GREEN-LANTERN REBIRTH", 2004)?.numeros)
    }
}
