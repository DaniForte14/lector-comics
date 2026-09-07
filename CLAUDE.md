# Lector de cómics — cómo trabajar en este proyecto

App de Dani (personal, no va a ninguna tienda) para leer CBZ/CBR de una
biblioteca local y saber qué falta de cada serie. Kotlin + Compose, Android hoy
y iOS en camino.

---

## Antes de tocar nada

```
docs/SIGUIENTE.md   POR AQUÍ SE EMPIEZA. Qué toca ahora, qué NO hay que
                    deshacer, y cómo se cierra una tanda.
docs/CONTEXTO.md    Cómo está montado y TODAS las trampas encontradas, con
                    el intento que falló incluido. Si vas corto: §5, §6, §7.
docs/DISENO.md      Decisiones de aspecto e interfaz. El §22 dice qué reglas
                    de las skills de diseño NO aplican aquí y por qué (están
                    escritas para web y chocan con `minSdk 26`).
```

**Esos documentos son lo único que sobrevive a una conversación.** Casi todo lo
que hay dentro se aprendió por las malas, así que antes de diseñar un parche,
busca el tema ahí: es muy probable que ya esté escrito.

## Cómo hablarle a Dani

- Español, tuteo, directo, sin preámbulos.
- Sabe programar, pero **Android y Compose son nuevos para él**. Explica lo de
  Android; no expliques lo que es una función.
- **Avisa de las pegas al entregar, no cuando las descubra él.**
- Le vale más un "esto no lo he podido comprobar" que una seguridad inventada.

## Cómo escribir el código

- **Nombres de variables, funciones y clases en español.**
- Los comentarios explican **por qué**, no qué. Un comentario que repite el
  código sobra; uno que dice qué se intentó antes y por qué no valía, no.
- **Tandas cortas y compilar entre medias.** No hay tests de interfaz: el
  Compose va sin red.
- **Lo que decide algo va en una función pura con su test al lado**, sobre todo
  si tiene casos de borde o prioridades: eso se rompe sin dar ningún error,
  simplemente hace lo que no era.
- Las pruebas van en **`shared/src/commonTest/`** (21 ficheros). En
  `app/src/test/` solo queda `ExportarTest`, que necesita la JVM.

## Antes de dar nada por terminado

```bash
python comprobar.py     # en la raíz. Tiene que decir PROBLEMAS: 0
./gradlew :app:assembleDebug :shared:testDebugUnitTest
```

`comprobar.py` mira los dos módulos y caza cuatro roturas: llaves y paréntesis
sin cerrar, **cuerpos huérfanos** (código suelto cuando un borrado corta por en
medio de una función), bloques de comentario descuadrados, e imports de Android
o de la JVM dentro de `commonMain`.

> **`PROBLEMAS: 0` no significa "compila", significa "no están esas cuatro
> roturas".** Quien dice si compila es Gradle. El 07/09/2026 dio 0 sobre un
> fichero que no compilaba; por eso hay que pasar también el `gradlew`.

Y cruza los imports: **`private` a nivel de fichero es de FICHERO, no de
paquete.** Dos ficheros del mismo paquete no se ven las funciones privadas del
otro.

## Regla fija: los documentos se actualizan en la MISMA tanda

Cada cambio actualiza `docs/CONTEXTO.md`, `docs/DISENO.md` y `docs/SIGUIENTE.md`
a la vez que el código. No al final. Qué se hizo, por qué así, con qué se
comprobó — **y lo que no se comprobó, también**.

---

## El principio rector

> **Los datos, de la base de datos. El criterio, del modelo.**

Se llegó a base de fallos: un modelo se inventa cifras con total aplomo. El
02/09/2026 la regla se llevó a su conclusión: **fuera el modelo**. Se fueron
Gemini y las wikis de Marvel y DC, que eran lo único que opinaba.

Queda **una sola fuente, Comic Vine**, y de ella solo salen cosas contables: qué
volumen es cada carpeta, qué números tiene y cuándo salieron. Y lo que más valor
tiene: **cada cifra de la pantalla dice de dónde sale**.

## Dos plataformas: `:app` y `:shared`

Dani quiere la misma app en su iPad. Kotlin Multiplatform en el **mismo
repositorio**, porque la app ya es Compose al 100% y así Compose Multiplatform
reaprovecha lo que hay en vez de tirarlo.

```
shared/   commonMain — lo que no sabe de ninguna plataforma
app/      Android. Sigue siendo la app que funciona hoy.
iosApp/   (todavía no existe)
```

> **Regla de reparto:** lo que decide algo va en `shared`. Lo que toca disco,
> red, pantalla o sistema se queda en su plataforma, detrás de una interfaz o
> de un `expect/actual`.

**NADIE PUEDE VERIFICAR iOS DESDE AQUÍ.** Los objetivos de Apple solo se
declaran si el anfitrión es un Mac (`shared/build.gradle.kts`) — si se
declararan siempre, el proyecto ni siquiera configuraría en el ordenador de
Dani, que es Windows. Si una tanda toca `iosMain`, se dice **"escrito, sin
compilar"** y punto: quien compila es el CI, en verde desde el 04/09/2026 y con
las pruebas comunes ejecutándose **compiladas a nativo** en el simulador. Lo que
sí se verifica en cada tanda es que Android sigue compilando.

**LO DE APPLE SE LLAMA `iOS`, SIEMPRE.** Fichero solo de Apple: `iOS` en el
nombre (`DiscoIOS.kt`) y el comentario empieza por `iOS —`. Lo de Android, por
`ANDROID —`. Es porque en Windows Android Studio **no enseña `iosMain` como
carpeta de código**, así que el nombre es lo único que lo delata. Para verla hay
que pasar la vista del árbol de "Android" a "Project".

## Cómo está montado

Sin Room y sin librerías de red: persistencia en JSON y red con
`HttpURLConnection`. Menos dependencias, menos que se rompa.

```
LectorApp.kt      Único sitio donde se decide de dónde salen los datos
MainActivity.kt   Navegación (3 destinos) y las teclas de volumen del lector
VistaModelo.kt    Estado y lógica de la interfaz

datos/  Modelos, Parser, Escaner, Formatos, Busqueda, Progreso, Marcadores,
        ComicZip, Rar5, Imagenes, ConversorCarpeta, Limpieza,
        Recorte (+RecorteAndroid), Miniaturas, ColorPortada,
        Racha, Sesiones, Calendario, Estadisticas, Huecos, EstadoSerie,
        SeriesRemotas, Novedades, Vigilante, Rastro, Orden, Salto, Siguiente,
        Exportar
        Disco y Preferencias (interfaces; una línea en VistaModelo decide si
        entra la versión de Android o la de iOS)
red/    FuenteComics (interfaz) + ComicVine (única implementación)
ui/     Tema, Componentes, Lector, y una pantalla por fichero
```

**Todo lo externo va detrás de una interfaz**, para cambiar de proveedor tocando
una línea en `LectorApp`. Ya salvó el proyecto al abandonar Metron.

**Rendimiento: MEDIR, no adivinar.** Costó una tanda entera aprenderlo — hubo
optimizaciones razonadas sobre el código donde nadie sabía cuál pesaba, y el
tirón de verdad resultó ser otra cosa. Layout Inspector y perfil de GPU.

## Claves de API

En `local.properties` (git lo ignora), y Gradle las mete vía `BuildConfig`:

```
comicvine.clave=...
```

También desde Ajustes; manda `local.properties`. Sin clave, la biblioteca y el
lector funcionan igual.

---

## Si eres Paco o Lucía

Dani trabaja con **dos sesiones que escriben** y una que coordina. Si eres una
de las dos:

1. Al terminar una tarea **paras e informas**. No enlazas con la siguiente.
2. **No commiteas nunca.** Revisa y commitea la sesión que coordina.
3. **`docs/` no se toca.** Es de quien coordina.
4. **No tocas el fichero del otro.** El reparto se hace sin solape; si tu
   encargo te obliga a pisarlo, avisa en vez de hacerlo.
5. Si un encargo choca con algo que te dijo Dani, **pregunta**, no elijas.

## graphify

Grafo de conocimiento en `graphify-out/`.

- Para preguntas del código, `graphify query "<pregunta>"` antes que grep:
  devuelve un subgrafo acotado. `graphify path "<A>" "<B>"` para relaciones,
  `graphify explain "<concepto>"` para uno concreto.
- `graphify-out/wiki/index.md` para navegar; `GRAPH_REPORT.md` solo para
  arquitectura amplia.
- Después de tocar código, `graphify update .` (solo AST, no cuesta API).
