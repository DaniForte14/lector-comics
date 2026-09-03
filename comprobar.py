#!/usr/bin/env python3
"""
Comprobacion de estructura del codigo Kotlin, para antes de compilar.

POR QUE EXISTE. Este proyecto se edita con reemplazos de texto desde fuera de
Android Studio, y el fallo caro no es el que descuadra las llaves —ese se ve
enseguida— sino el que deja un CUERPO HUERFANO: se borra una funcion cortando
por su primera linea en vez de por su firma completa, y queda el cuerpo suelto
a nivel de fichero. Las llaves siguen cuadrando y no lo ve nadie hasta que
Gradle dice "Expecting a top level declaration" cincuenta veces.

Paso el 02/09/2026 dos veces en la misma sesion, con Velo y con
BarraDesplazamiento. La segunda llego al movil de Dani.

QUE MIRA
  1. Llaves y parentesis sin cerrar, por fichero.
  2. Lineas sangradas cuando no hay nada abierto: eso es un cuerpo huerfano.

QUE NO MIRA: cualquier otra cosa. No es un compilador, es la red que se puede
tender en tres minutos desde un sitio donde no hay compilador.

    python3 comprobar.py
"""
import io, re, glob, sys

def limpia(l):
    """Fuera cadenas y comentarios de linea: sus llaves no cuentan."""
    l = re.sub(r'"""(?:.|\n)*?"""', '""', l)
    l = re.sub(r'"(?:\\.|[^"\\])*"', '""', l)
    l = re.sub(r"'(?:\\.|[^'\\])*'", "''", l)
    return l.split("//")[0]

# Si la linea anterior acaba en uno de estos, la de abajo es continuacion suya
# y que este sangrada es lo normal.
CONT = ("=", ",", "(", "->", ":", "+", "?", "&&", "||", ".", "{")

def continuacion(anterior):
    if anterior.endswith(CONT): return True
    # `val X: Shape = if (cyber)` y la expresion en la linea siguiente
    return anterior.endswith(")") and re.search(r'\bif\s*\(', anterior) is not None

problemas = 0
# LOS DOS MODULOS. Desde que existe :shared, media logica del proyecto vive
# fuera de app/ y este comprobador se habia quedado ciego a ella justo el dia
# que empezaron a moverse ficheros, que es cuando mas falta hace.
FUENTES = sorted(glob.glob("app/src/**/*.kt", recursive=True)
                 + glob.glob("shared/src/**/*.kt", recursive=True))

for f in FUENTES:
    llaves = parens = 0
    comentario = False
    anterior = ""
    for n, cruda in enumerate(io.open(f, encoding="utf-8").read().split("\n"), 1):
        s = cruda.strip()
        if comentario:
            if "*/" in s: comentario = False
            continue
        if s.startswith("/*"):
            if "*/" not in s: comentario = True
            continue
        if not s or s.startswith("//") or s.startswith("*"): continue

        if (llaves == 0 and parens == 0 and cruda.startswith((" ", "\t"))
                and not continuacion(anterior)
                and not s.startswith((".", "?", ":", ")", "@"))):
            print(f"{f}:{n}  CUERPO HUERFANO -> {s[:70]}")
            problemas += 1

        l = limpia(cruda)
        llaves += l.count("{") - l.count("}")
        parens += l.count("(") - l.count(")")
        anterior = l.strip()

    if llaves: print(f"{f}  LLAVES SIN CERRAR: {llaves}"); problemas += 1
    if parens: print(f"{f}  PARENTESIS SIN CERRAR: {parens}"); problemas += 1

# ─────────────── QUE commonMain SEA DE VERDAD COMUN ───────────────
#
# POR QUE HACE FALTA ESTO. El target de Android de :shared tiene el SDK y la JVM
# en el classpath, asi que `import android.graphics.Bitmap` dentro de commonMain
# COMPILA SIN REJISTAR aqui y solo revienta en el runner de macOS, cinco minutos
# despues y en otra maquina.
#
# Paso tres veces: toSortedSet, android.net.Uri.decode y dos imports huerfanos
# que se quedaron al sacar Portada de Componentes. Las tres se podian haber visto
# en un segundo, aqui.
#
# Solo mira los IMPORTS, que es lo barato y coge la mayoria. Lo que se cuela por
# nombre completo (`java.util.Calendar.getInstance()`) sigue siendo cosa del CI.
PROHIBIDO = ("import android.", "import java.", "import javax.", "import org.json.")

for f in sorted(glob.glob("shared/src/commonMain/**/*.kt", recursive=True)
                + glob.glob("shared/src/commonTest/**/*.kt", recursive=True)):
    for n, cruda in enumerate(io.open(f, encoding="utf-8").read().splitlines(), 1):
        s = cruda.strip()
        if any(s.startswith(p) for p in PROHIBIDO):
            print(f"{f}:{n}  NO ES COMUN: {s}")
            problemas += 1

print("PROBLEMAS:", problemas)
sys.exit(1 if problemas else 0)
