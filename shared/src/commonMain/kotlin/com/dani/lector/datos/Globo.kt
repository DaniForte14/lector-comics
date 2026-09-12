package com.dani.lector.datos

/** Un punto, en pixeles de la pagina analizada. */
data class Punto(val x: Int, val y: Int)

/**
 * Un globo: el recuadro que lo envuelve y su contorno.
 *
 * EL CONTORNO ES LO QUE DA LA FORMA EXACTA, como en Play Books: al ampliar se
 * recorta el globo por su silueta y no por el recuadro, que se llevaria las
 * esquinas del dibujo de alrededor. Es un poligono cerrado —el ultimo punto se
 * une con el primero— con los puntos justos para que se vea curvo: guardar la
 * mascara del relleno pixel a pixel serian decenas de KB por globo para lo
 * mismo.
 *
 * El [recuadro] va aparte aunque se pueda sacar del contorno porque es lo que
 * usan el orden de lectura, la fusion y el encuadre del zoom.
 */
data class Globo(val recuadro: Recuadro, val contorno: List<Punto>)
