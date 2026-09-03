# Lector de cómics — proyecto Android

App personal para leer tu colección de CBZ/CBR en el móvil y llevar la cuenta
de lo que te falta de cada personaje.

## Qué hace

**Biblioteca.** Muestra tu árbol de carpetas tal como lo tengas organizado, en
filas de portadas estilo catálogo. Tú organizas, la app enseña.

**Lecturas.** Eliges un personaje y una *etapa* (New 52, Rebirth, Absolute
Universe... o una edad, si la editorial no las etiqueta) y te lista sus series
con cuántos números tiene cada una, para ir marcando lo que lees.

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
gemini.clave=...
gemini.modelo=          # opcional, por defecto gemini-flash-latest
```

Gradle las mete vía `BuildConfig`. También se pueden escribir en la pantalla de
Ajustes; manda `local.properties`. **`local.properties` está en `.gitignore`.**

**Las wikis de Marvel y DC no piden clave.** Y sin clave de Comic Vine o de
Gemini la app sigue funcionando: la biblioteca y el lector van igual, solo se
queda corto el TODO.

## De dónde salen los datos

| Qué | De dónde | Clave |
|---|---|---|
| Qué series tiene un personaje, y su etapa | Wikis de Marvel y DC (MediaWiki) | no |
| Cuántos números tiene cada serie | Comic Vine | sí |
| Cuáles importan y por qué | Gemini | sí |

El reparto no es casual: **los datos, de la base de datos; el criterio, del
modelo**. La lista se construye sobre series reales, así que el modelo no puede
colar nada inventado. Está explicado en `CLAUDE.md`.

## La estética

En `ui/Tema.kt`, primera línea:

```kotlin
val ESTILO = Estilo.CYBERPUNK   // o Estilo.IOS
```

Todo el diseño pasa por los tokens de ese fichero, así que cambiar esa línea
cambia color, forma y tipografía de la app entera.

## Lo que NO hace

- **CBR con RAR5**: no se leen, a proposito. junrar lee RAR4 (que es lo que
  genera todo el mundo) y la app detecta la firma del fichero: si algun dia
  te cruzas con un RAR5, te lo dice con ese nombre en vez de dar un error
  vago. Los CBZ y los CBR normales van.
- **Arcos sueltos** (por ejemplo Year One): el TODO va por series, y una
  historia dentro de una colección no tiene casilla propia. O la coges por su
  recopilatorio o no aparece.
