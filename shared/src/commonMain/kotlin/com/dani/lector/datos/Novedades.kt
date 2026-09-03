package com.dani.lector.datos

import com.dani.lector.red.NumeroRemoto
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn

/**
 * Que series toca volver a preguntar y de que numeros hay que avisar.
 *
 * EL PRESUPUESTO ES EL DISEÑO. Comic Vine tarda unos diez segundos por peticion
 * y corta con un 420 pasadas unas doscientas por hora. Preguntar por las
 * sesenta series de un personaje todos los dias es gastarse la cuota en algo
 * que casi nunca ha cambiado. Por eso hay dos velocidades:
 *
 *  - Las series que SIGUES se miran a diario y con tope alto. Son pocas, las
 *    has elegido tú, y son justo de las que quieres enterarte.
 *  - Las demas se van repasando por turnos, la mas vieja primero y tres por
 *    pasada, solo mientras la app esta abierta. Ahi no hay prisa: es para que
 *    el recuento de "te faltan N" no se quede rancio.
 *
 * En las dos, solo las series EN EMISION: una que termino en 1994 no va a
 * sacar un numero nuevo y preguntarlo es tirar una peticion.
 *
 * Funcion PURA como el resto de las que deciden algo: entra lo que hay
 * guardado, sale a quien preguntar y de que avisar. El reloj entra por
 * parametro, igual que en [Racha] y [EstadoSerie].
 */
object Novedades {

    /**
     * El calendario con el que se decide todo: el de España.
     *
     * NO ES LA DEL MOVIL, Y ES A PROPOSITO. `LocalDate.now()` a secas usa la
     * zona del sistema, y eso significa dos cosas malas:
     *
     *  - En UTC, que es donde puede acabar corriendo un trabajo de fondo, a las
     *    dos de la mañana de un miercoles en Madrid todavia es martes. Un
     *    numero que sale el miercoles no se avisaria hasta el jueves.
     *  - Y si el movil cambia de zona —un viaje a Japon, que son nueve horas
     *    por delante— los avisos se adelantarian un dia entero respecto al
     *    calendario con el que Dani cuenta los dias.
     *
     * Fijarla aqui, con nombre, hace que la app diga siempre lo mismo esté
     * donde esté el movil. Y entra por parametro en todo lo que decide, igual
     * que el reloj en [Racha]: una funcion que llama al reloj no se puede
     * probar dos veces con el mismo resultado.
     */
    val ZONA: TimeZone = TimeZone.of("Europe/Madrid")

    /** Que dia es hoy en España. */
    fun hoy(): LocalDate = Clock.System.todayIn(ZONA)

    /** Lo que hace falta saber de una serie guardada para decidir si preguntar. */
    data class Candidata(
        val ruta: String,
        val serie: String,
        val volumenId: String,
        /** La fecha de portada mas reciente que conocemos, "aaaa-mm-dd". */
        val ultima: String?,
        /** Cuando se pregunto por ella la ultima vez, en milisegundos. */
        val revisada: Long,
        val seguida: Boolean = false
    )

    /** Espera entre consultas de una serie cualquiera. */
    const val ESPERA = 3L * 24 * 60 * 60 * 1000

    /**
     * Espera de una serie seguida. 20 horas y no 24: con 24 clavadas, un
     * trabajo diario que se ejecuta unos minutos antes que el dia anterior se
     * salta la comprobacion un dia si y otro no.
     */
    const val ESPERA_SEGUIDA = 20L * 60 * 60 * 1000

    /**
     * Dias que la fecha de PORTADA va por delante de la de venta.
     *
     * ES UNA CONVENCION DEL SECTOR, NO UN DATO, y SOLO SE USA DE RESPALDO.
     * Comic Vine tiene la fecha de venta de verdad (`store_date`), pero viene
     * vacia en muchos numeros —empezo a guardarla tarde—, y para esos hay que
     * estimarla: en los comics americanos la fecha de portada va dos o tres
     * meses por delante del dia en que la grapa llega a la tienda, herencia de
     * cuando le decia al quiosquero hasta cuando dejarla en el expositor.
     * Sesenta dias es el valor de en medio.
     *
     * Va aqui, con nombre y explicacion, porque es exactamente el tipo de
     * numero que dentro de seis meses alguien va a mirar preguntandose de donde
     * sale. Es de la misma familia que [Edades]: convencion util, no verdad.
     *
     * PRIMERA VERSION (02/09/2026): esto no era el respaldo, era el unico
     * camino, porque el cliente no pedia `store_date` y Comic Vine no manda lo
     * que no le pides. Se deja escrito porque es la misma trampa que ya costo
     * el id de los volumenes, y cayo otra vez.
     */
    const val ADELANTO_PORTADA = 60L

    /**
     * Dias que un numero tarda en llegar a España desde que sale en EE.UU.
     *
     * CERO, Y ESO ES UNA DECISION, NO UN OLVIDO. `store_date` de Comic Vine es
     * el dia que la grapa sale ALLI, un miercoles. No existe ninguna base de
     * datos con la fecha española: Comic Vine es americana y no guarda nada de
     * las ediciones de aqui.
     *
     * Se deja en cero porque para lo que lee Dani —numeros USA sueltos, no
     * tomos de Panini ni de ECC— el dia es el mismo o casi: lo digital sale a
     * la vez y la importacion llega esa misma semana. Si algun dia se ve que
     * llega tarde, se cambia este numero y ya, que es justo para lo que esta
     * aqui con nombre en vez de estar sumado en medio de una cuenta.
     *
     * LO QUE ESTO NO PUEDE HACER, y conviene no prometerlo: dar la fecha de una
     * edicion española (Panini, ECC). Esas salen meses despues, con otra
     * numeracion, y Comic Vine no las tiene. Haria falta otra fuente.
     */
    const val DESFASE_ESPANA = 0L

    /**
     * Pasados estos dias desde que un numero debio salir, ya no es novedad.
     *
     * Sin esto, cualquier numero VIEJO que Comic Vine diera de alta tarde
     * —completar un volumen antiguo, corregir una ficha— llegaria como aviso de
     * novedad. Se marca como visto y no se avisa: mejor callar un caso raro que
     * despertar el movil por una grapa de hace dos años.
     */
    const val CADUCIDAD = 240L

    /**
     * A quien preguntar en esta pasada.
     *
     * [fechaCorte] es la misma que usa [EstadoSerie] para "en emision": la
     * fecha de Comic Vine es de PORTADA y va meses por delante de la de venta,
     * asi que el corte son cuatro meses y no uno.
     *
     * Con [soloSeguidas] la lista se limita a las que sigues y la espera baja:
     * es el modo del trabajo en segundo plano, que corre una vez al dia y no
     * tiene que repasar la biblioteca entera.
     */
    fun aRevisar(
        candidatas: List<Candidata>,
        ahora: Long,
        fechaCorte: String,
        tope: Int = 3,
        soloSeguidas: Boolean = false
    ): List<Candidata> = candidatas
        .filter { it.volumenId.isNotBlank() }
        .filter { !soloSeguidas || it.seguida }
        .filter { it.ultima != null && it.ultima > fechaCorte }
        .filter { ahora - it.revisada >= if (it.seguida) ESPERA_SEGUIDA else ESPERA }
        // Las seguidas primero, y dentro de cada grupo la mas vieja. Asi, si el
        // tope corta, corta por donde menos duele.
        .sortedWith(compareByDescending<Candidata> { it.seguida }.thenBy { it.revisada })
        .take(tope)

    /**
     * Que hacer con cada numero que todavia no se ha avisado.
     *
     * [avisar] son los que ya deberian estar en la tienda: van a notificacion.
     * [callar] son los que se dan por vistos SIN avisar, porque son demasiado
     * viejos para ser novedad.
     *
     * Lo que no sale en ninguna de las dos listas queda PENDIENTE a proposito:
     *
     *  - Un numero anunciado pero que aun no ha salido. Este es el caso normal
     *    y es la razon de que esta funcion exista. Comic Vine da de alta los
     *    numeros cuando se anuncian, unos tres meses antes de que lleguen a la
     *    tienda; avisar ahi seria avisar de un comic que todavia no existe.
     *    Se queda esperando y salta el dia que le toca.
     *  - Un numero SIN FECHA. Sin fecha no hay forma de saber cuando sale, y
     *    colocarlo por comodidad seria inventarse el dato — la misma regla que
     *    en [Huecos]. Se queda pendiente por si algun dia
     *    Comic Vine le pone la fecha.
     */
    data class Reparto(
        val avisar: List<NumeroRemoto>,
        val callar: List<NumeroRemoto>
    )

    fun aAvisar(
        numeros: List<NumeroRemoto>,
        avisados: Set<String>,
        hoy: LocalDate
    ): Reparto {
        val avisar = mutableListOf<NumeroRemoto>()
        val callar = mutableListOf<NumeroRemoto>()
        for (n in numeros) {
            if (n.etiqueta in avisados) continue
            val venta = venta(n) ?: continue            // sin fecha: pendiente
            when {
                venta > hoy -> {}                // aun no ha salido: pendiente
                venta < hoy.minus(DatePeriod(days = CADUCIDAD.toInt())) -> callar.add(n)
                else -> avisar.add(n)
            }
        }
        return Reparto(avisar.sortedBy { it.fecha }, callar)
    }

    /**
     * Cuando salio (o deberia salir) un numero a la venta.
     *
     * La de verdad si Comic Vine la tiene; si no, la estimacion. En ese orden y
     * nunca al reves: un dato real siempre gana a una convencion.
     */
    fun venta(n: NumeroRemoto): LocalDate? =
        (fecha(n.venta) ?: fecha(n.fecha)?.minus(DatePeriod(days = ADELANTO_PORTADA.toInt())))
            ?.plus(DatePeriod(days = DESFASE_ESPANA.toInt()))

    /** Si a este numero le hemos tenido que estimar la fecha de venta. */
    fun estimada(n: NumeroRemoto): Boolean = fecha(n.venta) == null

    private fun fecha(f: String?): LocalDate? {
        val t = f?.takeIf { it.isNotBlank() } ?: return null
        return runCatching { LocalDate.parse(t) }.getOrNull()
    }

    /**
     * El siguiente numero que queda por salir, y cuando.
     *
     * Es lo que contesta a "¿cuando sale el siguiente?". Se coge el que antes
     * salga de los que aun no han salido, no el de numero mas alto: con dos
     * anunciados a la vez, el que viene es el que llega primero.
     *
     * Los que no tienen fecha quedan fuera, como en todas partes: sin fecha no
     * se puede decir cuando sale, y decir uno a ojo seria inventarselo.
     */
    fun proximo(numeros: List<NumeroRemoto>, hoy: LocalDate): NumeroRemoto? =
        numeros.mapNotNull { n -> venta(n)?.let { n to it } }
            .filter { it.second > hoy }
            .minByOrNull { it.second }
            ?.first

    /**
     * "el 2 de septiembre", y con año si no es el de este año.
     *
     * El año solo cuando hace falta: "sale el 2 de septiembre" se entiende y
     * "sale el 2 de septiembre de 2026" suena a formulario.
     */
    fun enCristiano(cuando: LocalDate, hoy: LocalDate): String {
        val meses = listOf("enero", "febrero", "marzo", "abril", "mayo", "junio",
            "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre")
        val mes = meses.getOrElse(cuando.monthNumber - 1) { "" }
        val anio = if (cuando.year == hoy.year) "" else " de ${cuando.year}"
        return "el ${cuando.dayOfMonth} de $mes$anio"
    }

    /**
     * La linea de salida al empezar a seguir una serie.
     *
     * SIN ESTO, seguir una serie de sesenta numeros suelta sesenta
     * notificaciones la primera noche. Todo lo que ya existe cuando le das a
     * seguir se da por visto; solo se avisa de lo que aparezca despues.
     */
    fun etiquetasDe(numeros: List<NumeroRemoto>): Set<String> =
        numeros.map { it.etiqueta }.toSet()

    /**
     * Si la respuesta nueva se puede dar por buena y guardar encima de la vieja.
     *
     * ESTO ES LO QUE IMPIDE PERDER LOS NUMEROS DE UNA SERIE. Cuando Comic Vine
     * corta con un 420, el cliente devuelve una lista vacia, que es
     * indistinguible de "esta serie no tiene numeros". Guardarla encima dejaria
     * la serie a cero hasta que alguien la comprobara a mano.
     *
     * Misma familia que la regla del conversor de CBR: contar es barato y un
     * comic perdido no se recupera. Una respuesta con MENOS numeros que los que
     * ya teniamos es sospechosa por definicion —una serie no pierde grapas— asi
     * que no se guarda.
     */
    fun fiable(antes: List<NumeroRemoto>, ahora: List<NumeroRemoto>): Boolean =
        ahora.isNotEmpty() && ahora.size >= antes.size

    /**
     * "#12 y #13". Va aparte de [texto] porque la notificacion los quiere
     * separados: el nombre de la serie es el titulo y esto el cuerpo.
     */
    fun lista(nuevos: List<NumeroRemoto>): String {
        val etiquetas = nuevos.map { n ->
            if (n.etiqueta.firstOrNull()?.isDigit() == true) "#${n.etiqueta}" else n.etiqueta
        }
        return when (etiquetas.size) {
            0 -> ""
            1 -> etiquetas[0]
            2 -> "${etiquetas[0]} y ${etiquetas[1]}"
            else -> etiquetas.dropLast(1).joinToString(", ") + " y " + etiquetas.last()
        }
    }

    /**
     * Como se cuenta que ha salido, segun lo que se sepa.
     *
     * ENSEÑAR DE DONDE SALE EL DATO, otra vez: si la fecha es la de verdad se
     * dice en presente, y si esta estimada se dice "deberia". Prometer menos de
     * lo que se sabe es mejor que sonar exacto y fallar, y con una sola palabra
     * de diferencia se distingue lo comprobado de lo calculado.
     */
    fun fraseVenta(nuevos: List<NumeroRemoto>): String =
        if (nuevos.any { estimada(it) }) "ya debería estar en tiendas"
        else "ya está en tiendas"

    /**
     * "El siguiente, el #21, sale el 2 de septiembre."
     *
     * Con "debería salir" cuando la fecha esta estimada, por lo mismo que
     * [fraseVenta]: una palabra de diferencia separa el dato de la cuenta.
     */
    fun fraseProximo(n: NumeroRemoto, hoy: LocalDate): String? {
        val cuando = venta(n) ?: return null
        val etiqueta = if (n.etiqueta.firstOrNull()?.isDigit() == true) "#${n.etiqueta}"
                       else n.etiqueta
        val verbo = if (estimada(n)) "debería salir" else "sale"
        return "El siguiente, el $etiqueta, $verbo ${enCristiano(cuando, hoy)}."
    }

    // ─────────────────── LA AGENDA DE LO QUE VIENE ───────────────────

    /** Un numero anunciado que aun no ha salido, con su serie y su fecha. */
    data class Prevista(
        val serie: String,
        val numero: NumeroRemoto,
        val cuando: LocalDate,
        /** Si la fecha ha habido que calcularla en vez de leerla. */
        val estimada: Boolean
    ) {
        /** "#21", o la etiqueta tal cual si no empieza por cifra ("Annual 2"). */
        val etiqueta: String get() =
            if (numero.etiqueta.firstOrNull()?.isDigit() == true) "#${numero.etiqueta}"
            else numero.etiqueta
    }

    /**
     * Todo lo anunciado y sin salir de las series que sigues, lo que antes
     * llegue primero.
     *
     * POR FECHA Y NO POR SERIE, y eso es lo unico que aporta sobre la lista de
     * series seguidas —que ya dice el proximo de cada una—: la pregunta que
     * contesta no es "¿que viene de Batman?" sino "¿que es lo siguiente que me
     * llega?". Ordenado por serie eso hay que reconstruirlo leyendo.
     *
     * TODOS los anunciados de cada serie, no solo el primero. Como la lista va
     * por fecha, una serie con tres anunciados no tapa a las demas: se
     * intercalan solas.
     *
     * Los que no tienen fecha quedan fuera, como en todas partes: sin fecha no
     * se puede decir cuando sale, y colocarlo por comodidad seria inventarselo.
     */
    fun agenda(
        fichas: List<Ficha>,
        hoy: LocalDate,
        tope: Int = 15
    ): List<Prevista> =
        fichas.filter { it.seguida }
            .flatMap { f ->
                f.numeros.mapNotNull { n ->
                    venta(n)?.takeIf { it > hoy }
                        ?.let { Prevista(f.nombre, n, it, estimada(n)) }
                }
            }
            // El desempate por nombre y etiqueta no es cosmetico: sin el, dos
            // numeros del mismo dia pueden salir en un orden distinto cada vez
            // que se repinta la pantalla.
            .sortedWith(compareBy({ it.cuando }, { it.serie.lowercase() }, { it.etiqueta }))
            .take(tope)

    /**
     * "mañana", "en 3 días", "el 30 de septiembre".
     *
     * Lo cercano en dias y lo lejano en fecha, porque es como se piensa: a tres
     * dias vista lo que quieres saber es cuanto falta, y a dos meses, que dia
     * es para mirarlo en el calendario. "en 47 días" no le dice nada a nadie.
     */
    fun cuandoSale(cuando: LocalDate, hoy: LocalDate): String {
        val dias = hoy.daysUntil(cuando).toLong()
        return when {
            dias <= 0L -> "ya"
            dias == 1L -> "mañana"
            dias < 7L -> "en $dias días"
            else -> enCristiano(cuando, hoy)
        }
    }

    /** "Green Lantern #12 y #13", para el aviso de dentro de la app. */
    fun texto(serie: String, nuevos: List<NumeroRemoto>): String {
        val l = lista(nuevos)
        return if (l.isBlank()) serie else "$serie $l"
    }
}
