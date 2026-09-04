# Lector de cómics — contexto del proyecto

App Android en Kotlin + Compose para leer y organizar una colección de cómics
en local. Proyecto personal de Dani, no se publica en ninguna tienda.

Este documento es para retomar el proyecto sin repetir errores. Lo importante
no es la lista de archivos: son las **decisiones y por qué**.

Última revisión: 3 de septiembre de 2026 (el diario de lectura y el calendario,
el icono de la app, y las tres pestañas convertidas en un carrusel que se pasa
deslizando).

---

## 1. Qué hace la app

**Biblioteca.** Muestra el árbol de carpetas del usuario tal cual, en filas de
portadas estilo catálogo, con un banner de lo último que estaba leyendo. Él
organiza, la app enseña. Buscador que mira **toda** la biblioteca y una fila de
**"En curso"** con lo que tienes a medias.

**Lo que te falta de cada serie.** En una carpeta que es una serie: cuántos
tienes, qué huecos hay, y —si la has comprobado contra Comic Vine— cuántos
faltan de los que existen y cuándo sale el siguiente.

**Seguir series.** "Avisarme cuando salga uno nuevo": la app mira una vez al día
con la app cerrada y notifica cuando el número debería estar en tiendas.

**Lector.** Zoom con recarga en alta resolución, doble toque, modo página con
giro 3D y modo tira, doble página al girar, recorte de bordes, teclas de
volumen, zonas táctiles, tira de miniaturas, marcapáginas y tarjeta al
siguiente cómic de la carpeta.

**Lecturas.** Las estadísticas de lo que llevas leído, contadas **sobre tus
ficheros**: cómics leídos, páginas, racha, series completas y avance por
personaje, al que se baja tocando. Un **calendario** del mes con lo que leíste
cada día —qué cómic y qué páginas— y la lista de las series que sigues. Con los
marcapáginas a un toque.

**Las tres secciones se pasan deslizando** (Biblioteca, Lecturas, Ajustes): son
un carrusel, no pantallas apiladas.

**Y además:** copia de seguridad del progreso y los marcapáginas, y un
interruptor de estética entre iOS y cyberpunk (ver `LECTOR-COMICS-DISENO.md`).

---

## 2. El principio rector

> **Los datos, de la base de datos. El criterio, del modelo.**

Se llegó a esto a base de fallos: un modelo se inventa cifras con total aplomo.
Llegó a colar "Devil's Reign: Moon Knight" (un one-shot) como si fuera la serie
regular.

**Y el 02/09/2026 la regla se llevó a su conclusión: fuera el modelo.** Con el
TODO por personaje se fueron Gemini y las wikis de Marvel y DC, que eran las dos
únicas partes donde algo opinaba. Lo que queda es **una sola fuente, Comic
Vine**, y de ella solo se sacan cosas contables: qué volumen es cada carpeta,
qué números tiene, y en qué fecha salieron.

La regla vieja sigue escrita porque explica por qué el proyecto acabó así, y
porque el día que vuelva a entrar un modelo hay que releerla entera.

Lo que **sí** se mantiene de aquella época, y es lo que más valor tiene: cada
cifra de la pantalla dice de dónde sale. "según «Absolute Batman» (2025)", "por
fecha de portada", "ya **debería** estar en tiendas".

---

## 3. Arquitectura

Sin Room, sin librerías de red externas: persistencia en JSON y red con
`HttpURLConnection`. Menos dependencias, menos que se rompa.

```
LectorApp.kt      Único sitio donde se decide de dónde salen los datos
MainActivity.kt   Navegación y las teclas de volumen del lector
VistaModelo.kt    Estado y lógica de la interfaz

datos/
  Modelos.kt        Carpeta y Comic. Y nada más
  Parser.kt         Saca el número del nombre del fichero
  Escaner.kt        Lee el árbol de carpetas por niveles
  Formatos.kt       ZIP / RAR4 / RAR5 por la firma del fichero
  Busqueda.kt       Buscar cómics por texto en toda la biblioteca
  Progreso.kt       Por qué página vas de cada cómic
  Marcadores.kt     Marcapáginas
  ComicZip.kt       Lee CBZ y CBR, extrae páginas, tres cachés
  Recorte.kt        Quita el marco liso de una página
  Miniaturas.kt     Caché de portadas
  ColorPortada.kt   El color dominante de una portada, para teñir la interfaz
  Racha.kt          Días seguidos leyendo
  Sesiones.kt       El diario: qué leíste, qué día y cuánto (+ la regla, Lectura)
  Calendario.kt     El mes con lo leído cada día
  Estadisticas.kt   Las cuentas, sobre TUS ficheros
  Huecos.kt         Lo que falta entre el primero y el último que tienes
  EstadoSerie.kt    Lo tuyo cruzado con lo que la fuente dice que existe
  SeriesRemotas.kt  Qué volumen es cada carpeta, y sus números
  Novedades.kt      A quién volver a preguntar y de qué números avisar
  Vigilante.kt      La pasada compartida, la notificación y el trabajo diario
  Rastro.kt         Las migas de pan: qué estaba haciendo la app antes de fallar

red/
  FuenteComics.kt   Interfaz + elegirVolumen (la regla de elección)
  ComicVine.kt      La única implementación, y la única red que queda

ui/
  Tema.kt           El interruptor de estética y todos los tokens
  Componentes.kt    Cabecera, Grupo, Fila, Campo, Boton, Buscador,
                    BarraDesplazamiento, Interruptor, Segmentado,
                    caratula(), animaciones
  Pantallas.kt      Todas las pantallas
  Lector.kt         El visor
```

**Todo lo externo va detrás de una interfaz.** `FuenteComics`, `Enriquecedor` y
`FuenteVolumenes` permiten cambiar de proveedor tocando una línea en
`LectorApp`. Ya salvó el proyecto cuando hubo que abandonar Metron.

**Lo que tiene reglas se separa de la red** para poder probarlo con respuestas
reales guardadas, sin tocar Internet: `Wiki.interpretar`,
`Wiki.interpretarIndice`, `elegirVolumen`, `Eras.de`, `Racha`, `Busqueda.de`,
`Huecos.de`, `EstadoSerie.de`, `Parser.numeroDe`, `OrdenLectura.de` y
`Novedades`. **De esos, los que tienen fichero de prueba de verdad son cinco**:
ver §7.

---

## 4. Claves de API

En `local.properties` (git lo ignora), y Gradle las mete vía `BuildConfig`:

```
comicvine.clave=...
gemini.clave=...
gemini.modelo=          # opcional, por defecto gemini-flash-latest
```

También desde Ajustes; manda `local.properties`. **Las wikis no piden clave.**
Sin Comic Vine ni Gemini la biblioteca y el lector funcionan igual.

---

## 5. Trampas encontradas

### Kotlin y Compose
- **`private` a nivel de fichero es de FICHERO, no de paquete.** Dos ficheros
  del mismo paquete no se ven las funciones privadas del otro. `OpcionMenu` era
  privada de `Pantallas.kt` y el visor la necesitaba; el arreglo es moverla a
  `Componentes.kt`, no relajar la visibilidad donde estaba.
- **Los comentarios de bloque anidan en Kotlin.** Un `/*` dentro de un
  comentario (escribir `assets/*.json`) se come el resto del archivo.
- **La lambda final solo se engancha al ÚLTIMO parámetro.** Con un parámetro
  con valor por defecto detrás de la lambda, la llamada no compila y el error
  que sale ("no value passed", "unresolved reference it") no apunta a la causa.
  Por eso `repararNumeros(personaje, forzar, aviso)` lleva el `aviso` el último
  aunque sea el que se añadió antes: meter `forzar` detrás habría roto las dos
  llamadas de `Pantallas.kt` con un error que no señala al sitio.
  **Y por eso `onOrden` se añadió al final de `PantallaCarpeta`** (02/09/2026),
  detrás de `onAtras`, aunque agrupado con los demás `on...` quedaría más
  bonito.
- **`remember` no se puede llamar en el cuerpo de un `LazyColumn`**, solo dentro
  de un `item {}`.
- **Dos `pointerInput` sobre el mismo elemento: el primero se come los eventos
  del segundo.** Con zoom puesto se consumía cualquier movimiento, incluidos los
  dos o tres píxeles del doble toque, y el doble toque no llegaba nunca. La
  solución es no consumir hasta pasar `viewConfiguration.touchSlop` con un dedo.
- **`detectTransformGestures` consume todos los eventos** y bloquea el
  pasapáginas. Hace falta un detector propio con `awaitEachGesture`.
- **Leer `layoutInfo` de un `LazyListState` en la composición repinta en cada
  fotograma de scroll.** Va dentro de `derivedStateOf`.
- **Las teclas de volumen no llegan al árbol de Compose** salvo que algo tenga
  el foco. Se capturan en `onKeyDown` de la Activity, y hay que comerse también
  el `onKeyUp` o el sistema saca su barra igualmente.
- **`combinedClickable` y `ModalBottomSheet` son experimentales**: mejor el
  opt-in a nivel de módulo.
- **`getOffsetFractionForPage` es de Compose reciente.** El equivalente estable:
  `(estado.currentPage - i) + estado.currentPageOffsetFraction`.

### Rendimiento y memoria
### El tirón del 26/08/2026 y sus tres causas

Dani dijo "la app va un poco lageada". Se buscó en el código en vez de dar
consejos genéricos, y salieron tres cosas — **las dos primeras metidas ese mismo
día**:

1. **Una expresión regular por CARÁCTER.** `Parser.sinPrefijoDeCarpeta` llamaba
   a `normalizar` —que monta una cadena y pasa un `Regex`— una vez por cada
   carácter del nombre, y otra vez sobre el trozo entero al final. Eso corre por
   cada carta de la rejilla y en cada repintado: con la pantalla llena y haciendo
   scroll, miles de regex por segundo. Ahora hay un `normChar` que compara
   caracteres sin reservar memoria y corta en cuanto algo no cuadra.
2. **Decenas de consultas a SAF por repintado.** `portadasDe` hacía
   `comicsBajo(docId, ruta)` —una consulta por subcarpeta— **por cada fila de
   carpeta de la pantalla de inicio**, y se rehacía con cada cambio de `sello`
   (que sube al marcar una página, al volver del visor...). Ahora filtra el
   índice que `todosLosComics()` ya tiene cacheado: el disco se recorre una vez.
3. **Cálculos de cadenas sin `remember`.** El título recortado de cada carta y la
   etiqueta de cada portada se recalculaban en cada recomposición. Envueltos en
   `remember(uri, carpeta)`.

La moraleja no es ninguna de las tres por separado: es que **el trabajo por
elemento se multiplica por el número de elementos y otra vez por el número de
repintados**. Algo que "solo" cuesta una regex se convierte en miles.

### El tirón AL SCROLLEAR RÁPIDO del 25/08/2026, y otras tres

Arreglado lo de arriba, Dani volvió con algo más concreto: "en la biblioteca al
scrollear rápido se ve lag". Concreto de verdad, porque señala el momento: no es
que la app vaya lenta, es que **al volver a pasar por cartas que ya habías
visto** se ven grises un instante. Eso apunta a la caché, no al cálculo.

1. **La caché de miniaturas era de 30 entradas contadas de una en una.** Una
   miniatura de 220 px son unos 300 KB, así que eran 9 MB mal repartidos: en la
   rejilla de tres columnas caben doce a la vez y los carruseles de inicio gastan
   otras tantas, o sea que bajando rápido la caché se vaciaba sola y cada carta
   que volvía a entrar tenía que ir otra vez al disco a decodificar un JPEG.
   Ahora el límite es por TAMAÑO (ver arriba): más de cien portadas en un móvil
   de hoy.
2. **`Portada` suspendía aunque la respuesta ya estuviera en memoria.** Su
   `LaunchedEffect` llamaba a `vm.portada(uri)`, que es `suspend` y salta a un
   hilo de IO: uno o dos fotogramas con la carta en gris **aunque la miniatura
   estuviera hecha desde hacía rato**. Eso es exactamente el parpadeo que se ve.
   Ahora `Portada` recibe además `inmediato: (String) -> Bitmap?`
   (`Miniaturas.enMemoria`, un simple `LruCache.get`), lo mira SIN suspender al
   componer y solo lanza la corrutina si de verdad hay que ir a buscarla.
3. **Un `collectAsState` POR CARTA.** `TarjetaComic` y `FilaResultado` recogían
   cada una el `estado` del ViewModel para leer `sello`. Eso son dos cosas malas
   a la vez: una suscripción al flujo que se crea y se tira por cada carta que
   entra y sale de pantalla, y que **cualquier** cambio del estado repinta todas
   las cartas visibles. La pantalla ya recoge el estado una vez arriba; ahora el
   `sello` baja por parámetro.

La moraleja de esta tanda es distinta de la de la anterior: allí sobraba
trabajo, aquí sobraban **esperas y suscripciones**. Un `suspend` que devuelve al
instante sigue costando un salto de hilo, y en una lista eso se paga por
elemento y por fotograma. **Si el dato ya está en memoria, léelo en memoria.**

**Segunda pasada, porque con eso quedó "más suelto pero un poco lagueado".**
Lo que faltaba no era cálculo, era **basura por fotograma**:

4. **Un `Brush` nuevo por carta y por repintado.** El velo del pie de cada
   tarjeta y el degradado de fondo de la pantalla se montaban dentro del
   Composable. Un `Brush` no es una descripción inerte: al pintarlo se le pide
   su shader y **el shader se guarda dentro del objeto**, así que un objeto
   nuevo cada vez es un shader nuevo cada vez. Los degradados fijos —velo de la
   carta, rayas del escaneo— pasan a `val` de fichero, y el del fondo a
   `remember(ambiente)`. Se puede porque sus paradas son **fracciones**, no
   píxeles: valen para cualquier tamaño.
5. **Las miniaturas en RGB_565 y no en ARGB_8888.** La mitad de bytes en
   memoria (o sea el doble de portadas con el mismo techo) y **la mitad que
   subir a la GPU**, que es lo que se paga cuando entran doce cartas de golpe.
   Se pierde el canal alfa —que un JPEG no tiene— y precisión de color, que a
   220 px no se ve y a `ColorPortada` se le pierde en el redondeo a casillas de
   15 grados de tono.
6. **`asImageBitmap()` en cada recomposición.** Envuelve el `Bitmap` en un
   objeto nuevo de Compose cada vez que se llama. Ahora va en `remember(b)`.
7. **`chunked(3)` y un `filter` en el cuerpo del `LazyColumn`.** Se rehacían
   con cada recomposición de la pantalla: con quinientos cómics, ciento setenta
   listas nuevas para la basura. Van en `remember` **fuera** del `LazyColumn`,
   porque su cuerpo no es `@Composable` y `remember` no se puede llamar ahí
   (ver la lista de trampas de Compose más arriba). **La pantalla de orden
   repite el patrón**: el filtro de "lo que me falta" va en un `remember` fuera
   de la lista.

**Lo que queda sabido y sin tocar**, por si vuelve a hacer falta: las cartas
reciben `vm: VistaModelo` por parámetro, y un ViewModel es un tipo **inestable**
para Compose, así que `TarjetaComic` y `FilaResultado` **nunca se pueden
saltar**: cada recomposición de la pantalla las repinta enteras. No se nota
haciendo scroll —ahí la pantalla no se recompone— pero sí con cada cambio de
`sello`. Arreglarlo es dejar de pasar el ViewModel y pasar solo lo que hace
falta, y eso es un cambio grande. **Y si vuelve a haber tirón: hay que MEDIRLO**
con las cuentas de recomposición del Layout Inspector de Android Studio, no
seguir adivinando. En esta segunda pasada ya se estaba adivinando: cada punto
de la lista es una causa real y comprobable leyendo el código, pero **cuál de
ellas pesaba de verdad no lo sabe nadie**.

### El sello partido en dos: progreso y catálogo (25/08/2026)

**El problema.** El `Estado` tenía un solo `sello` que subía cuando se guardaba
cualquier cosa — una página leída, un marcador, un ajuste, una búsqueda
reciente: **25 sitios**. Y `PantallaCarpeta` lo llevaba en el `remember`:

```kotlin
var contenido by remember(docId, estado.sello) { mutableStateOf(null) }
LaunchedEffect(docId, estado.sello) { contenido = vm.abrirCarpeta(docId, ruta) }
```

Resultado: **leías una página, salías del cómic, y la biblioteca se vaciaba,
sacaba la rueda de carga y volvía a preguntarle a SAF por una carpeta que no
había cambiado.** Lo mismo el banner de "seguir leyendo", la fila de "en curso",
la barra de abajo y cada fila de carpeta, cada una con su propia rueda.

**El arreglo, en dos mitades que van juntas:**

1. **`Estado.catalogo`**, que sube SOLO cuando cambian los ficheros. La regla
   para clasificar: *¿cambia lo que HAY en el disco, o solo lo que sabemos SOBRE
   ello?* Lo primero sube el catálogo; lo segundo, solo el sello. De los 25
   puntos, **solo tres** cambian ficheros: `elegirCarpeta`, `convertirCbr` y
   `limpiarBiblioteca`. En caso de duda, catálogo: recargar de más es lento,
   pero recargar de menos es enseñar algo que ya no existe.
2. **El sello sale del `remember` y se queda solo en el `LaunchedEffect`.** Esto
   es la mitad que de verdad quita el parpadeo, y vale para lo que sí tiene que
   reaccionar al progreso. Con la clave en el `remember`, el valor se tira y se
   ve el hueco mientras se recalcula; con la clave solo en el efecto, **se
   sustituye lo que hay sin borrarlo antes**. Misma frescura, sin rueda.

`contenido` escucha solo el catálogo. `portadasDe`, `enCurso`, `seguirLeyendo` y
el buscador escuchan los dos, pero ya sin vaciarse: salen del índice en memoria,
que es barato — lo caro era la rueda, no el cálculo.

### Lo que rompió partir el sello: los ficheros que llegan por fuera

Dani, el mismo día: *"acabo de añadir el 34 y no se ha actualizado"*. Culpa
directa del cambio anterior. Con el sello partido, la carpeta se relee solo
cuando **la app** cambia ficheros — convertir, limpiar, cambiar de raíz. Pero
los ficheros también cambian **por fuera**: copias un número al móvil y la app
ni se entera.

Lo bueno es que ese caso tiene un momento exacto y siempre el mismo: **pasa
mientras la app no está delante.** Nadie copia cómics con el lector abierto. Así
que volver a la app es justo cuando toca mirar, y ya existía `enPrimerPlano()`
—escrito para parar las animaciones— que dice exactamente eso.

**Se relee SOLO la carpeta que estás mirando**, que es una consulta a SAF. Tirar
el índice entero significaría recorrer el árbol completo cada vez que vuelves de
mirar un mensaje, y eso sí se nota. Para el repaso a fondo hay un botón aparte
en Ajustes (`repasarBiblioteca`), que tira el índice y vuelve a buscar CBR.

**La lección:** al separar una señal en dos, lo que se rompe no es lo que la
señal hacía — es lo que hacía **de rebote**. El sello subía tan a menudo que la
carpeta se refrescaba por accidente, y nadie se había dado cuenta de que ese
accidente era la única forma que tenía la app de ver un fichero nuevo.

### R8 activado en release, y por qué se puede

`isMinifyEnabled = true` + `isShrinkResources = true` + `proguard-rules.pro`.

R8 borra lo que nadie llama y renombra lo que queda, y eso funciona porque ve
quién llama a qué. **Lo único que se le escapa es lo que se busca por NOMBRE en
tiempo de ejecución: reflexión y código nativo.** En esta app hay una cosa así y
solo una: **7-Zip-JBinding**, cuyo motor RAR5 busca las clases Java desde C++.
Sin `-keep`, la app compila igual de bien y revienta al abrir el primer CBR con
un `NoSuchMethodError` que no dice de dónde sale.

Lo que **no** hace falta conservar, y conviene saber por qué: el JSON de las
listas y de las APIs se monta y se lee a mano con `org.json.JSONObject`, campo
por campo. **No hay ninguna clase que se rellene por reflexión**, así que los
modelos no necesitan reglas. Si algún día entra kotlinx.serialization o Gson,
esto deja de ser verdad y hay que volver aquí.

**Al probar release hay que probar dos cosas sí o sí**: abrir un CBR (motor
nativo) y crear una lista (red + JSON). Son los dos sitios donde un fallo de R8
no aparece al compilar sino al usar.

### 3,78 GB de caché: cómics enteros que nadie borraba (26/08/2026)

Dani, mirando el almacenamiento del móvil: "la caché ocupa 3,78 GB". **Era un
bug de verdad, y el más caro de toda la sesión en espacio.**

`Rar5.aCbz` fabrica el CBZ en `cacheDir/convertidos` y lo deja ahí para no tener
que rehacerlo la próxima vez que se lea ese cómic. Eso está bien **cuando se
LEE** un CBR. El problema son las otras dos partes:

1. **`ConversorCarpeta` usaba la misma función** y dejaba la copia ahí. Pero al
   convertir, el CBZ bueno acaba de quedarse en la carpeta del usuario: esa
   copia es **redundante desde el segundo en que se escribe**. Convertir la
   biblioteca entera dejó un duplicado de cada tomo.
2. **La caché no tenía techo.** Nunca se borraba nada. Y lo que se guarda ahí no
   son miniaturas: es un tomo entero por comic — Blackest Night solo son 366 MB.

Tres arreglos, y hacen falta los tres:

- `ConversorCarpeta` llama a `Rar5.olvidar(uri)` **pase lo que pase** al acabar
  cada cómic. Si la escritura salió mal, el original sigue ahí y volver a
  convertirlo es darle otra vez al botón: guardar gigas por si acaso no compensa.
- `Rar5.podar()` con un techo de **500 MB**, del menos usado al más usado
  (`lastModified`). Así el que estás leyendo es el último en caer.
- Una tarjeta en Ajustes que **enseña cuánto ocupa** y deja borrarlo. Esto es lo
  que faltaba de verdad: `Rar5.tamano` y `Rar5.limpiar` existían desde el
  principio "para poder decirlo en Ajustes" y **nunca se conectaron a la
  interfaz**. Un número que no se ve no lo vigila nadie, y por eso llegó a 3,78
  GB sin que saltara ninguna alarma.

**La lección:** una caché sin techo no es una caché, es una fuga lenta. Y una
función de diagnóstico que se escribe "para enseñarla en Ajustes" y se queda sin
conectar no vale nada — peor, da la falsa sensación de que el problema está
cubierto.

**Segunda pasada, buscando la misma forma de bug en el resto de la app.** Y
estaba, dos veces:

- **Las miniaturas en disco tampoco tenían techo.** Misma fuga en pequeño: una
  por cómic, unos 20 KB, y **no se borra nunca ninguna** — ni siquiera la de un
  fichero que ya has borrado tú. Techo de 150 MB (unas siete mil portadas),
  podando de la menos usada a la más usada, y comprobándolo cada 50 escrituras
  porque listar la carpeta entera cuesta.
- **`Miniaturas.limpiar` tampoco estaba conectada a nada**, exactamente igual
  que `Rar5.limpiar`. Dos funciones de mantenimiento huérfanas escritas con
  meses de diferencia: no fue un despiste, es un patrón. Ahora las dos tienen su
  contador y su botón en la misma tarjeta de Ajustes.

Y un techo por tamaño **no basta solo**: si conviertes tres tomos y no los
vuelves a abrir, se quedan ahí para siempre porque nunca llegan al límite. Los
convertidos caducan además **a los 14 días sin abrirlos**.

**Lo que se descartó a propósito:** quitar el fichero intermedio y escribir el
CBZ directamente al `OutputStream` de SAF. Ahorraría escribir cada tomo dos
veces y no necesitaría espacio temporal, pero **rompe la garantía de todo o
nada**: hoy, si la conversión falla a mitad, lo que queda es un `.parcial` en la
caché y en tu carpeta no se ha tocado nada. Escribiendo directo, un fallo deja
un CBZ truncado entre tus cómics. El problema real era no borrar, y eso ya está
resuelto: no compensa cambiar por esto un diseño que ya funciona en la
biblioteca de Dani.

## El apartado de lecturas, del revés (26/08/2026)

Dani: *"lo del apartado de lecturas no me acaba de casar"*. No era un fallo, era
la función entera. Y el diagnóstico, una vez puesto en llano, era claro: **el
resto de la app va de TUS cómics** —tu carpeta, tus portadas, por dónde ibas— y
ese apartado iba del catálogo mundial de DC y Marvel, dependiendo de una wiki,
de Comic Vine y de un modelo que opina. Era casi otra app metida dentro.

Lo que pidió en su lugar, con sus palabras: que de lo que tiene le diga **"está
completa o te faltan N"**, que avise de las **que están en emisión** y **notifique
cuando salga un número nuevo**; y un **orden de lectura que intercale entre
series** — GL Vol.4 #1-5, luego todo *Corps Recharge*, luego GL Vol.4 hasta el
#10 — marcando por el camino lo que no tiene.

**La clave técnica del orden intercalado**: Comic Vine da la **fecha de portada**
de cada número. Ordenando los números de todas sus series por esa fecha, el
intercalado sale solo — es literalmente como se leen los crossovers, porque se
publicaron para leerse así. No hace falta que nadie opine.

**Y lo que hay que decirle claro**: orden de publicación **no es** orden de
lectura perfecto. Clava el 90%, pero hay tie-ins que van antes o después de lo
que su fecha sugiere. Esto no va a ser una guía curada de foro; va a ser una
aproximación buena **que sabe decir de dónde sale cada dato**.

Plan en cuatro trozos, usables por separado:

1. **Huecos, sin red** ← hecho
2. **Completa / en emisión**: los números reales de Comic Vine ← hecho
3. **El orden intercalado**, cruzado con las carpetas ← hecho (02/09/2026)
4. **Aviso de número nuevo** ← hecho (02/09/2026), con notificación de verdad
   y con la app cerrada. Ver §"Las notificaciones de número nuevo".

### El punto 2: los números reales, y el vínculo separado del dato

Tres piezas nuevas:

- **`VolumenRemoto.id`**, que no existía. Sin él no se pueden pedir los números,
  que se piden por id. Y hubo que añadirlo también al `field_list` de las tres
  búsquedas: **Comic Vine no manda lo que no le pides**, ni siquiera el id.
- **`ComicVine.numerosDe()`**, que trae todos los números con su fecha de
  portada **paginando de 100 en 100**. Sin paginar, *Detective Comics* diría que
  tiene 100 y la app se creería que la tienes completa con 100. El número de
  cada issue viene como TEXTO —hay "1.MU", "Annual 2", "-1"— y un número que no
  se entiende queda como **ausente, nunca como cero**: un cero de mentira abre
  un hueco falso.
- **`SeriesRemotas`**, que guarda en disco qué volumen es cada carpeta.

**El vínculo se guarda aparte de los números, y eso es lo importante del
diseño.** `volumenId` es una *decisión* —"esta carpeta ES esta serie"— que puede
estar mal, porque `elegirVolumen` falla de vez en cuando y ya está documentado
que falla. Los números son un *dato* que se puede volver a pedir sin perder
nada. Separados, se corrige el vínculo sin tirar lo demás y se refrescan los
números de una serie viva sin tocar el vínculo. **El punto 4 depende de esa
separación**: refrescar los números de una serie en emisión no toca el vínculo.

**Y en la pantalla siempre se ve de dónde sale el dato**: debajo del estado pone
*"según «Green Lantern» (2005) · no es esta"*. Si la carpeta dice Vol. 4 y ahí
aparece un año que no cuadra, se ve al momento y se deshace de un toque.
**Enseñar de dónde sale un dato es lo que permite desconfiar de él**, y esta es
justo la parte del proyecto donde ya se ha metido la pata confiando de más.

`EstadoSerie` es función pura con 15 pruebas. Aporta sobre `Huecos` lo que
`Huecos` no puede saber: **los extremos**. Que tienes hasta el #58 de una de 62,
y que empiezas por el #5 de una que empieza en el #1.

**El corte de "en emisión" son 120 días, no 30**, y la razón importa: la fecha
de Comic Vine es la de PORTADA, que en los cómics americanos va dos o tres meses
por delante de la de venta. Con un mes, cualquier serie viva parecería terminada
la mitad del tiempo. **El punto 4 usa ese mismo corte**, y en un solo sitio:
`VistaModelo.fechaCorte()`.

---

## La amputación del 02/09/2026

En una sola tarde salieron de la app **el orden de lectura** (escrito esa misma
mañana) y **el TODO por personaje** (el trabajo de varias sesiones). Los dos por
la misma razón, dicha por Dani: *"al final no lo voy a usar"* y *"el apartado de
lecturas que sea más estadísticas y ese tipo de cosas"*.

Conviene dejar escrito **qué se fue y por qué**, porque es mucho código bueno y
alguien va a preguntarse si fue un error.

### Lo que se fue

| Fichero | Qué hacía |
|---|---|
| `datos/Listas.kt` | el almacén del TODO y del marcado por serie |
| `datos/GeneradorLista.kt` | wiki + Comic Vine + Gemini → lista verificada |
| `datos/Vinculador.kt` | casaba tus carpetas con las series del TODO |
| `datos/Eras.kt`, `datos/Edades.kt` | etapas y edades por año |
| `red/Wiki.kt` | el índice de series por personaje de Marvel y DC |
| `red/Enriquecedor.kt` | el criterio, con Gemini |
| `datos/OrdenLectura.kt` | el intercalado por fecha de portada |
| 9 pantallas | Listas, Lista, CrearLista, Eras, AñadirSerie, Autovincular, Vincular, Orden, FilaTramo |

Están en `_borrar_a_mano/`, no borrados: `listas_y_todo/` y `orden_de_lectura/`.

### Por qué no fue un error escribirlo

Porque **es lo que enseñó el principio rector del proyecto**. Toda la lección de
"los datos de la base de datos, el criterio del modelo" se aprendió peleándose
con Gemini y con las wikis; toda la lección de "un cero puede significar dos
cosas" salió del 420 de Comic Vine repasando 268 series de Batman. Esa
experiencia se queda aunque el código se vaya, y está en este documento.

Y porque el diagnóstico ya estaba escrito desde agosto, dos veces: *"lo del
apartado de lecturas no me acaba de casar"*, y el análisis de entonces —**el
resto de la app va de TUS cómics y ese apartado iba del catálogo mundial**— era
correcto. Lo que pasó en septiembre fue terminar de aplicarlo. La señal
definitiva estaba en la propia pantalla: "batman · 219 series · **0 de 2411
números**". Una cifra que no es tuya y que no se puede completar nunca.

### Qué NO se fue, y es importante no confundirlo

**Comic Vine se queda entero.** El vínculo carpeta ↔ volumen
(`SeriesRemotas`) es otra cosa que las listas: de ahí salen "te faltan 5 de 18",
los huecos, "en emisión", el seguir series y las notificaciones. Todo eso va de
**tus carpetas**, que es de lo que va la app.

Lo que desapareció es el catálogo mundial de un personaje y todo lo que hacía
falta para construirlo.

### Las estadísticas, rehechas

`Estadisticas.calcular` recibía el progreso **y las listas**. Ahora recibe el
progreso **y los cómics de tu biblioteca**. Cambios que van con ello:

- **El avance se cuenta sobre lo que tienes.** "87 de 120" en vez de "15 de
  796". Una es una cifra que puedes terminar; la otra, no.
- **Y se NAVEGA por niveles, no se adivina la estructura.** La primera versión
  cogía el primer tramo de la ruta y lo llamaba "personaje", dando por hecho el
  árbol Personaje / Serie / números. En la biblioteca de Dani el primer tramo
  resulta ser la **editorial** —"DC Comics", "Marvel"— así que la pantalla decía
  *"DC Comics · 15 de 208"*: cierto y completamente inútil. Ahora
  `Estadisticas.avance(progreso, comics, base)` devuelve un nivel, tocas una
  fila y bajas al siguiente. Vale para el árbol que tenga cada uno hoy y para el
  que monte mañana — el mismo requisito que Dani ya había puesto para Comic
  Vine: *o vale para cualquiera, o no vale*.
  Lleva su propio `BackHandler`, porque es estado de navegación fuera del
  NavHost y el gesto de atrás se lo salta (la trampa ya está documentada para la
  pila de carpetas).
- **El progreso de cómics que ya no tienes no cuenta.** El fichero de progreso
  guarda marcas de ficheros borrados, y contarlas daba "142 leídos" en una
  biblioteca de 90. La racha sí sale de todo el progreso: haber leído ese día es
  un hecho y borrar el fichero después no deshace el día.
- **Se cae la explicación de "no tienen por qué cuadrar"**, porque ya no hay dos
  cifras contra cosas distintas. Un texto menos que mantener.

### El diario de lectura (03/09/2026)

`datos/Sesiones.kt`, con la regla pura en `Lectura` y `LecturaTest`.

**Lo pidió Dani con un caso concreto:** *"si leo lunes miércoles y viernes un
mismo cómic que salga en los 3 días"*. Con `Marca` no se podía: guarda **una sola
fecha por cómic** —la última vez que lo tocaste— así que un tomo repartido en
tres tardes salía solo el viernes. Estaba escrito como limitación del calendario
y resultó ser justo lo que hacía falta.

**Una fila por cómic y día**, no por página: es lo que hace que el fichero no
crezca sin control leyendo, y sigue contestando las dos preguntas —qué días leí y
cuánto— sin guardar un registro por pasada de página.

**Se cuentan las páginas NUEVAS, no las llamadas.** El visor avisa en cada cambio
de página, así que contar llamadas daría números disparatados solo con pasar
adelante y atrás mirando una viñeta. Se suma la **diferencia** contra lo más
lejos que habías llegado — y eso cuadra solo en el modo de dos páginas, donde
cada pasada avanza dos y suma dos.

**Ir hacia atrás no suma ni resta.** Releer una página no es leer una página
nueva, pero tampoco deshace lo leído: el día sigue contando y solo se refresca la
hora.

**Tope de 6000 filas**, podando por lo más viejo. Con cinco cómics al día son
unos tres años, y un calendario no se mira más atrás. Un diario sin tope es una
fuga lenta — la lección que ya costó 3,78 GB de caché.

**Y el tramo, no solo la cuenta (03/09/2026).** Dani: *"el calendario me
gustaría que me dijera, leídas páginas 3-4 por ejemplo"*. La `Sesion` ya
guardaba `desde` y `hasta` —hacían falta para contar las páginas nuevas sin
sumar los idas y venidas— así que el dato estaba ahí desde el primer día y solo
faltaba enseñarlo: `Calendario.Leido.tramo` da *"págs. 4-16"*, o *"pág. 4"* en
singular cuando el tramo es de una sola. **El total sigue debajo en pequeño**
porque las dos cifras no tienen por qué cuadrar: releer no suma, así que un
tramo de la 3 a la 40 puede ser de 12 páginas nuevas. Un tramo sin la cuenta
mentiría; la cuenta sin el tramo no dice qué leíste.

### La copia de seguridad sube a versión 3

Ya no guarda listas, y desde el 03/09/2026 guarda también el **diario de
lectura**. Todo eso es justo **lo único que no se le puede volver a pedir a
nadie**, así que esta copia importa más ahora que antes, no menos. Sigue
guardando por carpeta+nombre y no por uri.

**Una copia de la v2 se sigue restaurando**: lo que no venga en el fichero
simplemente no se toca. Un formato nuevo no puede dejar inservible el respaldo
que ya tenías.

### Y se hace sola al salir (02/09/2026)

Dani: *"¿cómo puedo hacer una copia de seguridad que se haga automática cada vez
que salgo de la app?"*.

- **Se dispara en `ON_STOP`, no en `ON_PAUSE`.** Pausa también ocurre al abrir
  un diálogo del sistema o al bajar la persiana de notificaciones, y eso no es
  salir de la app.
- **Hace falta el ÁRBOL de SAF con permiso persistente**, no el selector de
  "guardar como". Los uris que da ese selector son **de un solo uso**: mañana la
  app no puede volver a escribir ahí. Por eso hay que elegir una carpeta una vez
  desde Ajustes.
- **Si no ha cambiado nada, no se escribe.** Se compara la marca más reciente
  con la de la última copia. Sin eso, cada abrir y cerrar reescribe el fichero —
  y si esa carpeta la sincroniza otra app, cada reescritura es una subida y una
  versión nueva en la nube para nada.
- **Se escribe con `"wt"`, que TRUNCA.** Con `"w"` a secas, una copia más corta
  que la anterior deja la cola de la vieja pegada al final y el JSON no se puede
  leer. Es un fichero que solo se lee el día que hace falta, así que un fallo así
  no se descubre hasta el peor momento posible.
- **El fichero se busca por PREFIJO dentro de la carpeta**, no por nombre
  exacto: SAF le pone su extensión al fichero que creas y unos proveedores dejan
  "lector-copia" y otros "lector-copia.json". Es la misma trampa que ya costó
  las chapas de los números al convertir los CBR.

### Google Drive: por qué no se puede elegir una carpeta suya

Dani preguntó por sincronizar contra Drive. **El selector de Android no deja
elegir una carpeta de Google Drive**: su proveedor de documentos da ficheros
sueltos, pero no soporta el modo ÁRBOL (`ACTION_OPEN_DOCUMENT_TREE`), que es lo
que hace falta para poder volver a escribir ahí sin preguntar cada vez.

Lo que sí funciona es cualquier carpeta **local** que otra app mantenga
sincronizada — Dropbox, Nextcloud, Syncthing, FolderSync—: esas son carpetas de
verdad del sistema de ficheros y el árbol funciona. La app no tiene que saber
nada de la nube; escribe un fichero en una carpeta y ya.

**Meter un cliente de Drive dentro de la app está descartado por ahora**: son
credenciales de Google, consentimiento OAuth y una dependencia grande, para algo
que una app de sincronización ya hace mejor y sin que este proyecto tenga que
mantenerlo.

## Las notificaciones de número nuevo (02/09/2026)

Tres piezas: `datos/Novedades.kt` (puro, con `NovedadesTest`), `datos/Vigilante.kt`
(la pasada, la notificación y el trabajo diario) y el interruptor de "seguir"
en la tira de la serie.

### Entra WorkManager, y hay que decir por qué se puede

Este documento decía **"ni servicios, ni WorkManager, ni wakelocks"**. Esa regla
se escribió el día que se descubrió que el móvil se calentaba, y lo que
calentaba era **una animación infinita repintando a 120 Hz con la app quieta**:
trabajo continuo. Un `PeriodicWorkRequest` diario con restricción de red es otra
cosa — el sistema despierta la app una vez al día, hace tres o cuatro peticiones
y la vuelve a dormir— y es literalmente para lo que WorkManager existe.

**La regla vieja sigue valiendo para todo lo demás**: sigue sin haber servicios
en primer plano, ni wakelocks, ni nada que se despierte cada pocos minutos. Lo
que cambia es que había una regla escrita por un motivo que no aplicaba a este
caso, y se ha comprobado antes de saltársela en vez de después.

`ExistingPeriodicWorkPolicy.KEEP` y no `REPLACE`: con REPLACE, cada arranque de
la app tiraría el trabajo programado y empezaría a contar el día otra vez. Quien
abre la app a diario **no recibiría un aviso nunca**.

### "En cuanto lo ponen en Comic Vine" no es "ha salido"

Dani lo pidió así, suponiendo que dar de alta el número en Comic Vine equivale a
que esté en la tienda. **No lo es**: Comic Vine da de alta los números cuando se
**anuncian**, unos tres meses antes de que lleguen a la tienda. Avisar al
aparecer el registro habría significado recibir "Absolute Batman #12" en junio
para un cómic que se compra en septiembre. **Se le planteó y eligió avisar
cuando esté a la venta.**

### `store_date`, y la misma trampa por tercera vez

La primera versión estimaba la fecha de venta restándole dos meses a la de
portada, **porque se dio por hecho que Comic Vine solo tenía la de portada**.
Dani preguntó lo obvio —"¿y cómo sabe cuándo está en tiendas?"— y bastó abrir la
documentación:

> **`store_date`** — *"The date the issue was first sold in stores."*

Existe. No se estaba pidiendo. `numerosDe` mandaba
`field_list=issue_number,cover_date,name` y **Comic Vine no manda lo que no le
pides** — que es exactamente la lección que este documento ya tenía escrita para
el `id` de los volúmenes, y que volvió a costar lo mismo.

Ahora se piden las dos y `NumeroRemoto` guarda las dos, **que no es lo mismo que
tener una**:

| Para | Qué fecha | Por qué |
|---|---|---|
| Ordenar el orden de lectura | portada (`cover_date`) | la tienen todos los números |
| Avisar de que ha salido | venta (`store_date`) | es la de verdad, pero falta a menudo |

Mezclarlas en un solo campo habría sido peor que no tener la segunda: la lista
de lectura saltaría dos meses cada vez que un número tuviera una y el siguiente
la otra.

`store_date` **viene vacía en muchos números** —Comic Vine empezó a guardarla
tarde— así que la estimación no desaparece: pasa a ser el **respaldo**. Un dato
real siempre gana a una convención, y nunca al revés. Cuánto de a menudo falta
en las series de Dani **no se sabe todavía**: desde aquí no hay red a Comic Vine
y no se ha comprobado contra una respuesta real.

**Y la app dice cuál de las dos ha usado**, con una palabra de diferencia:
*"ya está en tiendas"* cuando la fecha es la de verdad, *"ya debería estar en
tiendas"* cuando ha habido que calcularla. Es la regla de siempre del proyecto
—enseñar de dónde sale el dato— llevada a una notificación.

Así que `Novedades.aAvisar` reparte cada número en tres:

- **Avisar**: su fecha de venta estimada ya ha pasado.
- **Callar**: su fecha de venta es de hace más de 240 días
  (`Novedades.CADUCIDAD`). No es novedad, es Comic Vine completando fichas
  viejas. Se marca como visto sin notificar: mejor callar un caso raro que
  despertar el móvil por una grapa de hace dos años.
- **Pendiente** (ni una cosa ni la otra): el anunciado que aún no ha salido —el
  caso normal, y la razón de que esta función exista— y **el que no tiene
  fecha**, porque sin fecha no hay forma de saber cuándo sale y colocarlo por
  comodidad sería inventarse el dato. Misma regla que en `OrdenLectura` y en
  `Huecos`.

**`Novedades.ADELANTO_PORTADA = 60` es una CONVENCIÓN, no un dato**, y está
puesta con nombre y explicación por eso. Es de la misma familia que `Edades`:
útil, y rotulada como lo que es. Solo se usa cuando falta `store_date`.

### "El siguiente sale el 2 de septiembre"

Dani, viendo el aviso: *"lo que quiero es que si un cómic sale el 2 de
septiembre en España me diga, el siguiente sale el 2 de septiembre en España"*.

Son dos cosas y las dos se hicieron:

**Decir la fecha, no solo que ha salido.** Donde antes ponía "Sigue en emisión,
así que irán saliendo más" —que no dice nada— ahora pone **"El siguiente, el
#21, sale el 30 de septiembre"**. `Novedades.proximo` coge el que antes salga de
los que quedan, no el de número más alto: con dos anunciados a la vez, el que
viene es el que llega primero. Y el verbo cambia igual que en la notificación:
*"sale"* con la fecha de verdad, *"debería salir"* con la estimada.

**Y que sea la fecha española.** Aquí hay que ser claro: **no existe ninguna
fecha española que consultar.** Comic Vine es una base de datos americana;
`store_date` es el día que la grapa sale allí y no guarda nada de las ediciones
de aquí. Lo que hay es `Novedades.DESFASE_ESPANA`, **puesto a cero**, y ese cero
es una decisión: para lo que lee Dani —números USA sueltos, no tomos de Panini
ni de ECC— el día es el mismo o casi, porque lo digital sale a la vez y la
importación llega esa misma semana. Está con nombre y explicación para que
cambiarlo sea una línea el día que se vea que llega tarde.

**Lo que sigue sin poder hacerse**: dar la fecha de una edición española. Salen
meses después, con otra numeración, y Comic Vine no las tiene. Haría falta otra
fuente.

### La fecha es la de España, no la del móvil

`Novedades.ZONA = ZoneId.of("Europe/Madrid")`, y de ahí sale `Novedades.hoy()`,
que es lo único que mira el calendario en toda esta parte. `LocalDate.now()` a
secas usa la zona del sistema, y eso rompe de dos maneras:

- **El trabajo lo despierta el sistema a cualquier hora.** En UTC, a las dos de
  la mañana de un miércoles en Madrid todavía es martes: un número que sale el
  miércoles no se avisaría hasta el jueves.
- **Si el móvil cambia de zona** —Dani tiene un viaje a Japón, que son nueve
  horas por delante— los avisos se adelantarían un día entero respecto al
  calendario con el que cuenta los días.

El corte de "en emisión" del ViewModel también pasa por ahí: era un
`SimpleDateFormat` sobre la zona por defecto y ahora es `Novedades.hoy()`. **Un
solo sitio decide qué día es hoy.**

### La línea de salida al empezar a seguir

Al pulsar "seguir", **todo lo que ya existe se da por visto**
(`Novedades.etiquetasDe` → `Ficha.avisados`). Sin eso, seguir una serie de
sesenta números suelta sesenta notificaciones esa misma noche, y una sola vez
que pase eso el usuario apaga las notificaciones de la app **para siempre**.

`avisados` se guarda **aparte de los números**, por el mismo motivo por el que
`volumenId` se guarda aparte de ellos: son cosas con vidas distintas. Los
números se vuelven a pedir enteros cada pasada; `avisados` es la memoria de lo
que ya se ha contado, y perderla significa repetirse.

Al **dejar** de seguir no se borra `avisados`: si vuelves a seguirla, no tiene
sentido que te cuente los meses que estuviste fuera.

### Una sola pasada para los dos sitios

`Vigilante.pasada` la llaman dos cosas: la app al abrirse (repaso lento de toda
la biblioteca, tres series por pasada, para que el "te faltan N" no se quede
rancio) y el trabajo diario (solo las seguidas, tope 12, para notificar). Es la
misma consulta, la misma comparación y el mismo guardado; **lo único que cambia
es a quién se pregunta y qué se hace con el resultado**.

Escribirlo dos veces habría sido la forma segura de que las dos versiones se
separaran. Y como las dos escriben en el mismo `avisados`, **la que llegue
primero deja a la otra sin nada que decir: no hay aviso doble**.

`pasada` no notifica ni toca la interfaz — devuelve de qué hay que avisar y
quien la llama decide si eso es un diálogo o una notificación. Va entera en
`Dispatchers.IO`: el cliente de Comic Vine ya se cambia de hilo solo, pero esto
además **escribe el JSON de las fichas una vez por serie**, y desde el ViewModel
eso caería en el hilo principal.

### El presupuesto, que sigue siendo el diseño

Comic Vine tarda unos diez segundos por petición y corta con un 420 pasadas unas
doscientas por hora. Dos velocidades:

- **Seguidas**: cada 20 horas, hasta 12 por pasada. Son pocas y elegidas.
- **El resto**: cada 3 días, 3 por pasada, la más vieja primero, y solo con la
  app abierta.

Y en las dos, **solo las series en emisión**, con el mismo corte de 120 días de
`EstadoSerie`. Las 20 horas y no 24: con 24 clavadas, un trabajo diario que se
ejecuta unos minutos antes que el día anterior se salta la comprobación un día
sí y otro no.

**`Novedades.fiable` es lo que impide perder los números de una serie.** Cuando
Comic Vine corta con un 420, el cliente devuelve una lista vacía, que es
indistinguible de "esta serie no tiene números". Una respuesta con **menos**
números que los guardados es sospechosa por definición —una serie no pierde
grapas— así que no se guarda. Es la tercera vez que este proyecto tropieza con
"un cero puede significar dos cosas".

### Lo que hay que saber antes de prometer nada

- **"Una vez al día" es lo que se PIDE, no lo que se garantiza.** Android agrupa
  los trabajos, respeta el ahorro de batería y con el móvil en reposo profundo
  puede retrasarlo horas. El aviso llega con un día de margen, no a la hora en
  punto.
- **El permiso de notificaciones se pide al seguir la primera serie**, no al
  arrancar. Desde Android 13 se piden en marcha y el sistema solo deja preguntar
  una o dos veces: gastar la pregunta en el primer arranque, cuando el usuario
  aún no sabe para qué las quiere la app, es como se consigue un "no"
  permanente.
- **Seguir una serie funciona aunque deniegue el permiso.** Son dos cosas: seguir
  es una decisión que se guarda, notificar es un permiso del sistema. Si lo
  deniega se sigue guardando y comprobando y se **dice en pantalla** que el aviso
  no va a salir, en vez de dejar un interruptor que parece encendido y no hace
  nada.
- **El icono de la notificación tiene que ser monocromo con transparencia**
  (`res/drawable/ic_aviso.xml`, el único drawable del proyecto). Android le pone
  su propio color: cualquier dibujo a color —incluido un `android.R.drawable`
  cualquiera o el icono de la app— sale como un cuadrado blanco.
- **El canal se crea en `LectorApp.onCreate`**, antes de que nadie pueda
  notificar. Notificar sin canal se descarta en silencio, que es el fallo que
  hace pensar que el aviso no funciona.
- **Una notificación por serie**, con el id sacado de la ruta. Dos números de la
  misma serie se juntan en un aviso ("#12 y #13") y el de mañana **sustituye** al
  de hoy en vez de acumularse.

### Lo que sigue sin poder hacerse

Que el aviso llegue **el día exacto** que la grapa entra en la tienda. Comic Vine
no guarda la fecha de venta, solo la de portada; los dos meses son una
estimación. Si algún día hiciera falta clavarlo, haría falta otra fuente (las
listas de novedades semanales de las distribuidoras), y eso es otro proveedor
detrás de otra interfaz, no un ajuste.

### La pantalla en negro: el NavHost sin destinos (03/09/2026)

**La causa real: pulsar "Atrás" dos veces seguidas vaciaba la pila de
navegación.** Lo dijo Dani con sus palabras — *"si le doy mucho... se pone la
pantalla en negro"*— y era literal:

- Primer toque: `popBackStack()` saca "lecturas" y vuelve a "inicio".
- Segundo toque, con la transición todavía en marcha: el botón de la pantalla
  saliente **sigue ahí** y vuelve a llamar. Esta vez saca **"inicio"**.
- Con la pila vacía, el NavHost no tiene ningún destino que pintar. **No es que
  la pantalla falle: es que no hay pantalla.** De ahí el negro absoluto y que no
  responda a nada — no hay interfaz a la que tocar.

**Dos cerrojos, y hacen falta los dos.** El primero solo deja navegar al destino
que está `RESUMED`: durante una transición ninguno lo está, así que el segundo
toque no hace nada. El segundo es incondicional: **si no hay destino debajo del
actual, no se saca**, da igual quién llame ni cuántas veces. El primero depende
de ganar una carrera; el segundo, no.

Vale igual para `navigate`: dos toques seguidos apilaban la misma pantalla dos
veces.

**Y el 03/09/2026 se le quitó el suelo al fallo, no solo los cerrojos**: las tres
pestañas dejaron de ser destinos apilados. Ver la sección siguiente.

### Deslizar entre pestañas: tres destinos se vuelven uno (03/09/2026)

Dani: *"me gustaría poder pasar entre el inicio, la biblioteca y así deslizando
hacia el lado"*.

**Y eso no se arregla con un gesto: se arregla con la estructura.** Biblioteca,
Lecturas y Ajustes eran **tres destinos del NavHost apilados uno encima de
otro**, y de ahí venía todo lo raro: para ir de una a otra había que tocar la
barra y volver con "Atrás", el "Atrás" de Lecturas prometía volver a algún sitio,
y —lo importante— **se podía vaciar la pila desde ellas**, que es exactamente lo
que ponía la pantalla en negro.

Deslizar solo tiene sentido entre **hermanas**. Así que el NavHost pasa de cinco
destinos a tres:

| Antes | Ahora |
|---|---|
| `inicio`, `lecturas`, `ajustes` | `principal` — un `HorizontalPager` de 3 páginas |
| `leer` | `leer` |
| `marcadores` | `marcadores` |

Lo que se apila sigue apilado —el visor y los marcapáginas son pantallas que
tapan a las demás— y lo que son secciones hermanas deja de apilarse.

Detalles que no son casualidad:

- **La barra de pestañas va FUERA del pager.** Dentro se desplazaría con las
  páginas, que es justo lo que no tiene que hacer: es el mando, no el contenido.
  Y la pestaña marcada la dice `paginas.currentPage`, no una variable aparte, o
  al deslizar la marca se quedaría atrás — que es medio motivo de deslizar.
- **Dos `BackHandler`, y el orden importa.** Se registran de fuera hacia dentro
  porque **gana el último**: primero "vuelve a la biblioteca" (si no estás en la
  página 0), y encima "sube de carpeta" (si estás en la 0 y has bajado). El de
  la pantalla de estadísticas se registra dentro del pager, así que gana a los
  dos cuando estás dentro de un personaje, que es lo correcto.
- **Ni Lecturas ni Ajustes tienen ya "Atrás".** `onAtras` pasa a admitir `null` y
  la `Cabecera` se salta la fila entera. Una flecha que no lleva a ninguna parte
  es peor que no tener flecha.
- **Las páginas vecinas se destruyen al asentarse** (el `beyondViewportPageCount`
  por defecto es 0), así que el `BackHandler` de las estadísticas no queda
  suelto interceptando el "Atrás" de la biblioteca. Si algún día se sube ese
  valor por rendimiento, **hay que volver aquí**: pasaría a estar vivo en
  segundo plano.

**Y hay una pega real, que conviene saber antes de que la note él.** Los
carruseles de portadas de la biblioteca se desplazan en horizontal igual que el
pager: arrastrando sobre una fila de portadas, el gesto se lo queda la fila y el
cambio de pestaña **solo entra cuando esa fila llega a su tope**. Es el
comportamiento estándar de Compose (el hijo que puede desplazarse consume
primero) y no hay forma limpia de darle la vuelta sin romper los carruseles.
Deslizando por cualquier otra parte de la pantalla funciona a la primera.

### Cuatro comodidades, y dos fallos que salieron de ellas (03/09/2026)

Dani: *"¿qué más cosas de QoL podemos poner?"*. Salieron cuatro, y dos de ellas
destaparon fallos que ya estaban ahí.

**Ir a una página concreta.** El contador de la barra del visor —`"12 / 84"`—
deja de compartir el toque con el título: el nombre sigue abriendo la tira de
miniaturas y **el número pide un número**. Funciona porque en Compose un hijo con
`clickable` se come el evento y el de la columna no llega a dispararse.

La conversión está en `Salto.destino`, que es pura y tiene prueba **porque
parece una línea y tiene cuatro casos**: vacío, con letras, cero o negativo, y
pasado del final. Los cuatro devuelven `null` —no muevas nada— y no un valor
acotado: si escribes 900 en un cómic de 22, colocarte en la última es adivinar
lo que querías decir.

**Marcar leído sin abrirlo — la mitad ya estaba, y estaba mal.** `MenuComic` ya
tenía "marcar como leído" desde antes; lo que faltaba era la carpeta entera. Al
escribirlo salió el fallo:

```kotlin
marcas.marcarTerminado(comic.uri, marcas.de(comic.uri)?.paginas ?: 1)
```

Ese `?: 1` es el cómic que **nunca has abierto**, que es justo el caso normal de
esta función. Y la marca es *"vas por la página N de M"*, con M sumando en las
estadísticas: **diez tomos marcados a mano añadían diez páginas leídas**. Ahora
se abre el fichero para contarlas de verdad (`cuantasPaginas`), fuera del hilo
principal, y el `?: 1` se queda solo para el que no se puede leer — marcar como
leído algo ilegible sigue siendo legítimo: lo has leído en otro sitio y solo
quieres que deje de salir en "En curso".

`marcarCarpeta` va con aviso de progreso, porque treinta cómics son treinta
lecturas de disco, y con diálogo de confirmación que dice **la consecuencia y no
la pregunta**: se pierde por dónde ibas, que es lo único que no se deshace
volviendo a pulsar. Marca **solo el nivel en el que estás**, no las subcarpetas:
en "DC Comics" la fila se llama "Sueltos aquí" y eso es lo que marca.

**Ordenar la carpeta.** Número, nombre o recientes, en `OrdenCarpeta` —puro, con
`OrdenTest`— y el escáner pasa a usarlo también para su orden por defecto, así
que hay **una sola definición de "por número"**. El orden es **global y no por
carpeta**: uno distinto en cada sitio se olvida en cuanto lo eliges y luego no se
entiende por qué una serie sale al revés que la de al lado.

Para "recientes" hizo falta `Comic.cuando`, que sale de
`COLUMN_LAST_MODIFIED` en la **misma consulta** de SAF que ya se hacía — una
columna más no cuesta otra vuelta, que es lo caro. **Y hay que decir qué es:**
SAF no guarda la fecha en que añadiste el fichero, solo la de modificación.
Copiando al móvil casi siempre coinciden, pero si algún día salen en un orden
raro, la explicación es esa.

**Abrir un CBZ desde otra app.** Dos `intent-filter`: uno con los tipos MIME
propios del cómic, y otro con `*/*` y `pathPattern`, que es el que de verdad
funciona casi siempre porque el gestor de ficheros de turno suele decir
`application/octet-stream`. Los `pathPattern` repetidos con `.*\.` no son un
copia y pega mal hecho: **el comodín de Android es glotón** y `.*\.cbz` no casa
con una ruta que ya lleve un punto antes. **No se declara `application/zip`**:
haría que el lector saliera en el "abrir con" de cualquier zip del móvil.

Y aquí está el segundo fallo evitado, que es el interesante: con el modo de
lanzamiento normal, **cada "abrir con" crea otra instancia de la Activity**, y
eso son dos `VistaModelo` y dos `Progreso`, cada uno con su copia en memoria
pisándose al guardar. Es exactamente la trampa de los almacenes duplicados que
este documento ya tenía escrita. De ahí `launchMode="singleTask"` y
`onNewIntent`: **una sola Activity, siempre**.

Dos detalles más del camino:

- **El cómic de fuera va sin carpeta, y no con la raíz.** Con la raíz, el visor
  buscaría el "siguiente cómic" entre los sueltos de tu biblioteca y ofrecería
  uno que no tiene nada que ver. Con la uri fuera del índice, `siguienteComic`
  devuelve `null` él solo.
- **Hay que esperar a que el NavHost esté en pie.** `nav.ir()` se niega a
  navegar hasta que el destino está `RESUMED` —es el cerrojo de la pantalla en
  negro— y en la primera composición todavía no lo está, así que abrir un cómic
  con la app cerrada se habría perdido en silencio. Se espera con tope en vez de
  saltarse el cerrojo.
- **Lo que se lee así no sale en el calendario ni en las estadísticas**, porque
  las dos cosas cruzan por uri contra tu biblioteca y ese fichero no está en
  ella. Es coherente —no es tuyo, está de paso— pero conviene saberlo.

### Cinco comodidades más, y lo que enseñaron (03/09/2026, tercera tanda)

**"Sigue por el #7".** `Siguiente.de` es puro y tiene prueba porque es una regla
con **orden de prioridad**, y esa clase de cosa se rompe sin dar ningún error:
simplemente te abre el que no era. El orden: lo que tienes a medias gana (eso es
lo que significa "seguir", aunque el #8 esté entero sin empezar), y si no hay
nada a medias, el primero sin terminar. La página 0 **no** cuenta como empezado
— misma condición que usa "En curso", porque abrir un cómic y salir sin pasar de
la portada no es tenerlo empezado.

El botón **dice cuál es** en vez de poner "continuar": la mitad de las veces
solo querías saber por dónde ibas y ya no hace falta pulsarlo. Se le pasan los
cómics **en orden de número**, no en el que el usuario haya elegido para verlos:
"sigue por el #7" habla de la serie, no de cómo está ordenada la pantalla.

**Deshacer el marcado en bloque.** Un solo paso, a propósito: un historial de
verdad obliga a decidir cuándo se tira, qué pasa al cerrar la app y qué pasa si
mientras tanto has leído. Lo que hacía falta es la red del "le he dado sin querer
a los treinta".

Dos detalles que no son adorno:

- **Se guarda el valor exacto, y también los que NO tenían marca, como `null`.**
  Deshacer tiene que volver a **quitar** las marcas que esto creó, no dejarlas a
  cero, que es otra cosa distinta. De ahí `Progreso.restaurar`, aparte de
  `marcar`: aquella pone la fecha de *ahora* —correcto cuando estás leyendo— y
  si deshacer refrescara la fecha, los cómics rescatados subirían al principio
  de "En curso" como si acabaras de tocarlos.
- **El temporizador vive en el ViewModel, no en la pantalla.** En la pantalla,
  cambiar de pestaña lo reiniciaría o lo mataría. Y lleva un contador de "vez"
  porque **sin él, el temporizador del aviso viejo borra el aviso nuevo**:
  marcas una carpeta, marcas otra a los dos segundos, y el segundo aviso
  desaparece a los cuatro en vez de a los siete.

**Buscar solo en esta carpeta.** El acotado va por **prefijo de ruta y no por
igualdad**: buscando dentro de "Batman" tienen que salir los de "Batman/Vol 3".
Lo que se acota es el subárbol, no el nivel exacto. Los dos chips solo salen
dentro de una carpeta — en la raíz, "aquí" y "en todo" son lo mismo y serían dos
botones para elegir entre una cosa y esa misma cosa.

**La barra de progreso, arrastrable.** Un solo `pointerInput` con detector
propio y no dos modificadores: la trampa de "el primero se come los eventos del
segundo" ya mordió en esta pantalla con el zoom. El mismo gesto sirve de toque y
de arrastre porque se apunta la posición desde el primer contacto.

**Y solo se viaja al soltar.** Mientras el dedo se mueve, la barra enseña a dónde
iría y un globo dice el número, pero no se pasa de página: con 500 páginas,
seguir el dedo serían medio millar de decodificaciones. El globo va **pegado a
donde está el dedo y no centrado**, o con la mano tapando el borde no se ve.

`Salto.deBarra` **sí acota**, al revés que `Salto.destino`, y no es una
incoherencia: escribir 900 en un cómic de 22 es un error; arrastrar hasta el
borde derecho es una intención. Y hay dos casos de borde reales con prueba: el
ancho a **cero** —el primer fotograma, antes de que la barra se haya medido— y
el último píxel, que trunca a `total` cuando la última es `total - 1`.

**Guardar o compartir una página.** La pulsación larga en el visor no hacía nada,
así que el hueco estaba libre. **Va en el `detectTapGestures` que ya había**, con
`onLongPress`, y no en un `pointerInput` nuevo: dos detectores sobre el mismo
elemento se pisan, y aquí eso ya costó el doble toque una vez.

Tres decisiones dentro:

- **La página se decodifica otra vez y en grande** (al triple, la misma
  resolución que usa el zoom). La que hay pintada está al ancho de la pantalla:
  para leer sobra y para guardar es una imagen borrosa. Se pide **al abrir la
  hoja**, no al pulsar cada opción, así la espera pasa una vez con la hoja ya
  delante en vez de después de tocar, que es cuando parece que el botón no ha
  hecho nada.
- **Compartir funciona siempre; guardar solo desde Android 10.** Guardar en la
  galería necesita MediaStore, que por debajo de Android 10 pediría
  `WRITE_EXTERNAL_STORAGE` — un permiso enorme para esto. Por debajo la opción
  no se ofrece y queda compartir, que hace lo mismo con dos toques más.
- **El `FileProvider` presta SOLO `cache/compartir/`**, no la caché entera: ahí
  dentro también están los CBZ convertidos, que son cómics completos. Un
  provider más ancho de lo necesario es como se presta sin querer lo que no se
  quería prestar. Y esa carpeta **se vacía en cada uso**, o sería otra fuga
  lenta en la caché.

**Y una trampa de Kotlin que costó mover una función:** `private` a nivel de
fichero es literalmente eso — desde **otro fichero del mismo paquete no se ve**.
`OpcionMenu` era privada de `Pantallas.kt` y el visor la necesitaba, así que
sube a `Componentes.kt`, que es donde vive lo compartido.

### "Qué sale próximamente" (03/09/2026, cuarta tanda)

Dani lo eligió de una lista de ideas. **No hace falta ni una petición nueva a
Comic Vine**: los números anunciados ya están guardados en `SeriesRemotas` desde
que se montó el aviso de número nuevo, y lo único que faltaba era mirarlos de
otra manera.

**Y esa "otra manera" es lo único que aporta.** La lista de series seguidas ya
decía el próximo de cada una, pero ordenada **por serie**; la agenda ordena **por
fecha** y mezcla. La pregunta que contesta no es *"¿qué viene de Batman?"* sino
*"¿qué es lo siguiente que me llega?"*, y ordenado por serie eso hay que
reconstruirlo leyendo.

`Novedades.agenda` es pura, con `AgendaTest`, y hereda las reglas que ya tenía
esta parte del proyecto: **sin fecha, fuera** —colocarlo por comodidad sería
inventárselo, igual que en `Huecos` y en `proximo`— y **la fecha de venta real
gana a la estimada**, diciéndolo: las filas cuya fecha ha habido que calcular
llevan un "aproximada" debajo, y solo esas.

Dos detalles que no son cosméticos:

- **Todos los anunciados de cada serie, no solo el primero.** Como la lista va
  por fecha, una serie con tres anunciados no tapa a las demás: se intercalan
  solas. El tope de 15 corta por el final, que es lo más lejano.
- **El desempate por nombre y etiqueta.** Sin él, dos números del mismo día
  pueden salir en un orden distinto cada vez que se repinta la pantalla.
- **La lista va SIN `key`**, al revés que la de series seguidas. Allí la clave es
  la ruta de una carpeta tuya y es única de verdad; aquí saldría de datos de
  Comic Vine, donde un número repetido no es imposible — y dos claves iguales en
  un `LazyColumn` no se ven raras: revientan la pantalla.

**`cuandoSale` dice lo cercano en días y lo lejano en fecha** —"mañana", "en 3
días", "el 30 de septiembre"— porque es como se piensa: a tres días vista lo que
quieres saber es cuánto falta, y a dos meses, qué día es para mirarlo en el
calendario. "en 47 días" no le dice nada a nadie.

### Y el diagnóstico falso que hubo por el camino

Antes de esto hubo **cuatro** intentos fallidos: la posición del scroll, el
índice de la tira, el fondo negro del visor, y —ya con el rastro delante— leer
mal lo que el rastro decía. Merece la pena guardar el cuarto, porque es el más
instructivo:

```
01:38:48.950  pantalla: inicio      <- vuelve de Lecturas
01:38:48.993  carpeta: «raíz»
              (nada durante 5 segundos)
01:38:53.802  ciclo: ON_PAUSE      <- se rinde y sale
```

`abrirCarpeta` empezaba y no terminaba. Y los tiempos daban el patrón entero:

| Momento | Tarda |
|---|---|
| Arranque limpio | 57, 18, 30, 33 ms |
| **Volviendo de Lecturas** | **723, 720, 722 ms… o nunca** |

Se leyó ese `leída:` que falta como *"`abrirCarpeta` se cuelga"*. **Era justo lo
contrario: la pantalla se destruía a mitad de cargar** —porque acababa de salir
de la pila— y su corrutina moría con ella. El rastro decía la verdad; la lectura
fue la equivocada.

**La lección: un evento que falta puede significar "no terminó" o "ya no había
nadie esperándolo", y son diagnósticos opuestos.** Si el rastro hubiera apuntado
también la salida de cada pantalla, se habría visto a la primera.

Aun así, el arreglo que salió de esa lectura equivocada **era necesario por su
cuenta** y se queda: `todosLosComics()` no tenía candado y se llamaba cuatro o
cinco veces a la vez. Eran cuatro líneas —mira el índice, si no está recorre el
árbol, guarda— y con el índice vacío fallaba de dos maneras:

1. **Barridos simultáneos.** Al volver a la biblioteca se piden el "seguir
   leyendo", el "en curso" y la fila del recorrido. Cada uno llamaba por su
   cuenta, y encima `recorrido()` lo llamaba **tres veces él solo**: una directa,
   otra dentro de `seguirLeyendo()` y otra dentro de `siguienteComic()`. Con 293
   cómics eso son cinco barridos completos de SAF a la vez, peleándose por un
   proveedor que los sirve de uno en uno.
2. **El trabajo se tiraba al cambiar de pantalla.** El barrido corría en la
   corrutina de quien lo pedía; al salir de Lecturas antes de que acabara se
   cancelaba y el índice se quedaba a null. **Por eso el fallo aparecía siempre
   volviendo de Lecturas**, y por eso era intermitente: con el índice ya hecho,
   todo iba a 20 ms.

**El arreglo:** el barrido pasa a ser un único `Deferred` en `viewModelScope`,
protegido por un `Mutex`. Quien llegue segundo espera al mismo trabajo en vez de
lanzar otro, y salirse de la pantalla ya no lo mata porque el trabajo no es suyo.
Y `recorrido()` pide la lista una sola vez y saca los tres cómics de ella.

**La lección de esa parte:** una caché perezosa sin candado no es una caché, es
una carrera. Mientras solo la pidiera uno funcionaba de milagro; el día que la
piden tres a la vez, multiplica el trabajo en vez de ahorrarlo. Si un `suspend`
cachea algo caro, o lleva candado o no sirve. Y con el arreglo puesto, el rastro
lo confirma: **293 cómics en 337 ms**, una sola vez.

### El marcapáginas que "desleía" un cómic (02/09/2026)

Dani: *"si tengo una página de un cómic que ya he leído en el marcapáginas,
cuando le doy es como si quisiera comenzar a leerlo y se pone en el apartado de
en curso y se quita el ya leído"*.

**Y tenía razón, y la causa está en el modelo de datos**: `Marca.terminado` no
es un campo guardado, es una **cuenta** sobre la página por la que vas
(`pagina >= paginas - 1`). Así que abrir el marcapáginas de la página 4 de un
cómic de 23 guardaba "vas por la 4 de 23", y con eso el cómic dejaba de estar
terminado, aparecía en "En curso" y encima subía al principio de la fila, porque
al guardar también se le refresca la fecha.

**La regla nueva: consultar no es leer.** `Progreso.cuenta(pagina, techo)`, pura
y con `ProgresoTest`. `techo` es la página por la que ibas cuando entraste por
un marcapáginas; mientras no la pases, lo que mires no cuenta. En cuanto la
pasas, es que estás leyendo de verdad y el progreso vuelve a guardarse normal —
incluido pasar páginas hacia atrás, que leyendo es lo más normal.

**Arregla dos casos con el mismo mecanismo, y el segundo no lo había visto
nadie:**

- Un cómic **terminado**: su página guardada es la última, así que ningún
  marcapáginas la pasa. Sigue leído y sigue fuera de "En curso".
- Un cómic **a medias**: ibas por la 40 y el marcapáginas es la 2. Antes esto te
  movía a la 2 y **perdías por dónde ibas**. Ahora no.

**Lo que no cubre, a propósito**: un marcapáginas por DELANTE de donde ibas (la
60 estando en la 40) sí adelanta el progreso. Se acepta porque un marcapáginas
se pone en lo que ya has visto, y distinguir ese caso pedía guardar más cosas
para algo que casi no pasa.

**Por qué no se hizo de la otra forma**, que era la primera idea: guardar un
campo `leido` pegajoso en la marca, que una vez puesto no se quita. Habría
arreglado el caso del cómic terminado y **no** el del cómic a medias, que es el
mismo problema. El error estaba en dónde se guarda, no en qué se guarda.

### Una inferencia de más, otra vez (26/08/2026)

Dani mandó una captura con la tira de huecos funcionando y el aviso de que le
faltaba el #34 aunque lo tenía. En la captura se veía una carta con "100" arriba
y "#32" debajo, y se dio por hecho que era una chapa de número mal sacada del
nombre — **era el indicador de batería del móvil**. Dani: *"no, eso del 100 es
la batería"*.

Se había metido ya la regla nueva del parser y un comentario que citaba esa
captura como prueba. **La regla se queda, porque es correcta por sí misma** —un
`#` delante de una cifra no es ambiguo y debe mandar sobre el barrido de derecha
a izquierda, que se traga cualquier cifra del título— **pero el comentario se
corrigió para decir que el caso es construido y no un fallo observado.** Dejar
en el código un "porqué" falso es peor que no poner ninguno: el siguiente que lo
lea creerá que está comprobado.

**Y el #34 era del parser.** Con el nombre del fichero delante se vio en un
segundo:

```
"Green Lantern Vol4 #34 - Secret Origin 6 -"   ->  el parser daba 6
```

`numeroDe` barría los tokens **de derecha a izquierda** y se comía el "6" del
título *Secret Origin 6* antes de llegar al `#34`. El cómic salía colocado entre
el #06 y el #07, y la carpeta decía que faltaba el 34. Toda la tanda de "Secret
Origin" estaba mal por lo mismo: son seis partes numeradas dentro del título.

**La regla nueva —si hay un `#` delante de una cifra, ese manda— era exactamente
el arreglo.** Comprobado sobre doce nombres reales: 2 arreglados, 0 rotos.

**Y aquí está la lección de verdad, que es doble y va en las dos direcciones:**

- Primero se **inventó** una evidencia: se leyó "100" en una captura y se dio por
  hecho que era una chapa de número mal sacada. Era el indicador de batería.
- Y luego, al corregir eso, se pasó al otro extremo y se declaró que la regla
  nueva **"no arregla tu caso"**. Tampoco eso se sabía: era justo el arreglo.

Las dos afirmaciones tenían el mismo defecto —hablar de lo que hace un fichero
sin haber visto el fichero— y ser cauto la segunda vez no lo compensó. Lo que
resolvió el caso no fue razonar mejor: fue **una captura con el nombre y el
número que la app había sacado de él**, uno al lado del otro.

Ahora `Parser` tiene `ParserTest`, con el caso del Secret Origin y con los que
ya funcionaban, para que arreglar esto no rompa aquello.

### `Huecos`: lo que se sabe seguro, separado de lo que hay que preguntar

`Huecos.de(numeros)` es una función pura. Si tienes el 11 y el 14, te faltan el
12 y el 13, y eso es verdad hoy y dentro de diez años.

**Lo que a propósito NO hace, y es tan importante como lo que hace**: no dice
que te falten los de antes del primero ni los de después del último. Si tienes
del 5 al 20 no afirma que falten el 1 al 4 — puede que la serie empiece en el 5,
o en el 0, o que continúe la numeración de otra. Eso hay que preguntarlo fuera y
por eso va aparte: **mezclar un dato tuyo con una suposición es como se acaban
diciendo tonterías con mucho aplomo**.

Los especiales quedan fuera del conteo: un annual no lleva la numeración de la
serie y abriría huecos que no existen. Y la tira solo sale cuando la carpeta ES
una serie —cómics dentro, ninguna subcarpeta—: en "DC Comics" hablar de huecos
no significa nada.

### Por fin hay tests, y el primero cazó un fallo (en la prueba)

El proyecto **no tenía carpeta de tests ni JUnit**, y este documento llevaba
tiempo diciendo que había pruebas de `Wiki.interpretarIndice`, `elegirVolumen`,
`Eras.de`, `Racha` y `Busqueda.de`. **No existían.** Otra afirmación del
documento que nadie había contrastado, como la de "miniaturas (60)".

Ya hay `testImplementation("junit:junit:4.13.2")` y `HuecosTest`, con los casos
que salen de verdad en una biblioteca: repetidos —dos ficheros del mismo número
no tapan un hueco—, desordenados, el #0, una carpeta sin números.

**Y el primer intento de escribirlos falló, del lado de la prueba.** El caso
"varios huecos separados" ponía `(11, 14, 49, 59)` y esperaba dos huecos: son
tres, porque entre el 14 y el 49 también falta todo. Se cazó **antes de
entregarlo** reimplementando la lógica aparte y comparando los resultados. La
moraleja del proyecto se repite otra vez, ahora al revés: **un test que no se ha
ejecutado no prueba nada** — puede estar mal él.

**Volvió a pasar el 02/09/2026 con `OrdenLecturaTest`**, y esa vez el test
equivocado escondía un fallo de diseño, no solo una cuenta mal (ver la sección
del orden de lectura). **La comprobación que lo cazó fue la misma las dos
veces**: reimplementar la lógica aparte —en Python, fuera del proyecto— y
comparar los resultados con lo que el test esperaba. Se hace así porque **en el
entorno donde se escribe este código no se pueden ejecutar los tests**: solo hay
Java 11 y el plugin de Android pide 17, así que `./gradlew test` no arranca.
Mientras eso siga siendo verdad, **una expectativa escrita a la vez que el
código no está comprobada por nadie**, y hay que contrastarla aparte antes de
darla por buena.

### Los CBR se convierten solos (26/08/2026)

Dani, sin rodeos: *"lo que quiero es que en cuanto un CBR entre en la carpeta se
convierta en CBZ, se borre el CBR, y ese CBZ sustituya al CBR donde estaba"*.

Casi todo estaba hecho: `ConversorCarpeta` ya escribe el CBZ al lado, cuenta las
páginas de los dos y solo borra el CBR si el CBZ no tiene menos. Lo que faltaba
era **dispararlo sin que nadie pulse nada**.

**Lo único que no sale exactamente como se pidió, y por qué.** "En cuanto entre"
no es posible: la app no puede vigilar la carpeta mientras no la estás usando.
Para eso haría falta un servicio en segundo plano despertándose cada poco — que
es **exactamente lo que se acaba de quitar de en medio porque calentaba el
móvil**. Así que se dispara cuando la app abre la biblioteca: la primera vez que
ve el fichero, lo convierte. Si no estás usando la app, da igual cuándo pase.

Tres decisiones dentro:

- **Primer filtro por extensión, y es un recorte a propósito.** El botón de
  Ajustes mira la FIRMA de todos los ficheros, porque hay `.cbr` que por dentro
  son ZIP. Hacer eso al arrancar sería una lectura de disco por cada cómic de la
  biblioteca, cada vez, y para nada el 99% de las veces. El automático mira solo
  los que se LLAMAN `.cbr`; a los raros los sigue cogiendo el botón.
- **Un interruptor, no una decisión fija.** La conversión tarda y calienta. Está
  puesto de serie, pero si algún día estorba se apaga y queda el botón.
- **Mientras convierte, lo dice.** Una tira arriba con el progreso. Sin eso, la
  app se pone a trabajar sola al abrirla y desde fuera solo se ve que va lenta y
  se calienta — justo la sensación que se acababa de arreglar. **Una app que
  tarda sin explicarse parece rota; una que dice por qué tarda, no.**

Y se avisa al terminar **solo si ha pasado algo**: un mensaje cada vez que abres
la app diciendo que no había nada que hacer es ruido. **El aviso de número nuevo
sigue la misma regla y comparte la cola de avisos.**

### El APK: 55 MB de librerías nativas para usar una

Al preparar R8 salió un número que dejaba a R8 en ridículo. El APK de debug son
**28 MB**, y las librerías nativas suman **55 MB sin comprimir**: el motor RAR5
de 7-Zip se empaqueta una vez **por arquitectura** — 15,8 MB para `arm64-v8a`,
12,5 para `armeabi-v7a`, 12,4 para `x86` y 14,6 para `x86_64`.

El móvil usa **una**. `arm64-v8a` es lo que lleva cualquier móvil de los últimos
diez años; `armeabi-v7a` es de 32 bits y las dos `x86` son emuladores. Con
`ndk { abiFilters += listOf("arm64-v8a") }` se van unos **40 MB** de golpe,
bastante más de lo que quita R8.

Si algún día hace falta el emulador, se añade `"x86_64"` a la lista. Sin él la
app arranca igual: el motor RAR5 no carga, `Rar5.iniciar` lo atrapa y lo cuenta.

**La lección:** antes de optimizar código, mirar de qué está hecho el APK. Una
dependencia nativa cuesta lo suyo multiplicado por el número de arquitecturas, y
eso no se ve leyendo Kotlin.

### Los CBR: el camino sigue vivo aunque ya no queden

Dani, al leer el plan de pruebas: "pero recuerda que ya no tenemos CBR, solo
CBZ". Cierto — se convirtieron todos. Pero **el camino nativo no es código
muerto**: los cómics nuevos que entren en CBR se seguirán convirtiendo, que fue
justo lo que se pidió. Lo que cambia es que **ese camino ya no se ejercita
solo**, y un fallo de R8 ahí aparecería el día que aparezca el primer CBR nuevo
— el peor momento posible para enterarse.

Por eso la prueba en release hay que forzarla a mano: comprimir dos imágenes en
RAR desde el PC y meter el fichero como `.cbr`. **Renombrar un CBZ a `.cbr` no
vale**: el conversor mira la FIRMA del fichero, no la extensión, y lo
descartaría con razón.

### La variante release se puede instalar

`signingConfig = signingConfigs.getByName("debug")`. Es una app personal que no
va a ninguna tienda; lo único que hacía falta era poder darle a Run con release
puesto. **Y hacía falta poder probarla**: Android Studio instala la versión
DEBUG, donde el compilador de Compose no aplica las mismas optimizaciones y hay
comprobaciones de más. **Medir el tirón en debug es medir otra app.**

### El botón atrás salía de la app estando dentro de una carpeta

Dani, el 25/08/2026: "si estoy en la carpeta de Absolute Batman, en vez de ir
para atrás sale de la aplicación, supongo que es porque lo trata todo como una
pantalla". **Diagnóstico suyo y correcto.**

Bajar por las carpetas NO es navegar entre destinos del NavHost: es una pila
propia (`pila`, un `mutableStateListOf` en `MainActivity`), y se hizo así por un
motivo que sigue siendo válido — los identificadores de documento de SAF llevan
barras y romperían una ruta de navegación. Pero el NavHost no sabe nada de esa
pila: estando en el destino `inicio` no tenía nada que desapilar, así que el
gesto de atrás hacía lo de siempre y cerraba la app.

Arreglado con un `BackHandler(enabled = pila.size > 1)` dentro de `inicio`. Dos
detalles que no son casualidad:

- **`enabled` solo con más de un nivel.** En la raíz, atrás vuelve a significar
  salir de la app, que es lo que espera cualquiera. Un `BackHandler` siempre
  activo dejaría la app sin forma de cerrarse con el gesto.
- **Va antes del menú contextual.** Los `BackHandler` se resuelven del último
  registrado al primero, así que la hoja de `MenuComic` —que se compone después—
  gana: con el menú abierto, atrás lo cierra a él y no sube de carpeta.

**La lección general:** cualquier estado de navegación que se lleve por fuera
del NavHost necesita su propio `BackHandler`, o el botón atrás se lo salta. Si
algún día se añade otra pila así, esto se repite. **La pantalla de orden no
necesita uno**: es un destino del NavHost de verdad y `popBackStack` la resuelve.

### Lo que calentaba el móvil: una animación que no paraba nunca

Dani, el 25/08/2026: "consume mucho la aplicación y el móvil se calienta". Es
un síntoma DISTINTO del tirón, y apunta a otro sitio: el tirón es trabajo que
llega tarde, el calor es **trabajo que no para cuando no hace falta**.

El culpable era `Modifier.escaneo()`, el barrido cyberpunk de la tarjeta de
"Seguir leyendo". Estaba montado con `rememberInfiniteTransition`: un valor que
cambia en **cada fotograma, para siempre**, mientras el banner esté en pantalla.
Eso significa repintar una tarjeta de 300 dp a la frecuencia de la pantalla sin
descanso, y **en un móvil con pantalla adaptativa le impide además bajar de 120
Hz para ahorrar**. Con la app abierta y quieta seguía trabajando exactamente
igual que haciendo scroll.

Ahora el haz **va a rachas**: barre en 1,8 s y se queda quieto 6. Mientras está
quieto el valor no cambia, así que no se repinta nada — se anima menos de una
cuarta parte del tiempo. Las rayas fijas se quedan (son un rectángulo con un
degradado repetido, cuestan cero). Y de paso queda mejor, que es lo mismo que ya
se había decidido con el glitch del título: **un efecto que aparece de vez en
cuando se ve; uno que no para se vuelve ruido.**

Y las dos animaciones respetan ahora `ANIMATOR_DURATION_SCALE`: quien apaga las
animaciones del sistema —por mareos, por batería o porque le molestan— no espera
que una app se las salte por su cuenta.

**COMPROBADO EN EL MÓVIL, no supuesto.** Dani dejó la app abierta y quieta diez
minutos, desenchufada: no se calentó y la batería no bajó nada. Antes del cambio
calentaba. Eso convierte la animación infinita de causa probable en **causa
confirmada**, y es la única de todas las de esa jornada que ha pasado por una
medición de verdad. Las del tirón siguen siendo razonamiento sobre el código.

**La lección, que no es la misma que la del tirón:** una animación infinita no
se nota en un perfil de scroll ni en las cuentas de recomposición, porque no es
un pico, es un suelo. Se busca al revés — **¿qué sigue trabajando con la app
quieta?** — y en Compose eso son `rememberInfiniteTransition`, los `while (true)`
dentro de un `LaunchedEffect` y los efectos cuya clave sube sola. Se miden
dejando la app abierta sin tocarla, con el perfil de renderizado GPU en barras
puesto: si con la app quieta se siguen dibujando barras, ahí está.

### "Y creo que la app se queda abierta aunque salga de ella"

Es verdad a medias, y la mitad que es verdad importa.

**Que Android se quede con el proceso en memoria al salir es normal y no gasta
batería por sí solo**: lo guarda por si vuelves, y lo mata cuando necesite la
memoria. Eso no hay que arreglarlo. Lo que sí gasta es lo que siga TRABAJANDO
dentro, y ahí la app tenía dos cosas: **Compose no para los efectos al pasar a
segundo plano**. Un `while (true)` con `delay` dentro de un `LaunchedEffect`
sigue despertándose con la pantalla apagada hasta que el sistema mate el
proceso, y la app tenía dos: el glitch del título y —recién metido— el barrido
de la tarjeta.

Ahora las dos animaciones se enganchan al ciclo de vida (`enPrimerPlano()`, un
`LifecycleEventObserver` a mano): al salir se cancela la corrutina, al volver
arranca. Hizo falta añadir `lifecycle-runtime-compose`, porque el
`LocalLifecycleOwner` de `androidx.compose.ui.platform` quedó obsoleto en
Compose 1.7 y se mudó ahí.

**Lo que había y lo que hay.** La regla era "ni servicios, ni WorkManager, ni
wakelocks". **El 02/09/2026 entró WorkManager**, un trabajo diario con
restricción de red, y la razón está en §"Las notificaciones de número nuevo":
esta regla se escribió por el calor, y lo que calentaba era una animación
infinita repintando a 120 Hz, que es trabajo continuo. Un despertar al día es
otra cosa.

**Lo demás de la regla sigue en pie**: no hay servicios en primer plano, ni
wakelocks, ni nada que se despierte cada pocos minutos. El único
`FLAG_KEEP_SCREEN_ON` está en el visor y se quita en su `onDispose`.

Y las tareas largas de `viewModelScope` (convertir CBR, enriquecer con la wiki,
refrescar los números de una serie en emisión) **sí siguen si sales de la app**,
y eso es a propósito: una conversión a medias es peor que una conversión que
termina sola. Mueren si el sistema mata el proceso, que es el comportamiento
correcto.

De paso: `Miniaturas.precalentar` no la llamaba nadie. Fuera.

**Los avisos del compilador de esa misma tanda**, porque uno de ellos no era
ruido: `portadasDe` seguía pidiendo un `docId` **que ya no usaba nadie** desde
que dejó de consultar SAF. Un parámetro muerto en una firma pública es peor que
un aviso: invita a creer que hace algo. Fuera. Los demás sí eran ruido y se
limpiaron igual: `prefs.edit()...apply()` pasa al `edit { }` de core-ktx (que
además no se puede olvidar el `apply()`), `limpiarBusquedas` era código muerto,
`comicsBajo` pasa a privada, y sobraban un `val app` sin usar y dos
cualificadores de paquete completos.

- **Nada de leer de disco al pintar.** Vale igual para filtrar: el buscador
  filtra una vez por cambio de texto con `remember(lista, busqueda)`.
- **El árbol entero se recorre una vez y se cachea** (`todosLosComics`). Hace
  falta en cuatro sitios —el buscador, el "seguir leyendo", el siguiente cómic de
  la carpeta y el orden de lectura— y recorrerlo cada vez se nota. La caché se
  tira al cambiar de carpeta raíz; si añades ficheros con la app abierta, hay que
  volver a entrar.
- **TRES cachés de bitmaps, y la razón importa.** Con una sola caché de ocho,
  las portadas del catálogo echaban fuera las páginas que estabas leyendo y al
  volver parpadeaban. Ahora van separadas: miniaturas, páginas (8) y detalle de
  zoom (2). La de detalle es pequeña a propósito: una página a resolución de
  zoom pesa veinte megas.
- **`LruCache` cuenta lo que le digas en `sizeOf`, y por defecto cuenta
  ENTRADAS.** Las de páginas y zoom cuentan entradas a propósito, porque lo que
  hay que limitar ahí es cuántas páginas gordas hay vivas. La de miniaturas
  **cuenta kilobytes** desde el 25/08/2026, con un techo de un octavo del montón
  de memoria de la app (la receta de siempre en Android) y un máximo de 48 MB.
  Este documento decía "miniaturas (60)" y en el código ponía 30: **la cifra del
  documento no se había comprobado contra el código.** Ahora no hay cifra que
  desmentir porque el límite se calcula solo.
- **Al ampliar, la página se ve pixelada** porque está decodificada para el
  tamaño de la pantalla. Se recarga al triple **solo mientras hay zoom y solo
  esa página**, con un cuarto de segundo de espera para no recargar en mitad del
  pellizco.
- **Las páginas se sacan de una en una**, nunca el cómic entero: descomprimir
  un tomo completo en memoria revienta el límite de 256 MB del proceso.
- **`OutOfMemoryError` NO es una `Exception`, es un `Error`.** `ComicZip` tenía
  `catch (e: Exception)` en todos sus métodos y aun así la app **se cerraba** al
  llegar a un cómic concreto del catálogo (Blackest Night, 25/08/2026): el OOM
  se colaba entero por encima de esos catch y se llevaba el proceso. Donde se
  abren ficheros de terceros hay que atrapar **`Throwable`**, que es lo único
  que cubre las dos ramas. Está puesto en `Miniaturas.obtener`,
  `ComicZip.extraerRarA`, `ComicZip.paginas`, `ComicZip.pagina` y `Rar5`.
  **Y ojo con arreglarlo solo a medias**: la primera vez se cambiaron unos
  cuantos y se dejaron los `catch` de `paginas` y `pagina`. Resultado: la
  portada del cómic ya salía bien, pero **la app seguía cerrándose al abrirlo**,
  y parecía un problema distinto. Cuando aparezca un `catch (Exception)` que
  envuelve E/S de ficheros ajenos, hay que revisarlos **todos** de una vez.
- **Y la causa de fondo: junrar se traga el ARCHIVO ENTERO.** Con un
  `InputStream` que no se puede rebobinar —y el de SAF no se puede— junrar
  necesita bufferizarlo todo para saltar por él. Para sacar una portada de
  200 KB se comía los 300 MB del tomo. A eso se le sumaba un `ByteArray` con la
  página completa **además** del bitmap.
  El arreglo (25/08/2026): en ZIP se decodifica **en chorro** desde la entrada,
  abriendo dos veces y sin array intermedio; en RAR se extrae la página **una
  vez a un temporal** de la caché y se decodifica de ahí. Se cambia una lectura
  más de fichero por no tener nada grande en memoria, que es el cambio que
  hacía falta.
- **Si `inJustDecodeBounds` no consigue las medidas, PARAR.** Antes, con
  `outWidth = 0`, la división daba 0, `inSampleSize` se quedaba en 1 y se
  intentaba decodificar la imagen a tamaño completo — otra vía al mismo OOM.
- **`DocumentFile` es lento e incompleto** con carpetas grandes. Usar
  `ContentResolver` con un cursor.

### Ficheros y almacenamiento
- **Las URIs de SAF NO sobreviven.** Llevan dentro la ruta completa y el permiso
  concreto: al reinstalar, al mover la carpeta o al volver a dar permiso,
  cambian todas. Por eso la copia de seguridad guarda **carpeta + nombre de
  fichero** y recoloca contra la biblioteca del momento de restaurar. Se usan
  los dos últimos tramos y no solo el nombre: "Batman 01.cbz" se repite en
  cuatro carpetas, "Batman Vol 3/Batman 01.cbz" no.
- **Al restaurar no se pisa nada.** Las listas que ya existen se respetan; el
  marcado se suma (marcar de más es recuperable, desmarcar no); en el progreso
  gana la marca más reciente.
- **`local.properties` se lee en ISO-8859-1 por defecto.** Hay que leerlo con un
  Reader en UTF-8 o una contraseña con `ó` llega destrozada.
- **Los campos nuevos de `Serie` van con valor por defecto y se leen con
  `optBoolean`/`optInt`.** El JSON guardado antes no los lleva, y tiene que
  seguir cargando sin tocar nada. Así entró `noEncontrada`.

### Nombres de fichero
El parser aprendió los formatos reales uno a uno: `Daredevil 36`,
`Daredevil #025`, `Moon Knight#14`, `Moon Knight nº14`,
`Daredevil Vol.7 #01 [9R] @Grupo`, `Daredevil 001 (2019)`.

Claves: separar la cifra pegada a la palabra antes de partir por espacios, y
descartar años sueltos para no confundir `(2022)` con un número.

**Buscar exige TODAS las palabras, en cualquier orden.** "batman 01" y "01
batman" tienen que dar lo mismo, y un `contains` sobre una sola cadena no lo
hace. Se busca en el nombre **y** en la carpeta, así que "vol 3 annual"
encuentra el annual dentro de la carpeta del volumen 3 aunque el fichero no
diga "vol 3" por ninguna parte.

### Comic Vine
- **Lento** (~10 s por petición) y exige **User-Agent propio** (403 con el de
  Java). Hace coincidencia **parcial** de nombre y devuelve **200 aunque haya
  error**: hay que mirar `status_code` dentro del JSON.
- **La escala es brutal**: `filter=name:Batman` devuelve **2217 volúmenes**. El
  tope de lectura de 200 estaba tirando el 90% **en silencio**, y por eso
  faltaban las series nuevas (Absolute Batman, 2024). Ahora el tope se cuenta y
  se avisa: "198 de las 2217 que tiene".
- **Corta por exceso de peticiones con un 420.** El límite documentado ronda las
  200 peticiones por recurso y hora, y además hay detección de ritmo.
- **`count_of_issues` viene en las llamadas de LISTA**, no hace falta pedir
  `/volume/<id>/` por serie. Ya está en los tres `field_list` del cliente desde
  siempre. Se apunta porque en una sesión sin el código delante se propuso
  "añadirlo al `field_list`" como si fuera el arreglo: era un problema que no
  existía. **Mirar el código antes de diseñar el parche.**

**Una serie no cuesta una petición: cuesta hasta DOS.** `volumen()` pide primero
`name:X,start_year:Y` y, si de ahí no sale nada, repite con `name:X` a secas.
Las 268 series de Batman nunca fueron 268 peticiones: eran hasta 536. Ese es el
motivo real de que el 420 llegara "a mitad".

**Y al cortar, la app aceleraba.** Un 420 hace que `get` devuelva `null`,
`buscar` una lista vacía y `elegirVolumen` un `null` — indistinguible de "no
está". Eso disparaba la segunda petición, que se comía otro 420. A partir del
corte cada serie pasaba a costar el doble, justo cuando ya estabas contra el
límite. **Arreglado (25/08/2026)**: la segunda búsqueda solo sale si la primera
llegó a responder (`fallo == null`).

**"No la tiene" y "me han cortado" son cosas distintas y hay que guardarlas
distintas.** `repararNumeros` recorría todas las series a cero sin separarlas,
así que las que Comic Vine simplemente no tiene se reintentaban enteras en cada
pasada, a dos peticiones cada una, **antes** de llegar a las recuperables: el
botón se gastaba la cuota de la hora en lo que no tenía arreglo. **Arreglado
(25/08/2026)**: `Serie.noEncontrada` marca las que el servidor respondió no
tener, se persiste, y el botón las salta. Con salida: si quedan descartadas
aparece un segundo enlace para reintentarlas a la fuerza, porque una serie que
no existía hace un mes puede existir hoy y si no, se quedaría a cero para
siempre sin botón que la rescate.

**Y la misma distinción, otra vez, en el aviso de número nuevo (02/09/2026).**
Allí una lista vacía o más corta que la guardada se trata como corte y no como
dato (`Novedades.fiable`): si no, un 420 dejaría la serie a cero. Es la tercera
vez que este proyecto tropieza con "un cero puede significar dos cosas".

- **La elección entre candidatos está en `elegirVolumen`** y probada contra los
  17 resultados reales de "Absolute Batman": nombre exactamente igual
  normalizado, solo la editorial mayoritaria (fuera la edición francesa de Urban
  Comics), año exacto o un año de margen, y a igualdad la que más números tenga.
  De 17 saca la correcta.
- El filtro combinado `name:X,start_year:Y` se intenta primero, pero la elección
  se hace igualmente en el cliente: así el resultado no depende de si el
  servidor respeta ese filtro. Solo depende la velocidad.

**VERIFICADO contra el servidor (25/08/2026)**, cruzando `filter=name:Absolute`
(173 resultados) con `filter=name:Absolute Batman` (5).

- **`filter=name:` es una subcadena de verdad**, no una palabra completa:
  buscando "Absolute" entran "Marvel **Absolutely** Everything You Need To Know"
  y "The Amazing... **Absolutely** True Adventures".
- **Ordenación por `id` ascendente, confirmada** en dos respuestas
  independientes (9336 → 122065, y 2839 → 40761). El `id` se asigna al dar de
  alta el volumen, así que **lo más nuevo cae siempre en las últimas páginas**.
  Ningún tope recorta al azar: se come justo las series recientes.
- **`start_year` en el filtro combinado se IGNORA.**
  `filter=name:Green Lantern,start_year:2021` devuelve **303 resultados y ni uno
  de 2021**: salen 1960, 1981, 1983, 1986, 1989... Esto tira el comentario que
  había en `volumen()` sobre "si Comic Vine respeta `start_year` son cuatro
  resultados en vez de cien". No los respeta. Nunca.
- **`sort` también se ignora.** `&sort=start_year:desc` sobre la misma búsqueda
  devuelve exactamente el mismo orden (2839, 3077, 3198... 1960, 1981, 1983).
  En `/volumes/` **lo único que se puede mover es el `offset`**: mismos
  resultados, mismo orden, siempre.

**Y de ahí sale el fallo que explica las series que no se encuentran.**
`buscar()` pedía `limit=100` **sin `offset` y sin paginar**: veía los 100
primeros, que por la ordenación son **los 100 más antiguos**. Con
`name:Green Lantern` son 303 resultados y la app solo miraba hasta el `id` 40761
(año 2011). Por eso `Green Lantern` (2005, `id` 18216) resolvía y `Green Lantern`
(2021) y (2023) no existían para la app. Es el mismo fallo que el `TOPE = 200` de
`volumenesDe`, pero peor: allí al menos se cuenta lo que se deja fuera y se
avisa; aquí no se paginaba, no se contaba y no se decía nada, y el cero
resultante era indistinguible de "no existe".

Cuanto más corto y común es el término, peor: `Ion` (2006) se busca con
`filter=name:Ion`, que al ser subcadena casa con Act**ion** Comics, Leg**ion**,
Champ**ion**, Rebell**ion**... y la serie buscada queda a miles de posiciones.

**La salida es `/search/`, que es OTRO buscador.** `api/search/?resources=volume
&query=X` no se parece en nada a `/volumes/?filter=name:X`:

  - **Busca por palabras, no por subcadena.** `query=Ion` devuelve **8
    resultados**; `filter=name:Ion` devuelve miles, porque le casan Act**ion**
    Comics, Leg**ion**, Champ**ion**... En los 8 no hay ni un "Action Comics".
  - **Ordena por relevancia**, no por `id`. El primer resultado de `query=Ion`
    es `Ion` (2006, DC, 12 números, `id` 18143), que es justo el que la app no
    encontraba.
  - Trae `name`, `start_year`, `count_of_issues` y `publisher` —todo lo que
    necesita `VolumenRemoto`— más `aliases` y `date_added`.

  - **Comprobado con los dos casos difíciles.** `query=Green Lantern` (660
    resultados) trae entre los **20 primeros** los de **2021** (`id` 135228, 12
    números) y **2023** (`id` 150172, 37 números), que eran justo los que la app
    daba por inexistentes, junto con los de 1941, 1960, 1990, 2005 y 2011.

  Encaja con la arquitectura sin tocar lo probado: **`elegirVolumen` se queda
  igual**. El ranking solo tiene que colocar al candidato bueno en la primera
  página; quién gana lo sigue decidiendo la función por nombre exacto
  normalizado, editorial mayoritaria y año. Si algún día el ranking cambia, la
  elección no se ve afectada. Y de hecho hace falta: entre esos 20 hay ediciones
  de ECC, Panini, Planeta DeAgostini, Editorial Televisa y TM-Semic. Nueve de
  los candidatos con nombre exacto son de DC, así que la regla de la editorial
  mayoritaria tira las traducciones y por año quedan los dos correctos.

**El espacio va como `%20`, no como `+`.** `URLEncoder.encode` es codificación
de formulario y convierte el espacio en `+`. En `/volumes/?filter=` se sabe que
Comic Vine lo acepta, porque la app lleva así desde siempre; en `/search/?query=`
**no se sabe**, porque todas las pruebas se hicieron desde el navegador, que
manda `%20`. Si allí el `+` no valiera, se estaría buscando la cadena literal
"Green+Lantern" y no encontraría nada nunca. `%20` funciona en los dos sitios y
es lo único comprobado, así que hay un `encQuery` que lo fuerza. No se depende
de averiguarlo.

**ANOMALÍA ABIERTA, sin explicar.** `filter=name:Absolute Batman` devuelve 5 y
declara `number_of_total_results: 5`, cuando en `name:Absolute` hay diez
volúmenes con "Absolute Batman" literal. Se creyó ver un patrón —que se caían
los que llevan `:` o `,` pegados al término— y **se declaró verificado con diez
ejemplos de una sola búsqueda. Era falso**: en `name:Green Lantern` aparecen sin
problema `Green Lantern: Emerald Dawn`, `: Mosaic`, `: Rebirth`, `: Dragon Lord`
y `: The New Corps`, que son el mismo caso estructural. Queda como rareza sin
diagnóstico. Se deja escrito el error a propósito: fue exactamente la trampa de
"un caso comprobado no comprueba los demás", dos mensajes después de escribir
esa lección en este documento.

**Un filtro que no llega NO da error: devuelve el universo.** Una petición a la
que se le perdió el `filter` respondió `status_code 1`, `error: OK` y
`number_of_total_results: 159876` — el catálogo entero. `volumenesDe` se habría
tragado los 200 primeros (Jumbo Comics, Whiz Comics, cómics de 1938) como si
fueran las series del personaje, y habría avisado con el mensaje tranquilizador
de siempre: "se leen las 200 primeras, así que pueden faltar las más nuevas".
No es que falte información: es basura presentada como buena. **Pendiente**: una
guarda que dé la voz cuando `number_of_total_results` se dispara para lo que era
el nombre de un personaje.

**No hay salida de red a Comic Vine ni desde el puente con el ordenador ni desde
el entorno de la sesión** (las dos dan `000`): toda comprobación pasa por pegar
las respuestas a mano.

**Descartada: el `+` de `URLEncoder` NO es un problema.** Se sospechó que sí,
porque `URLEncoder.encode` convierte el espacio en `+` y no en `%20`, así que la
app manda `name:Green+Lantern` mientras que las pruebas a mano desde el
navegador mandan `%20`. Si Comic Vine no interpretara el `+`, la app llevaría
desde siempre buscando cadenas literales con un `+` dentro. **No es el caso**:
en Green Lantern la mayoría de las 60 series resuelven su conteo, "Green
Lantern" incluido, que ya son dos palabras. Se apunta la sospecha y su refutación
porque era buena y volverá a ocurrirse.

### Wikis de Marvel y DC (Fandom) — todo verificado contra el servidor
API de MediaWiki en `/api.php`, sin clave.

- **Buscar por prefijo no sirve.** `apprefix=Daredevil Vol ` devuelve también la
  página de cada grapa y los 50 primeros resultados se los come el volumen 1
  entero. Como los títulos son deterministas, **se piden directamente**.
- **El año no está en la ficha.** La plantilla de volumen no tiene ningún campo
  de fecha; buscar un año en el wikitexto devuelve el del premio Eisner.
- **El año está en las categorías**, que por eso no se ven con `action=raw`.
- **Marvel y DC NO usan las mismas categorías.** Marvel: `2019 Volume Debuts` /
  `2022 Volume Ends`. DC: `2016 Comic Debuts` / `2026 Last Issues`. Lo único que
  comparten es `Category:Volumes`, que es lo que confirma que la página es de un
  volumen. Se prueban los dos juegos en las dos wikis.
- **Los patrones van anclados a los dos extremos**, o se cuelan
  `2022 Eisner Awards` y `August Comic Debuts`.
- **El artículo "The" no va en el título.** Comic Vine dice "The Amazing
  Spider-Man"; la wiki titula "Amazing Spider-Man Vol 5" y la otra forma
  devuelve `missing`.
- **MediaWiki solo capitaliza la PRIMERA letra.** Escribir "Green lantern" pedía
  `Category:Green lantern Titles`, que no existe, y la app concluía que no
  conocía al personaje. Se prueban varias formas del nombre.
- **La wiki tiene el índice de series por personaje**, y esto es lo que hace
  viable todo: `Category:Batman Titles` en DC, `Category:Daredevil Comic Books`
  en Marvel. Es una lista **curada**: sin tomos recopilatorios, sin ediciones
  extranjeras. Y mete lo que toca aunque no lleve el nombre — Detective Comics
  Vol 1 está en "Batman Titles".
- **Las etapas también son categorías**, pero con nombres irregulares:
  `The New 52`, `DC Rebirth Titles`, `Absolute Universe Titles`. **No se
  distingue una etapa de un índice de personaje por el nombre** ("DC Rebirth
  Titles" y "Catwoman Titles" tienen la misma pinta), así que solo se etiquetan
  como etapa las verificadas y el resto sale sin etiquetar, pero sale.
- `cllimit` cuenta las categorías de todas las páginas juntas: hay que seguir
  por `clcontinue`. Y `titles` admite 50 por petición: hay que trocear.
- **Que el índice venga de la wiki es lo que impide agrupar peticiones en Comic
  Vine.** Las 268 series de la etapa de Batman incluyen Detective Comics,
  Nightwing y demás, que no llevan "Batman" en el nombre: un solo
  `filter=name:Batman` cubre una parte y deja fuera el resto.

### Gemini
- Jubila modelos cada pocos meses: usar el alias `gemini-flash-latest`. Da 503
  cuando se satura: reintentar con espera creciente y probar otro modelo.
- **Trocear**: 198 series de golpe cortan la respuesta por longitud y no llega
  nada. Tandas de 25.
- **Valorar una serie suelta necesita su propio prompt.** El normal reparte un
  único "EMPIEZA AQUÍ" entre todas las series del personaje; si solo ve una, la
  corona por descarte, y cualquier cosa añadida a mano se convertía en la puerta
  de entrada.
- **Las claves de API de Anthropic no van con la suscripción de Claude.**
- **El orden de lectura NO pasa por aquí.** Es el único apartado grande de la
  app donde el modelo no interviene: las fechas de portada bastan.

### CBR

> **LA PREMISA DE ESTA SECCIÓN CAYÓ EL 25/08/2026.** Decía que "los CBR del
> usuario abren bien" y que RAR5 era "un caso que no se da". **Se da.** Dani
> tenía RAR5 repartidos por su biblioteca —al menos en *Blackest night*, *Final
> crisis rage of the red lanterns* y otros sueltos—, y la app se los rechazaba
> uno a uno con el mensaje de RAR5.
>
> Y lo más importante, porque es contraintuitivo: **convertir CBR a CBZ dentro
> de la app NO arregla esto por sí solo.** Para convertir hay que descomprimir
> el RAR5, que es exactamente lo que la app no sabía hacer. Ninguna astucia del
> lado de la app lee un RAR5 sin un descompresor de RAR5. O se convierten fuera,
> o entra el motor nativo. No hay tercera vía.
>
> **DECIDIDO (Dani, 25/08/2026): entra el motor nativo**, y encima de él, la
> conversión a CBZ. La conversión no es un adorno: convertido una vez, ese cómic
> **no vuelve a pasar por junrar nunca**, así que arregla de un tiro el RAR5 que
> no se sabe leer y el atracón de memoria del RAR4 grande.
>
> **FUNCIONA (25/08/2026).** El motor nativo arranca y los CBR de RAR5 se leen.
> `datos/Rar5.kt` los convierte a CBZ **en la caché de la app** y a partir de ahí
> los lee el camino de ZIP de siempre.
>
> Dos cosas que costaron y conviene no repetir:
>
> - **`initSevenZipFromPlatformJAR` no tiene sobrecarga con `Context`** en esta
>   versión, solo `File` y `String` — un directorio donde extraer el `.so`. Lo
>   dijo el compilador: "None of the following candidates is applicable". Se le
>   pasa `ctx.cacheDir`.
> - **RAR4 también pasa por la conversión.** El motor de 7-Zip lee las dos
>   versiones de RAR (`ArchiveFormat.RAR` para RAR4), y eso mata de raíz el
>   atracón de memoria de junrar. junrar se queda solo de respaldo por si el
>   motor nativo no arranca.

- **junrar solo lee RAR4.**
- **RAR4 y RAR5 se distinguen por el SÉPTIMO byte** de la firma
  (`52 61 72 21 1A 07` y luego `00` para RAR4, `01` para RAR5). Antes la app
  solo miraba si ponía "Rar!", así que un CBR roto y uno de RAR5 daban el mismo
  error vago y no había forma de saber cuál era. Ahora lo dice
  `Formatos.de()`, que está probado con las firmas reales, y cada caso tiene su
  mensaje.
- **El motivo también se ve en la carta del catálogo** (25/08/2026). El mensaje
  bueno existía pero solo salía al ABRIR el cómic; en la rejilla la carta se
  quedaba negra y muda, así que con cuarenta números no había forma de saber
  cuáles eran RAR5 sin abrirlos uno a uno. `ComicZip.motivoCorto` +
  `Miniaturas.motivo` + el hueco `vacio` de `Portada`. El formato solo se mira
  **cuando la portada ya ha fallado**: hacerlo siempre sería una lectura de
  disco por cada carta del catálogo para nada.
- **DESDE EL 25/08/2026 LA CARPETA SE PIDE EN LECTURA Y ESCRITURA.** El
  conversor deja el `.cbz` al lado del original y borra el original tras
  comprobarlo. **Un permiso ya concedido no se amplía solo**: quien eligiera la
  carpeta antes del cambio la tiene en solo lectura y **tiene que volver a
  elegirla** desde Ajustes, o la conversión fallará con "no se ha podido crear
  el fichero".
- **`_cbr_originales` está en la lista de carpetas que el escáner IGNORA**
  (`Escaner.IGNORADAS`). Sin eso pasan dos cosas feas: sale en el catálogo como
  una carpeta más y ves cada cómic **dos veces**, y la siguiente conversión
  encuentra esos originales y los vuelve a convertir. Es la papelera de la app,
  no biblioteca.
- **Primer resultado real (25/08/2026): 84 de 97 convertidos.** Los 13 que no,
  todos por el mismo motivo: ya había un `.cbz` con ese nombre al lado. Ahí no se
  toca nada, que es lo correcto: decidirlo es del usuario.
- **RESULTADO FINAL DE LA NOCHE (25/08/2026): los 97 CBR resueltos.** Todos
  convertidos a CBZ salvo uno, `Green Lantern - Emerald Warriors #13`, y ese
  **no es un fallo**: dentro del RAR hay un **PDF**, no páginas sueltas. No hay
  nada que convertir. La app lo dice con esas palabras y no toca el fichero.
  **Si algún día se quiere leer**: Android trae `android.graphics.PdfRenderer`
  desde API 21, y necesita un fichero con acceso aleatorio — el PDF se extraería
  del RAR a la caché, igual que ya se hace para junrar y para 7-Zip. Sería su
  propia tanda, no un parche.
- **SAF le pone SU extensión al fichero que creas.** Pidiendo `X.cbz` con mime
  `application/zip`, el proveedor crea **`X.cbz.zip`**. Parece cosmético y no lo
  es: rompe el número de la grapa. `numeroDe` quita la última extensión, se queda
  con `...#01.cbz`, y descarta ese trozo por su regla de "más de dos letras no es
  un número". Las 84 portadas convertidas se quedaron sin su chapa, y lo vio Dani
  mirando una captura. **Arreglo**: crear, consultar el `DISPLAY_NAME` real, y
  renombrar si no es el que se pidió.
- **Botón de limpiar biblioteca** (`ConversorCarpeta.limpiar`): arregla las
  extensiones dobles y borra duplicados —**solo si tienen exactamente las mismas
  páginas que el original**. Si no coinciden no toca nada y da los dos números.
  Misma regla que impidió perder Blackest Night.
- **RENOMBRAR SIN MIRAR SI EL DESTINO EXISTE CREA DUPLICADOS.** El arreglo de
  las extensiones dobles renombraba `X.cbz.zip` → `X.cbz` a pelo. Cuando `X.cbz`
  ya existía, Android resolvió la colisión poniendo `X (1).cbz`. Resultado: **el
  arreglo fabricó los duplicados que luego había que borrar**, y encima con un
  `(1)` indistinguible a simple vista de un número de grapa.
  **Dos reglas de aquí en adelante**: antes de renombrar, comprobar si el
  destino existe (y si existe, comparar y borrar el sobrante en vez de
  renombrar); y al buscar "¿ya está convertido?", buscar también las variantes
  que dejaron versiones anteriores.
- **`Corps (14)` → `Corps #14`, y no es cosmético.** `Parser` quita a propósito
  lo que va entre paréntesis (`RE_PARENT`) para que un `(2016)` no cuele como
  número de grapa. Consecuencia: los cómics numerados así se quedan **sin
  número**, sin chapa en la portada, y ordenados como texto. El limpiador los
  renombra al formato de grapa, **rellenando con ceros según el mayor de esa
  carpeta**. **Los años se saltan**, con la misma regla 1930-2100 que usa
  `Parser`.
- **UN `(n)` NO ES UNA MARCA DE COPIA POR SÍ SOLO.** La primera versión
  renombraba `X (1).cbz` → `X.cbz` cuando no había original al lado. Eso habría
  destrozado `Green Lantern Corps (21).cbz` —el 21 es el **número**— y
  `Batman (2016).cbz` —el 2016 es el **año**—. Lo cazó Dani en una captura antes
  de que llegara a ejecutarse.
  **Regla buena: un duplicado siempre tiene su original al lado. Si no está, no
  es un duplicado y no se toca.**
  **Si renombra algo, la pasada termina ahí** y pide que se pulse otra vez: los
  nombres en memoria ya no son los del disco, y buscar duplicados sobre datos
  viejos es como se borra el fichero equivocado.
- **Los mensajes de fallo llevan NÚMEROS Y NOMBRES, no adjetivos.** La cadena
  de esa noche lo dice todo: "no se ha podido leer el CBR" → inútil; "el CBR no
  tiene imágenes dentro" → mejor pero ambiguo; "el motor ve 1 ficheros dentro y
  ninguno parece una página" → ya se sabe que el archivo se abre bien y que el
  problema es qué lleva dentro. Cada vuelta costó una compilación del usuario.
  **Poner el dato desde el principio sale más barato que tres iteraciones de
  adivinanza.**
- **CADA MOTOR PARA LO SUYO: RAR4 con junrar, RAR5 con 7-Zip.** No es simetría
  por gusto. Con Blackest Night (RAR4, 531 páginas) **7-Zip decía tener 19
  ficheros y junrar los 531 correctos**, comprobado abriendo el mismo archivo
  por los dos caminos. Usar el motor nativo "porque lee las dos versiones" costó
  un CBZ de 19 páginas dado por bueno. Que un motor sea más moderno no lo hace
  mejor en todo.
- **LEER UNA PÁGINA Y RECORRER EL ARCHIVO ENTERO NO CUESTAN LO MISMO**, y
  confundirlo llevó a dos conclusiones opuestas y las dos equivocadas.
  Primero se dijo que junrar se tragaba el archivo con el stream de SAF y que
  por eso se cerraba la app. Falso: lo que reventaba era el `ByteArray` con la
  página completa. Luego se dijo lo contrario —"junrar va bien con este
  fichero"— y también falso: al **convertirlo**, recorriendo las 531 entradas de
  una sentada, dio `OutOfMemoryError`.
  Las dos cosas son ciertas a la vez: leer una página busca su cabecera y extrae
  esa; recorrerlo entero obliga a junrar a bufferizar. **Arreglo**: se copia el
  CBR a la caché y se le pasa a junrar un `File`, no el `InputStream` de SAF.
- **NO COMPARES UNA LISTA CONSIGO MISMA.** La conversión se declaraba "todo o
  nada" comparando las páginas escritas con las páginas que **el propio motor
  decía tener**. Con Blackest Night, 7-Zip dijo tener 19 ficheros donde junrar
  veía 531: escribió 19, la comprobación cuadró, y quedó un CBZ de 19 páginas con
  pinta de conversión correcta — **y el original ya movido**. Una comprobación
  que usa la misma fuente que el trabajo que valida no comprueba nada.
  Ahora se cuentan tres cosas: ficheros que dice tener el archivo, los que
  parecen página, y los escritos.
- **El formato no se le impone a 7-Zip.** Se abre con `null` y que lo detecte
  él. Forzarlo a partir de la firma del fichero fue lo que dio las 19 de 531.
- **CAMBIO DE CRITERIO (Dani, 25/08/2026): el conversor SÍ borra el CBR.**
  **Pero el borrado va detrás de una comprobación que no se quita**: se cuentan
  las páginas del CBR y del CBZ, y el CBR solo se borra si el CBZ **no tiene
  menos**. La regla que pidió tal cual —"si ya hay un CBZ, borra el CBR"— habría
  destruido Blackest Night esa misma noche: había un `.cbz` de 19 páginas al
  lado de un CBR de 531. Contar es barato; un cómic perdido no se recupera.

---

## 6. Lecciones de método

**La interfaz no es el sitio para explicar cómo funciona la app.** Se llegó a
9.023 caracteres de prosa por las pantallas, una explicación cada vez, todas
razonables por separado. El sitio de eso son estos documentos y los comentarios
del código. La regla está en `LECTOR-COMICS-DISENO.md` §14: se explica cuando
algo va mal o antes de una acción que borra o gasta cuota; el resto se calla.

**Hay un `comprobar.py` en la raíz: pasarlo antes de dar nada por terminado.**
Mira llaves y paréntesis sin cerrar y, sobre todo, **cuerpos huérfanos** —código
que se queda suelto a nivel de fichero cuando un borrado corta por en medio de
una función—. Ese fallo no descuadra nada y no se ve leyendo; solo lo caza
Gradle, y el 02/09/2026 llegó dos veces al móvil de Dani por no tener esto.

**Un reemplazo por rango tiene que comprobar qué hay DENTRO del rango.** Exigir
que el patrón aparezca N veces exactas —que es lo que este proyecto ya hacía— no
protege de esto: reescribiendo `TiraSerie` se borraron de paso `PantallaOrden` y
`FilaTramo`, porque el ancla del final estaba más lejos de lo que se creía. Ni
saltó la comprobación ni se desbalanceó nada. La verificación barata es listar
las funciones del fichero antes y después y comparar.

**Instrumentar antes que adivinar.** Si hay dos rondas seguidas de conjeturas,
toca añadir un diagnóstico que diga el motivo exacto.

**Y esta regla se cobró su propia deuda el 02/09/2026.** Apareció una pantalla
en negro de la que no se sale, y se intentó diagnosticar **tres veces
adivinando** —la posición del scroll, el índice de la tira, el fondo negro del
visor— sin acertar. Dani no sabe decir cuándo pasa, el fallo aparece usando el
móvil por ahí y no enchufado al PC, y desde donde se programa no hay ni móvil ni
logcat.

La salida fue `datos/Rastro.kt`: un fichero de migas de pan con hora —qué
pantalla, qué carpeta, qué cómic, qué dice el visor, y la traza si algo revienta—
que se lee desde **Ajustes > Diagnóstico** y se copia al portapapeles. Se cierra
la app, se vuelve a abrir, y ahí está lo que hacía justo antes.

Tres detalles del diseño que importan:

- **Fichero y no logcat**: logcat se pierde al desconectar el cable, y el fallo
  aparece lejos del PC.
- **Se escribe en el hilo que sea, sin corrutina.** Meter un salto de hilo por
  miga perdería justo las últimas —las que importan— si el proceso muere.
  Añadir una línea a un fichero cuesta microsegundos.
- **El manejador de excepciones se ENCADENA al que ya había, no lo sustituye.**
  El de Android es el que cierra la app y saca el diálogo del sistema; quitarlo
  dejaría el proceso colgado y en pantalla, que es sospechosamente parecido a lo
  que se está buscando.

**Una respuesta real vale más que la documentación.** El cliente de las wikis se
escribió a ojo, con reglas en cascada "por si acaso", y las dos mitades estaban
mal. Bastaron dos URLs pegadas en el chat. Cuando no se pueda llegar a una API
desde donde se programa, **pedir una respuesta de ejemplo antes de escribir el
parser, no después**.

**Y el código fuente es igual de real que la respuesta.** Diseñar un parche sin
el fichero delante sale caro: se propuso "añadir `count_of_issues` al
`field_list`" cuando ya llevaba años puesto, y el problema verdadero —la
petición doble— estaba a la vista en veinte líneas. Si el código se puede leer,
se lee antes de proponer nada.

**Un test que no se ha ejecutado no prueba nada, y puede estar mal él.** Ha
pasado dos veces (`HuecosTest` y `OrdenLecturaTest`), y la segunda el test
equivocado escondía un **fallo de diseño**: la expectativa describía lo que se
quería que pasara, no lo que el código hacía, porque se escribieron a la vez.
En este entorno no se pueden ejecutar los tests (Java 11, el plugin de Android
pide 17), así que la única red es **reimplementar la lógica aparte y comparar**.
Hacerlo cuesta cinco minutos y las dos veces ha encontrado algo.

**Un caso comprobado no comprueba los demás.** Daredevil funcionaba y de ahí no
se seguía nada: DC usaba otros nombres de categoría, Spider-Man otro título y
"Green lantern" fallaba por una mayúscula. Cuando algo debe valer para "todo lo
que el usuario meta", hay que probar formas distintas.

**No cachear los fallos pasajeros.** La lección vieja —cachear los fallos al
abrir ficheros— casi hunde esto: al pasarse del límite, Comic Vine devuelve 420
y la serie se quedaba con cero números **para siempre**, porque el siguiente
intento leía el cero de la caché. Allí el fallo es permanente; aquí es pasajero.
Distinguir los dos casos es la diferencia.

**Y el reverso: tampoco olvidar los permanentes.** El mismo cero significaba dos
cosas —"me cortaron" y "no existe"— y por no separarlas el botón de reparar
gastaba la cuota en las que no tenían arreglo. Un valor que puede venir de dos
causas con remedios opuestos necesita **dos campos, no uno**.

**Cuando algo se descarta, tiene que haber camino de vuelta.** Marcar una serie
como "no encontrada" arregla el gasto, pero si no hay botón para reintentarlas
se convierte en el tope silencioso de 200 con otro disfraz: un recorte que se
calla y pasa por "esto es todo lo que hay".

**Un tope que se calla pasa por "esto es todo lo que hay".** El de 200 de Comic
Vine escondía 2017 series. Si se recorta algo, hay que decirlo. **Vale igual
para el orden de lectura**: las carpetas sin vincular se enseñan en pantalla en
vez de omitirse.

**No inventar precisión que la fuente no da.** El orden de lectura sale por mes
y no en una secuencia exacta porque **dentro de un mes el dato no dice nada**.
Es la misma regla por la que `Huecos` no habla de los extremos y por la que un
número que no se entiende queda ausente y nunca en cero.

**Un umbral fijo casi nunca vale; comparar sí.** Vale para las portadas y vale
para el "empieza aquí": no se le pregunta al modelo si una serie es la puerta de
entrada, se le pregunta cuál de dos entra mejor.

**La numeración de volúmenes no está en ninguna base de datos.** "Vol. 6" es una
convención de aficionados. Las wikis sí tienen páginas literales.

**Separar la red de las reglas.** Todo lo que decide algo está en funciones
puras que se prueban con respuestas reales guardadas.

**Cuidado con las cachés en memoria duplicadas.** Un almacén instanciado dos
veces escribe bien en disco pero la otra instancia sigue viendo su copia vieja.

---

## 7. Estado y pendientes

### Las pruebas: dónde están de verdad

**Ahora sí hay pruebas, trece ficheros**, en
`app/src/test/java/com/dani/lector/datos/`:

| Fichero | Qué cubre |
|---|---|
| `HuecosTest` | repetidos, desordenados, el #0, carpeta sin números |
| `ParserTest` | el caso del *Secret Origin* y los formatos reales |
| `EstadoSerieTest` | los extremos, "en emisión", el texto de lo que falta |
| `NovedadesTest` | a quién preguntar, cuándo avisar, cuándo sale el siguiente, la guarda del 420 |
| `ProgresoTest` | que consultar un marcapáginas no cuente como leer |
| `EstadisticasTest` | el avance sobre tus ficheros, series completas, páginas |
| `CalendarioTest` | las semanas del mes, el domingo, el bisiesto, lo leído por día |
| `LecturaTest` | qué cuenta como leer: avanzar, hoja doble, volver atrás, otro día |
| `SaltoTest` | "ir a la página": vacío, letras, cero, pasado del final |
| `OrdenTest` | por número (10 después del 2), por nombre, recientes, empates |
| `SiguienteTest` | por cuál seguir: lo empezado gana, el hueco no adelanta, la pág. 0 no cuenta |
| `ExportarTest` | el nombre del fichero al guardar una página |
| `AgendaTest` | qué sale próximamente: orden por fecha, sin fecha fuera, "mañana" / "en 3 días" |
| `RecorteTest` | el marco liso: fondo negro, ruido del escáner, el tope de la mitad, la regla del 40% |
| `ImagenesTest` | qué entra como página: `._` de macOS, `__MACOSX`, y el orden por profundidad |
| `LimpiezaTest` | qué se renombra y qué se borra: "Corps (21)", "Batman (2016)", el relleno de cifras |

**EJECUTADOS POR FIN el 03/09/2026**, con Java 17 en el entorno. `./gradlew
testDebugUnitTest`: **126 pruebas, una en rojo**, y el fallo estaba en la prueba,
no en el código — `HuecosTest` esperaba `"el 2, el 4 y el 6 y 2 tramos más"`, con
dos "y" seguidas, y el código escribe `"el 2, el 4, el 6 y 2 tramos más"`, que es
lo correcto y lo que confirman los otros dos casos de `texto`. Se corrigió la
expectativa. **Las otras 125 pasaron a la primera**, así que el contraste en
Python valió: eran expectativas escritas a mano y acertaron.

**LA DEUDA MÁS CARA, `elegirVolumen`, YA ESTÁ PAGADA (03/09/2026).**
`app/src/test/java/com/dani/lector/red/ElegirVolumenTest.kt`, **12 casos, uno por
regla y uno por cada borde que se conocía**: nombre exacto normalizado (que es lo
que evita emparejar "Green Lantern" con "Green Lantern Corps Quarterly"),
editorial mayoritaria con las ediciones de ECC / Panini / Planeta / Televisa /
TM-Semic del caso real de `query=Green Lantern`, año exacto por delante del año
de margen, el margen de un año, dos años ya fuera, un candidato sin año que no
puede colarse por el margen, a igualdad la que más números tiene, y la
normalización de mayúsculas, acentos y puntuación. Los candidatos salen de las
respuestas reales del 25/08/2026 recogidas en la sección de Comic Vine de este
documento, recortados a los cuatro campos que la función mira.

Sigue **sin prueba** `Wiki.interpretar`, `Wiki.interpretarIndice`, `Eras.de`,
`Racha`, `Busqueda.de`, `Formatos.de` y `Vinculador`.

**Sin verificar**: todo el código de Compose, que solo se compila en Android
Studio.

### Lo tocado el 03/09/2026 (cuarta tanda) — SIN COMPILAR

| Fichero | Qué |
|---|---|
| `datos/Novedades.kt` | `Prevista`, `agenda` y `cuandoSale` |
| `test/…/AgendaTest.kt` | **nuevo**: 10 casos |
| `ui/Pantallas.kt` | sección "PRÓXIMAMENTE" en Lecturas y `FilaPrevista` |
| `ui/Lector.kt` | la barra de progreso se separa 18 dp del borde |

### Lo tocado el 03/09/2026 (tercera tanda) — SIN COMPILAR

| Fichero | Qué |
|---|---|
| `datos/Siguiente.kt` + `SiguienteTest` | **nuevos**: por cuál seguir en esta carpeta |
| `datos/Exportar.kt` + `ExportarTest` | **nuevos**: la página a la galería o a otra app |
| `datos/Salto.kt` | `deBarra`: la página que toca al arrastrar la barra |
| `datos/Progreso.kt` | `restaurar`: dejar una marca exactamente como estaba |
| `VistaModelo.kt` | `deshacerMarcado`/`cerrarDeshacer`, `siguienteSinLeer`, `Estado.deshacer` |
| `ui/Componentes.kt` | `OpcionMenu` sube aquí desde `Pantallas.kt` (el visor la necesita) |
| `ui/Lector.kt` | barra de progreso arrastrable con globo, y `HojaExportar` con pulsación larga |
| `ui/Pantallas.kt` | `FilaSeguirSerie`, `ChipAmbito` y el buscador acotado a la carpeta |
| `MainActivity.kt` | `AvisoDeshacer` flotando encima de las pestañas |
| `AndroidManifest.xml` + `res/xml/rutas_compartir.xml` | el `FileProvider` para compartir |

### Lo tocado el 03/09/2026 (segunda tanda) — SIN COMPILAR

| Fichero | Qué |
|---|---|
| `datos/Salto.kt` + `SaltoTest` | **nuevos**: "ir a la página N", con los cuatro casos malos |
| `datos/Orden.kt` + `OrdenTest` | **nuevos**: ordenar una carpeta por número, nombre o recientes |
| `datos/Modelos.kt` | `Comic.cuando` (última modificación según SAF), con valor por defecto |
| `datos/Escaner.kt` | `COLUMN_LAST_MODIFIED` en la proyección; el orden sale de `OrdenCarpeta` |
| `VistaModelo.kt` | `marcarCarpeta`, `cuantasPaginas`, `orden`; `marcarLeido` cuenta las páginas de verdad |
| `ui/Componentes.kt` | `Campo` admite teclado numérico |
| `ui/Lector.kt` | el contador abre "ir a la página" |
| `ui/Pantallas.kt` | `MenuCarpeta` (orden + marcar en bloque), y la fila "Cómics" lo abre |
| `AndroidManifest.xml` | `launchMode="singleTask"` y dos `intent-filter` de VIEW para CBZ/CBR |
| `MainActivity.kt` | abre el cómic que llega de fuera con "abrir con..." |

### Lo tocado el 03/09/2026 — SIN COMPILAR

| Fichero | Qué |
|---|---|
| `datos/Sesiones.kt` | **nuevo**: el diario (`Sesion`, `Lectura`, `Sesiones`) |
| `test/…/LecturaTest.kt` | **nuevo**: qué cuenta como leer |
| `datos/Calendario.kt` | **nuevo**: el mes, lo leído por día, y `Leido.tramo` ("págs. 3-4") |
| `test/…/CalendarioTest.kt` | **nuevo**: semanas, domingo, bisiesto, lo leído, el tramo |
| `datos/Racha.kt` | el día sale de `Novedades.ZONA`, no de la zona del móvil |
| `ui/Pantallas.kt` | `ChipsFiltro`/`Filtro`, `CalendarioMes`/`Casilla`/`DetalleDelDia`, `FilaSeguida`, y `onAtras` pasa a admitir `null` |
| `MainActivity.kt` | el NavHost de cinco destinos a tres: `principal` es un `HorizontalPager` de 3 páginas; `BarraInferior` fuera del pager con la pestaña marcada por `currentPage` |
| `VistaModelo.kt` | `sesiones`, copia v3 (guarda el diario) |
| `res/drawable/ic_lanzador_frente.xml`, `ic_lanzador_mono.xml` | **nuevos**: el bocadillo de cómic |
| `res/values/colores.xml`, `mipmap-anydpi-v26/…` | el icono adaptativo, negro sobre `#FCEE0A` |

### Lo tocado el 02/09/2026 — SIN COMPILAR

| Fichero | Qué |
|---|---|
| `datos/Novedades.kt` | **nuevo**: a quién repreguntar y qué números son nuevos |
| `test/…/NovedadesTest.kt` | **nuevo**: 9 casos |
| `VistaModelo.kt` | `revisarNovedades`, `seguirSerie`, copia v2, y −13 KB al quitar el TODO |
| `datos/Estadisticas.kt` | rehecho: cuenta sobre tus ficheros, no sobre las listas |
| `test/…/EstadisticasTest.kt` | **nuevo**: 6 casos |
| `ui/Pantallas.kt` | `PantallaOrden` + `FilaTramo`, y la entrada en `PantallaCarpeta` |
| `MainActivity.kt` | la cola de avisos; la pestaña Lecturas abre las estadísticas; ocho rutas fuera |
| `datos/Vigilante.kt` | **nuevo**: la pasada compartida, la notificación y el trabajo diario |
| `datos/Novedades.kt` | `aAvisar`, `venta`, `estimada`, `fraseVenta`, `ZONA`/`hoy`, `etiquetasDe`, dos velocidades en `aRevisar` |
| `red/FuenteComics.kt` | `NumeroRemoto.venta` (la fecha de venta real) |
| `red/ComicVine.kt` | `store_date` en el `field_list` de `numerosDe` |
| `datos/SeriesRemotas.kt` | campos `seguida` y `avisados`, compatibles con el JSON viejo |
| `res/drawable/ic_aviso.xml` | **nuevo**: el icono monocromo de la notificación |
| `AndroidManifest.xml` | `POST_NOTIFICATIONS` |
| `app/build.gradle.kts` | `androidx.work:work-runtime-ktx:2.9.1` |
| `LectorApp.kt` | `onCreate`: crea el canal y programa el trabajo |
| `VistaModelo.kt` | `seguirSerie`, y `revisarNovedades` pasa a delegar en `Vigilante` |
| `ui/Pantallas.kt` | `SeguirSerie` dentro de `TiraSerie`, con el permiso; "el siguiente sale el…"; **poda de textos en todas las pantallas** (9.023 → 6.009 caracteres) y el "no es esta" pasa a diálogo |
| `datos/Progreso.kt` | `Progreso.cuenta`: consultar un marcapáginas no es leer |
| `test/…/ProgresoTest.kt` | **nuevo**: 6 casos |

### Lo tocado el 25/08/2026

| Fichero | Qué |
|---|---|
| `red/ComicVine.kt` | la segunda búsqueda solo sale si la primera respondió; `/search/` en vez de `/volumes/?filter=` en `volumen()` y `buscarSeries()`, con el filtro viejo de respaldo; `encQuery` para el `%20` |
| `datos/Modelos.kt` | campo `Serie.noEncontrada`, por defecto `false` |
| `datos/Listas.kt` | guardar y leer ese campo (`optBoolean`, compatible) |
| `datos/GeneradorLista.kt` | marcar la descartada ya al crear la lista (`Triple`) |
| `VistaModelo.kt` | `sinNumeros` sin descartadas, `descartadas()`, `forzar` |
| `ui/Pantallas.kt` | segundo enlace para reintentar las descartadas |

**RESULTADO en el móvil, Green Lantern: de 18 descartadas a 9.** Se resolvieron
solas las que el diagnóstico predecía —Green Lantern (2021) y (2023), Ion
(2006), The Green Lantern: Season Two (2020)—, que era justo lo que el orden por
`id` escondía pasada la primera página.

**Las 9 que quedan ya no son un fallo de búsqueda: las dos fuentes llaman
distinto a la misma cosa, y a veces ni siquiera es la misma cosa.**

  - `Just Imagine` (2001) — Comic Vine lo tiene, pero como
    `Just Imagine Stan Lee With Dave Gibbons Creating Green Lantern`
    (`id` 26069). La wiki lo abrevia y el nombre exacto no casa.
  - `Tales of the Sinestro Corps` (2007) — **en Comic Vine no existe ese
    volumen**: están los one-shots sueltos. La wiki los agrupa y Comic Vine no.
    No hay nada que encontrar.
  - `DC Retroactive: Green Lantern` (2011), `Justice League: The Darkseid war`
    (2015), `Hal Jordan and the Green Lantern Corps` (2016),
    `Green Lantern: The Lost Army` (2015) y tres más — misma familia.

  **DECIDIDO (Dani, 25/08/2026): se quedan así.** Los motivos, por si dentro de
  seis meses parece que faltó ambición:

  1. La alternativa era relajar o ampliar `elegirVolumen`, que es **la función
     que impide que se cuele basura**, y que hoy no tiene ni una prueba que la
     respalde. Mucho riesgo para rescatar 9 de 60.
  2. Algunas **no se pueden rescatar de ninguna manera**: `Tales of the
     Sinestro Corps` no existe como volumen en Comic Vine.
  3. Ya hay salida manual: "añadir una serie a mano".

  Si algún día se retoma, el camino es **en ese orden**: pruebas primero,
  `aliases` después.

  **Y ahora tienen una consecuencia nueva**: una serie sin vincular no entra en
  el orden de lectura. La pantalla las lista para que se vea cuáles faltan.

### La pasada de rendimiento (03/09/2026): tres sospechas, dos ciertas

Estaban escritas como **sospechas SIN VERIFICAR**. Se miraron una a una:

- **`Rastro.apunta` — FALSA.** No relee el fichero por cada miga: solo poda
  cuando pasa de `LINEAS * 120` bytes, unos 36 KB, y el resto de las veces es un
  `appendText` y un `length()`. El comentario del propio fichero ya lo decía.
  **La sospecha estaba mal escrita, el código estaba bien.**
- **`VistaModelo.orden` — CIERTA, y la causa era otra.** El problema no era
  `orden` sino `prefs`, que era `private val prefs get() = ctx.getSharedPreferences(...)`:
  **una llamada por cada uno de los ~20 accesos a preferencias de la clase**, no
  solo el de `orden`. Y `orden` sí se lee dentro de la lista de la biblioteca
  (`Pantallas.kt:253`). Arreglado en la raíz con `by lazy`: una línea, y vale
  para los veinte sitios en vez de para uno. **Es el caso de manual de "arregla
  la función compartida, no cada llamador".**
- **`Novedades.hoy()` por fila — CIERTA.** `Pantallas.kt:1009` (ficha de serie) y
  `Pantallas.kt:1712` (`FilaSeguida`) llamaban a `LocalDate.now(ZONA)` sueltas
  dentro del composable: una por fila y por recomposición. Metidas en un
  `remember`.
- **`androidx.documentfile` — CIERTA, dependencia muerta.** Su única aparición en
  todo `app/src` era un comentario de `Escaner.kt` explicando que **no** se usa
  (el escáner va con `ContentResolver` a pelo). Fuera de `build.gradle.kts`.

**Lo que NO se ha tocado, a propósito.** El diagnóstico escrito sobre que
`TarjetaComic` y `FilaResultado` "nunca se pueden saltar" por recibir el
ViewModel sigue **sin recomprobar**: con Kotlin 2.0.21 el *strong skipping* está
activado por defecto y puede que ya no sea verdad. Reescribirlas por si acaso es
exactamente lo que la regla del proyecto prohíbe. **Eso se mide con el Layout
Inspector delante, y hasta entonces no se toca.**

### La pasada por el codigo entero (03/09/2026): que se quito y que NO

Se barrio `app/src` entero buscando lo que sobra. **Salio muy poco, y eso es un
dato**: 10.626 lineas y solo cuatro declaraciones muertas.

- `ConversorCarpeta.carpetaOriginales` — 11 lineas, privada, **no la llamaba
  nadie**. La constante `CARPETA_ORIGINALES` sí se usa (`Escaner` la ignora al
  escanear), asi que se queda: lo muerto era la funcion, no la constante.
- `Tema.RojoLector` y `Tema.AzuliOS` — el comentario decia literalmente "por si
  se vuelve". Nadie los usaba. Ahora hay git: si se vuelve, se sacan de ahi.
- `Tema.FormaMarca` — declarada y nunca usada.
- La lista de extensiones de imagen estaba **duplicada** en `ComicZip` y en
  `Rar5`, y el propio `Rar5` lo decia en un comentario ("igual que en
  ComicZip"). Ahora vive en `ComicZip.EXT` y `Rar5` la lee de ahi.
- El unico aviso del compilador que quedaba (`Icons.Filled.List`, obsoleto) esta
  arreglado con la version `AutoMirrored`. **`compileDebugKotlin` no saca ni un
  `w:`.**

**El escaneo se puede repetir**: cuenta las apariciones de cada declaracion en
todo `app/src` con los comentarios quitados, y lo que aparece una sola vez es su
propia definicion. Ahora da cero.

### De donde viene el peso del APK, medido (03/09/2026)

APK de debug, **25,5 MB**, abierto y contado por dentro:

| Qué | MB | |
|---|---|---|
| `lib/arm64-v8a/lib7-Zip-JBinding.so` | **15,81** | **62% del APK** |
| `classes.dex` + los otros ocho dex | 9,3 | el codigo, Compose incluido |
| `resources.arsc` y `res/` | 0,41 | |

**El 62% del APK es el motor nativo de 7-Zip, y esta ahi para una sola cosa:
convertir los CBR.** No se toca. `Rar5` no es un extra: `ComicZip` lo llama para
los RAR5 **y para los RAR4 grandes**, que sin convertir cierran la app porque
junrar se traga el fichero entero en memoria. Quitar la dependencia son 15,8 MB
menos y **perder los CBR**, que es justo lo que Dani dijo que no.

Se deja escrito para no volver a medirlo: **por el lado del tamaño ya no queda
nada barato**. `abiFilters` ya esta en solo `arm64-v8a` y R8 con
`isShrinkResources` en release. Lo unico que movería la aguja es un 7-Zip
compilado con menos codecs, que es un proyecto entero, no una tanda.

### Marcar una carpeta reescribia el fichero una vez por comic

`Progreso.marcar` guarda **el JSON entero** cada vez. Es lo correcto para una
marca suelta y una barbaridad para treinta seguidas: `marcarCarpeta` llamaba a
`marcarTerminado` por comic, y `deshacerMarcado` a `restaurar` por comic, asi
que una carpeta de treinta eran **treinta reescrituras del fichero completo**, y
deshacerlo, otras treinta.

Arreglado en `Progreso` **y no en los dos llamadores**: `tanda { }` levanta una
bandera, `guardar()` no hace nada mientras dura, y al salir escribe una vez.
Dos sitios envueltos, una sola escritura cada uno. Si mañana aparece un tercer
bucle, se envuelve igual.

**NO tiene prueba automatica**: `Progreso` necesita un `Context` para saber donde
esta `filesDir`, y las trece pruebas del proyecto son de funciones puras. Meter
Robolectric por esto seria mas dependencia que arreglo. **Comprobado leyendo, no
ejecutando.**

`Progreso.importar` ya guardaba una sola vez al final, y `Marcadores`, `Sesiones`
y `SeriesRemotas` no tienen ningun bucle que escriba por elemento. Se miraron.

### El tiron al pasar por primera vez a Lecturas (03/09/2026)

Dani, con el build de ese dia en el movil: **"cuando carga por primera vez, las
transiciones de moverme de la biblioteca a las lecturas van con un poco de lag"**.

**Causa, leida en el codigo y no adivinada.** `Progreso`, `Sesiones` y
`SeriesRemotas` cargan su JSON **la primera vez que alguien les pregunta**, y esa
primera vez ocurria dentro de los `remember` de `PantallaEstadisticas`:

```
val r = remember(...) { Estadisticas.calcular(vm.marcas.todas(), it) }
val seguidas = remember(...) { vm.seriesSeguidas() }        // seriesRemotas.todas()
val porDia = remember(...) { Calendario.porDia(vm.sesiones.todas(), ...) }
```

Un `remember` corre **en el hilo principal, en mitad de la composicion**. Asi que
el primer deslizamiento a Lecturas leia y parseaba **tres ficheros de golpe** sin
soltar la UI — y encima el `HorizontalPager` compone la pagina vecina mientras
arrastras, asi que el tiron cae justo en la animacion.

**Arreglado precalentando, no reescribiendo la pantalla.** `VistaModelo.init`
llama a `precalentar()`, que en `viewModelScope` con `Dispatchers.IO` pide
`marcas.todas()`, `sesiones.todas()` y `seriesRemotas.todas()`. No calcula nada ni
toca el estado: deja la cache caliente para que la pantalla se la encuentre hecha.
En `viewModelScope` y no en la corrutina de una pantalla, **por la misma razon que
el indice**: salirse de la pantalla no debe cancelarlo.

**Y de paso se cerro una carrera que el precalentado creaba.** Los cuatro
almacenes hacian `cache = m; return m` al final de `cargar()`. Con dos hilos
cargando a la vez, el que terminaba el ultimo pisaba la cache del otro — y si el
primero ya habia escrito una marca en la suya, esa marca desaparecia de memoria
(del fichero no, pero la pantalla mentia hasta recargar). Ahora es
`return cache ?: m.also { cache = it }`: **manda el que llego primero**. Cuatro
ficheros, una linea cada uno.

**LO QUE FALTA POR MEDIR, y por eso lleva cronometro.** Quitado el disco, en el
hilo principal queda todavia `Estadisticas.calcular`, que recorre la biblioteca
entera. **No se ha tocado**: se ha instrumentado. El rastro escribe ahora dos
lineas nuevas —`fichas precargadas en N ms` y `estadisticas de N cómics en N ms`—
y con esos dos numeros se decide. Si son milisegundos, esto ya esta; si son
decenas, el calculo se va a `Dispatchers.Default` y lo tapa el
`CircularProgressIndicator` que la pantalla **ya tiene** para cuando `r == null`.
Se instrumenta antes de mover nada porque es exactamente la regla que costo una
tanda entera aprender.

### Lo que dijo el rastro del movil (03/09/2026, 18:06) — MEDIDO

Se instrumento, se instalo, y Dani pego el rastro. **Los numeros mataron la
sospecha principal y sacaron tres cosas que nadie estaba mirando.**

```
18:06:32.932    fichas precargadas en 37 ms
18:06:33.322    índice: 293 cómics en 332 ms
18:06:38.990    estadísticas de 293 cómics en 5 ms
```

- **`Estadisticas.calcular`: 4-6 ms con 293 comics.** Era la sospechosa y **no
  era**. Se quita el cronometro y se queda donde esta: moverla a
  `Dispatchers.Default` seria pagar un salto de hilo por cinco milisegundos.
- **El precalentado: 37 ms.** Eso es lo que se le quito al hilo principal. Real,
  pero no es el tiron que Dani nota.
- **El indice: 253-495 ms** en cada arranque, y a veces **dos veces por sesion**.

**LA PETADA, que estaba en el rastro dos veces y nadie habia mirado.**

```
!!! PETADA en main: java.lang.IllegalArgumentException:
Key "DC Comics/Green lantern/Absolute green lantern" was already used.
```

`PantallaEstadisticas` tiene **dos `items()` en el MISMO `LazyColumn`** —las
series que sigues y el nivel que estas navegando— **y los dos iban con
`key = { it.ruta }`**. Un LazyColumn comparte espacio de claves entre todos sus
`items`, asi que una serie **seguida** que ademas aparezca en el nivel actual
repite clave y Compose cierra la app. Paso las dos veces igual: entrar en
"Absolute green lantern", que Dani sigue, y luego pasar a Lecturas. Arreglado
poniendo prefijo a las claves (`"seguida:"` y `"nivel:"`), que es lo unico que
hacia falta: las rutas ya eran unicas **dentro** de cada lista.

**LA BARRA FLOTANTE NO SE COMIA EL TOQUE.** Dani: *"cuando le doy a la barra de
abajo para moverme a las lecturas o ajustes, siempre doy sin querer a algun
comic"*. La pildora pinta fondo, chaflan y filo, pero **solo las tres `Pestana`
atendian punteros**: el relleno de 4 dp, los huecos del `SpaceEvenly` y el filo
dejaban pasar el toque a la lista de debajo. Un
`pointerInput(Unit) { detectTapGestures { } }` en el `Row` los absorbe. Se usa
eso y no `clickable` **a proposito**: `clickable` añadiria semantica de boton a
un contenedor que no lo es, y lo leeria TalkBack.

**EL INDICE CONSULTABA CADA CARPETA DOS VECES.** `todosBajo` recorre el arbol
llamando a `Escaner.abrir` en cada carpeta, y `abrir` llama a `contar` por cada
subcarpeta para el rotulo de su fila. Recorriendo entero, **cada carpeta se
consultaba una vez como `contar` desde su padre y otra como `abrir` al
visitarla**, y las cuentas de esa segunda vuelta se tiraban: `todosBajo` solo usa
`comics` y `carpetas`. Ahora `abrir` lleva `conCuentas`, y el recorrido va con
`false`. **Quien navega sigue viendo las cuentas**, que es donde se ven.

**SIN EXPLICAR TODAVIA, y es lo siguiente que hay que medir.** Volver a la
biblioteca desde el visor tarda **~720 ms** de forma sospechosamente constante:

```
02:08:22.937  pantalla: inicio
02:08:22.977  carpeta: «raíz»
02:08:23.694    leída: 2 carpetas, 0 cómics     ← 717 ms
```

y esa misma lectura al arrancar tarda **20-60 ms**. Es la MISMA carpeta y el
MISMO trabajo: dos carpetas y ningun comic. `Escaner.abrir` ya corre en
`Dispatchers.IO`, asi que no es el hilo principal. Lo que se ve constante entre
700 y 725 ms **huele a espera fija, no a trabajo**. Hipotesis sin comprobar:
contencion con SAF cuando algo mas esta recorriendo el arbol a la vez.

**RESUELTO EL 04/09/2026, Y NO ERA NADA DE LO QUE SE SOSPECHABA.** Ver mas
abajo, "Los 706 ms eran una animacion".

**Instrumentado en su dia asi:** `Escaner.abrir` lleva el cronometro
partido y apunta **solo si pasa de `LENTO_MS` (200 ms)**, para no ensuciar el
rastro navegando:

```
LENTA «raíz»: 717 ms (cursor 12, contar 4, 2 subcarpetas, 0 cómics)
```

Y el indice apunta ahora tambien cuando **empieza**, no solo cuando acaba, que es
lo que permite ver si la lectura lenta cae DENTRO de un recorrido del arbol.

**Como se lee el resultado**, decidido antes de mirarlo para no contarse un
cuento con el numero delante:

- `cursor` alto → la consulta a SAF es lenta de verdad. La carpeta se lee una
  sola vez, asi que no hay nada que quitar: tocaria cachear el contenido.
- `contar` alto → lo caro son las cuentas por subcarpeta. Se calculan solo para
  el rotulo de cada fila, asi que se pueden diferir o cachear.
- **`cursor` y `contar` bajos pero `total` alto** → no es trabajo, es **espera**.
  Confirma la contencion, y el arreglo va en quien reserva SAF, no aqui.
- Si la linea `LENTA` **no sale** y el hueco sigue estando en el rastro, entonces
  los 720 ms no estan dentro de `abrir` y hay que buscar en el `LaunchedEffect`
  que la llama.

### La version de iPad: por que KMP y no Flutter (03/09/2026)

Dani trajo un PDF de una conversacion con Gemini sobre programar iOS desde
Linux/Windows. **Su recomendacion principal era Flutter, y para esta app estaba
mal**, pero no por culpa del que respondia: la conversacion **termina preguntando
si la app usa Jetpack Compose o XML, y nunca se contesto**. Usa Compose al 100%.
Con ese dato la respuesta cambia: **Compose Multiplatform reaprovecha el codigo;
Flutter significaria tirar 10.626 lineas y aprender Dart**.

**Lo que se midio antes de decidir nada**, contando imports de Android por
fichero:

| | Lineas | Que hay que hacer |
|---|---|---|
| Porta tal cual | ~1.100 | Huecos, Parser, Racha, Estadisticas, Orden, Siguiente, Formatos, Busqueda, EstadoSerie, Salto, Modelos, Tema, `elegirVolumen` con sus pruebas |
| Porta con cambio mecanico | ~1.600 | `Novedades`/`Calendario` (`java.time` → kotlinx-datetime), `ComicVine` (`HttpURLConnection` → Ktor), los cuatro almacenes JSON |
| UI, porta con cirugia | ~3.800 | `Pantallas`, `Lector`, `Componentes`: Compose compila, pero tocan `Bitmap`, `Uri` y las teclas de volumen |
| Hay que reimplementar | ~2.500 | `Escaner` (SAF no existe en iOS), `ComicZip`+`Rar5`+`ConversorCarpeta`, `Miniaturas`/`ColorPortada`, `Vigilante` |

**DOS COSAS QUE EL PDF NO DICE Y SON LAS QUE DUELEN.** La primera: **junrar es
Java y 7-Zip-JBinding es JVM mas una libreria nativa**, y ninguno corre en
Kotlin/Native. Dani quiere en iOS el mismo trato que en Android —lee CBZ, y un
CBR que entre se convierte a CBZ—, asi que **iOS necesita su propio motor RAR**.
Lo salva la arquitectura que ya hay: `Formatos` detecta, `Rar5.aCbz` convierte y
`ComicZip` lee, o sea que **solo se sustituye la pieza de convertir**, no el
camino. La segunda: **desde Windows no se puede compilar iOS**, asi que esa pieza
en concreto se verifica en el CI o no se verifica.

**Tanda 1, HECHA Y VERIFICADA.** Se creo `:shared` y se movieron **sin tocar una
sola linea** los diez ficheros con cero dependencias: `Huecos`, `EstadoSerie`,
`Salto`, `Orden`, `Siguiente`, `Formatos`, `Modelos`, `Busqueda`, `Parser` y
`red/FuenteComics` (con `elegirVolumen`), mas sus **siete ficheros de prueba**,
que pasan a `commonTest` con `kotlin.test` en vez de JUnit. `git mv`, asi que el
historial de cada fichero sigue entero.

**Se movio tambien `Marca`**, que vivia dentro de `Progreso.kt`. `Progreso`
necesita `Context` para saber donde escribir; **la marca en si son tres numeros y
dos cuentas** y la usan `Siguiente`, `EstadoSerie` y `Estadisticas`, que son
comunes. Quien la guarda es de cada plataforma; lo que guarda, de los dos.

**Se quedaron fuera a proposito `Racha` y `Estadisticas`**, y por una linea cada
uno: `java.util.TimeZone` y `System.currentTimeMillis()`. Entran en la tanda 2
con kotlinx-datetime. **No se mezcla un cambio de dependencia con un movimiento
de ficheros**: si algo se rompe, que se sepa cual de las dos cosas fue.

Verificado: `:shared:build`, `:app:assembleDebug` y las pruebas de los dos
modulos en verde, y comprobado con un filtro `--tests` a una clase inexistente
que las pruebas movidas **se ejecutan de verdad** y no es que no fallen.

### Tanda 2 del port: las fechas pasan a kotlinx-datetime (03/09/2026)

**Por que entera y no a medias.** El plan era mover solo `Racha` y
`Estadisticas`, que era lo minimo. No se pudo: `Novedades` y `Calendario`
**devuelven `LocalDate` a la interfaz**, asi que dejarlos en Android habria
dejado **dos librerias de fecha conviviendo** —`java.time` en la pantalla y
kotlinx en la logica— con conversiones en la frontera. Peor que hacerlo entero.
La red de seguridad para hacerlo de golpe existia: **cinco ficheros de prueba de
fechas** (`Novedades`, `Calendario`, `Agenda`, `Estadisticas` y las de `Racha`).

**Movidos a `:shared`**: `Novedades`, `Calendario`, `Racha`, `Estadisticas` y sus
cuatro ficheros de prueba.

**Las traducciones que NO son obvias**, apuntadas porque se van a volver a
necesitar:

| java.time | kotlinx-datetime |
|---|---|
| `ZoneId.of("Europe/Madrid")` | `TimeZone.of("Europe/Madrid")` |
| `LocalDate.now(ZONA)` | `Clock.System.todayIn(ZONA)` |
| `a.isAfter(b)` / `isBefore` | `a > b` / `a < b` (LocalDate es Comparable) |
| `plusDays(n)` / `minusDays(n)` | `plus/minus(DatePeriod(days = n))`, **y es funcion de extension: hay que importarla** |
| `ChronoUnit.DAYS.between(a, b)` | `a.daysUntil(b)` — **devuelve Int, no Long** |
| `.monthValue` | `.monthNumber` |
| `dayOfWeek.value` | `dayOfWeek.isoDayNumber` |
| `Instant.ofEpochMilli(t).atZone(z).toLocalDate()` | `Instant.fromEpochMilliseconds(t).toLocalDateTime(z).date` |
| `primero.lengthOfMonth()` | **no existe**: `primero.plus(1 mes).minus(1 dia).dayOfMonth` |
| `fecha.format(ISO_LOCAL_DATE)` | `fecha.toString()`, que ya es ISO-8601 |
| `YearMonth` | **no existe**: un `LocalDate` al dia 1 |

**`String.format` tampoco existe en Kotlin/Native.** `Calendario.clave` construia
"2026-09-03" con `"%04d-%02d-%02d".format(...)`; ahora rellena con `padStart`. No
es cosmetico: **esa cadena es la clave con la que el calendario encuentra lo
leido**, y si un dia saliera "2026-9-3" no daria ningun error, simplemente no
encontraria nada.

**Tres clases mas tuvieron que salir de su fichero**, y las tres por el mismo
motivo: eran datos puros atrapados dentro de una clase que necesita `Context`.

- `Marca`, de `Progreso.kt`
- `Sesion`, de `Sesiones.kt`
- `Ficha`, que estaba **anidada** dentro de `SeriesRemotas`. Kotlin no deja poner
  un alias de tipo dentro de una clase, asi que pasa a primer nivel y el nombre
  cambia de `SeriesRemotas.Ficha` a `Ficha`. Siete referencias en cuatro
  ficheros.

**Es un patron, no tres casualidades**: el dato quiere ser comun y el almacen es
de cada plataforma. Cuando la tanda 4 mueva los cuatro almacenes JSON, esto ya
esta hecho.

**Y la dependencia va como `api`, no `implementation`.** `Novedades` y
`Calendario` **devuelven** `LocalDate`, asi que quien use `:shared` tiene que ver
el tipo. Con `implementation` compilaba `:shared` y fallaba `:app` con
"Cannot access class kotlinx.datetime.LocalDate", que no dice en absoluto que el
problema sea el alcance de la dependencia.

Verificado: `:shared:build`, `:app:assembleDebug` y las pruebas en verde, y cada
clase de prueba de fechas lanzada **por separado** con `--tests` para ver que se
ejecuta y no que no falla.

### Tanda 3 del port: Comic Vine pasa a Ktor (03/09/2026)

`ComicVine` se muda a `:shared`. Era el candidato natural: **ya estaba detras de
la interfaz `FuenteComics`**, que se escribio justo para poder cambiar de
proveedor tocando una linea, y ha servido para cambiar de *transporte*.

**Lo que habia que sustituir**: `HttpURLConnection`, `URLEncoder` y `org.json`
son de la JVM y no existen en Kotlin/Native.

**No se han hecho clases `@Serializable`, y es deliberado.** Se escribio
`PuenteJson.kt`, cuatro funciones de extension —`optString`, `optInt`,
`optJSONArray`, `optJSONObject`— con **los mismos nombres que las de org.json**,
sobre `kotlinx.serialization.json`. Dos motivos:

1. La respuesta de Comic Vine es un objeto enorme del que la app usa seis campos,
   cambia sin avisar y trae campos ausentes o a null segun el volumen. Con clases
   declaradas, **un campo inesperado tira el parseo entero**; leyendo a mano, lo
   que no esta simplemente no esta.
2. Manteniendo los nombres, **el codigo que interpreta la respuesta se queda
   igual letra por letra**. Si algo se rompe en el port, no fue en la
   interpretacion.

Tampoco hace falta el plugin de serializacion: solo el runtime.

**Se fue `withContext(Dispatchers.IO)`.** Estaba para no bloquear el hilo con
`HttpURLConnection`; **Ktor suspende en vez de bloquear**, asi que sobra. Queda
un `conRed { }` que no cambia de hilo y solo existe para que los `return@` de
dentro tengan a donde volver, o sea para no reescribir el cuerpo de los metodos.

**El `catch` ya no distingue el tipo.** Antes cazaba `SocketTimeoutException`,
que es de `java.net`; cada motor de Ktor lanza la suya. Se reintenta ante
cualquier fallo de red, que para el caso —tres intentos y a otra cosa— hace lo
mismo. Y `Thread.sleep(2000)` pasa a `delay(2000)`, que ademas no bloquea.

**EL CODIFICADOR DE URL SE ESCRIBIO A MANO, Y TIENE PRUEBA.** `URLEncoder` es de
la JVM, asi que hay un `paraUrl()` que deja pasar lo "sin reservar" de RFC 3986 y
escapa el resto sobre sus bytes UTF-8. Va **suelto y no dentro de `ComicVine`**
precisamente para poder probarlo, y `ParaUrlTest` cubre seis casos.

**Por que merecia prueba y otras cosas no**: si este codificador se equivoca **no
da ningun error**. Comic Vine responde 200 con cero resultados y la app se queda
sin datos como si la serie no existiera — el mismo fallo silencioso que ya costo
un dia con `filter=name:`. El caso que mas importa es el del espacio: **%20 y
nunca `+`**, por lo que ya estaba escrito en la seccion de Comic Vine.
De paso, `encQuery` y `enc` **ya son la misma funcion**: el apaño de
`.replace("+", "%20")` sobraba en cuanto el codificador dejo de ser el de
formularios.

**LO QUE NO SE HA COMPROBADO, y es lo importante de esta tanda.** Compila y las
pruebas pasan, pero **ninguna peticion real ha salido**: desde aqui no hay red a
Comic Vine, como ya estaba escrito. Lo que esta verificado es el codificador y
que todo compila. **Lo que NO**: que Ktor mande las cabeceras que Comic Vine
exige (responde 403 sin User-Agent propio), ni el camino de reintento, ni la
lectura de `status_code`. Se prueba desde Ajustes > "Probar conexion", que para
eso existe.

### Tanda 4 del port: los cuatro almacenes JSON (03/09/2026)

`Progreso`, `Marcadores`, `Sesiones` y `SeriesRemotas` pasan a `:shared`. Pedian
un `Context` solo para una cosa: **saber donde escribir**.

**Se resolvio con una interfaz, `Disco`, y no con un `expect class`.** Tres
metodos: `leer`, `escribir`, `borrar`, por nombre de fichero. `DiscoAndroid`
usa `filesDir` —**la misma carpeta de siempre, asi que lo que Dani ya tiene en el
movil se sigue leyendo igual, esto no migra nada**— y `DiscoIos` usara la carpeta
Documents. La alternativa era un objeto de plataforma con el `Context` en una
variable global inicializada al arrancar: un sitio mas donde algo puede estar a
null cuando no toca.

**Documents y NO Caches en iOS**: el sistema vacia Caches cuando le hace falta
espacio, y perder por donde ibas leyendo porque el iPad andaba justo de disco
seria un fallo imposible de reproducir.

**Y AQUI ESTA LO QUE DE VERDAD GANA ESTA TANDA, que no era portar.** Esos cuatro
almacenes **no tenian ni una prueba** —necesitaban `Context`— y son justo el
sitio donde se pierden los datos. Con `Disco` detras de una interfaz basta un
disco de mentira en memoria: `AlmacenesTest` prueba **la vuelta entera** —
escribir, tirar la instancia y leer con otra nueva sobre el mismo disco.

La vuelta entera y no solo escribir, porque **la instancia viva responde bien
desde su cache aunque lo que haya escrito en el fichero sea basura**: un fallo de
serializacion no se ve de ninguna otra forma.

Lo que cubre: marca guardada y releida, olvidar, disco vacio, **un JSON roto que
se lee como vacio en vez de tirar la app**, marcapaginas, sesiones, y la ficha de
serie con su lista anidada y sus opcionales ausentes. Mas dos que valen por si
solas: **una tanda escribe UNA vez y sin tanda escribe una por marca** — que era
el arreglo del marcado en bloque y hasta hoy no tenia como comprobarse.

**Se llevo `LecturaTest` y `ProgresoTest`** con su codigo. En `app/src/test` solo
queda `ExportarTest`, que prueba algo de Android de verdad.

**org.json fuera.** Es de Android. La lectura ya tenia el puente de la tanda 3
—`optString`, `optInt`, `optJSONArray`, `optJSONObject`, ahora tambien `optLong`,
`optBoolean` y `has`— y para escribir se usa `buildJsonObject`/`buildJsonArray`
de kotlinx, que tienen casi la misma forma que `JSONObject().apply { put(...) }`.
Sin libreria propia: lo hace el estandar.

**Segunda vez con el mismo tropiezo del alcance de dependencia.** La
serializacion iba como `implementation` y `:shared` compilaba mientras `:app`
fallaba con "Cannot access class". Los almacenes **devuelven** `JsonObject` en
`exportar()`, asi que tiene que ser `api`. Es exactamente lo que ya paso con
kotlinx-datetime en la tanda 2: **si un tipo aparece en la firma publica, va con
`api`**.

**Una sola linea decide donde se guarda todo**: `private val disco = DiscoAndroid(ctx)`
en `VistaModelo`. En iOS sera `DiscoIos()` y no cambia nada mas.

**Y de cara a la Raspberry Pi que Dani quiere montar**: la interfaz tiene ya la
forma que haria falta —una `DiscoRemoto` que hable con la Pi entra sin tocar los
almacenes— pero **no se ha escrito nada para eso**. Los comics en red son otro
problema distinto y le toca a `Escaner`, no a esto.

Reparto tras la tanda: **17 ficheros en `app`, 22 en `shared`**; en pruebas, 1 y
14.

### Tanda 5 del port: el CI, y por que NO hay todavia un iosApp (03/09/2026)

**No se ha creado `iosApp/`, y es deliberado.** La interfaz sigue entera en
`app/` y pegada a Android, asi que un `.ipa` hoy no tendria nada que enseñar:
seria andamiaje que se tira entero en cuanto se porte la UI. Tampoco se declara
un `framework` en `:shared` por lo mismo — no hay nada que lo consuma. Las dos
cosas entran juntas en la tanda de la interfaz.

**Lo que si vale hoy es el CI**, y no por costumbre: es **la unica forma de
compilar `DiscoIos` y todo `commonMain` contra el compilador de verdad de
Apple**. Desde Windows no se puede, y lo que se escribio en la tanda 4 para iOS
no lo ha mirado ningun compilador todavia.

`.github/workflows/compilar.yml`, dos trabajos:

- **`android`, en `ubuntu-latest`**: `:app:assembleDebug`, las pruebas de los dos
  modulos, y sube el APK como artefacto. Va en Ubuntu **porque un runner de
  macOS cuesta diez veces mas minutos** y este trabajo coge las regresiones
  normales, que son casi todas.
- **`ios`, en `macos-latest`**: `:shared:iosSimulatorArm64Test` y
  `:shared:compileKotlinIosArm64`.

**Lo importante es la primera de esas dos tareas: no compila, EJECUTA las
pruebas comunes compiladas a nativo.** Pasarlas en la JVM no demuestra lo mismo:
las diferencias de `java.time`, de `String.format` y de todo lo que se toco en
las tandas 2 y 3 aparecen justo ahi. Y la segunda compila para arm64, que es otro
objetivo distinto del simulador.

**No hace falta ningun secret para que esto pase.** La clave de Comic Vine se lee
de `local.properties`, que git ignora; si no esta, `secreto()` devuelve "" y la
app compila igual y la pide por Ajustes. El secret solo hara falta el dia que se
quiera un instalable con la clave ya dentro. **Un blanco menos para arrancar.**

**Un fallo que se caza escribiendo y no probando**: la primera version del
workflow llamaba a `linkDebugFrameworkIosArm64`, y **esa tarea no existe** si el
modulo no declara un framework. Habria fallado en la primera ejecucion, en un
runner de pago. Se cambio por `compileKotlinIosArm64`, que si existe por defecto.

En `DiscoIos` se añadio `@OptIn(ExperimentalForeignApi::class)`: las llamadas de
Foundation llevan un puntero a `NSError` de ultimo parametro y eso es API
foranea, que Kotlin 2.0 no deja usar sin opt-in. **Escrito a ciegas: si sobra,
sale un aviso; si faltaba, el CI lo dira.**

Y `.kotlin/` a `.gitignore`, que Kotlin 2.0 deja ahi su cache de sesion.

**LO QUE ESTO NO DEMUESTRA.** El workflow esta escrito y **no se ha ejecutado
nunca**: el repositorio todavia no tiene remoto. Hasta que Dani no lo suba y
salga verde, `DiscoIos` sigue siendo codigo que nadie ha compilado.

### Los 706 ms eran una animacion (04/09/2026)

Volver del visor a la biblioteca tardaba ~720 ms clavados; el mismo trabajo
cambiando de pestaña, 11-22 ms. **Ninguna de las hipotesis era la buena**: ni
contencion con SAF, ni el hilo principal ocupado, ni las cuentas por subcarpeta.

**Lo resolvio partir el cronometro en dos.** Con la marca puesta al entrar en el
cuerpo del efecto, el rastro del movil lo dijo en tres lineas:

```
carpeta: «raíz»                    00:30:11.114
  (empieza a leer la carpeta)      00:30:11.820   ← 706 ms de espera
  leída: 2 carpetas, 0 cómics      00:30:11.829   ← 9 ms de trabajo
```

**Nueve milisegundos de leer detras de setecientos de esperar.** Y en el mismo
rastro, cambiando de pestaña, `(empieza a leer)` sale **en el mismo
milisegundo** que `carpeta:`. Mismo codigo, cero espera.

**LA CAUSA, Y ES UNA TRAMPA QUE VOLVERA A APARECER:**

> `LocalLifecycleOwner` dentro de un NavHost **NO es la Activity: es la entrada
> de la pila de navegacion de esa pantalla**. Una entrada que vuelve a estar
> arriba se queda en STARTED **durante toda la animacion de transicion** y solo
> llega a RESUMED cuando la animacion termina.

`PantallaCarpeta` releia la carpeta dentro de un `LaunchedEffect` guardado por
`enPrimerPlano()`, que exigia RESUMED. O sea que **la lectura estaba esperando a
que acabara una animacion**, y por eso el numero salia tan constante: no era
trabajo, era la duracion de la transicion.

Se explica ademas por que solo pasaba volviendo del visor y no cambiando de
pestaña: **las pestañas son un carrusel dentro de la MISMA entrada de
navegacion**, asi que ahi no hay transicion que esperar.

**El arreglo**: `enPrimerPlano(minimo: Lifecycle.State = RESUMED)`, y
`PantallaCarpeta` pide STARTED. Va con parametro y **no cambiando el significado
para todos**: los otros dos usos son para parar animaciones, y ahi RESUMED es lo
correcto — mientras se transiciona no hace falta que nada se mueva.

De paso, el observador **mira el estado en vez de acumular eventos**
(`currentState.isAtLeast(minimo)`), asi vale igual para RESUMED que para STARTED
sin mantener dos listas de eventos que se pueden desincronizar.

**LA LECCION, que es de metodo y no de Android**: se sospecho de SAF, del hilo
principal y de las cuentas por subcarpeta, y **las tres eran mentira**. Lo unico
que sirvio fue **partir el intervalo en dos y medir cada mitad**. Cuando un
numero sale sospechosamente constante —700, 706, 719, 725— no es trabajo: es una
espera, y las esperas tienen una duracion fija que alguien decidio.

**CONFIRMADO EN EL MOVIL, en el mismo rastro que trae los dos builds.** Con el
arreglo puesto, volviendo del visor:

```
carpeta: «raíz»                    00:34:04.834
  (empieza a leer la carpeta)      00:34:04.834   ← el MISMO milisegundo
  leída: 2 carpetas, 0 cómics      00:34:04.842   ← 8 ms en total
```

**719 ms → 8 ms**, y los cambios de pestaña siguen igual (0-13 ms), o sea que no
se ha estropeado el camino que ya iba bien.

La marca `(empieza a leer la carpeta)` se quita: ya dijo lo que tenia que decir.
**Se queda la linea `LENTA` de `Escaner.abrir`**, que en toda esta caceria
**nunca llego a saltar** —leer siempre estuvo por debajo de sus 200 ms— y por eso
mismo vale la pena dejarla: solo habla cuando algo va mal de verdad.

### El CI encontro lo que la JVM se tragaba (04/09/2026)

**El trabajo de Android salio VERDE**: compila y pasan todas las pruebas en una
maquina limpia. Eso ya es verificacion de verdad y no "en mi ordenador va".

**El de iOS saco tres fallos reales**, y los tres son de la misma familia:
**codigo que la JVM acepta y Kotlin/Native no**. Ninguno lo podia coger nada de
lo que hay aqui —ni compilar, ni `comprobar.py`, ni las pruebas— porque en la JVM
son correctos.

- **`toSortedSet()` no existe en Kotlin/Native.** Devuelve un
  `java.util.SortedSet`. Estaba en `Huecos.de` y en `EstadoSerie.resumen`, o sea
  en **dos de las funciones mas probadas del proyecto**. Cambiado por
  `distinct().sorted()`, que hace lo mismo —quita repetidos y ordena— y devuelve
  una lista.
- **`android.net.Uri.decode` en `Progreso.clave`.** Se colo en la mudanza de la
  tanda 4: el fichero se movio a `commonMain` y esa linea siguio ahi, compilando
  tan feliz contra el SDK de Android.

**Y esa tercera importa mas de lo que parece.** `clave()` es **la clave estable
con la que la copia de seguridad reencuentra tus comics al restaurar**: las uris
de SAF llegan codificadas, y "Green%20Lantern%2001.cbz" tiene que volver a ser
"Green Lantern 01.cbz" o la copia no casa con nada. Se escribio `desdeUrl()` al
lado de `paraUrl()` en `red/ParaUrl.kt`, **con pruebas de ida y vuelta**
—`desdeUrl(paraUrl(x)) == x` sobre ocho cadenas reales— mas los bordes: el `+`
que NO es espacio (igual que `Uri.decode` y al reves que `URLDecoder`) y el %XX
mal formado, que se deja tal cual en vez de lanzar: **un nombre raro no puede
tirar la restauracion entera**.

**LA LECCION, y es la que justifica el CI entero:**

> Un modulo `commonMain` compilado SOLO para Android **no es codigo comun**: es
> codigo de Android en una carpeta con otro nombre. El compilador de la JVM
> acepta `toSortedSet`, `java.time`, `String.format`, `org.json` y
> `android.net.Uri` sin rechistar. **Lo unico que dice la verdad es compilar para
> Kotlin/Native**, y eso solo pasa en el runner de macOS.

Dicho de otra forma: durante cinco tandas se movio codigo a `:shared` **creyendo**
que era portable, y tres cosas no lo eran. Se descubrieron en la primera
ejecucion que llego a compilar. Cada tanda que se mueva codigo nuevo, el CI es el
que dice si de verdad se movio.

### Segunda vuelta del CI: `commonMain` YA compila para iOS (04/09/2026)

Arreglados `toSortedSet` y `Uri.decode`, la siguiente ejecucion fallo en
**`compileTestKotlinIosSimulatorArm64`**, y eso es una noticia buena escondida en
un log rojo: **si esta compilando las PRUEBAS es que `commonMain` e `iosMain` ya
compilaron**. O sea que **`DiscoIos` pasa**: las firmas de Foundation escritas de
memoria —`stringWithContentsOfFile`, `writeToFile`,
`NSSearchPathForDirectoriesInDomains`— y el `@OptIn(ExperimentalForeignApi)`
puesto a ciegas estaban bien.

Lo que quedaba, dos cosas mas de la misma familia:

- **Los nombres de funcion entre acentos graves no admiten `,` ni `%` en
  Kotlin/Native**, porque acaban siendo simbolos de Objective-C:
  `Name contains illegal characters: ","`. Once nombres de prueba tenian coma y
  uno tenia un `%20`. Se quitaron las comas y el `%20` se dice con palabras.
  **En la JVM son perfectamente validos**, asi que esto no lo coge nada de aqui.
- **`String.format` otra vez**, ahora en `EstadoSerieTest`. Mismo cambio que en
  `Calendario.clave`: `padStart`.

**Es la tercera capa de fallos y todas de lo mismo**: la JVM acepta cosas que
Kotlin/Native no. Fue por capas porque cada arreglo dejaba pasar al compilador un
poco mas adentro: configuracion de Gradle → `commonMain` → `iosMain` →
`commonTest`.

### LOS DOS TRABAJOS EN VERDE (04/09/2026)

```
Android y pruebas comunes                                     success
iOS (compila y CORRE las pruebas comunes en Kotlin/Native)    success

> Task :shared:linkDebugTestIosSimulatorArm64
> Task :shared:iosSimulatorArm64Test        ← EJECUTADAS, no solo compiladas
> Task :shared:compileKotlinIosArm64        ← y compila para el iPad de verdad
```

**Que queda demostrado a dia de hoy, y que no.**

DEMOSTRADO:

- Las **29 pruebas de `commonTest` pasan compiladas a NATIVO**, no en la JVM. Las
  fechas con kotlinx-datetime, el codificador de URL con su ida y vuelta,
  `padStart`, `elegirVolumen` y los cuatro almacenes con su disco de mentira: todo
  se comporta igual fuera de la maquina virtual.
- **`commonMain` es de verdad comun.** Ya no es "codigo de Android en una carpeta
  con otro nombre": lo compila el compilador de Apple.
- **`DiscoIos` compila** para el simulador y para arm64.
- El modulo enlaza para el **iPad de verdad** (`compileKotlinIosArm64`), que es
  otro objetivo distinto del simulador.

NO demostrado, y conviene no confundirlo:

- **`DiscoIos` no se ha EJECUTADO nunca.** Compila, y nadie ha leido ni escrito un
  fichero con el. Las pruebas de los almacenes corren con `DiscoEnMemoria`.
- **No hay app de iOS.** No hay `.ipa`, no hay interfaz portada, no hay nada que
  instalar en el iPad. Lo que hay es la logica compartida, verificada.
- **Ni una peticion real a Comic Vine** ha salido desde el codigo nuevo de Ktor.

Cinco capas de fallos hasta el verde —permisos de `gradlew`, `iosMain` no
encontrado, `toSortedSet`, `Uri.decode`, nombres con coma— y **ninguna se podia
coger desde Windows**. Esa es la factura de la tanda 5 y esa es su razon de ser.

### Tanda 6a: entra Compose Multiplatform, y con el `Tema` (04/09/2026)

Primer paso del port de la interfaz, y **a proposito el mas pequeño que la puede
tumbar**: meter el plugin de Compose Multiplatform en `:shared` y mover **un solo
fichero**. Alinear `org.jetbrains.compose` 1.7.3 con Kotlin 2.0.21, AGP 8.7.2 y
el Compose que ya usa `:app` por su BOM es lo que podia romper; probarlo con
`Tema.kt` cuesta una tarde, descubrirlo con 3.834 lineas movidas cuesta la tanda
entera.

Salio a la primera: `compose.runtime`, `compose.foundation`, `compose.material3`
y `compose.ui` en `commonMain`, y en Android se resuelven a los mismos
artefactos de androidx que ya habia.

**`Tema.kt` no era portable del todo, y el culpable era el color.** Usaba
`ColorPortada.oscurecer`, que por dentro llamaba a
`android.graphics.Color.colorToHSV` y `HSVToColor`. Asi que hubo que escribir las
conversiones RGB↔HSV a mano, en `ui/Colores.kt`.

**Y esa es la parte con pruebas, `ColoresTest`.** Son conversiones con casos de
borde que **si se tuercen no dan ningun error**: la pantalla se tiñe de un color
raro y nadie sabe por que. Lo que cubre:

- Ida y vuelta de los siete colores puros.
- **El rojo, que esta en el tono 0 y es donde el circulo da la vuelta**: si el
  calculo se sale por negativo, sale magenta en vez de rojo.
- Verde en 120 y azul en 240.
- **Los grises no tienen tono**, que es el caso que `oscurecer` mira para no
  inventarle color a una portada en blanco y negro.
- Y el que justifica que la funcion exista: **un verde y un rojo oscurecidos
  siguen distinguiendose**. Mezclar contra negro los volvia el mismo gris, que
  fue el primer intento y se quedo invisible en el movil.

**Ahora hay UNA sola aritmetica de color.** `ColorPortada` se queda en `:app`
—necesita `Bitmap`— pero ya no tiene la suya: llama a la de `:shared`. Antes
habia dos sitios donde tocar lo mismo.

Reparto: `Tema.kt` (243 lineas) y `Colores.kt` fuera de `app`. Quedan
`Pantallas.kt` (2.151), `Lector.kt` (978) y `Componentes.kt` (705), que son las
que tocan `Bitmap`, `Uri` y las teclas de volumen.

### Tanda 6b: `Componentes.kt` a comun, partido por su costura (04/09/2026)

Cuatro anclas a Android en 705 lineas. Tres se resolvieron; la cuarta **no se
toco a proposito**, y esa es la decision que merece estar escrita.

- **`java.util.Calendar`** en `saludo()` → `Clock.System.now().toLocalDateTime(Novedades.ZONA).hour`.
  De paso deja de usar la zona del sistema y usa la de la app, como todo lo demas
  que decide fechas aqui.
- **`Settings.Global.ANIMATOR_DURATION_SCALE`** en `hayAnimaciones()` →
  `expect/actual`. En Apple el equivalente es **Reducir movimiento**
  (`UIAccessibilityIsReduceMotionEnabled`), que va al reves —dice si hay que
  REDUCIR— asi que se niega.
- **`LocalLifecycleOwner`** en `enPrimerPlano()` → **no hizo falta expect/actual**.
  Bastaba la version multiplataforma de JetBrains,
  `org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose`. Era la duda que
  mas preocupaba porque arrastraba el arreglo de los 706 ms a las dos
  plataformas, y se quedo en una linea de dependencia.

**Y AL SUBIRLO, EL CI CANTO DOS IMPORTS HUERFANOS** que se quedaron al sacar
`Portada`: `android.graphics.Bitmap` y `asImageBitmap`, ya sin usar. Compilaban
sin rechistar para Android —el target de Android de `:shared` tiene el SDK en el
classpath— y solo reventaron en macOS. **De ahi salio la guarda de
`comprobar.py`.**

**Y LA CUARTA: `Portada` SE QUEDA EN ANDROID.** Es la unica funcion del fichero
que habla de `Bitmap`, y llevarsela obligaba a cambiar el tipo a `ImageBitmap` en
la misma tanda, y con el:

- `Miniaturas`, que cachea `Bitmap` y **mide la cache por `byteCount`**, que
  `ImageBitmap` no tiene
- `ColorPortada`, que necesita un `Bitmap` de verdad para sacar el color dominante
- y los **trece sitios** que llaman a `Portada`

Asi que el fichero se partio por ahi: `Portada` a `ui/PortadaAndroid.kt` (44
lineas) y el resto a comun. **Partir por donde estaba la dependencia sale mas
barato que arrastrarla**, y la tuberia de imagenes se porta cuando le toque, sola
y con su cache, en vez de de refilon dentro de otra tanda.

Reparto: **16 ficheros en `app`, 29 en `shared`**. Y ya hay simetria en las
carpetas de plataforma: `DiscoAndroid`/`DiscoIOS` y
`AnimacionesAndroid`/`AnimacionesIOS`.

### EL MAPA DE LO QUE FALTA PARA EL IPAD (04/09/2026)

Escrito **cuando el analisis estaba fresco**, que es lo caro de rehacer. Al
terminar la tanda 6b el reparto es: **29 ficheros en `:shared`, 16 en `:app`**, y
lo que queda no es "mover mas ficheros": es escribir para iOS cosas que ahora
mismo solo existen en Android.

**EL ORDEN IMPORTA, Y NO ES EL QUE APETECE.** Apetece portar pantallas porque se
ven; pero **la interfaz es lo ultimo que importa si la app no puede abrir un
comic**. Hoy en iOS hay logica verificada y **cero acceso a ficheros**.

**1. La tuberia de imagenes.** Hoy `Bitmap` esta en `ComicZip`, `Miniaturas`,
`ColorPortada` y `Portada`. En la frontera comun tiene que ser `ImageBitmap`, que
es de Compose y ya es multiplataforma. Ojo con dos cosas que ya se saben:
`Miniaturas` **mide su cache por `byteCount`**, que `ImageBitmap` no tiene (se
calcula con ancho × alto × 2 en RGB_565), y `ColorPortada.dominante` necesita un
`Bitmap` de verdad, asi que se queda en Android detras de la frontera.

**2. Leer un CBZ sin `java.util.zip`.** `ComicZip` usa `ZipInputStream`,
`BitmapFactory`, `LruCache` y `contentResolver`: siete anclas. **Lo mas probable
NO es portarlo, sino ponerle una interfaz delante** —como se hizo con `Disco` y
con `FuenteComics`, que funcionaron las dos veces— y escribir la implementacion
de iOS aparte. Esa interfaz **no se ha creado todavia a proposito**: solo la
usaria el `Lector` comun, que aun no existe, y seria andamiaje.

**3. `Escaner` en iOS.** No hay SAF. Hay `UIDocumentPicker` y marcadores con
permiso (*security-scoped bookmarks*), que ademas hay que **guardar y volver a
resolver** en cada arranque. Es la pieza mas distinta de todo el port.

**4. `Lector.kt` (978 lineas), cinco anclas.** Pantalla completa y
`KEEP_SCREEN_ON` (en iOS, `idleTimerDisabled` mas ocultar la barra); orientacion
apaisada (facil: `BoxWithConstraints` y comparar ancho con alto); compartir
pagina (`Intent.createChooser` → `UIActivityViewController`); **las teclas de
volumen, que en iOS sencillamente no existen como concepto**; y la pagina, que es
un `Bitmap`. **El visor ES la tuberia de imagenes**: aqui no hay costura por la
que partir, como si la hubo en `Componentes`.

**5. Las cuatro pantallas** (2.151 lineas, hoy repartidas en `PantallaBiblioteca`,
`PantallaEstadisticas`, `PantallaAjustes` y `PantallaMarcadores`). Lo mas grande
y lo menos acoplado de los dos. Va despues porque depende de `Portada`.

**EL APARATO ES UN IPAD AIR DE 4a GENERACION, CHIP A14**, y no da igual:

- **arm64.** El CI ya compila `iosArm64` y prueba en `iosSimulatorArm64`, asi que
  esa parte esta cubierta. No hace falta x86.
- **CUATRO GIGAS DE RAM, Y ESTO ES LO QUE MAS VA A DOLER.** Un lector de comics
  descomprime imagenes grandes, y **iOS mata la app sin avisar** cuando se pasa
  de su cuota (jetsam); no hay `OutOfMemoryError` que atrapar como en Android.
  Ademas el techo de la cache de miniaturas se calcula hoy con
  `Runtime.getRuntime().maxMemory() / 8`, que **no existe fuera de la JVM**: en
  iOS habra que poner un numero fijo y prudente. Es un `expect/actual` que ya se
  sabe que hara falta.
- **La pantalla es de tableta, no de movil grande.** 10,9 pulgadas, unos
  1180 × 820 puntos.
  `Pantallas.kt` esta pensada para una columna de movil: en 11 pulgadas la
  rejilla de portadas tiene que dar mas columnas o las cartas salen enormes.
  **No es un `expect/actual`, es diseño**: se resuelve con `BoxWithConstraints` y
  midiendo el ancho, que ademas arregla de paso el movil apaisado.
- **La doble pagina deja de ser la excepcion y pasa a ser lo normal.** Hoy se
  activa al girar el movil; en un iPad apaisado, dos paginas lado a lado es como
  se lee de verdad un comic. La logica ya existe, lo que cambia es cuando entra.
- **Las teclas de volumen no tienen sentido aqui.** En un movil pasan pagina; en
  un iPad ni se usan asi. No es que "no se puedan portar": es que **no hay que
  portarlas**, y su hueco lo cubren las zonas tactiles que ya estan.
- **Sin barra de navegacion por gestos que estorbe**, pero si con la barra de
  estado: ocultarla en el visor es lo mismo que se hace en Android, con
  `idleTimerDisabled` para que no se apague la pantalla leyendo.

**6. `iosApp/` de verdad**: proyecto de Xcode, `binaries.framework` en
`:shared`, unas veinte lineas de Swift para el `@main`, y el workflow que genera
el `.ipa`. Y ahi si haran falta el secret de Comic Vine y la firma.

**LO QUE HAY QUE RECORDAR DE ESTA NOCHE**, aunque este arriba con mas detalle:

- Un `commonMain` compilado solo para Android **no es codigo comun**. Cinco capas
  de fallos lo demostraron y **ninguna se podia coger desde Windows**.
  **`comprobar.py` ya vigila esto**: mira los imports de `commonMain` y
  `commonTest` y canta si aparece `android.`, `java.`, `javax.` u `org.json.`. Se
  añadio tras la TERCERA vez que pasaba, y coge en un segundo lo que el CI tarda
  cinco minutos y otra maquina en decir. Lo que se cuela por nombre completo
  —`java.util.Calendar.getInstance()`— sigue siendo cosa del CI.
- Si un tipo sale en la firma publica de `:shared`, la dependencia va con **`api`**
  y no con `implementation`. Se tropezo dos veces con lo mismo.
- **Partir un fichero por donde esta la dependencia sale mas barato que
  arrastrarla.** `Portada` fuera de `Componentes` costo 44 lineas; llevarsela
  habria costado `Miniaturas`, `ColorPortada` y trece llamadas.

### Tanda 7: la tuberia de imagenes, y `Portada` ya es comun (04/09/2026)

El paso 1 del mapa. Salio **mas pequeño de lo que decia el propio mapa**, y por
un motivo que solo se vio al mirar: los **trece** sitios que llaman a `Portada`
pasan todos por **dos** metodos, `vm.portada()` y `vm.portadaYa()`. No habia
trece cambios, habia dos.

**`Miniaturas` cachea `ImageBitmap` y no `Bitmap`.** Es el tipo que entiende
Compose en las dos plataformas, y asi la conversion se hace **una vez al
decodificar** en vez de en cada repintado de cada carta de la rejilla — que era
justo lo que el `remember(b) { b.asImageBitmap() }` de `Portada` estaba
esquivando. Ese `remember` ya no hace falta y se fue.

La cache se mide a mano, `ancho × alto × 2`, porque `ImageBitmap` no tiene
`byteCount` y se decodifica en RGB_565. Era una de las dos pegas apuntadas en el
mapa.

**Y la otra pega se resolvio al reves de lo previsto.** El mapa decia que
`ColorPortada.dominante` "necesita un `Bitmap` de verdad y se queda en Android".
Al mirarlo, resulto que **pasarlo a `ImageBitmap` QUITA codigo**: se van
`Bitmap.createScaledBitmap`, `getPixels`, `colorToHSV` y el `recycle`. En su
lugar, `toPixelMap()` —que es de Compose y vale en las dos plataformas— y
**muestrear con salto en vez de reescalar**, que para lo que se busca aqui (que
casilla de tono pesa mas) hace lo mismo y ademas se ahorra crear y reciclar un
bitmap por portada.

**CONFIRMADO EN EL MOVIL (04/09/2026)**: Dani probo la app con las tandas 7 a 11
puestas y dijo "de Android funciona todo". Los degradados de las portadas siguen
pareciendose a la portada, que era lo unico que habia que mirar aqui. Se deja
escrito lo de abajo porque explica QUE se cambio y por donde volver si algun dia
canta un color.

Reescalar a 40×40
promedia los pixeles vecinos; muestrear con salto los ignora. Los colores que
salgan **pueden no ser exactamente los mismos**. La funcion es visual, asi que se
comprueba mirando la app, no con una prueba: **hay que abrir la biblioteca y ver
si los degradados siguen pareciendose a la portada**. Si alguno canta, el arreglo
es promediar un bloque pequeño alrededor de cada muestra en vez de coger el pixel
suelto.

Reparto: **15 ficheros en `app`, 30 en `shared`**. `Portada.kt` en comun deja
`Pantallas.kt` sin su ultima atadura de imagen.

### `Pantallas.kt` NO se puede mover tal cual (04/09/2026)

Analisis hecho, sin tocar codigo. Es distinto de lo que paso con `Componentes`:
alli habia **una** costura (`Portada`) y el resto era Compose puro. Aqui hay
cuatro ataduras, y no son de esquina:

| Linea | Que | Salida |
|---|---|---|
| 174, 197 | `LocalContext` para `Rastro` y `ColorPortada` | Facil: pasarles el `Disco` que ya existe |
| 1087-1115 | Permiso de notificaciones: `Manifest`, `Build.VERSION`, `ContextCompat`, launcher | Puro Android; iOS pide permisos de otra forma |
| 1350 | `BackHandler` | **En iOS no hay boton atras.** No hay equivalente comun |
| 1796-1808 | Elegir fichero y carpeta con SAF, en Ajustes | Puro Android; en iOS es `UIDocumentPicker` |

El fichero contiene **la pantalla de Ajustes entera** —la mas pegada a Android de
toda la app— mezclada con Biblioteca, Lecturas y Marcapaginas, que son casi puro
Compose.

**LO SIGUIENTE ES PARTIR EL FICHERO POR PANTALLAS, y no como paso previo del
port**: 2.151 lineas en un fichero son dificiles de tocar de todas formas, con
iPad o sin el. Es una tanda mecanica y verificable —compila o no compila— que
deja cuatro ficheros donde hay uno.

**HECHO el 04/09/2026**, ver "Tanda 8" mas abajo. Las lineas de la tabla de aqui
arriba son las del fichero viejo; los sitios nuevos estan alli.

Despues, cada pantalla se porta o se queda segun lo que lleve dentro. **Ajustes
probablemente se queda en Android mucho tiempo**, y no pasa nada: es la pantalla
que menos falta hace en el iPad.

### Tanda 8: `Pantallas.kt` partido en cuatro (04/09/2026)

2.158 lineas en un fichero, ahora **cuatro ficheros y una pantalla en cada uno**:

| Fichero | Lineas | Que lleva |
|---|---|---|
| `ui/PantallaBiblioteca.kt` | 1.274 | `PantallaCarpeta`, el buscador, `TiraSerie` (la ficha de serie), `TarjetaComic`, `MenuComic` y sus 15 auxiliares |
| `ui/PantallaEstadisticas.kt` | 498 | la pestaña de Lecturas: cifras, `CalendarioMes`, `DetalleDelDia`, la agenda y las series seguidas |
| `ui/PantallaAjustes.kt` | 350 | Ajustes entera |
| `ui/PantallaMarcadores.kt` | 92 | los marcapaginas |

**MOVER Y NADA MAS: ni una linea de codigo cambia.** Se comprobo sin fiarse del
ojo — se ordenan las lineas del fichero viejo y las de los cuatro nuevos, se
quitan imports, `package` y lineas en blanco, y se comparan. **La unica
diferencia son los dos separadores `═══ BIBLIOTECA ═══` / `═══ EL TODO ═══`, que
sobran cuando el nombre del fichero ya lo dice, y el comentario de cabecera que
cada fichero nuevo estrena.** El resto es identico caracter por caracter.

**Por que salio limpio, y era lo que habia que mirar antes de cortar.** En este
proyecto `private` a nivel de fichero es de FICHERO: si dos pantallas
compartieran un auxiliar privado, partir las obliga a subirlo a `internal` o a
duplicarlo. Se conto el uso de las 24 declaraciones privadas **antes de mover
nada**, y **cada una la usa una sola pantalla**. Por eso el corte es mecanico:
no hubo que cambiar la visibilidad de nada.

`MenuComic` (publica) se va con Biblioteca, que es de donde se abre.

**El unico tropiezo fue un import**, `androidx.compose.ui.graphics.Color`, que se
quedo fuera de la cabecera de Biblioteca al repartirlos. Lo canto el compilador
en el primer intento; cinco errores en cuatro sitios y una linea de arreglo.

Verificado: `comprobar.py` con **PROBLEMAS: 0**, `:app:assembleDebug` en verde,
`compileDebugKotlin` **sin un solo `w:`**, y las pruebas de los dos modulos
relanzadas con `--rerun-tasks`, en verde. **`MainActivity` no se toco**: las
cuatro pantallas siguen siendo publicas y en el mismo paquete `com.dani.lector.ui`.

**Las anclas a Android de la tabla de arriba, con su sitio nuevo**: `LocalContext`
en `PantallaBiblioteca.kt:167` y `:190`; el permiso de notificaciones en
`PantallaBiblioteca.kt:1083-1108`; `BackHandler` en `PantallaEstadisticas.kt:78`;
y el SAF de Ajustes en `PantallaAjustes.kt:44-56`. **El reparto para el port no
cambia**, solo se lee mejor: Marcadores y Lecturas son casi Compose puro,
Biblioteca lleva una atadura (el permiso) y Ajustes es la que se queda en
Android.

### Tanda 9: `Recorte` decide en comun y Android solo corta (04/09/2026)

`Recorte` quita el marco liso de una pagina escaneada. Eran 102 lineas en `:app`
y ahora son dos piezas:

| Donde | Que |
|---|---|
| `shared/.../datos/Recorte.kt` | **la decision**: `Recorte.util` devuelve un `Recuadro` con el trozo que tiene dibujo, o null |
| `app/.../datos/RecorteAndroid.kt` | **el corte**: leer pixeles con `getPixels` y `Bitmap.createBitmap` |

**Se partio por donde estaba la dependencia**, que es la leccion que ya costo
`Portada` en la tanda 6b. Lo unico de Android eran `Bitmap`, `Rect` y las dos
llamadas de pixeles; las cuatro reglas que deciden el margen son aritmetica.

**Los pixeles entran por dos funciones y no como un array entero**, y no es
capricho: una pagina de 2000x3000 son 24 MB de ints, y el algoritmo solo mira
unas pocas filas y columnas de los bordes. `RecorteAndroid` pasa dos lambdas que
**reutilizan un unico buffer**, exactamente como hacia el codigo de antes, asi
que no hay ni una asignacion nueva por borde mirado. El algoritmo no se ha
tocado: mismas constantes, mismos bucles, mismo orden.

`Rect` se cambia por un `Recuadro` propio de cuatro enteros. **`der` y `abajo`
siguen siendo excluyentes**, igual que en `android.graphics.Rect`, para que
quien corta pase `ancho`/`alto` sin sumar ni restar nada — que es justo donde se
cuela un fallo de un pixel que nadie ve.

**Y AQUI ESTA LO QUE DE VERDAD GANA LA TANDA, que otra vez no era portar.**
`Recorte` **no tenia ni una prueba** y tiene cuatro reglas con casos de borde
que, cuando se tuercen, **no dan ningun error**: la pagina sale recortada de mas
o de menos y solo se ve mirando el movil pagina por pagina. Ahora hay
`RecorteTest`, ocho casos sobre paginas de mentira —un `IntArray` y dos
funciones que devuelven filas y columnas, que es justo lo que pide `util`:

- El marco blanco se recorta, con sus dos pixeles de gracia.
- **El marco negro tambien**, que es el caso que justifica que el fondo se tome
  de la esquina en vez de suponerlo blanco.
- Una pagina cuyo dibujo llega a los cuatro bordes se devuelve **sin tocar**.
- Una pagina lisa entera se descarta por la regla del 40%.
- Una pagina de menos de 60 pixeles ni se mira.
- **Los dos lados del limite de ruido**: dos pixeles sueltos en una fila de 100
  son el 2% justo y el corte sigue; tres ya paran el corte en esa fila. Sin esa
  tolerancia, una mota del escaner impide recortar la pagina entera.
- Y que no se busca mas alla de la mitad de la pagina, que es lo que salva a una
  splash page clara de comerse su propio dibujo.

Verificado: `comprobar.py` con **PROBLEMAS: 0**, `:app:assembleDebug` y las
pruebas de los dos modulos con `--rerun-tasks` en verde, y `RecorteTest` lanzada
**por separado** con `--tests` mas el control de una clase inexistente, que
falla con "No tests found" — o sea que las ocho se ejecutan de verdad y no es
que no fallen.

**COMPROBADO EN EL MOVIL (04/09/2026)**: entra en el "de Android funciona todo"
de Dani. Queda escrito lo que se miraba, por si vuelve:
que una pagina de verdad se recorte igual que
antes. Es la misma aritmetica sobre los mismos pixeles y la prueba cubre las
reglas, pero **el camino Bitmap -> lambdas -> Bitmap no lo ha recorrido ningun
comic**. Se ve en un segundo: abrir un CBZ con marco blanco y el ajuste de
recorte puesto.

**Y lo que esta tanda NO desbloquea**, para que no parezca mas de lo que es: las
pantallas siguen sin poder mudarse. Todas reciben `vm: VistaModelo`, que son
1.195 lineas de `AndroidViewModel` en `:app` con SharedPreferences, `Escaner`,
`Miniaturas` y `ComicZip` dentro. La cadena real es
`ComicZip`/`Escaner`/preferencias detras de interfaz -> `VistaModelo` -> las
pantallas, y ahi es donde esta el trabajo de verdad.

Reparto: **18 ficheros en `app`, 31 en `shared`**; en pruebas, 1 y 17.

### Tanda 10: la regla de que es una pagina, en un solo sitio (04/09/2026)

**La regla estaba duplicada letra por letra**, en `ComicZip:106` y en
`Rar5:375`. Y no por descuido de hoy: el 03/09 se unifico **la lista de
extensiones** en `ComicZip.EXT` y se dejo el *predicado* copiado en los dos
ficheros. O sea que se arreglo el sintoma que se veia —dos listas— y sobrevivio
la copia de verdad, con el agravante de que el comentario de `EXT` avisaba
exactamente de eso: "dos listas que hay que acordarse de cambiar a la vez acaban
no cambiandose a la vez".

Ahora las dos reglas viven en `shared/.../datos/Imagenes.kt`:

| | Que decide |
|---|---|
| `Imagenes.es(nombre)` | si una entrada del archivo es una pagina |
| `Imagenes.ordenadas(lista)` | en que orden se leen |
| `Imagenes.EXT` | la lista de extensiones |

`ComicZip` y `Rar5` las llaman las dos; **no queda ni una copia** (`grep esImagen`
sobre `app/src` y `shared/src` no devuelve nada). Y son decisiones, no
fontaneria: ni el nombre de una entrada ni su orden saben de Android, asi que
van a `:shared` por la regla de reparto de siempre.

**Y otra vez lo que gana la tanda son las pruebas, que no habia ninguna.** Las
dos reglas fallan calladas: el comic se abre igual, solo que por la pagina que
no era o con una pagina de mas. `ImagenesTest`, once casos, y los que importan
son estos cuatro:

- **`._portada.jpg` fuera.** Es un fichero de metadatos de macOS de dos
  kilobytes **que si tiene extension de imagen**, y sin la regla saldria como
  primera pagina del comic.
- **`__MACOSX/` fuera.** El compresor de macOS mete ahi una copia sombra de cada
  imagen: sin esto **cada pagina saldria dos veces**.
- **Una carpeta oculta no tira sus paginas** (`.extras/pagina01.jpg` SI entra):
  lo oculto es el nombre del fichero, no la ruta. Es el borde que separa las dos
  reglas de arriba de una que se pasaria de lista.
- **Primero por profundidad y luego por nombre.** Hay CBZ con las paginas
  sueltas en la raiz y ademas una subcarpeta de extras; ordenando solo por
  nombre, `extras/aaa.jpg` se cuela delante de `pagina01.jpg` y **abres el comic
  por los extras**. Y el nombre se compara en minusculas porque en orden de
  bytes todas las mayusculas van antes: `Page10` se colaria delante de `page02`.

Verificado: `comprobar.py` con **PROBLEMAS: 0**, `:app:assembleDebug` y las
pruebas de los dos modulos con `--rerun-tasks` en verde, y `ImagenesTest` lanzada
por separado con `--tests`.

**COMPROBADO EN EL MOVIL (04/09/2026)**, dentro del "de Android funciona todo":
que un CBZ de verdad liste sus paginas igual que
antes. El predicado y el comparador son los mismos caracteres movidos de sitio,
pero **por el camino nuevo no ha pasado ningun comic**. Se ve abriendo uno
cualquiera: si el orden fuera mal, se nota a la primera.

Reparto: **18 ficheros en `app`, 32 en `shared`**; en pruebas, 1 y 18.

### Tanda 11: las reglas que borran ficheros, por fin con pruebas (04/09/2026)

`ConversorCarpeta.limpiar` es **la unica funcion de la app que renombra y borra
ficheros de la biblioteca de Dani**. Tiene cuatro reglas con orden de prioridad,
dos desastres esquivados en su historial, y **no tenia ni una prueba**. Es
literalmente el caso que describe la regla del proyecto: "lo que decide algo va
en una funcion pura con su test al lado, sobre todo si son reglas con casos de
borde o con orden de prioridad, que se rompen sin dar ningun error".

Las tres reglas de nombre salen a `shared/.../datos/Limpieza.kt`:

| | Que decide |
|---|---|
| `sinDobleExtension` | "X.cbz.zip" -> "X.cbz" |
| `originalDe` | como se llamaria el original de una copia, o null |
| `aGrapa` | "Corps (1).cbz" -> "Corps #01.cbz", mirando la carpeta entera |

**`originalDe` devuelve un nombre y NO un veredicto, y eso es deliberado.** Un
"(21)" solo es una copia si "Corps.cbz" esta al lado; sin original, es el numero
de la grapa. Esa distincion no la puede tomar una funcion que solo ve un nombre,
asi que se queda donde esta la lista de la carpeta. **Si algun dia alguien hace
que decida sola, se carga la numeracion de una serie entera sin dar un error**, y
eso es exactamente lo que la primera version hacia: lo cazo Dani en una captura
antes de que llegara a ejecutarse. Hay una prueba con ese nombre puesto.

Lo que **NO se ha movido, a proposito**: la comparacion de paginas. Decidir si
dos ficheros son el mismo comic exige contarles las paginas, y eso es leer disco.
Se queda en `ConversorCarpeta`, que es quien puede. **La regla de Blackest Night
—no borrar nada sin haber contado los dos— no se ha tocado ni un caracter.**

**Catorce casos en `LimpiezaTest`**, y los que valen la tanda:

- **"Green Lantern Corps (21).cbz"**, con su nombre puesto en el test.
- **"Batman (2016).cbz"**, el año. Y **los dos bordes del rango**: 1929 y 2101
  vuelven a ser numeros de grapa. Ese rango era un `if (n in 1930..2100)` suelto
  en mitad de un `mapNotNull`, sin nada que dijera que pasa justo al lado.
- **El relleno lo decide el mayor de la carpeta**: con numeros hasta el 17 basta
  "#01", pero si la serie llega a 120 hace falta "#001" o el orden se rompe otra
  vez en el 100. Es un `if (mayor >= 100) 3 else 2` del que dependia toda la
  numeracion y que nadie habia comprobado.
- **Si el nombre nuevo ya existe, se marca y no se renombra.** Sin eso, arreglar
  la numeracion pisa un fichero.

**Un cambio de forma, minimo, en el paso 4**: antes se recorrian los `Comic` y se
calculaba el plan por el camino; ahora `Limpieza.aGrapa` da el plan y el bucle lo
ejecuta. **Se sigue iterando sobre los `Comic` y no sobre los nombres**, para no
cambiar que pasa si dos ficheros de una carpeta se llaman igual salvo por
mayusculas. El `porNombre` de ese bloque desaparece: quien mira los choques ahora
es `aGrapa`.

Verificado: `comprobar.py` con **PROBLEMAS: 0**, `:app:assembleDebug` sin un solo
`w:`, las pruebas de los dos modulos con `--rerun-tasks` en verde, y
`LimpiezaTest` lanzada por separado con `--tests`.

**LO QUE SIGUE SIN COMPROBAR, y aqui importa mas que de costumbre.** El
"de Android funciona todo" del 04/09/2026 cubre que la app funciona; **NO consta
que se haya pulsado el boton de limpiar sobre una carpeta de verdad**, que es
otra cosa. Se pregunto expresamente y no se contesto, asi que se deja como
pendiente en vez de darlo por bueno: la limpieza
**no se ha ejecutado sobre una carpeta de verdad**. Las reglas estan probadas una
a una; lo que no ha pasado por ningun fichero es el camino entero. Si se prueba
en el movil, **que sea sobre una carpeta de la que haya copia**.

Reparto: **18 ficheros en `app`, 33 en `shared`**; en pruebas, 1 y 19.

### Tanda 12: una pasada de acabado con `better-ui` (04/09/2026)

Dani instalo el plugin `interfaces` y pidio pasarle la skill `better-ui` a la
app. **El detalle esta en `docs/DISENO.md`, apartado 22**, que es donde toca;
aqui solo lo que hace falta saber desde el codigo.

Cuatro cambios, todos de tacto y ninguno de contenido: la pista del interruptor
pasa a `FormaPista` (formas concentricas: 5 + 3 = 8), la bola del interruptor se
desliza en vez de saltar, y lo que se pulsa se encoge a 0,96 —el boton, las
cartas de la rejilla y los dos juegos de chips—.

**Dos apuntes que valen desde el codigo:**

- **El orden de los modificadores ES el efecto.** Por eso `pulsable` lleva la
  forma y el fondo dentro en vez de dejarlos al llamador: la escala tiene que ir
  por FUERA del `clip`/`background` o encoge solo el contenido, y el `clickable`
  por DENTRO del `clip` o la onda se sale en cuadrado del chaflan.
- **Se descarto animar el color de fondo del chip**, que era lo que pedia la
  skill. El chip marcado va amarillo con el texto en negro: animando solo el
  fondo quedan ~75 ms de texto negro sobre panel oscuro, ilegible. O los dos o
  ninguno, y el texto no lo controla el modificador.

Y una regla de la skill que **se incumple a proposito**: prohibe los filos
tintados y `FiloColor` es amarillo al 40%. Lo que prohibe es un neutro con
tinte, no un color de marca puesto queriendo.

Verificado: `comprobar.py` con **PROBLEMAS: 0**, `:app:assembleDebug` sin un solo
`w:` y las pruebas en verde.

**LO QUE NO ESTA VERIFICADO, y en una tanda de aspecto es casi todo**: no se ha
visto. Desde aqui no hay movil ni Layout Inspector, asi que **las duraciones
estan leidas del codigo, no reproducidas**. Se mira en un minuto: Ajustes (la
bola debe deslizarse, y dandole dos veces rapido no debe teletransportarse) y
cualquier carta de la rejilla (debe encogerse bajo el dedo, y la pulsacion larga
seguir abriendo el menu).

### Tanda 13: los ajustes detras de una interfaz (04/09/2026)

Primer paso real hacia mover `VistaModelo`, que es **el tapon del port**: las
cuatro pantallas reciben `vm: VistaModelo`, asi que mientras esa clase siga en
`:app` no se puede mudar ninguna.

`VistaModelo` tenia cuatro ataduras a Android. Esta tanda quita una: los ajustes.

| | |
|---|---|
| `shared/.../datos/Preferencias.kt` | la interfaz: ocho metodos, texto/si/entero/largo |
| `shared/.../androidMain/.../PreferenciasAndroid.kt` | `SharedPreferences` |
| `shared/.../iosMain/.../PreferenciasIOS.kt` | `NSUserDefaults`, **escrito sin compilar** |

Misma jugada que `Disco` y que `FuenteComics`, que han funcionado las dos veces:
una interfaz pequeña y **una sola linea decide cual entra**
(`private val ajustes: Preferencias = PreferenciasAndroid(ctx)`).

**AQUI NO SE MIGRA NADA.** `PreferenciasAndroid` usa el **mismo fichero
(`"lector"`) y las mismas claves** que hasta hoy, asi que lo que Dani tiene
guardado en el movil —la carpeta elegida, sus interruptores del visor, su orden
de la biblioteca— se sigue leyendo igual. Diecisiete reemplazos, uno por acceso,
**cada uno conservando su valor por defecto literal**: `recortar` sigue siendo
`true`, `llenar` sigue siendo `false`.

**LA TRAMPA DE VERDAD ESTA EN iOS, y es la razon de que esta tanda merezca su
comentario largo.** `NSUserDefaults.boolForKey` de una clave que no existe
devuelve **false**, e `integerForKey` devuelve **0**: no hay forma de distinguir
"no guardado" de "guardado en false". Leyendolos a pelo, `recortar` y
`autoConvertir` —que van **encendidos** de serie— **apareceran apagados la
primera vez que se abra la app en el iPad**, y nadie sabria por que. Por eso las
tres lecturas miran `objectForKey` antes y, si la clave no esta, mandan el valor
por defecto. Es el mismo tipo de fallo silencioso que ya costo caro con
`String.format` y con `toSortedSet`.

**Sin dependencia nueva.** La primera version usaba `edit { }` de
`androidx.core:core-ktx`, que es dependencia de `:app` y no de `:shared`; lo
canto el compilador. Se hace con el `Editor` a pelo y `apply()`, que es
exactamente lo que ese azucar hace por dentro.

**Y se quito un `PreferenciasEnMemoria` que se habia escrito por simetria con
`DiscoEnMemoria`.** Aquel existe porque `AlmacenesTest` tira de el; este no lo
usaba nadie. Cuando una prueba lo necesite son diez lineas.

Verificado: `comprobar.py` con **PROBLEMAS: 0**, `:app:assembleDebug` sin un solo
`w:` y las pruebas en verde.

**LO QUE NO ESTA VERIFICADO, y es lo que hay que mirar en el movil**: que los
ajustes guardados sigan ahi. Si una clave se hubiera escrito mal, el ajuste
**volveria a su valor por defecto sin dar ningun error** — que es justo el fallo
que este proyecto persigue. Se comprueba abriendo Ajustes: la carpeta de la
biblioteca tiene que seguir elegida y los interruptores como se dejaron.
No hay prueba automatica posible: esto necesita `Context`.

**LO QUE FALTA PARA MOVER `VistaModelo`.** Aqui se escribio "cuatro ataduras" y
**estaba mal**: se conto de memoria en vez de contarlas. Contadas de verdad
(`grep` sobre el fichero) son **ocho objetos de Android y 24 llamadas**:

| | llamadas |
|---|---|
| `Rastro` | 5 |
| `Miniaturas` | 4 |
| ~~`Escaner`~~ | ~~4~~ **hecho, tanda 17** |
| ~~`ComicZip`~~ | ~~4~~ **hecho, tanda 16** |
| `ConversorCarpeta` | 3 |
| `Rar5` | 2 |
| `Vigilante` | 1 |
| `ColorPortada` | 1 |

Mas `AndroidViewModel(Application)` y las preferencias, que ya estan. **Es
bastante mas trabajo del que decia esta linea**, y conviene saberlo antes de
prometer un iPad para el mes que viene.

### Tanda 14: los tres avisos de Dani al probar la 12 (04/09/2026)

Probo el acabado en el movil y saco tres cosas. **El detalle esta en
`docs/DISENO.md`, apartado 23**; aqui lo que importa desde el codigo.

- **La escala al pulsar faltaba en el carrusel y en "tu recorrido".** La tanda 12
  toco `TarjetaComic` y nada mas. Es el fallo de arreglar el sitio que estabas
  mirando en vez de todos los que hacen lo mismo — y aqui **no habia forma de
  cogerlo compilando**: solo usandolo. Ahora los tres comparten
  `escalaAlPulsar`.
- **Vuelve el velo de los leidos**, que se habia quitado el 03/09 a proposito.
  Token `VELO_LEIDO` a 0,55, ni el 70% de antes ni nada. La chapa se queda y
  crece a 22 dp. **Es la segunda vez que esto cambia de bando**: los dos extremos
  ya se probaron y ninguno valia solo.
- **Tocar "Biblioteca" estando ya en ella sube a la raiz** (`pila.removeRange`).
  Viniendo de otra pestaña, el primer toque solo cambia de pestaña.

Verificado: `comprobar.py` con **PROBLEMAS: 0**, `:app:assembleDebug` sin un solo
`w:` y las pruebas en verde. **Sin ver en el movil**, como toda tanda de aspecto.

### El tiron del arranque: lo que el rastro descarta (04/09/2026)

Dani: *"al principio cuando entro en la app todo tarda y va con lag, porque
supongo que esta cargando las portadas"*. Pego el rastro. **La hipotesis sigue
sin probarse, pero el rastro descarta casi todo lo demas.**

**LO MEDIDO, siete arrancadas seguidas.** De "── la app arranca ──" a tener el
indice hecho:

| | ms |
|---|---|
| Arranque del proceso hasta `ON_CREATE` | 149-250 |
| `fichas precargadas` | 23-66 |
| Leer la carpeta raiz | 8-105 |
| `indice: 293 comics` | **194-670** |
| **Total hasta cargado del todo** | **391-956** |

**Nada de eso bloquea el hilo principal**: el precalentado, el indice y el
escaner corren en `Dispatchers.IO`. O sea que **el tiron que Dani nota no esta en
nada de lo que hoy se mide**.

**SE DESCARTO QUE EL INDICE SE RECONSTRUYA SOLO.** En el rastro del 03/09 aparece
tres veces en una sesion, que era la sospecha buena a primera vista. Se miro:
`tirarIndice()` solo lo llaman cuatro sitios y los cuatro los pide el usuario
(elegir carpeta, convertir, limpiar, repasar). Y **en las siete arrancadas
recientes el indice se construye exactamente UNA vez cada una**. Las tres de
aquel dia eran un build anterior y el boton de "repasar biblioteca". No hay
fuga.

**LO QUE SI SALIO, Y ERA DE LA TANDA 12.** `hayAnimaciones()` hace un
`Settings.Global.getFloat`, que es **IPC al proveedor de ajustes**, y su
`remember` solo lo evita dentro del composable que lo llama. Mientras lo llamaban
dos sitios daba igual. Desde que `escalaAlPulsar` lo usa **lo llama cada carta**:
en `Green Lantern Vol. 4`, que tiene 68 numeros, son **68 IPC en el hilo
principal justo mientras se compone la rejilla**. Ahora se recuerda una vez por
proceso.

Se cuenta entero porque es una leccion de las de este proyecto: **el coste no
estaba en lo que se añadio —una escala— sino en lo que ya habia y paso de dos
llamadas a sesenta y ocho.**

**LO QUE FALTA POR MEDIR, y es la hipotesis de Dani.** Las portadas eran **el
unico hueco del arranque sin cronometro**. Ahora `Miniaturas` apunta un resumen
cada 25: `portadas: N de cache (X ms), N generadas (Y ms)`. Distingue los dos
casos que importan — leerlas del disco es barato, sacarlas del comic es abrir un
fichero de decenas de megas.

**A priori no deberian dar tirones**: salen en `Dispatchers.IO` de tres en tres y
no tocan el hilo de la interfaz. Lo que si podrian hacer es **marear al
recolector de basura** descomprimiendo bitmaps sin parar, y eso si se nota en la
fluidez. Los dos numeros lo dicen: si el total es pequeño, no eran ellas y toca
mirar la composicion con el Layout Inspector, que es lo unico que queda.

### LAS PORTADAS NO ERAN, Y EL RASTRO LO DICE (04/09/2026)

Con el cronometro puesto, la sesion de las 16:25:

```
16:25:21.755  ── la app arranca ──
16:25:22.154    índice: 293 cómics en 200 ms
16:25:22.222    portadas: 1 de cache (3 ms), 0 generadas (0 ms)
16:25:22.317    portadas: 10 de cache (20 ms), 0 generadas (0 ms)
16:25:37.231    portadas: 20 de cache (49 ms), 0 generadas (0 ms)
16:25:38.587    portadas: 40 de cache (123 ms), 0 generadas (0 ms)
```

**Cuarenta portadas, todas de cache, 123 ms en total repartidos en 16 segundos.
Cero generadas.** O sea que ni una vez hubo que abrir un comic para sacar su
portada. **La sospecha de Dani era razonable y era falsa**, como las tres
anteriores de este proyecto. Y el arranque completo —indice hecho y las diez
primeras portadas puestas— son **562 ms**.

**LO QUE DEJA UNA SOLA VENTANA SIN EXPLICAR**, y llevaba ahi desde el principio:
entre `── la app arranca ──` y `ciclo: ON_CREATE` pasan **147-250 ms** en todos
los arranques. Ahi dentro solo habia dos llamadas, y una es cara:

> `WorkManager.getInstance()` **monta una base de datos Room la primera vez**, y
> se llamaba en `Application.onCreate`: **en el hilo principal y antes de que
> exista la Activity**. Todo lo que tarde se lo come el arranque entero.

Nada de eso hace falta para pintar la primera pantalla —el canal solo se usa al
notificar y el trabajo es DIARIO—, asi que se va a un hilo aparte. Va con `KEEP`,
asi que si el proceso muere antes de programarlo se programa en el siguiente
arranque y no se pierde nada.

**Y se cronometro, porque era la hipotesis y no la conclusion. SALIO FALSA:**

```
16:28:31.811  ── la app arranca ──
16:28:31.817    arranque: canal 3 ms, trabajo diario 2 ms
16:28:31.965  ciclo: ON_CREATE
```

**Cinco milisegundos los dos.** Y la ventana de 154 ms sigue igual de grande, o
sea que ahi dentro no hay nada nuestro. El motivo es que **WorkManager ya se ha
inicializado antes de llegar a `Application.onCreate`**, en su propio
ContentProvider de arranque: para cuando se le pide la instancia, el trabajo caro
ya esta hecho y no se ve desde aqui.

El hilo aparte **se ha devuelto a su sitio**: cinco milisegundos no pagan un hilo
suelto. El cronometro se queda, que cuesta dos restas y es lo unico que impide
volver a sospechar de esto.

### CINCO SOSPECHAS, CINCO FALSAS: se cambia de instrumento (04/09/2026)

SAF, el hilo principal, las cuentas por subcarpeta, las portadas y el trabajo
diario. **Las cinco descartadas con numeros.** Eso ya no es mala suerte, es un
fallo de metodo:

> **El rastro cuenta SUCESOS —cuanto tarda leer una carpeta, hacer el indice,
> sacar una portada— y un tiron NO es un suceso: es un fotograma que no llega a
> tiempo.** Se estaban midiendo indicios en vez de la queja.

Asi que se mide la queja. `datos/Fluidez.kt` engancha
`addOnFrameMetricsAvailableListener` y apunta `fluidez: N de 300 fotogramas por
encima de 32 ms, el peor N ms`. Solo habla cuando ha habido alguno malo: una
linea cada cinco segundos diciendo que todo va bien es lo que hace que nadie lea
el rastro el dia que pasa algo.

**No es un contador de FPS de los que se reenganchan al Choreographer.** Aquellos
fuerzan un fotograma en cada vsync, o sea que mantienen la app dibujando sin
parar —gastan bateria y **cambian justo lo que se quiere medir**—. Este solo
habla de fotogramas que el sistema ha pintado de verdad.

Con eso, la proxima vez el rastro dice **cuando** va a tirones y **cuanto**, y
recorriendo el rastro hacia arriba se ve que estaba pasando en ese momento. Si
los fotogramas lentos salen todos pegados al arranque, es carga de clases y toca
Baseline Profile; si salen al entrar en una carpeta grande, es composicion.

**Y EL CRONOMETRO NACIO MUDO, que es un error de metodo y va escrito para no
repetirlo.** Apuntaba cada 25 portadas. **La raiz de Dani tiene 2 carpetas y
ningun comic suelto**, asi que ahi solo se piden el banner de "seguir leyendo",
las tres de "tu recorrido" y lo visible de dos carruseles: unas quince o veinte.
Nunca llegaba a 25, asi que **el cronometro se callaba justo en la pantalla de la
que se estaba hablando** — y su silencio parecia un dato ("no cargan portadas")
cuando solo era un umbral mal puesto. Ahora apunta la primera y luego cada diez.

La leccion: **un instrumento que solo habla pasado un umbral hay que probarlo en
el caso pequeño**, que suele ser justo el que se esta investigando. La linea
`LENTA` de `Escaner` tiene la misma forma y por eso lleva escrito que en toda la
caceria de los 706 ms no llego a saltar ni una vez.

### EL TIRON ERA EL CARRUSEL DESTRUYENDO LA BIBLIOTECA (04/09/2026)

Con `Fluidez` puesto, el rastro dijo en cuatro lineas lo que cinco sospechas no
habian conseguido:

```
16:32:40.674    fluidez: 17 de 300 fotogramas por encima de 32 ms, el peor 172 ms
16:32:44.315    fluidez: 16 de 300, el peor 135 ms
16:32:48.531    fluidez:  9 de 300, el peor 111 ms
16:32:52.946    fluidez:  7 de 300, el peor 91 ms
```

**El tiron existe y es gordo**: 172 ms es perder diez fotogramas seguidos. Y va
bajando —17, 16, 9, 7—, o sea que es coste de PRIMERA VEZ.

**LA CAUSA ESTABA EN EL RASTRO DESDE EL PRIMER DIA Y NADIE LA HABIA LEIDO.** En
todas las sesiones, cada vuelta a la pestaña 0 apunta otra vez
`carpeta: «raíz»`. Esa linea sale de un `LaunchedEffect(docId)`, y `docId` **no
habia cambiado**. Un LaunchedEffect con la misma clave solo vuelve a dispararse
si el composable se ha recreado:

> **`HorizontalPager` DESTRUYE por defecto la pagina que no se ve.** Cada
> deslizamiento entre pestañas rehacia `PantallaCarpeta` entera: releer la
> carpeta, recomponer el banner, "tu recorrido" y los dos carruseles, y perder
> por el camino todos los `remember` —incluidas las portadas ya resueltas—.

Arreglado con `beyondViewportPageCount = 1`, que mantiene compuestas las vecinas.
Cuesta tener tres pantallas ligeras en memoria y ahorra rehacer una entera en
cada pasada.

**POR QUE SE TARDO TANTO, que es la leccion.** Las `carpeta: «raíz»` estaban en
el rastro desde el principio y se leyeron como ruido —"se relee la carpeta al
volver, son 15 ms, no es el tiron"—. Y era verdad que los 15 ms no eran el
tiron: **el tiron era lo que esa linea DELATABA**, que es una recomposicion
entera. Se estaba mirando el coste de la linea en vez de preguntar por que
aparecia.

Y hasta que no hubo un instrumento que midiera **fotogramas** en vez de sucesos,
no habia forma de saber si eso importaba o no. Cinco sospechas cayeron por medir
indicios; la buena salio a la primera con el instrumento correcto.

**CONFIRMADO EN EL MOVIL (04/09/2026), y de la forma mas limpia posible.** En la
sesion de las 16:35, catorce cambios de pestaña seguidos **sin una sola linea
`carpeta:`**:

```
16:35:46.011  pestaña: 1
16:35:46.519  pestaña: 2
16:35:47.309  pestaña: 0      ← antes aqui salia "carpeta: «raíz»"
16:35:47.876  pestaña: 1
16:35:48.982  pestaña: 0      ← y aqui
```

La pantalla ya no se recrea. Es un si o no, no una impresion.

**Y la fluidez, comparando ventanas equivalentes:**

| | Antes (16:32) | Despues (16:35) |
|---|---|---|
| Arranque | 17 lentos, peor 172 ms | 13 lentos, peor **260 ms** |
| Cambiando pestañas | **16** lentos, peor 135 ms | **4** lentos, peor 126 ms |
| Navegando carpetas | 9 lentos, peor 111 ms | 17 lentos, peor 120 ms |

**Los cambios de pestaña pasan de 16 a 4.** Lo que queda ya no es el pager y se
reparte en dos sitios distintos, los dos de "primera vez":

1. **Arranque en frio.** El peor fotograma de todo el rastro —260 ms— cae justo
   al abrir la app. Eso es carga de clases y JIT, y la herramienta para eso es un
   **Baseline Profile**: hace falta un modulo de macrobenchmark y correrlo desde
   Android Studio, asi que es una tanda aparte y la tiene que lanzar Dani.
2. **Primera composicion de cada carpeta nueva.** Los 17 de la tercera ventana
   son entrar en DC Comics, luego en Green lantern —doce carpetas, cada una con
   su carrusel de portadas— y volver. Eso es trabajo de verdad la primera vez;
   con el pager arreglado, la segunda ya no lo repite.

**DANI LO CONFIRMA: "si va mucho mejor" (04/09/2026).** Con eso se cierra la
caceria. Lo que queda —arranque en frio y primera composicion de cada carpeta—
**no molesta lo suficiente para pagar un Baseline Profile**, que es la unica
herramienta que lo arreglaria de verdad. Queda escrito por si algun dia vuelve.

**Y LOS DOS INSTRUMENTOS SE QUEDAN PUESTOS**, `Fluidez` y el contador de
portadas. Cuestan dos restas por fotograma y una linea cada diez portadas, solo
hablan cuando algo va mal, y son lo que resolvio esto **despues de cinco
sospechas falsas seguidas**. Quitarlos seria tirar justo lo que funciono.

**NO SE TOCA NADA MAS SIN QUE DANI DIGA SI LO NOTA.** El rebote por pestañas
—que era lo que se repetia todo el rato y por tanto lo que mas se sufre— esta
quitado y medido. Lo que queda cuesta bastante mas y puede que ya no moleste:
eso lo dice el que la usa, no el numero.

### Tanda 15: la pagina que sale del comic ya es `ImageBitmap` (04/09/2026)

Paso 2 del mapa del iPad —"leer un CBZ"— empezado por su mitad barata: **antes de
poner una interfaz delante hay que arreglar el tipo que cruza la frontera**.

`ComicZip.pagina` y `ComicZip.portada` devolvian `android.graphics.Bitmap`. Ahora
devuelven `ImageBitmap`, que es el tipo que entiende Compose en las dos
plataformas y el que ya usaba `Miniaturas` desde la tanda 7. Las tres caches del
visor —paginas, miniaturas y detalle— guardan tambien `ImageBitmap`.

**Y ESTO NO ERA SOLO PARA EL IPAD: en el visor habia el mismo fallo que la tanda
7 quito de la rejilla.** El visor guardaba `Bitmap` y llamaba a `asImageBitmap()`
**dentro del composable**, en tres sitios. Eso es **una conversion por
recomposicion** en vez de una por decodificacion, y ocurre justo mientras se
amplia y se arrastra una pagina. Ahora la conversion se hace una vez, al
decodificar, y el visor pinta lo que le llega.

`Paginas` se va a `:shared`. Es el contrato —una lista de nombres o una cadena—
y no sabe de ninguna plataforma; lo usan el visor, el conversor y el ViewModel.
**El motivo sigue siendo texto y no un codigo de error**, a proposito: lo unico
que se hace con el es enseñarlo, y cada fallo tiene su frase propia. Una
enumeracion obligaria a traducir codigo a frase en la pantalla, que es donde peor
se mantiene.

**DOS SITIOS VUELVEN A `Bitmap`, Y ESTA BIEN.** Guardar la miniatura en disco y
exportar una pagina son comprimir a JPEG, que es de Android; las dos llaman a
`asAndroidBitmap()`. Es **una vez por portada guardada y una por pagina
exportada**, contra las que se han quitado, que eran por repintado. El saldo es a
favor y las dos piezas se quedan en `:app` de todas formas.

Verificado: `comprobar.py` con **PROBLEMAS: 0**, `:app:assembleDebug` sin un solo
`w:` y las pruebas en verde.

**LO QUE NO ESTA COMPROBADO**: que el visor siga viendose igual. Es un cambio de
tipo, no de pixeles, pero **por el camino nuevo no ha pasado ninguna pagina**. Se
mira abriendo un comic: que la pagina salga, que ampliar siga funcionando, que la
tira de miniaturas de abajo se pinte, y que guardar y compartir una pagina —que
son los dos sitios que vuelven a `Bitmap`— hagan su fichero.

**LO SIGUIENTE**: ya con el tipo bueno en la frontera, la interfaz de leer comics
es mecanica. Y despues `Escaner`, que es la pieza mas distinta de todo el port.

### Tanda 16: abrir un comic, detras de una interfaz (04/09/2026)

Segunda mitad del paso 2 del mapa. Con el tipo de la frontera ya arreglado en la
tanda 15, esto salio mecanico: `Archivo` en `:shared` y `ArchivoAndroid` en
`:app`. **`VistaModelo` ya no menciona `ComicZip`.**

**TRES METODOS Y NO SEIS, y es la decision de la tanda.** `ComicZip` sabe ademas
decir el formato de un fichero y por que no ha podido con el, pero eso solo se lo
preguntan `ConversorCarpeta` y `Miniaturas`, que son de Android y se quedan
alli. La interfaz lleva **lo que `VistaModelo` necesita hoy** —listar paginas,
decodificar una, precargar las de al lado— y no todo lo que la implementacion
sabe hacer. Lo demas se añade el dia que alguien portable lo pida.

**Y `ArchivoAndroid` es un envoltorio fino, no una mudanza.** `ComicZip` son 330
lineas con tres caches, el respaldo de junrar y la conversion de RAR5, y cada una
de esas decisiones costo un cierre de la app en su dia. Meterle una interfaz por
dentro seria tocar todo eso para no ganar nada: **lo unico que hacia falta era
quitarle el `Context` a quien lo llama**, y eso se consigue guardandolo en el
envoltorio.

Verificado: `comprobar.py` con **PROBLEMAS: 0**, `:app:assembleDebug` sin un solo
`w:` y las pruebas en verde. **Android:** no cambia ni un comportamiento, es la
misma llamada con el `Context` guardado en otro sitio. **iOS:** aqui no se ha
escrito nada de iOS todavia; `ArchivoIOS` es de la tanda que traiga el motor de
descompresion, que es de las gordas.

### Tanda 17: recorrer la biblioteca, detras de una interfaz (04/09/2026)

Paso 3 del mapa, y **la pieza mas distinta de todo el port**. `Biblioteca` en
`:shared`, `BibliotecaAndroid` en `:app`. Ni `VistaModelo` ni `PantallaBiblioteca`
mencionan ya `Escaner`.

**POR QUE UNA INTERFAZ Y NO UN `expect/actual`.** En Android es SAF: eliges una
carpeta una vez, el sistema da permiso persistente sobre su arbol y se consulta
con un `ContentResolver`. En iOS **no hay nada parecido**: `UIDocumentPicker` y
*security-scoped bookmarks*, que hay que **guardar y volver a resolver en cada
arranque** y abrir y cerrar el acceso a mano. No es la misma operacion escrita en
dos idiomas: son **dos mecanismos distintos que casualmente responden a la misma
pregunta** —que hay en esta carpeta—. Un `expect/actual` obligaria a que las dos
tuvieran la misma forma, y no la tienen.

**LA REGLA QUE HAY QUE NO ROMPER, y va escrita en la interfaz**: `raiz` y `docId`
**son cadenas opacas**. En Android son uris de SAF y en iOS seran marcadores.
Quien llama **no las interpreta nunca**: las guarda y las devuelve. El dia que
alguien parta un `docId` por barras para sacar el nombre de la carpeta, el port
se rompe y el fallo aparecera en iOS, a cinco mil kilometros de la linea culpable.

`Escaner.Contenido` pasa a `Contenido` en `:shared`. Es una pareja de listas de
`Carpeta` y `Comic`, que ya viven alli.

**Lo que NO entra en la interfaz**: `raizDe`, y el parametro `conCuentas` de
`abrir`. El primero solo lo usa el propio `Escaner`; el segundo lo pone `todosBajo`
por dentro, y quien llama desde fuera siempre quiere las cuentas. Misma regla que
en la tanda 16: **la interfaz lleva lo que el consumidor necesita, no lo que la
implementacion sabe hacer**.

Verificado: `comprobar.py` con **PROBLEMAS: 0**, `:app:assembleDebug` sin un solo
`w:` y las pruebas en verde. **Android:** no cambia ningun comportamiento —misma
llamada, el `Context` guardado en el envoltorio—. **iOS:** no se ha escrito nada;
`BibliotecaIOS` es una tanda entera y de las caras.

### Pendiente

- **Pulsar el boton de limpiar la biblioteca sobre una carpeta de verdad**, y
  **que sea una de la que haya copia**. Las reglas tienen catorce pruebas desde
  la tanda 11, pero el camino entero —contar paginas, renombrar y borrar con
  SAF— no lo ha recorrido ningun fichero.
- **Mirar la tanda 12 (el acabado de `better-ui`) en el movil**: que la bola de
  los interruptores se deslice y no salte, que dandole dos veces seguidas no se
  teletransporte, que las cartas de la rejilla se encojan bajo el dedo y —lo que
  mas riesgo tiene, porque cambio como se conecta el toque— **que la pulsacion
  larga siga abriendo el menu del comic**.
- **Confirmar que la pantalla en negro se ha ido.** La causa está encontrada y
  arreglada (ver más abajo), pero hasta que Dani no lo use un rato no está
  cerrado. Si vuelve, el rastro ahora incluye cuánto tarda el barrido del índice.
- **Probar el deslizado en el móvil**, y en concreto lo que pasa **encima de un
  carrusel de portadas**: ahí el gesto se lo queda la fila hasta que llega a su
  tope. Está explicado arriba; falta ver si en la mano molesta o ni se nota.
- **Comprobar el trabajo diario de verdad en el móvil.** Con Android Studio se
  fuerza desde `App Inspection > Background Task Inspector`, o con
  `adb shell cmd jobscheduler run -f com.dani.lector <id>`. Hasta que no salte
  una notificación en el móvil de Dani, esto es código que compila, no una
  función que funciona.
- **Ver cuántos números traen `store_date` de verdad.** Es lo que decide si la
  estimación de 60 días es un respaldo raro o el camino normal, y ahora mismo
  **no se sabe**: desde aquí no hay red a Comic Vine. Se comprueba pegando una
  respuesta de `issues/?filter=volume:<id>&field_list=issue_number,cover_date,store_date`
  y contando los `store_date` vacíos. Si vienen casi todos, `ADELANTO_PORTADA`
  deja de importar; si faltan casi todos, hay que afinar esos 60 días con casos
  reales.
- **Ver si `DESFASE_ESPANA = 0` acierta.** Es la apuesta de que un número USA
  llega a Dani el mismo día que sale allí. Se comprueba con el primer aviso
  real: si llega dos días pronto, se suben esos dos días y ya.
- **Paginar `buscar()`** — ya NO hace falta para el caso normal: se resolvió
  cambiando de endpoint. Sigue pendiente solo si aparece algún caso que
  `/search/` no cubra.
  **Requisito explícito de Dani: la solución vale para cualquier personaje que
  meta, o no vale.** Nada de heurísticas afinadas a la biblioteca de hoy: la
  idea de "empezar por la última página" se descartó por eso — funciona porque
  sus series son modernas y se rompe con Golden Age. La regla tiene que ser
  ciega al personaje, y **avisar** si hay que cortar por presupuesto en vez de
  devolver un cero que parece "no existe".
- Confirmar el vínculo comparando portadas (la comparación se quitó en su día).
- ~~El PDF dentro de un CBR~~ — **cerrado el 03/09/2026**: Dani abrió
  `Green Lantern - Emerald Warriors #13` y se ve bien. Si algún día vuelve a
  aparecer un CBR con un PDF dentro, el camino sigue siendo `PdfRenderer` sobre
  un temporal extraído a la caché.
- ~~Modo claro~~ — **descartado por Dani (03/09/2026)**. Los tokens siguen
  montados para poder añadirlo si cambia de idea.

### Higiene

**El proyecto YA ESTÁ EN GIT (03/09/2026).** `git init` en la raíz del módulo y
un primer commit, `83b29d3`, con 177 ficheros. **`local.properties` NO entró**:
el `.gitignore` que ya había lo cubre, y solo se versiona `local.properties.ejemplo`.
Sin remoto: es una red de seguridad local, no una copia fuera del ordenador.

Al hacerlo, git avisa en bucle de `LF will be replaced by CRLF`. **Son avisos, no
errores**: es la normalización de fin de línea de Windows. Se callan con
`git config core.safecrlf false` si molestan.

Queda por limpiar: en `~/Downloads` había **38 copias** del proyecto
(`lector-comics-android`, `_1` … `_19`, cada una carpeta y zip). Eran los backups
a mano que sustituye el repositorio.

La basura quedó **toda junta en `_borrar_a_mano/`**, y desde el 02/09/2026 hay
dos carpetas más ahí: `orden_de_lectura/` y `listas_y_todo/`, con el código
amputado ese día. **No se borra todavía a propósito**: hasta que el proyecto no
compile y se pruebe en el móvil, es la única copia que hay de todo eso.

De antes (25/08/2026), unos 232 KB:

- `git_a_medias/` — el `git init` abortado. Sin commits.
- `carpeta_llaves/` — el árbol de carpetas **vacías** que salió del `mkdir` con
  llaves que Windows no expandió. No contenía ni un solo fichero.

Se borra una carpeta y se acabó.

---

## 8. Preferencias de trabajo

- Español, tuteo, directo y sin rodeos
- Código comentado explicando **por qué**, no qué
- Nombres de variables y funciones en español
- Avisar de los problemas antes de que aparezcan, no después
- Tandas cortas y compilar entre medias: el Compose va sin red de seguridad
- **Los dos documentos del proyecto se actualizan en la misma tanda que el
  cambio**, no al final. Qué se hizo, por qué se decidió así, y con qué se
  comprobó — y lo que no se comprobó, también.
