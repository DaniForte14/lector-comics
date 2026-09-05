# Por dónde seguir

Escrito el 04/09/2026 para retomar el trabajo en otra conversación. **Se lee en
dos minutos**; el detalle de cada cosa está en `docs/CONTEXTO.md`, que es largo a
propósito.

> Si estás empezando: lee antes `CLAUDE.md`, y de `docs/CONTEXTO.md` al menos
> §5 (trampas), §6 (lecciones de método) y §7 (estado y pendientes).

---

## Lo primero, en cuanto entres

**Mirar el CI del último commit.** Todo el trabajo de iOS se escribe a ciegas
—desde Windows no hay Kotlin/Native— y el único juez es el runner de macOS:

```bash
gh run list --limit 1
```

Desde la tanda 24 el trabajo de iOS **deja un `.ipa` como artefacto**. Si está
en verde, hay algo instalable. Compilar no es funcionar: nadie lo ha arrancado.

---

## La tarea siguiente: **que Dani instale el `.ipa`**

No es una tanda de código. Es el paso que convierte siete ficheros de
"compilan" en "funcionan", y **sólo lo puede dar él**.

1. Bajar el artefacto `lector-ipa` de la última ejecución verde del CI
   (pestaña Actions > la ejecución > Artifacts). Es un zip con `Lector.ipa`.
2. Sideloadly en el PC, iPad enchufado, su Apple ID. **Con cuenta gratuita la
   app caduca a los 7 días** y se refresca reconectándola.
3. En la app Archivos del iPad: *En mi iPad > Lector*, y meter ahí un CBZ.
4. Abrir la app.

**Lo que se está probando es la SONDA, no la app.** Sale una lista de ficheros;
tocando uno debería verse su primera página. Si esa página aparece,
`BibliotecaIOS`, `ArchivoIOS`, `ZipIOS` e `ImagenIOS` están bien. Si no:

| Lo que se ve | Dónde mirar |
|---|---|
| "No hay ningún CBZ en Documents" | `UIFileSharingEnabled`, o el fichero no llegó |
| Sale la lista, pero la página no | `ZipIOS` (descomprimir) o `ImagenIOS` (decodificar) |
| Un mensaje de error | Es el `Paginas.Error`, y ya dice el motivo |
| La app se cierra sola | Memoria: iOS mata sin avisar y no hay excepción que ver |

**Después de eso**, y sólo después, van el selector de documentos con marcadores
—que es la otra mitad del riesgo de iOS y sigue sin ejecutarse nunca— y la
mudanza de la interfaz de verdad.

**Lo más frágil, y no lo puede ver el compilador:** en iOS la opción
`withSecurityScope` de los marcadores es de macOS. `BibliotecaIOS` resuelve sin
opciones y pide el acceso después, que es como funciona en el iPad. **Si al
probarlo no deja abrir los ficheros, ése es el primer sitio donde mirar.**

**Y una cosa que `ArchivoIOS` dejó a medias a propósito:** el parámetro
`recortar` se ignora. `Recorte` decide el recuadro y es común, pero necesita los
píxeles, y en iOS hay que sacarlos de `ImagenIOS` antes de que Skia los envuelva.
Es un `RecorteIOS` de unas pocas líneas cuando toque, no un problema abierto.

---

## El mapa del port, con el marcador de verdad

El tapón es `VistaModelo`: **las cuatro pantallas lo reciben por parámetro**, así
que mientras siga en `:app` no se puede mudar ninguna. Tenía ocho objetos de
Android y 24 llamadas (contadas con `grep`, no de memoria):

| | Estado |
|---|---|
| preferencias | HECHO (tanda 13) |
| `ComicZip` a `Archivo` | HECHO (tanda 16) |
| `Escaner` a `Biblioteca` | HECHO (tanda 17) |
| `Miniaturas` a `Portadas` | HECHO (tanda 18) |
| `ArchivoIOS` — leer un CBZ en el iPad | HECHO (tanda 22), **compila a la primera** |
| `BibliotecaIOS` — marcadores y NSFileManager | HECHO (tanda 23) |
| `iosApp/` + framework + `.ipa` en el CI | HECHO (tanda 24), a la primera |
| `Rastro` (5 llamadas) | **es una decisión, no una tanda** — ver abajo |
| `ConversorCarpeta` (3) | pendiente |
| `Rar5` (2) | pendiente |
| `Vigilante` (1) | pendiente |
| `ColorPortada` (1) | pendiente |
| `AndroidViewModel(Application)` | pendiente |

**`Rastro` no es "otro envoltorio".** Sus llamadas no están sólo en
`VistaModelo`: hay unas cuarenta repartidas por `MainActivity`, el visor, las
cuatro pantallas, `Escaner` y `Miniaturas`, todas como `Rastro.apunta(ctx, ...)`.
Pasarlo a instancia obliga a tocarlas todas; dejarlo como objeto global con un
`Disco` dentro es **el patrón que este proyecto rechazó al escribir `Disco`**.
Hay que plantearle las dos opciones a Dani, no colarlo dentro de otra tanda.

**`ConversorCarpeta` y `Rar5` son la conversión de CBR**, y en iOS **no hay motor
de RAR** (junrar es Java, 7-Zip-JBinding es JVM más una librería nativa). Puede
que la respuesta correcta no sea una interfaz sino que **esa función no exista en
el iPad de momento**. También hay que hablarlo.

---

## Cuánto falta para un `.ipa` que Dani pueda instalar

Dani sigue el avance por aquí, así que **cada tanda de iOS actualiza esta tabla**.
El objetivo es un `.ipa` que entre en el iPad con Sideloadly.

| | |
|---|---|
| Lógica portable en `commonMain` | ✅ 4.425 líneas, con pruebas |
| Piezas de plataforma: `Disco`, `Zip`, `Imagen`, `Archivo`, `Biblioteca` | ✅ escritas, **ninguna ejecutada nunca** |
| `PortadasIOS`, `ColorPortada`, `Vigilante` | ❌ |
| `Rastro` — **es una decisión, no una tanda** (ver arriba) | ❌ |
| La interfaz a Compose Multiplatform | ❌ 3.228 líneas de `ui/` + 1.204 de `VistaModelo` + 713 de `MainActivity`, en `:app` |
| `iosApp/` — proyecto de Xcode (XcodeGen) | ✅ |
| CI que empaqueta el `.ipa` sin firmar | ✅ artefacto `lector-ipa`, 10,1 MB |
| **Que alguien lo instale y arranque** | ❌ **el paso que falta ahora** |

**~45%.** Sube diez de golpe porque la tanda 24 tacha las dos filas que no eran
código sino tubería, y porque ya existe algo instalable. Sigue siendo más bajo de
lo que dirían las líneas:
**lo que queda es donde está todo el riesgo.** La mudanza de la interfaz es la
mitad del trabajo real y no se ha empezado, el proyecto de Xcode tiene cero
líneas, y **nada de esto ha arrancado nunca en un iPad**.

Y de ese 45, **la parte de datos y el empaquetado ya están**: lo que falta es la
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
