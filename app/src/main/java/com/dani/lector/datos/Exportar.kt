package com.dani.lector.datos

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

/**
 * Sacar una pagina del comic: a la galeria o a otra app.
 *
 * DOS CAMINOS Y NO UNO, porque son dos cosas distintas de verdad:
 *
 *  - **Compartir** escribe en la cache de la app y presta el fichero con un
 *    FileProvider. No pide permisos, funciona en cualquier version, y lo que
 *    salga de ahi lo decide el usuario en la hoja del sistema (que en casi todos
 *    los moviles incluye "guardar en Fotos" de todas formas).
 *  - **Guardar** mete la imagen en la galeria de verdad, y para eso hace falta
 *    MediaStore. Desde Android 10 se puede sin ningun permiso; por debajo habria
 *    que pedir WRITE_EXTERNAL_STORAGE, que es un permiso enorme para esto. Ahi
 *    la opcion no se ofrece — ver [sePuedeGuardar]— y queda compartir, que hace
 *    lo mismo con dos toques mas.
 */
object Exportar {

    /** Si la galeria esta disponible sin pedir permisos. Android 10 en adelante. */
    val sePuedeGuardar: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    /**
     * Un nombre de fichero decente a partir del comic y la pagina.
     *
     * Sin la extension del comic y sin nada que el sistema de ficheros pueda
     * rechazar: esto acaba siendo el nombre de un JPG en la galeria del movil.
     */
    fun nombre(comic: String, pagina: Int): String {
        val limpio = comic.substringBeforeLast('.')
            .replace(Regex("[^\\p{L}\\p{N} #._-]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(60)
            .ifBlank { "pagina" }
        return "$limpio - p${pagina.toString().padStart(3, '0')}.jpg"
    }

    /**
     * Guarda la pagina en la galeria. Devuelve `true` si ha entrado.
     *
     * En su propio album ("Lector") para que no se mezcle con las fotos: una
     * pagina de comic entre las fotos del movil es justo lo que hace que la
     * gente desactive estas cosas.
     */
    fun aGaleria(ctx: Context, bmp: Bitmap, nombre: String): Boolean {
        if (!sePuedeGuardar) return false
        return runCatching {
            val datos = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, nombre)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/Lector")
                // PENDIENTE mientras se escribe: hasta que no se pone a 0, la
                // galeria no la enseña. Sin esto se ve una imagen a medias.
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val destino = ctx.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, datos) ?: return false
            ctx.contentResolver.openOutputStream(destino)?.use {
                bmp.compress(Bitmap.CompressFormat.JPEG, 92, it)
            } ?: return false
            datos.clear()
            datos.put(MediaStore.Images.Media.IS_PENDING, 0)
            ctx.contentResolver.update(destino, datos, null, null)
            true
        }.getOrDefault(false)
    }

    /**
     * Escribe la pagina en la cache y devuelve el intent para compartirla.
     *
     * UNA CARPETA QUE SE VACIA: cada compartir borra lo anterior. Sin eso esto
     * seria otra fuga lenta en la cache, que es la leccion que a este proyecto
     * ya le costo 3,78 GB.
     */
    fun intentDeCompartir(ctx: Context, bmp: Bitmap, nombre: String): Intent? =
        runCatching {
            val carpeta = File(ctx.cacheDir, "compartir").apply {
                deleteRecursively(); mkdirs()
            }
            val f = File(carpeta, nombre)
            f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            val uri: Uri = FileProvider.getUriForFile(
                ctx, "${ctx.packageName}.ficheros", f)
            Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }.getOrNull()
}
