# Por dónde seguir

Escrito el 04/09/2026 para retomar el trabajo en otra conversación. **Se lee en
dos minutos**; el detalle de cada cosa está en `docs/CONTEXTO.md`, que es largo a
propósito.

> Si estás empezando: lee antes `CLAUDE.md`, y de `docs/CONTEXTO.md` al menos
> §5 (trampas), §6 (lecciones de método) y §7 (estado y pendientes).

---

## Donde se paro el 07/09/2026

**DANI HA DECIDIDO PORTAR LA APP ENTERA A iOS ANTES DE INSTALAR NADA.** Lo dijo
asi: se adapta todo, luego se instala el `.ipa`, y **solo si no va** se empieza a
toquetear. Eso cambia el orden que tenia escrito este documento: la sonda del
`.ipa` deja de ser el siguiente paso y pasa a ser el examen final.

**Se trabaja con dos sesiones que escriben —Paco y Lucia— y una que reparte,
revisa y commitea.** Reparto por ficheros disjuntos, cero colisiones. Lo que hay
que saber esta en las tandas 25 y 27 de `docs/CONTEXTO.md`; lo mas importante:
**leer un fichero mientras el agente lo edita da una foto a medio editar**, y por
poco se le acusa en falso a Paco. Se compila antes de decirle a nadie que su
codigo esta mal.

| Commit | Que |
|---|---|
| `9024ae0` | `comprobar.py` ya no es ciego a los comentarios descuadrados |
| `d1fdbd7` | `dominante` partida en dos y ocho pruebas que la sujetan |
| `e4f4609` | `CLAUDE.md` a la mitad. **CI verde** |
| tanda 27 | `Rastro` a `commonMain` + `Disco.anadir` + `RecorteIOS` |

**Tres decisiones que estaban abiertas, ya cerradas por Dani:**

- **`Rastro`: global con un `Disco` dentro**, no instancia. Hecho en la tanda 27,
  con el porque dentro del propio fichero.
- **El motor de RAR para iOS: SI, pero DESPUES.** Hay via (libunrar de RARLAB,
  licencia aceptable), son tres tandas y la primera es a ciegas. El informe
  entero esta en `CONTEXTO.md`. Su caso —*"si meto un CBR en la carpeta de la
  nube y lo abro primero con el iPad"*— se resolvera **leyendo el CBR directo**,
  no convirtiendolo: convertir obligaria ademas a escribir ZIP, que hoy no
  existe.
- **`Vigilante` en iOS: notificacion local** con `UNUserNotificationCenter`, no
  "solo Android". Pendiente, va en la fase 2.

## Lo primero, en cuanto entres

**Mirar el CI del último commit.** Todo el trabajo de iOS se escribe a ciegas
—desde Windows no hay Kotlin/Native— y el único juez es el runner de macOS:

```bash
gh run list --limit 1
```

Desde la tanda 24 el trabajo de iOS **deja un `.ipa` como artefacto**. Si está
en verde, hay algo instalable. Compilar no es funcionar: nadie lo ha arrancado.

---

## La tarea siguiente: **portar la interfaz**

Es la mitad del trabajo real y no se ha empezado: 3.228 lineas de `ui/` + 1.204
de `VistaModelo` + 713 de `MainActivity`, todas en `:app`.

**La buena noticia, medida y no supuesta:** `shared/` ya tiene Compose
Multiplatform montado y ya viven ahi `ui/Tema`, `Colores`, `Componentes` y
`Portada`. Y de todos los imports de `ui/`, **casi todos son `androidx.compose.*`,
que en CMP son los mismos**. Lo que ata a Android es poco y esta localizado:

| Fichero | Lo que ata a Android |
|---|---|
| `PantallaMarcadores` (92) | nada, ya es portable |
| `PantallaEstadisticas` (498) | solo `BackHandler` |
| `PantallaAjustes` (350) | selector SAF + `LocalContext` |
| `PantallaBiblioteca` (1.306) | permiso de notificaciones + `LocalContext` |
| `Lector` (979) | `Intent` de compartir, `asAndroidBitmap`, `WindowCompat` |
| `MainActivity` (711) | cascara + navegacion + `BackHandler` |
| `VistaModelo` (1.213) | `AndroidViewModel(Application)` y cuatro piezas |

**Las fases, en este orden y una por tanda:**

1. ~~`Rastro` a comun~~ **HECHO (tanda 27)**, y `RecorteIOS` con el.
2. `Vigilante` detras de una interfaz de avisos + la notificacion local de iOS, y
   `ConversorCarpeta`/`Rar5` detras de otra.
3. **`VistaModelo` a `commonMain`. Es EL tapon**: las cuatro pantallas lo reciben
   por parametro, asi que mientras siga en `:app` no se puede mudar ninguna.
4. Las pantallas, de menor a mayor riesgo: `Marcadores` -> `Estadisticas` ->
   `Ajustes` -> `Biblioteca` -> `Lector`.
5. `MainActivity` se parte: raiz Compose comun + cascara de Android +
   `PuntoDeEntradaIOS`.
6. **Entonces** se instala el `.ipa` (abajo esta como se hace), y despues el
   motor de RAR.

**La pega, dicha ahora y no cuando se descubra:** todo lo de iOS de las fases
1-5 se escribe a ciegas desde Windows y solo lo juzga el CI de macOS. Compilar no
es funcionar. Portar entero antes de instalar significa acumular mucho sin
ejecutar, asi que **cada fase tiene que dejar el CI en verde** para no acabar
depurando diez cosas a la vez.

## Cuando toque instalar: el `.ipa` en el iPad

No es una tanda de codigo. Es el paso que convierte "compilan" en "funcionan", y
**solo lo puede dar Dani**.

1. Bajar el artefacto `lector-ipa` de la ultima ejecucion verde del CI
   (pestaña Actions > la ejecucion > Artifacts). Es un zip con `Lector.ipa`.
2. Sideloadly en el PC, iPad enchufado, su Apple ID. **Con cuenta gratuita la
   app caduca a los 7 dias** y se refresca reconectandola.
3. En la app Archivos del iPad: *En mi iPad > Lector*, y meter ahi un CBZ.
4. Abrir la app.

| Lo que se ve | Donde mirar |
|---|---|
| "No hay ningun CBZ en Documents" | `UIFileSharingEnabled`, o el fichero no llego |
| Sale la lista, pero la pagina no | `ZipIOS` (descomprimir) o `ImagenIOS` (decodificar) |
| Un mensaje de error | Es el `Paginas.Error`, y ya dice el motivo |
| La app se cierra sola | Memoria: iOS mata sin avisar y no hay excepcion que ver |

**Lo mas fragil, y no lo puede ver el compilador:** en iOS la opcion
`withSecurityScope` de los marcadores es de macOS. `BibliotecaIOS` resuelve sin
opciones y pide el acceso despues, que es como funciona en el iPad. **Si al
probarlo no deja abrir los ficheros, ese es el primer sitio donde mirar.**

## El mapa del port, con el marcador de verdad

El tapon es `VistaModelo`: **las cuatro pantallas lo reciben por parametro**, asi
que mientras siga en `:app` no se puede mudar ninguna.

| | Estado |
|---|---|
| preferencias | HECHO (tanda 13) |
| `ComicZip` a `Archivo` | HECHO (tanda 16) |
| `Escaner` a `Biblioteca` | HECHO (tanda 17) |
| `Miniaturas` a `Portadas` | HECHO (tanda 18) |
| `ArchivoIOS` — leer un CBZ en el iPad | HECHO (tanda 22), **compila a la primera** |
| `BibliotecaIOS` — marcadores y NSFileManager | HECHO (tanda 23) |
| `iosApp/` + framework + `.ipa` en el CI | HECHO (tanda 24), a la primera |
| `ColorPortada` | HECHO (tanda 25) |
| `Rastro` (26 llamadas en 9 ficheros) | HECHO (tanda 27), global con `Disco` dentro |
| `RecorteIOS` | HECHO (tanda 27), escrito sin compilar |
| `Vigilante` (1) | **decidido**: notificacion local de iOS. Pendiente, fase 2 |
| `ConversorCarpeta` (3) y `Rar5` (2) | **aplazados a despues del `.ipa`** — libunrar, tres tandas |
| `AndroidViewModel(Application)` | pendiente, fase 3. Son **16 usos de `ctx`**, y 6 son piezas que ya estan detras de interfaz |

## Lo que hace falta para mudar la interfaz (sondeado el 07/09/2026)

Contado sobre el codigo, no de memoria. Base de hoy: **Kotlin 2.0.21, Compose
Multiplatform 1.7.3, AGP 8.7.2, lifecycle (JB) 2.8.4, coroutines 1.9.0**, y no
hay catalogo de versiones: todo va a pelo en los `build.gradle.kts`.

**Una sola dependencia nueva, y ninguna mas:**
`org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4` — la misma
version que el `lifecycle-runtime-compose` que ya esta. Trae `ViewModel`,
`viewModelScope` y el `viewModel()` de Compose en `commonMain`. **Se prefiere a
una clase con su propio `CoroutineScope`** porque `viewModelScope` sale 17 veces
en `VistaModelo` —esas 17 no se tocan— y porque en Android un `ViewModel`
sobrevive a la rotacion y una clase recordada en la composicion no: cambiarlo
seria una regresion de Android para ahorrar una linea de Gradle.

**La navegacion se QUITA, no se muda.** Existe
`org.jetbrains.androidx.navigation:navigation-compose:2.8.0-alpha10` para CMP
1.7.x y no choca, pero son **tres destinos**, y este proyecto ya se peleo dos
veces con esa pila: la pantalla negra del 03/09 salia de dejar el `NavHost` sin
destinos, y el remedio fueron los dos cerrojos `enPie()` e `ir()`. Con un `when`
sobre una pila en el modelo, esa clase de fallo desaparece y se van esas tres
funciones. Ademas el deslizamiento entre pestañas ya no es navegacion desde la
tanda 18: es un `HorizontalPager`. **Lo que se pierde es el gesto de volver
deslizando desde el borde**, que en iOS se da por hecho; si se quiere desde el
dia uno, entonces si vale la alpha10.

**Los siete agujeros de plataforma**, ninguno es Compose: todos son el sistema
operativo, que es justo donde este proyecto ya pone interfaces.

| Que | Usos | Que hay que escribir |
|---|---|---|
| `BackHandler` | 6 | `expect/actual`. **En iOS no hay boton ni gesto de sistema**, asi que el `actual` no hace nada y los dos casos (salir del zoom, subir de carpeta) necesitan control en pantalla |
| `LocalContext` | 7 | Casi todos se van solos al inyectar las piezas |
| `asAndroidBitmap` + guardar en galeria + compartir | 6, en `ui/Lector.kt` | Interfaz nueva. En iOS: `Image.encodeToData` de Skia + `UIImageWriteToSavedPhotosAlbum` y `UIActivityViewController`. **Y `NSPhotoLibraryAddUsageDescription` en el Info.plist, o iOS mata la app al guardar** |
| Barras del sistema (`WindowCompat`) | 6, en `ui/Lector.kt` | En iOS no es de Compose: `prefersStatusBarHidden` del `UIViewController`, expuesto desde `iosApp/` |
| Selector SAF | 8 | `UIDocumentPickerViewController` + marcador. `BibliotecaIOS` ya resuelve marcadores, **pero el selector no existe**: es lo que mas bulto tiene de la lista |
| Permiso de notificaciones | 1 | `UNUserNotificationCenter.requestAuthorization` |
| `viewModel()` / `Application` | 1 | Lo cubre la dependencia de arriba |

**EL UNICO CHOQUE DE VERSIONES DE VERDAD:** el `BackHandler` comun
(`androidx.compose.ui.backhandler`) llega en **CMP 1.8.0**, y CMP 1.8.0 exige
**Kotlin 2.1.0 como minimo**. Eso es una tanda propia con su vuelta de CI y **no
se cuela dentro del port**: con 1.7.3 se tapa con un `expect/actual` de diez
lineas.

---

## Cuánto falta para un `.ipa` que Dani pueda instalar

Dani sigue el avance por aquí, así que **cada tanda de iOS actualiza esta tabla**.
El objetivo es un `.ipa` que entre en el iPad con Sideloadly.

| | |
|---|---|
| Lógica portable en `commonMain` | ✅ 4.425 líneas, con pruebas |
| Piezas de plataforma: `Disco`, `Zip`, `Imagen`, `Archivo`, `Biblioteca` | ✅ escritas, **ninguna ejecutada nunca** |
| `PortadasIOS`, `ColorPortada`, `Vigilante` | ⏳ dos de tres (tanda 25). Falta `Vigilante`: **decidido**, notificación local de iOS, va en la fase 2 |
| `Rastro` y `RecorteIOS` | ✅ tanda 27. `Rastro` global con `Disco` dentro; `RecorteIOS` escrito sin compilar |
| Motor de RAR en iOS | ⏸ **aplazado a después del `.ipa`**, decidido por Dani. No cuenta para este % |
| La interfaz a Compose Multiplatform | ❌ 3.228 líneas de `ui/` + 1.204 de `VistaModelo` + 713 de `MainActivity`, en `:app` |
| `iosApp/` — proyecto de Xcode (XcodeGen) | ✅ |
| CI que empaqueta el `.ipa` sin firmar | ✅ artefacto `lector-ipa`, 10,1 MB |
| **Que alguien lo instale y arranque** | ❌ **el paso que falta ahora** |

**~49%.** La tanda 24 subió diez de golpe porque tachó las dos filas que no eran
código sino tubería. La 25 subió dos (`ColorPortada`, `PortadasIOS`) y la 27
**otras dos, y no más**: tacha `Rastro`, que era una fila entera, y `RecorteIOS`,
pero ninguna de las dos es la interfaz, que sigue a cero. Sigue siendo más bajo de lo que dirían las líneas:
**lo que queda es donde está todo el riesgo.** La mudanza de la interfaz es la
mitad del trabajo real y no se ha empezado, y **nada de esto ha arrancado nunca
en un iPad**.

Y de ese 49, **la parte de datos y el empaquetado ya están**: lo que falta es la
interfaz de verdad — y comprobar que lo escrito funciona en un iPad.

**Un `.ipa` no se genera desde Windows** — hace falta Xcode. **Y no hace falta
tener un Mac**: desde la tanda 24 el runner de macOS empaqueta uno sin firmar en
cada push, y Sideloadly lo firma con la cuenta de Apple de Dani al instalarlo
(gratis, caduca a los siete días).

## Pendiente de Android, que sólo puede hacer Dani

- **Pulsar el botón de limpiar la biblioteca sobre una carpeta de la que haya
  copia.** Las reglas tienen catorce pruebas desde la tanda 11, pero el camino
  entero —contar páginas, renombrar y borrar con SAF— no lo ha recorrido ningún
  fichero de verdad.
- **La notificación diaria**, que nunca ha saltado. Se fuerza desde Android
  Studio: *App Inspection > Background Task Inspector*.
- Ver cuántos números traen `store_date` de verdad, y si `DESFASE_ESPANA = 0`
  acierta.

Todo lo demás de Android está confirmado por él hasta el 04/09/2026 inclusive.

---

## Las trampas de Kotlin/Native que ya han costado vueltas de CI

**Lo difícil compila; lo que falla es cómo se escribe el nombre de algo del
sistema.** Tres tandas seguidas igual:

- zlib, `inflateInit2_`, `ZLIB_VERSION` y la ventana `-15`: a la primera.
  Falló **un `import`**: los métodos de una *category* de Objective-C son
  funciones de extensión y **se importan uno a uno**
  (`fileHandleForReadingAtPath`).
- ImageIO, `CFDataCreate`, `CGBitmapContext`, `Image.makeRaster` y
  `toComposeImageBitmap`: a la primera. Falló **una constante**: las de
  CoreGraphics viven dentro de su enumeración y hay que pedirles `.value`
  (`CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value`).
- `BibliotecaIOS`: marcadores, `NSFileManager` y los punteros de salida, a la
  primera. Falló **un dispatcher**: con coroutines 1.9.0 `Dispatchers.IO` es
  **`internal` en Kotlin/Native**. Se escribe por reflejo porque en la JVM existe
  y `Escaner` lo usa. Fuera de la JVM va `Dispatchers.Default`.

Y **`@Volatile` sin `import kotlin.concurrent.Volatile`**, que ya ha pasado
DOS veces (`PortadasIOS` en la 25, `Rastro` en la 27): en la JVM se resuelve
solo, por el import implicito de `kotlin.jvm`, y en Kotlin/Native no.

Y de antes: `toSortedSet`, `String.format`, `android.net.Uri.decode` y los
nombres de prueba con coma. **Nada de esto lo coge Windows.** `comprobar.py` sólo
vigila los *imports* de `commonMain`; lo que se cuela por nombre completo o por
la forma del binding es cosa del CI.

---

## Lo que NO hay que deshacer

Cosas que parecen mejorables y no lo son. Están explicadas en `docs/DISENO.md` y
en `docs/CONTEXTO.md`, pero éstas son las que más fácil se tocan por error:

- **`beyondViewportPageCount = 1` en el `HorizontalPager`.** Sin eso, el carrusel
  **destruye y reconstruye la pantalla de Biblioteca en cada deslizamiento**. Era
  la causa de los tirones y costó cinco hipótesis falsas encontrarla.
- **`VELO_LEIDO = 0.55f`.** Ha cambiado de bando dos veces: se quitó al 70% porque
  apagaba la pantalla entera, y volvió porque Dani lo prefería. **Es un número
  para tocar, no para quitar.**
- **`Fluidez` y el contador de portadas.** Cuestan dos restas por fotograma y sólo
  hablan cuando algo va mal. Son lo que resolvió los tirones después de cinco
  sospechas falsas.
- **`Limpieza.originalDe` devuelve un nombre y no un veredicto.** Un `(21)` sólo
  es una copia si el original está al lado; si alguien hace que decida sola, se
  carga la numeración de una serie entera sin dar ningún error.
- **`Modifier.pulsable` lleva la forma y el fondo dentro.** En Compose el orden de
  los modificadores *es* el efecto: la escala va por fuera del `clip` o encoge
  sólo el contenido y deja el fondo quieto.

---

## Cómo se cierra una tanda aquí

1. `python comprobar.py` tiene que decir **PROBLEMAS: 0**.
2. `./gradlew :app:assembleDebug :shared:testDebugUnitTest` verde y **sin un solo
   `w:`**.
3. Si hay prueba nueva, lanzarla **por separado** con `--tests`, y comprobar con
   una clase inexistente que el filtro de verdad ejecuta algo.
4. **Actualizar `docs/CONTEXTO.md` (y `DISENO.md` si toca) en la MISMA tanda**,
   diciendo qué se comprobó **y qué no**.
5. Commit en español, Conventional Commits, con el porqué en el cuerpo.
   **Enseñarle siempre el mensaje escrito a Dani**, no sólo el hash.
6. Al pedirle que compruebe algo, **decir siempre si es de Android o de iOS**.
