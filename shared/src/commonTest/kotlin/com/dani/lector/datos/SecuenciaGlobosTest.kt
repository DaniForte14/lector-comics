package com.dani.lector.datos

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `SecuenciaGlobos.paso` decide a donde lleva cada toque con los bocadillos
 * encendidos, y **si se tuerce no da ningun error**: se salta un globo, se
 * queda en la misma pagina para siempre o pasa dos de golpe. Una prueba por
 * regla, con una pagina de tres globos salvo donde la regla va de otra cosa.
 */
class SecuenciaGlobosTest {

    @Test fun `adelante desde la pagina entera va al primer globo`() {
        assertEquals(Paso.EnPagina(0), SecuenciaGlobos.paso(null, 3, adelante = true))
    }

    @Test fun `adelante de un globo va al siguiente`() {
        assertEquals(Paso.EnPagina(1), SecuenciaGlobos.paso(0, 3, adelante = true))
        assertEquals(Paso.EnPagina(2), SecuenciaGlobos.paso(1, 3, adelante = true))
    }

    @Test fun `adelante desde el ultimo globo pasa de pagina`() {
        assertEquals(Paso.PaginaSiguiente, SecuenciaGlobos.paso(2, 3, adelante = true))
    }

    @Test fun `atras de un globo va al anterior`() {
        assertEquals(Paso.EnPagina(1), SecuenciaGlobos.paso(2, 3, adelante = false))
    }

    @Test fun `atras desde el primer globo vuelve a la pagina entera`() {
        assertEquals(Paso.EnPagina(null), SecuenciaGlobos.paso(0, 3, adelante = false))
    }

    @Test fun `atras desde la pagina entera va a la pagina anterior`() {
        assertEquals(Paso.PaginaAnterior, SecuenciaGlobos.paso(null, 3, adelante = false))
    }

    @Test fun `una pagina sin globos pasa de pagina en los dos sentidos`() {
        assertEquals(Paso.PaginaSiguiente, SecuenciaGlobos.paso(null, 0, adelante = true))
        assertEquals(Paso.PaginaAnterior, SecuenciaGlobos.paso(null, 0, adelante = false))
    }

    @Test fun `un globo que ya no existe cuenta como pasado el ultimo`() {
        // La pagina se recalculo con 3 globos mientras se veia el 5.
        assertEquals(Paso.PaginaSiguiente, SecuenciaGlobos.paso(5, 3, adelante = true))
        assertEquals(Paso.EnPagina(2), SecuenciaGlobos.paso(5, 3, adelante = false))
        // El caso justo en el borde: el indice 3 en una pagina de 3.
        assertEquals(Paso.PaginaSiguiente, SecuenciaGlobos.paso(3, 3, adelante = true))
        assertEquals(Paso.EnPagina(2), SecuenciaGlobos.paso(3, 3, adelante = false))
    }

    @Test fun `si al recalcular no queda ningun globo atras se ve la pagina entera`() {
        // Y no la anterior: la que se estaba leyendo aun no se ha visto entera.
        assertEquals(Paso.EnPagina(null), SecuenciaGlobos.paso(2, 0, adelante = false))
        assertEquals(Paso.PaginaSiguiente, SecuenciaGlobos.paso(2, 0, adelante = true))
    }
}
