# Reglas de R8 para el lector.
#
# R8 borra lo que nadie llama y renombra lo que queda. Eso funciona porque
# puede VER quien llama a que. Lo que se le escapa es lo que se busca por
# NOMBRE en tiempo de ejecucion: reflexion y codigo nativo. Aqui hay una cosa
# de esas y solo una.

# 7-Zip-JBinding: el motor RAR5 es una libreria NATIVA que busca estas clases
# y sus metodos por nombre desde C++ (JNI). Si R8 las renombra, la app compila
# igual de bien y luego revienta al abrir el primer CBR con un
# NoSuchMethodError que no dice de donde viene. Se conservan enteras.
-keep class net.sf.sevenzipjbinding.** { *; }
-keepclassmembers class net.sf.sevenzipjbinding.** { *; }
-dontwarn net.sf.sevenzipjbinding.**

# junrar es Java puro y no necesita conservarse, pero arrastra referencias a
# clases de escritorio que en Android no existen. Sin esto R8 avisa y falla.
-dontwarn com.github.junrar.**

# Para que un fallo en el movil siga diciendo fichero y linea. Sin esto, el
# informe de un cierre inesperado es una lista de numeros: se ahorran unos KB
# a cambio de no poder arreglar nada.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# El JSON de las listas y de las APIs se monta y se lee A MANO con
# org.json.JSONObject, campo por campo. No hay ninguna clase que se rellene
# por reflexion, asi que NO hacen falta reglas de -keep para los modelos.
# Si algun dia se mete kotlinx.serialization o Gson, esto deja de ser verdad.
