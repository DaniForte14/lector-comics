package com.dani.lector.datos

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.Test

class GuiasTest {

    @Test fun `green lantern en cualquier nivel de la ruta`() {
        assertEquals(Guias.GREEN_LANTERN, Guias.de("Green Lantern/Green Lantern Corps Recharge"))
        assertEquals(Guias.GREEN_LANTERN, Guias.de("DC/Green Lantern/Blackest Night"))
    }

    @Test fun `red lanterns tambien es de la guia de green lantern`() {
        assertEquals(Guias.GREEN_LANTERN, Guias.de("Red Lanterns (2011)"))
    }

    @Test fun `sin distinguir mayusculas`() {
        assertEquals(Guias.GREEN_LANTERN, Guias.de("green LANTERN Vol4"))
    }

    @Test fun `flash y flashpoint van a la de barry y wally`() {
        assertEquals(Guias.FLASH, Guias.de("The Flash vol.2 (1987)"))
        assertEquals(Guias.FLASH, Guias.de("Flashpoint"))
    }

    @Test fun `lo demas no tiene guia`() {
        assertNull(Guias.de("Daredevil/Daredevil vol.6 (2019)"))
        assertNull(Guias.de(""))
    }
}
