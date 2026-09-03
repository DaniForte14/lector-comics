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
 * Se lee una vez y se recuerda: es un ajuste que no cambia mientras la pantalla
 * esta abierta. Y con runCatching: un fabricante puede no tener ese ajuste, y
 * quedarse sin animaciones por eso seria peor que el problema.
 */
@Composable
actual fun hayAnimaciones(): Boolean {
    val ctx = LocalContext.current
    return remember(ctx) {
        runCatching {
            Settings.Global.getFloat(
                ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
        }.getOrDefault(true)
    }
}
