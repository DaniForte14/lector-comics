package com.dani.lector.datos

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/**
 * iOS — el corte en si. Quien decide QUE se corta es [Recorte], en `commonMain`,
 * y ahi es donde estan las reglas y sus pruebas. Aqui solo se leen pixeles y se
 * copia un trozo, que es lo unico que necesita una imagen de verdad.
 *
 * Es el gemelo de `RecorteAndroid`, y se parte por el mismo sitio: las cuatro
 * reglas que deciden el margen son aritmetica, y sacar pixeles de una imagen no.
 *
 * POR QUE PASA POR COMPOSE Y NO POR CoreGraphics, que era la otra via. El plan
 * escrito decia sacar los pixeles de [ImagenIOS] **antes** de que Skia los
 * envuelva, y se puede: alli hay un buffer RGBA crudo a mano. Pero eso obliga a
 * abrir [ImagenIOS] y a inventar una segunda salida suya, y sobre todo mete mas
 * `cinterop` — que es de donde han salido las tres vueltas de CI de este puerto,
 * siempre por el nombre de algo del sistema.
 *
 * `toPixelMap` es de Compose, vale en las dos plataformas y ya lo usa
 * `ColorPortada.dominante`. **El precio es una copia de mas**: se pinta el
 * recorte en una imagen nueva en vez de recortar el CGImage antes de decodificar.
 * A estas alturas la pagina ya viene reducida por [ImagenIOS] al ancho pedido,
 * asi que la copia es de la miniatura, no de los 2000x3000 originales.
 *
 * Y los pixeles se leen POR FILAS Y POR COLUMNAS, con un buffer reutilizado, no
 * con un `toPixelMap()` de la imagen entera: [Recorte] solo mira los bordes, y
 * copiar la pagina entera a un `IntArray` para mirar veinte filas serian varios
 * MB por pagina y por pasada. Es la misma razon por la que [Recorte.util] pide
 * dos funciones en vez de un array.
 *
 * ESCRITO Y SIN COMPILAR: desde Windows no hay Kotlin/Native. Lo ve por primera
 * vez el runner de macOS del CI.
 */
object RecorteIOS {

    fun aplicar(img: ImageBitmap): ImageBitmap {
        val an = img.width
        val al = img.height

        // Un unico buffer por fila y otro por columna, igual que en Android: sin
        // esto seria un IntArray nuevo por cada borde que se mira.
        val fila = IntArray(an)
        val columna = IntArray(al)

        val r = Recorte.util(an, al,
            { y -> img.toPixelMap(0, y, an, 1, fila); fila },
            { x -> img.toPixelMap(x, 0, 1, al, columna); columna }
        ) ?: return img

        if (r.ancho >= an && r.alto >= al) return img

        return runCatching {
            val recortada = ImageBitmap(r.ancho, r.alto)
            Canvas(recortada).drawImageRect(
                image = img,
                srcOffset = IntOffset(r.izq, r.arriba),
                srcSize = IntSize(r.ancho, r.alto),
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(r.ancho, r.alto),
                paint = Paint()
            )
            recortada
        }.getOrDefault(img)
    }
}
