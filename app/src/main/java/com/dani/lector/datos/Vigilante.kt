package com.dani.lector.datos

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.dani.lector.LectorApp
import com.dani.lector.MainActivity
import com.dani.lector.R
import com.dani.lector.red.FuenteComics
import com.dani.lector.red.NumeroRemoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Mira si han salido numeros nuevos de las series que sigues, y avisa.
 *
 * UNA SOLA PASADA PARA LOS DOS SITIOS. Esto lo llaman dos cosas: la app cuando
 * la abres (repaso lento de todo, para que el "te faltan N" no se quede rancio)
 * y el trabajo diario en segundo plano (solo las seguidas, para notificar).
 * Es la misma consulta, la misma comparacion y el mismo guardado; lo unico que
 * cambia es a quien se pregunta y que se hace con el resultado. Tenerlo escrito
 * dos veces habria sido la forma segura de que las dos versiones se separaran.
 *
 * [pasada] NO notifica ni toca la interfaz: devuelve de que hay que avisar y
 * quien la llama decide si eso es un dialogo o una notificacion.
 */
object Vigilante {

    data class Aviso(
        val serie: String,
        val ruta: String,
        val nuevos: List<NumeroRemoto>
    )

    /**
     * @param soloSeguidas modo del trabajo en segundo plano.
     * @param tope cuantas series preguntar como mucho en esta pasada.
     */
    suspend fun pasada(
        ctx: Context,
        fuente: FuenteComics,
        seriesRemotas: SeriesRemotas,
        fechaCorte: String,
        soloSeguidas: Boolean,
        tope: Int,
        progreso: (String) -> Unit = {}
    ): List<Aviso> = withContext(Dispatchers.IO) {
        // EN IO Y NO EN EL HILO DE QUIEN LLAMA. El cliente de Comic Vine ya se
        // cambia de hilo el solo, pero esto ademas ESCRIBE el JSON de las
        // fichas una vez por serie, y desde el ViewModel eso caeria en el hilo
        // principal. Es la regla de siempre del proyecto: nada de disco donde
        // se pinta.
        if (!fuente.disponible) return@withContext emptyList()

        val ahora = System.currentTimeMillis()
        // La fecha española, no la del movil. Ver Novedades.ZONA.
        val hoy = Novedades.hoy()

        val candidatas = seriesRemotas.todas().map {
            Novedades.Candidata(
                ruta = it.ruta,
                serie = it.nombre,
                volumenId = it.volumenId,
                ultima = it.numeros.mapNotNull { n -> n.fecha }.maxOrNull(),
                revisada = it.cuando,
                seguida = it.seguida
            )
        }
        val toca = Novedades.aRevisar(candidatas, ahora, fechaCorte, tope, soloSeguidas)
        if (toca.isEmpty()) return@withContext emptyList()

        val avisos = mutableListOf<Aviso>()
        for (c in toca) {
            val ficha = seriesRemotas.de(c.ruta) ?: continue
            progreso("Mirando ${c.serie}...")
            val traidos = runCatching { fuente.numerosDe(c.volumenId) }
                .getOrDefault(emptyList())

            // Una respuesta corta o vacia casi siempre es un 420, no una serie
            // que ha perdido numeros. Ni se guarda ni se compara: se deja la
            // ficha como estaba y se reintentara otro dia.
            if (!Novedades.fiable(ficha.numeros, traidos)) continue

            val reparto = Novedades.aAvisar(traidos, ficha.avisados, hoy)

            // Se dan por vistos los avisados Y los callados en la misma
            // escritura. Si se guardara solo lo avisado, los viejos volverian a
            // clasificarse en cada pasada; y si se guardara antes de notificar,
            // un fallo al notificar te dejaria sin enterarte para siempre.
            val vistos = ficha.avisados +
                reparto.avisar.map { it.etiqueta } +
                reparto.callar.map { it.etiqueta }

            seriesRemotas.guardar(
                ficha.copy(numeros = traidos, cuando = ahora, avisados = vistos)
            )
            if (reparto.avisar.isNotEmpty())
                avisos.add(Aviso(ficha.nombre, ficha.ruta, reparto.avisar))
        }
        avisos
    }

    // ─────────────────────────── LA NOTIFICACION ───────────────────────────

    const val CANAL = "novedades"

    fun crearCanal(ctx: Context) {
        val canal = NotificationChannel(
            CANAL,
            "Números nuevos",
            // DEFAULT y no HIGH: esto no es una alarma. Suena una vez y se
            // queda en la barra; que un comic haya salido no justifica una
            // notificacion flotante que tape lo que estes haciendo.
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Avisa cuando sale un número de una serie que sigues"
        }
        ctx.getSystemService(NotificationManager::class.java)
            ?.createNotificationChannel(canal)
    }

    /**
     * UNA NOTIFICACION POR SERIE, con el id sacado de la ruta.
     *
     * Asi, si en la misma pasada salen dos numeros de la misma serie, se
     * juntan en un aviso ("#12 y #13") en vez de dos; y si la pasada de mañana
     * trae otro de esa serie, SUSTITUYE al de hoy en vez de acumularse. Una
     * barra de notificaciones con seis avisos de la misma serie es ruido.
     */
    fun notificar(ctx: Context, aviso: Aviso) {
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return

        val abrir = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val n = NotificationCompat.Builder(ctx, CANAL)
            // MONOCROMO Y CON TRANSPARENCIA, o sale un cuadrado blanco:
            // Android le pone su propio color al icono de notificacion. Es el
            // unico drawable del proyecto y existe solo para esto.
            .setSmallIcon(R.drawable.ic_aviso)
            .setContentTitle(aviso.serie)
            // La frase cambia segun de donde salga la fecha: "ya esta en
            // tiendas" cuando Comic Vine da la de venta de verdad, "ya deberia"
            // cuando ha habido que estimarla desde la de portada. Es la regla
            // de siempre del proyecto llevada a una notificacion: que se vea si
            // el dato esta comprobado o calculado.
            .setContentText("${Novedades.lista(aviso.nuevos)} ${Novedades.fraseVenta(aviso.nuevos)}")
            .setContentIntent(abrir)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_RECOMMENDATION)
            .build()

        runCatching {
            NotificationManagerCompat.from(ctx).notify(aviso.ruta.hashCode(), n)
        }
    }

    // ─────────────────────── EL TRABAJO DE SEGUNDO PLANO ───────────────────────

    const val TRABAJO = "novedades-diario"

    /**
     * POR QUE ENTRA WORKMANAGER, CON LO QUE COSTO QUITAR TODO LO DEMAS.
     *
     * El documento del proyecto decia "ni servicios, ni WorkManager, ni
     * wakelocks", y esa regla se escribio el dia que se descubrio que el movil
     * se calentaba. Pero lo que calentaba era una animacion infinita repintando
     * a 120 Hz con la app quieta: trabajo CONTINUO. Esto es otra cosa — el
     * sistema despierta la app una vez al dia, hace tres o cuatro peticiones y
     * se vuelve a dormir— y es exactamente para lo que existe WorkManager.
     *
     * Aun asi la regla vieja sigue valiendo para lo demas: sigue sin haber
     * servicios en primer plano, ni wakelocks, ni nada que se despierte cada
     * pocos minutos.
     *
     * Y LO QUE HAY QUE SABER ANTES DE PROMETER NADA: "una vez al dia" es lo que
     * se PIDE, no lo que se garantiza. Android agrupa los trabajos, respeta el
     * modo de ahorro y con el movil en reposo profundo puede retrasarlo horas.
     * El aviso llegara con un dia de margen, no a la hora en punto.
     */
    fun programar(ctx: Context) {
        val trabajo = PeriodicWorkRequestBuilder<TrabajoNovedades>(1, TimeUnit.DAYS)
            // Sin red no hay nada que preguntar, y reintentarlo sin ella solo
            // gasta bateria. WorkManager lo espera a que la haya.
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .build()

        // KEEP y no REPLACE: con REPLACE, cada arranque de la app tiraria el
        // trabajo programado y empezaria a contar el dia otra vez. Alguien que
        // abre la app a diario no recibiria un aviso nunca.
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
            TRABAJO, ExistingPeriodicWorkPolicy.KEEP, trabajo
        )
    }
}

/** Una pasada de [Vigilante] con la app cerrada, y sus notificaciones. */
class TrabajoNovedades(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val app = ctx as? LectorApp ?: return Result.success()

        // El corte de "en emision" se calcula aqui igual que en el ViewModel.
        // Son cuatro meses porque la fecha de Comic Vine es de PORTADA: con un
        // mes, cualquier serie viva pareceria terminada la mitad del tiempo.
        //
        // Y sale de Novedades.hoy(), o sea del calendario español: este trabajo
        // lo despierta el sistema a cualquier hora, y con la zona del proceso
        // una pasada de madrugada contaria el dia anterior.
        val corte = Novedades.hoy().minusDays(120)
            .format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)

        val avisos = runCatching {
            Vigilante.pasada(
                ctx = ctx,
                fuente = app.fuente,
                seriesRemotas = SeriesRemotas(ctx),
                fechaCorte = corte,
                soloSeguidas = true,
                // Doce y no tres: aqui solo entran las que sigues, que son
                // pocas y elegidas por ti, y hay un dia entero por delante
                // antes de la siguiente pasada.
                tope = 12
            )
        }.getOrElse {
            // NO SE REINTENTA, Y ES A PROPOSITO. Los fallos de red ya se tragan
            // dentro de la pasada —una serie que no responde se deja para otro
            // dia sin tocar su ficha—, asi que lo que llegue hasta aqui es un
            // fallo de programacion. Reintentar eso es despertar el movil para
            // volver a fallar. Mañana hay otra pasada.
            return Result.success()
        }

        avisos.forEach { Vigilante.notificar(ctx, it) }
        return Result.success()
    }
}
