package com.dani.lector.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled

/**
 * iOS — Si el iPad tiene las animaciones puestas.
 *
 * El equivalente de `ANIMATOR_DURATION_SCALE` en Apple es
 * **Reducir movimiento**, en Accesibilidad. Va al reves —dice si hay que
 * REDUCIR— asi que se niega.
 *
 * Igual que en Android: se lee una vez y se recuerda. Cambiarlo obliga a salir
 * a los Ajustes del sistema, y al volver la pantalla se recompone entera.
 *
 * ESCRITO Y SIN PROBAR EN UN IPAD. Compilarlo lo compila el CI; que el ajuste se
 * lea de verdad no lo sabe nadie hasta que haya app.
 */
@Composable
actual fun hayAnimaciones(): Boolean = remember { !UIAccessibilityIsReduceMotionEnabled() }
