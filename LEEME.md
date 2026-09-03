# Lector de cómics — proyecto Android

App personal para leer tu colección de CBZ/CBR en el móvil y llevar la cuenta
de lo que te falta de cada personaje.

## Qué hace

**Biblioteca.** Muestra tu árbol de carpetas tal como lo tengas organizado, en
filas de portadas estilo catálogo. Tú organizas, la app enseña.

**Lecturas.** Las series que sigues, con cuántos números tiene cada una y qué
te falta, para ir marcando lo que lees. Y una agenda de lo que está por salir.

**Lector.** Zoom, doble toque, modo página con giro 3D y modo tira, doble
página al girar el móvil, recorte de bordes, teclas de volumen, zonas táctiles,
tira de miniaturas y tarjeta al siguiente cómic de la carpeta.

**Copia de seguridad.** Exporta e importa todo lo leído a un fichero. Guarda
carpeta + nombre, no rutas: las URIs de Android no sobreviven a una
reinstalación, así que al restaurar se recoloca contra la biblioteca que haya
en ese momento. Al importar no se pisa nada — se suma.

## Cómo abrirlo

1. Android Studio (Ladybug o posterior), `File > Open` y esta carpeta.
2. La primera vez tarda unos minutos bajando Gradle. Es normal.
3. Móvil con depuración USB y al play.

## Las claves de API

Copia `local.properties.ejemplo` a `local.properties` y rellena:

```
comicvine.clave=...
```

Gradle la mete vía `BuildConfig`. También se puede escribir en la pantalla de
Ajustes; manda `local.properties`. **`local.properties` está en `.gitignore`.**

Sin clave la app **sigue funcionando**: la biblioteca y el lector van igual, y
lo único que se queda a oscuras es cuántos números tiene cada serie.

## De dónde salen los datos

| Qué | De dónde | Clave |
|---|---|---|
| Qué volumen es cada carpeta | Comic Vine | sí |
| Qué números tiene, y en qué fecha salieron | Comic Vine | sí |

**Una sola fuente, y solo cosas contables.** El 02/09/2026 se fueron Gemini y
las wikis de Marvel y DC, que eran las dos únicas partes donde algo *opinaba*:
un modelo se inventa cifras con total aplomo. De Comic Vine se saca lo que se
puede contar y nada más, y **cada cifra de la pantalla dice de dónde sale**.
Está explicado en `CLAUDE.md`.

## La estética

En `ui/Tema.kt`, primera línea:

```kotlin
val ESTILO = Estilo.CYBERPUNK   // o Estilo.IOS
```

Todo el diseño pasa por los tokens de ese fichero, así que cambiar esa línea
cambia color, forma y tipografía de la app entera.

## Lo que NO hace

- **CBR sin convertir**: los RAR5 (y los RAR4 grandes) no se leen directamente.
  junrar solo lee RAR4 y se traga el fichero entero en memoria, así que la app
  detecta la firma y **convierte el cómic a CBZ en su caché** la primera vez que
  lo abres; a partir de ahí va por el camino de siempre. Se nota una vez, en el
  primer arranque de ese cómic. La caché se ve y se vacía desde Ajustes.
- **Arcos sueltos** (por ejemplo Year One): el TODO va por series, y una
  historia dentro de una colección no tiene casilla propia. O la coges por su
  recopilatorio o no aparece.
