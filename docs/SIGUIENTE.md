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

El último commit de código es `f559536`, que arregla una constante de
CoreGraphics. Si su trabajo de iOS está en verde, **las tres piezas de leer un
CBZ en el iPad compilan**.

---

## La tarea siguiente: `BibliotecaIOS`

`ArchivoIOS` está **HECHO (tanda 22, 05/09/2026)** y con él las cuatro piezas de
leer un CBZ en el iPad están escritas. Lo que falta ahora es **quién le da los
ficheros**, y eso es `BibliotecaIOS`.

**Lo primero que tiene que resolver ya está esperándolo con nombre.**
`ArchivoIOS` tiene un `private fun ruta(uri) = uri` que hoy es la identidad y
acepta rutas normales. En iOS el `uri` será el marcador de un *security-scoped
bookmark*, porque **una app del iPad no puede guardarse una ruta y volver a
abrirla mañana**: hay que resolver el marcador, pedir el acceso, y soltarlo al
terminar. Quien sabe de eso es `BibliotecaIOS`; `ArchivoIOS` solo tendrá que
llamarle desde esa línea.

Lo demás de la tanda es lo mismo que hace `Escaner` en Android: recorrer una
carpeta elegida por el usuario y devolver los cómics que hay. La parte que
decide (qué es un cómic, el número, el orden) ya es común y tiene pruebas.

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
| `ArchivoIOS` — leer un CBZ en el iPad | HECHO (tanda 22), sin compilar |
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
| Piezas de plataforma: `Disco`, `Zip`, `Imagen`, `Archivo` | ✅ escritas, las juzga el CI |
| `BibliotecaIOS`, `PortadasIOS`, `ColorPortada`, `Vigilante` | ❌ |
| `Rastro` — **es una decisión, no una tanda** (ver arriba) | ❌ |
| La interfaz a Compose Multiplatform | ❌ 3.228 líneas de `ui/` + 1.204 de `VistaModelo` + 713 de `MainActivity`, en `:app` |
| `iosApp/` — proyecto de Xcode | ❌ no existe |
| Trabajo de CI que archive el `.ipa` | ❌ no existe |

**~30%.** Por líneas de código sale el 45%, pero el número honesto es más bajo:
**lo que queda es donde está todo el riesgo.** La mudanza de la interfaz es la
mitad del trabajo real y no se ha empezado, el proyecto de Xcode tiene cero
líneas, y **nada de esto ha arrancado nunca en un iPad**.

**Un `.ipa` no se genera desde Windows** — hace falta Xcode. Pero **no hace falta
tener un Mac**: el runner de macOS del CI puede archivar un `.ipa` sin firmar y
Sideloadly lo firma con la cuenta de Apple de Dani al instalarlo (gratis, caduca
a los siete días). **Eso obliga a que `iosApp/` sea un proyecto de Xcode que el
CI pueda archivar solo**, no un esqueleto que haya que abrir a mano.

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
sistema.** Dos tandas seguidas igual:

- zlib, `inflateInit2_`, `ZLIB_VERSION` y la ventana `-15`: a la primera.
  Falló **un `import`**: los métodos de una *category* de Objective-C son
  funciones de extensión y **se importan uno a uno**
  (`fileHandleForReadingAtPath`).
- ImageIO, `CFDataCreate`, `CGBitmapContext`, `Image.makeRaster` y
  `toComposeImageBitmap`: a la primera. Falló **una constante**: las de
  CoreGraphics viven dentro de su enumeración y hay que pedirles `.value`
  (`CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value`).

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
