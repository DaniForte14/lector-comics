# Lector de cómics — el diseño

La app tiene **dos estéticas completas** y un interruptor. Este documento
existe para que nadie deshaga por accidente una decisión que costó tomar, y
para saber qué se puede y qué no.

Estado: cyberpunk activo, compilando y en uso (agosto 2026).

---

## 1. El interruptor

En `ui/Tema.kt`, primera línea:

```kotlin
enum class Estilo { IOS, CYBERPUNK }
val ESTILO = Estilo.CYBERPUNK
```

Cambiar esa línea cambia la app entera: color, forma y tipografía.

**Lo de iOS no está comentado ni borrado, está al lado.** Comentar código es
dejarlo pudrirse: en un mes ni compilaría. Así las dos opciones se mantienen
vivas, se pueden comparar, y el fichero documenta las decisiones de las dos.

Funciona porque **todo el diseño pasa por los tokens de ese fichero**. Los
nombres (`Tinta`, `Panel`, `Hueso`, `FormaTarjeta`, `Tipo.grande`...) son los
mismos en las dos estéticas y las pantallas no saben cuál está puesta.

Ese detalle —**mantener los nombres viejos y cambiar solo los valores**— es lo
que permitió restilar 109 usos repartidos por las pantallas sin tocarlas una a
una, y que las pantallas aún no migradas siguieran siendo coherentes mientras
tanto. Es la razón de que el cambio de aspecto se hiciera en un paso y no en
veinte.

---

## 2. Lo que NO se puede hacer, y no es por pereza

**SF Pro no se puede meter en un APK.** Su licencia es solo para plataformas de
Apple. La alternativa libre más cercana es **Inter**: iría en `res/font` y se
cambiaría una línea en `Tipo`.

**SF Symbols, igual.** Los iconos salen de `material-icons-core`, que ya estaba
en el gradle.

**Nada de desenfoque en las barras.** `Modifier.blur` pide API 31 y el `minSdk`
de este proyecto es **26**.

**Solo modo oscuro.** Los tokens están montados para añadir el claro después
sin rehacer nada.

---

## 3. La estética de iOS

Lo que hacía que pareciera de Apple, por orden de impacto:

1. **Interletrado negativo** en los cuerpos grandes. Más de la mitad del
   efecto, más que el tamaño de letra.
2. **Corte de carátula**: esquina redondeada y un filo blanco al 10%. El filo
   no es adorno: sobre negro puro, una portada de marco oscuro se funde con el
   fondo y la carta pierde la forma.
3. **Separadores de medio punto y sangrados** por la izquierda.
4. **Lo pulsable va en color de acento**, no en negrita ni más grande.
5. **Nada de mayúsculas espaciadas.** Apple no grita.
6. **Fondo negro puro**, que además apaga el píxel en OLED.
7. **Acciones destructivas en rojo**, no escondidas en gris.

Se mantuvo el **rojo del proyecto** en vez del azul del sistema: Apple tampoco
obliga, Podcasts es morado y Fitness verde.

---

## 4. La estética cyberpunk (2077)

Las tres marcas de fábrica del juego, que son las que lo hacen reconocible:

**1. El amarillo ácido `#FCEE0A` como mancha, no como detalle.** Y con él lo que
casi nadie copia bien: **el texto encima va en negro**. Sobre ese amarillo el
blanco es ilegible, y ese contraste bestia es lo que da aspecto de rótulo
industrial. Hay un token, `SobreAcento`, para que ningún botón se escape.

**2. Las esquinas no se redondean: se cortan.** Un chaflán en diagonal, en las
esquinas opuestas, que es como están casi todos los paneles del juego. Compose
lo trae de serie con `CutCornerShape`. Es lo que más lo separa de "app oscura
genérica con neones". En las portadas el chaflán es pequeño y solo en una
esquina: comerse un trozo de dibujo canta.

**3. Los tres colores**: amarillo para lo que manda, rojo `#FF003C` para el
aviso, cian `#02D7F2` para lo bueno.

**El fondo tira a cálido, no a azul.** En el juego todo parece impreso sobre
metal sucio, no iluminado por un rótulo frío. Los grises van hacia el oliva.
(La primera versión tiraba a azul y estaba mal.)

**Tipografía**: interletrado abierto —lo contrario de Apple— y **monoespaciada
en todo lo que sea un dato** (años, números, rutas, diagnósticos). No es
capricho: alinea las cifras en columna y da el aire de terminal **sin meter
ninguna fuente en el APK**, porque `FontFamily.Monospace` la trae el sistema.
Es la respuesta a lo que no se pudo hacer con SF Pro.

### Las animaciones

Van solo en la cabecera y el banner, y **apagadas automáticamente en el estilo
iOS**. Ninguna se pinta encima de una página del cómic: esto es una app de leer
durante horas, no una intro.

- **Glitch del título**: descuadre de canal, copia cian a un lado y roja al
  otro. La clave no es el efecto sino el **ritmo**: pestañea tres fotogramas y
  se está quieto entre dos y seis segundos. Un glitch continuo marea y encima
  impide leer el título, que es para lo que está.
- **Líneas de barrido y haz**: las rayas se pintan con **un solo rectángulo** y
  un degradado repetido (`TileMode.Repeated`), no con un bucle de doscientas
  líneas por fotograma. Parece lo mismo y no cuesta nada.

---

## 5. El color ambiente (agosto 2026)

**La interfaz se tiñe del color de la portada que estás leyendo.** Un tomo de
Daredevil pone las barras rojizas y uno de Green Lantern verdosas, sin que
nadie haya elegido esos colores a mano.

Vino de un vídeo de un reproductor de música que pasó Dani. De todo lo que se
veía allí, esto es lo que más hace por el aspecto: el fondo deja de ser un gris
de plantilla y pasa a ser *de la obra que tienes abierta*.

**De dónde sale el color.** `datos/ColorPortada.kt`, sin dependencias nuevas:

- Reduce la portada a 40×40 y cuenta. 1600 píxeles bastan de sobra.
- **Descarta los píxeles sin color**: casi negros, casi blancos y grises. En un
  cómic eso es la tinta de las viñetas y el blanco de los bocadillos, que son
  media página. Sin ese filtro **todas** las portadas darían gris.
- Agrupa el resto por tono en 24 casillas y las pesa por saturación y cercanía
  al brillo medio, para que un detalle chillón en una esquina no le gane a la
  mancha grande.
- Si no queda ni un píxel con color —una portada en blanco y negro— devuelve un
  gris del brillo medio. **No se inventa un tono que no existe.**

No se usa `androidx.palette`: sería meter una dependencia entera, y además
apunta a colores "vibrantes" para rótulos, cuando aquí hace falta el color que
**manda** en la imagen aunque sea apagado.

Se apoya en `Miniaturas`, así que reutiliza la miniatura de 220 px que ya está
en caché para pintar el catálogo: en el caso normal no cuesta ni una lectura de
disco.

**Cómo se pinta, que es donde está la decisión de diseño.** El color **no se
pinta crudo**: pasa por los tokens `ambienteFondo` y `ambienteBarra` de
`Tema.kt`, como todo lo demás. Si se pintara directo, el interruptor de estética
dejaría de mandar y una portada rosa chicle rompería el aspecto de 2077 en
cuanto abrieras ese tomo.

Por eso las dos estéticas lo usan con fuerza distinta: en **iOS** el color puede
mandar (brillo 0.20–0.30, es un baño), y en **cyberpunk** es un **tinte** más
oscuro (0.15–0.20). El fondo tiene que seguir pareciendo metal sucio impreso, no
una pantalla de color, porque **lo único intocable es que el amarillo del acento
destaque sobre el fondo**. Un ambiente fuerte se lo comería, y con él la marca
de la casa.

### El primer intento no se veía, y el porqué es la parte útil

La versión inicial oscurecía **mezclando el color contra negro** (`lerp`). Es lo
obvio y **es justo lo que no funciona**: al mezclar contra negro se pierde
saturación además de brillo, así que un verde al 20% sobre un negro casi puro da
**un gris con una idea de verde**. En el móvil, invisible. Dani lo probó y la
respuesta fue "no se ve nada, ¿no?".

Lo que sí funciona, en `ColorPortada.oscurecer`: pasar a HSV, **bajar solo el
brillo y sostener la saturación**. Eso deja un verde oscuro que se distingue de
un rojo oscuro, que es todo lo que hacía falta.

Con una excepción que importa: **la saturación mínima no se aplica a los
grises.** Si la portada era en blanco y negro, forzarle saturación sería
inventarle un tono — exactamente lo que `dominante` se cuida de no hacer. Un
umbral de 0.10 lo deja pasar tal cual.

La otra mitad del efecto es **dónde** se pinta. Un color plano en dos barras
finas no se nota por muy bueno que sea el color; la barra de arriba del visor va
con `ambienteVelo`, un degradado del tinte a transparente, para que el color
ocupe mucha más pantalla sin tapar la página. El texto vive en la franja opaca.

**El ambiente no se queda en el visor.** La pantalla de biblioteca se tiñe
también, con el color de la portada de lo que estás leyendo — es lo que hace el
vídeo en su pantalla de inicio y lo que da la sensación de que la app *sabe* qué
tienes abierto.

Ahí el degradado **se apaga al 38% de la altura**. Teñir la pantalla entera pone
de color hasta la barra de pestañas y deja de parecer un ambiente para parecer
un tema mal elegido.

**Comprobado en el móvil el 25/08/2026** con un Green Lantern: la barra y la
tira de miniaturas tiran claramente a verde, y el título ya no lo pisa la
cámara. La tira de abajo se dejó en color plano y no en degradado a propósito:
allí hace falta opacidad para que las miniaturas se recorten contra algo.

**Sin portada legible devuelve el negro al 80% de siempre**, así que el visor se
ve exactamente como antes. Esto no puede romper nada: como mucho, no tiñe.

La parte que decide (`ColorPortada.dominante`) es una **función pura**: bitmap
entra, color sale. Sin `Context`, sin caché, sin Compose. Se puede ejecutar
fuera de Android con una imagen guardada, que es la misma regla que ya se sigue
con `Wiki.interpretar` o `elegirVolumen`.

---

## 6. La barra de "seguir leyendo" (agosto 2026)

El mini reproductor de las apps de música, pegado encima de las pestañas: lo que
tienes a medias, siempre a un toque.

**Aquí gana más que en una app de música**, y ese es el argumento para tenerla:
en la biblioteca te metes por carpetas, y el banner grande de portada solo está
en la raíz. En cuanto bajabas un nivel, lo que estabas leyendo desaparecía de la
vista y había que subir a buscarlo.

Vive en `MainActivity.BarraInferior`, **fuera** de `PantallaCarpeta`, justo para
que siga ahí mientras navegas carpetas.

Lleva portada pequeña, nombre, "página X de Y" y una raya de progreso de 2 dp
pegada a la línea de las pestañas — el progreso sin gastar una fila de texto.

**Se tiñe con el color de SU portada, no con el de la pantalla.** Es una pieza
del cómic que estás leyendo, no del sitio donde estás. Usa el mismo
`ambienteBarra` de la sección 5.

**Va OPACA del todo, al revés que las barras del visor.** Allí la translucidez
es buena porque deja ver la página que hay debajo. Aquí la barra flota sobre una
lista que **se mueve**, y con el 93% de `ambienteBarra` se leía el "2 carpetas ·
1 cómics" de la fila de abajo a través del texto de la barra. Misma función, más
`copy(alpha = 1f)`.

Tres trampas que costaron:

- **El hueco del final de la lista.** Había un `Spacer(80.dp)` para la barra de
  pestañas; con la barra nueva hacen falta 148. Y el dato que decide es si la
  barra está visible, que es `seguirLeyendo() != null` **en cualquier carpeta**
  — no solo en la raíz. Por eso `PantallaCarpeta` pide ese valor siempre aunque
  el banner grande solo lo use en la raíz.
- **`Portada` lleva `cargar` de tercer parámetro y `encima` de último.** Pasar
  la lambda pegada al paréntesis la engancha a `encima`: la portada sale vacía
  y no hay ningún error que lo explique. Es la trampa de la lambda final, otra
  vez.
- **`Portada` recibe DOS formas de conseguir el bitmap** (25/08/2026):
  `cargar` (`suspend`, va al disco si hace falta) e `inmediato` (síncrona, solo
  mira la memoria). No es duplicar por duplicar: suspender cuesta un salto de
  hilo y de vuelta, o sea uno o dos fotogramas con la carta gris **aunque la
  miniatura ya estuviera hecha**, y en una rejilla eso se paga por cada carta
  que entra en pantalla. Con `inmediato`, lo que ya está puesto se pinta en el
  mismo fotograma. `inmediato` va **antes** de `vacio` y `encima`, así que la
  trampa de la lambda final sigue igual de vigente.
- **Nada que ya está en pantalla desaparece para volver a salir.** Cuando un
  dato se recalcula, la clave va en el `LaunchedEffect` y **no** en el
  `remember`: así el valor viejo se sustituye por el nuevo en vez de vaciarse
  primero. Una rueda de carga sobre algo que ya se estaba viendo se lee como
  que la app va lenta, aunque el cálculo dure dos milisegundos.
- **Ningún efecto decorativo se anima en bucle continuo.** El barrido de la
  tarjeta de "Seguir leyendo" pasa cada seis segundos y el glitch del título
  salta cada dos a seis; entre medias no se repinta nada. Un bucle infinito
  mantiene la pantalla a 120 Hz y calienta el móvil con la app quieta, y encima
  se ve peor: lo que no para deja de leerse como un efecto y pasa a ser ruido.
  Los dos respetan el ajuste de animaciones del sistema.
- **Los degradados fijos son `val` de fichero, no se montan en el Composable**
  (`VELO_CARTA` en `PantallaBiblioteca.kt`, `RAYAS` en `Componentes.kt`). Un `Brush`
  guarda su shader dentro, así que uno nuevo por carta y por repintado es un
  shader nuevo por carta y por repintado. Se pueden sacar fuera **porque sus
  paradas van en fracciones**: si algún degradado nuevo necesita píxeles o el
  tamaño del elemento, ése no se puede hoistear y va con `remember`.
- **El `sello` baja por parámetro a las cartas, no lo recogen ellas.**
  `TarjetaComic` y `FilaResultado` hacían su propio `collectAsState`: una
  suscripción por carta y un repintado de todas con cualquier cambio del estado.
  La pantalla lo recoge una vez y reparte el número.

---

## 7. Ajustes en tarjetas, y el interruptor que no se puede poner ahí

Los ajustes pasan a tarjetas agrupadas. **`Grupo` ya existía en
`Componentes.kt` desde el restilado y no lo usaba nadie**: la pantalla de
ajustes seguía con rótulos sueltos en mayúsculas y controles a pelo. Solo hubo
que usarlo.

Con él, `Interruptor` gana el relleno lateral de `Fila` y su separador sangrado
(`ultima = true` en el último, igual que `Fila`), porque ahora vive dentro de
una tarjeta y sin margen el texto tocaba el borde.

**`Segmentado`** es nuevo: el patrón "System / Light / Dark" de iOS. Sirve para
lo que un interruptor hace mal — **cuando la opción contraria tiene nombre
propio**. "Llenar la pantalla: apagado" no dice qué pasa entonces;
"Encajar | Llenar" sí. La marcada va en `Acento` con el texto en `SobreAcento`,
o sea negro sobre amarillo en 2077.

Las dos claves de API van ahora en **una sola tarjeta con un botón de guardar**.
Antes parecían dos ajustes independientes con un botón suelto debajo y no se
sabía a cuál de los dos aplicaba.

### Lo que NO se ha podido hacer: elegir la estética desde Ajustes

En el vídeo que originó todo esto, el control segmentado es para el tema. Aquí
**no se puede**, y conviene saber por qué antes de intentarlo:

`ESTILO` es un `val` de nivel superior evaluado **al compilar**, y de él salen
todos los tokens (`Tinta`, `Acento`, `FormaTarjeta`, `Tipo.*`...), también como
`val` de nivel superior. Un control en Ajustes no puede cambiar eso: haría falta
que los tokens se leyeran desde la composición, con un `CompositionLocal` o
convirtiéndolos en propiedades `@Composable`.

Y ahí está el coste real: **no son solo los tokens**. `colorPeso`,
`etiquetaPeso`, `ambienteBarra`, `ambienteFondo` y `ambienteVelo` son funciones
normales que los usan, y dejarían de compilar. Son ~40 tokens, cinco funciones y
109 usos.

Es un refactor de verdad, no un añadido. Si algún día se hace, que sea su propia
tanda y no colado dentro de un cambio de aspecto. Mientras tanto, la estética se
cambia donde siempre: una línea en `Tema.kt`.

---

## 8. Portadas en la lista de series (agosto 2026)

Las series del TODO eran solo texto: pastilla de peso, nombre, año y una barra
de progreso. Ahora llevan carátula a la izquierda.

**Y las que no tienes descargadas también salen**, con la carta vacía y su
nombre dentro. Eso no es un apaño: era el planteamiento desde el principio —
enseñar también lo que **no** tienes, que es de lo que va un TODO de lectura.
Una lista donde solo aparece lo que ya tienes no sirve para saber qué te falta.

La portada es la del **número más bajo** de la carpeta vinculada. No hay
carátula "de la serie" en ningún sitio: lo más parecido es su primer número.

**El contexto del modelo va debajo y a todo el ancho**, no al lado de la
portada. Son tres o cuatro líneas y en una columna de media pantalla salían
ocho, con lo que cada serie ocupaba una pantalla entera.

### El nombre va ENCIMA de la portada, no debajo

En la rejilla del catálogo (`TarjetaComic`) el nombre iba debajo, en texto
suelto sobre el fondo. Ahora va sobre el arte, como las cartas de "Recently
played" del vídeo. Es lo que más separa un catálogo con aspecto de app de música
de una rejilla de miniaturas con pie de foto, y encima gana sitio: la carta
ocupa lo mismo y el nombre no roba dos líneas por debajo.

- **El velo es un degradado que empieza a media carta**, no un rectángulo
  opaco: el dibujo se sigue viendo entero y el texto tiene contra qué leerse.
- **Va de `Tinta`, no de negro puro**, para que en 2077 herede el negro cálido
  en vez de meter un gris azulado que ahí canta.
- **El orden de pintado importa**: primero lo que oscurece (la capa de
  "terminado"), encima el velo, y el texto y las marcas al final para que no se
  los coma nada.

**Y el título no repite el nombre de la carpeta.** Al verlo en el móvil, las
sesenta cartas de "Green Lantern Vol. 4" ponían todas *"Green Lantern Vol4
#NN"*: tres líneas de título gastadas en decir dónde estás, que ya lo pone
arriba. `Parser.sinPrefijoDeCarpeta` lo recorta y deja "#00 Secret Origin".

Lo que hace que funcione es que **compara normalizado por los dos lados**: la
carpeta dice "Vol. 4" y el fichero "Vol4", y como cadenas no se parecen en nada;
sin tildes ni signos, `greenlanternvol4` sí es prefijo de
`greenlanternvol400secretorigin`. Luego recorre el original contando solo los
caracteres que sobreviven a normalizar, para cortar en el texto de verdad y no
en el normalizado, que es otra cadena.

Devuelve el nombre entero si no encaja o si al quitar el prefijo no queda nada:
una carta sin título es peor que una con el título repetido.

Es una **función pura** en `Parser`, y se comprobó con ocho casos reales
—incluidos `Daredevil Vol.7 #01 [9R] @Grupo` y los que no deben recortarse—
antes de enchufarla a la interfaz.

**No se ha tocado `FilaPortadas`**, el carrusel de 104 dp. Allí las cartas son
números sueltos de una misma carpeta y ya llevan su chapa con el número: meterle
además "Green Lantern Vol4 #06" a tres líneas sobre una carta tan pequeña es
ruido, no información. El patrón del vídeo funciona en cartas grandes.

### Búsquedas recientes, en pastillas y en una sola fila

En el vídeo los recientes son una lista vertical, porque allí la búsqueda tiene
**pantalla propia**. Aquí el buscador vive encima de la biblioteca: ocho
entradas en vertical empujarían el banner fuera de la pantalla cada vez que
abres la app. Van en pastillas, en una fila horizontal bajo el buscador, y solo
cuando el campo está vacío. Tocar una la repite; la × la borra.

Se guardan en las prefs que ya había, no en un JSON aparte: son ocho cadenas
cortas y **no entran en la copia de seguridad**. Que se pierdan al reinstalar no
le duele a nadie, al revés que lo leído.

**Se apuntan al parar de teclear**, no en cada letra: escribiendo "batman"
pasarías por "ba", "bat", "batm"... y el historial se llenaría de fragmentos. El
efecto se cancela y rearranca con cada cambio de texto, así que la espera de 1,2
s solo llega al final. Y solo se guarda si la búsqueda encontró algo.

**Trampa que casi entra**: ese efecto va con clave `busqueda` **y sin
`estado.sello`**. `recordarBusqueda` sube el sello, y con el sello en la clave el
efecto se rearrancaría a sí mismo cada segundo y medio, para siempre.

La comparación para no duplicar va **normalizada** ("Green Lantern" y
"green lantern" son la misma entrada) pero se guarda lo que tecleaste: en la
lista tiene que salir tal cual lo escribiste.

### Las filas de carpeta enseñan series, no números sueltos

La fila de portadas bajo "DC Comics" era `comicsBajo(...).take(12)`: los doce
primeros cómics que salieran, aplastados. En la práctica eso significaba doce
números de Green Lantern y ni rastro de las demás series — una fila que no dice
nada de lo que tienes.

Ahora hay **una carta por serie**, con su nombre encima del arte, y la portada
que enseña es **la del número por el que ibas**: si tienes esa serie empezada
sale por donde la dejaste, no por el #01 que leíste hace un mes.

**Serie = la carpeta que contiene los cómics de verdad**, por hondo que esté, no
la subcarpeta directa de la fila. El primer intento agrupó por el nivel de
arriba y bajo "DC Comics" salían dos cartas —"Green lantern" y "Batman"—, que es
tan poco informativo como la lista aplastada de antes. Lo útil es ver "Absolute
Green Lantern", "Green Lantern Vol. 4" y "Absolute Batman" por separado. Por eso
la etiqueta es el **último** tramo de la ruta y no el primero.

El orden de `porDondeIbas`: el que tienes a medias (el más reciente si hay
varios), el primero sin terminar, y si está todo leído el primero de todos.

Los cómics **sueltos** de ese nivel salen enteros, uno por carta: no son una
serie y agruparlos en una sola los escondería.

Se lee la carpeta **una vez** y se agrupa en memoria. Preguntar por cada
subcarpeta serían tantos recorridos de disco como series tengas.

### El cálculo va fuera de la composición, y esto es lo importante

`vm.portadasPorCarpeta()` devuelve el **mapa entero** de carpeta → primer cómic,
de una vez para toda la pantalla.

Hacerlo por fila habría significado recorrer la biblioteca completa una vez por
cada una de las ~300 series de un personaje grande, **y dentro del cuerpo de la
lista**, que es la regla que más duele romper en este proyecto ("nada de leer de
disco al pintar", ver `LECTOR-COMICS-CONTEXTO.md`).

Una pasada y sin ordenar: quedarse con el mínimo sale más barato que ordenar la
lista entera para coger luego el primero de cada grupo.

---

## 9. Componentes propios

En `ui/Componentes.kt`:

- `Cabecera` — título grande. **La firma no se tocó a propósito**, para que
  todas las pantallas se restilaran solas.
- `Grupo` + `Fila` — lista agrupada con separador sangrado.
- `Campo` — campo de texto. Sin `OutlinedTextField`: el de Material trae
  etiqueta flotante, contorno y relleno alto, y nada de eso existe en iOS.
- `Boton` — sin elevación ni sombra. `relleno = false` da el secundario.
- `Interruptor` — sin `Switch` de Material, que trae su propio look.
- `Segmentado`, `Buscador`, `BarraDesplazamiento`, `caratula()`, `TextoGlitch`,
  `escaneo()`.

Y en `MainActivity`, `Pestana`: la tab bar con icono y rótulo. Lleva
**Biblioteca** como pestaña marcada porque una tab bar que no indica dónde
estás no es una tab bar.

**No queda ni un `OutlinedTextField`, ni un `Button` ni un `Card` de Material
en toda la app.**

---

## 10. Trampas de Compose que salieron aquí

- **Leer `layoutInfo` de un `LazyListState` en la composición repinta en cada
  fotograma de scroll.** Va dentro de `derivedStateOf`.
- **La barra de desplazamiento va por ÍNDICE, no por píxeles.** Las filas miden
  muy distinto (una serie desplegada ocupa diez veces más que una cerrada) y
  Compose no sabe lo que mide lo que aún no ha pintado.
- **Dos `pointerInput` sobre el mismo elemento: el primero se come los eventos
  del segundo.** Ver `LECTOR-COMICS-CONTEXTO.md`.
- **El encaje de página se hace cambiando la escala de partida del zoom**, no el
  `ContentScale`. Así el arrastre y el doble toque siguen funcionando. Pero
  obliga a medir **todo** contra esa base y no contra 1: si no, en modo llenar
  la app creería que siempre hay zoom y el pasapáginas quedaría muerto.
- **`Color.value.toInt()` NO es el color.** `value` es un `ULong` que lleva el
  espacio de color dentro; truncarlo a `Int` da otro color. Para guardar un
  color como entero, `toArgb()`.
- **`statusBarsPadding()` vale CERO cuando las barras están ocultas, y el
  agujero de la cámara sigue ahí.** Lo correcto es
  `windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))`,
  que incluye `displayCutout` y no depende de que las barras se vean. Está
  puesto en `Cabecera` y en la barra del visor.
  **Y el orden importa**: `background()` va ANTES del padding de inset, para que
  el color llegue hasta el borde y lo que se aparte sea el texto.
- **Un parámetro nuevo en un Composable va el ÚLTIMO.** La lambda final solo se
  engancha al último, así que meter uno en medio rompe las llamadas que ya hay
  con un error que no señala al sitio. Por eso `onOrden` va detrás de `onAtras`
  en `PantallaCarpeta` (02/09/2026) aunque agrupado con los demás `on...`
  quedaría mejor.

---

## 11. Método, por si hay que repetirlo

Los parches se aplican con reemplazos de texto que **exigen que el fragmento
aparezca exactamente N veces** y abortan sin escribir nada si no cuadra. Ha
saltado varias veces: por indentaciones distintas del mismo bloque, por una
línea en blanco de más en el patrón. Las tres veces habría salido un reemplazo
a medias que compila y se ve raro.

El código de Compose **no se puede compilar fuera de Android Studio**, así que
va sin red de seguridad. Tandas cortas y compilar entre medias.

### `comprobar.py`, la red que sí se puede tender

**El fallo caro de editar así no es el que descuadra las llaves** — ese se ve
enseguida contándolas. Es el que deja un **cuerpo huérfano**: se borra una
función cortando por su primera línea en vez de por su firma completa, y el
cuerpo se queda suelto a nivel de fichero. Las llaves siguen cuadrando y no lo
ve nadie hasta que Gradle escupe "Expecting a top level declaration" cincuenta
veces.

Pasó **dos veces el 02/09/2026**, en la misma sesión y con el mismo error de
método: contar llaves desde la línea del `fun` cuando la firma ocupa varias
líneas, así que la cuenta empieza y acaba en cero y no se borra nada del cuerpo.
Con `Velo` no llegó a nada porque su firma cabía en una línea; con
`BarraDesplazamiento` sí, y llegó al móvil de Dani.

`comprobar.py`, en la raíz del proyecto, mira dos cosas: llaves y paréntesis sin
cerrar, y **líneas sangradas cuando no hay nada abierto**, que es exactamente la
forma que tiene un cuerpo huérfano. No es un compilador; es lo que se puede
tender en tres minutos desde un sitio donde no hay compilador.

**Se pasa antes de dar nada por terminado**, junto con las otras dos
comprobaciones que ya se hacían a mano: imports muertos, y que todo lo que la
interfaz llama exista de verdad.

**Y una advertencia que este documento se ganó a pulso:** decía que varias
funciones "tienen pruebas de verdad" cuando no había ni un fichero de prueba en
el repositorio (comprobado el 25/08/2026). **Corregido el 02/09/2026: ahora hay
cinco** —`HuecosTest`, `ParserTest`, `EstadoSerieTest`, `OrdenLecturaTest` y
`NovedadesTest`— pero **ninguno se ha ejecutado todavía**, porque en el entorno
donde se escriben no arranca Gradle. Ver `LECTOR-COMICS-CONTEXTO.md` §7.

---

## 12. La pantalla de orden de lectura (02/09/2026)

La lista de todas las series de un personaje intercaladas por fecha de portada.
Se entra desde la carpeta del personaje, en una tarjeta encima de las filas de
carpeta, y **solo aparece con dos series vinculadas o más**: con una no hay nada
que intercalar.

**Va agrupada por MES, y esa es la decisión de diseño entera.** La primera
versión era una lista seguida de tramos ("del #1 al #5"), que es lo que Dani
pidió con sus palabras. No se puede: dos series mensuales que corren a la vez
comparten mes uno a uno, así que la lista sale alternando y los tramos salen de
uno en uno. Y la alternancia es **correcta** — los dos números estuvieron en la
tienda el mismo mes. Lo que no se puede es decir cuál va antes, porque el dato
no lo dice.

Así que la cabecera de mes hace dos cosas a la vez: **agrupa** lo que salió
junto y **avisa** de que dentro de ella el orden es arbitrario. Un diseño que
disimulara eso —una lista numerada de arriba abajo— estaría prometiendo una
precisión que la fuente no da. Es la misma regla que hace que `Huecos` no hable
de los extremos.

Lo demás de la pantalla:

- **La primera línea dice de dónde sale el dato** y que es orden de
  publicación, no de lectura. En `Tipo.minuscula` y `Apagado`: tiene que estar,
  no tiene que gritar.
- **Las carpetas sin vincular salen en una tarjeta en `Acento`**, con sus
  nombres y qué hacer para arreglarlo. Un orden que se calla lo que no ha
  podido meter pasa por completo.
- **Una barrita de 3 dp a la izquierda de cada fila**: `Acento` si te falta
  algo, `Linea` si lo tienes. Es lo que deja recorrer trescientas filas sin
  leerlas. El texto de la derecha ("te faltan 2", "✓") lo confirma, pero el
  color es lo que se ve de un vistazo.
- **`Segmentado` con "Todo | Lo que me falta"**, que es exactamente el caso para
  el que se hizo: la opción contraria tiene nombre propio.
- **Una fila sin nada que abrir no es pulsable.** Un toque que no hace nada se
  lee como que la app falla.
- **El filtro va en un `remember` FUERA del `LazyColumn`**, como el `chunked(3)`
  de la biblioteca: el cuerpo de la lista no es `@Composable`.
- **Los números sin fecha van al final, rotulados como lo que son.** No se
  colocan en ningún sitio.

---

## 13. "Avisarme cuando salga uno nuevo" (02/09/2026)

Vive **dentro de la tira de la serie**, debajo del "te faltan 3 de 62" y encima
del "según «Green Lantern» (2005)". No tiene pantalla propia ni ajuste global, y
eso es la decisión: seguir una serie es una propiedad **de esa serie**, y el
sitio donde se decide es donde ya estás mirando cómo va.

- **No es un `Interruptor`.** Es una línea de texto que cambia:
  "Avisarme cuando salga uno nuevo" en `Acento` cuando no la sigues, "✓ Sigues
  esta serie" en `Cian` cuando sí. `Cian` es el color de lo que está bien y no
  pide nada, y `Acento` el de lo que puedes pulsar — la misma pareja que ya usa
  el estado de la serie justo encima. Un `Switch` habría metido un control de
  otra familia en una tira que hasta ahora era solo texto.
- **Debajo, una línea que dice qué va a pasar**, y dice tres cosas distintas
  según el caso: que se mira una vez al día y que el aviso salta cuando toque
  estar en tiendas (lo normal); que la serie ha terminado y no va a salir nada
  (pero que si sale, avisa); o que **sin permiso de notificaciones no puede
  avisar**, con dónde se arregla.
- **El permiso se pide justo después de pulsar**, no al arrancar la app. Ver
  `LECTOR-COMICS-CONTEXTO.md`: el sistema solo deja preguntar una o dos veces y
  gastarlo en el primer arranque es como se consigue un "no" permanente.
- **Y el orden importa**: primero se guarda la decisión, después se pide el
  permiso. Al revés, el diálogo del sistema dejaría el interruptor a medias
  mientras el usuario decide, y si lo cancela quedaría sin seguir la serie sin
  saber por qué.
- **La notificación cambia una palabra según de dónde salga la fecha**: "ya
  está en tiendas" cuando Comic Vine da la fecha de venta de verdad
  (`store_date`), "ya **debería** estar en tiendas" cuando ha habido que
  calcularla desde la de portada. Prometer menos de lo que se sabe es mejor que
  sonar exacto y fallar — es la misma regla por la que la pantalla de etapas
  rotula lo opinable como opinión, en una sola palabra.
- **`res/drawable/ic_aviso.xml` es el único drawable del proyecto.** Un
  marcapáginas monocromo, porque lo que avisa es que hay algo que leer. Tiene que
  ser monocromo con transparencia o Android lo pinta como un cuadrado blanco.

### "El siguiente sale el 30 de septiembre"

Donde antes ponía **"Sigue en emisión, así que irán saliendo más"** ahora va la
fecha del siguiente número. La frase vieja ocupaba una línea para no decir nada:
que una serie en emisión va a sacar más números es justo lo que significa "en
emisión".

Va en `Tipo.minuscula` y `Tenue`, debajo del estado y encima del "sigues esta
serie": es contexto, no un titular. Y **cae a la frase vieja** cuando no hay
ningún número anunciado con fecha — mejor eso que dejar el hueco, porque un
espacio vacío donde antes había texto se lee como que algo falló.

El verbo cambia solo, igual que en la notificación: *"sale"* cuando la fecha es
la de Comic Vine, *"debería salir"* cuando está calculada desde la de portada.

---

## 14. Las reglas del texto (02/09/2026)

Dani, con una captura de la tira de una serie: *"y todo este tipo de cosas con
tanta letra y demás, haz una revisión entera de la app para dejarla más
limpia"*. Tenía razón y la cuenta lo dice: **9.023 caracteres** de prosa
repartidos por la interfaz, **3.010 solo en Ajustes**, y 852 en una tira que
debería ser de tres líneas. Después de la pasada: **6.009**, y Ajustes 1.482.

No creció de golpe. Creció una explicación cada vez, cada una razonable por sí
sola, todas escritas por quien acababa de entender cómo funciona algo y quería
dejarlo dicho. **El sitio para eso son estos documentos y los comentarios del
código, no la pantalla.**

### La regla

> **Se explica cuando algo va mal o antes de una acción que borra o gasta
> cuota. El resto se calla.**

Lo que se queda, y por qué cada uno:

- **Estados de fallo**: "Las wikis no tienen índice de «X»" — el usuario está
  parado y necesita saber qué hacer.
- **Avisos de coste o de pérdida**: "Son 23 consultas a Comic Vine", "Solo borra
  el CBR si el CBZ tiene todas las páginas", "Lo que has leído no sale de ningún
  sitio". Van antes de algo que no se puede deshacer.
- **Avisos de que un dato puede estar mal**: "Las de confianza media se
  equivocan a veces: comprueba el año antes de marcarlas."
- **Diferencias que parecen un bug**: "Los leídos salen de tus ficheros; los
  marcados, de las listas. No tienen por qué cuadrar."
- **Pantallas vacías**: son el único sitio donde no hay nada más que leer, y sin
  una línea no se sabe ni por dónde empezar. Pero una línea.
- **La procedencia de un dato**, que es principio del proyecto — en una línea,
  no en un párrafo. "Por fecha de portada" basta; explicar qué es el orden de
  publicación, no.

Lo que se fue: cómo funciona la app por dentro, por qué se tomó una decisión,
qué pasaría si, y todo lo que empieza por "así que". **Eso se lee una vez y
estorba las otras mil.**

### Lo que se rompía en concreto

**"no es esta".** La tira ponía *"según «Absolute Green Lantern» (2025) · no es
esta"*, y ese "no es esta" era un **botón** para deshacer el vínculo cuando
Comic Vine acierta con la serie equivocada. Escrito así se lee como el final de
la frase: la app parecía estar llevándote la contraria sobre tu propia carpeta.

Ahora la línea es solo el nombre, en `Apagado`, y al tocarlo pregunta *"¿No es
esta serie?"* con Deshacer y Cancelar. **Un texto que es un botón no puede estar
escrito como si fuera prosa** — y si la acción es destructiva, el sitio de la
pregunta es un diálogo, no un renglón que se pulsa sin querer.

**La tira, de seis líneas a tres.** Cada una contesta una pregunta distinta:

```
13 de 18 · te faltan del #14 al #18      qué tengo
El #14 sale el 30 de septiembre          qué viene
✓ La sigues · Absolute Green Lantern     de dónde sale, y la acción
```

Se fueron: *"Sigue en emisión, así que irán saliendo más"* (que una serie en
emisión saque más números es lo que significa "en emisión"), las cinco líneas de
*"Miro una vez al día…"* (nadie relee cómo funciona un aviso), y *"Sin huecos
entre el primero y el último que tienes"* — **lo que va bien no se anuncia.**

### Y una avería de método, que es la parte que hay que recordar

El reemplazo que reescribió `TiraSerie` cogió como final el comentario de
`TarjetaComic`, y entre las dos había **`PantallaOrden` y `FilaTramo`**, escritas
esa misma sesión. **Las borró.** No saltó ninguna comprobación: el patrón
aparecía una vez, como se pide, y el fichero quedó con las llaves balanceadas.

Se pilló al buscar los textos de `PantallaOrden` para limpiarlos y no aparecer
ninguno. Habría llegado a Android Studio como un `Unresolved reference` en
`MainActivity`, que al menos no compila — pero un borrado silencioso de código
recién escrito es exactamente lo que no puede pasar en un proyecto sin git.

**La lección, que es distinta de la que ya estaba escrita:** este documento
avisaba de que los patrones deben aparecer N veces exactas. Eso no basta. **Un
reemplazo que abarca un RANGO tiene que comprobar además qué hay dentro del
rango**, porque el ancla del final puede haberse alejado desde que se escribió.
La comprobación barata: listar las funciones del fichero antes y después y
comparar. Se hizo después y por eso se encontró; hacerla antes habría evitado el
susto.

---

## 15. Lecturas pasa a ser estadísticas (02/09/2026)

La pestaña abría en una lista de TODOs por personaje que decía "batman · 219
series · **0 de 2411 números**". Dos cifras del catálogo mundial y ni una tuya.
Ahora abre en las cifras de lo que llevas leído, con Marcapáginas a un toque
arriba.

- **El avance por personaje se cuenta sobre lo que tienes**: "87 de 120", no
  "15 de 796". Una barra que puede llegar al final vale para algo; una que no
  llega nunca es decoración.
- **Se cayó la línea de "no tienen por qué cuadrar"** sola, sin tocarla: ya no
  hay dos cifras contadas contra cosas distintas. La mejor forma de quitar una
  explicación es quitar lo que había que explicar.
- El orden por personaje es **por porcentaje y a igualdad por tamaño**. Con
  cuatro personajes al 0% el orden tiene que ser estable o la lista baila sola
  entre repintados.

---

## 16. La primera tanda de Mistbook (02/09/2026)

Dani pasó capturas de **Mistbook**, una app de seguimiento de lecturas, con el
encargo de coger su diseño **manteniendo el cyberpunk**. Lo que se coge de ella
es la ESTRUCTURA; lo que no, su piel: nada de tipografía serif (2077 va de
monoespaciada), nada de degradados pastel, nada de lo social.

### Lo que entra en esta tanda

**La barra inferior pasa a una píldora flotante.** Antes ocupaba todo el ancho
con una línea encima, que es el patrón de Material de hace diez años y se lee
como el final de la pantalla. Flotando se lee como un control, y deja ver que la
lista sigue por debajo. Chaflán y no esquina redonda —en 2077 nada se
redondea— y con filo, porque sobre negro puro un panel oscuro sin borde no
tiene forma.

**Y la pestaña activa va en cápsula rellena**, con el contenido en `SobreAcento`:
negro sobre amarillo, la marca de la casa. Antes las tres pestañas eran el mismo
icono gris y la activa solo cambiaba de color, que se distingue peor de lo que
parece. **El rótulo solo lo lleva la activa**: los otros dos iconos se entienden
solos y así la píldora no se come el ancho.

**Cabecera de inicio con saludo y racha.** `CabeceraInicio`, aparte de
`Cabecera` a propósito: aquella la usan todas las pantallas y su firma se dejó
intacta para que el restilado no tuviera que tocarlas una a una. Meterle aquí un
saludo habría puesto "Buenas tardes" también en Ajustes y en el visor.

**La racha sube a la portada porque abajo no la veía nadie.** Se calculaba desde
el primer día y vivía enterrada en estadísticas, a dos toques. Un número que
solo se ve cuando lo vas a buscar no anima a nadie a mantener una racha, que es
exactamente para lo que sirve. Y **solo sale con racha viva**: una cápsula que
pone "0" es un recordatorio de que lo estás haciendo mal, y eso no lo pidió
nadie.

### Segunda tanda: "seguir leyendo" en horizontal

El banner era una tarjeta de **300 dp con la portada de fondo** y el texto
encima. Se comía media pantalla para decir tres cosas, el título sobre el dibujo
se leía regular, y en la captura de Dani el nombre gastaba **dos líneas en
repetir dónde estabas**: "313 - Green Lantern Corps Recharge #04" con la ruta
completa debajo.

En horizontal ocupa la mitad, la portada se ve entera contra el panel, y cabe
más información legible: rótulo, título, serie, barra y **"52% · pág. 12 de
23"**. El nombre pasa por `Parser.sinPrefijoDeCarpeta`, igual que en la rejilla.

Se queda el `escaneo()` — el barrido a rachas de 2077 — porque sigue siendo la
tarjeta destacada de la pantalla. Lo que se fue con el fondo es `Velo`, que ya
no lo usaba nadie.

### Y dos arreglos que salieron de la misma captura

**La última fila quedaba medio tapada por la píldora.** El hueco del final de la
lista eran 80/148 dp, calculados para la barra pegada al borde; la píldora
flotante ocupa más porque lleva su propio margen. Ahora son 88/152 y **se
calculan a la vez que lo que flota**. Es la tercera vez que este hueco se queda
corto al cambiar algo de abajo: si vuelve a tocarse la barra, hay que venir aquí.

**La barra de "seguir leyendo" solo sale DENTRO de una carpeta.** En la raíz, la
tarjeta de arriba ya dice lo mismo, y las dos juntas eran el mismo cómic dos
veces en la misma pantalla. Su razón de ser siempre fue que al bajar de nivel el
banner desaparece de la vista — ahí es donde hace falta, y solo ahí.

**Las etiquetas de las cartas pequeñas bajan a dos líneas.** Con tres, sobre una
carta de 104 dp, el título tapa medio dibujo y la fila entera se lee como un
bloque de texto en vez de como un carrusel de portadas.

### La pantalla negra al entrar en una carpeta

Dani: *"cuando le doy a cómics en la pantalla principal se pone una pantalla en
negro"*. **No estaba rota: estaba desplazada**, y la captura lo decía — negro
entero, sin cabecera, y con la barra de desplazamiento asomando a la derecha.

Dos decisiones viejas que por separado están bien y juntas dan esto:

1. **La cabecera de `PantallaCarpeta` vive DENTRO del `LazyColumn`**, como un
   `item` más, para que se vaya al hacer scroll.
2. **Bajar de carpeta no cambia de pantalla**: solo cambia el `docId`. El
   `LazyColumn` es el mismo objeto, así que **conserva la posición de scroll**.

Bajas por la raíz —que con la tarjeta nueva y el recorrido es más larga que
antes—, entras en una carpeta con menos contenido, y te quedas por debajo del
final. Y como la cabecera es un item, se queda arriba con todo lo demás.

Arreglado con `rememberLazyListState` + `scrollToItem(0)` al cambiar de `docId`.
**Se pierde volver a donde estabas al subir de carpeta**, y se acepta: recordar
la posición de cada nivel es un mapa más que mantener, y llegar arriba del todo
es lo que hace cualquier explorador de ficheros.

**La lección:** un estado que sobrevive a un cambio de contenido —scroll,
selección, página— tiene que reiniciarse con la clave de ese contenido, o el
día que el contenido nuevo sea más corto aparece un hueco. Es primo del `hueco`
del final de la lista: las dos veces, algo calculado para el tamaño de antes.

### Tercera tanda: la chapa de leído y "tu recorrido"

**La chapa circular de leído** sustituye a la capa negra al 70%. Con la rejilla
llena de cómics leídos la pantalla entera se apagaba y el catálogo dejaba de
parecer un catálogo. Ahora la portada se ve igual de bien y lo dice una chapa
que se lee de un vistazo, en vez de haber que comparar brillos entre cartas.

Cian con el tick en `Tinta` —negro sobre color, como el amarillo del acento—
porque sobre cian claro el blanco no se lee. Y **arriba a la izquierda**, para no
chocar con la chapa del número, que va a la derecha y es la otra cosa que se
mira de un vistazo.

**"TU RECORRIDO"**: ÚLTIMO / ACTUAL / SIGUIENTE en tres columnas. El último
terminado sale de la fecha del progreso, el actual de "seguir leyendo" y el
siguiente de `siguienteComic`, los tres en **una sola pasada** sobre el índice
que ya está en memoria — es una fila de la pantalla de inicio y no puede costar
tres recorridos del árbol.

**Repite el cómic de la tarjeta de arriba a propósito**, y esta vez la
duplicación se queda: son dos cosas distintas. La tarjeta es la **acción** —lleva
el progreso y es lo que tocas para seguir— y esto es la **secuencia**, que solo
se entiende si el del medio está. Sin él, las otras dos portadas no dicen
respecto a qué son anterior y siguiente. (No era el caso de la barra flotante de
abajo, que decía exactamente lo mismo que la tarjeta, y por eso se quitó.)

**Un hueco es un hueco.** Sin último —el primer día— o sin siguiente —el último
número de una carpeta— sale un recuadro vacío con una raya. Rellenarlo con algo
para que no quede feo sería mentir sobre lo que hay.

### Cuarta tanda: chips de filtro y las series que sigues (03/09/2026)

**Los chips de la rejilla** — Todos / Sin leer / Leyendo / Leídos, como la
Estantería de Mistbook. Tres decisiones dentro:

- **Llevan el recuento**, y eso es la mitad de para lo que sirven: *"Sin leer
  12"* contesta la pregunta sin tener que pulsarlo. Un chip que solo pone "Sin
  leer" te obliga a tocarlo para saber si merece la pena tocarlo.
- **Los grupos vacíos no salen**, y si solo hay un grupo no sale la fila entera.
  Un "Leídos 0" es un botón que no lleva a ningún sitio y ocupa lo mismo que uno
  que sí; y con toda la carpeta sin leer, un filtro es un control que no hace
  nada.
- **El filtro se reinicia al cambiar de carpeta.** Entrar en una serie y
  encontrarla filtrada por lo que elegiste en otra es de las cosas que más
  desconciertan, porque parece que faltan cómics.

El recuento y el filtrado salen de **una sola pasada** con `remember`, con el
`sello` en la clave porque marcar una página cambia a qué grupo pertenece un
cómic. Esto corre por cada repintado y la carpeta puede traer sesenta.

**La lista de series que sigues**, en Lecturas y no en Ajustes: seguir una serie
es una decisión de lectura, no un ajuste de la app. Hasta ahora solo se veían de
una en una entrando en la carpeta de cada una, así que **no había forma de dejar
de seguir algo sin ir a buscarlo**. Cada fila dice cuándo sale el siguiente,
reutilizando la misma frase que la tira de la serie.

La cruz de "dejar de seguir" va en `Tenue` y no en `Alarma`: no borra nada —el
registro de lo ya avisado se conserva— así que pintarla de rojo sería avisar de
un peligro que no existe.

### Quinta tanda: el calendario (03/09/2026)

El mes con las portadas de lo que leíste cada día, en Lecturas. Era lo mejor que
tenía Mistbook y aquí sale casi gratis: las miniaturas ya están en caché para
pintar el catálogo.

**Lo que se ve es la PORTADA, no un punto.** Un calendario con marcas de colores
dice cuánto has leído; uno con portadas dice **qué** leíste, y eso es lo que hace
que te pares a mirarlo. Es toda la diferencia entre una gráfica y un recuerdo.

Decisiones que van con ello:

- **No se puede pasar del mes actual.** Un calendario de lectura no tiene futuro,
  y una flecha que lleva a doce casillas vacías es una flecha rota.
- **El número lleva chapa oscura debajo**, no solo color: una portada puede ser
  blanca y el número tiene que leerse sobre cualquiera.
- **Si ese día leíste más de uno, se dice** con un "+2" en la esquina. Solo se ve
  la portada de uno —el último— y callar los otros sería contar de menos.
- **Las semanas empiezan en lunes**, que es como se lee un calendario aquí, y las
  calcula `Calendario.semanas` **fuera de Compose**: es aritmética con dos casos
  de borde (el mes que empieza en domingo y el que necesita seis filas) y eso se
  prueba mejor donde se puede probar. `CalendarioTest`, con noviembre de 2026
  —que empieza en domingo— y un febrero bisiesto.

**Lo que el calendario no podía saber, y ya sabe.** La primera versión salía de
`Marca.cuando`, que es *la última vez que tocaste ese cómic*: un tomo leído en
tres tardes aparecía solo el último día. Quedó escrito aquí como limitación —"para
lo otro haría falta una fila por sesión de lectura, que es otro fichero y otra
decisión"— y **fue justo lo siguiente que pidió Dani**, esa misma tarde. Ahora
existe ese fichero (`datos/Sesiones.kt`) y el calendario sale de él.

**Tocar un día abre el detalle**, y ahí va la frase que pidió: *"leídas páginas
3-4"*. En grande el **tramo** (`Calendario.Leido.tramo`), y debajo en pequeño
cuántas fueron nuevas. Son dos cifras que **no tienen por qué cuadrar** —releer
no suma, así que un tramo de la 3 a la 40 pueden ser 12 páginas— y por eso van
las dos: el tramo dice *qué* leíste, el total dice *cuánto*. Enseñar solo una
sería mentir por omisión, que es la regla de siempre del proyecto aplicada a un
sitio pequeño.

**Y de paso, una incoherencia arreglada:** `Racha.dia()` usaba la zona horaria
del móvil y todo lo demás usa `Novedades.ZONA` (España). La racha y el calendario
salen en la **misma pantalla**, así que en cuanto Dani cruzara un huso podrían
discrepar en un día — y dos cifras de la misma pantalla que no cuadran se leen
como un fallo. Ahora las dos usan el mismo calendario.

### Y con esto se acaba lo de Mistbook

Entró: la barra en píldora con la pestaña activa en cápsula, la cabecera con
saludo y racha, "seguir leyendo" en horizontal, ÚLTIMO/ACTUAL/SIGUIENTE, la chapa
circular de leído, los chips de filtro con recuento, y el calendario.

No entró, a propósito: su tipografía serif (2077 va de monoespaciada), los
degradados pastel, y todo lo social. Lo que se copió fue la **estructura**, no la
piel.

---

## 17. El icono de la app (03/09/2026)

Hasta hoy la app salía en el lanzador con el robot verde por defecto: el
manifiesto no declaraba icono ninguno.

**Un bocadillo de cómic**, en vector, con el chaflán de 2077. Por qué un
bocadillo: tiene que decir "cómics" a 48 dp y sin texto. Un libro vale para
cualquier lector, unas gafas no dicen nada, y una portada concreta ata el icono a
una serie. El bocadillo solo significa una cosa.

**Negro sobre amarillo, no al revés.** Es la regla de la casa: *el amarillo
ácido como mancha, no como detalle, y el texto encima va en negro*. Y además un
fondo amarillo se ve sobre cualquier fondo de pantalla; uno negro se funde con la
mitad de ellos.

**Los tres puntos dentro no son adorno.** A tamaño pequeño la mancha negra sola
parece un borrón; los puntos la rompen y de paso leen como "aquí hay algo que
leer".

### La zona segura manda sobre el tamaño

Un icono adaptativo es un lienzo de 108 y **el lanzador lo recorta con la máscara
que le dé la gana**: círculo, cuadrado o gota. Lo único garantizado es el círculo
central de 66 de diámetro — radio 33 desde (54,54).

La primera versión se salía por 8 unidades y en un lanzador redondo habría
perdido la esquina de arriba a la izquierda del bocadillo. La buena tiene **todas
las esquinas dentro de ese radio**; la más lejana está a 32,6.

### En vector y no en PNG, y con versión monocroma

Vector: escala a cualquier densidad, pesa nada y no hay que exportar seis
tamaños. Y como el `minSdk` es 26, **los iconos adaptativos funcionan en todas
las versiones que la app soporta**, así que no hace falta ni un PNG de respaldo
en `mipmap-hdpi` y compañía.

El `<monochrome>` es para los iconos con tema de Android 13, donde el sistema lo
recolorea con la paleta del fondo de pantalla. Es el mismo bocadillo pero
**macizo, sin los puntos**: al recolorearlo todo del mismo color, los puntos
desaparecerían y quedaría una silueta con agujeros invisibles.

---

## 18. Las secciones se pasan deslizando (03/09/2026)

Dani: *"me gustaría poder pasar entre el inicio, la biblioteca y así deslizando
hacia el lado"*.

**Es un cambio de estructura disfrazado de gesto.** Biblioteca, Lecturas y
Ajustes eran tres pantallas apiladas una encima de otra, y por eso ninguna de
las dos cosas que se esperan de unas pestañas funcionaba: no se podían pasar
deslizando, y "Atrás" desde Lecturas te devolvía a la biblioteca como si
hubieras entrado en un submenú. Ahora son **tres páginas de un carrusel** y solo
hay un destino de navegación: el visor y los marcapáginas, que sí tapan, siguen
apilándose.

Lo que eso cambia en pantalla:

- **La barra de pestañas no se mueve.** Va fuera del carrusel, flotando abajo
  como hasta ahora, y la cápsula marcada la decide la página en la que estás —al
  arrastrar, se mueve con el dedo en vez de saltar al soltar.
- **Fuera el "Atrás" de Lecturas y de Ajustes.** Una flecha que no lleva a
  ninguna parte confunde más que la falta de flecha, así que `Cabecera` se salta
  la fila entera cuando no hay a dónde volver. La biblioteca sí la conserva
  dentro de una carpeta, porque ahí sí se sube un nivel.
- **El gesto de atrás sigue el mismo orden que el ojo**: dentro de un personaje
  en Lecturas vuelve al listado; en Lecturas o Ajustes vuelve a la biblioteca; en
  la biblioteca sube de carpeta; en la raíz, sale de la app.

**La pega, dicha en claro:** las filas de portadas también se desplazan en
horizontal. Arrastrando encima de una, el carrusel de portadas se queda el gesto
y la pestaña **no cambia hasta que esa fila llega a su tope**. Es el
comportamiento normal de Compose —el hijo que puede desplazarse consume primero—
y darle la vuelta significaría estropear los carruseles, que se usan más. Por
cualquier otra parte de la pantalla el deslizado entra a la primera.

---

## 19. Cuatro comodidades (03/09/2026)

Dani: *"¿qué más cosas de QoL podemos poner?"*. Lo que se metió y las decisiones
de pantalla que llevan dentro.

### Ir a la página N: el número pide un número

En la barra del visor, el título y el contador estaban en la misma columna y
hacían lo mismo: abrir la tira de miniaturas. Ahora se reparten — **el nombre
abre la tira, el número abre "ir a la página"**. Es el reparto obvio: tocas un
número, escribes un número. Y no hace falta enseñar ninguna pista, que es la
regla del §14: el sitio donde se escribe un número es el sitio donde pone un
número.

**El botón "Ir" sale apagado mientras lo escrito no valga.** Es la única señal
de que 900 no existe en un cómic de 22, y llega **antes** de pulsar, no después
con un mensaje de error. Un diálogo que no puede fallar no necesita explicar
nada.

Teclado numérico, no el normal: son cuatro toques menos y, sobre todo, no deja
escribir letras que luego habría que rechazar.

### Las opciones de la carpeta viven en la carpeta

El orden y el "marcar todo" son la misma clase de cosa —**van de esta carpeta
entera**— así que comparten hoja, y la hoja se abre desde la fila que ya
encabeza la rejilla: `Cómics ·  Nº  ›`.

Dos cosas en un sitio, y por eso funciona: **el rótulo de la derecha dice cómo
están ordenados ahora** (se ve sin abrir nada) y el chevron invita a tocar, que
es el mismo patrón de "ver todo" de iOS que ya usa el resto del catálogo. Meter
el orden en Ajustes lo habría dejado a tres pantallas de donde se usa.

**Solo se ofrece lo que cambia algo:** en una carpeta ya entera leída, "marcar
todos como leídos" es un botón que no hace nada, así que no sale. Igual que los
chips de filtro, que solo aparecen si hay algo que separar.

**Y el diálogo dice la consecuencia, no la pregunta.** No "¿estás seguro?", sino
*"los que tengas a medias perderán la página por la que ibas"*. Es lo único que
no se deshace volviendo a pulsar, y es exactamente el caso del §14 donde sí toca
explicar: antes de una acción que borra.

### Abrir un cómic que llega de fuera

No pasa por la biblioteca ni la toca: se abre en el visor y ya. **Funciona
aunque no haya carpeta elegida**, porque el visor es un destino aparte —instalar
la app y abrir un CBZ que te acaban de mandar tiene que funcionar a la primera,
sin montar antes toda la biblioteca.

Y **no aparece en "En curso", ni en el calendario, ni en las estadísticas**, aunque
el visor guarde el progreso como con cualquier otro. No hace falta ninguna
excepción para eso: las tres cosas cruzan por uri contra tu biblioteca y ese
fichero no está en ella. Sale bien porque la uri que da otra app es de un solo
uso —mañana ya no vale— y un cómic de paso conviene que se quede de paso.

---

## 20. Cinco comodidades más (03/09/2026)

### El botón dice cuál es, no "continuar"

En la carpeta de una serie, encima de la rejilla: **`▶ Sigue por el #7`**, y
debajo en pequeño *"por la página 12 de 24"* si lo tienes a medias.

**Poner el número en vez de "continuar" es la decisión.** Un botón que pone
"continuar" obliga a pulsarlo para saber qué hace; con el número, la mitad de
las veces ya no hace falta pulsarlo — solo querías saber por dónde ibas. Y la
segunda línea solo sale cuando cambia lo que va a pasar: que no abre por la
primera página, sino por la 12.

Solo dentro de una carpeta. En la raíz eso ya lo contesta la tarjeta de "seguir
leyendo", que además habla de toda la biblioteca y no de un sitio.

### Deshacer, y por qué lleva una X aparte

Tras marcar treinta cómics de golpe, una píldora encima de las pestañas: *"30
marcados como leídos · Deshacer · ✕"*.

**La X no sobra.** Sin ella, la única forma de quitar el aviso de en medio sería
deshacer, que es justo lo contrario de lo que quieres si lo estás leyendo para
confirmar que salió bien.

Siete segundos, y el temporizador vive en el ViewModel: si viviera en la
pantalla, cambiar de pestaña lo reiniciaría o lo mataría y el aviso duraría lo
que le apeteciera.

### Dos chips para decir dónde se busca

El buscador siempre miró toda la biblioteca, y eso está bien de partida — es lo
que quieres el 90% de las veces. Pero dentro de una serie, buscar "01" devuelve
cuarenta resultados de toda la casa.

Los dos chips (**En «Absolute Batman»** / **En todo**) tienen el mismo aspecto
que los del filtro de la rejilla, y eso es lo que hace que se entiendan sin
explicar nada: en esta app, **una chapa amarilla es "lo que está puesto ahora"**.
Solo aparecen dentro de una carpeta, y se reinician al cambiar de carpeta, igual
que el filtro y por lo mismo.

### La barra de progreso, y el globo pegado al dedo

La raya de abajo del visor pasa a ser arrastrable. Tres decisiones de pantalla:

- **La zona táctil es de dedo aunque la raya sea fina.** 3 dp es imposible de
  acertar: la caja que recoge el toque mide 16, y sube a 22 mientras arrastras.
  La raya también engorda de 3 a 6 dp, que es la señal de "te tengo".
- **El globo con el número va pegado a donde está el dedo, no centrado.** Con la
  mano tapando un borde, un globo centrado se ve; uno en el borde contrario, no.
- **Solo se viaja al soltar.** Mientras arrastras, la barra enseña a dónde irías
  y el globo dice el número, pero la página no cambia. Con 500 páginas, seguir
  el dedo serían medio millar de decodificaciones — y además así puedes pasarte
  y corregir sin haber ido a ninguna parte.
- **Separada del borde, no pegada a él.** Dani, al probarla: *"funciona bien
  pero siento que la barra está como muy abajo"*. Debajo ya estaba el hueco de
  la barra de gestos, pero eso solo evita que la raya caiga **encima**: seguía
  quedando dentro de la franja donde el sistema se queda los deslizamientos para
  ir a inicio, que es la peor zona posible para algo que ahora se arrastra. Y
  hay una razón de diseño además de la práctica: un control pegado al borde se
  lee como *el final de la pantalla*, no como algo que se pueda tocar. Es el
  mismo motivo por el que la barra de pestañas de la biblioteca flota.

### Guardar o compartir una página

Pulsación larga en el visor. El hueco estaba libre: era el único gesto de esa
pantalla que no hacía nada.

**Mientras la imagen se prepara, las opciones salen apagadas y no hay ni una
línea de texto.** Es el §14 llevado al detalle: un control apagado ya dice
"espera" sin prosa, y la única frase que aparece es la de después —"Guardada en
la galería"— o la de cuando algo falla.

**En su propio álbum, "Lector".** Una página de cómic suelta entre las fotos del
móvil es justo lo que hace que la gente desactive estas cosas.

---

## 21. "Próximamente", en Lecturas (03/09/2026)

Encima de la lista de series que sigues, y **antes** que ella a propósito: la
lista de seguidas es la misma toda la semana y esto cambia solo, así que lo que
caduca va arriba.

**La fecha a la derecha y en acento**, porque es la columna que se lee: la lista
está ordenada por ella, así que el ojo baja por ese lado. El nombre de la serie
va arriba y el número debajo, no al revés, porque con quince filas lo que buscas
es tu serie, no el número.

**"aproximada" solo cuando lo es.** Es la regla de siempre del proyecto —decir de
dónde sale el dato— con el mínimo de letra posible: una palabra, en gris, y solo
en las filas donde Comic Vine no traía la fecha de venta y ha habido que
calcularla. Las demás no dicen nada, que es lo correcto: lo normal no se explica.

**Y no aparece si no sigues nada.** Un titular de sección sobre una lista vacía
es un hueco que hay que justificar; sin series seguidas, la sección entera no
existe.
