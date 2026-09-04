package com.dani.lector.datos

import android.app.Activity
import android.os.Handler
import android.os.HandlerThread
import android.view.FrameMetrics

/**
 * ANDROID — Cuenta los fotogramas que llegan tarde.
 *
 * POR QUE EXISTE, y es la misma historia que la de [Rastro]. Dani dice que la
 * app "va con lag". Se sospecho de SAF, del hilo principal, de las cuentas por
 * subcarpeta, de las portadas y del trabajo diario de WorkManager: **cinco
 * sospechas y las cinco falsas**, todas descartadas con numeros del rastro.
 *
 * El problema es que el rastro cuenta SUCESOS —cuanto tarda leer una carpeta,
 * hacer el indice, sacar una portada— y **un tiron no es un suceso**: es un
 * fotograma que no llega a tiempo. Se estaban midiendo indicios en vez de la
 * queja. Esto mide la queja.
 *
 * COMO. `addOnFrameMetricsAvailableListener` da el tiempo real de cada
 * fotograma que el sistema ha pintado de verdad. **No es un contador de FPS de
 * los que se reenganchan al Choreographer**: aquellos fuerzan un fotograma cada
 * vsync, o sea que mantienen la app dibujando sin parar y gastan bateria —y
 * encima cambian justo lo que quieren medir. Este solo habla cuando ya se ha
 * pintado algo.
 *
 * El oyente corre en un hilo aparte, que es lo que exige la API: apuntar en el
 * rastro desde el hilo principal por cada fotograma seria el colmo.
 */
object Fluidez {

    /** Dos fotogramas a 60 Hz. A partir de ahi el ojo lo ve como un salto. */
    private const val LENTO_MS = 32L

    /** Cada cuantos fotogramas se resume. 300 son unos cinco segundos a 60 Hz. */
    private const val CADA = 300

    private var puesto = false
    private var vistos = 0
    private var lentos = 0
    private var peor = 0L

    fun vigilar(actividad: Activity) {
        // Una sola vez por proceso: la Activity se recrea al girar la pantalla y
        // dos oyentes contarian cada fotograma dos veces.
        if (puesto) return
        puesto = true

        val app = actividad.applicationContext
        val hilo = HandlerThread("fluidez").apply { start() }

        actividad.window.addOnFrameMetricsAvailableListener({ _, metricas, _ ->
            // TOTAL_DURATION y no DRAW_DURATION: es lo que tarda el fotograma
            // ENTERO —entrada, medida, composicion, dibujo y entrega—, que es lo
            // que se nota. Quedarse con una fase sola diria que todo va bien
            // mientras el tiron esta en la de al lado.
            val ms = metricas.getMetric(FrameMetrics.TOTAL_DURATION) / 1_000_000L

            vistos++
            if (ms >= LENTO_MS) {
                lentos++
                if (ms > peor) peor = ms
            }
            if (vistos >= CADA) {
                // Solo se apunta si ha habido alguno malo. Una linea cada cinco
                // segundos diciendo que todo va bien es justo lo que hace que
                // nadie lea el rastro cuando pasa algo.
                if (lentos > 0) Rastro.apunta(app,
                    "  fluidez: $lentos de $vistos fotogramas por encima de " +
                    "$LENTO_MS ms, el peor $peor ms")
                vistos = 0
                lentos = 0
                peor = 0
            }
        }, Handler(hilo.looper))
    }
}
