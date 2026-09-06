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

Y el 07/09/2026 otra vez, con la misma forma pero en un COMENTARIO: un
reemplazo partio un kdoc y dejo dos `*/` seguidos. Este script dijo
PROBLEMAS: 0 sobre un fichero que no compilaba, porque saltaba entera
cualquier linea que empezara por `*`. De ahi el punto 3.

QUE MIRA
  1. Llaves y parentesis sin cerrar, por fichero.
  2. Lineas sangradas cuando no hay nada abierto: eso es un cuerpo huerfano.
  3. Bloques de comentario descuadrados, por los dos lados: un `*/` que no
     cierra nada, y un `/*` que no se cierra nunca y se come el resto del
     fichero. Ninguno de los dos descuadra una llave.
  4. Imports de Android o de la JVM dentro de commonMain.

QUE NO MIRA, y conviene tenerlo presente:
  - Un bloque de comentario que empieza a MEDIA linea (`val x = 1 /* nota`).
    Solo se detectan los que abren al principio de la linea, que es como se
    escriben en este proyecto.
  - Cualquier otra cosa. No es un compilador, es la red que se puede tender en
    tres minutos desde un sitio donde no hay compilador. Que diga PROBLEMAS: 0
    no es que compile: eso lo dice Gradle.

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
    abierto = 0
    anterior = ""
    for n, cruda in enumerate(io.open(f, encoding="utf-8").read().split("\n"), 1):
        s = cruda.strip()
        if comentario:
            if "*/" in s: comentario = False
            continue
        if s.startswith("/*"):
            if "*/" not in s: comentario, abierto = True, n
            continue
        if not s or s.startswith("//") or s.startswith("*"):
            # UN `*/` AQUI ES UN CIERRE HUERFANO. Si llega a esta linea es que
            # `comentario` esta a False, o sea que no hay ningun bloque abierto
            # que cerrar. Pasa al partir un kdoc por la mitad con un reemplazo
            # de texto: queda el `*/` viejo y el nuevo, uno debajo del otro.
            #
            # SE MIRA AQUI Y NO EN UN CONTADOR APARTE a proposito: aqui la
            # maquina de estados de arriba YA ha decidido que esta linea es un
            # comentario, asi que un `*/` dentro de una cadena de texto no llega
            # nunca. Un contador suelto de `/*` contra `*/` por fichero cantaria
            # con `val marca = "*/"`, y un guardia que grita por codigo bueno se
            # acaba ignorando.
            #
            # `startswith("*")` y no solo `"*/" in s` porque si no salta con
            # cualquier `// ... */ ...`, que es texto y no cierra nada.
            if s.startswith("*") and "*/" in s:
                print(f"{f}:{n}  CIERRE DE COMENTARIO HUERFANO -> {s[:70]}")
                problemas += 1
            continue

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
    # El otro lado del mismo fallo: un `/**` que se queda abierto se COME el
    # resto del fichero. No descuadra ninguna llave —van dentro del comentario—
    # asi que las dos comprobaciones de arriba lo dan por bueno.
    if comentario:
        print(f"{f}:{abierto}  COMENTARIO SIN CERRAR: se come el resto del fichero")
        problemas += 1

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
