package com.dani.lector.datos

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFNumberCreate
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFNumberIntType
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGContextDrawImage
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.CGRectMake
// LAS CONSTANTES DE CoreGraphics NO SON CONSTANTES SUELTAS en Kotlin/Native:
// van dentro de su enumeracion y hay que pedirle el `.value`, que es el UInt que
// espera la API de C. Es la segunda vuelta de CI que se va en algo asi —la otra
// fue un metodo de category sin importar—; lo dificil compilo las dos veces.
import platform.CoreGraphics.CGImageAlphaInfo
import platform.ImageIO.CGImageSourceCreateThumbnailAtIndex
import platform.ImageIO.CGImageSourceCreateWithData
import platform.ImageIO.kCGImageSourceCreateThumbnailFromImageAlways
import platform.ImageIO.kCGImageSourceThumbnailMaxPixelSize
import platform.posix.free
import platform.posix.malloc
import platform.posix.memcpy

/**
 * iOS — Convertir los bytes de una pagina en una imagen, **ya reducida**.
 *
 * ESTO ES EL `inSampleSize` DE ANDROID, Y NO ESTA EN SKIA. Android decodifica en
 * dos pasadas: mira cuanto mide sin decodificar y luego deja que el decodificador
 * SALTE pixeles mientras lee, asi que una pagina de 2000x3000 sale directamente a
 * 220 px y **el bitmap grande no llega a existir**.
 *
 * Con `Image.makeFromEncoded` de Skia si existiria: 2000 x 3000 x 4 = **24 MB por
 * pagina**, y con tres a la vez, 72 MB de picos. En un iPad de 4 GB eso no
 * revienta siempre, revienta A VECES —y ademas **iOS mata la app sin avisar**, no
 * hay `OutOfMemoryError` que atrapar—, que es la peor clase de fallo.
 *
 * La respuesta es **ImageIO**, del sistema: `CGImageSourceCreateThumbnailAtIndex`
 * con `kCGImageSourceThumbnailMaxPixelSize` **decodifica ya al tamaño pedido**.
 * Cero dependencias nuevas, la misma regla que zlib. Skia entra solo al final,
 * para envolver los pixeles ya reducidos.
 *
 * SE TRABAJA CON CoreFoundation Y NO CON NSData a proposito: `CGImageSource`
 * pide un `CFData`, y aunque estan puenteados, el puente entre un objeto de
 * Objective-C y un puntero de C **no es un cast en Kotlin**. Creando el `CFData`
 * directo con `CFDataCreate` no hay puente que cruzar.
 *
 * **TODO LO QUE SE CREA AQUI SE SUELTA A MANO.** CoreFoundation no tiene recogida
 * de basura: cada `Create` lleva su `CFRelease`, y cada `malloc` su `free`. Una
 * fuga aqui no da ningun error — solo hace que la app crezca hasta que iOS la
 * mata, que es justo lo que se estaba evitando.
 *
 * ESCRITO Y SIN COMPILAR: lo dice el CI de macOS.
 */
@OptIn(ExperimentalForeignApi::class)
object ImagenIOS {

    /**
     * [datos] es una pagina codificada (JPEG, PNG...). [anchoMax] es el ancho que
     * se quiere; la imagen sale con su proporcion y sin pasar de ahi.
     */
    fun decodificar(datos: ByteArray, anchoMax: Int): ImageBitmap? {
        if (datos.isEmpty() || anchoMax <= 0) return null

        val cfDatos = datos.usePinned {
            CFDataCreate(null, it.addressOf(0).reinterpret(), datos.size.toLong())
        } ?: return null

        try {
            val fuente = CGImageSourceCreateWithData(cfDatos, null) ?: return null
            try {
                val cg = miniatura(fuente, anchoMax) ?: return null
                try {
                    return aImageBitmap(cg)
                } finally {
                    CGImageRelease(cg)
                }
            } finally {
                CFRelease(fuente)
            }
        } finally {
            CFRelease(cfDatos)
        }
    }

    private fun miniatura(fuente: platform.ImageIO.CGImageSourceRef, anchoMax: Int) = memScoped {
        val opciones = CFDictionaryCreateMutable(null, 2, null, null)
        CFDictionarySetValue(opciones, kCGImageSourceCreateThumbnailFromImageAlways, kCFBooleanTrue)

        // EL MAXIMO ES DEL LADO MAYOR, NO DEL ANCHO. Una pagina de comic es mas
        // alta que ancha, asi que pidiendo el ancho a secas saldria una imagen
        // mas pequeña de lo pedido y se veria borrosa. Se pide 3/2, que es la
        // proporcion de una pagina de comic.
        val n = alloc<IntVar>()
        n.value = (anchoMax * 3) / 2
        val lado = CFNumberCreate(null, kCFNumberIntType, n.ptr)
        CFDictionarySetValue(opciones, kCGImageSourceThumbnailMaxPixelSize, lado)

        val cg = CGImageSourceCreateThumbnailAtIndex(fuente, 0u, opciones)
        CFRelease(lado)
        CFRelease(opciones)
        cg
    }

    private fun aImageBitmap(cg: platform.CoreGraphics.CGImageRef): ImageBitmap? {
        val ancho = CGImageGetWidth(cg).toInt()
        val alto = CGImageGetHeight(cg).toInt()
        if (ancho <= 0 || alto <= 0) return null

        // PINTARLO EN UN CONTEXTO ES LA UNICA FORMA DE SACAR PIXELES CRUDOS de un
        // CGImage. Parece un rodeo y no lo es: a estas alturas la imagen YA esta
        // reducida, asi que este buffer es pequeño.
        val porFila = ancho * 4
        val cuantos = porFila * alto
        val buffer = malloc(cuantos.toULong()) ?: return null
        try {
            val espacio = CGColorSpaceCreateDeviceRGB()
            val ctx = CGBitmapContextCreate(
                buffer, ancho.toULong(), alto.toULong(), 8u,
                porFila.toULong(), espacio,
                CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value
            )
            CGColorSpaceRelease(espacio)
            if (ctx == null) return null

            CGContextDrawImage(ctx, CGRectMake(0.0, 0.0, ancho.toDouble(), alto.toDouble()), cg)
            CGContextRelease(ctx)

            val pixeles = ByteArray(cuantos)
            pixeles.usePinned { memcpy(it.addressOf(0), buffer, cuantos.toULong()) }

            // RGBA8888 Y NO 565 COMO EN ANDROID, y no es un descuido:
            // CGBitmapContext no hace 565 de forma razonable. Cuesta el doble de
            // memoria por miniatura, y por eso el techo de la cache en iOS —que
            // ya tenia que ser un numero fijo, porque `Runtime.maxMemory()` no
            // existe aqui— hay que ponerlo contando con ese doble.
            val info = ImageInfo(ancho, alto, ColorType.RGBA_8888, ColorAlphaType.PREMUL)
            return Image.makeRaster(info, pixeles, porFila).toComposeImageBitmap()
        } finally {
            free(buffer)
        }
    }
}
