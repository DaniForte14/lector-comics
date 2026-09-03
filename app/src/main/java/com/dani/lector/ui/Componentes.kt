package com.dani.lector.ui

import android.graphics.Bitmap
import android.provider.Settings
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ═══════════════════════ ANIMACIONES CYBERPUNK ═══════════════════════
//
// Las dos van APAGADAS en el estilo iOS, no hace falta tocar las pantallas.
// Y las dos estan pensadas para no cansar: esto es una app de leer comics
// durante horas, no una intro. Nada de esto se pinta encima de una pagina.

/**
 * Texto con el descuadre de canal de toda la vida: una copia cian a un lado,
 * otra roja al otro, y el texto bueno encima.
 *
 * La clave es el RITMO, no el efecto: pestañea tres fotogramas y se esta
 * quieto entre dos y seis segundos. Un glitch continuo marea y ademas te
 * impide leer el titulo, que es justo para lo que esta ahi.
 */
@Composable
fun TextoGlitch(
    texto: String,
    estilo: TextStyle,
    color: Color,
    modifier: Modifier = Modifier
) {
    if (ESTILO != Estilo.CYBERPUNK) {
        Text(texto, modifier, color = color, style = estilo)
        return
    }

    // Respeta el ajuste del sistema y se para al salir de la app, igual que el
    // escaneo y por el mismo motivo: este `while` no se detiene solo.
    val anima = hayAnimaciones() && enPrimerPlano()
    var desvio by remember(texto) { mutableStateOf(0) }
    LaunchedEffect(texto, anima) {
        desvio = 0
        while (anima) {
            delay(2200L + (0..4000).random())
            repeat(3) {
                desvio = listOf(-3, -2, 2, 3).random()
                delay(45)
            }
            desvio = 0
        }
    }

    Box(modifier) {
        if (desvio != 0) {
            Text(texto, Modifier.offset { IntOffset(-desvio * 2, 0) },
                color = Cian.copy(alpha = 0.75f), style = estilo)
            Text(texto, Modifier.offset { IntOffset(desvio * 2, 0) },
                color = Alarma.copy(alpha = 0.75f), style = estilo)
        }
        Text(texto, color = color, style = estilo)
    }
}

/**
 * Las rayas del escaneo, montadas UNA vez para toda la app.
 *
 * Un Brush no es una descripcion inerte: al pintarlo se le pide su shader, y el
 * shader se cachea DENTRO del objeto. Un Brush nuevo en cada recomposicion es
 * un shader nuevo en cada pintada. Como estas rayas no dependen del tamaño
 * —van en pixeles y se repiten— pueden vivir aqui arriba y no cambiar nunca.
 */
private val RAYAS = Brush.linearGradient(
    0.0f to Color.Transparent,
    0.5f to Color.Transparent,
    0.5f to Color(0x33000000),
    1.0f to Color(0x33000000),
    start = Offset(0f, 0f),
    end = Offset(0f, 4f),
    tileMode = TileMode.Repeated
)

/** Lo que tarda el haz en recorrer la tarjeta, y lo que descansa entre pasadas. */
private const val BARRIDO_MS = 1800
private const val REPOSO_MS = 6000L

/** Fuera de la tarjeta: ningun avance real vale esto, asi que sirve de "quieto". */
private const val QUIETO = -1f

/**
 * Lineas de barrido fijas y un haz que pasa cada pocos segundos.
 *
 * Las lineas se pintan con UN solo rectangulo y un degradado repetido
 * (TileMode.Repeated) en vez de con un bucle de doscientas rayas por
 * fotograma. Parece lo mismo y no cuesta nada.
 *
 * El haz, en cambio, si costaba: ver el comentario de dentro.
 */
@Composable
fun Modifier.escaneo(): Modifier {
    if (ESTILO != Estilo.CYBERPUNK) return this

    // Las rayas son estaticas: se pintan siempre y no cuestan nada.
    val base = this.drawWithContent { drawContent(); drawRect(RAYAS) }
    if (!hayAnimaciones()) return base

    // EL HAZ VA A RACHAS, NO EN BUCLE CONTINUO. Y no es una decision estetica.
    //
    // Antes era un rememberInfiniteTransition: un valor que cambia en CADA
    // fotograma, para siempre, mientras el banner este en pantalla. Eso obliga
    // a repintar una tarjeta de 300 dp a la frecuencia de la pantalla sin parar
    // —y en un movil con pantalla adaptativa, ademas, le impide bajar de 120 Hz
    // para ahorrar—. Con la app abierta y QUIETA seguia trabajando igual. Eso
    // es lo que se nota en la bateria y en el calor.
    //
    // Ahora barre en 1,8 s y se queda quieto 6. Mientras esta quieto [avance]
    // no cambia, asi que no se repinta nada y no hay ni un fotograma de mas:
    // se anima menos de una cuarta parte del tiempo. Y de paso queda mejor, que
    // es lo mismo que ya se decidio con el glitch del titulo: un efecto que
    // aparece de vez en cuando se ve, y uno que no para se vuelve ruido.
    // Y ademas SOLO mientras la app este delante. Al salir, la composicion
    // sigue viva —Android se queda con el proceso en memoria, que es lo
    // normal— y un `while (true)` dentro de un LaunchedEffect NO se para solo:
    // seguiria despertandose cada pocos segundos para siempre. Con la clave
    // puesta en [vivo], al salir la corrutina se cancela y al volver arranca.
    val vivo = enPrimerPlano()
    var avance by remember { mutableStateOf(QUIETO) }
    LaunchedEffect(vivo) {
        avance = QUIETO
        if (!vivo) return@LaunchedEffect
        while (true) {
            delay(REPOSO_MS)
            animate(0f, 1f, animationSpec = tween(BARRIDO_MS, easing = LinearEasing)) { v, _ ->
                avance = v
            }
            avance = QUIETO
        }
    }

    return base.drawWithContent {
        drawContent()
        if (avance == QUIETO) return@drawWithContent

        // el haz: una banda clara que recorre la tarjeta de arriba abajo
        val alto = 110f
        val y = (size.height + alto * 2) * avance - alto
        drawRect(
            Brush.verticalGradient(
                listOf(Color.Transparent, Acento.copy(alpha = 0.13f), Color.Transparent),
                startY = y, endY = y + alto
            ),
            topLeft = Offset(0f, y),
            size = Size(size.width, alto)
        )
    }
}

/**
 * Si la app esta DELANTE ahora mismo.
 *
 * Salir de una app no la cierra: Android se queda con el proceso en memoria por
 * si vuelves, y eso por si solo no gasta bateria. Lo que si gasta es lo que siga
 * TRABAJANDO ahi dentro, y Compose no para los efectos por su cuenta: un
 * `while (true)` con delays sigue despertandose con la pantalla apagada hasta
 * que el sistema mate el proceso.
 *
 * Se observa el ciclo de vida a mano en vez de con repeatOnLifecycle para no
 * meter una dependencia mas: LifecycleEventObserver ya viene con Compose.
 *
 * Ademas de para parar animaciones, sirve para RELEER: volver a la app es el
 * momento exacto en que puede haber ficheros nuevos, porque los has copiado
 * mientras estabas fuera. Nadie copia comics con el lector delante.
 */
/**
 * OJO CON QUIEN ES EL DUEÑO DEL CICLO DE VIDA, que costo 706 ms y un dia.
 *
 * `LocalLifecycleOwner` dentro de un NavHost **no es la Activity: es la entrada
 * de la pila de navegacion de esa pantalla**. Y una entrada que vuelve a estar
 * arriba se queda en STARTED durante TODA la animacion de transicion; solo llega
 * a RESUMED cuando la animacion termina.
 *
 * Por eso [minimo]. Para parar animaciones, RESUMED es lo correcto: mientras se
 * transiciona no hace falta que nada se mueva. Pero para VOLVER A LEER la
 * carpeta, esperar a RESUMED significaba esperar a que acabara la animacion, y
 * en el rastro del movil se veia clavado:
 *
 *     carpeta: «raíz»                    00:30:11.114
 *       (empieza a leer la carpeta)      00:30:11.820   ← 706 ms de espera
 *       leída: 2 carpetas, 0 cómics      00:30:11.829   ← 9 ms de trabajo
 *
 * Nueve milisegundos de leer detras de setecientos de esperar. Con STARTED la
 * lectura arranca en cuanto la pantalla es visible, que es cuando ya tiene
 * sentido.
 *
 * SE COMPRUEBA EL ESTADO Y NO SE ACUMULAN EVENTOS: asi vale igual para RESUMED
 * (ON_RESUME / ON_PAUSE) que para STARTED (ON_START / ON_STOP) sin escribir dos
 * listas de eventos que se pueden desincronizar.
 */
@Composable
fun enPrimerPlano(minimo: Lifecycle.State = Lifecycle.State.RESUMED): Boolean {
    val duenio = LocalLifecycleOwner.current
    var delante by remember(duenio, minimo) {
        mutableStateOf(duenio.lifecycle.currentState.isAtLeast(minimo))
    }
    DisposableEffect(duenio, minimo) {
        val ojo = LifecycleEventObserver { fuente, _ ->
            delante = fuente.lifecycle.currentState.isAtLeast(minimo)
        }
        duenio.lifecycle.addObserver(ojo)
        onDispose { duenio.lifecycle.removeObserver(ojo) }
    }
    return delante
}

/**
 * Si el movil tiene las animaciones puestas.
 *
 * Quien apaga las animaciones del sistema —por mareos, por bateria o porque le
 * molestan— no espera que una app se las salte por su cuenta. Se lee una vez y
 * se recuerda: es un ajuste que no cambia mientras la pantalla esta abierta.
 */
@Composable
private fun hayAnimaciones(): Boolean {
    val ctx = LocalContext.current
    return remember(ctx) {
        runCatching {
            Settings.Global.getFloat(
                ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
        }.getOrDefault(true)
    }
}

/**
 * Cabecera de pantalla: titulo grande al estilo de iOS.
 *
 * Dos cosas hacen la mitad del efecto y no son el tamaño: el interletrado
 * negativo del titulo (ver [Tipo]) y que la flecha de volver sea una comilla
 * angular con la palabra al lado, en color de acento, en su propia barra de
 * 44 dp. El resto es aire.
 *
 * La firma no cambia a proposito, para que todas las pantallas se restilen
 * solas sin tocarlas una a una.
 */
@Composable
fun Cabecera(titulo: String, sub: String, atras: (() -> Unit)? = null, linea: Boolean = true) {
    // safeDrawing en vez de statusBarsPadding: incluye el RECORTE de pantalla
    // (el agujero de la camara), que sigue ocupando sitio aunque la barra de
    // estado no se este viendo. Con statusBarsPadding, al volver del visor
    // —donde las barras van ocultas— el titulo se subia hasta el borde y la
    // camara se le comia un trozo.
    Column(Modifier.fillMaxWidth()
        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))) {
        if (atras != null) Row(
            Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                Modifier.clip(RoundedCornerShape(8.dp))
                    .clickableSimple(accion = atras)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("\u2039", fontSize = 28.sp, color = Acento,
                    modifier = Modifier.padding(end = 5.dp))
                Text("Atrás", style = Tipo.cuerpo, color = Acento)
            }
        }
        Column(Modifier.padding(
            start = 20.dp, end = 20.dp,
            top = if (atras != null) 2.dp else 12.dp, bottom = 10.dp
        )) {
            if (sub.isNotBlank()) Text(sub, style = Tipo.pie, color = Tenue)
            TextoGlitch(titulo, Tipo.grande, Hueso)
        }
    }
    // el separador de iOS es de medio punto, no de uno
    if (linea) Box(Modifier.fillMaxWidth().height(0.5.dp).background(Linea))
}

/**
 * La cabecera de la pantalla de inicio: saludo, titulo y la racha.
 *
 * APARTE DE [Cabecera] a proposito. Aquella la usan todas las pantallas y su
 * firma se dejo intacta para que el restilado no tuviera que tocarlas una a
 * una; meterle aqui un saludo y una racha habria puesto las dos cosas tambien
 * en Ajustes y en el visor.
 *
 * LA RACHA SUBE AQUI PORQUE ABAJO NO LA VE NADIE. Se calculaba desde el primer
 * dia y vivia enterrada en la pantalla de estadisticas, a dos toques. Un numero
 * que solo se ve cuando lo vas a buscar no anima a nadie a seguir la racha, que
 * es exactamente para lo que sirve.
 */
@Composable
fun CabeceraInicio(titulo: String, racha: Int) {
    Column(Modifier.fillMaxWidth()
        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))) {
        Row(
            Modifier.fillMaxWidth().padding(20.dp, 12.dp, 16.dp, 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(saludo(), style = Tipo.pie, color = Tenue)
                TextoGlitch(titulo, Tipo.grande, Hueso)
            }
            // Solo con racha viva: una cápsula que pone "0" es un recordatorio
            // de que lo estás haciendo mal, y eso no lo pidió nadie.
            if (racha > 0) Column(
                Modifier.clip(FormaChapa).background(PanelAlto)
                    .border(FiloAncho, FiloColor, FormaChapa)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("$racha", style = Tipo.destacado, color = Acento)
                Text(if (racha == 1) "día" else "días",
                    style = Tipo.minuscula, color = Tenue)
            }
        }
    }
}

/** Por hora local. Los cortes son los de siempre: 6, 13 y 21. */
private fun saludo(): String {
    val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return when {
        h < 6 -> "Buenas noches"
        h < 13 -> "Buenos días"
        h < 21 -> "Buenas tardes"
        else -> "Buenas noches"
    }
}

// ═══════════════════════ LISTAS AGRUPADAS ═══════════════════════

/**
 * Contenedor de lista agrupada, como los Ajustes de iOS: bloque redondeado
 * separado de los bordes, sobre el fondo, con un rotulo pequeño encima.
 */
@Composable
fun Grupo(
    titulo: String? = null,
    modifier: Modifier = Modifier,
    contenido: @Composable ColumnScope.() -> Unit
) {
    Column(modifier.fillMaxWidth().padding(bottom = 22.dp)) {
        if (titulo != null) Text(
            titulo.uppercase(), style = Tipo.pie, color = Tenue, letterSpacing = 0.5.sp,
            modifier = Modifier.padding(start = 32.dp, end = 20.dp, bottom = 7.dp)
        )
        Column(
            Modifier.padding(horizontal = 16.dp)
                .clip(FormaTarjeta)
                .background(Panel),
            content = contenido
        )
    }
}

/**
 * Fila de una lista agrupada.
 *
 * El separador va sangrado por la izquierda y no llega al borde: es el detalle
 * que distingue una lista de iOS de una lista cualquiera con lineas.
 */
@Composable
fun Fila(
    titulo: String,
    valor: String? = null,
    detalle: String? = null,
    color: Color = Hueso,
    chevron: Boolean = false,
    ultima: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val clic = if (onClick != null) Modifier.clickableSimple(accion = onClick) else Modifier
    Column(Modifier.fillMaxWidth().then(clic)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(16.dp, 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(titulo, style = Tipo.cuerpo, color = color)
                if (detalle != null) Text(detalle, style = Tipo.pie, color = Tenue,
                    modifier = Modifier.padding(top = 2.dp))
            }
            if (valor != null) Text(valor, style = Tipo.cuerpo, color = Tenue,
                modifier = Modifier.padding(start = 10.dp))
            if (chevron) Text("\u203a", fontSize = 20.sp, color = Apagado,
                modifier = Modifier.padding(start = 7.dp))
        }
        if (!ultima) Box(Modifier.padding(start = 16.dp)
            .fillMaxWidth().height(0.5.dp).background(Linea))
    }
}

@Composable
fun Portada(
    uri: String?,
    modifier: Modifier = Modifier,
    cargar: suspend (String) -> Bitmap?,
    // Lo que ya esta en memoria, sin esperar. Por que hace falta las dos cosas:
    // [cargar] suspende, y suspender significa saltar a otro hilo y volver, o
    // sea uno o dos fotogramas con la carta en gris AUNQUE la portada estuviera
    // hecha. Al bajar y volver a subir en la biblioteca eso es justo el tiron
    // que se ve. Con esto la portada que ya esta puesta se pinta en el mismo
    // fotograma y [cargar] solo entra cuando de verdad hay que ir a buscarla.
    inmediato: (String) -> Bitmap? = { null },
    // OJO: vacio va ANTES de encima. Si fuera el ultimo, la lambda final de
    // todas las llamadas que ya hay se engancharia aqui y las marcas y chapas
    // se pintarian solo cuando NO hay portada, que es justo al reves.
    vacio: @Composable BoxScope.() -> Unit = {},
    encima: @Composable BoxScope.() -> Unit = {}
) {
    val yaEsta = remember(uri) { uri?.let(inmediato) }
    var bmp by remember(uri) { mutableStateOf(yaEsta) }
    // Hace falta saber si ya se ha INTENTADO, no solo si hay bitmap: mientras
    // carga tambien es null, y sin esto la carta diria "no se puede abrir"
    // durante un instante en cada portada que tarde.
    var intentado by remember(uri) { mutableStateOf(yaEsta != null) }
    LaunchedEffect(uri) {
        // Si ya estaba en memoria no se vuelve a pedir: seria una corrutina y
        // un salto de hilo por cada carta que entra en pantalla, para nada.
        if (yaEsta == null) {
            bmp = uri?.let { cargar(it) }
            intentado = true
        }
    }
    Box(modifier.background(Panel)) {
        val b = bmp
        if (b != null) {
            // remember: asImageBitmap envuelve el Bitmap en un objeto nuevo de
            // Compose CADA vez que se llama. Es barato de uno en uno y caro
            // multiplicado por las cartas de la rejilla y por los repintados.
            val img = remember(b) { b.asImageBitmap() }
            Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else if (intentado) vacio()
        encima()
    }
}

/**
 * El corte de caratula de Apple: esquina redondeada y un filo claro encima.
 *
 * El filo no es un adorno. Sobre fondo negro, una portada con marco oscuro se
 * funde con el fondo y la carta pierde la forma; medio punto de blanco al 10%
 * la recorta sin que se note que hay un borde.
 */
fun Modifier.caratula(forma: Shape = FormaCaratula) = this
    .clip(forma)
    .border(FiloAncho, FiloColor, forma)

/**
 * Cabecera de seccion del catalogo.
 *
 * Titulo a la izquierda, el dato y la comilla angular a la derecha, y la fila
 * entera es el area tactil. Asi presenta iOS un "ver todo": el chevron ES la
 * invitacion a tocar, no hace falta un enlace de texto aparte gritando en rojo.
 */
@Composable
fun TituloFila(texto: String, detalle: String? = null, onAccion: (() -> Unit)? = null) {
    val clic = if (onAccion != null) Modifier.clickableSimple(accion = onAccion) else Modifier
    Row(
        Modifier.fillMaxWidth().then(clic).padding(20.dp, 18.dp, 16.dp, 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(texto, style = Tipo.subtitulo, color = Hueso, modifier = Modifier.weight(1f))
        if (detalle != null) Text(detalle, style = Tipo.secundario, color = Tenue)
        if (onAccion != null) Text("\u203a", fontSize = 20.sp, color = Apagado,
            modifier = Modifier.padding(start = 6.dp))
    }
}

/**
 * Una linea de una hoja de opciones: el texto en color de acento y, si hace
 * falta, una explicacion pequeña debajo.
 *
 * VIVE AQUI Y NO EN Pantallas.kt, donde nacio, porque el visor tambien la usa
 * —guardar y compartir una pagina— y en Kotlin `private` a nivel de fichero es
 * literalmente eso: desde otro fichero del mismo paquete no se ve.
 *
 * [activo] va ANTES de la accion por la trampa de la lambda final: detras, la
 * lambda pegada al parentesis de todas las llamadas se engancharia ahi.
 */
@Composable
fun OpcionMenu(
    texto: String,
    sub: String? = null,
    activo: Boolean = true,
    onClick: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth()
            .then(if (activo) Modifier.clickableSimple(accion = onClick) else Modifier)
            .padding(20.dp, 14.dp)
    ) {
        Text(texto, style = Tipo.cuerpo, color = if (activo) Acento else Apagado)
        if (sub != null) Text(sub, style = Tipo.pie, color = Apagado,
            modifier = Modifier.padding(top = 2.dp))
    }
}

/**
 * [enabled] va ANTES de la accion, no detras: si fuera el ultimo parametro, la
 * lambda pegada al parentesis de las veinte llamadas que ya hay se engancharia
 * ahi. Es la trampa de la lambda final, que en este fichero ya ha mordido dos
 * veces (ver Portada e Interruptor).
 */
fun Modifier.clickableSimple(enabled: Boolean = true, accion: () -> Unit) =
    this.clickable(enabled = enabled, onClick = accion)


/**
 * Campo de texto al estilo de iOS.
 *
 * Sin `OutlinedTextField`: el de Material trae etiqueta flotante que sube al
 * escribir, contorno y relleno alto. Nada de eso existe en iOS, donde un campo
 * es una caja redondeada con el texto de pista dentro y ya.
 */
@Composable
fun Campo(
    valor: String,
    pista: String,
    modifier: Modifier = Modifier,
    oculto: Boolean = false,
    numerico: Boolean = false,
    alCambiar: (String) -> Unit
) {
    Box(
        modifier.fillMaxWidth()
            .clip(FormaTarjeta)
            .background(PanelAlto)
            .padding(horizontal = 12.dp, vertical = 11.dp)
    ) {
        if (valor.isEmpty()) Text(pista, style = Tipo.cuerpo, color = Apagado)
        BasicTextField(
            value = valor,
            onValueChange = alCambiar,
            singleLine = true,
            textStyle = Tipo.cuerpo.copy(color = Hueso),
            cursorBrush = Brush.verticalGradient(listOf(Acento, Acento)),
            visualTransformation =
                if (oculto) PasswordVisualTransformation() else VisualTransformation.None,
            // El teclado numerico y no el normal cuando lo que se pide es un
            // numero: son cuatro toques menos y, sobre todo, no deja escribir
            // letras que luego hay que rechazar.
            keyboardOptions = KeyboardOptions(
                keyboardType = if (numerico) KeyboardType.Number else KeyboardType.Text
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Boton al estilo de iOS: esquina de 12, sin elevacion y sin sombra.
 *
 * [relleno] false da el boton secundario, que en iOS no lleva contorno sino
 * fondo gris y el texto en color de acento.
 */
@Composable
fun Boton(
    texto: String,
    modifier: Modifier = Modifier,
    activo: Boolean = true,
    relleno: Boolean = true,
    onClick: () -> Unit
) {
    val fondo = if (relleno && activo) Acento else PanelAlto
    val tinta = when {
        !activo -> Apagado
        relleno -> SobreAcento
        else -> Acento
    }
    Box(
        modifier.clip(FormaBoton)
            .background(fondo)
            .then(if (activo) Modifier.clickableSimple(accion = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(texto, style = Tipo.destacado, color = tinta)
    }
}

/** Interruptor de ajuste. Sin Switch de Material, que trae su propio look. */
@Composable
fun Interruptor(
    texto: String,
    detalle: String? = null,
    activo: Boolean,
    // Va ANTES de onCambio a proposito. Si fuera detras, la lambda final se
    // engancharia a este parametro y las tres llamadas de Ajustes dejarian de
    // compilar con un error que no señala aqui.
    ultima: Boolean = false,
    onCambio: (Boolean) -> Unit
) {
    Column(Modifier.fillMaxWidth().clickableSimple { onCambio(!activo) }) {
        Row(
            // Mismo relleno que Fila: estos ahora viven DENTRO de una tarjeta
            // de Grupo, y sin margen lateral el texto tocaba el borde.
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f).padding(end = 14.dp)) {
                Text(texto, style = Tipo.cuerpo, color = Hueso)
                if (detalle != null) Text(detalle, style = Tipo.pie, color = Tenue,
                    modifier = Modifier.padding(top = 2.dp))
            }
            Box(
                Modifier.width(48.dp).height(26.dp).clip(FormaChapa)
                    .background(if (activo) Acento else PanelAlto).padding(3.dp),
                contentAlignment = if (activo) Alignment.CenterEnd else Alignment.CenterStart
            ) {
                Box(Modifier.width(20.dp).height(20.dp).clip(FormaChapa)
                    .background(if (activo) SobreAcento else Apagado))
            }
        }
        if (!ultima) Box(Modifier.padding(start = 16.dp)
            .fillMaxWidth().height(0.5.dp).background(Linea))
    }
}

/**
 * Control segmentado: dos o tres opciones a la vista y una marcada.
 *
 * Es el patron del "System / Light / Dark" de los ajustes de iOS. Sirve para lo
 * que un interruptor hace mal: cuando la opcion contraria tiene nombre propio.
 * "Llenar la pantalla: apagado" no dice que pasa entonces; "Encajar | Llenar"
 * si.
 *
 * La marcada va en Acento con el texto en SobreAcento, que en 2077 es negro
 * sobre amarillo. Nada de Material: su SegmentedButton trae su propio aspecto.
 */
@Composable
fun Segmentado(
    opciones: List<String>,
    elegida: Int,
    modifier: Modifier = Modifier,
    onElegir: (Int) -> Unit
) {
    Row(modifier.fillMaxWidth().clip(FormaChapa).background(PanelAlto).padding(3.dp)) {
        opciones.forEachIndexed { i, texto ->
            val marcada = i == elegida
            Box(
                Modifier.weight(1f).clip(FormaChapa)
                    .background(if (marcada) Acento else Color.Transparent)
                    .clickableSimple { onElegir(i) }
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(texto, style = Tipo.secundario, maxLines = 1,
                    color = if (marcada) SobreAcento else Tenue)
            }
        }
    }
}

/**
 * Buscador de una linea.
 *
 * Con BasicTextField y no con TextField: el de Material trae relleno alto,
 * etiqueta flotante y subrayado, y aqui los tres estorban.
 */
@Composable
fun Buscador(
    texto: String,
    pista: String,
    alCambiar: (String) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(16.dp, 6.dp)
            .clip(FormaTarjeta)
            .background(PanelAlto)
            .padding(10.dp, 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("\u2315", fontSize = 17.sp, color = Apagado,
            modifier = Modifier.padding(end = 7.dp))

        Box(Modifier.weight(1f)) {
            if (texto.isEmpty()) Text(pista, style = Tipo.cuerpo, color = Apagado)
            BasicTextField(
                value = texto,
                onValueChange = alCambiar,
                singleLine = true,
                textStyle = Tipo.cuerpo.copy(color = Hueso),
                cursorBrush = Brush.verticalGradient(listOf(Acento, Acento)),
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (texto.isNotEmpty())
            Text("\u2715", fontSize = 14.sp, color = Apagado,
                modifier = Modifier.padding(start = 8.dp).clickableSimple { alCambiar("") })
    }
}
