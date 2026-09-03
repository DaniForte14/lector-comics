package com.dani.lector.datos

/**
 * Por cual seguir en esta carpeta.
 *
 * LO QUE CONTESTA es la pregunta que se hace uno al entrar en una serie: "¿por
 * cual iba?". Hasta ahora habia que buscarlo en la rejilla mirando cual no lleva
 * la chapa de leido, y con cuarenta numeros eso es trabajo que la app puede
 * hacer sola.
 *
 * PURA Y CON PRUEBA porque tiene una regla con orden de prioridad, y esa clase
 * de cosa se rompe sin dar ningun error: simplemente te abre el que no era.
 */
object Siguiente {

    /**
     * El comic por el que seguir, o `null` si no queda ninguno.
     *
     * [comics] tiene que venir EN ORDEN DE LECTURA (por numero), no en el orden
     * que el usuario haya elegido para verlos: "sigue por el #7" es una
     * afirmacion sobre la serie, no sobre como esta ordenada la pantalla ahora
     * mismo.
     *
     * El orden de prioridad, y el porque de cada paso:
     *
     *  1. **Lo que tienes a medias, lo mas reciente primero.** Si dejaste el #12
     *     por la mitad, seguir es volver ahi, aunque el #8 este entero sin
     *     empezar. Es lo que significa "seguir".
     *  2. **El primero sin terminar.** Sin nada a medias, el siguiente es el
     *     primero de la lista que no esta leido — el #8 del ejemplo.
     *  3. **Nada.** Con todo leido no hay boton que enseñar, y eso es mejor que
     *     un boton que te devuelve al principio sin avisar.
     */
    fun de(comics: List<Comic>, marcas: Map<String, Marca>): Comic? {
        // A medias: hay marca, no esta terminado, y has pasado de la primera.
        // La condicion `pagina > 0` es la misma que usa "En curso": abrir un
        // comic y salir sin pasar de la portada no es tenerlo empezado.
        val aMedias = comics
            .mapNotNull { c -> marcas[c.uri]?.let { c to it } }
            .filter { (_, m) -> !m.terminado && m.pagina > 0 }
            .maxByOrNull { (_, m) -> m.cuando }
        if (aMedias != null) return aMedias.first

        return comics.firstOrNull { marcas[it.uri]?.terminado != true }
    }
}
