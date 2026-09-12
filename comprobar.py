#!/usr/bin/env python3
"""
Que commonMain sea de verdad comun: ni un import de Android o de la JVM dentro
de shared/src/commonMain ni de commonTest.

POR QUE SOLO ESTO (12/09/2026). Hasta ese dia miraba cuatro cosas: llaves y
parentesis, cuerpos huerfanos, comentarios descuadrados e imports. Dani lo
quito porque "siempre sale que hay que hacer algo": las tres primeras saltaban
sobre ficheros a medio editar por los agentes, y ademas Gradle las caza igual,
con peor mensaje. Se recupero SOLO esta porque es la unica que Gradle no ve
desde Windows. La version de cuatro esta en git (`9024ae0`) si algun dia vuelve
a hacer falta.

POR QUE HACE FALTA. El target de Android de :shared tiene el SDK y la JVM en el
classpath, asi que `import android.graphics.Bitmap` dentro de commonMain
COMPILA aqui y solo revienta en el runner de macOS, una vuelta de CI despues.
Paso tres veces: toSortedSet, android.net.Uri.decode y dos imports huerfanos al
sacar Portada de Componentes.

Solo mira los IMPORTS, que es lo barato y coge la mayoria. Lo que se cuela por
nombre completo (`java.util.Calendar.getInstance()`) sigue siendo cosa del CI.

    python comprobar.py
"""
import glob, io, sys

PROHIBIDO = ("import android.", "import java.", "import javax.", "import org.json.")

problemas = 0
for f in sorted(glob.glob("shared/src/commonMain/**/*.kt", recursive=True)
                + glob.glob("shared/src/commonTest/**/*.kt", recursive=True)):
    for n, cruda in enumerate(io.open(f, encoding="utf-8").read().splitlines(), 1):
        s = cruda.strip()
        if s.startswith(PROHIBIDO):
            print(f"{f}:{n}  NO ES COMUN: {s}")
            problemas += 1

print("PROBLEMAS:", problemas)
sys.exit(1 if problemas else 0)
