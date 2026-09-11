package com.dani.lector.datos

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Encontrar las lineas de texto de una pagina. Es lo UNICO de los bocadillos
 * que cambia entre Android e iOS.
 *
 * Detras hay un OCR del sistema —ML Kit en Android, `Vision` en iOS— y de el
 * solo se usan las CAJAS, no el texto leido: saber que dice el globo no hace
 * falta para ampliarlo. Que es un globo, que contorno tiene y en que orden se
 * lee lo decide [Bocadillos], en comun y con pruebas. El OCR no sabe lo que es
 * un globo; solo sabe donde hay letras.
 *
 * Detras de una interfaz, como todo lo externo del proyecto: si ML Kit no
 * pilla la rotulacion a mano de un comic, se cambia por un modelo entrenado
 * con comics tocando la implementacion y nada mas. Ver `docs/DISENO.md` §24.
 */
interface DetectorTexto {

    /**
     * Las lineas de texto de [pagina], en pixeles de ESA imagen.
     *
     * Vacia si no hay texto y tambien si el OCR falla: una pagina sin globos se
     * lee entera, que es lo mismo que pasaria sin esta funcion. Un fallo aqui
     * no puede dejar el lector sin poder pasar de pagina.
     */
    suspend fun lineas(pagina: ImageBitmap): List<Recuadro>
}
