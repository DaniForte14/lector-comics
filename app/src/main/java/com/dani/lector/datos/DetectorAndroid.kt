package com.dani.lector.datos

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * ANDROID — El [DetectorTexto] de Android: ML Kit, empaquetado en el APK.
 *
 * De lo que devuelve solo se usan las cajas de cada LINEA, no las de bloque ni
 * el texto: un bloque de ML Kit puede juntar dos globos que estan cerca, y
 * juntar o separar es justo lo que decide [Bocadillos]. Darle lo mas fino que
 * hay le deja decidir a el.
 *
 * `boundingBox` es un `android.graphics.Rect`, con `right` y `bottom`
 * excluyentes igual que [Recuadro]: se copia campo a campo, sin sumar ni
 * restar nada.
 */
class DetectorAndroid : DetectorTexto {

    // Un solo cliente para toda la app, y creado la primera vez que se usa:
    // crearlo carga el modelo, asi que por pagina seria pagar esa carga en cada
    // una, y en el arranque se pagaria aunque nadie encendiera los bocadillos.
    private val cliente by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    override suspend fun lineas(pagina: ImageBitmap): List<Recuadro> = try {
        val imagen = InputImage.fromBitmap(pagina.asAndroidBitmap(), 0)
        // suspendCancellableCoroutine a mano y no kotlinx-coroutines-play-services:
        // es una dependencia entera para cuatro lineas. Los oyentes corren en el
        // hilo principal, pero la corrutina sigue en el suyo al reanudarse.
        val texto = suspendCancellableCoroutine { cont ->
            cliente.process(imagen)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
        texto.textBlocks
            .flatMap { bloque -> bloque.lines.mapNotNull { it.boundingBox } }
            .map { Recuadro(it.left, it.top, it.right, it.bottom) }
    } catch (e: CancellationException) {
        // Pasar de pagina cancela la corrutina: eso no es un fallo del OCR y no
        // se puede tragar, o la corrutina seguiria viva creyendose terminada.
        throw e
    } catch (e: Throwable) {
        // Throwable y no Exception: un OutOfMemoryError no es una Exception, y
        // ya tiro la app una vez (ver ComicZip). El contrato manda devolver
        // vacio; el rastro dice por que, que para eso es una sonda.
        Rastro.apunta("  OCR falla: $e")
        emptyList()
    }
}
