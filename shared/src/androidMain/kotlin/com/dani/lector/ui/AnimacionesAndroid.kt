package com.dani.lector.ui

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * ANDROID — Si el movil tiene las animaciones puestas.
 *
 * Quien apaga las animaciones del sistema —por mareos, por bateria o porque le
 * molestan— no espera que una app se las salte por su cuenta.
 *
 * SE LEE UNA VEZ POR PROCESO, y esa cache de mas no es un adorno. El `remember`
 * solo evita releerlo en cada recomposicion DE ESE composable, asi que valia
 * mientras esto lo llamaban dos sitios. Desde que `escalaAlPulsar` lo usa, lo
 * llama **cada carta de la rejilla**: en una carpeta de 68 numeros eso eran 68
 * consultas al proveedor de ajustes del sistema —que son IPC, no un campo en
 * memoria— en el hilo principal, justo mientras se compone la lista.
 *
 * El ajuste no cambia mientras la app esta viva; si alguien lo toca en mitad,
 * se coge al volver a abrirla, que es exactamente lo que ya prometia el
 * comentario de antes.
 *
 * Y con runCatching: un fabricante puede no tener ese ajuste, y quedarse sin
 * animaciones por eso seria peor que el problema.
 */
private var recordado: Boolean? = null

@Composable
actual fun hayAnimaciones(): Boolean {
    val ctx = LocalContext.current
    return remember(ctx) {
        recordado ?: runCatching {
            Settings.Global.getFloat(
                ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
        }.getOrDefault(true).also { recordado = it }
    }
}
