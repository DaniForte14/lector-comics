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

### Lo siguiente, y está a medias

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

- **Correr los tests**: trece ficheros escritos y **ninguno ejecutado**. En el
  entorno donde se escribieron solo había Java 11 y AGP pide 17, así que se
  contrastaron reimplementando la lógica en Python. `./gradlew testDebugUnitTest`
  es lo primero que hay que hacer con el IDE delante.
- **`elegirVolumen` sigue sin pruebas** y es la deuda más cara: es la función
  que impide que se cuele basura de Comic Vine. Las respuestas reales para
  escribirlas están recogidas en `docs/CONTEXTO.md`.
- **Forzar el trabajo diario de notificaciones en el móvil** (Android Studio >
  App Inspection > Background Task Inspector). Hasta que no salte una
  notificación de verdad, es código que compila, no una función que funciona.
- **Ver cuántos números traen `store_date`** de verdad, y si `DESFASE_ESPANA = 0`
  acierta.
- **El proyecto NO está en git.** Cualquier tanda grande va sin red de
  seguridad. En `_borrar_a_mano/` está el código amputado (orden de lectura y
  el TODO por personaje): es la única copia que hay de todo eso.
