# Lector de cómics — cómo trabajar en este proyecto

App Android (Kotlin + Compose) para leer CBZ/CBR de una biblioteca local y saber
qué falta de cada serie. Proyecto personal de Dani, no se publica en ninguna
tienda.

---

## LEE ESTO ANTES DE TOCAR NADA

```
docs/CONTEXTO.md    Qué hace, cómo está montado, y TODAS las trampas
                    encontradas — con los errores cometidos y por qué.
docs/DISENO.md      Las decisiones de aspecto y de interfaz, y qué NO se
                    puede hacer (y no es por pereza).
```

Son largos a propósito. **No son documentación de cortesía: son la única cosa
que sobrevive a una conversación**, y casi todo lo que hay dentro se aprendió
por las malas. Antes de diseñar cualquier parche, busca el tema en
`docs/CONTEXTO.md` — es muy probable que ya esté escrito, con el intento que
falló incluido.

Si tienes poco contexto, lee por lo menos: §5 (trampas), §6 (lecciones de
método) y §7 (estado y pendientes) de `docs/CONTEXTO.md`.

---

## Cómo hablarle a Dani

- Español, tuteo, directo, sin preámbulos.
- Sabe programar, pero **Android y Compose son nuevos para él**. Explica lo de
  Android; no expliques lo que es una función.
- **Avisa de los problemas antes de que aparezcan, no después.** Si algo de lo
  que pide tiene una pega, se dice al entregarlo, no cuando la descubra él.
- Le vale más un "esto no lo he podido comprobar" que una seguridad inventada.

## Cómo escribir el código

- **Nombres de variables, funciones y clases en español.**
- Los comentarios explican **por qué**, no qué. Un comentario que repite el
  código sobra; uno que dice qué se intentó antes y por qué no valía, no.
- **Tandas cortas y compilar entre medias.** No hay tests de interfaz: el
  Compose va sin red de seguridad.
- **Lo que decide algo va en una función pura, en `datos/`, con su test al
  lado.** Trece ficheros de test en `app/src/test/`. La regla vale sobre todo
  para reglas con casos de borde o con orden de prioridad: se rompen sin dar
  ningún error, simplemente hacen lo que no era.

## Antes de dar nada por terminado

```bash
python3 comprobar.py        # en la raíz del módulo. Tiene que decir PROBLEMAS: 0
```

Mira llaves y paréntesis sin cerrar y, sobre todo, **cuerpos huérfanos** —
código que se queda suelto cuando un borrado corta por en medio de una función.
Ese fallo no descuadra nada y no se ve leyendo; solo lo caza Gradle, y llegó dos
veces al móvil de Dani por no tener esto.

Y cruza los imports: **`private` a nivel de fichero es de FICHERO, no de
paquete.** Dos ficheros del mismo paquete no se ven las funciones privadas del
otro.

## Regla fija: los documentos se actualizan en la MISMA tanda

Cada cambio del proyecto actualiza `docs/CONTEXTO.md` y `docs/DISENO.md` a la
vez que el código. No al final, no cuando se acuerde alguien. Qué se hizo, por
qué se decidió así, con qué se comprobó — **y lo que no se comprobó, también**.

---

## El principio rector

> **Los datos, de la base de datos. El criterio, del modelo.**

Se llegó a esto a base de fallos: un modelo se inventa cifras con total aplomo.
**Y el 02/09/2026 la regla se llevó a su conclusión: fuera el modelo.** Se
fueron Gemini y las wikis de Marvel y DC, que eran las dos únicas partes donde
algo opinaba.

Hoy queda **una sola fuente, Comic Vine**, y de ella solo se sacan cosas
contables: qué volumen es cada carpeta, qué números tiene, y en qué fecha
salieron. Lo que sí se mantiene, y es lo que más valor tiene: **cada cifra de la
pantalla dice de dónde sale**.

## Dos plataformas: `:app` y `:shared` (desde el 03/09/2026)

Dani quiere la misma app en su iPad. **La decision: Kotlin Multiplatform en el
MISMO repositorio**, porque la app ya es Compose al 100% y eso hace que Compose
Multiplatform reaproveche lo que hay en vez de tirarlo.

```
shared/     commonMain — lo que no sabe de ninguna plataforma
app/        Android. Sigue siendo la app que funciona hoy.
iosApp/     (todavia no existe)
```

**Los objetivos de Apple solo se declaran si el anfitrion es un Mac**
(`shared/build.gradle.kts`). Kotlin/Native no compila para iOS desde Windows, y
si se declararan siempre el proyecto **ni siquiera configuraria** en el
ordenador de Dani, que es donde se trabaja. En Windows se compila y se prueba
Android con normalidad; el runner macOS del CI es el que ve los targets de iOS.

**LO QUE ESTO SIGNIFICA AL TRABAJAR: nadie puede verificar iOS desde aqui.** Si
una tanda toca `iosMain`, se dice **"escrito, sin compilar"** y punto; quien
compila es el CI. Lo que si se verifica en cada tanda, como siempre, es que
Android sigue compilando y en verde.

**Regla de reparto**, para no discutirlo cada vez:

> Lo que decide algo va en `shared`. Lo que toca disco, red, pantalla o sistema
> se queda en su plataforma, detras de una interfaz o de un `expect/actual`.

Y `comprobar.py` mira **los dos modulos**. Se quedo ciego a `shared/` el dia que
se creo, que es justo cuando mas falta hacia.

## Cómo está montado

Sin Room y sin librerías de red: persistencia en JSON y red con
`HttpURLConnection`. Menos dependencias, menos que se rompa.

```
LectorApp.kt      Único sitio donde se decide de dónde salen los datos
MainActivity.kt   Navegación (3 destinos) y las teclas de volumen del lector
VistaModelo.kt    Estado y lógica de la interfaz

datos/  Modelos, Parser, Escaner, Formatos, Busqueda, Progreso, Marcadores,
        ComicZip, Rar5, ConversorCarpeta, Recorte, Miniaturas, ColorPortada,
        Racha, Sesiones, Calendario, Estadisticas, Huecos, EstadoSerie,
        SeriesRemotas, Novedades, Vigilante, Rastro, Orden, Salto, Siguiente,
        Exportar
red/    FuenteComics (interfaz) + ComicVine (única implementación)
ui/     Tema (tokens y el interruptor de estética), Componentes, Pantallas,
        Lector
```

**Todo lo externo va detrás de una interfaz**, para poder cambiar de proveedor
tocando una línea en `LectorApp`. Ya salvó el proyecto cuando hubo que abandonar
Metron.

## Claves de API

En `local.properties` (git lo ignora), y Gradle las mete vía `BuildConfig`:

```
comicvine.clave=...
```

También se pueden meter desde Ajustes; manda `local.properties`. Sin clave, la
biblioteca y el lector funcionan igual.

---

## Estado (3 de septiembre de 2026)

Todo lo de los últimos días **está escrito pero SIN COMPILAR NI PROBAR EN EL
MÓVIL** salvo donde se diga. Lo entregado ese día, por tandas:

1. Diario de lectura + calendario con tramos de páginas; icono de la app.
2. Las tres pestañas pasan a un carrusel que se pasa deslizando.
3. Ir a una página concreta; marcar leído sin abrir (uno y carpeta entera);
   ordenar la carpeta; abrir CBZ/CBR desde otras apps.
4. Barra de progreso arrastrable; guardar/compartir una página; "Sigue por el
   #7"; deshacer el marcado en bloque; buscar solo en esta carpeta.
5. "Próximamente": agenda de novedades de las series que sigues.

**Confirmado funcionando en el móvil**: la tanda 1 y 2, y la 3-4-5 compilan y
arrancan (Dani las probó); la barra de progreso se subió 18 dp a petición suya.

### La pasada de optimización: hecha la parte que no era adivinar (03/09/2026)

Dani pidió **"más liviano, más rápido, sin perder ninguna funcionalidad"**. Las
cuatro sospechas que había escritas se miraron una a una y salieron **tres
ciertas y una falsa**:

- `prefs` en `VistaModelo` era `get()`, o sea **una llamada a
  `getSharedPreferences` por cada uno de los ~20 accesos** de la clase, y `orden`
  se lee dentro de la lista de la biblioteca. Ahora es `by lazy`: **una línea, y
  arregla los veinte sitios**, no solo el que se había apuntado.
- `Novedades.hoy()` se llamaba suelta por fila en `Pantallas.kt:1009` y `:1712`.
  Metida en `remember`.
- `androidx.documentfile` era **dependencia muerta**: su única aparición era un
  comentario diciendo que no se usa. Fuera.
- `Rastro.apunta` **NO relee el fichero por miga** — solo poda al pasar de 36 KB.
  La sospecha era falsa.

**Y una pasada por el código entero**, que salió casi limpia: cuatro
declaraciones muertas (`carpetaOriginales`, `RojoLector`, `AzuliOS`,
`FormaMarca`), la lista de extensiones duplicada entre `ComicZip` y `Rar5`
unificada, y el último aviso del compilador arreglado — **`compileDebugKotlin` ya
no saca ni un `w:`**.

Lo que sí era un fallo de verdad: **`marcarCarpeta` reescribía `progreso.json`
entero una vez por cómic**, y `deshacerMarcado` igual. Arreglado en `Progreso`
con `tanda { }`, no en los dos llamadores. Sin prueba automática: `Progreso`
necesita `Context` y las trece pruebas del proyecto son de funciones puras.

**Y el peso, medido**: el APK de debug son 25,5 MB y **15,8 son
`lib7-Zip-JBinding.so`, el 62%**. Está ahí para convertir los CBR (RAR5 y RAR4
grandes) y quitarlo es perder los CBR. Por el lado del tamaño **ya no queda nada
barato**.

**Compila (`assembleDebug`) y las 138 pruebas pasan.** Dani instaló ese build y
**lo probó**: de ahí salió el aviso del tirón al pasar a Lecturas.

### El tirón al entrar en Lecturas por primera vez

`Progreso`, `Sesiones` y `SeriesRemotas` leían su JSON **la primera vez que
alguien preguntaba**, y esa primera vez caía dentro de los `remember` de
`PantallaEstadisticas`, o sea **en el hilo principal durante la composición**:
tres ficheros parseados de golpe justo en la animación del carrusel.

`VistaModelo.init` ahora llama a `precalentar()`, que los carga en
`Dispatchers.IO` al arrancar. Y los cuatro almacenes cierran la carrera de caché
con `return cache ?: m.also { cache = it }` — manda el que llegó primero, para
que un precalentado lento no pise una marca recién escrita.

**Queda medir, no adivinar**: `Estadisticas.calcular` sigue en el hilo principal.
Lleva cronómetro. El rastro escribe `fichas precargadas en N ms` y
`estadísticas de N cómics en N ms`; con esos dos números se decide si hace falta
moverlo a `Dispatchers.Default`. **SIN PROBAR EN EL MÓVIL todavía.**

Lo que **queda a medias, y a propósito**: el diagnóstico sobre `TarjetaComic` y
`FilaResultado` ("nunca se pueden saltar" por recibir el ViewModel) sigue sin
recomprobar. Kotlin es 2.0.21, el *strong skipping* está activado por defecto, y
puede que ya no sea verdad. **Eso se mide con el Layout Inspector, no se adivina**
— es la regla que costó una tanda entera aprender.

### Lo anterior, que ya está hecho

Dani pidió una **pasada de optimización**: *"que sea más liviano, más rápido,
sin perder ninguna funcionalidad, porque creo que no podemos meterle más
funcionalidades"*. El análisis se empezó y **no se terminó**. Lo que ya se sabe:

- **El build ya está optimizado**: `abiFilters` a solo `arm64-v8a` (se llevó
  ~40 MB) y R8 con `isShrinkResources` en release. Por ahí queda poco.
- **Kotlin 2.0.21**, así que el *strong skipping* del compilador de Compose está
  activado por defecto. Eso cambia el diagnóstico que hay escrito en
  `docs/CONTEXTO.md` sobre que `TarjetaComic` y `FilaResultado` "nunca se pueden
  saltar" por recibir el ViewModel: **hay que volver a comprobarlo antes de
  reescribir nada**, porque puede que ya no sea verdad.
- **Sospechas SIN VERIFICAR** (mirar el código antes de tocar):
  `Rastro.apunta` — comprobar si cada apunte relee o reescribe el fichero
  entero, porque se llama en cada cambio de pantalla y de pestaña;
  `VistaModelo.orden` lee SharedPreferences en cada recomposición de la
  biblioteca; `Novedades.hoy()` se llama por fila en algunas listas;
  la dependencia `androidx.documentfile` puede que ya no la use nadie
  (el escáner usa `ContentResolver` a pelo).

**La regla de este proyecto para el rendimiento, y ha costado aprenderla:
MEDIR, no adivinar.** Ya hubo una tanda entera de optimizaciones razonadas sobre
el código donde nadie sabe cuál pesaba de verdad. Con Android Studio delante hay
Layout Inspector (cuentas de recomposición) y el perfil de renderizado GPU.

### Pendientes de antes

- ~~Correr los tests~~ **HECHO (03/09/2026)**: 126 pruebas, una en rojo, y el
  fallo estaba **en la prueba** (`HuecosTest` esperaba dos "y" seguidas). Las
  otras 125 pasaron a la primera.
- ~~`elegirVolumen` sin pruebas~~ **HECHO**: `test/…/red/ElegirVolumenTest.kt`,
  12 casos con los datos reales del 25/08/2026 — nombre exacto, editorial
  mayoritaria, año exacto por delante del margen, margen de un año, dos años
  fuera, sin año, a igualdad la de más números, y la normalización.
- **Forzar el trabajo diario de notificaciones en el móvil** (Android Studio >
  App Inspection > Background Task Inspector). Hasta que no salte una
  notificación de verdad, es código que compila, no una función que funciona.
- **Ver cuántos números traen `store_date`** de verdad, y si `DESFASE_ESPANA = 0`
  acierta.
- ~~El proyecto NO está en git~~ **HECHO**: `git init` y commit `83b29d3`, 177
  ficheros, sin remoto. `local.properties` queda fuera, lo cubre el `.gitignore`.
  En `_borrar_a_mano/` sigue el código amputado (orden de lectura y el TODO por
  personaje): ahora ya no es la única copia, pero tampoco está en el historial.
